package app.phonegateway

import android.content.Context

/** Messages, calls and received SMS are kept 30 days, then deleted to save storage. */
object Retention {
    const val DAYS = 30
    private const val MAX_AGE_MS = DAYS * 24L * 3600 * 1000

    fun purge(ctx: Context) {
        JobStore.init(ctx)
        JobStore.purge(MAX_AGE_MS)
        Inbox.purge(MAX_AGE_MS)
    }
}
