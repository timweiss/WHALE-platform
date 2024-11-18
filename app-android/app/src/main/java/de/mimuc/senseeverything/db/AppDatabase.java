package de.mimuc.senseeverything.db;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {LogData.class, PendingQuestionnaire.class}, version = 2, autoMigrations = {
        @AutoMigration(from = 1, to = 2)
}, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract LogDataDao logDataDao();

    public abstract PendingQuestionnaireDao pendingQuestionnaireDao();
}
