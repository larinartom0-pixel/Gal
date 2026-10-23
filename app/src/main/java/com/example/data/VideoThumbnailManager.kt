package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Generates and caches smart video thumbnails (covers).
 * 
 * Rules:
 * 1. Picks the middle of the video (duration / 2).
 * 2. Checks if there is an excess of black color / darkness in the frame.
 *    If > 85% of pixels are dark/black, tries candidate timestamps (25%, 75%, 15%, 40%, 60%, 10%)
 *    to find an informative non-black frame.
 * 3. Compresses and saves the resulting thumbnail into internal cache directory
 *    so it is persistently available across restarts.
 */
object VideoThumbnailManager {
    private const val TAG = "VideoThumbnailManager"
    private const val CACHE_SUBDIR = "video_covers"

    private fun getCoverCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getCacheFileKey(videoPath: String, fileSize: Long, lastModified: Long): String {
        val rawKey = "$videoPath:$fileSize:$lastModified"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(rawKey.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    /**
     * Checks if a cached cover already exists on disk.
     */
    fun getCachedCoverFile(context: Context, videoPath: String, fileSize: Long, lastModified: Long): File? {
        val cacheFile = File(getCoverCacheDir(context), getCacheFileKey(videoPath, fileSize, lastModified))
        return if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
    }

    /**
     * Obtains the video cover file. If not cached, extracts the middle frame (with dark frame rejection)
     * and saves to cache. Returns the File or null if extraction failed.
     */
    suspend fun getOrGenerateCoverFile(
        context: Context,
        videoPath: String,
        fileSize: Long,
        lastModified: Long
    ): File? = withContext(Dispatchers.IO) {
        val cacheFile = File(getCoverCacheDir(context), getCacheFileKey(videoPath, fileSize, lastModified))
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return@withContext cacheFile
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists() || !videoFile.canRead()) {
            return@withContext null
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationUs = (durationStr?.toLongOrNull() ?: 1000L) * 1000L

            // Candidates to try:
            // 1) 50% (middle)
            // 2) 35%, 65%, 20%, 80%, 10%
            val candidateFractions = listOf(0.50, 0.35, 0.65, 0.20, 0.80, 0.10, 0.05)
            var bestBitmap: Bitmap? = null
            var bestBrightness = -1.0

            for (fraction in candidateFractions) {
                val timeUs = (durationUs * fraction).toLong().coerceAtLeast(0L)
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                
                if (frame != null) {
                    val blackRatio = calculateBlackPixelRatio(frame)
                    // If not dominated by black (black ratio < 0.75), we found our ideal cover!
                    if (blackRatio < 0.75) {
                        bestBitmap = frame
                        break
                    }
                    
                    // Keep best candidate if all happen to be somewhat dark
                    val brightnessScore = 1.0 - blackRatio
                    if (brightnessScore > bestBrightness) {
                        bestBrightness = brightnessScore
                        bestBitmap?.recycle()
                        bestBitmap = frame
                    } else {
                        frame.recycle()
                    }
                }
            }

            // Fallback: if all candidate checks failed, try frame at time 0
            if (bestBitmap == null) {
                bestBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512)
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            }

            if (bestBitmap != null) {
                // Scale down slightly if massive to preserve memory & speed up grid rendering
                val scaled = scaleDownIfNeeded(bestBitmap, maxDimension = 512)
                val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                FileOutputStream(tempFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }
                if (scaled != bestBitmap) {
                    scaled.recycle()
                }
                bestBitmap.recycle()

                if (tempFile.renameTo(cacheFile)) {
                    return@withContext cacheFile
                } else if (tempFile.exists()) {
                    return@withContext tempFile
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnail for $videoPath", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return@withContext null
    }

    /**
     * Calculates what percentage of sampled pixels are essentially black/dark.
     * Samples a 16x16 grid across the bitmap for fast performance without full pixel decoding.
     */
    private fun calculateBlackPixelRatio(bitmap: Bitmap): Double {
        val sampleGrid = 16
        var blackCount = 0
        var totalCount = 0

        val stepX = (bitmap.width / sampleGrid).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleGrid).coerceAtLeast(1)

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Standard perceptual luminance formula
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                // Luminance < 25 (out of 255) is regarded as nearly black / dark letterbox
                if (luminance < 25.0) {
                    blackCount++
                }
                totalCount++
            }
        }

        return if (totalCount > 0) blackCount.toDouble() / totalCount.toDouble() else 1.0
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
