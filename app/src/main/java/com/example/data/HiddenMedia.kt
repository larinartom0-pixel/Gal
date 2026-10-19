package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_media")
data class HiddenMedia(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalFilename: String,
    val originalUri: String?,
    val internalPath: String,
    val mimeType: String,
    val size: Long,
    val dateHidden: Long = System.currentTimeMillis(),
    val isVideo: Boolean
)
