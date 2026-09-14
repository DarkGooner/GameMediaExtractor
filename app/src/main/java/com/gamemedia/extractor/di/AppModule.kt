package com.gamemedia.extractor.di

import android.content.Context
import androidx.room.Room
import com.gamemedia.extractor.data.db.AppDatabase
import com.gamemedia.extractor.data.db.ExtractionHistoryDao
import com.gamemedia.extractor.data.repository.ExtractionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHistoryDao(db: AppDatabase): ExtractionHistoryDao = db.extractionHistoryDao()

    @Provides
    @Singleton
    fun provideExtractionRepository(
        @ApplicationContext context: Context,
        historyDao: ExtractionHistoryDao
    ): ExtractionRepository = ExtractionRepository(context, historyDao)
}
