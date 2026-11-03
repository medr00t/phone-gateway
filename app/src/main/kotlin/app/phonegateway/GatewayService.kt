package app.phonegateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

class GatewayService : Service() {
    private var server: ApiServer? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        JobStore.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gateway", "Passerelle", NotificationManager.IMPORTANCE_LOW))
        val settings = Settings(this)
        val note = Notification.Builder(this, "gateway")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Passerelle active")
            .setContentText("API sur le port ${settings.port}")
            .setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(1, note)

        running = true
        if (server == null) {
            server = ApiServer(settings, settings.port).also { it.start(5000, false) }
            Dispatcher.start(this)
            purgeLoop()
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonegateway:service").also { it.acquire() }
        }
        return START_STICKY
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun purgeLoop() {
        Thread { Retention.purge(this) }.start()
        handler.postDelayed({ purgeLoop() }, Retention.intervalMs(Settings(this).retentionDays))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        running = false
        server?.stop(); server = null
        Dispatcher.stop()
        wake?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, GatewayService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, GatewayService::class.java))
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Settings(ctx).autostart) {
            try { GatewayService.start(ctx) } catch (_: Exception) {  }
        }
    }
}
