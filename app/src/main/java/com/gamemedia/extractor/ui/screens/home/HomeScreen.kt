package com.gamemedia.extractor.ui.screens.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.gamemedia.extractor.R
import com.gamemedia.extractor.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val gameFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onGameFolderSelected(uri, uri.lastPathSegment ?: uri.toString())
        }
    }

    val destinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onDestinationSelected(uri, uri.lastPathSegment ?: uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResourceCompat(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.History.route) }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Extract images and video from Unity and RPG Maker games",
                style = MaterialTheme.typography.titleMedium
            )

            SelectionCard(
                icon = Icons.Default.Folder,
                title = "Game folder",
                subtitle = state.gameFolderLabel.ifBlank { "Not selected" },
                buttonLabel = stringResourceCompat(R.string.select_game_folder),
                onClick = { gameFolderLauncher.launch(null) }
            )

            SelectionCard(
                icon = Icons.Default.Save,
                title = "Destination folder",
                subtitle = state.destinationLabel.ifBlank { "Not selected" },
                buttonLabel = stringResourceCompat(R.string.select_destination),
                onClick = { destinationLauncher.launch(null) }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { navController.navigate(Screen.Scan.route) },
                enabled = state.canProceed,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResourceCompat(R.string.start_scan))
            }
        }
    }
}

@Composable
private fun SelectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            OutlinedButton(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
