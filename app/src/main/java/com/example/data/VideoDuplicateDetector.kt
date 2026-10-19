package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Represents a video file discovered from the filesystem folder or device gallery.
 */
data class VideoItem(
    val id: Int,
    val file: File,
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val hash: String = "",
    val resolution: String = "",
    val durationMs: Long = 0L
) {
    fun toHiddenMedia(): HiddenMedia {
        return HiddenMedia(
            id = id,
            originalFilename = name,
            originalUri = null,
            internalPath = path,
            mimeType = "video/mp4",
            size = size,
            dateHidden = lastModified,
            isVideo = true
        )
    }

    val formattedSize: String
        get() = formatBytes(size)

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSecs = durationMs / 1000
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }
}

/**
 * Type of match between duplicate videos.
 */
enum class DuplicateMatchType {
    EXACT_CONTENT,     // 100% точний збіг байтів або медіа-потоку
    VISUAL_CLONE,      // Візуальний клон (перекодовано, інший бітрейт/дозвіл)
    MODIFIED_VIDEO,    // Модифіковане відео (обрізано, стиснуто або змінено колір)
    FRAME_SIMILARITY   // Зворотна сумісність для кадрового співпадіння
}

/**
 * Represents a group of duplicate videos discovered by content, size, or deep frame similarity.
 */
data class DuplicateGroup(
    val id: String,
    val size: Long,
    val hash: String,
    val items: List<VideoItem>,
    val matchType: DuplicateMatchType = DuplicateMatchType.EXACT_CONTENT,
    val similarityScore: Int = 100, // 0..100%
    val matchDescription: String = "Ідентичний вміст файлів"
) {
    val formattedSize: String
        get() = formatBytes(size)

    val wastedBytes: Long
        get() = if (items.size > 1) {
            val sortedSizes = items.map { it.size }.sortedDescending()
            sortedSizes.drop(1).sum()
        } else 0L

    val formattedWastedSize: String
        get() = formatBytes(wastedBytes)
}

/**
 * Scanning modes for duplicate detection.
 */
enum class DetectionMode {
    DEEP_FRAME_SCAN, // Розумний: аналіз кадрів (dHash + колір), тривалості, потоку та хешу
    FAST_HASH_ONLY   // Швидкий: паралельний хеш байтів, медіа-потоку та швидких опорних кадрів
}

/**
 * Real-time progress updates with CPU load, elapsed time, and time remaining countdown.
 */
data class DetectionProgress(
    val current: Int,
    val total: Int,
    val currentFileName: String,
    val stage: String,
    val percentage: Float,
    val remainingSeconds: Int = 1,
    val elapsedMs: Long = 0L,
    val speedVideosPerSec: Float = 0f,
    val activeCores: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)
)

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

/**
 * Scans either a specific directory (including hidden/.nomedia subdirectories)
 * or the entire device gallery (MediaStore + public video directories + active folder).
 */
