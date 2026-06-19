package com.ulysses.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.ulysses.app.R
import com.ulysses.app.blocker.AppBlocker
import com.ulysses.app.blocker.WebsiteBlocker
import com.ulysses.app.protection.SettingsInterceptor
import kotlinx.coroutines.*

/**
 * Main accessibility service for Ulysses.
 * Handles:
 * 1. App blocking (detect foreground app, press home if blocked)
 * 2. Website/keyword blocking (detect browser URL bars, redirect/go back)
 * 3. Settings interception (go back when user opens settings during session)
 *
 * Inspired by curbox's AppBlockerService and its pressHome()/pressBack() approach.
 */
@SuppressLint("AccessibilityPolicy")
class UlyssesAccessibilityService : AccessibilityService() {

    private val appBlocker = AppBlocker()
    private val websiteBlocker = WebsiteBlocker()
    private val settingsInterceptor = SettingsInterceptor()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastBackPressTimestamp: Long = SystemClock.uptimeMillis()

    companion object {
        private const val TAG = "UlyssesA11y"
        private const val CHANNEL_ID = "ulysses_service_channel"
        private const val NOTIFICATION_ID = 7742

        @Volatile
        var instance: UlyssesAccessibilityService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        startForegroundNotification()

        appBlocker.setup(this)
        websiteBlocker.setup(this)
        settingsInterceptor.setup(this)

        Log.d(TAG, "Service connected and blockers initialized")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        try {
            // 1. Settings interception (highest priority during sessions)
            settingsInterceptor.checkEvent(event)

            // 2. App blocking
            appBlocker.checkEvent(event)

            // 3. Website/keyword blocking via browser URL detection
            websiteBlocker.checkEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    // ── Public actions ──

    fun pressHome() {
        if (isDelayOver(lastBackPressTimestamp, 400)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            lastBackPressTimestamp = SystemClock.uptimeMillis()
        }
    }

    fun pressBack() {
        if (isDelayOver(lastBackPressTimestamp, 400)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastBackPressTimestamp = SystemClock.uptimeMillis()
        }
    }

    private fun isDelayOver(lastTimestamp: Long, delay: Int): Boolean {
        return SystemClock.uptimeMillis() - lastTimestamp > delay
    }

    // ── Foreground notification ──

    private fun startForegroundNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ulysses Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Running in the background to protect you from distractions"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ulysses is active")
            .setContentText("Protecting your focus")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
