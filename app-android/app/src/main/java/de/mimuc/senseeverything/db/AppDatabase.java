package de.mimuc.senseeverything.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {LogData.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract LogDataDao logDataDao();
}