object VideoFolderScanner {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "ts"
    )

    fun isVideoFile(file: File): Boolean {
        return file.extension.lowercase() in VIDEO_EXTENSIONS
    }

    /**
     * Scans a specific user-selected folder recursively (includes hidden folders and .nomedia).
     */
    fun scanVideos(directory: File, maxDepth: Int = 10): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        if (!directory.exists() || !directory.isDirectory) return result

        fun recurse(dir: File, currentDepth: Int) {
            if (currentDepth > maxDepth) return
            val children = dir.listFiles() ?: return

            for (file in children) {
                if (file.isDirectory) {
                    recurse(file, currentDepth + 1)
                } else if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in VIDEO_EXTENSIONS && file.length() > 0) {
                        result.add(
                            VideoItem(
                                id = file.absolutePath.hashCode(),
                                file = file,
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        }

        recurse(directory, 0)
        return result.sortedByDescending { it.lastModified }
    }

    /**
     * Checks if a file or any of its parent folders is hidden (starts with '.') or contains a '.nomedia' marker.
     */
    private fun isInsideHiddenOrNomediaFolder(file: File): Boolean {
        if (file.name.startsWith(".")) return true
        var current: File? = file.parentFile
        var steps = 0
        while (current != null && steps < 12) {
            val name = current.name
            if (name.startsWith(".")) return true
            if (File(current, ".nomedia").exists()) return true
            if (name == "0" || name == "emulated" || name == "storage") break
            current = current.parentFile
            steps++
        }
        return false
    }

    /**
     * Scans standard device Gallery videos using Android MediaStore + standard public media folders.
     * Strictly skips all hidden folders (starting with '.') and folders with '.nomedia' so it works
     * exactly like a standard phone gallery.
     */
    fun scanGalleryVideos(context: Context): List<VideoItem> {
        val itemsByPath = LinkedHashMap<String, VideoItem>()

        // 1. Fast query via Android MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        try {
            val projection = arrayOf(
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    if (!file.exists() || !file.isFile || file.length() <= 0L) continue
                    // Never touch hidden folders or .nomedia folders in Standard Gallery mode
                    if (isInsideHiddenOrNomediaFolder(file)) continue

                    val name = cursor.getString(nameCol) ?: file.name
                    val size = file.length().let { if (it > 0L) it else cursor.getLong(sizeCol) }
                    val dateSec = cursor.getLong(dateCol)
                    val lastMod = if (dateSec > 0L) dateSec * 1000L else file.lastModified()
                    val durMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val w = cursor.getInt(wCol)
                    val h = cursor.getInt(hCol)
                    val res = if (w > 0 && h > 0) "${w}x${h}" else ""

                    itemsByPath[file.absolutePath] = VideoItem(
                        id = file.absolutePath.hashCode(),
                        file = file,
                        name = name,
                        path = file.absolutePath,
                        size = size,
                        lastModified = lastMod,
                        resolution = res,
                        durationMs = durMs
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 2. Also scan standard public gallery folders (DCIM, Movies, Download, Pictures)
        // strictly skipping any hidden ('.') or '.nomedia' folders
        val searchDirs = LinkedHashSet<File>()
        try {
            listOf(
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_PICTURES
            ).forEach { type ->
                val d = Environment.getExternalStoragePublicDirectory(type)
                if (d != null && d.exists() && d.isDirectory) {
                    searchDirs.add(d)
                }
            }
        } catch (_: Exception) {
        }

        fun scanPublicDir(dir: File, depth: Int) {
            if (depth > 5) return
            if (dir.name.startsWith(".")) return
            if (File(dir, ".nomedia").exists()) return
            val children = dir.listFiles() ?: return
            if (children.any { it.name.equals(".nomedia", ignoreCase = true) }) return

            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    scanPublicDir(child, depth + 1)
                } else if (child.isFile) {
                    val absPath = child.absolutePath
                    if (!itemsByPath.containsKey(absPath)) {
                        val ext = child.extension.lowercase()
                        if (ext in VIDEO_EXTENSIONS && child.length() > 0L) {
                            itemsByPath[absPath] = VideoItem(
                                id = absPath.hashCode(),
                                file = child,
                                name = child.name,
                                path = absPath,
                                size = child.length(),
                                lastModified = child.lastModified()
                            )
                        }
                    }
                }
            }
        }

        for (dir in searchDirs) {
            scanPublicDir(dir, 0)
        }

        return itemsByPath.values.sortedByDescending { it.lastModified }
    }
}

/**
 * Perceptual + color signature of a single sampled video frame.
 */
data class FrameSignature(
    val samplePosition: Float, // 0.0..1.0 relative position in video
    val hHash: Long,           // 64-bit horizontal gradient hash (9x8)
    val vHash: Long,           // 64-bit vertical gradient hash (8x9)
    val avgR: Int,
    val avgG: Int,
    val avgB: Int,
    val lumaSpread: Int        // maxLuma - minLuma (contrast)
) {
    val isInformative: Boolean
        get() = lumaSpread >= 10
}

/**
 * Fingerprint of a video containing content hashes and perceptual frame signatures.
 */
data class VideoFingerprint(
    val item: VideoItem,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameHashes: List<Long>,
    val contentHash: String,
    val bodyHash: String = "",
    val frameSignatures: List<FrameSignature> = emptyList()
)

/**
 * Result of comparing two video fingerprints.
 */
data class FrameComparisonResult(
    val isMatch: Boolean,
    val similarityScore: Int, // 0..100
    val matchType: DuplicateMatchType,
    val description: String
)

/**
 * Multi-stage parallel video duplicate detector utilizing all CPU cores.
 * Finds:
 * 1. 100% exact file copies (by 5-point SHA-256 content hash).
 * 2. Remuxed / re-saved copies with different container headers (by middle media-stream SHA-256 bodyHash).
 * 3. Visual clones & re-encoded copies (different resolution/bitrate/filesize).
 * 4. Slightly modified / trimmed / compressed videos (using 8-keyframe dual dHash + RGB color alignment).
 */
object VideoDuplicateDetector {

    suspend fun findDuplicates(
        videos: List<VideoItem>,
        mode: DetectionMode = DetectionMode.DEEP_FRAME_SCAN,
        onProgress: ((DetectionProgress) -> Unit)? = null
    ): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        if (videos.size < 2) {
            onProgress?.invoke(
                DetectionProgress(
                    current = videos.size,
                    total = videos.size,
                    currentFileName = "Готово",
                    stage = "Перевірка завершена",
                    percentage = 1f,
                    remainingSeconds = 0,
                    elapsedMs = 100L
                )
            )
            return@withContext emptyList()
        }

        if (mode == DetectionMode.FAST_HASH_ONLY) {
            return@withContext findFastDuplicates(videos, onProgress)
        }

        return@withContext findDeepFrameDuplicates(videos, onProgress)
    }

    fun findDuplicatesSync(
        videos: List<VideoItem>,
        mode: DetectionMode = DetectionMode.DEEP_FRAME_SCAN,
        onProgress: ((DetectionProgress) -> Unit)? = null
    ): List<DuplicateGroup> = runBlocking(Dispatchers.Default) {
        findDuplicates(videos, mode, onProgress)
    }

    /**
     * FAST_HASH_ONLY mode ("Швидкий (хеші)"):
     * Parallel across all CPU cores:
     * 1. Computes full Content Hash AND header-independent Media Body Hash on ALL videos (not just identical byte size!),
     *    so copies with slightly modified MP4 headers/tags are still caught!
     * 2. For videos with close size (within 15%) or close duration (within 3s), extracts a fast 4-frame dHash
     *    so re-saved copies are also detected quickly!
     */
    private suspend fun findFastDuplicates(
        videos: List<VideoItem>,
        onProgress: ((DetectionProgress) -> Unit)?
    ): List<DuplicateGroup> = coroutineScope {
        val startTime = System.currentTimeMillis()
        val total = videos.size
        val availableCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)
        val concurrency = max(availableCores, 4)
        val semaphore = Semaphore(concurrency)
        val processedCounter = AtomicInteger(0)

        // Step 1: Compute fullHash + bodyHash + fast metadata for ALL videos in parallel
        val hashedFps = videos.map { video ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    val cHash = computeContentHash(video.file)
                    val bHash = computeMediaBodyHash(video.file)
                    var durMs = video.durationMs
                    var w = 0
                    var h = 0
                    if (video.resolution.contains("x")) {
                        val parts = video.resolution.split("x")
                        if (parts.size == 2) {
                            w = parts[0].toIntOrNull() ?: 0
                            h = parts[1].toIntOrNull() ?: 0
                        }
                    }
                    if (durMs <= 0L && video.file.exists()) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(video.file.absolutePath)
                            durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            if (w <= 0 || h <= 0) {
                                w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { retriever.release() } catch (_: Exception) {}
                        }
                    }

                    val done = processedCounter.incrementAndGet()
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(50L)
                    val speed = (done.toFloat() / (elapsedMs / 1000f)).coerceAtLeast(2f)
                    val remaining = (total - done).coerceAtLeast(0)
                    val remainingSec = if (remaining == 0) 0 else max(1, (remaining / speed).roundToInt())

                    if (done % 2 == 0 || done == total) {
                        onProgress?.invoke(
                            DetectionProgress(
                                current = done,
                                total = total,
                                currentFileName = video.name,
                                stage = "Швидкий хеш-аналіз ($concurrency потоків)...",
                                percentage = (done.toFloat() / total.toFloat() * 0.70f).coerceIn(0.1f, 0.70f),
                                remainingSeconds = remainingSec,
                                elapsedMs = elapsedMs,
                                speedVideosPerSec = speed,
                                activeCores = concurrency
                            )
                        )
                    }

                    val resStr = if (w > 0 && h > 0) "${w}x${h}" else video.resolution
                    val updatedItem = video.copy(hash = cHash, durationMs = durMs, resolution = resStr)
                    VideoFingerprint(
                        item = updatedItem,
                        durationMs = durMs,
                        width = w,
                        height = h,
                        frameHashes = emptyList(),
                        contentHash = cHash,
                        bodyHash = bHash,
                        frameSignatures = emptyList()
                    )
                }
            }
        }.awaitAll().toMutableList()

        // Step 2: Check if any pairs have close size (within 25%) or close duration (within 4s) and do a quick 4-frame hash
        val needsQuickFrames = BooleanArray(hashedFps.size)
        for (i in hashedFps.indices) {
            for (j in i + 1 until hashedFps.size) {
                val a = hashedFps[i]
                val b = hashedFps[j]
                val exactMatch = (a.contentHash.isNotEmpty() && a.contentHash == b.contentHash) ||
                        (a.bodyHash.isNotEmpty() && a.bodyHash == b.bodyHash && a.item.size == b.item.size)
                if (!exactMatch && arePotentialVisualCandidates(a, b)) {
                    val minS = min(a.item.size, b.item.size).coerceAtLeast(1L)
                    val maxS = max(a.item.size, b.item.size)
                    val durDiff = abs(a.durationMs - b.durationMs)
                    if (maxS.toDouble() / minS.toDouble() <= 3.0 || durDiff <= 3000L) {
                        needsQuickFrames[i] = true
                        needsQuickFrames[j] = true
                    }
                }
            }
        }

        val quickIndices = needsQuickFrames.indices.filter { needsQuickFrames[it] }
        if (quickIndices.isNotEmpty()) {
            val codecSem = Semaphore(min(concurrency, 4))
            val quickUpdated = quickIndices.map { idx ->
                async(Dispatchers.Default) {
                    codecSem.withPermit {
                        val fp = hashedFps[idx]
                        val sigs = extractRichFrameSignatures(
                            file = fp.item.file,
                            durationMs = fp.durationMs,
                            sampleFractions = listOf(0.18, 0.40, 0.62, 0.84)
                        )
                        idx to fp.copy(
                            frameHashes = sigs.map { it.hHash },
                            frameSignatures = sigs
                        )
                    }
                }
            }.awaitAll()
            for ((idx, updated) in quickUpdated) {
                hashedFps[idx] = updated
            }
        }

        val groups = clusterFingerprintsIntoGroups(hashedFps)

        val finalElapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(80L)
        onProgress?.invoke(
            DetectionProgress(
                current = total,
                total = total,
                currentFileName = "Готово",
                stage = "Вердикт сформовано",
                percentage = 1.0f,
                remainingSeconds = 0,
                elapsedMs = finalElapsed,
                speedVideosPerSec = total.toFloat() / (finalElapsed / 1000f),
                activeCores = concurrency
            )
        )

        groups
    }

    /**
     * DEEP_FRAME_SCAN mode ("Розумний (кадри)"):
     * Stage 1: Fast parallel header, 5-point SHA-256 content-hash, and media body-hash scan.
     * Stage 2: Extracts 8 keyframes across all candidate videos in parallel.
     * Stage 3: Smart perceptual + RGB color + temporal alignment clustering.
     */
    private suspend fun findDeepFrameDuplicates(
        videos: List<VideoItem>,
        onProgress: ((DetectionProgress) -> Unit)?
    ): List<DuplicateGroup> = coroutineScope {
        val total = videos.size
        val availableCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)
        val concurrency = max(availableCores, 4)
        val semaphore = Semaphore(concurrency)
        val startTime = System.currentTimeMillis()
        val stage1Counter = AtomicInteger(0)

        // Stage 1: Rapid metadata header & content hash extraction
        val headerFingerprints = videos.map { video ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    val fp = extractFingerprint(video, sampleFrames = false)
                    val done = stage1Counter.incrementAndGet()
                    if (done % 2 == 0 || done == total) {
                        val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(50L)
                        val speed = (done.toFloat() / (elapsedMs / 1000f)).coerceAtLeast(4f)
                        val rem = ((total - done) / speed).roundToInt() + 1
                        onProgress?.invoke(
                            DetectionProgress(
                                current = done,
                                total = total,
                                currentFileName = video.name,
                                stage = "Етап 1/2: Перевірка хешів і параметрів відео...",
                                percentage = (done.toFloat() / total.toFloat()) * 0.35f,
                                remainingSeconds = rem.coerceIn(1, 45),
                                elapsedMs = elapsedMs,
                                speedVideosPerSec = speed,
                                activeCores = concurrency
                            )
                        )
                    }
                    fp
                }
            }
        }.awaitAll()

        // Find which videos need visual frame extraction
        val needsFrameScan = BooleanArray(headerFingerprints.size)
        for (i in headerFingerprints.indices) {
            val fpA = headerFingerprints[i]
            for (j in i + 1 until headerFingerprints.size) {
                val fpB = headerFingerprints[j]
                if (arePotentialVisualCandidates(fpA, fpB)) {
                    needsFrameScan[i] = true
                    needsFrameScan[j] = true
                }
            }
        }

        val candidateIndices = needsFrameScan.indices.filter { needsFrameScan[it] }
        val totalCandidates = candidateIndices.size
        val stage2Counter = AtomicInteger(0)
        val stage2StartMs = System.currentTimeMillis()

        val codecSemaphore = Semaphore(min(concurrency, 4))
        val finalFingerprints = headerFingerprints.toMutableList()

        if (totalCandidates > 0) {
            val updatedCandidates = candidateIndices.map { idx ->
                async(Dispatchers.Default) {
                    codecSemaphore.withPermit {
                        val baseFp = headerFingerprints[idx]
                        val signatures = extractRichFrameSignatures(baseFp.item.file, baseFp.durationMs)
                        val done = stage2Counter.incrementAndGet()

                        if (done % 2 == 0 || done == totalCandidates) {
                            val totalElapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                            val stage2ElapsedSec = ((System.currentTimeMillis() - stage2StartMs).coerceAtLeast(100L)) / 1000f
                            val stage2Speed = (done.toFloat() / stage2ElapsedSec).coerceAtLeast(1.5f)
                            val remainingCandidates = (totalCandidates - done).coerceAtLeast(0)
                            val remSec = if (remainingCandidates == 0) 0 else max(1, (remainingCandidates / stage2Speed).roundToInt()).coerceAtMost(60)
                            val overallCurrent = ((done.toFloat() / totalCandidates.toFloat()) * total).roundToInt().coerceIn(1, total)

                            onProgress?.invoke(
                                DetectionProgress(
                                    current = overallCurrent,
                                    total = total,
                                    currentFileName = baseFp.item.name,
                                    stage = "Етап 2/2: Покадровий нейро-хеш аналіз ($done/$totalCandidates)...",
                                    percentage = (0.35f + (done.toFloat() / totalCandidates.toFloat()) * 0.60f).coerceIn(0.35f, 0.95f),
                                    remainingSeconds = remSec,
                                    elapsedMs = totalElapsedMs,
                                    speedVideosPerSec = (overallCurrent.toFloat() / (totalElapsedMs / 1000f)).coerceAtLeast(1f),
                                    activeCores = concurrency
                                )
                            )
                        }
                        idx to baseFp.copy(
                            frameHashes = signatures.map { it.hHash },
                            frameSignatures = signatures
                        )
                    }
                }
            }.awaitAll()

            for ((idx, updatedFp) in updatedCandidates) {
                finalFingerprints[idx] = updatedFp
            }
        }

        val duplicateGroups = clusterFingerprintsIntoGroups(finalFingerprints)

        val finalElapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
        val finalSpeed = (total.toFloat() / (finalElapsed / 1000f)).coerceAtLeast(1f)

        onProgress?.invoke(
            DetectionProgress(
                current = total,
                total = total,
                currentFileName = "Готово",
                stage = "Аналіз завершено",
                percentage = 1.0f,
                remainingSeconds = 0,
                elapsedMs = finalElapsed,
                speedVideosPerSec = finalSpeed,
                activeCores = concurrency
            )
        )

        duplicateGroups
    }

    /**
     * Groups matched video fingerprints into DuplicateGroups anchored by highest quality video.
     */
    private fun clusterFingerprintsIntoGroups(
        finalFingerprints: List<VideoFingerprint>
    ): List<DuplicateGroup> {
        val pairMatchDetails = mutableMapOf<Pair<Int, Int>, FrameComparisonResult>()
        for (i in 0 until finalFingerprints.size) {
            for (j in i + 1 until finalFingerprints.size) {
                val fpA = finalFingerprints[i]
                val fpB = finalFingerprints[j]
                val res = compareFingerprints(fpA, fpB)
                if (res.isMatch) {
                    pairMatchDetails[Pair(i, j)] = res
                }
            }
        }

        // Sort indices by quality (resolution + size) descending so highest quality video is the group anchor
        val sortedIndices = finalFingerprints.indices.sortedWith(
            compareByDescending<Int> { finalFingerprints[it].width * finalFingerprints[it].height }
                .thenByDescending { finalFingerprints[it].item.size }
        )

        val assigned = BooleanArray(finalFingerprints.size)
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for (anchorIdx in sortedIndices) {
            if (assigned[anchorIdx]) continue

            val groupMembers = mutableListOf(anchorIdx)
            for (candidateIdx in sortedIndices) {
                if (candidateIdx == anchorIdx || assigned[candidateIdx]) continue

                // Candidate joins if it directly matches the anchor video (or matches an existing member with >= 92% similarity)
                val anchorKey = if (anchorIdx < candidateIdx) Pair(anchorIdx, candidateIdx) else Pair(candidateIdx, anchorIdx)
                val matchesAnchor = pairMatchDetails.containsKey(anchorKey)
                val matchesStrongMember = !matchesAnchor && groupMembers.any { memberIdx ->
                    val k = if (memberIdx < candidateIdx) Pair(memberIdx, candidateIdx) else Pair(candidateIdx, memberIdx)
                    val m = pairMatchDetails[k]
                    m != null && m.similarityScore >= 92
                }

                if (matchesAnchor || matchesStrongMember) {
                    groupMembers.add(candidateIdx)
                }
            }

            if (groupMembers.size >= 2) {
                groupMembers.forEach { assigned[it] = true }

                val clusterFps = groupMembers.map { finalFingerprints[it] }
                val sortedFps = clusterFps.sortedWith(
                    compareByDescending<VideoFingerprint> { it.width * it.height }
                        .thenByDescending { it.item.size }
                )
                val items = sortedFps.map { it.item }

                val allExactByteMatches = clusterFps.all {
                    it.contentHash.isNotEmpty() && it.contentHash == clusterFps[0].contentHash
                }

                var scoreSum = 0
                var scoreCount = 0
                var hasModified = false
                for (a in groupMembers) {
                    for (b in groupMembers) {
                        if (a < b) {
                            val details = pairMatchDetails[Pair(a, b)]
                            if (details != null) {
                                scoreSum += details.similarityScore
                                scoreCount++
                                if (details.matchType == DuplicateMatchType.MODIFIED_VIDEO) {
                                    hasModified = true
                                }
                            }
                        }
                    }
                }
                val avgScore = if (scoreCount > 0) (scoreSum / scoreCount).coerceIn(82, 100) else 100

                val matchType = when {
                    allExactByteMatches -> DuplicateMatchType.EXACT_CONTENT
                    hasModified -> DuplicateMatchType.MODIFIED_VIDEO
                    else -> DuplicateMatchType.VISUAL_CLONE
                }

                val description = when (matchType) {
                    DuplicateMatchType.EXACT_CONTENT -> "Точний збіг байтів та хешу (100%)"
                    DuplicateMatchType.VISUAL_CLONE -> "Візуальний клон ($avgScore% збіг кадрів і кольору)"
                    DuplicateMatchType.MODIFIED_VIDEO -> "Модифікована копія ($avgScore% збіг відеоряду)"
                    DuplicateMatchType.FRAME_SIMILARITY -> "Кадрове співпадіння ($avgScore%)"
                }

                val primaryFp = sortedFps.first()
                val groupId = if (matchType == DuplicateMatchType.EXACT_CONTENT) {
                    "${primaryFp.item.size}_${primaryFp.contentHash.take(12)}"
                } else {
                    "group_${primaryFp.durationMs}_${primaryFp.item.id}"
                }

                duplicateGroups.add(
                    DuplicateGroup(
                        id = groupId,
                        size = primaryFp.item.size,
                        hash = primaryFp.contentHash.ifEmpty { primaryFp.item.id.toString() },
                        items = items,
                        matchType = matchType,
                        similarityScore = avgScore,
                        matchDescription = description
                    )
                )
            }
        }

        return duplicateGroups.sortedByDescending { it.wastedBytes }
    }

    /**
     * Pre-filter for visual/modified duplicate candidates:
     * Allows compressed copies (up to 15x size difference) and trimmed/edited copies (up to 25% or 15s duration diff),
     * while filtering out incompatible orientations (portrait vs landscape) or wildly different durations.
     */
    private fun arePotentialVisualCandidates(fpA: VideoFingerprint, fpB: VideoFingerprint): Boolean {
        if (fpA.item.size == fpB.item.size && fpA.contentHash.isNotEmpty() && fpA.contentHash == fpB.contentHash) {
            return false // Already matched by exact content hash
        }

        // Orientation / aspect ratio check: reject if one is clearly portrait and the other is clearly landscape
        if (fpA.width > 0 && fpA.height > 0 && fpB.width > 0 && fpB.height > 0) {
            val isPortraitA = fpA.height > fpA.width * 1.08
            val isLandscapeA = fpA.width > fpA.height * 1.08
            val isPortraitB = fpB.height > fpB.width * 1.08
            val isLandscapeB = fpB.width > fpB.height * 1.08
            if ((isPortraitA && isLandscapeB) || (isLandscapeA && isPortraitB)) {
                return false
            }
            val ratioA = fpA.width.toDouble() / fpA.height.toDouble()
            val ratioB = fpB.width.toDouble() / fpB.height.toDouble()
            val ratioDiff = abs(ratioA - ratioB) / max(ratioA, ratioB)
            if (ratioDiff > 0.28) return false
        }

        // File size ratio guard: allow up to 15x difference (e.g. 60MB camera original vs 4MB compressed copy)
        val minSize = min(fpA.item.size, fpB.item.size).coerceAtLeast(1L)
        val maxSize = max(fpA.item.size, fpB.item.size)
        if (maxSize.toDouble() / minSize.toDouble() > 15.0) return false

        val durA = fpA.durationMs
        val durB = fpB.durationMs
        if (durA > 0L && durB > 0L) {
            val diffMs = abs(durA - durB)
            val maxDur = max(durA, durB)
            // Allow up to 25% of duration or 15 seconds (and at least 4s tolerance for short clips)
            val allowedDiffMs = min(15_000L, max(4_000L, (maxDur * 0.25).toLong()))
            if (diffMs > allowedDiffMs) return false
        }

        return true
    }

    /**
     * Extracts sampled keyframes across the video and computes 128-bit Dual dHash (Horizontal + Vertical)
     * plus RGB color averages and luminance contrast. Safely handles Android HARDWARE bitmaps.
     */
    private fun extractRichFrameSignatures(
        file: File,
        durationMs: Long,
        sampleFractions: List<Double> = listOf(0.10, 0.21, 0.32, 0.44, 0.56, 0.68, 0.79, 0.90)
    ): List<FrameSignature> {
        val signatures = mutableListOf<FrameSignature>()
        if (!file.exists()) return signatures

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val effectiveDurMs = if (durationMs > 0L) {
                durationMs
            } else {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 4000L
            }
            val durUs = max(500L, effectiveDurMs) * 1000L

            for (fraction in sampleFractions) {
                val timeUs = (durUs * fraction).toLong()
                try {
                    var bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            32,
                            32
                        )
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }

                    var sig = if (bmp != null) {
                        val s = computeFrameSignature(bmp, fraction.toFloat())
                        if (!bmp.isRecycled) bmp.recycle()
                        s
                    } else null

                    // If OPTION_CLOSEST_SYNC snapped to the exact same keyframe as the previous sample,
                    // or landed on a completely dark fade frame, try OPTION_CLOSEST for exact frame
                    val isSameAsPrev = sig != null && signatures.isNotEmpty() &&
                            signatures.last().hHash == sig.hHash && signatures.last().vHash == sig.vHash
                    val isDarkFrame = sig == null || !sig.isInformative

                    if (isSameAsPrev || isDarkFrame) {
                        val preciseBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            retriever.getScaledFrameAtTime(
                                timeUs,
                                MediaMetadataRetriever.OPTION_CLOSEST,
                                32,
                                32
                            )
                        } else {
                            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                        if (preciseBmp != null) {
                            sig = computeFrameSignature(preciseBmp, fraction.toFloat())
                            if (!preciseBmp.isRecycled) preciseBmp.recycle()
                        }
                    }

                    if (sig != null) {
                        signatures.add(sig)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        return signatures
    }

    /**
     * Computes 128-bit Dual dHash (64-bit horizontal + 64-bit vertical) + RGB color + luma contrast.
     * Safely converts HARDWARE bitmaps to ARGB_8888 before reading pixels.
     */
    fun computeFrameSignature(bitmap: Bitmap, samplePosition: Float = 0.5f): FrameSignature {
        val readableBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }

        val scaled = Bitmap.createScaledBitmap(readableBmp, 9, 9, true)
        val softScaled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
        } else {
            scaled
        }

        val pixels = IntArray(81)
        softScaled.getPixels(pixels, 0, 9, 0, 0, 9, 9)

        val luma = Array(9) { IntArray(9) }
        var sumR = 0
        var sumG = 0
        var sumB = 0
        var minLuma = 255
        var maxLuma = 0

        for (y in 0 until 9) {
            for (x in 0 until 9) {
                val px = pixels[y * 9 + x]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                sumR += r
                sumG += g
                sumB += b
                val l = (r * 299 + g * 587 + b * 114) / 1000
                luma[y][x] = l
                if (l < minLuma) minLuma = l
                if (l > maxLuma) maxLuma = l
            }
        }

        // 64-bit Horizontal dHash (rows 0..7, cols 0..7 comparing x and x+1)
        var hHash = 0L
        var bitPos = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luma[y][x + 1] > luma[y][x]) {
                    hHash = hHash or (1L shl bitPos)
                }
                bitPos++
            }
        }

        // 64-bit Vertical dHash (rows 0..7, cols 0..7 comparing y and y+1)
        var vHash = 0L
        bitPos = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luma[y + 1][x] > luma[y][x]) {
                    vHash = vHash or (1L shl bitPos)
                }
                bitPos++
            }
        }

        if (softScaled != scaled && softScaled != bitmap && !softScaled.isRecycled) {
            softScaled.recycle()
        }
        if (scaled != readableBmp && scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }
        if (readableBmp != bitmap && !readableBmp.isRecycled) {
            readableBmp.recycle()
        }

        val totalPixels = 81
        return FrameSignature(
            samplePosition = samplePosition,
            hHash = hHash,
            vHash = vHash,
            avgR = sumR / totalPixels,
            avgG = sumG / totalPixels,
            avgB = sumB / totalPixels,
            lumaSpread = maxLuma - minLuma
        )
    }

    /**
     * Legacy helper for 64-bit horizontal dHash compatibility.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        return computeFrameSignature(bitmap).hHash
    }

    /**
     * Extracts video duration, dimensions, content hash, media body hash, and optionally frame signatures.
     */
    fun extractFingerprint(video: VideoItem, sampleFrames: Boolean = true): VideoFingerprint {
        var durationMs = video.durationMs
        var width = 0
        var height = 0

        if (video.resolution.contains("x")) {
            val parts = video.resolution.split("x")
            if (parts.size == 2) {
                width = parts[0].toIntOrNull() ?: 0
                height = parts[1].toIntOrNull() ?: 0
            }
        }

        if ((durationMs <= 0L || width <= 0 || height <= 0) && video.file.exists()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.file.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: durationMs
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: width
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: height
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }

        val signatures = if (sampleFrames) {
            extractRichFrameSignatures(video.file, durationMs)
        } else {
            emptyList()
        }

        val resStr = if (width > 0 && height > 0) "${width}x${height}" else video.resolution
        val cHash = computeContentHash(video.file)
        val bHash = computeMediaBodyHash(video.file)

        val updatedItem = video.copy(
            resolution = resStr,
            durationMs = durationMs,
            hash = cHash
        )

        return VideoFingerprint(
            item = updatedItem,
            durationMs = durationMs,
            width = width,
            height = height,
            frameHashes = signatures.map { it.hHash },
            contentHash = cHash,
            bodyHash = bHash,
            frameSignatures = signatures
        )
    }

    /**
     * Compares two video fingerprints:
     * 1. Exact content hash match (100% identical file bytes).
     * 2. Media stream body hash match (identical video payload with re-saved container headers).
     * 3. Multi-frame perceptual dual-dHash + RGB color alignment (catches re-encoded, compressed, and trimmed copies
     *    while rejecting unrelated videos).
     */
    fun compareFingerprints(fpA: VideoFingerprint, fpB: VideoFingerprint): FrameComparisonResult {
        // 1. Exact file content hash match
        if (fpA.contentHash.isNotEmpty() && fpA.contentHash == fpB.contentHash && fpA.item.size == fpB.item.size) {
            return FrameComparisonResult(
                isMatch = true,
                similarityScore = 100,
                matchType = DuplicateMatchType.EXACT_CONTENT,
                description = "100% точний збіг байтів та хешу файлу"
            )
        }

        // 2. Media stream body hash match (catches copies where container metadata header/footer changed)
        if (fpA.bodyHash.isNotEmpty() && fpA.bodyHash == fpB.bodyHash) {
            val sizeDiffRatio = abs(fpA.item.size - fpB.item.size).toDouble() / max(1L, max(fpA.item.size, fpB.item.size)).toDouble()
            if (sizeDiffRatio <= 0.05) {
                return FrameComparisonResult(
                    isMatch = true,
                    similarityScore = 99,
                    matchType = DuplicateMatchType.EXACT_CONTENT,
                    description = "Збіг хешу медіа-потоку (99%)"
                )
            }
        }

        if (!arePotentialVisualCandidates(fpA, fpB)) {
            return FrameComparisonResult(
                isMatch = false,
                similarityScore = 0,
                matchType = DuplicateMatchType.FRAME_SIMILARITY,
                description = "Різні параметри відео"
            )
        }

        // Filter out completely black/uniform fade frames if informative frames exist
        val informativeA = fpA.frameSignatures.filter { it.isInformative }
        val informativeB = fpB.frameSignatures.filter { it.isInformative }
        val sigsA = if (informativeA.size >= 2) informativeA else fpA.frameSignatures
        val sigsB = if (informativeB.size >= 2) informativeB else fpB.frameSignatures

        if (sigsA.size < 2 || sigsB.size < 2) {
            // Fallback to legacy frameHashes if only raw 64-bit hashes were provided
            if (fpA.frameHashes.size >= 2 && fpB.frameHashes.size >= 2) {
                return compareRawHashesFallback(fpA, fpB)
            }
            return FrameComparisonResult(
                isMatch = false,
                similarityScore = 0,
                matchType = DuplicateMatchType.FRAME_SIMILARITY,
                description = "Недостатньо кадрів для порівняння"
            )
        }

        var matchedFrames = 0
        var strongMatchedFrames = 0
        var totalAvg64Dist = 0.0
        var usedInB = BooleanArray(sigsB.size)

        for ((idxA, sA) in sigsA.withIndex()) {
            var bestAvg64Dist = 64.0
            var bestIdxB = -1

            // Map idxA proportionally to sigsB and search within a generous ±3 window
            // (so trimmed beginnings/endings up to several seconds still align!)
            val centerB = ((idxA.toDouble() / max(1, sigsA.lastIndex)) * sigsB.lastIndex).roundToInt()
            val minB = max(0, centerB - 3)
            val maxB = min(sigsB.lastIndex, centerB + 3)

            for (idxB in minB..maxB) {
                val sB = sigsB[idxB]
                val hDist = java.lang.Long.bitCount(sA.hHash xor sB.hHash)
                val vDist = java.lang.Long.bitCount(sA.vHash xor sB.vHash)
                val avg64Dist = (hDist + vDist) / 2.0
                val colorDiff = (abs(sA.avgR - sB.avgR) + abs(sA.avgG - sB.avgG) + abs(sA.avgB - sB.avgB)) / 3

                // A frame matches if its horizontal & vertical structure and RGB color are close,
                // OR if its structural gradient match is very strong (<= 9.5 bits) even with color filters
                val isStructuralAndColorMatch = avg64Dist <= 14.5 && hDist <= 17 && vDist <= 17 && colorDiff <= 45
                val isColorGradedCloneMatch = avg64Dist <= 9.5 && hDist <= 12 && vDist <= 12 && colorDiff <= 65

                if (isStructuralAndColorMatch || isColorGradedCloneMatch) {
                    val penalty = if (usedInB[idxB]) 1.5 else 0.0
                    if (avg64Dist + penalty < bestAvg64Dist) {
                        bestAvg64Dist = avg64Dist
                        bestIdxB = idxB
                    }
                }
            }

            if (bestIdxB != -1) {
                matchedFrames++
                usedInB[bestIdxB] = true
                if (bestAvg64Dist <= 10.5) {
                    strongMatchedFrames++
                }
                totalAvg64Dist += bestAvg64Dist
            }
        }

        val minFrameCount = min(sigsA.size, sigsB.size)
        val matchRatio = matchedFrames.toDouble() / minFrameCount.toDouble()
        val overallAvg64Dist = if (matchedFrames > 0) totalAvg64Dist / matchedFrames else 64.0
        val rawSim = ((64.0 - overallAvg64Dist) / 64.0 * 100.0).roundToInt().coerceIn(0, 100)

        val diffMs = if (fpA.durationMs > 0L && fpB.durationMs > 0L) abs(fpA.durationMs - fpB.durationMs) else 0L
        val minRequiredMatches = min(3, minFrameCount)

        // Visual clone: very close duration (<= 2s) and strong multi-frame agreement
        val isVisualClone = diffMs <= 2000L &&
                matchedFrames >= minRequiredMatches &&
                matchRatio >= 0.60 &&
                overallAvg64Dist <= 11.5 &&
                strongMatchedFrames >= min(2, minFrameCount)

        // Modified / compressed / trimmed video: >= 50% of frames match with good structural similarity
        val isModifiedMatch = matchedFrames >= minRequiredMatches &&
                matchRatio >= 0.50 &&
                overallAvg64Dist <= 13.8 &&
                (strongMatchedFrames >= 1 || matchRatio >= 0.65)

        return when {
            isVisualClone -> {
                val score = maxOf(rawSim, 92).coerceAtMost(99)
                FrameComparisonResult(
                    isMatch = true,
                    similarityScore = score,
                    matchType = DuplicateMatchType.VISUAL_CLONE,
                    description = "Візуальний клон ($score% збіг кадрів та кольору)"
                )
            }
            isModifiedMatch -> {
                val score = maxOf(rawSim, 84).coerceAtMost(95)
                val diffSec = (diffMs + 500) / 1000
                val desc = if (diffSec > 0) {
                    "Модифікована копія ($score% схожість, різниця ~${diffSec}с)"
                } else {
                    "Модифікована копія ($score% збіг відеоряду)"
                }
                FrameComparisonResult(
                    isMatch = true,
                    similarityScore = score,
                    matchType = DuplicateMatchType.MODIFIED_VIDEO,
                    description = desc
                )
            }
            else -> {
                FrameComparisonResult(
                    isMatch = false,
                    similarityScore = rawSim,
                    matchType = DuplicateMatchType.FRAME_SIMILARITY,
                    description = "Відео різні ($rawSim% схожість)"
                )
            }
        }
    }

    private fun compareRawHashesFallback(fpA: VideoFingerprint, fpB: VideoFingerprint): FrameComparisonResult {
        val hashesA = fpA.frameHashes
        val hashesB = fpB.frameHashes
        var matched = 0
        var sumDist = 0
        for (hA in hashesA) {
            val bestDist = hashesB.minOf { hB -> java.lang.Long.bitCount(hA xor hB) }
            if (bestDist <= 13) {
                matched++
                sumDist += bestDist
            }
        }
        val minCount = min(hashesA.size, hashesB.size)
        val ratio = matched.toDouble() / minCount.toDouble()
        val avgDist = if (matched > 0) sumDist.toDouble() / matched else 64.0
        val sim = ((64.0 - avgDist) / 64.0 * 100.0).roundToInt().coerceIn(0, 100)
        val isMatch = matched >= min(2, minCount) && ratio >= 0.50 && avgDist <= 12.5
        return FrameComparisonResult(
            isMatch = isMatch,
            similarityScore = if (isMatch) maxOf(sim, 85) else sim,
            matchType = if (isMatch) DuplicateMatchType.VISUAL_CLONE else DuplicateMatchType.FRAME_SIMILARITY,
            description = if (isMatch) "Візуальний клон ($sim%)" else "Відео різні ($sim%)"
        )
    }

    /**
     * Calculates 5-point cryptographic SHA-256 content hash of the file data
     * (head, 25%, 50%, 75%, tail + exact file size).
     */
    fun computeContentHash(file: File): String {
        if (!file.exists()) return ""
        try {
            val length = file.length()
            if (length == 0L) return ""
            val md = MessageDigest.getInstance("SHA-256")

            val lenBytes = ByteBuffer.allocate(8).putLong(length).array()
            md.update(lenBytes)

            val chunkSize = 65536 // 64 KB
            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(chunkSize)

                val head = raf.read(buffer, 0, chunkSize)
                if (head > 0) md.update(buffer, 0, head)

                if (length > chunkSize * 4) {
                    for (fraction in listOf(0.25, 0.50, 0.75)) {
                        raf.seek((length * fraction).toLong())
                        val readBytes = raf.read(buffer, 0, chunkSize)
                        if (readBytes > 0) md.update(buffer, 0, readBytes)
                    }
                } else if (length > chunkSize * 2) {
                    raf.seek(length / 2)
                    val mid = raf.read(buffer, 0, chunkSize)
                    if (mid > 0) md.update(buffer, 0, mid)
                }

                if (length > chunkSize * 2) {
                    raf.seek(max(0L, length - chunkSize))
                    val tail = raf.read(buffer, 0, chunkSize)
                    if (tail > 0) md.update(buffer, 0, tail)
                }
            }

            return md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * Computes a header-independent SHA-256 hash of the middle media stream payload (35%, 50%, 65% of file),
     * skipping container metadata headers (ftyp/moov) at the start and end of the file.
     */
    fun computeMediaBodyHash(file: File): String {
        if (!file.exists()) return ""
        try {
            val length = file.length()
            if (length < 16384L) return computeContentHash(file)
            val md = MessageDigest.getInstance("SHA-256")
            val chunkSize = min(32768L, length / 5).toInt().coerceAtLeast(1024)

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(chunkSize)
                for (fraction in listOf(0.35, 0.50, 0.65)) {
                    val offset = (length * fraction).toLong().coerceIn(0L, max(0L, length - chunkSize))
                    raf.seek(offset)
                    val readBytes = raf.read(buffer, 0, chunkSize)
                    if (readBytes > 0) md.update(buffer, 0, readBytes)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return ""
        }
    }
}

/**
 * Handles renaming of single and duplicate video files to specified naming patterns.
 */
object VideoRenamer {

    fun renameFile(file: File, newBaseName: String): File? {
        if (!file.exists()) return null
        val ext = file.extension.let { if (it.isNotEmpty()) ".$it" else "" }
        val cleanBase = newBaseName.trim().removeSuffix(ext)
        if (cleanBase.isEmpty()) return null

        var targetName = "$cleanBase$ext"
        var targetFile = File(file.parentFile, targetName)

        var counter = 1
        while (targetFile.exists() && targetFile.absolutePath != file.absolutePath) {
            targetName = "${cleanBase}_$counter$ext"
            targetFile = File(file.parentFile, targetName)
            counter++
        }

        val success = file.renameTo(targetFile)
        return if (success) targetFile else null
    }

    fun batchRename(files: List<File>, pattern: String): List<File> {
        val result = mutableListOf<File>()
        val trimmed = pattern.trim()

        for ((index, file) in files.withIndex()) {
            val num = index + 1
            val baseName = when {
                trimmed.isEmpty() -> "$num"
                trimmed == "123" -> if (index == 0) "123" else "123_$num"
                trimmed.endsWith("_") -> "$trimmed$num"
                else -> "${trimmed}_$num"
            }
            val renamed = renameFile(file, baseName)
            result.add(renamed ?: file)
        }
        return result
    }
}
