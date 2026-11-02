package app.phonegateway

import org.json.JSONArray
import org.json.JSONObject

/** The answer shape of the SMSGate "local server" API (POST /message, GET /message/{id}). */
object SmsCompat {
    fun state(s: Status): String = when (s) {
        Status.QUEUED -> "Pending"
        Status.SENDING -> "Processed"
        Status.SENT -> "Sent"
        Status.DELIVERED -> "Delivered"
        Status.FAILED -> "Failed"
        else -> "Pending"
    }

    /** One state for a message with several receivers. */
    fun aggregate(statuses: List<Status>): String = when {
        statuses.isEmpty() -> "Pending"
        statuses.any { it == Status.FAILED } -> "Failed"
        statuses.all { it == Status.DELIVERED } -> "Delivered"
        statuses.all { it == Status.SENT || it == Status.DELIVERED } -> "Sent"
        statuses.any { it == Status.SENDING || it == Status.SENT || it == Status.DELIVERED } -> "Processed"
        else -> "Pending"
    }

    fun response(id: String, jobs: List<Job>): JSONObject {
        val rec = JSONArray()
        jobs.forEach { j ->
            rec.put(JSONObject().put("phoneNumber", j.to).put("state", state(j.status)).apply { if (j.error != null) put("error", j.error) })
        }
        return JSONObject().put("id", id).put("state", aggregate(jobs.map { it.status })).put("recipients", rec)
    }
}
