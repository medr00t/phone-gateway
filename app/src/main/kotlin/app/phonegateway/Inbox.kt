package app.phonegateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONObject

class InboxMessage(val from: String, val text: String, val at: Long) {
    fun toJson(): JSONObject = JSONObject().put("from", from).put("text", text).put("at", at)
}

object Inbox {
    private const val MAX = 2000
    private const val DUPLICATE_WINDOW_MS = 5000L
    private val items = ArrayList<InboxMessage>()
    private var file: java.io.File? = null

    @Synchronized fun init(ctx: Context) {
        if (file != null) return
        file = java.io.File(ctx.filesDir, "inbox.json").also { f ->
            if (f.exists()) try {
                val arr = org.json.JSONArray(f.readText())
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); items.add(InboxMessage(o.getString("from"), o.getString("text"), o.getLong("at"))) }
            } catch (_: Exception) {  }
        }
    }

    @Synchronized fun add(m: InboxMessage): Boolean {
        val last = items.lastOrNull()
        if (last != null && last.from == m.from && last.text == m.text && m.at - last.at < DUPLICATE_WINDOW_MS) return false
        items.add(m)
        while (items.size > MAX) items.removeAt(0)
        save()
        return true
    }

    fun receive(ctx: Context, intent: Intent) {
        JobStore.init(ctx)
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val from = parts[0].originatingAddress ?: return
        val m = InboxMessage(from, parts.joinToString("") { it.messageBody.orEmpty() }, System.currentTimeMillis())
        if (!add(m)) return
        val st = Settings(ctx)
        if (st.webhookUrl.isNotEmpty()) Notifier.sms(st.webhookUrl, st.token, m)
    }

    @Synchronized fun all(): List<InboxMessage> = items.toList()

    @Synchronized fun purge(maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        val before = items.size
        items.removeAll { it.at < now - maxAgeMs }
        if (before != items.size) save()
        return before - items.size
    }

    @Synchronized fun since(at: Long, from: String?): List<InboxMessage> {
        val tail = from?.filter { it.isDigit() }?.takeLast(8)
        return items.filter { it.at > at && (tail == null || it.from.filter { c -> c.isDigit() }.endsWith(tail)) }
    }

    private fun save() {
        val f = file ?: return
        val arr = org.json.JSONArray(); items.forEach { arr.put(it.toJson()) }
        try { f.writeText(arr.toString()) } catch (_: Exception) { }
    }
}

class SmsInboxReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Inbox.receive(ctx, intent)
}
