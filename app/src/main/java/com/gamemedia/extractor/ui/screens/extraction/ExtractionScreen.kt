package com.gamemedia.extractor.ui.screens.extraction

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.gamemedia.extractor.R
import com.gamemedia.extractor.ui.screens.home.HomeViewModel

@Composable
fun ExtractionScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel(),
    viewModel: ExtractionViewModel = hiltViewModel()
) {
    val homeState by homeViewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val src = homeState.gameFolderUri
        val dst = homeState.destinationUri
        if (src != null && dst != null) {
            viewModel.startExtraction(context, src, dst)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val p = progress
            if (p == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Starting extraction…")
            } else {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { p.percent },
                        modifier = Modifier.size(160.dp),
                        strokeWidth = 10.dp
                    )
                    Text("${(p.percent * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(24.dp))
                Text(p.currentFileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text("${p.processedItems} / ${p.totalItems} items")
                Text("%.2f MB/s".format(p.bytesPerSecond / (1024 * 1024)))
                Text(if (p.etaSeconds >= 0) "ETA: ${formatEta(p.etaSeconds)}" else "Calculating ETA…")
                if (p.failedItems > 0) {
                    Text("${p.failedItems} item(s) failed", color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { viewModel.togglePause() }) {
                        Text(if (p.isPaused) stringRes(R.string.resume) else stringRes(R.string.pause))
                    }
                    OutlinedButton(onClick = { viewModel.cancel(context) }) {
                        Text(stringRes(R.string.cancel))
                    }
                }

                if (p.isComplete || p.isCancelled) {
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { navController.popBackStack("home", false) }) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
