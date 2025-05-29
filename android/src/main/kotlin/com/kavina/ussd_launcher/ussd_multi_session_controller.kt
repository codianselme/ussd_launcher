package com.kavina.ussd_launcher

import com.kavina.ussd_launcher.UssdAccessibilityService
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
import android.widget.Toast





class UssdMultiSession(private val context: Context) {
    
    private var ussdOptionsQueue: ArrayDeque<String> = ArrayDeque()

    private var isRunning = false
    private var callbackInvoke: CallbackInvoke? = null
    private var map: HashMap<String, HashSet<String>>? = null

    companion object {
        private const val KEY_ERROR = "KEY_ERROR"
        private const val KEY_LOGIN = "KEY_LOGIN"
    }


    private fun createHashMap(): HashMap<String, HashSet<String>> {
        val map = HashMap<String, HashSet<String>>()
        map["KEY_ERROR"] = hashSetOf("Error message")
        map["KEY_LOGIN"] = hashSetOf("Login message")
        return map
    }

    fun callUSSDInvoke(str: String, simSlot: Int, hashMap: HashMap<String, HashSet<String>>, callbackInvoke: CallbackInvoke) {
        this.callbackInvoke = callbackInvoke
        this.map = hashMap
        if (verifyAccesibilityAccess(context)) {
            dialUp(str, simSlot)
        } else {
            this.callbackInvoke?.over("Check your accessibility")
        }
    }

    fun callUSSDWithMenu(str: String, simSlot: Int, options: List<String>, hashMap: HashMap<String, HashSet<String>>, callbackInvoke: CallbackInvoke) {
        this.callbackInvoke = callbackInvoke
        this.map = hashMap
        this.ussdOptionsQueue.clear()
        this.ussdOptionsQueue.addAll(options)
        if (verifyAccesibilityAccess(context)) {
            dialUp(str, simSlot)
        } else {
            this.callbackInvoke?.over("Check your accessibility")
        }
    }

    fun callUSSDOverlayInvoke(str: String, simSlot: Int, hashMap: HashMap<String, HashSet<String>>, callbackInvoke: CallbackInvoke) {
        this.callbackInvoke = callbackInvoke
        this.map = hashMap
        if (verifyAccesibilityAccess(context) && verifyOverLay(context)) {
            dialUp(str, simSlot)
        } else {
            this.callbackInvoke?.over("Check your accessibility | overlay permission")
        }
    }


    private fun dialUp(str: String, simSlot: Int) {
        val hashMap = this.map
        if (hashMap == null || !hashMap.containsKey(KEY_ERROR) || !hashMap.containsKey(KEY_LOGIN)) {
            this.callbackInvoke?.over("Bad Mapping structure")
            return
        }
        if (str.isEmpty()) {
            this.callbackInvoke?.over("Bad ussd number")
            return
        }
        val encodedHash = Uri.encode("#")
        val ussdCode = str.replace("#", encodedHash)
        val uri = Uri.parse("tel:$ussdCode")
        if (uri != null) {
            this.isRunning = true
        }
        setHideDialogs(true) // Activer le masquage des dialogues
        context.startActivity(getActionCallIntent(uri, simSlot))

         // Ajoutez un délai pour attendre que l'appel soit établi avant d'envoyer les options
         Handler(Looper.getMainLooper()).postDelayed({
            sendNextUssdOption()
        }, 3500) // 3.5 secondes de délai, ajustez si nécessaire
    }

    private fun sendNextUssdOption() {
        if (ussdOptionsQueue.isNotEmpty()) {
            val nextOption = ussdOptionsQueue.poll()
            if (nextOption != null) {
                sendUssdOption(nextOption)
            }
        } else {
            println("Toutes les options USSD ont été traitées. Fin de la session.")
            // this.callbackInvoke?.over("USSD session completed")
            Toast.makeText(context, "Session USSD terminée", Toast.LENGTH_SHORT).show()
            try {
                cancelSession()
            } catch (e: Exception) {
                println("Erreur lors de la fin de la session USSD : ${e.message}")
            }
        }
    }

    private fun sendUssdOption(option: String) {
        try {
            UssdAccessibilityService.sendReply(listOf(option))
            Handler(Looper.getMainLooper()).postDelayed({
                // this.callbackInvoke?.responseInvoke("Option envoyée : $option")
                // Toast.makeText(context, "Option envoyée : $option", Toast.LENGTH_SHORT).show()
                sendNextUssdOption()
            }, 2000) // 2 secondes de délai, ajustez si nécessaire
        } catch (e: Exception) {
            println("Erreur lors de l'envoi de l'option USSD : ${e.message}")
            callbackInvoke?.over("Erreur lors de l'envoi de l'option USSD")
        }
    }

    private fun getActionCallIntent(uri: Uri, simSlot: Int): Intent {
        val slotKeys = arrayOf(
            "extra_asus_dial_use_dualsim",
            "com.android.phone.extra.slot",
            "slot",
            "simslot",
            "sim_slot",
            "Subscription",
            "phone",
            "com.android.phone.DialingMode",
            "simSlot",
            "slot_id",
            "simId",
            "simnum",
            "phone_type",
            "slotId",
            "slotIdx"
        )
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.force.slot", true)
            putExtra("Cdma_Supp", true)
        }
        for (key in slotKeys) {
            intent.putExtra(key, simSlot)
        }
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager?
        telecomManager?.let {
            val phoneAccounts = it.callCapablePhoneAccounts
            if (phoneAccounts.size > simSlot) {
                intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", phoneAccounts[simSlot])
            }
        }
        return intent
    }

    private fun verifyAccesibilityAccess(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (services != null) {
                return services.lowercase().contains(context.packageName.lowercase())
            }
        }
        return false
    }

    private fun verifyOverLay(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun sendMessage(message: String, result: Result) {
        // Implémentez ici la logique pour envoyer le message USSD
        result.success("Message envoyé : $message")
    }

    fun cancelSession(result: Result? = null) {
        if (isRunning) {
            isRunning = false
            println("Annulation de la session USSD en cours")
            try {
                UssdAccessibilityService.cancelSession()
                // callbackInvoke?.over("USSD session cancelled")
                // Toast.makeText(context, "Session USSD annulée", Toast.LENGTH_SHORT).show()
                result?.success(null)
            } catch (e: Exception) {
                println("Erreur lors de l'annulation de la session USSD : ${e.message}")
                result?.error("CANCEL_ERROR", "Erreur lors de l'annulation de la session USSD", null)
            }
        } else {
            println("Aucune session USSD en cours à annuler")
            result?.error("NO_RUNNING_SESSION", "Aucune session USSD en cours à annuler", null)
        }
    }

    interface CallbackInvoke {
        fun responseInvoke(message: String)
        fun over(message: String)
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
    fun getSimCards(result: Result) {
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

    fun setHideDialogs(hide: Boolean) {
        UssdAccessibilityService.hideDialogs = hide
    }
}

