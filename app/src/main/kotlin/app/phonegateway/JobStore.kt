package app.phonegateway

import android.content.Context
import org.json.JSONArray
import java.io.File

/** All jobs, newest last. Kept in memory and saved to a file so a restart keeps the history. */
object JobStore {
    private const val MAX = 5000
    private val jobs = LinkedHashMap<String, Job>()
    private var file: File? = null

    @Synchronized fun init(ctx: Context) {
        if (file != null) return
        Notifier.init(ctx)
        file = File(ctx.filesDir, "jobs.json").also { f ->
            if (f.exists()) try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val j = Job.fromJson(arr.getJSONObject(i))
                    // A job that was running when the app died cannot be trusted any more.
                    if (j.status == Status.SENDING || j.status == Status.CALLING) j.set(Status.FAILED, "interrompu (redémarrage)")
                    jobs[j.id] = j
                }
            } catch (_: Exception) { /* unreadable history: start clean */ }
        }
    }

    @Synchronized fun add(j: Job) {
        jobs[j.id] = j
        while (jobs.size > MAX) jobs.remove(jobs.keys.first())
        save()
    }

    @Synchronized fun get(id: String): Job? = jobs[id]

    /** The jobs of one SMSGate-style message: the job with that id, or the group sharing it. */
    @Synchronized fun byMessage(id: String): List<Job> = jobs.values.filter { it.id == id || it.groupId == id }

    @Synchronized fun all(): List<Job> = jobs.values.toList()

    @Synchronized fun save() {
        val f = file ?: return
        val arr = JSONArray()
        jobs.values.forEach { arr.put(it.toJson()) }
        try { f.writeText(arr.toString()) } catch (_: Exception) { }
    }

    @Synchronized fun queued(): List<Job> = jobs.values.filter { it.status == Status.QUEUED }
}
