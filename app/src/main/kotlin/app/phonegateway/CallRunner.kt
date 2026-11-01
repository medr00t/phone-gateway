package app.phonegateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

/**
 * Places a call, hangs up after [Job.hangupAfterSec], then reads the phone's call log:
 * a duration above 0 means the line was picked up (a person or a voicemail); 0 means it was
 * rejected / never answered. A GPS tracker rejects the call and answers by SMS, so
 * "durationSec = 0" followed by an incoming SMS from that number = tracker alive.
 */
object CallRunner {
    @Suppress("DEPRECATION", "MissingPermission")
    fun run(ctx: Context, job: Job) {
        for (p in listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)) {
            if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                job.set(Status.FAILED, "permission manquante: ${p.substringAfterLast('.')}")
                return
            }
        }
        val telecom = ctx.getSystemService(TelecomManager::class.java)
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        if (tm.callState != TelephonyManager.CALL_STATE_IDLE) {
            job.set(Status.FAILED, "le téléphone est déjà en appel")
            return
        }
        job.set(Status.CALLING)
        val start = System.currentTimeMillis()
        telecom.placeCall(Uri.fromParts("tel", job.to, null), Bundle())

        // wait for the line to open, then for it to close by itself or for the hang-up delay
        val begin = System.currentTimeMillis()
        while (tm.callState == TelephonyManager.CALL_STATE_IDLE && System.currentTimeMillis() - begin < 5000) Thread.sleep(250)
        val deadline = System.currentTimeMillis() + job.hangupAfterSec * 1000L
        while (tm.callState != TelephonyManager.CALL_STATE_IDLE && System.currentTimeMillis() < deadline) Thread.sleep(500)
        if (tm.callState != TelephonyManager.CALL_STATE_IDLE) {
            try { telecom.endCall() } catch (_: SecurityException) { }
        }
        // the log entry is written a moment after the line closes
        Thread.sleep(3000)

        val last8 = job.to.filter { it.isDigit() }.takeLast(8)
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.DATE),
            "${CallLog.Calls.DATE} >= ?", arrayOf((start - 2000).toString()), "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(0).orEmpty().filter { it.isDigit() }.endsWith(last8)) {
                    job.durationSec = c.getInt(1)
                    job.connected = job.durationSec > 0
                    // for a tracker: rejected (0 s) = alive, then it answers by SMS; picked up = voicemail or a person
                    job.set(Status.DONE)
                    return
                }
            }
        }
        job.set(Status.FAILED, "appel introuvable dans le journal d'appels")
    }
}
