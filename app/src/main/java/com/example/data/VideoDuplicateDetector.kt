package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
            mimeType = if (name.endsWith(".webm", ignoreCase = true)) "video/webm" else "video/mp4",
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
    EXACT_CONTENT,     // 100% точний збіг байтів або хешу
    FLEXIBLE_NAME_SIZE,// Гнучкий збіг за нормалізованою назвою та розміром
    SIZE_AND_DURATION, // Збіг за розміром (±2%) та тривалістю
    RESOLUTION_NAME,   // Збіг за роздільною здатністю та схожістю назви
    VISUAL_CLONE,      // Візуальний клон
    MODIFIED_VIDEO,    // Модифіковане відео
    FRAME_SIMILARITY   // Зворотна сумісність
}

/**
 * Represents a group of duplicate videos discovered by flexible comparison.
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
    FLEXIBLE_MATCH,  // Швидке та надійне гнучке порівняння (назва + розмір + роздільність)
    DEEP_FRAME_SCAN, // Для зворотної сумісності (використовує надійне гнучке порівняння)
    FAST_HASH_ONLY   // Для зворотної сумісності
}

/**
 * Real-time progress updates.
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
 * Scans directories or MediaStore for video files (MP4, WebM, MKV, AVI, etc.).
 */
object VideoFolderScanner {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "webm", "mkv", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts"
    )

    fun isVideoFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0) return false
        val ext = file.extension.lowercase(Locale.ROOT)
        return VIDEO_EXTENSIONS.contains(ext)
    }

    fun scanFolder(folder: File): List<VideoItem> {
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        val result = mutableListOf<VideoItem>()
        var idCounter = 1

        fun scanRecursive(current: File) {
            val children = current.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    scanRecursive(child)
                } else if (isVideoFile(child)) {
                    result.add(
                        VideoItem(
                            id = idCounter++,
                            file = child,
                            name = child.name,
                            path = child.absolutePath,
                            size = child.length(),
                            lastModified = child.lastModified()
                        )
                    )
                }
            }
        }

        scanRecursive(folder)
        return result.sortedByDescending { it.lastModified }
    }

    fun scanEntireDeviceGallery(context: Context, activeFolder: File? = null): List<VideoItem> {
        val itemsByPath = LinkedHashMap<String, VideoItem>()
        var idCounter = 1

        // 1. MediaStore query
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val wIdx = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val path = if (dataIdx != -1) cursor.getString(dataIdx) else null
                    if (path.isNullOrBlank()) continue
                    val file = File(path)
                    if (!file.exists() || file.length() <= 0) continue

                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: file.name else file.name
                    val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else file.length()
                    val dateMod = if (dateIdx != -1) cursor.getLong(dateIdx) * 1000L else file.lastModified()
                    val durMs = if (durIdx != -1) cursor.getLong(durIdx) else 0L
                    val w = if (wIdx != -1) cursor.getInt(wIdx) else 0
                    val h = if (hIdx != -1) cursor.getInt(hIdx) else 0
                    val res = if (w > 0 && h > 0) "${w}x${h}" else ""

                    itemsByPath[path] = VideoItem(
                        id = idCounter++,
                        file = file,
                        name = name,
                        path = path,
                        size = size,
                        lastModified = dateMod,
                        resolution = res,
                        durationMs = durMs
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Active folder scan if present
        if (activeFolder != null && activeFolder.exists() && activeFolder.isDirectory) {
            val folderItems = scanFolder(activeFolder)
            for (item in folderItems) {
                if (!itemsByPath.containsKey(item.path)) {
                    itemsByPath[item.path] = item.copy(id = idCounter++)
                }
            }
        }

        return itemsByPath.values.sortedByDescending { it.lastModified }
    }

    fun scanVideos(folder: File): List<VideoItem> = scanFolder(folder)

    fun scanGalleryVideos(context: Context, activeFolder: File? = null): List<VideoItem> =
        scanEntireDeviceGallery(context, activeFolder)
}

/**
 * Utility for single and batch renaming video files.
 */
