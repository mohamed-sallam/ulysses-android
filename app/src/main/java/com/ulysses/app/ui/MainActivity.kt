package com.ulysses.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ulysses.app.ui.navigation.Screen
import com.ulysses.app.ui.screens.*
import com.ulysses.app.ui.theme.UlyssesTheme
import com.ulysses.app.util.PermissionHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UlyssesTheme {
                UlyssesNavHost()
            }
        }
    }
}

@Composable
private fun UlyssesNavHost() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val permStatus = remember { PermissionHelper.getPermissionStatus(context) }
    val startDest = if (permStatus.allGranted) Screen.Home.route else Screen.Setup.route

    NavHost(navController = navController, startDestination = startDest, modifier = Modifier.fillMaxSize()) {
        composable(Screen.Setup.route) {
            SetupScreen(onSetupComplete = {
                navController.navigate(Screen.Home.route) { popUpTo(Screen.Setup.route) { inclusive = true } }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToBlocks = { navController.navigate(Screen.Blocks.route) },
                onNavigateToLists = { navController.navigate(Screen.Lists.route) },
                onNavigateToTriggers = { navController.navigate(Screen.Triggers.route) },
                onNavigateToNetwork = { navController.navigate(Screen.Network.route) },
                onNavigateToStartSession = { navController.navigate(Screen.StartSession.createRoute(it)) },
                onNavigateToSetup = { navController.navigate(Screen.Setup.route) }
            )
        }
        composable(Screen.Lists.route) {
            ListsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Blocks.route) {
            BlocksScreen(
                onBack = { navController.popBackStack() },
                onStartSession = { navController.navigate(Screen.StartSession.createRoute(it)) }
            )
        }
        composable(Screen.Triggers.route) {
            TriggersScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Network.route) {
            NetworkScreen(
                onBack = { navController.popBackStack() },
                onScanQr = { }
            )
        }
        composable(
            Screen.StartSession.route,
            arguments = listOf(navArgument("blockId") { type = NavType.StringType })
        ) { backStackEntry ->
            val blockId = backStackEntry.arguments?.getString("blockId") ?: return@composable
            StartSessionScreen(
                blockId = blockId,
                onBack = { navController.popBackStack() },
                onStarted = {
                    navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                }
            )
        }
    }
}
