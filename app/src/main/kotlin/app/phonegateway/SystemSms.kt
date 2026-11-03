package app.phonegateway

import android.app.Activity
import android.app.Service
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Telephony

object SystemSms {
    fun isDefault(ctx: Context): Boolean = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun requestIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)

    fun purge(ctx: Context, maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        if (!isDefault(ctx)) return 0
        return try {
            ctx.contentResolver.delete(Telephony.Sms.CONTENT_URI, "date < ?", arrayOf((now - maxAgeMs).toString()))
        } catch (_: SecurityException) { 0 }
    }

    fun clear(ctx: Context): Int {
        if (!isDefault(ctx)) return 0
        return try {
            ctx.contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null)
        } catch (_: SecurityException) { 0 }
    }
}

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Inbox.receive(ctx, intent)
}

class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { }
}

class ComposeActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
