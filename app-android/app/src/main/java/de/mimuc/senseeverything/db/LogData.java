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

    public boolean hasFile;

    public String filePath;

    public LogData(){}

    public LogData(long timestamp, String sensorName, String data){
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.data = data;
        this.synced = false;
    }

    public LogData(long timestamp, String sensorName, String data, boolean hasFile, String filePath) {
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.data = data;
        this.hasFile = hasFile;
        this.filePath = filePath;
    }
}
