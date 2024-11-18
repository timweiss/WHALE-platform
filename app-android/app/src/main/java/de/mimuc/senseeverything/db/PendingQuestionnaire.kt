package de.mimuc.senseeverything.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_questionnaire")
data class PendingQuestionnaire(
    @PrimaryKey(autoGenerate = true) val uid: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "valid_until") val validUntil: Long,
    @ColumnInfo(name = "questionnaire_json") val questionnaireJson: String
)
