package com.gamemedia.extractor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamemedia.extractor.ui.navigation.Screen
import com.gamemedia.extractor.ui.screens.extraction.ExtractionScreen
import com.gamemedia.extractor.ui.screens.history.HistoryScreen
import com.gamemedia.extractor.ui.screens.home.HomeScreen
import com.gamemedia.extractor.ui.screens.scan.ScanScreen
import com.gamemedia.extractor.ui.screens.settings.SettingsScreen
import com.gamemedia.extractor.ui.theme.GameMediaExtractorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameMediaExtractorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Scan.route) { ScanScreen(navController) }
        composable(Screen.Extraction.route) { ExtractionScreen(navController) }
        composable(Screen.History.route) { HistoryScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
