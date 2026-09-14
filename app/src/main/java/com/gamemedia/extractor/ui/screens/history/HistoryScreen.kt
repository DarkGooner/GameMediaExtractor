package com.gamemedia.extractor.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extraction history") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No extractions yet")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(history, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.gameFolderLabel) },
                        supportingContent = {
                            Text("${entry.engine} • ${entry.extractedItems}/${entry.totalItems} extracted • ${entry.status}")
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
