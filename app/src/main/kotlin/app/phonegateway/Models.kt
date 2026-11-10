package app.phonegateway

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

enum class Kind { SMS, CALL, USSD }

enum class Status { QUEUED, SENDING, SENT, DELIVERED, FAILED, CALLING, DONE }

class Job(
    val id: String,
    val kind: Kind,
    val to: String,
    val text: String,
    val ref: String? = null,
    val batchId: String? = null,
    val delayMs: Long = 2000,
    val hangupAfterSec: Int = 25,
    val waitSmsSec: Int = 30,
    val groupId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sim: Int? = null,
) {
    @Volatile var status: Status = Status.QUEUED
    @Volatile var error: String? = null
    @Volatile var updatedAt: Long = createdAt
    @Volatile var durationSec: Int = -1
    @Volatile var connected: Boolean? = null
    @Volatile var verdict: String? = null
    @Volatile var ringSec: Int = -1
    @Volatile var endedBy: String? = null
    @Volatile var response: String? = null
    @Volatile var usedSim: Int? = null
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
        (usedSim ?: sim)?.let { put("sim", it) }
        if (groupId != null) put("groupId", groupId)
        put("status", status.name)
        if (error != null) put("error", error)
        if (response != null) put("response", response)
        if (kind == Kind.CALL && durationSec >= 0) {
            put("durationSec", durationSec)
            put("connected", connected ?: false)
            put("outcome", if (connected == true) "PICKED_UP" else "NO_ANSWER")
            if (verdict != null) put("verdict", verdict)
            if (ringSec >= 0) put("ringSec", ringSec)
            if (endedBy != null) put("endedBy", endedBy)
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
            groupId = if (o.has("groupId")) o.getString("groupId") else null,
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            sim = if (o.has("sim")) o.getInt("sim") else null,
        ).also {
            it.status = Status.valueOf(o.optString("status", "QUEUED"))
            it.error = if (o.has("error")) o.getString("error") else null
            it.response = if (o.has("response")) o.getString("response") else null
            it.usedSim = if (o.has("sim")) o.getInt("sim") else null
            it.updatedAt = o.optLong("updatedAt", it.createdAt)
            if (o.has("durationSec")) {
                it.durationSec = o.getInt("durationSec")
                it.connected = o.optBoolean("connected", false)
                it.verdict = if (o.has("verdict")) o.getString("verdict") else null
                it.ringSec = o.optInt("ringSec", -1)
                it.endedBy = if (o.has("endedBy")) o.getString("endedBy") else null
            }
        }
    }
}
