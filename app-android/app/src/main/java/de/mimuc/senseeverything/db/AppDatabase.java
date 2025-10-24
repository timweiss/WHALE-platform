package de.mimuc.senseeverything.db;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;

import de.mimuc.senseeverything.db.models.GeneratedKey;
import de.mimuc.senseeverything.db.models.GeneratedKeyDao;
import de.mimuc.senseeverything.db.models.LogData;
import de.mimuc.senseeverything.db.models.LogDataDao;
import de.mimuc.senseeverything.db.models.NotificationTrigger;
import de.mimuc.senseeverything.db.models.NotificationTriggerDao;
import de.mimuc.senseeverything.db.models.PendingQuestionnaire;
import de.mimuc.senseeverything.db.models.PendingQuestionnaireDao;
import de.mimuc.senseeverything.db.models.ScheduledAlarm;
import de.mimuc.senseeverything.db.models.ScheduledAlarmDao;
import de.mimuc.senseeverything.db.models.SnapshotBatch;
import de.mimuc.senseeverything.db.models.SnapshotBatchDao;
import de.mimuc.senseeverything.db.models.SocialNetworkContact;
import de.mimuc.senseeverything.db.models.SocialNetworkContactDao;

@Database(entities = {
        LogData.class,
        PendingQuestionnaire.class,
        GeneratedKey.class,
        SocialNetworkContact.class,
        NotificationTrigger.class,
        ScheduledAlarm.class,
        SnapshotBatch.class
}, version = 18, autoMigrations = {@AutoMigration(from = 17, to = 18)}, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract LogDataDao logDataDao();

    public abstract PendingQuestionnaireDao pendingQuestionnaireDao();

    public abstract GeneratedKeyDao generatedKeyDao();

    public abstract SocialNetworkContactDao socialNetworkContactDao();

    public abstract NotificationTriggerDao notificationTriggerDao();

    public abstract ScheduledAlarmDao scheduledAlarmDao();

    public abstract SnapshotBatchDao snapshotBatchDao();
}
