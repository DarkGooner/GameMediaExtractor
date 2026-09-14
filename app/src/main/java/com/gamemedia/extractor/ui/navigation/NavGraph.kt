package com.gamemedia.extractor.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Scan : Screen("scan")
    data object Extraction : Screen("extraction")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
