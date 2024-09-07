package com.kavina.ussd_launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class UssdLauncherPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel : MethodChannel
    private lateinit var context: Context
    private var activity: android.app.Activity? = null

    companion object {
        lateinit var flutterChannel: MethodChannel
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ussd_launcher")
        flutterChannel = channel
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "launchUssd" -> {
                val ussdCode = call.argument<String>("ussdCode")
                if (ussdCode != null) {
                    launchUssd(ussdCode, result)
                } else {
                    result.error("INVALID_ARGUMENT", "USSD code is required", null)
                }
            }
            "openAccessibilitySettings" -> {
                openAccessibilitySettings()
                result.success(null)
            }
            "isAccessibilityPermissionEnabled" -> {
                result.success(isAccessibilityServiceEnabled())
            }
            else -> result.notImplemented()
        }
    }

    private fun launchUssd(ussdCode: String, result: Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.CALL_PHONE), 1)
                result.error("PERMISSION_DENIED", "Call phone permission is required", null)
                return
            }
        }

        if (!isAccessibilityServiceEnabled()) {
            result.error("ACCESSIBILITY_SERVICE_DISABLED", "Accessibility service is not enabled", null)
            return
        }

        val formattedUssdCode = ussdCode.replace("#", Uri.encode("#"))
        val ussdUri = Uri.parse("tel:$formattedUssdCode")
        val intent = Intent(Intent.ACTION_CALL, ussdUri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)

        result.success(null)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
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

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}

class UssdAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className == "android.app.AlertDialog") {
            val nodeInfo = event.source
            if (nodeInfo != null) {
                val dialogText = nodeInfo.findAccessibilityNodeInfosByViewId("android:id/message")
                if (dialogText.isNotEmpty()) {
                    val ussdMessage = dialogText[0].text.toString()
                    UssdLauncherPlugin.flutterChannel.invokeMethod("onUssdMessageReceived", ussdMessage)
                }
                nodeInfo.recycle()
            }
        }
    }

    override fun onInterrupt() {}
}
