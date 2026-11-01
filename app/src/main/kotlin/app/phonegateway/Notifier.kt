package app.phonegateway

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Pushes events to the operator's server: one thread (order kept), 3 tries per event. */
object Notifier {
    private var settings: Settings? = null
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "gateway-notifier").apply { isDaemon = true } }
    private val FINAL = setOf(Status.SENT, Status.DELIVERED, Status.FAILED, Status.DONE)
    private val RETRY_DELAYS_MS = longArrayOf(2_000, 10_000, 30_000)

    fun init(ctx: Context) { settings = Settings(ctx.applicationContext) }

    fun job(job: Job) {
        if (job.status !in FINAL) return
        val s = settings ?: return
        if (s.jobWebhookUrl.isEmpty()) return
        post(s.jobWebhookUrl, s.token, job.toJson().put("event", "job.status").toString())
    }

    fun sms(url: String, token: String, m: InboxMessage) =
        post(url, token, m.toJson().put("event", "sms.received").toString())

    fun post(url: String, token: String, body: String) {
        pool.execute {
            for (attempt in 0..RETRY_DELAYS_MS.size) {
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"; c.doOutput = true
                    c.connectTimeout = 8000; c.readTimeout = 8000
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.setRequestProperty("X-Gateway-Token", token)
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val ok = c.responseCode in 200..299
                    c.disconnect()
                    if (ok) return@execute
                } catch (_: Exception) { }
                if (attempt < RETRY_DELAYS_MS.size) try { Thread.sleep(RETRY_DELAYS_MS[attempt]) } catch (_: InterruptedException) { return@execute }
            }
        }
    }
}
