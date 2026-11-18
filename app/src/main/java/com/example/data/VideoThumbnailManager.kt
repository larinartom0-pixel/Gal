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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Generates and caches ultra-compressed, lightweight video thumbnails (covers).
 *
 * Rules:
 * 1. Uses fast keyframe extraction (`OPTION_CLOSEST_SYNC`) and picks the middle of the video.
 * 2. Rejects overly dark/black frames using a fast 16x16 grid luminance check.
 * 3. Strictly scales covers down to [THUMB_MAX_DIMENSION] (160px) and compresses as low-quality
 *    WebP ([THUMB_WEBP_QUALITY] = 32), producing ~1–3 KB covers ideal for small grid frames.
 */
object VideoThumbnailManager {
    private const val TAG = "VideoThumbnailManager"
    private const val CACHE_SUBDIR = "video_covers"
    private const val CACHE_SUFFIX = "_t160.webp"
    private const val THUMB_MAX_DIMENSION = 160
    private const val THUMB_WEBP_QUALITY = 32
    private val cleanedLegacyCache = AtomicBoolean(false)

    private fun getCoverCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        } else if (cleanedLegacyCache.compareAndSet(false, true)) {
            // Remove older high-res .jpg covers once so cache stays ultra-compact
            dir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(CACHE_SUFFIX) && !file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        }
        return dir
    }

    private fun getCacheFileKey(videoPath: String, fileSize: Long, lastModified: Long): String {
        val rawKey = "$videoPath:$fileSize:$lastModified"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(rawKey.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + CACHE_SUFFIX
    }

    /**
     * Checks if a cached ultra-compressed cover already exists on disk.
     */
    fun getCachedCoverFile(context: Context, videoPath: String, fileSize: Long, lastModified: Long): File? {
        val cacheFile = File(getCoverCacheDir(context), getCacheFileKey(videoPath, fileSize, lastModified))
        return if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
    }

    /**
     * Obtains the video cover file. If not cached, extracts a frame (with dark frame rejection),
     * downscales to 160px max dimension, and saves ultra-compressed WebP to cache.
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

            val candidateFractions = listOf(0.50, 0.35, 0.65, 0.20, 0.80, 0.10, 0.05)
            var bestBitmap: Bitmap? = null
            var bestBrightness = -1.0

            for (fraction in candidateFractions) {
                val timeUs = (durationUs * fraction).toLong().coerceAtLeast(0L)
                val frame = extractLowResFrame(retriever, timeUs)

                if (frame != null) {
                    val blackRatio = calculateBlackPixelRatio(frame)
                    if (blackRatio < 0.75) {
                        bestBitmap?.recycle()
                        bestBitmap = frame
                        break
                    }

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

            if (bestBitmap == null) {
                bestBitmap = extractLowResFrame(retriever, 0L)
            }

            if (bestBitmap != null) {
                val scaled = scaleDownIfNeeded(bestBitmap, maxDimension = THUMB_MAX_DIMENSION)
                val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                FileOutputStream(tempFile).use { out ->
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    scaled.compress(format, THUMB_WEBP_QUALITY, out)
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
            } catch (_: Exception) {
            }
        }
        return@withContext null
    }

    private fun extractLowResFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMB_MAX_DIMENSION,
                THUMB_MAX_DIMENSION
            )
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }

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

                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
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
