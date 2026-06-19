package com.ulysses.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ulysses.app.R
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.protection.LauncherManager
import com.ulysses.app.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Foreground service that maintains active block sessions.
 * 
 * Responsibilities:
 * 1. Show persistent notification with session countdown
 * 2. Auto-expire timed sessions
 * 3. Manage launcher role (anti-restart) during sessions
 * 4. Start/stop VPN service for DNS blocking
 * 5. Resume sessions after boot
 */
class SessionForegroundService : Service() {

    companion object {
        private const val TAG = "SessionService"
        private const val CHANNEL_ID = "ulysses_session_channel"
        private const val NOTIFICATION_ID = 7743
        const val ACTION_RESUME_SESSIONS = "com.ulysses.app.RESUME_SESSIONS"
        const val ACTION_CHECK_SESSIONS = "com.ulysses.app.CHECK_SESSIONS"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification("Ulysses is protecting your focus", null)

        when (intent?.action) {
            ACTION_RESUME_SESSIONS -> {
                Log.d(TAG, "Resuming sessions after boot")
                startMonitoring()
            }
            else -> {
                startMonitoring()
            }
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                val hasActive = checkAndManageSessions()
                if (!hasActive) {
                    // No active sessions - release protections and stop
                    withContext(Dispatchers.Main) {
                        LauncherManager.releaseLauncherRole(this@SessionForegroundService)
                    }
                    stopVpnService()
                    delay(5000) // Wait a bit before checking again
                    val stillNoActive = !checkAndManageSessions()
                    if (stillNoActive) {
                        stopSelf()
                        break
                    }
                } else {
                    startVpnService()
                    delay(1000) // Update every second for countdown
                }
            }
        }
    }

    /**
     * Check active sessions, expire completed ones, update notification.
     * Returns true if there are still active sessions.
     */
    private suspend fun checkAndManageSessions(): Boolean {
        try {
            val db = UlyssesDatabase.getInstance(this)
            val sessions = db.sessionDao().getActiveSessionsSync()
            val now = System.currentTimeMillis()

            var hasActive = false
            var nearestExpiry: Long? = null

            for (session in sessions) {
                if (session.lockType == "timer" && session.endsAt != null) {
                    if (now >= session.endsAt) {
                        // Session expired
                        db.sessionDao().deactivateSession(session.id)
                        Log.d(TAG, "Session ${session.id} expired")
                    } else {
                        hasActive = true
                        if (nearestExpiry == null || session.endsAt < nearestExpiry) {
                            nearestExpiry = session.endsAt
                        }
                    }
                } else if (session.lockType == "password") {
                    hasActive = true
                }
            }

            // Update notification
            if (hasActive && nearestExpiry != null) {
                val remaining = nearestExpiry - now
                val text = formatDuration(remaining)
                withContext(Dispatchers.Main) {
                    showNotification("Block session active", "Time remaining: $text")
                }
            } else if (hasActive) {
                withContext(Dispatchers.Main) {
                    showNotification("Block session active", "Password locked")
                }
            }

            return hasActive
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sessions", e)
            return false
        }
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun startVpnService() {
        val intent = Intent(this, UlyssesVpnService::class.java).apply {
            action = UlyssesVpnService.ACTION_START
        }
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, UlyssesVpnService::class.java).apply {
            action = UlyssesVpnService.ACTION_STOP
        }
        startService(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Block Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active block session countdown"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, text: String?) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text ?: "Protecting your focus")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        serviceScope.cancel()
    }
}
