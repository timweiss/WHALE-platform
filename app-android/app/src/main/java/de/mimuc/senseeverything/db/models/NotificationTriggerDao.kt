package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.util.UUID

@Dao
interface NotificationTriggerDao {
    @Query("SELECT * FROM notification_trigger")
    fun getAll(): List<NotificationTrigger>

    @Query("SELECT * FROM notification_trigger WHERE valid_from <= :timestamp ORDER BY valid_from DESC LIMIT 1")
    fun getLast(timestamp: Long): NotificationTrigger?

    @Query("SELECT * FROM notification_trigger WHERE modality = :modality AND valid_from > :timestamp ORDER BY valid_from ASC")
    fun getNextForModality(modality: NotificationTriggerModality, timestamp: Long): List<NotificationTrigger>

    @Query("SELECT * FROM notification_trigger WHERE valid_from BETWEEN :from AND :to ORDER BY valid_from ASC")
    fun getForInterval(from: Long, to: Long): List<NotificationTrigger>

    @Query("SELECT * FROM notification_trigger WHERE uid = :uid")
    fun getById(uid: UUID): NotificationTrigger?

    @Insert()
    fun insert(notificationTrigger: NotificationTrigger): Long

    @Update()
    fun update(notificationTrigger: NotificationTrigger)
}