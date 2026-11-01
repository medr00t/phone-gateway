package app.phonegateway

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

enum class Kind { SMS, CALL }

enum class Status { QUEUED, SENDING, SENT, DELIVERED, FAILED, CALLING, DONE }

/** One thing to do: send one SMS or place one call. */
class Job(
    val id: String,
    val kind: Kind,
    val to: String,
    val text: String,
    val ref: String? = null,          // caller's own reference, echoed back
    val batchId: String? = null,
    val delayMs: Long = 2000,         // pause after this job, before the next one
    val hangupAfterSec: Int = 25,     // calls only
    val waitSmsSec: Int = 30,         // calls only: how long to wait for the tracker's SMS reply
    val createdAt: Long = System.currentTimeMillis(),
) {
    @Volatile var status: Status = Status.QUEUED
    @Volatile var error: String? = null
    @Volatile var updatedAt: Long = createdAt
    @Volatile var durationSec: Int = -1       // calls: seconds the line was connected (0 = never answered)
    @Volatile var connected: Boolean? = null  // calls: true when somebody/something picked up
    @Volatile var verdict: String? = null     // calls: VOICEMAIL / ALIVE / OFFLINE
    val partsTotal = AtomicInteger(0)
    val partsSent = AtomicInteger(0)
    val partsDelivered = AtomicInteger(0)

    fun set(s: Status, err: String? = null) {
        status = s
        if (err != null) error = err
        updatedAt = System.currentTimeMillis()
        Notifier.job(this)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("to", to)
        if (kind == Kind.SMS) put("text", text)
        if (ref != null) put("ref", ref)
        if (batchId != null) put("batchId", batchId)
        put("status", status.name)
        if (error != null) put("error", error)
        if (kind == Kind.CALL && durationSec >= 0) {
            put("durationSec", durationSec)
            put("connected", connected ?: false)
            put("outcome", if (connected == true) "PICKED_UP" else "NO_ANSWER")
            if (verdict != null) put("verdict", verdict)
        }
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Job = Job(
            id = o.getString("id"),
            kind = Kind.valueOf(o.getString("kind")),
            to = o.getString("to"),
            text = o.optString("text", ""),
            ref = if (o.has("ref")) o.getString("ref") else null,
            batchId = if (o.has("batchId")) o.getString("batchId") else null,
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        ).also {
            it.status = Status.valueOf(o.optString("status", "QUEUED"))
            it.error = if (o.has("error")) o.getString("error") else null
            it.updatedAt = o.optLong("updatedAt", it.createdAt)
            if (o.has("durationSec")) {
                it.durationSec = o.getInt("durationSec")
                it.connected = o.optBoolean("connected", false)
                it.verdict = if (o.has("verdict")) o.getString("verdict") else null
            }
        }
    }
}
