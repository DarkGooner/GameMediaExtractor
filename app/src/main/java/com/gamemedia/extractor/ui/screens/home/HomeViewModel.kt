package com.gamemedia.extractor.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HomeUiState(
    val gameFolderUri: Uri? = null,
    val gameFolderLabel: String = "",
    val destinationUri: Uri? = null,
    val destinationLabel: String = ""
) {
    val canProceed: Boolean get() = gameFolderUri != null && destinationUri != null
}

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    fun onGameFolderSelected(uri: Uri, label: String) {
        _state.update { it.copy(gameFolderUri = uri, gameFolderLabel = label) }
    }

    fun onDestinationSelected(uri: Uri, label: String) {
        _state.update { it.copy(destinationUri = uri, destinationLabel = label) }
    }
}
