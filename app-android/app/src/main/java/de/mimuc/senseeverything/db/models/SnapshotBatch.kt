package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Temporary storage for snapshot batches during cross-process transfer.
 * This is a staging table - data is deleted after consumption by UITreeSensor.
 */
@Entity(tableName = "snapshot_batches")
data class SnapshotBatch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "json_data")
    val jsonData: String,

    @ColumnInfo(name = "count")
    val count: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
