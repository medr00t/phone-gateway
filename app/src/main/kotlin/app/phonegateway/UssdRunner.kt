package app.phonegateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object UssdRunner {
    class Result(val ok: Boolean, val response: String?, val error: String?)

    private const val TIMEOUT_SEC = 40L

    fun runJob(ctx: Context, job: Job) {
        job.set(Status.SENDING)
        job.usedSim = SimChoice.resolve(job.sim, Settings(ctx).ussdSim)
        val r = run(ctx, job.text, job.usedSim)
        if (r.ok) {
            job.response = r.response
            job.set(Status.DONE)
        } else {
            job.set(Status.FAILED, r.error)
        }
    }

    @Synchronized
    @Suppress("MissingPermission")
    fun run(ctx: Context, code: String, sim: Int? = null): Result {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return Result(false, null, "permission manquante: CALL_PHONE")
        }
        var tm = ctx.getSystemService(TelephonyManager::class.java)
        if (sim != null) {
            val info = ctx.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfoForSimSlotIndex(sim - 1)
                ?: return Result(false, null, "SIM $sim introuvable")
            tm = tm.createForSubscriptionId(info.subscriptionId)
        }
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
