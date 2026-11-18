package de.mimuc.senseeverything.db.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// The upload reads the backlog in timestamp order in batches (deleting each uploaded batch), and the
// unsynced counts filter by sync state and time; both are served by this index.
//
// The String columns stay nullable on purpose: the Java entity created them without NOT NULL, and
// making them non-null would change the schema hash (see schemas/.../21.json) and require a migration.
@Entity(indices = [Index(value = ["synced", "timestamp"])])
class LogData(
    // Room reads rows through this constructor, so reads don't generate a throwaway UUID.
    @PrimaryKey(autoGenerate = true) var id: Int,
    var timestamp: Long,
    var sensorName: String?,
    var synced: Boolean,
    var data: String?,
    var hasFile: Boolean,
    var filePath: String?,
    var localId: String?,
) {
    // For creating new rows from app code; @JvmOverloads keeps the 3- and 5-arg forms Java callers use.
    @Ignore
    @JvmOverloads
    constructor(
        timestamp: Long,
        sensorName: String?,
        data: String?,
        hasFile: Boolean = false,
        filePath: String? = null,
    ) : this(
        id = 0,
        timestamp = timestamp,
        sensorName = sensorName,
        synced = false,
        data = data,
        hasFile = hasFile,
        filePath = filePath,
        localId = UUID.randomUUID().toString(),
    )
}
