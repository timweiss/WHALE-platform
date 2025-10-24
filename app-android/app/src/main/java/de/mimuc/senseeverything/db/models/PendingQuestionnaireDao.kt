package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PendingQuestionnaireDao {
    @Query("SELECT * FROM pending_questionnaire")
    fun getAll(): List<PendingQuestionnaire>

    @Query("SELECT * FROM pending_questionnaire WHERE (valid_until > :now OR valid_until = -1) AND status != 'COMPLETED'")
    fun getAllNotExpiredFlow(now: Long): Flow<List<PendingQuestionnaire>>

    @Query("SELECT * FROM pending_questionnaire WHERE uid = :uid")
    fun getById(uid: UUID): PendingQuestionnaire?

    @Insert
    fun insert(pendingQuestionnaire: PendingQuestionnaire)

    @Delete
    fun delete(pendingQuestionnaire: PendingQuestionnaire)

    @Update
    fun update(pendingQuestionnaire: PendingQuestionnaire)

    @Query("DELETE FROM pending_questionnaire WHERE uid = :uid")
    fun deleteById(uid: UUID)

    @Query("DELETE FROM pending_questionnaire WHERE valid_until < :now AND valid_until != -1")
    fun deleteExpired(now: Long)

    @Query("DELETE FROM pending_questionnaire")
    fun deleteAll()
}