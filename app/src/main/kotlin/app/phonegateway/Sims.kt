package app.phonegateway

import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

object Sims {
    @Suppress("MissingPermission")
    fun list(ctx: Context): JSONArray {
        val out = JSONArray()
        try {
            val subs = ctx.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList ?: return out
            subs.sortedBy { it.simSlotIndex }.forEach {
                out.put(JSONObject()
                    .put("slot", it.simSlotIndex + 1)
                    .put("name", it.displayName?.toString() ?: "")
                    .put("carrier", it.carrierName?.toString() ?: "")
                    .put("number", it.number ?: ""))
            }
        } catch (_: SecurityException) { }
        return out
    }

    @Suppress("MissingPermission")
    fun subscriptionId(ctx: Context, slot: Int): Int? = try {
        ctx.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfoForSimSlotIndex(slot - 1)?.subscriptionId
    } catch (_: SecurityException) { null }

    @Suppress("DEPRECATION")
    fun smsManager(ctx: Context, slot: Int?): SmsManager {
        val subId = if (slot == null) null else subscriptionId(ctx, slot) ?: throw IllegalStateException("SIM $slot introuvable")
        return when {
            subId != null && Build.VERSION.SDK_INT >= 31 -> ctx.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            subId != null -> SmsManager.getSmsManagerForSubscriptionId(subId)
            Build.VERSION.SDK_INT >= 31 -> ctx.getSystemService(SmsManager::class.java)
            else -> SmsManager.getDefault()
        }
    }

    @Suppress("MissingPermission")
    fun callAccount(ctx: Context, slot: Int): PhoneAccountHandle? {
        val subId = subscriptionId(ctx, slot) ?: return null
        val telecom = ctx.getSystemService(TelecomManager::class.java)
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        return try {
            telecom.callCapablePhoneAccounts.firstOrNull { tm.createForPhoneAccountHandle(it)?.subscriptionId == subId }
        } catch (_: SecurityException) { null }
    }
}
