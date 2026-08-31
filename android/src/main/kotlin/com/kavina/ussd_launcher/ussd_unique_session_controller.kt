package com.kavina.ussd_launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class UssdSessionUnique(private val context: Context) {

    // Proper coroutine scope instead of GlobalScope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Send USSD request with Android version compatibility
     * - Android 8+ (API 26+): Uses TelephonyManager.sendUssdRequest()
     * - Android 5-7 (API 21-25): Falls back to ACTION_CALL intent
     */
    fun sendUssdRequest(ussdCode: String, subscriptionId: Int, result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sendUssdRequestModern(ussdCode, subscriptionId, result)
        } else {
            sendUssdRequestLegacy(ussdCode, result)
        }
    }

    /**
     * Modern implementation for Android 8+ using TelephonyManager.sendUssdRequest()
     */
    private fun sendUssdRequestModern(ussdCode: String, subscriptionId: Int, result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            result.error("UNSUPPORTED_API", "This method requires Android 8.0+", null)
            return
        }

        val telephonyManager = if (subscriptionId != -1) {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .createForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }

        val callback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
                result.success(response.toString())
            }

            override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
                val errorMessage = when (failureCode) {
                    TelephonyManager.USSD_RETURN_FAILURE -> "USSD request failed"
                    TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD service unavailable"
                    else -> "Unknown error occurred (code: $failureCode)"
                }
                result.error("USSD_FAILED", errorMessage, null)
            }
        }

        scope.launch {
            try {
                telephonyManager.sendUssdRequest(ussdCode, callback, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                result.error("PERMISSION_DENIED", "Permission denied: ${e.message}", null)
            } catch (e: Exception) {
                result.error("UNEXPECTED_ERROR", "Unexpected error: ${e.message}", null)
            }
        }
    }

    /**
     * Legacy implementation for Android 5-7 using ACTION_CALL intent
     * Note: This method cannot receive USSD response directly, 
     * it relies on the Accessibility Service to capture the response
     */
    private fun sendUssdRequestLegacy(ussdCode: String, result: MethodChannel.Result) {
        try {
            val encodedHash = Uri.encode("#")
            val formattedCode = ussdCode.replace("#", encodedHash)
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$formattedCode"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
            // For legacy mode, we inform the caller that the request was initiated
            // The actual response will come through the accessibility service listener
            result.success("USSD_INITIATED_LEGACY")
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "CALL_PHONE permission required: ${e.message}", null)
        } catch (e: Exception) {
            result.error("LEGACY_USSD_ERROR", "Failed to initiate USSD: ${e.message}", null)
        }
    }

    /**
     * Get available SIM cards information
     * Compatible with Android 5.1+ (API 22+)
     */
    fun getSimCards(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            result.error("UNSUPPORTED_API", "This feature requires Android 5.1+", null)
            return
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (activeSubscriptionInfoList != null && activeSubscriptionInfoList.isNotEmpty()) {
                val simCards = activeSubscriptionInfoList.map { subscriptionInfo ->
                    val simCard = mutableMapOf<String, Any?>(
                        "subscriptionId" to subscriptionInfo.subscriptionId,
                        "displayName" to subscriptionInfo.displayName?.toString(),
                        "carrierName" to subscriptionInfo.carrierName?.toString(),
                        "number" to subscriptionInfo.number,
                        "slotIndex" to subscriptionInfo.simSlotIndex,
                        "countryIso" to subscriptionInfo.countryIso
                    )
                    
                    // Add fields available in API 29+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        simCard["carrierId"] = subscriptionInfo.carrierId
                        simCard["isEmbedded"] = subscriptionInfo.isEmbedded
                    }
                    
                    // iccId may require READ_PRIVILEGED_PHONE_STATE on some devices
                    try {
                        simCard["iccId"] = subscriptionInfo.iccId
                    } catch (e: SecurityException) {
                        simCard["iccId"] = null
                    }
                    
                    simCard
                }
                result.success(simCards)
            } else {
                result.success(emptyList<Map<String, Any?>>())
            }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", "READ_PHONE_STATE permission required: ${e.message}", null)
        } catch (e: Exception) {
            result.error("SIM_CARDS_ERROR", "Failed to get SIM cards: ${e.message}", null)
        }
    }

    /**
     * Clean up coroutine scope when no longer needed
     */
    fun dispose() {
        scope.cancel()
    }
}