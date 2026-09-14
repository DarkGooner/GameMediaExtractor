package com.gamemedia.extractor.ui.screens.extraction

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.work.*
import com.gamemedia.extractor.work.ExtractionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ExtractionViewModel @Inject constructor() : ViewModel() {

    val progress: StateFlow<com.gamemedia.extractor.data.model.ExtractionProgress?> =
        ExtractionWorker.progressFlow

    fun startExtraction(context: Context, sourceUri: Uri, destUri: Uri) {
        val request = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(
                workDataOf(
                    ExtractionWorker.KEY_SOURCE_URI to sourceUri.toString(),
                    ExtractionWorker.KEY_DEST_URI to destUri.toString()
                )
            )
            .setConstraints(Constraints.Builder().build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "extraction_job", ExistingWorkPolicy.REPLACE, request
        )
    }

    fun togglePause() {
        ExtractionWorker.isPaused.set(!ExtractionWorker.isPaused.get())
    }

    fun cancel(context: Context) {
        ExtractionWorker.isCancelled.set(true)
        WorkManager.getInstance(context).cancelUniqueWork("extraction_job")
    }
}
