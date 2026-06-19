package com.ulysses.app.protection

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Manages the launcher role for anti-restart protection.
 * 
 * Strategy (from awb-android-prototype):
 * - During active block sessions, request to become the default launcher
 * - This prevents the user from restarting their phone to bypass blocks
 *   (because Ulysses launches as the home app on boot)
 * - When sessions end, release the launcher role back to the user's default
 *
 * Only activates during block sessions, not permanently.
 */
object LauncherManager {

    private const val TAG = "LauncherManager"
    private const val REQ_CODE = 592

    fun enableLauncherComponent(context: Context) {
        val pm = context.packageManager
        val comp = android.content.ComponentName(context, "com.ulysses.app.ui.LauncherActivity")
        pm.setComponentEnabledSetting(comp, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
    }

    fun disableLauncherComponent(context: Context) {
        val pm = context.packageManager
        val comp = android.content.ComponentName(context, "com.ulysses.app.ui.LauncherActivity")
        pm.setComponentEnabledSetting(comp, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
    }

    /**
     * Get intent to become the default home launcher.
     * Returns null if already held or not available.
     */
    fun getLauncherRoleIntent(context: Context): Intent? {
        enableLauncherComponent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        } else {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
        }
        return null
    }

    /**
     * Release the launcher role (allow user to set their preferred launcher).
     * Called when all block sessions end.
     */
    fun releaseLauncherRole(context: Context) {
        disableLauncherComponent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                // Clear our preferred activity to let the system choose
                context.packageManager.clearPackagePreferredActivities(context.packageName)
                Log.d(TAG, "Launcher role released")
            }
        } else {
            context.packageManager.clearPackagePreferredActivities(context.packageName)
        }
    }

    /**
     * Check if Ulysses is currently the default launcher.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val res = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            return res?.activityInfo?.packageName == context.packageName
        }
    }
}
