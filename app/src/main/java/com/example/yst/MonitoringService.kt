package com.example.yst

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps microphone monitoring alive after the screen closes.
 * Android only allows that while this foreground notification is visible.
 */
class MonitoringService : Service() {
    private var sessionId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getIntExtra(EXTRA_SESSION, sessionId) ?: sessionId
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (_: SecurityException) {
            MonitoringRegistry.get(application).onAppBackgrounded()
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            MonitoringRegistry.get(application).onAppBackgrounded()
            stopSelf()
            return START_NOT_STICKY
        }
        MonitoringRegistry.get(application).startMonitoring()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (sessionId == sessions.get() && MonitoringRegistry.isRunning()) {
            MonitoringRegistry.get(application).stopMonitoring()
        }
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitoring_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_listening)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.monitoring_notification))
        .setContentIntent(openApp())
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val CHANNEL_ID = "yst_listening"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_SESSION = "session"
        private val sessions = AtomicInteger()

        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
                .putExtra(EXTRA_SESSION, sessions.incrementAndGet())
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: IllegalStateException) {
                // Android rejected a start from the background. The open screen can try again.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
