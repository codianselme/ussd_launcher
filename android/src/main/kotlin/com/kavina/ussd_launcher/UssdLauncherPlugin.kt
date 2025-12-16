package com.kavina.ussd_launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.os.Handler
import android.os.Looper

class UssdLauncherPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var ussdSessionUnique: UssdSessionUnique
    private lateinit var ussdMultiSession: UssdMultiSession

    companion object {
        private var methodChannel: MethodChannel? = null
        private val handler = Handler(Looper.getMainLooper())
    
        fun onUssdResult(result: String) {
            handler.post {
                println("UssdLauncherPlugin: Sending USSD result to Flutter: $result")
                methodChannel?.invokeMethod("onUssdMessageReceived", result)
            }
        }
    
        private fun setMethodChannel(channel: MethodChannel) {
            methodChannel = channel
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ussd_launcher")
        channel.setMethodCallHandler(this)
        setMethodChannel(channel)
        context = flutterPluginBinding.applicationContext
        ussdSessionUnique = UssdSessionUnique(context)
        ussdMultiSession = UssdMultiSession(context)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "sendUssdRequest" -> handleSendUssdRequest(call, result)
            "multisessionUssd" -> handleMultisessionUssd(call, result)
            "getSimCards" -> ussdSessionUnique.getSimCards(result)
            "isAccessibilityEnabled" -> result.success(isAccessibilityServiceEnabled())
            "openAccessibilitySettings" -> {
                openAccessibilitySettings()
                result.success(null)
            }
            "sendNextOption" -> {
                ussdMultiSession.sendNextOptionManually(result)
            }
            "cancelSession" -> ussdMultiSession.cancelSession(result)
            "isOverlayPermissionGranted" -> result.success(UssdOverlayService.canDrawOverlay(context))
            "openOverlaySettings" -> {
                UssdOverlayService.openOverlaySettings(context)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun handleSendUssdRequest(call: MethodCall, result: Result) {
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings()
            result.error(
                "ACCESSIBILITY_NOT_ENABLED", 
                "Please enable accessibility service for USSD Launcher", 
                null
            )
            return
        }
        
        val ussdCode = call.argument<String>("ussdCode")
        val subscriptionId = call.argument<Int>("subscriptionId") ?: -1
        
        if (ussdCode.isNullOrEmpty()) {
            result.error("INVALID_ARGUMENT", "USSD code is required", null)
            return
        }
        
        ussdSessionUnique.sendUssdRequest(ussdCode, subscriptionId, result)
    }

    private fun handleMultisessionUssd(call: MethodCall, result: Result) {
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings()
            result.error(
                "ACCESSIBILITY_NOT_ENABLED", 
                "Please enable accessibility service for USSD Launcher", 
                null
            )
            return
        }

        val ussdCode = call.argument<String>("ussdCode")
        val slotIndex = call.argument<Int>("slotIndex") ?: 0
        val options = call.argument<List<String>>("options") ?: emptyList()
        val overlayMessage = call.argument<String>("overlayMessage")
        
        // Optional configurable delays
        call.argument<Int>("initialDelayMs")?.let { 
            ussdMultiSession.initialDelayMs = it.toLong() 
        }
        call.argument<Int>("optionDelayMs")?.let { 
            ussdMultiSession.optionDelayMs = it.toLong() 
        }
        
        // Custom overlay message
        overlayMessage?.let {
            ussdMultiSession.overlayMessage = it
        }
        
        if (ussdCode.isNullOrEmpty()) {
            result.error("INVALID_ARGUMENT", "USSD code is required", null)
            return
        }
        
        ussdMultiSession.callUSSDWithMenu(
            ussdCode, 
            slotIndex, 
            options, 
            UssdMultiSession.createDefaultHashMap(), 
            object : UssdMultiSession.CallbackInvoke {
                override fun responseInvoke(message: String) {
                    onUssdResult(message)
                }
                override fun over(message: String) {
                    onUssdResult(message)
                    result.success(null)
                }
            }
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 
                0
            )
            
            if (accessibilityEnabled == 1) {
                val service = "${context.packageName}/${UssdAccessibilityService::class.java.canonicalName}"
                val settingValue = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                settingValue?.contains(service) == true
            } else {
                false
            }
        } catch (e: Exception) {
            println("UssdLauncherPlugin: Error checking accessibility: ${e.message}")
            false
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            println("UssdLauncherPlugin: Error opening accessibility settings: ${e.message}")
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        methodChannel = null
        ussdSessionUnique.dispose()
    }
}
