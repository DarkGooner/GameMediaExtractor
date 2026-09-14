package com.gamemedia.extractor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtractionHistoryDao {

    @Insert
    suspend fun insert(entity: ExtractionHistoryEntity): Long

    @Query("SELECT * FROM extraction_history ORDER BY finishedAtEpochMs DESC")
    fun observeAll(): Flow<List<ExtractionHistoryEntity>>

    @Query("DELETE FROM extraction_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM extraction_history")
    suspend fun clearAll()
}
