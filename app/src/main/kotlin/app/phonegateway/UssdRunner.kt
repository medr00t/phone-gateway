package app.phonegateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object UssdRunner {
    class Result(val ok: Boolean, val response: String?, val error: String?)

    private const val TIMEOUT_SEC = 40L
    private const val DIAL_WAIT_MS = 8_000L

    fun runJob(ctx: Context, job: Job) {
        job.set(Status.SENDING)
        val r = run(ctx, job.text)
        if (r.ok) {
            job.response = r.response
            job.set(Status.DONE)
        } else {
            job.set(Status.FAILED, r.error)
        }
    }

    @Suppress("MissingPermission")
    fun dialJob(ctx: Context, job: Job) {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            job.set(Status.FAILED, "permission manquante: CALL_PHONE")
            return
        }
        job.set(Status.SENDING)
        try {
            ctx.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", job.text, null), Bundle())
        } catch (e: SecurityException) {
            job.set(Status.FAILED, "permission refusée")
            return
        }
        Thread.sleep(DIAL_WAIT_MS)
        job.response = "code composé, la réponse de l'opérateur s'affiche sur le téléphone"
        job.set(Status.DONE)
    }

    @Synchronized
    @Suppress("MissingPermission")
    fun run(ctx: Context, code: String): Result {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return Result(false, null, "permission manquante: CALL_PHONE")
        }
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val done = CountDownLatch(1)
        var result = Result(false, null, "pas de réponse de l'opérateur")
        try {
            tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) {
                    result = Result(true, response.toString(), null)
                    done.countDown()
                }

                override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) {
                    result = Result(false, null, "échec USSD (code $failureCode)")
                    done.countDown()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            return Result(false, null, "permission refusée")
        }
        done.await(TIMEOUT_SEC, TimeUnit.SECONDS)
        return result
    }
}
