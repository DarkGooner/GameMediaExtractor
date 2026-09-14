package com.gamemedia.extractor.ui.screens.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
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
import com.gamemedia.extractor.data.model.MediaType
import com.gamemedia.extractor.ui.navigation.Screen
import com.gamemedia.extractor.ui.screens.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel(),
    viewModel: ScanViewModel = hiltViewModel()
) {
    val homeState by homeViewModel.state.collectAsState()
    val scanState by viewModel.state.collectAsState()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(homeState.gameFolderUri) {
        homeState.gameFolderUri?.let { viewModel.startScan(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detected media (${scanState.items.size})") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { navController.navigate(Screen.Extraction.route) },
                    enabled = scanState.items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
                ) {
                    Text("Extract ${scanState.items.size} items")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (scanState.isScanning && scanState.items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning game folder…")
                }
            } else if (scanState.items.isEmpty() && !scanState.isScanning) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No extractable media found", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "This folder may not contain a supported Unity or RPG Maker game.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn {
                    items(scanState.items, key = { it.id }) { media ->
                        ListItem(
                            headlineContent = { Text(media.displayName) },
                            supportingContent = { Text("${media.engine} • ${formatSize(media.estimatedSizeBytes)}") },
                            leadingContent = {
                                Icon(
                                    when (media.type) {
                                        MediaType.IMAGE -> Icons.Default.Image
                                        MediaType.VIDEO -> Icons.Default.Movie
                                        MediaType.AUDIO -> Icons.Default.MusicNote
                                        MediaType.UNKNOWN -> Icons.Default.Movie
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
                if (scanState.isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}
