package de.mimuc.senseeverything.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.mimuc.senseeverything.db.models.LogDataDao
import de.mimuc.senseeverything.db.models.PendingQuestionnaireDao
import de.mimuc.senseeverything.db.models.SnapshotBatchDao
import javax.inject.Singleton

@Module()
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            name = "senseeverything-roomdb"
        )
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    fun provideLogDataDao(appDatabase: AppDatabase): LogDataDao {
        return appDatabase.logDataDao()
    }

    @Provides
    fun providePendingQuestionnaireDao(appDatabase: AppDatabase): PendingQuestionnaireDao {
        return appDatabase.pendingQuestionnaireDao()
    }

    @Provides
    fun provideSnapshotBatchDao(appDatabase: AppDatabase): SnapshotBatchDao {
        return appDatabase.snapshotBatchDao()
    }
}