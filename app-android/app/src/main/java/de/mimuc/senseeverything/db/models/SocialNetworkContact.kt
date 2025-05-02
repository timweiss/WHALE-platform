package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "social_network_contacts")
data class SocialNetworkContact(
    @PrimaryKey(autoGenerate = true) var uid: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "key") val key: String
) {
}