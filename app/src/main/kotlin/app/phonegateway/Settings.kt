package app.phonegateway

import android.content.Context
import java.security.SecureRandom

class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("gateway", Context.MODE_PRIVATE)

    var port: Int
        get() = p.getInt("port", 8088)
        set(v) { p.edit().putInt("port", v).apply() }

    var webhookUrl: String
        get() = p.getString("webhook", "") ?: ""
        set(v) { p.edit().putString("webhook", v.trim()).apply() }

    var jobWebhookUrl: String
        get() = p.getString("jobWebhook", "") ?: ""
        set(v) { p.edit().putString("jobWebhook", v.trim()).apply() }

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

    var roleSms: Boolean
        get() = p.getBoolean("roleSms", true)
        set(v) { p.edit().putBoolean("roleSms", v).apply() }

    var roleUssd: Boolean
        get() = SimChoice.withLegacy(p.contains("roleUssd"), p.getBoolean("roleUssd", false), p.getBoolean("roleCalls", false))
        set(v) { p.edit().putBoolean("roleUssd", v).apply() }

    var roleCall: Boolean
        get() = SimChoice.withLegacy(p.contains("roleCall"), p.getBoolean("roleCall", false), p.getBoolean("roleCalls", false))
        set(v) { p.edit().putBoolean("roleCall", v).apply() }

    var smsSim: Int
        get() = p.getInt("smsSim", 0)
        set(v) { p.edit().putInt("smsSim", v.coerceIn(0, 4)).apply() }

    var ussdSim: Int
        get() = p.getInt("ussdSim", 0)
        set(v) { p.edit().putInt("ussdSim", v.coerceIn(0, 4)).apply() }

    var callSim: Int
        get() = SimChoice.withLegacy(p.contains("callSim"), p.getInt("callSim", 0), p.getInt("ussdSim", 0))
        set(v) { p.edit().putInt("callSim", v.coerceIn(0, 4)).apply() }

    var retentionEnabled: Boolean
        get() = p.getBoolean("retentionEnabled", true)
        set(v) { p.edit().putBoolean("retentionEnabled", v).apply() }

    var retentionDays: Int
        get() = p.getInt("retentionDays", 30).let { if (it < 1) 30 else it.coerceAtMost(3650) }
        set(v) { p.edit().putInt("retentionDays", v.coerceIn(1, 3650)).apply() }

    var autostart: Boolean
        get() = p.getBoolean("autostart", true)
        set(v) { p.edit().putBoolean("autostart", v).apply() }
}
