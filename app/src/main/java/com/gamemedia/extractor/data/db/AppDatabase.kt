package com.gamemedia.extractor.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ExtractionHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun extractionHistoryDao(): ExtractionHistoryDao

    companion object {
        const val DATABASE_NAME = "extraction_history.db"
    }
}
