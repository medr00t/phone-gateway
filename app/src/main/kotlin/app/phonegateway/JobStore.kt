package app.phonegateway

import android.content.Context
import org.json.JSONArray
import java.io.File

object JobStore {
    private const val MAX = 5000
    private val jobs = LinkedHashMap<String, Job>()
    private var file: File? = null

    @Synchronized fun init(ctx: Context) {
        if (file != null) return
        Notifier.init(ctx)
        Inbox.init(ctx)
        file = File(ctx.filesDir, "jobs.json").also { f ->
            if (f.exists()) try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val j = Job.fromJson(arr.getJSONObject(i))

                    if (j.status == Status.SENDING || j.status == Status.CALLING) j.set(Status.FAILED, "interrompu (redémarrage)")
                    jobs[j.id] = j
                }
            } catch (_: Exception) {  }
        }
    }

    @Synchronized fun add(j: Job) {
        jobs[j.id] = j
        while (jobs.size > MAX) jobs.remove(jobs.keys.first())
        save()
    }

    @Synchronized fun get(id: String): Job? = jobs[id]

    @Synchronized fun byMessage(id: String): List<Job> = jobs.values.filter { it.id == id || it.groupId == id }

    @Synchronized fun all(): List<Job> = jobs.values.toList()

    @Synchronized fun save() {
        val f = file ?: return
        val arr = JSONArray()
        jobs.values.forEach { arr.put(it.toJson()) }
        try { f.writeText(arr.toString()) } catch (_: Exception) { }
    }

    @Synchronized fun purge(maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        val before = jobs.size
        jobs.values.removeAll { it.createdAt < now - maxAgeMs && it.status != Status.QUEUED && it.status != Status.SENDING && it.status != Status.CALLING }
        val removed = before - jobs.size
        if (removed > 0) save()
        return removed
    }

    @Synchronized fun queued(): List<Job> = jobs.values.filter { it.status == Status.QUEUED }
}
