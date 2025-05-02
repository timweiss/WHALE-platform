package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GeneratedKeyDao {
    @Query("SELECT * FROM generated_keys WHERE name = :name")
    fun getByName(name: String): GeneratedKey?

    @Insert
    fun insert(generatedKey: GeneratedKey): Long

    @Delete
    fun delete(generatedKey: GeneratedKey)

    @Query("DELETE FROM pending_questionnaire")
    fun deleteAll()
}