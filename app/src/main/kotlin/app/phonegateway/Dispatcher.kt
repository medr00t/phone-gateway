package app.phonegateway

import android.content.Context
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

/** One worker thread: jobs run one at a time, with a pause between them (carriers block bursts). */
object Dispatcher {
    private val queue = LinkedBlockingQueue<Job>()
    @Volatile private var worker: Thread? = null

    @Synchronized fun start(ctx: Context) {
        if (worker?.isAlive == true) return
        val app = ctx.applicationContext
        JobStore.queued().forEach { if (!queue.contains(it)) queue.add(it) }   // resume after a restart
        worker = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val job = try { queue.take() } catch (_: InterruptedException) { break }
                try {
                    when (job.kind) {
                        Kind.SMS -> SmsSender.send(app, job)
                        Kind.CALL -> CallRunner.run(app, job)
                    }
                } catch (e: Exception) {
                    job.set(Status.FAILED, e.message ?: e.javaClass.simpleName)
                }
                JobStore.save()
                try { Thread.sleep(job.delayMs) } catch (_: InterruptedException) { break }
            }
        }.also { it.isDaemon = true; it.name = "gateway-dispatcher"; it.start() }
    }

    @Synchronized fun stop() { worker?.interrupt(); worker = null }

    fun pending(): Int = queue.size

    fun enqueueSms(items: List<BatchItem>, delayMs: Long): String {
        val batchId = UUID.randomUUID().toString().take(8)
        for (it in items) {
            val j = Job(UUID.randomUUID().toString().take(12), Kind.SMS, it.to, it.text, it.ref, batchId, delayMs)
            JobStore.add(j); queue.add(j)
        }
        return batchId
    }

    /** SMSGate-compatible send: the caller's own message id is kept (one receiver) or used as group id (several). */
    fun enqueueSmsAs(id: String, numbers: List<String>, text: String, delayMs: Long = 2000) {
        numbers.forEachIndexed { i, n ->
            val single = numbers.size == 1
            val j = Job(if (single) id else "$id-${i + 1}", Kind.SMS, n, text, null, null, delayMs, groupId = if (single) null else id)
            JobStore.add(j); queue.add(j)
        }
    }

    /** probeText: optional SMS to send when the tracker does not text back by itself (e.g. "STATUS#"). */
    fun enqueueCall(to: String, ref: String?, hangupAfterSec: Int, waitSmsSec: Int = 30, probeText: String = ""): Job {
        val j = Job(UUID.randomUUID().toString().take(12), Kind.CALL, to, probeText, ref, null, 3000, hangupAfterSec, waitSmsSec)
        JobStore.add(j); queue.add(j)
        return j
    }
}
