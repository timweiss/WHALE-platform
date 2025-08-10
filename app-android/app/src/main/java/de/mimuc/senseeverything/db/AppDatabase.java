package de.mimuc.senseeverything.db;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

import de.mimuc.senseeverything.db.models.GeneratedKey;
import de.mimuc.senseeverything.db.models.GeneratedKeyDao;
import de.mimuc.senseeverything.db.models.LogData;
import de.mimuc.senseeverything.db.models.LogDataDao;
import de.mimuc.senseeverything.db.models.PendingQuestionnaire;
import de.mimuc.senseeverything.db.models.PendingQuestionnaireDao;
import de.mimuc.senseeverything.db.models.SocialNetworkContact;
import de.mimuc.senseeverything.db.models.SocialNetworkContactDao;

@Database(entities = {
        LogData.class,
        PendingQuestionnaire.class,
        GeneratedKey.class,
        SocialNetworkContact.class
}, version = 9, autoMigrations = {@AutoMigration(from = 1, to = 9)}, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract LogDataDao logDataDao();

    public abstract PendingQuestionnaireDao pendingQuestionnaireDao();

    public abstract GeneratedKeyDao generatedKeyDao();

    public abstract SocialNetworkContactDao socialNetworkContactDao();
}
