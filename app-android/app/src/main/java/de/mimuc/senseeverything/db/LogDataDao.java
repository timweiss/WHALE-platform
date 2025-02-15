package de.mimuc.senseeverything.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LogDataDao {

    @Query("SELECT * FROM logdata")
    List<LogData> getAll();

    @Insert
    void insertAll(LogData... logDatas);

    @Query("SELECT * FROM logdata WHERE synced = FALSE ORDER BY timestamp ASC LIMIT :n")
    List<LogData> getNextNUnsynced(int n);

    @Query("SELECT COUNT(*) FROM logdata WHERE synced = FALSE")
    long getUnsyncedCount();

    @Query("SELECT * FROM logdata ORDER BY timestamp DESC LIMIT 1")
    LogData getLastItem();

    @Update
    public void updateLogData(LogData... logData);

    @Delete
    public void deleteLogData(LogData... logData);

    @Query("DELETE FROM logdata")
    public void deleteAll();
}
