package app.phonegateway

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One valid message of a batch. */
data class BatchItem(val to: String, val text: String, val ref: String?)

class ParsedBatch(val items: List<BatchItem>, val delayMs: Long?, val errors: List<String>)

/**
 * Reads a JSON batch of SMS, each with its own receiver and its own text.
 *
 * Accepted shapes:
 *   [ {"to":"+212600000001","text":"Hello A"}, {"to":"0600000002","text":"Hello B"} ]
 *   { "delayMs": 3000, "messages": [ ... ] }
 * Per message: receiver in "to" (also "receiver"/"number"/"phone"), text in "text"
 * (also "message"/"body"), optional "ref". "to" may be an array of numbers to send
 * the same text to several receivers.
 */
object BatchParser {
    const val MAX_ITEMS = 1000
    const val MAX_TEXT = 1600           // 10 concatenated SMS
    private val NUMBER = Regex("^\\+?\\d{6,15}$")

    fun normalizeNumber(raw: String): String? {
        val n = raw.trim().replace(Regex("[\\s().-]"), "")
        return if (NUMBER.matches(n)) n else null
    }

    fun parse(json: String): ParsedBatch {
        val errors = ArrayList<String>()
        val root: Any = try {
            org.json.JSONTokener(json.trim()).nextValue()
        } catch (e: JSONException) {
            return ParsedBatch(emptyList(), null, listOf("JSON invalide: ${e.message}"))
        }
        var delay: Long? = null
        val arr: JSONArray = when (root) {
            is JSONArray -> root
            is JSONObject -> {
                if (root.has("delayMs")) delay = root.optLong("delayMs", -1).takeIf { it in 0..600_000 }
                root.optJSONArray("messages")
                    ?: return ParsedBatch(emptyList(), delay, listOf("Il manque la liste \"messages\""))
            }
            else -> return ParsedBatch(emptyList(), null, listOf("Le fichier doit contenir une liste ou un objet avec \"messages\""))
        }
        if (arr.length() > MAX_ITEMS) {
            return ParsedBatch(emptyList(), delay, listOf("Trop de messages (${arr.length()}), maximum $MAX_ITEMS par lot"))
        }

        val items = ArrayList<BatchItem>()
        for (i in 0 until arr.length()) {
            val label = "Message ${i + 1}"
            val o = arr.optJSONObject(i)
            if (o == null) { errors.add("$label: doit être un objet"); continue }
            val text = firstString(o, "text", "message", "body")
            if (text.isNullOrEmpty()) { errors.add("$label: texte manquant"); continue }
            if (text.length > MAX_TEXT) { errors.add("$label: texte trop long (${text.length} > $MAX_TEXT)"); continue }
            val ref = if (o.has("ref")) o.opt("ref")?.toString() else null

            val receivers = ArrayList<String>()
            val toKey = listOf("to", "receiver", "number", "phone").firstOrNull { o.has(it) }
            when (val v = if (toKey == null) null else o.get(toKey)) {
                is JSONArray -> for (k in 0 until v.length()) receivers.add(v.optString(k))
                null -> {}
                else -> receivers.add(v.toString())
            }
            if (receivers.isEmpty()) { errors.add("$label: destinataire manquant"); continue }
            for (r in receivers) {
                val n = normalizeNumber(r)
                if (n == null) errors.add("$label: numéro invalide \"$r\"") else items.add(BatchItem(n, text, ref))
            }
        }
        return ParsedBatch(items, delay, errors)
    }

    private fun firstString(o: JSONObject, vararg keys: String): String? {
        for (k in keys) if (o.has(k) && !o.isNull(k)) return o.get(k).toString()
        return null
    }
}