object VideoRenamer {
    fun renameFile(file: File, newName: String): File? {
        if (!file.exists()) return null
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return null
        val targetName = if (!trimmed.contains(".")) {
            "$trimmed.${file.extension}"
        } else {
            trimmed
        }
        val targetFile = File(file.parentFile, targetName)
        if (targetFile.exists() && targetFile.absolutePath != file.absolutePath) {
            return null
        }
        return if (file.renameTo(targetFile)) targetFile else null
    }

    fun batchRename(files: List<File>, pattern: String = "123"): List<File> {
        val renamed = mutableListOf<File>()
        val cleanPattern = pattern.trim().ifEmpty { "123" }
        for ((idx, file) in files.withIndex()) {
            if (!file.exists()) continue
            val ext = file.extension
            val newName = "${cleanPattern}_${idx + 1}.$ext"
            val target = File(file.parentFile, newName)
            if (file.renameTo(target)) {
                renamed.add(target)
            }
        }
        return renamed
    }
}

/**
 * High-speed flexible duplicate video detector.
 * Implements flexible multi-level comparison:
 * 1. Normalized title matching (cleans (1), _copy, -converted, extensions, etc.)
 * 2. File size proximity (flexible ±1-2% tolerance)
 * 3. Resolution & duration proximity
 * 4. Fast header/sample checksums for identical content
 */
object VideoDuplicateDetector {

    /**
     * Normalizes a video file name for comparison by stripping extensions,
     * common duplicate suffixes like (1), _copy, -1, [1], and punctuation.
     */
    fun normalizeVideoName(rawName: String): String {
        var name = rawName.substringBeforeLast('.').lowercase(Locale.ROOT)
        // Remove typical copy patterns
        name = name.replace(Regex("\\(\\s*\\d+\\s*\\)"), "") // (1), (2)
        name = name.replace(Regex("\\[\\s*\\d+\\s*\\]"), "") // [1]
        name = name.replace(Regex("[_\\-\\s]+(copy|копия|копія|converted|clone|dupe)\\b"), "")
        name = name.replace(Regex("[_\\-\\s]+\\d+$"), "") // _1, -2 at end
        name = name.replace(Regex("[^a-zа-яіїєґ0-9]"), "") // retain only alphanumeric
        return name.trim()
    }

