package app.phonegateway

import android.content.Context

object Retention {
    const val DAY_MS = 24L * 3600 * 1000

    const val TEST_MS = 2L * 60 * 1000
    const val TEST_DAYS = 0

    fun maxAgeMs(days: Int): Long = if (days <= TEST_DAYS) TEST_MS else days.coerceIn(1, 3650) * DAY_MS

    fun intervalMs(days: Int): Long = if (days <= TEST_DAYS) 60L * 1000 else 6L * 3600 * 1000

    fun purge(ctx: Context) {
        val settings = Settings(ctx)
        if (!settings.retentionEnabled) return
        JobStore.init(ctx)
        val maxAge = maxAgeMs(settings.retentionDays)
        JobStore.purge(maxAge)
        Inbox.purge(maxAge)
        SystemSms.purge(ctx, maxAge)
    }
}
