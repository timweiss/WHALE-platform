package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingQuestionnaireDao {
    @Query("SELECT * FROM pending_questionnaire")
    fun getAll(): List<PendingQuestionnaire>

    @Query("SELECT * FROM pending_questionnaire WHERE valid_until > :now OR valid_until = -1")
    fun getAllNotExpired(now: Long): List<PendingQuestionnaire>

    @Query("SELECT * FROM pending_questionnaire WHERE uid = :uid")
    fun getById(uid: Long): PendingQuestionnaire?

    @Insert
    fun insert(pendingQuestionnaire: PendingQuestionnaire): Long

    @Delete
    fun delete(pendingQuestionnaire: PendingQuestionnaire)

    @Update
    fun update(pendingQuestionnaire: PendingQuestionnaire)

    @Query("DELETE FROM pending_questionnaire WHERE uid = :uid")
    fun deleteById(uid: Long)

    @Query("DELETE FROM pending_questionnaire WHERE valid_until < :now")
    fun deleteExpired(now: Long)
}