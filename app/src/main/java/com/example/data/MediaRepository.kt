package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(
    private val context: Context,
    private val dao: HiddenMediaDao,
    private val settingsManager: SettingsManager = SettingsManager.getInstance(context)
) {
    val hiddenVideos: Flow<List<HiddenMedia>> = dao.getMediaByType(isVideo = true)
    val hiddenPhotos: Flow<List<HiddenMedia>> = dao.getMediaByType(isVideo = false)

    suspend fun getMediaById(id: Int): HiddenMedia? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    private fun saveOriginalPath(uniqueName: String, originalPath: String) {
        try {
            val backupFile = File(context.filesDir, "original_paths_backup.txt")
            backupFile.appendText("$uniqueName|$originalPath\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOriginalPath(uniqueName: String): String? {
        try {
            val backupFile = File(context.filesDir, "original_paths_backup.txt")
            if (backupFile.exists()) {
                backupFile.useLines { lines ->
                    for (line in lines) {
                        val parts = line.split("|", limit = 2)
                        if (parts.size == 2 && parts[0] == uniqueName) {
                            return parts[1]
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun importMedia(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var originalName = "file_${System.currentTimeMillis()}"
        var size = 0L
        var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        // Attempt to get metadata from ContentResolver queries
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    originalName = cursor.getString(nameIndex)
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        // Query the absolute file path on disk if available
        var originalPath: String? = null
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) {
                        originalPath = cursor.getString(dataIdx)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Determine if it is video based on MIME type or extension
        val isVideo = mimeType.startsWith("video") || 
                      originalName.endsWith(".mp4", ignoreCase = true) || 
                      originalName.endsWith(".webm", ignoreCase = true) || 
                      originalName.endsWith(".mkv", ignoreCase = true) || 
                      originalName.endsWith(".avi", ignoreCase = true) || 
                      originalName.endsWith(".mov", ignoreCase = true) || 
                      originalName.endsWith(".flv", ignoreCase = true) || 
                      originalName.endsWith(".3gp", ignoreCase = true)

        if (isVideo && !mimeType.startsWith("video")) {
            mimeType = if (originalName.endsWith(".webm", ignoreCase = true)) "video/webm" else "video/mp4"
        } else if (!isVideo && !mimeType.startsWith("image")) {
            mimeType = "image/jpeg"
        }

        val baseWorkDir = File(settingsManager.workingDirectory.value)
        val targetFolder = File(baseWorkDir, if (isVideo) "hidden_videos" else "hidden_photos")
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val uniqueName = "${timestamp}_${safeName}"
        val targetFile = File(targetFolder, uniqueName)

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                    if (size == 0L) {
                        size = targetFile.length()
                    }
                }
            } ?: return@withContext false

            val hiddenMedia = HiddenMedia(
                originalFilename = originalName,
                originalUri = uri.toString(),
                internalPath = targetFile.absolutePath,
                mimeType = mimeType,
                size = size,
                isVideo = isVideo
            )

            val rowId = dao.insert(hiddenMedia)
            
            // Save original path if we queried it
            if (originalPath != null) {
                saveOriginalPath(uniqueName, originalPath!!)
            }

            // Delete original file to make it "disappear" from the public gallery!
            try {
                if (originalPath != null) {
                    val origFile = File(originalPath!!)
                    if (origFile.exists()) {
                        origFile.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Deep system media-scanner scan to remove old public references
            if (originalPath != null) {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(originalPath), null, null)
            }

            return@withContext rowId > 0
        } catch (e: Exception) {
            e.printStackTrace()
            // Clean up file if failed
            if (targetFile.exists()) {
                targetFile.delete()
            }
            return@withContext false
        }
    }

    suspend fun restoreMedia(media: HiddenMedia): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(media.internalPath)
        if (!sourceFile.exists()) {
            // File missing on disk, clear from DB to stay clean
            dao.delete(media)
            return@withContext false
        }

        var restoreSuccessful = false
        var restoredPath: String? = null

        // Try restoring back to its exact original folder path
        val originalPath = getOriginalPath(sourceFile.name)
        if (originalPath != null) {
            try {
                val destFile = File(originalPath)
                val parent = destFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                restoredPath = originalPath
                restoreSuccessful = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!restoreSuccessful) {
            // Standard fallback using MediaStore content APIs
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, media.originalFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
                    if (media.isVideo) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/gal_restored")
                    } else {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/gal_restored")
                    }
                }

                val collectionUri = if (media.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val insertedUri = resolver.insert(collectionUri, contentValues)
                if (insertedUri != null) {
                    resolver.openOutputStream(insertedUri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    // Query absolute path of restored file for scanning
                    try {
                        val projection = arrayOf(MediaStore.MediaColumns.DATA)
                        resolver.query(insertedUri, projection, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                if (dataIdx != -1) {
                                    restoredPath = cursor.getString(dataIdx)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    restoreSuccessful = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (restoreSuccessful) {
            // Successfully restored! Let's delete the secure internal file and room entry
            if (sourceFile.exists()) {
                sourceFile.delete()
            }
            dao.delete(media)
            
            // Trigger media scanner on restored file path
            restoredPath?.let { path ->
                android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
            return@withContext true
        }

        return@withContext false
    }

    suspend fun deletePermanently(media: HiddenMedia): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(media.internalPath)
        try {
            if (sourceFile.exists()) {
                sourceFile.delete()
            }
            dao.delete(media)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
