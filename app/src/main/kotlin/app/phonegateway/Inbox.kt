package app.phonegateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONObject

class InboxMessage(val from: String, val text: String, val at: Long) {
    fun toJson(): JSONObject = JSONObject().put("from", from).put("text", text).put("at", at)
}

/** Last received SMS, in memory (the caller polls /api/inbox, or gets them by webhook). */
object Inbox {
    private const val MAX = 500
    private val items = ArrayList<InboxMessage>()
    @Synchronized fun add(m: InboxMessage) { items.add(m); while (items.size > MAX) items.removeAt(0) }
    @Synchronized fun since(at: Long, from: String?): List<InboxMessage> {
        val tail = from?.filter { it.isDigit() }?.takeLast(8)
        return items.filter { it.at > at && (tail == null || it.from.filter { c -> c.isDigit() }.endsWith(tail)) }
    }
}

class SmsInboxReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val from = parts[0].originatingAddress ?: return
        val m = InboxMessage(from, parts.joinToString("") { it.messageBody.orEmpty() }, System.currentTimeMillis())
        Inbox.add(m)
        val st = Settings(ctx)
        if (st.webhookUrl.isNotEmpty()) Notifier.sms(st.webhookUrl, st.token, m)
    }
}
