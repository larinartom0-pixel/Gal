package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vault_settings", Context.MODE_PRIVATE)

    private val defaultWorkDir: String = File(context.filesDir, "vault_storage").apply { if (!exists()) mkdirs() }.absolutePath

    private val defaultVideoDir: String = run {
        try {
            val movies = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            if (movies != null && movies.exists()) {
                movies.absolutePath
            } else {
                File(android.os.Environment.getExternalStorageDirectory(), "Movies").apply { if (!exists()) mkdirs() }.absolutePath
            }
        } catch (e: Exception) {
            File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }.absolutePath
        }
    }

    private val _workingDirectory = MutableStateFlow(
        prefs.getString(KEY_WORK_DIR, defaultWorkDir) ?: defaultWorkDir
    )
    val workingDirectory: StateFlow<String> = _workingDirectory.asStateFlow()

    private val _videoDirectory = MutableStateFlow(
        prefs.getString(KEY_VIDEO_DIR, defaultVideoDir) ?: defaultVideoDir
    )
    val videoDirectory: StateFlow<String> = _videoDirectory.asStateFlow()

    private val _batchQueueSize = MutableStateFlow(
        prefs.getInt(KEY_BATCH_QUEUE_SIZE, 5).coerceIn(1, 20)
    )
    val batchQueueSize: StateFlow<Int> = _batchQueueSize.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code) ?: AppLanguage.SYSTEM.code)
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // NEW PLAYBACK SETTINGS
    private val _autoPlayNext = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
    )
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _loopVideo = MutableStateFlow(
        prefs.getBoolean(KEY_LOOP_VIDEO, false)
    )
    val loopVideo: StateFlow<Boolean> = _loopVideo.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(
        prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
    )
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    // NEW APPEARANCE & GALLERY SETTINGS
    private val _gridColumnCount = MutableStateFlow(
        prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(2, 4)
    )
    val gridColumnCount: StateFlow<Int> = _gridColumnCount.asStateFlow()

    private val _showMediaBadges = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_BADGES, true)
    )
    val showMediaBadges: StateFlow<Boolean> = _showMediaBadges.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        MediaSortOrder.fromCode(prefs.getString(KEY_SORT_ORDER, MediaSortOrder.NEWEST_FIRST.code) ?: MediaSortOrder.NEWEST_FIRST.code)
    )
    val sortOrder: StateFlow<MediaSortOrder> = _sortOrder.asStateFlow()

    private val _playIntroOnLaunch = MutableStateFlow(
        prefs.getBoolean(KEY_PLAY_INTRO_ON_LAUNCH, false)
    )
    val playIntroOnLaunch: StateFlow<Boolean> = _playIntroOnLaunch.asStateFlow()

    // Browse mode: true = Standard Gallery (default on first launch), false = Specific Folder
    private val _isGalleryBrowseMode = MutableStateFlow(
        prefs.getBoolean(KEY_GALLERY_BROWSE_MODE, true)
    )
    val isGalleryBrowseMode: StateFlow<Boolean> = _isGalleryBrowseMode.asStateFlow()

    fun setGalleryBrowseMode(isGallery: Boolean) {
        prefs.edit().putBoolean(KEY_GALLERY_BROWSE_MODE, isGallery).apply()
        _isGalleryBrowseMode.value = isGallery
    }

    fun setPlayIntroOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAY_INTRO_ON_LAUNCH, enabled).apply()
        _playIntroOnLaunch.value = enabled
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _appLanguage.value = language
    }

    fun getEffectiveLanguage(): AppLanguage {
        return resolveEffectiveLanguage(_appLanguage.value)
    }

    fun setWorkingDirectory(path: String) {
        val finalPath = if (path.isNotBlank()) path else defaultWorkDir
        val dir = File(finalPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        prefs.edit().putString(KEY_WORK_DIR, finalPath).apply()
        _workingDirectory.value = finalPath
    }

    fun setVideoDirectory(path: String) {
        val finalPath = if (path.isNotBlank()) path else defaultVideoDir
        val dir = File(finalPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        prefs.edit().putString(KEY_VIDEO_DIR, finalPath).apply()
        _videoDirectory.value = finalPath
    }

    fun resetToDefaultVideoDirectory(): String {
        setVideoDirectory(defaultVideoDir)
        return defaultVideoDir
    }

    fun resetToDefaultDirectory(): String {
        setWorkingDirectory(defaultWorkDir)
        return defaultWorkDir
    }

    fun setBatchQueueSize(size: Int) {
        val safeSize = size.coerceIn(1, 20)
        prefs.edit().putInt(KEY_BATCH_QUEUE_SIZE, safeSize).apply()
        _batchQueueSize.value = safeSize
    }

    fun setAutoPlayNext(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
        _autoPlayNext.value = enabled
    }

    fun setLoopVideo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOOP_VIDEO, enabled).apply()
        _loopVideo.value = enabled
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_SPEED, speed).apply()
        _defaultPlaybackSpeed.value = speed
    }

    fun setGridColumnCount(count: Int) {
        val safeCount = count.coerceIn(2, 4)
        prefs.edit().putInt(KEY_GRID_COLUMNS, safeCount).apply()
        _gridColumnCount.value = safeCount
    }

    fun setShowMediaBadges(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_BADGES, enabled).apply()
        _showMediaBadges.value = enabled
    }

    fun setSortOrder(order: MediaSortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.code).apply()
        _sortOrder.value = order
    }

    // CACHE CLEARING HELPERS
    fun getThumbnailCacheSize(): Long {
        val cacheDir = File(context.cacheDir, "video_covers")
        return if (cacheDir.exists()) {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } else {
            0L
        }
    }

    fun clearThumbnailCache(): Long {
        val cacheDir = File(context.cacheDir, "video_covers")
        if (!cacheDir.exists()) return 0L
        val totalBytes = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        cacheDir.listFiles()?.forEach { it.delete() }
        return totalBytes
    }

    fun getDefaultWorkingDirectory(): String = defaultWorkDir
    fun getDefaultVideoDirectory(): String = defaultVideoDir

    companion object {
        private const val KEY_WORK_DIR = "key_working_directory"
        private const val KEY_VIDEO_DIR = "key_video_directory"
        private const val KEY_BATCH_QUEUE_SIZE = "key_batch_queue_size"
        private const val KEY_LANGUAGE = "key_app_language"
        private const val KEY_AUTO_PLAY_NEXT = "key_auto_play_next"
        private const val KEY_LOOP_VIDEO = "key_loop_video"
        private const val KEY_DEFAULT_SPEED = "key_default_speed"
        private const val KEY_GRID_COLUMNS = "key_grid_columns"
        private const val KEY_SHOW_BADGES = "key_show_badges"
        private const val KEY_SORT_ORDER = "key_sort_order"
        private const val KEY_PLAY_INTRO_ON_LAUNCH = "key_play_intro_on_launch"
        private const val KEY_GALLERY_BROWSE_MODE = "key_gallery_browse_mode"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun resolveEffectiveLanguage(preference: AppLanguage): AppLanguage {
            if (preference != AppLanguage.SYSTEM) {
                return preference
            }
            val systemLang = try {
                val locales = android.content.res.Resources.getSystem().configuration.locales
                if (locales != null && !locales.isEmpty) {
                    locales[0].language.lowercase().trim()
                } else {
                    java.util.Locale.getDefault().language.lowercase().trim()
                }
            } catch (e: Exception) {
                java.util.Locale.getDefault().language.lowercase().trim()
            }

            return when {
                systemLang.startsWith("uk") -> AppLanguage.UKRAINIAN
                systemLang.startsWith("ru") -> AppLanguage.RUSSIAN
                systemLang.startsWith("en") -> AppLanguage.ENGLISH
                else -> AppLanguage.ENGLISH
            }
        }
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "System (Auto)"),
    UKRAINIAN("uk", "Українська"),
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}

enum class MediaSortOrder(val code: String) {
    NEWEST_FIRST("newest"),
    OLDEST_FIRST("oldest"),
    NAME_AZ("name_az"),
    NAME_ZA("name_za"),
    SIZE_DESC("size_desc"),
    SIZE_ASC("size_asc");

    companion object {
        fun fromCode(code: String): MediaSortOrder {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NEWEST_FIRST
        }
    }
}
