package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationTriggerDao {
    @Query("SELECT * FROM notification_trigger")
    fun getAll(): List<NotificationTrigger>

    @Query("SELECT * FROM notification_trigger WHERE valid_from <= :timestamp ORDER BY valid_from DESC LIMIT 1")
    fun getLast(timestamp: Long): NotificationTrigger?

    @Insert()
    fun insert(notificationTrigger: NotificationTrigger): Long
}