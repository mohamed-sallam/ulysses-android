package com.ulysses.app.protection

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.service.UlyssesAccessibilityService
import kotlinx.coroutines.*

/**
 * Intercepts navigation to Settings when an active block session is running.
 * 
 * Strategy (from curbox):
 * - When user opens Settings app during an active session, press BACK immediately
 * - This prevents the user from disabling accessibility service or device admin
 * - Overlay over Settings is no longer supported on modern Android, so go-back is the approach
 *
 * Only active during block sessions. When no session is active, Settings is freely accessible.
 */
class SettingsInterceptor {

    companion object {
        private const val TAG = "SettingsInterceptor"

        // Package names for Settings on various Android devices
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.app.settings",        // Samsung
            "com.oneplus.security",                      // OnePlus
            "com.coloros.safecenter",                     // OPPO/Realme
            "com.miui.securitycenter",                   // Xiaomi
            "com.miui.home",                             // Xiaomi home settings
            "com.huawei.systemmanager",                  // Huawei
            "com.vivo.abe",                              // Vivo
        )

        // Additional packages that could be used to disable protections
        private val PROTECTION_PACKAGES = setOf(
            "com.android.packageinstaller",              // Package installer
            "com.google.android.packageinstaller",       // Google package installer
            "com.android.permissioncontroller",          // Permission manager
        )
    }

    private lateinit var service: UlyssesAccessibilityService

    @Volatile
    private var hasActiveSession = false

    private var refreshJob: Job? = null
    private var lastPackage = ""

    fun setup(service: UlyssesAccessibilityService) {
        this.service = service
        startRefreshLoop()
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                checkActiveSessions()
                delay(2000)
            }
        }
    }

    private suspend fun checkActiveSessions() {
        try {
            val db = UlyssesDatabase.getInstance(service)
            val sessions = db.sessionDao().getActiveSessionsSync()
            val now = System.currentTimeMillis()
            hasActiveSession = sessions.any { session ->
                if (session.lockType == "timer" && session.endsAt != null) {
                    now < session.endsAt
                } else true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check sessions", e)
        }
    }

    fun checkEvent(event: AccessibilityEvent?) {
        if (!hasActiveSession) return
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == lastPackage) return

        if (SETTINGS_PACKAGES.contains(packageName) || PROTECTION_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Settings access blocked during active session: $packageName")
            lastPackage = packageName
            service.pressBack()

            // Double-tap back then home for reliability
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                service.pressHome()
                lastPackage = ""
            }, 200)
        } else {
            lastPackage = packageName
        }
    }

    fun destroy() {
        refreshJob?.cancel()
    }
}
