package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenMediaDao {
    @Query("SELECT * FROM hidden_media ORDER BY dateHidden DESC")
    fun getAllHiddenMedia(): Flow<List<HiddenMedia>>

    @Query("SELECT * FROM hidden_media WHERE isVideo = :isVideo ORDER BY dateHidden DESC")
    fun getMediaByType(isVideo: Boolean): Flow<List<HiddenMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: HiddenMedia): Long

    @Delete
    suspend fun delete(media: HiddenMedia)

    @Query("DELETE FROM hidden_media WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM hidden_media WHERE id = :id")
    suspend fun getById(id: Int): HiddenMedia?
}
