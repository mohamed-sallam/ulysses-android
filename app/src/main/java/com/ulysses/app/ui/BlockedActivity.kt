package com.ulysses.app.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ulysses.app.ui.theme.*

/**
 * Overlay activity shown when a blocked app/website is opened.
 * Combines AWB's overlay approach with a modern Compose UI.
 */
class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPackage = intent.getStringExtra("blocked_package") ?: ""
        val blockedUrl = intent.getStringExtra("blocked_url")

        val appName = try {
            val ai = packageManager.getApplicationInfo(blockedPackage, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) { blockedPackage }

        setContent {
            UlyssesTheme {
                BlockedScreen(
                    appName = appName,
                    blockedUrl = blockedUrl,
                    onGoHome = { finishAffinity() }
                )
            }
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        // Prevent going back to the blocked app
        finishAffinity()
    }
}

@Composable
private fun BlockedScreen(appName: String, blockedUrl: String?, onGoHome: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse), label = "s")

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(100.dp).scale(scale), contentAlignment = Alignment.Center) {
                Box(Modifier.size(100.dp).clip(CircleShape).background(BlockedRed.copy(alpha = 0.15f)))
                Icon(Icons.Filled.Block, null, Modifier.size(56.dp), tint = BlockedRed)
            }
            Spacer(Modifier.height(32.dp))
            Text("Blocked", style = MaterialTheme.typography.displaySmall, color = PureWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (blockedUrl != null) "This website is blocked during your session"
                else "$appName is blocked during your session",
                style = MaterialTheme.typography.bodyLarge, color = SilverMist, textAlign = TextAlign.Center
            )
            if (blockedUrl != null) {
                Spacer(Modifier.height(8.dp))
                Text(blockedUrl, style = MaterialTheme.typography.bodySmall, color = SteelGray, textAlign = TextAlign.Center, maxLines = 2)
            }
            Spacer(Modifier.height(12.dp))
            Text("Stay focused. You've got this! 💪", style = MaterialTheme.typography.bodyMedium, color = GoldenAmber, fontSize = 16.sp)
            Spacer(Modifier.height(40.dp))
            Button(onClick = onGoHome, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)) {
                Icon(Icons.Filled.Home, null, tint = DeepOcean)
                Spacer(Modifier.width(8.dp))
                Text("Go Home", color = DeepOcean, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
