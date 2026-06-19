package com.ulysses.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.ui.components.CountdownDisplay
import com.ulysses.app.ui.components.PulsingShield
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Launcher activity that acts as the home screen during active block sessions.
 * This is the anti-restart protection (from awb):
 * - During a session, Ulysses becomes the default launcher
 * - If the user restarts the phone, they land here instead of their normal launcher
 * - Shows session countdown and prevents accessing blocked content
 * - When no session is active, redirects to the normal MainActivity
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UlyssesTheme {
                LauncherScreen(
                    onNoSession = {
                        // No active session - open normal app
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        // Do nothing - prevent leaving the launcher during sessions
    }
}

@Composable
private fun LauncherScreen(onNoSession: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { UlyssesDatabase.getInstance(context) }

    var allowedApps by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasActiveSession by remember { mutableStateOf(true) }
    var nearestExpiry by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val sessions = db.sessionDao().getActiveSessionsSync()
            val valid = sessions.filter { s ->
                if (s.lockType == "timer" && s.endsAt != null) now < s.endsAt else true
            }
            hasActiveSession = valid.isNotEmpty()
            nearestExpiry = valid.mapNotNull { it.endsAt }.minOrNull() ?: 0L

            // Auto-expire sessions
            sessions.forEach { s ->
                if (s.lockType == "timer" && s.endsAt != null && now >= s.endsAt) {
                    db.sessionDao().deactivateSession(s.id)
                }
            }
            
            if (valid.isNotEmpty()) {
                val denied = mutableSetOf<String>()
                val allowed = mutableSetOf<String>()
                for (s in valid) {
                    val entries = db.blockDao().getAllEntriesForBlock(s.blockId)
                    for (e in entries) {
                        if (e.ruleType == "app_name") {
                            if (e.type == "allow") allowed.add(e.value)
                            else denied.add(e.value)
                        }
                    }
                }
                val blockedApps = denied - allowed

                // Get all launcher apps
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val allApps = pm.queryIntentActivities(intent, 0)
                
                // Exclude blocked apps and Ulysses itself
                val nonBlocked = allApps.map { it.activityInfo.packageName }
                    .filter { it != context.packageName && !blockedApps.contains(it) }
                    .distinct()
                
                allowedApps = nonBlocked.toList()
            }

            if (!hasActiveSession) {
                onNoSession()
                break
            }
            delay(1000)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy)))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).fillMaxSize()) {
            PulsingShield(isActive = true)
            Spacer(Modifier.height(24.dp))
            Text("Ulysses", style = MaterialTheme.typography.headlineLarge, color = PureWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Block session is active", style = MaterialTheme.typography.bodyLarge, color = GoldenAmber)
            Spacer(Modifier.height(32.dp))

            if (nearestExpiry > 0) {
                val remaining = (nearestExpiry - now).coerceAtLeast(0)
                CountdownDisplay(remainingMillis = remaining)
                Spacer(Modifier.height(16.dp))
                Text("Stay focused. Your session will end automatically.",
                    style = MaterialTheme.typography.bodyMedium, color = SilverMist, textAlign = TextAlign.Center)
            } else {
                Text("Password-locked session active", style = MaterialTheme.typography.bodyMedium, color = SilverMist)
            }
            
            if (allowedApps.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text("Available Apps", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(allowedApps.size) { index ->
                        val pkg = allowedApps[index]
                        AllowedAppItem(pkg = pkg, context = context)
                    }
                }
            }
        }
    }
}

@Composable
fun AllowedAppItem(pkg: String, context: android.content.Context) {
    val pm = context.packageManager
    var appName by remember { mutableStateOf(pkg) }
    var appIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    
    LaunchedEffect(pkg) {
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            appName = pm.getApplicationLabel(info).toString()
            appIcon = pm.getApplicationIcon(info)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
            }
        }
    ) {
        if (appIcon != null) {
            androidx.compose.foundation.Image(
                painter = com.google.accompanist.drawablepainter.rememberDrawablePainter(appIcon),
                contentDescription = appName,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(Modifier.size(48.dp).background(SilverMist, androidx.compose.foundation.shape.CircleShape))
        }
        Spacer(Modifier.height(4.dp))
        Text(appName, style = MaterialTheme.typography.labelSmall, color = PureWhite, maxLines = 1, textAlign = TextAlign.Center)
    }
}
