package de.mimuc.senseeverything.db.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Insert

@Entity(
    primaryKeys = ["logDataId", "flag"],
    foreignKeys = [
        ForeignKey(
            entity = LogData::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("logDataId"),
            onDelete = CASCADE
        )
    ]
)
data class LogDataFlag(
    val logDataId: Int,
    val flag: String
)