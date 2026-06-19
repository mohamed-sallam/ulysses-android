package com.ulysses.app.ui.screens

import android.Manifest
import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import com.ulysses.app.ui.components.PermissionItem
import com.ulysses.app.ui.theme.*
import com.ulysses.app.util.PermissionHelper

/**
 * Setup/onboarding screen for granting permissions.
 * Easy UX inspired by curbox's onboarding flow.
 */
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    var permissionStatus by remember { mutableStateOf(PermissionHelper.getPermissionStatus(context)) }

    // Refresh permissions when returning to this screen
    LaunchedEffect(Unit) {
        while (true) {
            permissionStatus = PermissionHelper.getPermissionStatus(context)
            if (permissionStatus.allGranted) {
                onSetupComplete()
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionStatus = PermissionHelper.getPermissionStatus(context)
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        permissionStatus = PermissionHelper.getPermissionStatus(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepOcean, DarkNavy)
                )
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = "⛵",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Ulysses",
            style = MaterialTheme.typography.headlineMedium,
            color = PureWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Grant the permissions below to enable distraction blocking",
            style = MaterialTheme.typography.bodyMedium,
            color = SilverMist,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicator
        val progress = permissionStatus.grantedCount.toFloat() / permissionStatus.totalCount
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = GoldenAmber,
            trackColor = SlateBlue.copy(alpha = 0.3f),
            strokeCap = StrokeCap.Round
        )

        Text(
            text = "${permissionStatus.grantedCount}/${permissionStatus.totalCount} permissions granted",
            style = MaterialTheme.typography.labelSmall,
            color = SteelGray,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission items
        PermissionItem(
            title = "Accessibility Service",
            description = "Required to detect and block apps and websites",
            isGranted = permissionStatus.accessibility,
            icon = Icons.Filled.Accessibility,
            onClick = { PermissionHelper.openAccessibilitySettings(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItem(
            title = "Display Over Other Apps",
            description = "Shows blocking overlay when a blocked app is opened",
            isGranted = permissionStatus.overlay,
            icon = Icons.Filled.Layers,
            onClick = { PermissionHelper.requestOverlayPermission(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItem(
            title = "Usage Access",
            description = "Detects which app is in the foreground",
            isGranted = permissionStatus.usageStats,
            icon = Icons.Filled.BarChart,
            onClick = { PermissionHelper.requestUsageStatsPermission(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItem(
            title = "Notifications",
            description = "Shows session countdown and status",
            isGranted = permissionStatus.notification,
            icon = Icons.Filled.Notifications,
            onClick = {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItem(
            title = "Device Administrator",
            description = "Prevents uninstallation during block sessions",
            isGranted = permissionStatus.deviceAdmin,
            icon = Icons.Filled.AdminPanelSettings,
            onClick = { PermissionHelper.requestDeviceAdmin(context) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionItem(
            title = "Battery Optimization",
            description = "Keeps Ulysses running in the background",
            isGranted = permissionStatus.batteryOptimization,
            icon = Icons.Filled.BatteryChargingFull,
            onClick = { PermissionHelper.requestIgnoreBatteryOptimization(context) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Continue button
        AnimatedVisibility(
            visible = permissionStatus.allGranted,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut()
        ) {
            Button(
                onClick = onSetupComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepOcean,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = DeepOcean
                )
            }
        }

        // Skip button (for testing)
        if (!permissionStatus.allGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSetupComplete) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodySmall,
                    color = SteelGray
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
