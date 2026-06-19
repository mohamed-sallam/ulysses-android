package com.ulysses.app.blocker

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.BlockListEntryEntity
import com.ulysses.app.service.UlyssesAccessibilityService
import com.ulysses.app.ui.BlockedActivity
import kotlinx.coroutines.*

/**
 * Detects when the user opens a blocked app and intervenes.
 *
 * Strategy (combined from curbox + awb):
 * 1. Use accessibility events to detect foreground package changes
 * 2. Press HOME immediately (curbox approach - fast)
 * 3. Launch BlockedActivity overlay (awb approach - informative)
 *
 * Also uses UsageStatsManager as fallback for older Android versions.
 */
class AppBlocker {

    companion object {
        private const val TAG = "AppBlocker"
        private const val EVENT_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }

    private lateinit var service: UlyssesAccessibilityService
    private val handler = Handler(Looper.getMainLooper())

    // Cached blocked packages from active sessions
    @Volatile
    private var blockedPackages = setOf<String>()

    @Volatile
    private var allowedPackages = setOf<String>()

    private var lastPackage = ""
    private var refreshJob: Job? = null

    fun setup(service: UlyssesAccessibilityService) {
        this.service = service
        startRefreshLoop()
    }

    /**
     * Periodically refreshes the blocked/allowed packages from active sessions.
     */
    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                refreshBlockedPackages()
                delay(3000) // Refresh every 3 seconds
            }
        }
    }

    private suspend fun refreshBlockedPackages() {
        try {
            val db = UlyssesDatabase.getInstance(service)
            val activeSessions = db.sessionDao().getActiveSessionsSync()

            // Check if timed sessions have expired
            val now = System.currentTimeMillis()
            for (session in activeSessions) {
                if (session.lockType == "timer" && session.endsAt != null && now >= session.endsAt) {
                    db.sessionDao().deactivateSession(session.id)
                }
            }

            val validSessions = activeSessions.filter { session ->
                if (session.lockType == "timer" && session.endsAt != null) {
                    now < session.endsAt
                } else true
            }

            val denied = mutableSetOf<String>()
            val allowed = mutableSetOf<String>()

            for (session in validSessions) {
                val entries = db.blockDao().getAllEntriesForBlock(session.blockId)

                for (entry in entries) {
                    if (entry.ruleType == "app_name") {
                        if (entry.type == "allow") {
                            allowed.add(entry.value)
                        } else {
                            denied.add(entry.value)
                        }
                    }
                }
            }

            // Allowed overrides denied
            blockedPackages = denied - allowed
            allowedPackages = allowed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh blocked packages", e)
        }
    }

    fun checkEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType and EVENT_MASK) == 0) return

        val packageName = event.packageName?.toString() ?: return

        // Skip self, system UI, and already-known package
        if (packageName == service.packageName ||
            packageName == "com.android.systemui" ||
            packageName == lastPackage
        ) return

        lastPackage = packageName

        if (blockedPackages.contains(packageName)) {
            Log.d(TAG, "Blocked app detected: $packageName")
            blockApp(packageName)
        }
    }

    /**
     * Block the app using combined strategy:
     * 1. Press HOME immediately (curbox style - fast intervention)
     * 2. After short delay, show overlay warning (awb style - user feedback)
     */
    private fun blockApp(packageName: String) {
        // Immediate: press HOME (curbox approach)
        service.pressHome()
        lastPackage = "" // Reset so re-detection works

        // Delayed: show blocking overlay (awb approach)
        handler.postDelayed({
            try {
                val intent = Intent(service, BlockedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("blocked_package", packageName)
                }
                service.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch BlockedActivity", e)
            }
        }, 300)
    }

    fun isPackageBlocked(packageName: String): Boolean = blockedPackages.contains(packageName)

    fun destroy() {
        refreshJob?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
