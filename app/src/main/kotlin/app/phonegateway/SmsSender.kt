package app.phonegateway

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager

object SmsSender {
    const val ACTION_SENT = "app.phonegateway.SMS_SENT"
    const val ACTION_DELIVERED = "app.phonegateway.SMS_DELIVERED"

    @Suppress("DEPRECATION")
    fun send(ctx: Context, job: Job) {
        job.set(Status.SENDING)
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        val parts = sm.divideMessage(job.text)
        job.partsTotal.set(parts.size)
        val sent = ArrayList<PendingIntent>()
        val delivered = ArrayList<PendingIntent>()
        for (i in parts.indices) {
            sent.add(pending(ctx, ACTION_SENT, job.id, i))
            delivered.add(pending(ctx, ACTION_DELIVERED, job.id, i))
        }
        sm.sendMultipartTextMessage(job.to, null, parts, sent, delivered)
    }

    @Suppress("DEPRECATION")
    fun sendRaw(ctx: Context, to: String, text: String) {
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sm.sendMultipartTextMessage(to, null, sm.divideMessage(text), null, null)
    }

    private fun pending(ctx: Context, action: String, id: String, part: Int): PendingIntent {
        val i = Intent(action).setClass(ctx, SmsResultReceiver::class.java).putExtra("id", id)
        return PendingIntent.getBroadcast(ctx, (id.hashCode() * 31 + part) * 2 + (if (action == ACTION_SENT) 0 else 1),
            i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        JobStore.init(ctx)
        val job = JobStore.get(intent.getStringExtra("id") ?: return) ?: return
        when (intent.action) {
            SmsSender.ACTION_SENT ->
                if (resultCode == android.app.Activity.RESULT_OK) {
                    if (job.partsSent.incrementAndGet() >= job.partsTotal.get() && job.status != Status.FAILED) job.set(Status.SENT)
                } else {
                    job.set(Status.FAILED, sendError(resultCode, intent.getIntExtra("errorCode", -1)))
                }
            SmsSender.ACTION_DELIVERED ->
                if (resultCode == android.app.Activity.RESULT_OK) {
                    if (job.partsDelivered.incrementAndGet() >= job.partsTotal.get() && job.status != Status.FAILED) job.set(Status.DELIVERED)
                } else job.set(Status.FAILED, "DELIVERY_FAILED (recipient did not receive the SMS)")
        }
        JobStore.save()
    }
}

fun sendError(code: Int, radioCode: Int): String {
    val name = when (code) {
        1 -> "RESULT_ERROR_GENERIC_FAILURE (Generic failure cause)"
        2 -> "RESULT_ERROR_RADIO_OFF (Failed because radio was explicitly turned off)"
        3 -> "RESULT_ERROR_NULL_PDU (Failed because no pdu provided)"
        4 -> "RESULT_ERROR_NO_SERVICE (Failed because service is currently unavailable)"
        5 -> "RESULT_ERROR_LIMIT_EXCEEDED (Failed because we reached the sending queue limit)"
        6 -> "RESULT_ERROR_FDN_CHECK_FAILURE (Failed because FDN is enabled)"
        7 -> "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED (Failed because the short code is not allowed)"
        8 -> "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED (Failed because the short code is never allowed)"
        100 -> "RESULT_RIL_RADIO_NOT_AVAILABLE (Radio not available)"
        101 -> "RESULT_RIL_SMS_SEND_FAIL_RETRY (The network asked to retry the SMS)"
        102 -> "RESULT_RIL_NETWORK_REJECT (The network rejected the SMS)"
        103 -> "RESULT_RIL_INVALID_STATE (Invalid state)"
        104 -> "RESULT_RIL_INVALID_ARGUMENTS (Invalid arguments)"
        105 -> "RESULT_RIL_NO_MEMORY (No memory)"
        106 -> "RESULT_RIL_REQUEST_RATE_LIMITED (Rate limited)"
        107 -> "RESULT_RIL_INVALID_SMS_FORMAT (Invalid SMS format)"
        108 -> "RESULT_RIL_SYSTEM_ERR (System error)"
        109 -> "RESULT_RIL_ENCODING_ERR (Encoding error)"
        110 -> "RESULT_RIL_INVALID_SMSC_ADDRESS (Invalid SMSC address)"
        111 -> "RESULT_RIL_MODEM_ERR (Modem error)"
        112 -> "RESULT_RIL_NETWORK_ERR (Network error: the operator refused the SMS, often no credit or no service on the SIM)"
        113 -> "RESULT_RIL_INTERNAL_ERR (Internal error)"
        114 -> "RESULT_RIL_REQUEST_NOT_SUPPORTED (Request not supported)"
        115 -> "RESULT_RIL_INVALID_MODEM_STATE (Invalid modem state)"
        116 -> "RESULT_RIL_NETWORK_NOT_READY (Network not ready)"
        117 -> "RESULT_RIL_OPERATION_NOT_ALLOWED (Operation not allowed)"
        118 -> "RESULT_RIL_NO_RESOURCES (No resources)"
        119 -> "RESULT_RIL_CANCELLED (Cancelled)"
        120 -> "RESULT_RIL_SIM_ABSENT (SIM absent)"
        121 -> "RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED (SMS not allowed during a call)"
        122 -> "RESULT_RIL_ACCESS_BARRED (Access barred)"
        123 -> "RESULT_RIL_BLOCKED_DUE_TO_CALL (Blocked due to a call)"
        else -> "RESULT_ERROR_UNKNOWN (code $code)"
    }
    return if (radioCode >= 0) "$name, code radio $radioCode" else name
}
