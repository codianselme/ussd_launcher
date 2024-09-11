package com.kavina.ussd_launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler 
import io.flutter.plugin.common.MethodChannel.Result
import java.util.ArrayDeque
import android.view.KeyEvent
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import androidx.annotation.NonNull
import android.content.Context
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionInfo




class UssdLauncherPlugin: FlutterPlugin, MethodCallHandler {
    private lateinit var channel : MethodChannel
    // private lateinit var singleSessionChannel: MethodChannel
    // private lateinit var singleSessionUssd: SingleSessionUssd

    private lateinit var context: android.content.Context
    private var isMultiSession = false
    private var pendingResult: Result? = null

    companion object {
        private var instance: UssdLauncherPlugin? = null

        fun getInstance(): UssdLauncherPlugin? {
            return instance
        }

        // Appelé lorsqu'un résultat USSD est reçu
        fun onUssdResult(message: String) {
            Handler(Looper.getMainLooper()).post {
                getInstance()?.pendingResult?.success(message)
                getInstance()?.pendingResult = null
            }
        }
    }

    // Initialisation du plugin
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ussd_launcher")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        instance = this
    }

    // Gestion des appels de méthode depuis Flutter
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "sendUssdRequest" -> {
                val ussdCode = call.argument<String>("ussdCode")
                val subscriptionId = call.argument<Int>("subscriptionId") ?: -1
                if (ussdCode != null) {
                    sendUssdRequest(ussdCode, subscriptionId, result)
                } else {
                    result.error("INVALID_ARGUMENT", "USSD code is required", null)
                }
            }
            "multisessionUssd" -> {
                val ussdCode = call.argument<String>("ussdCode")
                val subscriptionId = call.argument<Int>("subscriptionId") ?: -1
                if (ussdCode != null) {
                    multisessionUssd(ussdCode, subscriptionId, result)
                } else {
                    result.error("INVALID_ARGUMENT", "USSD code is required", null)
                }
            }
            "sendMessage" -> {
                val message = call.argument<String>("message")
                if (message != null) {
                    sendMessage(message, result)
                } else {
                    result.error("INVALID_ARGUMENT", "Message is required", null)
                }
            }
            "cancelSession" -> {
                cancelSession(result)
            }
            "isAccessibilityPermissionEnabled" -> {
                result.success(isAccessibilityServiceEnabled())
            }
            "openAccessibilitySettings" -> {
                openAccessibilitySettings()
                result.success(null)
            }
            "getSimCards" -> {
                getSimCards(result)
            }
            else -> result.notImplemented()
        }
    }

    // Envoie une requête USSD
    private fun sendUssdRequest(ussdCode: String, subscriptionId: Int, result: MethodChannel.Result) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telephonyManager = if (subscriptionId != -1) {
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                telephonyManager.createForSubscriptionId(subscriptionId)
            } else {
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            }

            val callback = object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
                    result.success(response.toString())
                }

                override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
                    when (failureCode) {
                        TelephonyManager.USSD_RETURN_FAILURE -> result.error("USSD_FAILED", "USSD request failed", null)
                        else -> result.error("UNKNOWN_ERROR", "Unknown error occurred", null)
                    }
                }
            }

            GlobalScope.launch(Dispatchers.Main) {
                try {
                    telephonyManager.sendUssdRequest(ussdCode, callback, null)
                } catch (e: SecurityException) {
                    result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null)
                } catch (e: Exception) {
                    result.error("UNEXPECTED_ERROR", "Unexpected error: ${e.message}", null)
                }
            }
        } else {
            result.error("UNSUPPORTED_VERSION", "USSD requests are not supported on this Android version", null)
        }
    }

    private fun launchUssd(ussdCode: String, result: Result) {
        isMultiSession = false
        val ussdUri = formatUssdCode(ussdCode)
        val intent = Intent(Intent.ACTION_CALL, ussdUri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        result.success(null)
    }


    // Lance une session USSD multi-étapes
    @RequiresApi(Build.VERSION_CODES.O)
    private fun multisessionUssd(ussdCode: String, subscriptionId: Int, result: Result) {
        println("Launching multi-session USSD: $ussdCode")
        isMultiSession = true
        pendingResult = result
        val ussdUri = formatUssdCode(ussdCode)
        val telecomManager = context.getSystemService(android.content.Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.placeCall(ussdUri, null)
    }

    // Envoie un message dans une session USSD multi-étapes
    private fun sendMessage(message: String, result: Result) {
        println("Sending message: $message")
        if (!isMultiSession) {
            result.error("INVALID_STATE", "Not in a multi-session USSD dialog", null)
            return
        }
        pendingResult = result
        UssdAccessibilityService.sendReply(message)
    }

    // Formate le code USSD
    private fun formatUssdCode(ussdCode: String): Uri {
        var formattedCode = ussdCode
        if (!formattedCode.startsWith("tel:")) {
            formattedCode = "tel:$formattedCode"
        }
        formattedCode = formattedCode.replace("#", Uri.encode("#"))
        return Uri.parse(formattedCode)
    }

    // Annule la session USSD en cours
    private fun cancelSession(result: Result) {
        isMultiSession = false
        pendingResult = null
        UssdAccessibilityService.cancelSession()
        result.success(null)
    }

    // Vérifie si le service d'accessibilité est activé
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        if (accessibilityEnabled == 1) {
            val service = "${context.packageName}/${UssdAccessibilityService::class.java.canonicalName}"
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return settingValue?.contains(service) == true
        }
        return false
    }

    // Ouvre les paramètres d'accessibilité
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun getSimCards(result: Result) {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

        if (activeSubscriptionInfoList != null) {
            val simCards = activeSubscriptionInfoList.map { subscriptionInfo ->
                mapOf(
                    "subscriptionId" to subscriptionInfo.subscriptionId,
                    "displayName" to subscriptionInfo.displayName,
                    "carrierName" to subscriptionInfo.carrierName,
                    "number" to subscriptionInfo.number,
                    "slotIndex" to subscriptionInfo.simSlotIndex,
                    "countryIso" to subscriptionInfo.countryIso, // Ajout du code ISO du pays
                    "carrierId" to subscriptionInfo.carrierId, // Ajout de l'ID du transporteur
                    "isEmbedded" to subscriptionInfo.isEmbedded, // Indique si c'est une eSIM
                    "iccId" to subscriptionInfo.iccId, // ICCID de la carte SIM
                )
            }
            result.success(simCards)
        } else {
            result.error("NO_SIM_CARDS", "No SIM cards found", null)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        instance = null
    }
}

class UssdAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: UssdAccessibilityService? = null
        private var pendingMessage: String? = null

        // Envoie une réponse dans la session USSD
        fun sendReply(message: String) {
            println("Setting pending message: $message")
            pendingMessage = message
            instance?.performReply()
        }

        // Annule la session USSD
        fun cancelSession() {
            instance?.let { service ->
                val rootInActiveWindow = service.rootInActiveWindow
                val cancelButton = rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/button2")
                cancelButton?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }

    // Effectue la réponse dans la session USSD
    private fun performReply() {
        val message = pendingMessage ?: return
        println("Performing reply with message: $message")

        val rootInActiveWindow = this.rootInActiveWindow ?: return
        println("Root in active window: $rootInActiveWindow")

        // Chercher le champ de saisie
        val editText = findInputField(rootInActiveWindow)

        if (editText != null) {
            // Insérer le texte
            val bundle = Bundle()
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            val setTextSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            println("Set text action performed: $setTextSuccess")

            // Chercher et cliquer sur le bouton de confirmation
            val button = findConfirmButton(rootInActiveWindow)
            if (button != null) {
                val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                println("Click action performed: $clickSuccess")
            } else {
                println("Confirm button not found, trying alternative methods")
                tryAlternativeConfirmMethods(rootInActiveWindow)
            }
        } else {
            println("Input field not found")
        }

        pendingMessage = null
    }

    // Trouve le champ de saisie dans l'interface USSD
    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val editTexts = findNodesByClassName(root, "android.widget.EditText")
        return editTexts.firstOrNull()
    }


    // Trouve le bouton de confirmation dans l'interface USSD
    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = findNodesByClassName(root, "android.widget.Button")
        return buttons.firstOrNull {
            it.text?.toString()?.toLowerCase() in listOf("send", "ok", "submit", "confirmer", "envoyer")
        }
    }

    // Essaie des méthodes alternatives pour confirmer l'action USSD
    private fun tryAlternativeConfirmMethods(root: AccessibilityNodeInfo) {
        // Méthode 1: Essayer de cliquer sur tous les boutons
        val allButtons = findNodesByClassName(root, "android.widget.Button")
        for (button in allButtons) {
            println("Attempting to click button: ${button.text}")
            val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("Successfully clicked button: ${button.text}")
                return
            }
        }

        // Méthode 2: Essayer de cliquer sur tous les éléments cliquables
        val clickableNodes = findClickableNodes(root)
        for (node in clickableNodes) {
            println("Attempting to click node: ${node.className}")
            val clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("Successfully clicked node: ${node.className}")
                return
            }
        }

        // Méthode 3: Simuler un appui sur la touche "Entrée"
        val enterKeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        val dispatchSuccess = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(Path(), 0, 1))
                .build(),
            null,
            null
        )
        println("Dispatched Enter key event: $dispatchSuccess")
    }

    // Trouve tous les nœuds cliquables dans l'interface
    private fun findClickableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isClickable) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return result
    }

    // Trouve tous les nœuds d'une classe spécifique dans l'interface
    private fun findNodesByClassName(root: AccessibilityNodeInfo?, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className?.toString() == className) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return result
    }

    // Gère les événements d'accessibilité
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        println("Accessibility event received: ${event.eventType}")
        println("Event source: ${event.source}")
        println("Event class name: ${event.className}")
        println("Event package name: ${event.packageName}")
        println("Event text: ${event.text}")

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val nodeInfo = event.source
            if (nodeInfo != null) {
                println("Node info: ${nodeInfo.className}")
                println("Node text: ${nodeInfo.text}")
                val ussdMessage = findUssdMessage(nodeInfo)
                println("Potential USSD message: $ussdMessage")
                if (ussdMessage != null) {
                    UssdLauncherPlugin.onUssdResult(ussdMessage)
                }

                // Tenter d'insérer le message en attente, s'il y en a un
                if (pendingMessage != null) {
                    performReply()
                }

                nodeInfo.recycle()
            }
        }
    }

    // Trouve le message USSD dans l'interface
    private fun findUssdMessage(node: AccessibilityNodeInfo): String? {
        if (node.childCount == 0) {
            return node.text?.toString()
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            val message = findUssdMessage(childNode)
            if (message != null) {
                return message
            }
        }
        return null
    }

    override fun onInterrupt() {}

    // Appelé lorsque le service d'accessibilité est connecté
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        println("UssdAccessibilityService connected")
    }
}
