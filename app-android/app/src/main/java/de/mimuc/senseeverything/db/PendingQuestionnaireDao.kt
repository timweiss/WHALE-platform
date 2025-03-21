package de.mimuc.senseeverything.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingQuestionnaireDao {
    @Query("SELECT * FROM pending_questionnaire")
    fun getAll(): List<PendingQuestionnaire>

    @Query("SELECT * FROM pending_questionnaire WHERE valid_until > :now")
    fun getAllNotExpired(now: Long): List<PendingQuestionnaire>

    @Insert
    fun insert(pendingQuestionnaire: PendingQuestionnaire): Long

    @Delete
    fun delete(pendingQuestionnaire: PendingQuestionnaire)

    @Query("DELETE FROM pending_questionnaire WHERE uid = :uid")
    fun deleteById(uid: Int)

    @Query("DELETE FROM pending_questionnaire WHERE valid_until < :now")
    fun deleteExpired(now: Long)
}