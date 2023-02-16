package de.mimuc.senseeverything.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import de.mimuc.senseeverything.db.LogData;

public class SenseEverythingDataBackendWrapper {

    public String clientDeviceId;
    public String dataKey;
    @SerializedName("data")
    public List<LogData> datas;

    public SenseEverythingDataBackendWrapper(List<LogData> data) {
        this.clientDeviceId = "42"; // TODO set some useful user id
        this.dataKey = Math.random()+"a";
        this.datas = data;
    }
}
