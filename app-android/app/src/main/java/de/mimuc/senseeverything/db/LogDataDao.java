package de.mimuc.senseeverything.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LogDataDao {

    @Query("SELECT * FROM logdata")
    List<LogData> getAll();

    @Insert
    void insertAll(LogData... logDatas);



}
