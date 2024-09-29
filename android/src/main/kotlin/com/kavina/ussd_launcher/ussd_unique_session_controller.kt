package com.kavina.ussd_launcher

import com.kavina.ussd_launcher.UssdAccessibilityService
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class UssdSessionUnique(private val context: Context) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendUssdRequest(ussdCode: String, subscriptionId: Int, result: MethodChannel.Result) {
        
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
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getSimCards(result: MethodChannel.Result) {
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
                    "countryIso" to subscriptionInfo.countryIso,
                    "carrierId" to subscriptionInfo.carrierId,
                    "isEmbedded" to subscriptionInfo.isEmbedded,
                    "iccId" to subscriptionInfo.iccId,
                )
            }
            result.success(simCards)
        } else {
            result.error("NO_SIM_CARDS", "No SIM cards found", null)
        }
    }
}