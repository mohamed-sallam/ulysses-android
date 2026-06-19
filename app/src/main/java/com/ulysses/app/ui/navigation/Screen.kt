package com.ulysses.app.ui.navigation

/**
 * Navigation destinations for the Ulysses app.
 */
sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Home : Screen("home")
    data object Lists : Screen("lists")
    data object ListDetail : Screen("lists/{listId}") {
        fun createRoute(listId: String) = "lists/$listId"
    }
    data object Blocks : Screen("blocks")
    data object BlockDetail : Screen("blocks/{blockId}") {
        fun createRoute(blockId: String) = "blocks/$blockId"
    }
    data object Triggers : Screen("triggers")
    data object Network : Screen("network")
    data object ActiveSession : Screen("session/{sessionId}") {
        fun createRoute(sessionId: String) = "session/$sessionId"
    }
    data object StartSession : Screen("start_session/{blockId}") {
        fun createRoute(blockId: String) = "start_session/$blockId"
    }
    data object QrScanner : Screen("qr_scanner")
}
