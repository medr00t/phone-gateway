package app.phonegateway

import android.content.Context
import java.security.SecureRandom

/** Token, port and optional webhook, kept in private preferences. */
class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("gateway", Context.MODE_PRIVATE)

    var port: Int
        get() = p.getInt("port", 8088)
        set(v) { p.edit().putInt("port", v).apply() }

    /** Incoming SMS are POSTed here as JSON when set (e.g. a box answering a call). */
    var webhookUrl: String
        get() = p.getString("webhook", "") ?: ""
        set(v) { p.edit().putString("webhook", v.trim()).apply() }

    val token: String
        get() {
            val t = p.getString("token", null)
            if (t != null) return t
            val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val rnd = SecureRandom()
            val n = (1..32).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
            p.edit().putString("token", n).apply()
            return n
        }

    fun regenerateToken() { p.edit().remove("token").apply() }

    var autostart: Boolean
        get() = p.getBoolean("autostart", true)
        set(v) { p.edit().putBoolean("autostart", v).apply() }
}
