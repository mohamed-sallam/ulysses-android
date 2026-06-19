package com.ulysses.app.util

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import com.ulysses.app.protection.AdminReceiver
import com.ulysses.app.service.UlyssesAccessibilityService

/**
 * Permission utility helpers inspired by curbox's PermissionUtils.
 * Provides a clean API for checking and requesting all needed permissions.
 */
object PermissionHelper {

    // ── Accessibility Service ──

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, UlyssesAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expectedComponent) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(context, UlyssesAccessibilityService::class.java)
            intent.putExtra(":settings:fragment_args_key", componentName.flattenToString())
            val bundle = Bundle()
            bundle.putString(":settings:fragment_args_key", componentName.flattenToString())
            intent.putExtra(":settings:show_fragment_args", bundle)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ── Overlay Permission ──

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // ── Usage Stats ──

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                "android:get_usage_stats",
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ── Notification Permission ──

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ── VPN Permission ──

    fun hasVpnPermission(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    // ── Device Admin ──

    fun isDeviceAdminActive(context: Context): Boolean = AdminReceiver.isAdminActive(context)

    fun requestDeviceAdmin(context: Context) = AdminReceiver.requestAdmin(context)

    // ── Battery Optimization ──

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // ── Combined Check ──

    data class PermissionStatus(
        val accessibility: Boolean,
        val overlay: Boolean,
        val usageStats: Boolean,
        val notification: Boolean,
        val deviceAdmin: Boolean,
        val batteryOptimization: Boolean
    ) {
        val allGranted: Boolean
            get() = accessibility && overlay && usageStats && notification && deviceAdmin && batteryOptimization

        val grantedCount: Int
            get() = listOf(accessibility, overlay, usageStats, notification, deviceAdmin, batteryOptimization)
                .count { it }

        val totalCount: Int = 6
    }

    fun getPermissionStatus(context: Context): PermissionStatus {
        return PermissionStatus(
            accessibility = isAccessibilityServiceEnabled(context),
            overlay = hasOverlayPermission(context),
            usageStats = hasUsageStatsPermission(context),
            notification = hasNotificationPermission(context),
            deviceAdmin = isDeviceAdminActive(context),
            batteryOptimization = isBatteryOptimizationIgnored(context)
        )
    }
}
