package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.helpers.getRandomString

@Entity(tableName = "generated_keys")
data class GeneratedKey(
    @PrimaryKey(autoGenerate = true) var uid: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "key") val key: String
) {
    companion object {
        fun createEntry(name: String): GeneratedKey {
            return GeneratedKey(
                0,
                System.currentTimeMillis(),
                name,
                getRandomString(32)
            )
        }
    }
}