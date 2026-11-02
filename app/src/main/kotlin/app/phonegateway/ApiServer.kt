package app.phonegateway

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/**
 * HTTP API of the phone. Every call needs the token: header "Authorization: Bearer <token>".
 *
 *   SMSGate-compatible: POST /message, GET /message/{id}  (Basic auth, password = token)
 *   GET  /api/status
 *   POST /api/sms          {"to":"+2126...","text":"...","ref":"x"}
 *   POST /api/sms/batch    [ {"to","text"}, ... ]  or  {"delayMs":3000,"messages":[...]}
 *   POST /api/call         {"to":"+2126...","hangupAfterSec":25,"waitSmsSec":30,"probeText":"STATUS#","ref":"x"}
 *   GET  /api/jobs?batch=&status=&limit=       GET /api/jobs/{id}
 *   GET  /api/inbox?since=<ms>&from=<number>
 *   GET|POST /api/settings  {"jobWebhookUrl":"http://…","webhookUrl":"http://…"}
 */
class ApiServer(private val settings: Settings, port: Int) : NanoHTTPD(port) {
    private companion object { const val MAX_BODY = 2_000_000 }


    // CORS so a web page (the test console, your own back-office) can call the phone directly.
    // Safe: every real call still needs the secret token, which a page cannot guess.
    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) return cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        return cors(try {
            if (!authorized(session)) return cors(json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "token invalide")))
            route(session)
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "erreur"))
        })
    }

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        return r
    }

    private fun route(s: IHTTPSession): Response {
        val path = s.uri.trimEnd('/')
        val get = s.method == Method.GET
        val post = s.method == Method.POST
        return when {
            get && path == "/api/status" -> json(Response.Status.OK, JSONObject()
                .put("app", "phone-gateway").put("queued", Dispatcher.pending())
                .put("jobs", JobStore.all().size).put("now", System.currentTimeMillis()))

            post && path == "/api/sms" -> {
                val o = parseObject(s) ?: return bad("JSON invalide")
                val r = BatchParser.parse(JSONArray().put(o).toString())
                accept(r)
            }
            post && path == "/api/sms/batch" -> accept(BatchParser.parse(body(s)))

            post && path == "/api/call" -> {
                val o = parseObject(s) ?: return bad("JSON invalide")
                val to = BatchParser.normalizeNumber(o.optString("to", "")) ?: return bad("numéro invalide")
                val hang = o.optInt("hangupAfterSec", 25).coerceIn(5, 60)
                val wait = o.optInt("waitSmsSec", 30).coerceIn(0, 120)
                val probe = o.optString("probeText", "").take(160)
                val j = Dispatcher.enqueueCall(to, if (o.has("ref")) o.get("ref").toString() else null, hang, wait, probe)
                json(Response.Status.ACCEPTED, JSONObject().put("id", j.id))
            }

            get && path == "/api/jobs" -> {
                val p = s.parameters
                val batch = p["batch"]?.firstOrNull()
                val status = p["status"]?.firstOrNull()?.uppercase()
                val limit = (p["limit"]?.firstOrNull()?.toIntOrNull() ?: 200).coerceIn(1, 2000)
                val list = JobStore.all().filter { (batch == null || it.batchId == batch) && (status == null || it.status.name == status) }.takeLast(limit)
                val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
                json(Response.Status.OK, JSONObject().put("jobs", arr))
            }
            get && path.startsWith("/api/jobs/") -> {
                val j = JobStore.get(path.removePrefix("/api/jobs/")) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "introuvable"))
                json(Response.Status.OK, j.toJson())
            }

            post && path == "/message" -> compatSend(s)
            get && path.startsWith("/message/") -> {
                val id = path.removePrefix("/message/")
                val jobs = JobStore.byMessage(id)
                if (jobs.isEmpty()) json(Response.Status.NOT_FOUND, JSONObject().put("error", "message inconnu"))
                else json(Response.Status.OK, SmsCompat.response(id, jobs))
            }

            get && path == "/api/settings" -> json(Response.Status.OK, JSONObject()
                .put("jobWebhookUrl", settings.jobWebhookUrl).put("webhookUrl", settings.webhookUrl))
            post && path == "/api/settings" -> {
                val o = parseObject(s) ?: return bad("JSON invalide")
                for (k in listOf("jobWebhookUrl", "webhookUrl")) {
                    if (!o.has(k)) continue
                    val v = o.optString(k, "").trim()
                    if (v.isNotEmpty() && !(v.startsWith("http://") || v.startsWith("https://"))) return bad("$k doit commencer par http:// ou https://")
                    if (k == "jobWebhookUrl") settings.jobWebhookUrl = v else settings.webhookUrl = v
                }
                json(Response.Status.OK, JSONObject().put("jobWebhookUrl", settings.jobWebhookUrl).put("webhookUrl", settings.webhookUrl))
            }

            get && path == "/api/inbox" -> {
                val since = s.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
                val arr = JSONArray(); Inbox.since(since, s.parameters["from"]?.firstOrNull()).forEach { arr.put(it.toJson()) }
                json(Response.Status.OK, JSONObject().put("messages", arr))
            }
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "route inconnue"))
        }
    }

    // SMSGate "local server" API, so a client written for it (e.g. the hospital back-end) works unchanged.
    private fun compatSend(s: IHTTPSession): Response {
        val o = parseObject(s) ?: return bad("JSON invalide")
        val text = o.optJSONObject("textMessage")?.optString("text", "") ?: ""
        if (text.isEmpty()) return bad("textMessage.text manquant")
        if (text.length > BatchParser.MAX_TEXT) return bad("texte trop long")
        val arr = o.optJSONArray("phoneNumbers") ?: return bad("phoneNumbers manquant")
        if (arr.length() == 0 || arr.length() > 100) return bad("phoneNumbers: 1 à 100 numéros")
        val numbers = (0 until arr.length()).map { BatchParser.normalizeNumber(arr.optString(it)) ?: return bad("numéro invalide: ${arr.optString(it)}") }
        val id = o.optString("id", "").ifEmpty { java.util.UUID.randomUUID().toString().take(16) }
        if (!Regex("^[A-Za-z0-9_.:#-]{1,100}$").matches(id)) return bad("id invalide")
        if (JobStore.byMessage(id).isNotEmpty()) return json(Response.Status.CONFLICT, JSONObject().put("error", "id déjà utilisé"))
        Dispatcher.enqueueSmsAs(id, numbers, text)
        return json(Response.Status.ACCEPTED, SmsCompat.response(id, JobStore.byMessage(id)))
    }

    private fun accept(r: ParsedBatch): Response {
        val errs = JSONArray(); r.errors.forEach { errs.put(it) }
        // a caller-chosen id that already exists is refused for that message only
        val fresh = r.items.filter { it.id == null || JobStore.byMessage(it.id).isEmpty() }
        r.items.filter { it !in fresh }.forEach { errs.put("id déjà utilisé: ${it.id}") }
        if (fresh.isEmpty()) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "aucun message valide").put("errors", errs))
        val (batchId, jobs) = Dispatcher.enqueueSms(fresh, r.delayMs ?: 2000)
        val msgs = JSONArray()
        jobs.forEach { j ->
            msgs.put(JSONObject().put("id", j.id).put("to", j.to).put("status", j.status.name).apply { if (j.ref != null) put("ref", j.ref) })
        }
        return json(Response.Status.ACCEPTED, JSONObject().put("batchId", batchId).put("accepted", jobs.size).put("messages", msgs).put("errors", errs))
    }

    private fun authorized(s: IHTTPSession): Boolean {
        val h = s.headers["authorization"] ?: ""
        val given = when {
            h.startsWith("Bearer ", true) -> h.substring(7).trim()
            // SMSGate-compatible clients send Basic auth: any user name, the token as password
            h.startsWith("Basic ", true) -> try {
                String(java.util.Base64.getDecoder().decode(h.substring(6).trim()), Charsets.UTF_8).substringAfter(':', "")
            } catch (_: IllegalArgumentException) { "" }
            else -> ""
        }
        return MessageDigest.isEqual(given.toByteArray(), settings.token.toByteArray())
    }

    // Read the raw bytes and decode them as UTF-8 ourselves: NanoHTTPD decodes as US-ASCII when the
    // client sends no charset, which turned every accent of a French SMS into "��".
    private fun body(s: IHTTPSession): String {
        val len = s.headers["content-length"]?.toIntOrNull() ?: 0
        if (len <= 0) return ""
        if (len > MAX_BODY) throw IllegalArgumentException("corps trop gros (max $MAX_BODY octets)")
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = s.inputStream.read(buf, off, len - off)
            if (n < 0) break
            off += n
        }
        return String(buf, 0, off, Charsets.UTF_8)
    }

    private fun parseObject(s: IHTTPSession): JSONObject? = try { JSONObject(body(s)) } catch (_: JSONException) { null }

    private fun bad(msg: String) = json(Response.Status.BAD_REQUEST, JSONObject().put("error", msg))

    private fun json(st: Response.IStatus, o: JSONObject): Response =
        newFixedLengthResponse(st, "application/json; charset=utf-8", o.toString())
}
