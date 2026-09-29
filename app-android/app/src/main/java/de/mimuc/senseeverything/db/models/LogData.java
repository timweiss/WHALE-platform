package de.mimuc.senseeverything.db.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

// The upload reads the backlog in timestamp order in batches (deleting each uploaded batch), and the
// unsynced counts filter by sync state and time; both are served by this index.
@Entity(indices = {@Index(value = {"synced", "timestamp"})})
public class LogData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;

    public String sensorName;

    public boolean synced;

    public String data;

    public boolean hasFile;

    public String filePath;

    public String localId;

    public LogData(){}

    public LogData(long timestamp, String sensorName, String data){
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.data = data;
        this.synced = false;
        this.localId = UUID.randomUUID().toString();
    }

    public LogData(long timestamp, String sensorName, String data, boolean hasFile, String filePath) {
        this.timestamp = timestamp;
        this.sensorName = sensorName;
        this.data = data;
        this.hasFile = hasFile;
        this.filePath = filePath;
        this.localId = UUID.randomUUID().toString();
    }
}
