package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SocialNetworkContactDao {
    @Query("SELECT * FROM social_network_contacts")
    fun getAll(): List<SocialNetworkContact>

    @Query("SELECT * FROM social_network_contacts WHERE name LIKE '%' || :name || '%'")
    fun findMatchingName(name: String): List<SocialNetworkContact>

    @Insert
    fun insert(socialNetworkContact: SocialNetworkContact): Long

    @Delete
    fun delete(socialNetworkContact: SocialNetworkContact)

    @Query("DELETE FROM social_network_contacts WHERE uid = :uid")
    fun deleteById(uid: Long)
}