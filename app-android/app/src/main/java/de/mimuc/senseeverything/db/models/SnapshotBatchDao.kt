package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Used for cross-process communication between AccessibilityService and main app.
 */
@Dao
interface SnapshotBatchDao {
    @Insert
    suspend fun insert(batch: SnapshotBatch): Long

    @Query("SELECT * FROM snapshot_batches WHERE id = :id")
    suspend fun getById(id: Long): SnapshotBatch?

    @Query("DELETE FROM snapshot_batches WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM snapshot_batches WHERE created_at < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM snapshot_batches")
    suspend fun getCount(): Int
}