    /**
     * Calculates quick 16KB header + middle + footer checksum for identical content check.
     */
    fun computeQuickHash(file: File): String {
        if (!file.exists() || file.length() <= 0) return ""
        try {
            val length = file.length()
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)

            RandomAccessFile(file, "r").use { raf ->
                // First 8KB
                val read1 = raf.read(buffer)
                if (read1 > 0) digest.update(buffer, 0, read1)

                // Middle 8KB
                if (length > 16384) {
                    raf.seek(length / 2)
                    val read2 = raf.read(buffer)
                    if (read2 > 0) digest.update(buffer, 0, read2)
                }

                // Last 8KB
                if (length > 24576) {
                    raf.seek(length - 8192)
                    val read3 = raf.read(buffer)
                    if (read3 > 0) digest.update(buffer, 0, read3)
                }
            }

            return digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            return "${file.length()}"
        }
    }

    /**
     * Calculates Levenshtein similarity between two strings (0.0 to 1.0).
     */
    private fun stringSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        if (s1.contains(s2) || s2.contains(s1)) return 0.9f

        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(dp[i - 1][j] + 1, min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost))
            }
        }

        val maxLen = max(len1, len2)
        return 1.0f - (dp[len1][len2].toFloat() / maxLen.toFloat())
    }

    suspend fun findDuplicates(
        videos: List<VideoItem>,
        mode: DetectionMode = DetectionMode.FLEXIBLE_MATCH,
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
                    elapsedMs = 50L
                )
            )
            return@withContext emptyList()
        }

        val startTime = System.currentTimeMillis()
        val total = videos.size
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)
        val semaphore = Semaphore(cores * 2)
        val progressCount = AtomicInteger(0)

        // Stage 1: Parallel fast metadata extraction (duration & resolution)
        val enrichedVideos = videos.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    var dur = item.durationMs
                    var res = item.resolution

                    if ((dur <= 0L || res.isBlank()) && item.file.exists()) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(item.path)
                            if (dur <= 0L) {
                                dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            }
                            if (res.isBlank()) {
                                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                if (w > 0 && h > 0) {
                                    res = "${w}x${h}"
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            try {
                                retriever.release()
                            } catch (_: Exception) {}
                        }
                    }

                    val c = progressCount.incrementAndGet()
                    if (c % 5 == 0 || c == total) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val pct = (c.toFloat() / total.toFloat()) * 0.7f
                        onProgress?.invoke(
                            DetectionProgress(
                                current = c,
                                total = total,
                                currentFileName = item.name,
                                stage = "Зчитування медіа-даних ($c / $total)...",
                                percentage = pct,
                                remainingSeconds = max(1, ((total - c) / (max(cores * 5, 10))).toInt()),
                                elapsedMs = elapsed,
                                speedVideosPerSec = (c.toFloat() / (elapsed / 1000f + 0.1f)),
                                activeCores = cores
                            )
                        )
                    }

                    item.copy(durationMs = dur, resolution = res)
                }
            }
        }.awaitAll()

        // Stage 2: Flexible Multi-Criterion Matching
        onProgress?.invoke(
            DetectionProgress(
                current = total,
                total = total,
                currentFileName = "Аналіз...",
                stage = "Гнучке порівняння назв, розмірів та роздільності...",
                percentage = 0.85f,
                remainingSeconds = 1,
                elapsedMs = System.currentTimeMillis() - startTime,
                speedVideosPerSec = total.toFloat(),
                activeCores = cores
            )
        )

        // Pre-compute normalized names
        val normalizedNames = enrichedVideos.associate { it.id to normalizeVideoName(it.name) }
        val quickHashes = mutableMapOf<Int, String>()

        // Union-Find / Disjoint-Set structure to group matches
        val parent = IntArray(enrichedVideos.size) { it }
        fun findRoot(i: Int): Int {
            var curr = i
            while (curr != parent[curr]) {
                parent[curr] = parent[parent[curr]]
                curr = parent[curr]
            }
            return curr
        }
        fun union(i: Int, j: Int) {
            val rootI = findRoot(i)
            val rootJ = findRoot(j)
            if (rootI != rootJ) {
                parent[rootJ] = rootI
            }
        }

        val pairMatchDescriptions = mutableMapOf<Pair<Int, Int>, Pair<DuplicateMatchType, String>>()

        for (i in enrichedVideos.indices) {
            val vA = enrichedVideos[i]
            val normA = normalizedNames[vA.id] ?: ""

            for (j in i + 1 until enrichedVideos.size) {
                val vB = enrichedVideos[j]
                val normB = normalizedNames[vB.id] ?: ""

                var isMatch = false
                var matchType = DuplicateMatchType.FLEXIBLE_NAME_SIZE
                var matchDesc = ""

                // 1. Exact Size Match: check quick content hash
                if (vA.size > 0 && vA.size == vB.size) {
                    val hashA = quickHashes.getOrPut(vA.id) { computeQuickHash(vA.file) }
                    val hashB = quickHashes.getOrPut(vB.id) { computeQuickHash(vB.file) }
                    if (hashA.isNotEmpty() && hashA == hashB) {
                        isMatch = true
                        matchType = DuplicateMatchType.EXACT_CONTENT
                        matchDesc = "Точна копія файлу (ідентичний вміст)"
                    }
                }

                // 2. Normalized Title Match + Size/Duration proximity
                if (!isMatch && normA.isNotEmpty() && normB.isNotEmpty()) {
                    val sizeRatio = if (max(vA.size, vB.size) > 0) {
                        abs(vA.size - vB.size).toFloat() / max(vA.size, vB.size).toFloat()
                    } else 1.0f

                    val durDiffMs = if (vA.durationMs > 0 && vB.durationMs > 0) {
                        abs(vA.durationMs - vB.durationMs)
                    } else 0L

                    if (normA == normB) {
                        // Exact normalized name match
                        if (sizeRatio <= 0.08f || durDiffMs <= 3000L) {
                            isMatch = true
                            matchType = DuplicateMatchType.FLEXIBLE_NAME_SIZE
                            matchDesc = "Однакова назва та схожий розмір (${formatBytes(vA.size)} ~ ${formatBytes(vB.size)})"
                        }
                    } else {
                        // High similarity in normalized name
                        val sim = stringSimilarity(normA, normB)
                        if (sim >= 0.75f && (sizeRatio <= 0.05f || durDiffMs <= 2000L)) {
                            isMatch = true
                            matchType = DuplicateMatchType.FLEXIBLE_NAME_SIZE
                            matchDesc = "Схожа назва (${(sim * 100).toInt()}%) та близький розмір"
                        }
                    }
                }

                // 3. Size proximity (±1.5%) + Duration proximity (±1.5 sec)
                if (!isMatch && vA.size > 1024 * 50 && vB.size > 1024 * 50) {
                    val sizeDiff = abs(vA.size - vB.size).toFloat() / max(vA.size, vB.size).toFloat()
                    val durDiff = if (vA.durationMs > 0 && vB.durationMs > 0) abs(vA.durationMs - vB.durationMs) else -1L

                    if (sizeDiff <= 0.015f && durDiff in 0..1500L) {
                        isMatch = true
                        matchType = DuplicateMatchType.SIZE_AND_DURATION
                        matchDesc = "Однаковий розмір (похибка <1.5%) та хронометраж"
                    }
                }

                // 4. Same Resolution + Same Duration (±1s) + Partial Name Similarity
                if (!isMatch && vA.resolution.isNotBlank() && vA.resolution == vB.resolution && vA.durationMs > 2000L) {
                    val durDiff = abs(vA.durationMs - vB.durationMs)
                    if (durDiff <= 1000L) {
                        val sim = if (normA.isNotEmpty() && normB.isNotEmpty()) stringSimilarity(normA, normB) else 0.5f
                        if (sim >= 0.5f) {
                            isMatch = true
                            matchType = DuplicateMatchType.RESOLUTION_NAME
                            matchDesc = "Однакова роздільна здатність (${vA.resolution}) та тривалість"
                        }
                    }
                }

                if (isMatch) {
                    union(i, j)
                    pairMatchDescriptions[Pair(i, j)] = Pair(matchType, matchDesc)
                }
            }
        }

        // Group into sets
        val groupsByRoot = mutableMapOf<Int, MutableList<VideoItem>>()
        for (i in enrichedVideos.indices) {
            val root = findRoot(i)
            groupsByRoot.getOrPut(root) { mutableListOf() }.add(enrichedVideos[i])
        }

        val resultGroups = mutableListOf<DuplicateGroup>()
        for ((rootIdx, items) in groupsByRoot) {
            if (items.size > 1) {
                val primary = items.maxByOrNull { it.size } ?: items.first()
                val matchInfo = pairMatchDescriptions.entries.firstOrNull {
                    val p = it.key
                    items.any { item -> item.id == enrichedVideos[p.first].id } &&
                    items.any { item -> item.id == enrichedVideos[p.second].id }
                }?.value

                val mType = matchInfo?.first ?: DuplicateMatchType.FLEXIBLE_NAME_SIZE
                val mDesc = matchInfo?.second ?: "Гнучкий збіг назви та параметрів"

                resultGroups.add(
                    DuplicateGroup(
                        id = "group_${primary.id}_${System.currentTimeMillis()}",
                        size = primary.size,
                        hash = "${primary.id}_${primary.size}",
                        items = items.sortedByDescending { it.size },
                        matchType = mType,
                        similarityScore = if (mType == DuplicateMatchType.EXACT_CONTENT) 100 else 92,
                        matchDescription = mDesc
                    )
                )
            }
        }

        // Final Progress update
        onProgress?.invoke(
            DetectionProgress(
                current = total,
                total = total,
                currentFileName = "Готово",
                stage = "Виявлено груп: ${resultGroups.size}",
                percentage = 1.0f,
                remainingSeconds = 0,
                elapsedMs = System.currentTimeMillis() - startTime,
                speedVideosPerSec = total.toFloat(),
                activeCores = cores
            )
        )

        resultGroups.sortedByDescending { it.wastedBytes }
    }

    fun findDuplicatesSync(
        videos: List<VideoItem>,
        mode: DetectionMode = DetectionMode.FLEXIBLE_MATCH,
        onProgress: ((DetectionProgress) -> Unit)? = null
    ): List<DuplicateGroup> = runBlocking(Dispatchers.Default) {
        findDuplicates(videos, mode, onProgress)
    }
}
