package com.ulysses.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ulysses.app.service.SessionForegroundService

/**
 * Receives BOOT_COMPLETED broadcast to resume active block sessions.
 * Combined with the launcher activity (anti-restart), this ensures
 * block sessions survive device reboots.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot completed, starting session service")
                startSessionService(context)
            }
        }
    }

    private fun startSessionService(context: Context) {
        val serviceIntent = Intent(context, SessionForegroundService::class.java).apply {
            action = SessionForegroundService.ACTION_RESUME_SESSIONS
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session service on boot", e)
        }
    }
}
