package com.gamemedia.extractor.ui.screens.scan

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamemedia.extractor.data.model.DetectedMedia
import com.gamemedia.extractor.data.repository.ExtractionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val isScanning: Boolean = false,
    val items: List<DetectedMedia> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ExtractionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state

    fun startScan(context: android.content.Context, folderUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: run {
            _state.update { it.copy(error = "Could not open folder") }
            return
        }
        _state.update { it.copy(isScanning = true, items = emptyList(), error = null) }
        viewModelScope.launch {
            try {
                repository.scanFolder(root).collect { media ->
                    _state.update { it.copy(items = it.items + media) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isScanning = false) }
            }
        }
    }
}
