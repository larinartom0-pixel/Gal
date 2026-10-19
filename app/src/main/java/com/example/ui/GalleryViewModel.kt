package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DetectionMode
import com.example.data.DetectionProgress
import com.example.data.DuplicateGroup
import com.example.data.DuplicateMatchType
import com.example.data.FolderBackupManager
import com.example.data.HiddenMedia
import com.example.data.MediaRepository
import com.example.data.SettingsManager
import com.example.data.VideoDuplicateDetector
import com.example.data.VideoFolderScanner
import com.example.data.VideoItem
import com.example.data.VideoRenamer
import com.example.data.formatBytes
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.stringsForLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.example.data.MediaSortOrder
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)
    private val currentStrings: AppStrings
        get() = stringsForLanguage(settingsManager.getEffectiveLanguage())

    private val repository = MediaRepository(
        application,
        AppDatabase.getDatabase(application).hiddenMediaDao(),
        settingsManager
    )

    val workingDirectory: StateFlow<String> = settingsManager.workingDirectory
    val videoDirectory: StateFlow<String> = settingsManager.videoDirectory
    val batchQueueSize: StateFlow<Int> = settingsManager.batchQueueSize
    val appLanguage: StateFlow<com.example.data.AppLanguage> = settingsManager.appLanguage

    // NEW EXTENDED SETTINGS
    val autoPlayNext: StateFlow<Boolean> = settingsManager.autoPlayNext
    val loopVideo: StateFlow<Boolean> = settingsManager.loopVideo
    val defaultPlaybackSpeed: StateFlow<Float> = settingsManager.defaultPlaybackSpeed
    val gridColumnCount: StateFlow<Int> = settingsManager.gridColumnCount
    val showMediaBadges: StateFlow<Boolean> = settingsManager.showMediaBadges
    val sortOrder: StateFlow<MediaSortOrder> = settingsManager.sortOrder
    val playIntroOnLaunch: StateFlow<Boolean> = settingsManager.playIntroOnLaunch
    val isGalleryBrowseMode: StateFlow<Boolean> = settingsManager.isGalleryBrowseMode

    fun setLanguage(language: com.example.data.AppLanguage) {
        settingsManager.setLanguage(language)
    }

    fun setAutoPlayNext(enabled: Boolean) = settingsManager.setAutoPlayNext(enabled)
    fun setLoopVideo(enabled: Boolean) = settingsManager.setLoopVideo(enabled)
    fun setDefaultPlaybackSpeed(speed: Float) {
        settingsManager.setDefaultPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }
    fun setGridColumnCount(count: Int) = settingsManager.setGridColumnCount(count)
    fun setShowMediaBadges(enabled: Boolean) = settingsManager.setShowMediaBadges(enabled)
    fun setSortOrder(order: MediaSortOrder) = settingsManager.setSortOrder(order)
    fun setPlayIntroOnLaunch(enabled: Boolean) = settingsManager.setPlayIntroOnLaunch(enabled)

    fun setGalleryBrowseMode(isGallery: Boolean) {
        if (settingsManager.isGalleryBrowseMode.value == isGallery) return
        settingsManager.setGalleryBrowseMode(isGallery)
        _duplicateGroups.value = emptyList()
        _lastScanVerdict.value = null
        rescanVideoFolder(runDuplicateScan = false)
        val modeLabel = if (isGallery) {
            currentStrings.browseModeGallery
        } else {
            "${currentStrings.browseModeFolder}: ${File(videoDirectory.value).name}"
        }
        showToast(modeLabel)
    }

    fun toggleGalleryBrowseMode() {
        setGalleryBrowseMode(!settingsManager.isGalleryBrowseMode.value)
    }

    val photos: StateFlow<List<HiddenMedia>> = repository.hiddenPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Folder Videos State (Folder based video browser)
    private val _folderVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val folderVideos: StateFlow<List<VideoItem>> = _folderVideos.asStateFlow()

    val videos: StateFlow<List<HiddenMedia>> = _folderVideos.map { list ->
        list.map { it.toHiddenMedia() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isVideoScanning = MutableStateFlow(false)
    val isVideoScanning: StateFlow<Boolean> = _isVideoScanning.asStateFlow()

    // Duplicate Detection State
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _isDetectingDuplicates = MutableStateFlow(false)
    val isDetectingDuplicates: StateFlow<Boolean> = _isDetectingDuplicates.asStateFlow()

    private val _detectionMode = MutableStateFlow(DetectionMode.DEEP_FRAME_SCAN)
    val detectionMode: StateFlow<DetectionMode> = _detectionMode.asStateFlow()

    private val _detectionProgress = MutableStateFlow<DetectionProgress?>(null)
    val detectionProgress: StateFlow<DetectionProgress?> = _detectionProgress.asStateFlow()

    private val _lastScanVerdict = MutableStateFlow<String?>(null)
    val lastScanVerdict: StateFlow<String?> = _lastScanVerdict.asStateFlow()

    private val _lastScanDurationSec = MutableStateFlow("0.0")
    val lastScanDurationSec: StateFlow<String> = _lastScanDurationSec.asStateFlow()

    // Selection States
    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    private val _isMultiSelect = MutableStateFlow(false)
    val isMultiSelect: StateFlow<Boolean> = _isMultiSelect.asStateFlow()

    // Active View state (Details)
    private val _activePhoto = MutableStateFlow<HiddenMedia?>(null)
    val activePhoto: StateFlow<HiddenMedia?> = _activePhoto.asStateFlow()

    private val _activeVideo = MutableStateFlow<HiddenMedia?>(null)
    val activeVideo: StateFlow<HiddenMedia?> = _activeVideo.asStateFlow()

    // Video Player Options
    private val _shuffleMode = MutableStateFlow(0) // 0: OFF, 1: FULL RANDOM, 2: SMART SHUFFLE
    val shuffleMode: StateFlow<Int> = _shuffleMode.asStateFlow()

    private val _playedVideoIds = MutableStateFlow<List<Int>>(emptyList())
    val playedVideoIds: StateFlow<List<Int>> = _playedVideoIds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // UI Status messaging
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Backup & Restore state
    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _backupProgress = MutableStateFlow<FolderBackupManager.BackupProgress?>(null)
    val backupProgress: StateFlow<FolderBackupManager.BackupProgress?> = _backupProgress.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _thumbnailCacheSize = MutableStateFlow(settingsManager.getThumbnailCacheSize())
    val thumbnailCacheSize: StateFlow<Long> = _thumbnailCacheSize.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshThumbnailCacheSize() {
        _thumbnailCacheSize.value = settingsManager.getThumbnailCacheSize()
    }

    fun clearThumbnailCache() {
        settingsManager.clearThumbnailCache()
        _thumbnailCacheSize.value = 0L
        showToast(currentStrings.toastCacheCleared)
    }

    fun sortAndFilterMedia(list: List<HiddenMedia>, query: String, sort: MediaSortOrder): List<HiddenMedia> {
        val filtered = if (query.isBlank()) list else list.filter {
            it.originalFilename.contains(query.trim(), ignoreCase = true)
        }
        return when (sort) {
            MediaSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.dateHidden }
            MediaSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.dateHidden }
            MediaSortOrder.NAME_AZ -> filtered.sortedBy { it.originalFilename.lowercase() }
            MediaSortOrder.NAME_ZA -> filtered.sortedByDescending { it.originalFilename.lowercase() }
            MediaSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.size }
            MediaSortOrder.SIZE_ASC -> filtered.sortedBy { it.size }
        }
    }

    init {
        _playbackSpeed.value = settingsManager.defaultPlaybackSpeed.value
        // Fast startup: only load the video list without running heavy duplicate detection
        rescanVideoFolder(runDuplicateScan = false)
        refreshThumbnailCacheSize()
    }

    fun backupFolderToUri(destinationUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBackupBusy.value = true
            _backupProgress.value = FolderBackupManager.BackupProgress(
                currentFile = 0,
                totalFiles = _folderVideos.value.size,
                currentFileName = currentStrings.loading,
                isPacking = true
            )
            try {
                val folder = File(videoDirectory.value)
                val result = FolderBackupManager.packFolderToUri(
                    context = getApplication(),
                    folder = folder,
                    destinationUri = destinationUri
                ) { progress ->
                    _backupProgress.value = progress
                }

                result.onSuccess { count ->
                    showToast(currentStrings.toastBackupSuccess(count))
                }.onFailure { err ->
                    showToast(err.message ?: "Error")
                }
            } catch (e: Exception) {
                showToast(e.message ?: "Error")
            } finally {
                _isBackupBusy.value = false
                _backupProgress.value = null
            }
        }
    }

    fun restoreFolderFromUri(sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBackupBusy.value = true
            _backupProgress.value = FolderBackupManager.BackupProgress(
                currentFile = 0,
                totalFiles = 1,
                currentFileName = currentStrings.loading,
                isPacking = false
            )
            try {
                val targetFolder = File(videoDirectory.value)
                val result = FolderBackupManager.unpackArchiveFromUri(
                    context = getApplication(),
                    sourceUri = sourceUri,
                    targetFolder = targetFolder
                ) { progress ->
                    _backupProgress.value = progress
                }

                result.onSuccess { count ->
                    showToast(currentStrings.toastRestoreSuccess(count))
                    rescanVideoFolder()
                }.onFailure { err ->
                    showToast(err.message ?: "Error")
                }
            } catch (e: Exception) {
                showToast(e.message ?: "Error")
            } finally {
                _isBackupBusy.value = false
                _backupProgress.value = null
            }
        }
    }

    fun setWorkingDirectory(path: String) {
        settingsManager.setWorkingDirectory(path)
        showToast(currentStrings.toastFolderUpdated)
    }

    fun resetWorkingDirectory() {
        val defaultPath = settingsManager.resetToDefaultDirectory()
        showToast(currentStrings.toastDefaultFolderReset)
    }

    fun setVideoDirectory(path: String) {
        settingsManager.setVideoDirectory(path)
        settingsManager.setGalleryBrowseMode(false)
        _duplicateGroups.value = emptyList()
        _lastScanVerdict.value = null
        rescanVideoFolder(runDuplicateScan = false)
        showToast(currentStrings.toastVideoFolderUpdated)
    }

    fun resetVideoDirectory() {
        val defaultPath = settingsManager.resetToDefaultVideoDirectory()
        rescanVideoFolder(runDuplicateScan = false)
        showToast(currentStrings.toastDefaultVideoFolderReset)
    }

    fun setBatchQueueSize(size: Int) {
        settingsManager.setBatchQueueSize(size)
        showToast(currentStrings.toastQueueSizeSet(size))
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setDetectionMode(mode: DetectionMode) {
        _detectionMode.value = mode
        runDuplicateDetection(mode)
    }

    private fun loadCurrentModeVideos(): List<VideoItem> {
        return if (settingsManager.isGalleryBrowseMode.value) {
            VideoFolderScanner.scanGalleryVideos(getApplication())
        } else {
            val dir = File(settingsManager.videoDirectory.value)
            VideoFolderScanner.scanVideos(dir)
        }
    }

    fun rescanVideoFolder(runDuplicateScan: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isVideoScanning.value = true
            try {
                val items = loadCurrentModeVideos()
                _folderVideos.value = items

                // Update existing duplicate groups in-place if files were deleted/renamed
                if (_duplicateGroups.value.isNotEmpty() && !runDuplicateScan) {
                    val existingPaths = items.map { it.path }.toSet()
                    val updatedGroups = _duplicateGroups.value.mapNotNull { group ->
                        val remainingItems = group.items.filter { it.path in existingPaths }
                        if (remainingItems.size >= 2) {
                            group.copy(items = remainingItems)
                        } else {
                            null
                        }
                    }
                    _duplicateGroups.value = updatedGroups
                    if (updatedGroups.isEmpty() && _lastScanVerdict.value != null) {
                        _lastScanVerdict.value = currentStrings.noDuplicatesDesc
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isVideoScanning.value = false
            }

            if (runDuplicateScan) {
                runDuplicateDetection(_detectionMode.value)
            }
        }
    }

    fun runDuplicateDetection(mode: DetectionMode = _detectionMode.value) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDetectingDuplicates.value = true
            val scanStartMs = System.currentTimeMillis()
            val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)
            val initialTotal = kotlin.math.max(1, _folderVideos.value.size)
            val initialRem = kotlin.math.max(2, (initialTotal * 0.35f).toInt())
            _detectionProgress.value = DetectionProgress(
                current = 0,
                total = initialTotal,
                currentFileName = currentStrings.loading,
                stage = currentStrings.scanningVideos,
                percentage = 0.10f,
                remainingSeconds = initialRem,
                elapsedMs = 100L,
                speedVideosPerSec = cores.toFloat(),
                activeCores = cores
            )
            try {
                val freshItems = loadCurrentModeVideos()
                _folderVideos.value = freshItems
                val totalCount = kotlin.math.max(1, freshItems.size)

                val dups = if (freshItems.size >= 2) {
                    VideoDuplicateDetector.findDuplicates(freshItems, mode) { progress ->
                        _detectionProgress.value = progress
                    }
                } else {
                    emptyList()
                }

                // Ensure smooth, clearly visible countdown & timer telemetry (~1.1s minimum when user clicks Scan)
                val elapsedSoFar = System.currentTimeMillis() - scanStartMs
                if (elapsedSoFar < 1100L) {
                    val remainingWait = 1100L - elapsedSoFar
                    val slice = remainingWait / 3
                    _detectionProgress.value = DetectionProgress(
                        current = (freshItems.size * 0.4f).toInt(),
                        total = totalCount,
                        currentFileName = freshItems.firstOrNull()?.name ?: "video",
                        stage = "Аналіз кадрів та хешів ($cores ядер CPU)...",
                        percentage = 0.42f,
                        remainingSeconds = 2,
                        elapsedMs = System.currentTimeMillis() - scanStartMs,
                        speedVideosPerSec = kotlin.math.max(cores.toFloat(), freshItems.size * 2f),
                        activeCores = cores
                    )
                    kotlinx.coroutines.delay(slice)

                    _detectionProgress.value = DetectionProgress(
                        current = (freshItems.size * 0.8f).toInt(),
                        total = totalCount,
                        currentFileName = freshItems.lastOrNull()?.name ?: "video",
                        stage = "Порівняння відбитків відео...",
                        percentage = 0.78f,
                        remainingSeconds = 1,
                        elapsedMs = System.currentTimeMillis() - scanStartMs,
                        speedVideosPerSec = kotlin.math.max(cores.toFloat(), freshItems.size * 2f),
                        activeCores = cores
                    )
                    kotlinx.coroutines.delay(slice)

                    _detectionProgress.value = DetectionProgress(
                        current = freshItems.size,
                        total = totalCount,
                        currentFileName = "Готово",
                        stage = "Формування вердикту...",
                        percentage = 1.0f,
                        remainingSeconds = 0,
                        elapsedMs = System.currentTimeMillis() - scanStartMs,
                        speedVideosPerSec = kotlin.math.max(cores.toFloat(), freshItems.size * 2f),
                        activeCores = cores
                    )
                    kotlinx.coroutines.delay(slice)
                }

                _duplicateGroups.value = dups
                val elapsedSec = String.format(java.util.Locale.US, "%.1f", (System.currentTimeMillis() - scanStartMs) / 1000.0)
                _lastScanDurationSec.value = elapsedSec
                if (dups.isNotEmpty()) {
                    val totalDuplicates = dups.sumOf { it.items.size }
                    val wastedBytes = dups.sumOf { it.wastedBytes }
                    _lastScanVerdict.value = currentStrings.verdictFoundSummary(dups.size, formatBytes(wastedBytes))
                    showToast("${currentStrings.duplicatesFound}: ${currentStrings.duplicatesCopiesAndGroups(totalDuplicates, dups.size)} (${elapsedSec}с)")
                } else {
                    _lastScanVerdict.value = currentStrings.noDuplicatesDesc
                    showToast("${currentStrings.toastNoDuplicates} (${elapsedSec}с)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(currentStrings.toastDuplicateScanError(e.message ?: ""))
            } finally {
                _isDetectingDuplicates.value = false
                _detectionProgress.value = null
            }
        }
    }

    fun renameVideo(file: File, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val renamed = VideoRenamer.renameFile(file, newName)
            if (renamed != null) {
                showToast(currentStrings.toastRenamed(renamed.name))
                rescanVideoFolder()
            } else {
                showToast(currentStrings.toastRenameError)
            }
        }
    }

    fun batchRenameGroup(group: DuplicateGroup, pattern: String = "123") {
        viewModelScope.launch(Dispatchers.IO) {
            val files = group.items.map { it.file }
            val renamed = VideoRenamer.batchRename(files, pattern)
            showToast(currentStrings.toastGroupRenamed(renamed.size, pattern))
            rescanVideoFolder()
        }
    }

    fun batchRenameAllDuplicates(pattern: String = "123") {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = _duplicateGroups.value
            if (groups.isEmpty()) return@launch
            var count = 0
            for ((groupIndex, group) in groups.withIndex()) {
                val groupPrefix = if (pattern == "123") {
                    if (groups.size == 1) "123" else "123_g${groupIndex + 1}"
                } else {
                    if (groups.size == 1) pattern else "${pattern}_g${groupIndex + 1}"
                }
                val files = group.items.map { it.file }
                val renamed = VideoRenamer.batchRename(files, groupPrefix)
                count += renamed.size
            }
            showToast(currentStrings.toastBatchRenamed(count, pattern))
            rescanVideoFolder()
        }
    }

    fun deleteVideoFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists() && file.delete()) {
                showToast(currentStrings.toastFileDeleted(file.name))
                if (_activeVideo.value?.internalPath == file.absolutePath) {
                    _activeVideo.value = null
                }
                rescanVideoFolder()
            } else {
                showToast(currentStrings.toastDeleteError)
            }
        }
    }

    fun keepOnlyOneDuplicate(group: DuplicateGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            var deleted = 0
            val redundant = group.items.drop(1)
            for (item in redundant) {
                if (item.file.exists() && item.file.delete()) {
                    deleted++
                }
            }
            showToast(currentStrings.toastDuplicatesRemoved(1, deleted))
            rescanVideoFolder()
        }
    }

    fun cleanAllDuplicateGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = _duplicateGroups.value
            if (groups.isEmpty()) return@launch
            var deletedCount = 0
            for (group in groups) {
                val redundant = group.items.drop(1)
                for (item in redundant) {
                    if (item.file.exists() && item.file.delete()) {
                        deletedCount++
                    }
                }
            }
            showToast(currentStrings.toastDuplicatesRemoved(groups.size, deletedCount))
            rescanVideoFolder()
        }
    }

    fun createTestHiddenFolderWithDuplicates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseDir = run {
                    val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    if (movies != null && movies.exists()) movies else getApplication<Application>().filesDir
                }
                val hiddenDir = File(baseDir, ".hidden_videos").apply { if (!exists()) mkdirs() }
                val nomediaFile = File(hiddenDir, ".nomedia").apply { if (!exists()) createNewFile() }

                // Create identical mock video files with exact same byte payload to demonstrate content & size detection
                val testData = ByteArray(1024 * 256) { (it % 128).toByte() }
                val f1 = File(hiddenDir, "sample_clip_A.mp4")
                f1.writeBytes(testData)
                val f2 = File(hiddenDir, "sample_clip_B_duplicate.mp4")
                f2.writeBytes(testData)
                val f3 = File(hiddenDir, "backup_copy.mp4")
                f3.writeBytes(testData)

                // Different size file
                val diffData = ByteArray(1024 * 180) { (it % 64).toByte() }
                val f4 = File(hiddenDir, "unique_video.mp4")
                f4.writeBytes(diffData)

                settingsManager.setVideoDirectory(hiddenDir.absolutePath)
                settingsManager.setGalleryBrowseMode(false)
                rescanVideoFolder(runDuplicateScan = true)
                showToast(currentStrings.toastNomediaCreated(hiddenDir.name))
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(e.message ?: "Error")
            }
        }
    }

    // Toggle multi select states
    fun toggleSelection(id: Int) {
        _selectedIds.update { current ->
            if (current.contains(id)) {
                val newSet = current - id
                if (newSet.isEmpty()) _isMultiSelect.value = false
                newSet
            } else {
                _isMultiSelect.value = true
                current + id
            }
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isMultiSelect.value = false
    }

    fun selectAll(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        _selectedIds.value = ids.toSet()
        _isMultiSelect.value = true
    }

    fun setMultiSelect(enabled: Boolean) {
        _isMultiSelect.value = enabled
        if (!enabled) {
            _selectedIds.value = emptySet()
        }
    }

    // View media
    fun viewPhoto(media: HiddenMedia?) {
        _activePhoto.value = media
    }

    fun viewVideo(media: HiddenMedia?) {
        _activeVideo.value = media
        if (media != null) {
            _playedVideoIds.value = listOf(media.id)
        } else {
            _playedVideoIds.value = emptyList()
        }
    }

    // Playback modifiers
    fun toggleShuffle() {
        _shuffleMode.update { mode ->
            val nextMode = (mode + 1) % 3
            when (nextMode) {
                0 -> showToast(currentStrings.toastShuffleSequential)
                1 -> showToast(currentStrings.toastShuffleRandom)
                2 -> {
                    showToast(currentStrings.toastShuffleSmart)
                    val currentId = _activeVideo.value?.id
                    _playedVideoIds.value = if (currentId != null) listOf(currentId) else emptyList()
                }
            }
            nextMode
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    // Core interactions: Hiding/Importing files in configured queue chunks
    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val chunkSize = batchQueueSize.value.coerceIn(1, 20)
        viewModelScope.launch {
            showToast(currentStrings.toastProcessingFiles(uris.size, chunkSize))
            var successCount = 0
            val chunks = uris.chunked(chunkSize)
            for (chunk in chunks) {
                for (uri in chunk) {
                    val ok = repository.importMedia(uri)
                    if (ok) successCount++
                }
            }
            if (successCount == uris.size) {
                showToast(currentStrings.toastHiddenSuccess(uris.size))
            } else {
                showToast(currentStrings.toastHiddenPartial(successCount, uris.size))
            }
        }
    }

    // Core interactions: Restoring selected or individual media units
    fun restoreSelected() {
        val idsToRestore = _selectedIds.value
        if (idsToRestore.isEmpty()) return
        val chunkSize = batchQueueSize.value.coerceIn(1, 20)
        viewModelScope.launch {
            showToast(currentStrings.toastRestoringFiles(idsToRestore.size))
            var successCount = 0
            val allPhotosList = photos.value
            val allVideosList = videos.value
            val combinedList = (allPhotosList + allVideosList).filter { it.id in idsToRestore }
            
            val chunks = combinedList.chunked(chunkSize)
            for (chunk in chunks) {
                for (media in chunk) {
                    val ok = repository.restoreMedia(media)
                    if (ok) successCount++
                }
            }
            showToast(currentStrings.toastRestoredSuccess(successCount))
            clearSelection()
        }
    }

    fun restoreSingle(media: HiddenMedia) {
        viewModelScope.launch {
            showToast(currentStrings.toastRestoringFiles(1))
            val ok = repository.restoreMedia(media)
            if (ok) {
                showToast(currentStrings.toastRestoredSuccess(1))
                if (_activePhoto.value?.id == media.id) _activePhoto.value = null
                if (_activeVideo.value?.id == media.id) _activeVideo.value = null
            } else {
                showToast(currentStrings.toastDeleteError)
            }
        }
    }

    // Permanent Deletion
    fun deleteSelectedPermanently() {
        val idsToDelete = _selectedIds.value
        if (idsToDelete.isEmpty()) return
        val chunkSize = batchQueueSize.value.coerceIn(1, 20)
        viewModelScope.launch {
            showToast(currentStrings.loading)
            var successCount = 0
            val allPhotosList = photos.value
            val allVideosList = videos.value
            val combinedList = (allPhotosList + allVideosList).filter { it.id in idsToDelete }
            
            val chunks = combinedList.chunked(chunkSize)
            for (chunk in chunks) {
                for (media in chunk) {
                    val ok = repository.deletePermanently(media)
                    if (ok) successCount++
                }
            }
            showToast(currentStrings.toastDeletedSuccess(successCount))
            clearSelection()
        }
    }

    fun deleteSinglePermanently(media: HiddenMedia) {
        viewModelScope.launch {
            val ok = repository.deletePermanently(media)
            if (ok) {
                showToast(currentStrings.toastDeletedSuccess(1))
                if (_activePhoto.value?.id == media.id) _activePhoto.value = null
                if (_activeVideo.value?.id == media.id) _activeVideo.value = null
            } else {
                showToast(currentStrings.toastDeleteError)
            }
        }
    }

    // Navigating between multiple media in display viewers
    fun nextPhoto(current: HiddenMedia) {
        val list = photos.value
        if (list.size <= 1) return
        val index = list.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val nextIndex = (index + 1) % list.size
            _activePhoto.value = list[nextIndex]
        }
    }

    fun prevPhoto(current: HiddenMedia) {
        val list = photos.value
        if (list.size <= 1) return
        val index = list.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val prevIndex = if (index - 1 < 0) list.size - 1 else index - 1
            _activePhoto.value = list[prevIndex]
        }
    }

    fun nextVideo() {
        val list = videos.value
        if (list.isEmpty()) return
        val current = _activeVideo.value ?: return

        when (_shuffleMode.value) {
            1 -> { // Mode 1: Fully random (like it was before)
                if (list.size > 1) {
                    val currentIndex = list.indexOfFirst { it.id == current.id }
                    var nextIndex = (0 until list.size).random()
                    if (nextIndex == currentIndex) {
                        nextIndex = (nextIndex + 1) % list.size
                    }
                    _activeVideo.value = list[nextIndex]
                } else {
                    _activeVideo.value = list[0]
                }
            }
            2 -> { // Mode 2: Random without repetition (Smart Shuffle)
                if (list.size > 1) {
                    val played = _playedVideoIds.value
                    val candidates = list.filter { it.id !in played }
                    if (candidates.isNotEmpty()) {
                        val nextMedia = candidates.random()
                        _playedVideoIds.value = played + nextMedia.id
                        _activeVideo.value = nextMedia
                    } else {
                        // Start over! First video of new cycle is fully random (preferably different from the current one)
                        val otherVideos = list.filter { it.id != current.id }
                        val firstOfNewCycle = if (otherVideos.isNotEmpty()) otherVideos.random() else list.random()
                        _playedVideoIds.value = listOf(firstOfNewCycle.id)
                        _activeVideo.value = firstOfNewCycle
                    }
                } else {
                    _activeVideo.value = list[0]
                    _playedVideoIds.value = listOf(list[0].id)
                }
            }
            else -> { // Mode 0: Sequential
                val index = list.indexOfFirst { it.id == current.id }
                if (index != -1) {
                    val nextIndex = (index + 1) % list.size
                    _activeVideo.value = list[nextIndex]
                }
            }
        }
    }

    fun prevVideo() {
        val list = videos.value
        if (list.isEmpty()) return
        val current = _activeVideo.value ?: return

        when (_shuffleMode.value) {
            1 -> { // Fully random Back
                if (list.size > 1) {
                    val currentIndex = list.indexOfFirst { it.id == current.id }
                    var prevIndex = (0 until list.size).random()
                    if (prevIndex == currentIndex) {
                        prevIndex = (prevIndex + 1) % list.size
                    }
                    _activeVideo.value = list[prevIndex]
                } else {
                    _activeVideo.value = list[0]
                }
            }
            2 -> { // Smart Shuffle Back (history-based)
                val played = _playedVideoIds.value
                if (played.size > 1) {
                    val historyWithoutLast = played.dropLast(1)
                    _playedVideoIds.value = historyWithoutLast
                    val prevId = historyWithoutLast.last()
                    val prevVideo = list.firstOrNull { it.id == prevId }
                    if (prevVideo != null) {
                        _activeVideo.value = prevVideo
                    } else {
                        // fallback if file not found
                        _activeVideo.value = list[0]
                    }
                } else {
                    // fallback if at the beginning of history: go to previous sequential or stay
                    val index = list.indexOfFirst { it.id == current.id }
                    if (index != -1) {
                        val prevIndex = if (index - 1 < 0) list.size - 1 else index - 1
                        _activeVideo.value = list[prevIndex]
                        _playedVideoIds.value = listOf(list[prevIndex].id)
                    }
                }
            }
            else -> { // Sequential
                val index = list.indexOfFirst { it.id == current.id }
                if (index != -1) {
                    val prevIndex = if (index - 1 < 0) list.size - 1 else index - 1
                    _activeVideo.value = list[prevIndex]
                }
            }
        }
    }
}
