package com.gamemedia.extractor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A persisted record of a completed (or failed / cancelled) extraction job. */
@Entity(tableName = "extraction_history")
data class ExtractionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameFolderLabel: String,
    val destinationLabel: String,
    val engine: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val totalItems: Int,
    val extractedItems: Int,
    val failedItems: Int,
    val totalBytes: Long,
    val status: String, // COMPLETED, CANCELLED, FAILED
    val errorMessage: String? = null
)
