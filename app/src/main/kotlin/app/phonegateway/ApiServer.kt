package app.phonegateway

import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/**
 * HTTP API of the phone. Every call needs the token: header "Authorization: Bearer <token>".
 *
 *   GET  /api/status
 *   POST /api/sms          {"to":"+2126...","text":"...","ref":"x"}
 *   POST /api/sms/batch    [ {"to","text"}, ... ]  or  {"delayMs":3000,"messages":[...]}
 *   POST /api/call         {"to":"+2126...","hangupAfterSec":25,"ref":"x"}
 *   GET  /api/jobs?batch=&status=&limit=       GET /api/jobs/{id}
 *   GET  /api/inbox?since=<ms>&from=<number>
 */
class ApiServer(private val settings: Settings, port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (!authorized(session)) return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "token invalide"))
            route(session)
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "erreur"))
        }
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
                val j = Dispatcher.enqueueCall(to, if (o.has("ref")) o.get("ref").toString() else null, hang)
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

            get && path == "/api/inbox" -> {
                val since = s.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
                val arr = JSONArray(); Inbox.since(since, s.parameters["from"]?.firstOrNull()).forEach { arr.put(it.toJson()) }
                json(Response.Status.OK, JSONObject().put("messages", arr))
            }
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "route inconnue"))
        }
    }

    private fun accept(r: ParsedBatch): Response {
        val errs = JSONArray(); r.errors.forEach { errs.put(it) }
        if (r.items.isEmpty()) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "aucun message valide").put("errors", errs))
        val batchId = Dispatcher.enqueueSms(r.items, r.delayMs ?: 2000)
        return json(Response.Status.ACCEPTED, JSONObject().put("batchId", batchId).put("accepted", r.items.size).put("errors", errs))
    }

    private fun authorized(s: IHTTPSession): Boolean {
        val h = s.headers["authorization"] ?: ""
        val given = if (h.startsWith("Bearer ", true)) h.substring(7).trim() else ""
        return MessageDigest.isEqual(given.toByteArray(), settings.token.toByteArray())
    }

    private fun body(s: IHTTPSession): String {
        val files = HashMap<String, String>()
        s.parseBody(files)
        return files["postData"] ?: ""
    }

    private fun parseObject(s: IHTTPSession): JSONObject? = try { JSONObject(body(s)) } catch (_: JSONException) { null }

    private fun bad(msg: String) = json(Response.Status.BAD_REQUEST, JSONObject().put("error", msg))

    private fun json(st: Response.IStatus, o: JSONObject): Response =
        newFixedLengthResponse(st, "application/json; charset=utf-8", o.toString())
}
