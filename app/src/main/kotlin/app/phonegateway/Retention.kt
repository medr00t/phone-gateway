package app.phonegateway

import android.content.Context

object Retention {
    const val DAY_MS = 24L * 3600 * 1000

    fun maxAgeMs(days: Int): Long = days.coerceIn(1, 3650) * DAY_MS

    fun purge(ctx: Context) {
        val settings = Settings(ctx)
        if (!settings.retentionEnabled) return
        JobStore.init(ctx)
        val maxAge = maxAgeMs(settings.retentionDays)
        JobStore.purge(maxAge)
        Inbox.purge(maxAge)
    }
}
