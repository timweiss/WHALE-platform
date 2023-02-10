package de.mimuc.senseeverything.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class LogData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;

    public String sensorName;

    public boolean synced;

    public String data;

    public LogData(){}

    public LogData(long timestamp, String sensorName, String data){
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.data = data;
        this.synced = false;
    }

}
