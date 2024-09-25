package com.kavina.ussd_launcher


import androidx.annotation.NonNull
import com.kavina.ussd_launcher.USSDController
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.content.Context

class UssdLauncherPlugin: FlutterPlugin, MethodCallHandler {

    private lateinit var channel: MethodChannel
    private lateinit var ussdController: USSDController
    private lateinit var context: Context
    private lateinit var ussdService: USSDService

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "touch_ussd")
        context = flutterPluginBinding.applicationContext
        ussdService = USSDService() // Initialize USSDService instance
        ussdController = USSDController.getInstance(context, ussdService) // Pass ussdService
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "callUSSDInvoke" -> {
                val ussdCode = call.argument<String>("ussdCode")
                val simSlot = call.argument<Int>("simSlot") ?: 0
                if (ussdCode != null) {
                    // Initialize map with required keys
                    val mapping = HashMap<String, HashSet<String>>().apply {
                        put("KEY_ERROR", hashSetOf("Error 1", "Error 2")) // Replace with actual error messages
                        put("KEY_LOGIN", hashSetOf("Login Option 1", "Login Option 2")) // Replace with actual login options
                    }

                    ussdController.callUSSDInvoke(ussdCode, mapping, object : USSDController.CallbackInvoke {
                        override fun over(str: String) {
                            result.error("USSD_ERROR", str, null)
                        }

                        override fun responseInvoke(str: String) {
                            result.success(str)
                        }
                    })
                } else {
                    result.error("INVALID_ARGUMENT", "USSD code is required", null)
                }
            }
            "sendUSSD" -> {
                val response = call.argument<String>("response")
                val simSlot = call.argument<Int>("simSlot") ?: 0
                if (response != null) {
                    ussdController.send(response, object : USSDController.CallbackMessage {
                        override fun responseMessage(response: String) {
                            result.success(response)
                        }
                    })
                } else {
                    result.error("INVALID_ARGUMENT", "Response is required", null)
                }
            }
            "addUSSDStep" -> {
                val response = call.argument<String>("response")
                if (response != null) {
                    ussdController.addUSSDStep(response)
                    result.success(true)
                } else {
                    result.error("INVALID_ARGUMENT", "Response is required", null)
                }
            }
            "callUSSDOverlayInvoke" -> {
                val ussdCode = call.argument<String>("ussdCode")
                val simSlot = call.argument<Int>("simSlot") ?: 0
                val mapping = HashMap<String, HashSet<String>>().apply {
                    put("KEY_ERROR", hashSetOf("Error 1", "Error 2")) // Replace with your own messages
                    put("KEY_LOGIN", hashSetOf("Login Option 1", "Login Option 2")) // Replace with your own options
                }
                if (ussdCode != null) {
                    ussdController.callUSSDOverlayInvoke(ussdCode, mapping, object : USSDController.CallbackInvoke {
                        override fun over(str: String) {
                            result.error("USSD_ERROR", str, null)
                        }

                        override fun responseInvoke(str: String) {
                            result.success(str)
                        }
                    })
                } else {
                    result.error("INVALID_ARGUMENT", "USSD code is required", null)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}