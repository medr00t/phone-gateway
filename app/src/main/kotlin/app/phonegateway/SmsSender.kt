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

    /** Fire-and-forget SMS (no job): used by a call's probe. */
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

/** Android reports each SMS part here: sent (or why not) and, if the carrier tells us, delivered. */
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
                } else job.set(Status.FAILED, "non livré (le destinataire n'a pas reçu le SMS)")
        }
        JobStore.save()
    }
}

/** Android's SmsManager result codes, named. */
fun sendError(code: Int, radioCode: Int): String {
    val name = when (code) {
        1 -> "GENERIC_FAILURE (échec générique, souvent pas de crédit/forfait ou SIM bloquée)"
        2 -> "RADIO_OFF (mode avion ou radio coupée)"
        3 -> "NULL_PDU (message invalide)"
        4 -> "NO_SERVICE (pas de réseau)"
        5 -> "LIMIT_EXCEEDED (limite d'envoi Android atteinte)"
        6 -> "FDN_CHECK_FAILURE (numéro interdit par la SIM)"
        7 -> "SHORT_CODE_NOT_ALLOWED"
        8 -> "SHORT_CODE_NEVER_ALLOWED"
        else -> "code $code"
    }
    return if (radioCode >= 0) "$name, code radio $radioCode" else name
}
