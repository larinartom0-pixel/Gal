package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.AppLanguage
import com.example.data.SettingsManager

interface AppStrings {
    val appName: String
    val back: String
    val cancel: String
    val confirm: String
    val save: String
    val delete: String
    val restore: String
    val select: String
    val selectAll: String
    val deselectAll: String
    fun selectedCount(count: Int): String
    val close: String
    val change: String
    val reset: String
    val search: String
    val loading: String

    // Intro Screen
    val introSkip: String
    val introSkipDesc: String
    val introTapToSkip: String
    val introTagline: String

    // Tabs
    val tabVideos: String
    val tabPhotos: String
    val tabDuplicates: String

    // Gallery / Main Screen
    val searchVideosPlaceholder: String
    val btnFolder: String
    val btnSaveBackup: String
    val btnLoadBackup: String
    val btnSettings: String
    val scanningVideos: String
    val searchingFilesInFolder: String
    val noHiddenPhotos: String
    val noHiddenPhotosHint: String
    val noVideosFound: String
    val noVideosHint: String
    val filterAll: String
    val filterShort: String
    val filterMedium: String
    val filterLong: String
    val deleteConfirmTitle: String
    val deleteConfirmDesc: String
    val restoreConfirmTitle: String
    val restoreConfirmDesc: String

    // Backup & Restore
    val backupProgressTitle: String
    val restoreProgressTitle: String
    val backupSuccess: String
    val restoreSuccess: String
    fun folderSelectedToast(path: String): String
    val backupEncryptNotice: String
    val restoreDecryptNotice: String
    fun fileProgress(current: Int, total: Int): String

    // Settings Screen
    val settingsTitle: String
    val sectionLanguage: String
    val languageSectionDesc: String
    val langSystem: String
    val langSystemDesc: String
    val langUkrainian: String
    val langRussian: String
    val langEnglish: String
    fun currentEffectiveLanguageLabel(lang: String): String

    val sectionStorage: String
    val workingDirLabel: String
    val videoDirLabel: String
    val sectionBatchQueue: String
    val batchQueueDesc: String
    val sectionPermissions: String
    val allFilesAccessLabel: String
    val permissionGranted: String
    val permissionRequired: String
    val btnGrantPermission: String
    val sectionStats: String
    val totalMedia: String
    val totalSize: String
    val videoFilesCount: String
    val photosCount: String
    val sectionRoToy: String
    val btnPlayRoToyIntro: String
    val roToySectionDesc: String
    val vaultSecurityNotice: String
    val hidePhotos: String
    val allFilesAccessBannerTitle: String
    val allFilesAccessBannerDesc: String
    val settingsOpenError: String

    // Video Player
    val playerLoading: String
    val badgeVideo: String
    val shuffleOff: String
    val shuffleRandom: String
    val shuffleSmart: String
    val play: String
    val pause: String
    val prev: String
    val next: String
    val speed: String

    // Photo Detail
    val badgePhoto: String
    val photoZoomHint: String

    // Folder Picker
    val folderPickerTitle: String
    val folderPickerDesc: String
    val btnSafPicker: String
    val manualPathHeader: String
    val pathFieldLabel: String
    val popularFoldersHeader: String
    val btnCreateNomedia: String

    // Duplicate Detector
    val duplicateFinderTitle: String
    val duplicateFinderSubtitle: String
    val btnScanDuplicates: String
    val btnScanningDuplicates: String
    fun duplicatesFoundCount(count: Int): String
    fun wastedStorageLabel(size: String): String
    val noDuplicatesTitle: String
    val noDuplicatesDesc: String
    val btnDeleteDuplicates: String
    val btnBatchRename: String
    val btnRename: String
    val originalFileBadge: String
    val duplicateFileBadge: String
    val duplicateDeleteConfirmTitle: String
    val duplicateDeleteConfirmDesc: String
    val renameDialogTitle: String
    val batchRenameDialogTitle: String
    val newNamePrefixLabel: String

    val criteriaContentOnly: String
    val wastedSpace: String
    val duplicatesFound: String
    fun duplicatesCopiesAndGroups(copies: Int, groups: Int): String
    val renameVideo: String
    fun currentNameLabel(name: String): String
    val newName: String
    val renameGroupTitle: String
    fun renameGroupDesc(count: Int): String
    val prefixPatternLabel: String
    val apply: String
    val renameAllDuplicatesTitle: String
    fun renameAllDuplicatesDesc(files: Int, groups: Int): String
    val renameAll: String
    val deleteFileTitle: String
    fun deleteFileConfirmDesc(name: String): String
    val deleteRedundantTitle: String
    fun deleteRedundantConfirmDesc(original: String, copiesCount: Int, savedSize: String): String
    val keepOneAndDeleteOthers: String
    fun identicalVideosCount(count: Int): String
    fun exactSizeLabel(sizeStr: String, bytes: Long): String
    fun hashLabel(hash: String): String
    val renameAs123: String
    val keepOnlyOne: String
    fun hiddenFolderTag(folderName: String): String
    fun estimatedTimeRemaining(seconds: Int): String
    fun scanSpeedLabel(speed: Float, cores: Int): String
    fun turboCpuBadge(cores: Int): String
    val matchTypeExact: String
    val matchTypeVisual: String
    val matchTypeModified: String
    val masterOriginalBadge: String
    val redundantCopyBadge: String
    val btnKeepBestQuality: String
    val verdictLabel: String
    fun verdictFoundSummary(groups: Int, wasted: String): String

    // ViewModel Toast Messages
    val toastFolderUpdated: String
    val toastDefaultFolderReset: String
    val toastVideoFolderUpdated: String
    val toastDefaultVideoFolderReset: String
    fun toastQueueSizeSet(size: Int): String
    val toastNoDuplicates: String
    fun toastDuplicateScanError(err: String): String
    fun toastRenamed(name: String): String
    val toastRenameError: String
    fun toastGroupRenamed(count: Int, pattern: String): String
    fun toastBatchRenamed(count: Int, pattern: String): String
    fun toastFileDeleted(name: String): String
    val toastDeleteError: String
    fun toastDuplicatesRemoved(kept: Int, deleted: Int): String
    fun toastNomediaCreated(name: String): String
    val toastShuffleSequential: String
    val toastShuffleRandom: String
    val toastShuffleSmart: String
    fun toastProcessingFiles(count: Int, chunk: Int): String
    fun toastHiddenSuccess(count: Int): String
    fun toastHiddenPartial(success: Int, total: Int): String
    fun toastRestoringFiles(count: Int): String
    fun toastRestoredSuccess(count: Int): String
    fun toastDeletedSuccess(count: Int): String
    fun toastBackupSuccess(count: Int): String
    fun toastRestoreSuccess(count: Int): String

    // New Extended Settings & Features
    val sectionPlayback: String
    val autoPlayNext: String
    val autoPlayNextDesc: String
    val loopVideo: String
    val loopVideoDesc: String
    val defaultPlaybackSpeed: String

    val sectionAppearance: String
    val gridColumns: String
    val gridColumnsDesc: String
    val sortOrderLabel: String
    val sortNewest: String
    val sortOldest: String
    val sortNameAZ: String
    val sortNameZA: String
    val sortSizeDesc: String
    val sortSizeAsc: String
    val showMediaBadges: String
    val showMediaBadgesDesc: String

    val sectionCache: String
    val clearCache: String
    val clearCacheDesc: String
    fun cacheSizeLabel(sizeStr: String): String
    val toastCacheCleared: String

    val searchPlaceholder: String
    val noSearchResults: String
    val clearSearch: String

    val aboutSection: String
    val appVersion: String
    val appVersionDesc: String
    val easterEggIntroBtn: String
    val playIntroOnLaunch: String
    val playIntroOnLaunchDesc: String

    // Gallery vs Folder Browse Mode & Startup
    val browseModeGallery: String
    val browseModeFolder: String
    val galleryModeSubtitle: String
    val switchBrowseModeBtn: String
    val startupFastModeReady: String
}

object EnglishStrings : AppStrings {
    override val appName = "gal"
    override val back = "Back"
    override val cancel = "Cancel"
    override val confirm = "Confirm"
    override val save = "Save"
    override val delete = "Delete"
    override val restore = "Restore"
    override val select = "Select"
    override val selectAll = "Select All"
    override val deselectAll = "Deselect All"
    override fun selectedCount(count: Int) = "Selected: $count"
    override val close = "Close"
    override val change = "Change"
    override val reset = "Reset"
    override val search = "Search"
    override val loading = "Loading..."

    override val introSkip = "Skip"
    override val introSkipDesc = "Skip intro"
    override val introTapToSkip = "Tap screen to skip"
    override val introTagline = "INTERACTIVE MEDIA & STUDIOS"

    override val tabVideos = "Videos"
    override val tabPhotos = "Photos"
    override val tabDuplicates = "Duplicates"

    override val searchVideosPlaceholder = "Search videos..."
    override val btnFolder = "Folder"
    override val btnSaveBackup = "Save"
    override val btnLoadBackup = "Load"
    override val btnSettings = "Settings"
    override val scanningVideos = "Scanning..."
    override val searchingFilesInFolder = "Searching files in folder..."
    override val noHiddenPhotos = "No hidden photos"
    override val noHiddenPhotosHint = "Tap '+' to add photos to the vault"
    override val noVideosFound = "No videos in selected folder"
    override val noVideosHint = "Choose another folder or add video files"
    override val filterAll = "All"
    override val filterShort = "Short (< 1 min)"
    override val filterMedium = "Medium (1-10 min)"
    override val filterLong = "Long (> 10 min)"
    override val deleteConfirmTitle = "Delete selected files?"
    override val deleteConfirmDesc = "This action cannot be undone. Files will be permanently deleted."
    override val restoreConfirmTitle = "Restore selected files?"
    override val restoreConfirmDesc = "Files will be restored to their original location."

    override val backupProgressTitle = "Archiving..."
    override val restoreProgressTitle = "Restoring from archive..."
    override val backupSuccess = "Archive created successfully"
    override val restoreSuccess = "Archive restored successfully"
    override fun folderSelectedToast(path: String) = "Selected folder: $path"
    override val backupEncryptNotice = "Videos are packed and encrypted using AES algorithm. They cannot be viewed from outside."
    override val restoreDecryptNotice = "Archive is being decrypted and extracted to destination folder..."
    override fun fileProgress(current: Int, total: Int) = "File $current of $total"

    override val settingsTitle = "Settings"
    override val sectionLanguage = "Interface Language"
    override val languageSectionDesc = "System language detection or manual override"
    override val langSystem = "System (Auto)"
    override val langSystemDesc = "Match device language (English fallback if unsupported)"
    override val langUkrainian = "Українська"
    override val langRussian = "Русский"
    override val langEnglish = "English"
    override fun currentEffectiveLanguageLabel(lang: String) = "Active language: $lang"

    override val sectionStorage = "Vault Storage"
    override val workingDirLabel = "Working directory"
    override val videoDirLabel = "Video folder"
    override val sectionBatchQueue = "Batch Queue"
    override val batchQueueDesc = "Concurrent worker threads for media processing"
    override val sectionPermissions = "Storage Permissions"
    override val allFilesAccessLabel = "All files access (Android 11+)"
    override val permissionGranted = "Granted"
    override val permissionRequired = "Permission required"
    override val btnGrantPermission = "Grant access"
    override val sectionStats = "Storage Statistics"
    override val totalMedia = "Total media"
    override val totalSize = "Total size"
    override val videoFilesCount = "Video files"
    override val photosCount = "Photos"
    override val sectionRoToy = "roTOY Company"
    override val btnPlayRoToyIntro = "Play roTOY intro"
    override val roToySectionDesc = "Signature startup animation and cinematic logo"
    override val vaultSecurityNotice = "Files in vault are hidden from standard gallery apps and third-party media scanners."
    override val hidePhotos = "Hide photos"
    override val allFilesAccessBannerTitle = "Allow full access to storage"
    override val allFilesAccessBannerDesc = "Required to browse and save videos from any chosen folders."
    override val settingsOpenError = "Failed to open settings."

    override val playerLoading = "STUDIO LOADING..."
    override val badgeVideo = "VIDEO"
    override val shuffleOff = "Shuffle: Off"
    override val shuffleRandom = "Shuffle: Full Random"
    override val shuffleSmart = "Shuffle: Smart Shuffle"
    override val play = "Play"
    override val pause = "Pause"
    override val prev = "Previous"
    override val next = "Next"
    override val speed = "Speed"

    override val badgePhoto = "PHOTO"
    override val photoZoomHint = "Double tap to zoom • Pinch to scale"

    override val folderPickerTitle = "Select Video Folder"
    override val folderPickerDesc = "The app scans all video files inside the selected folder and subfolders, including hidden (.nomedia) folders."
    override val btnSafPicker = "System Folder Picker (SAF)"
    override val manualPathHeader = "Or enter folder path manually:"
    override val pathFieldLabel = "Folder path"
    override val popularFoldersHeader = "Popular system folders:"
    override val btnCreateNomedia = "Create test .nomedia folder"

    override val duplicateFinderTitle = "Duplicate Video Finder"
    override val duplicateFinderSubtitle = "Smart hash and visual frame comparison to free space"
    override val btnScanDuplicates = "Scan duplicates"
    override val btnScanningDuplicates = "Scanning duplicates..."
    override fun duplicatesFoundCount(count: Int) = "Groups found: $count"
    override fun wastedStorageLabel(size: String) = "Wasted space: $size"
    override val noDuplicatesTitle = "No duplicates found"
    override val noDuplicatesDesc = "All video files in the selected folder are unique"
    override val btnDeleteDuplicates = "Delete copies"
    override val btnBatchRename = "Batch rename"
    override val btnRename = "Rename"
    override val originalFileBadge = "Original (kept)"
    override val duplicateFileBadge = "Copy (can be deleted)"
    override val duplicateDeleteConfirmTitle = "Delete duplicate videos?"
    override val duplicateDeleteConfirmDesc = "All redundant duplicate copies will be permanently deleted while keeping the original file."
    override val renameDialogTitle = "Rename video"
    override val batchRenameDialogTitle = "Batch rename duplicates"
    override val newNamePrefixLabel = "New name prefix"

    override val criteriaContentOnly = "Only content bytes and size are analyzed. Name and date are ignored."
    override val wastedSpace = "Wasted space"
    override val duplicatesFound = "Duplicates found"
    override fun duplicatesCopiesAndGroups(copies: Int, groups: Int) = "$copies copies ($groups groups)"
    override val renameVideo = "Rename video"
    override fun currentNameLabel(name: String) = "Current name: $name"
    override val newName = "New name"
    override val renameGroupTitle = "Rename duplicate group"
    override fun renameGroupDesc(count: Int) = "$count identical videos will be renamed by pattern (e.g., 123_1, 123_2 or 1, 2, 3...)"
    override val prefixPatternLabel = "Prefix / Pattern"
    override val apply = "Apply"
    override val renameAllDuplicatesTitle = "Rename all duplicates"
    override fun renameAllDuplicatesDesc(files: Int, groups: Int) = "All identical videos found ($files files in $groups groups) will be renamed sequentially."
    override val renameAll = "Rename all"
    override val deleteFileTitle = "Delete file?"
    override fun deleteFileConfirmDesc(name: String) = "Are you sure you want to delete '$name'? This action cannot be undone."
    override val deleteRedundantTitle = "Delete redundant duplicates?"
    override fun deleteRedundantConfirmDesc(original: String, copiesCount: Int, savedSize: String) = "1 original ($original) will be kept, and the other $copiesCount identical copies will be deleted. You will free $savedSize."
    override val keepOneAndDeleteOthers = "Keep 1 and delete copies"
    override fun identicalVideosCount(count: Int) = "$count identical videos"
    override fun exactSizeLabel(sizeStr: String, bytes: Long) = "Exact size: $sizeStr ($bytes B)"
    override fun hashLabel(hash: String) = "Hash: $hash..."
    override val renameAs123 = "Rename as 123"
    override val keepOnlyOne = "Keep only 1"
    override fun hiddenFolderTag(folderName: String) = "hidden $folderName"
    override fun estimatedTimeRemaining(seconds: Int) = if (seconds > 60) "~${seconds / 60}m ${seconds % 60}s remaining" else "~${seconds}s remaining for verdict"
    override fun scanSpeedLabel(speed: Float, cores: Int) = "Speed: ${"%.1f".format(java.util.Locale.US, speed)} v/s • $cores CPU cores"
    override fun turboCpuBadge(cores: Int) = "⚡ Turbo: $cores Cores Active"
    override val matchTypeExact = "Exact Match (100%)"
    override val matchTypeVisual = "Visual Clone"
    override val matchTypeModified = "Modified Duplicate"
    override val masterOriginalBadge = "Master (Best Quality)"
    override val redundantCopyBadge = "Duplicate Copy"
    override val btnKeepBestQuality = "Keep Best Quality"
    override val verdictLabel = "Verdict"
    override fun verdictFoundSummary(groups: Int, wasted: String) = "Found $groups duplicate groups ($wasted wasted space)"

    override val toastFolderUpdated = "Working directory updated"
    override val toastDefaultFolderReset = "Reset to default working directory"
    override val toastVideoFolderUpdated = "Video folder updated"
    override val toastDefaultVideoFolderReset = "Reset to default video folder"
    override fun toastQueueSizeSet(size: Int) = "Batch queue size set: $size"
    override val toastNoDuplicates = "No duplicate videos found"
    override fun toastDuplicateScanError(err: String) = "Duplicate scan error: $err"
    override fun toastRenamed(name: String) = "Renamed: $name"
    override val toastRenameError = "Failed to rename video"
    override fun toastGroupRenamed(count: Int, pattern: String) = "Group ($count videos) renamed to '$pattern'"
    override fun toastBatchRenamed(count: Int, pattern: String) = "Renamed $count videos using pattern '$pattern'"
    override fun toastFileDeleted(name: String) = "File deleted: $name"
    override val toastDeleteError = "Failed to delete file"
    override fun toastDuplicatesRemoved(kept: Int, deleted: Int) = "Kept $kept original, deleted $deleted duplicates"
    override fun toastNomediaCreated(name: String) = "Created folder $name with .nomedia and duplicates!"
    override val toastShuffleSequential = "Sequential playback"
    override val toastShuffleRandom = "Mode: Full random"
    override val toastShuffleSmart = "Mode: Smart shuffle (no repeats)"
    override fun toastProcessingFiles(count: Int, chunk: Int) = "Processing $count files (batch size $chunk)..."
    override fun toastHiddenSuccess(count: Int) = "Successfully hidden all $count files"
    override fun toastHiddenPartial(success: Int, total: Int) = "Hidden $success of $total files. Some files could not be copied."
    override fun toastRestoringFiles(count: Int) = "Restoring $count files..."
    override fun toastRestoredSuccess(count: Int) = "Restored $count files to public gallery"
    override fun toastDeletedSuccess(count: Int) = "Permanently deleted $count files"
    override fun toastBackupSuccess(count: Int) = "Successfully encrypted and backed up $count videos!"
    override fun toastRestoreSuccess(count: Int) = "Successfully restored $count videos to folder!"

    // Extended Settings & Features
    override val sectionPlayback = "Playback & Player"
    override val autoPlayNext = "Auto-play Next Video"
    override val autoPlayNextDesc = "Automatically play the next video in queue when playback ends"
    override val loopVideo = "Loop Video"
    override val loopVideoDesc = "Repeat current video continuously"
    override val defaultPlaybackSpeed = "Default Playback Speed"

    override val sectionAppearance = "Appearance & Gallery"
    override val gridColumns = "Grid Columns"
    override val gridColumnsDesc = "Adjust gallery thumbnail columns count"
    override val sortOrderLabel = "Sort Order"
    override val sortNewest = "Newest First"
    override val sortOldest = "Oldest First"
    override val sortNameAZ = "Name (A to Z)"
    override val sortNameZA = "Name (Z to A)"
    override val sortSizeDesc = "Largest First"
    override val sortSizeAsc = "Smallest First"
    override val showMediaBadges = "Show Media Badges"
    override val showMediaBadgesDesc = "Display type icons and file tags on thumbnails"

    override val sectionCache = "Cache & Storage"
    override val clearCache = "Clear Thumbnail Cache"
    override val clearCacheDesc = "Free up device storage used by cached video covers"
    override fun cacheSizeLabel(sizeStr: String) = "Current cache size: $sizeStr"
    override val toastCacheCleared = "Thumbnail cache cleared successfully"

    override val searchPlaceholder = "Search files by name..."
    override val noSearchResults = "No matching files found"
    override val clearSearch = "Clear search"

    override val aboutSection = "About Vault"
    override val appVersion = "Version 2.4.0 Pro"
    override val appVersionDesc = "Private offline encrypted vault with hardware-grade zero tracking"
    override val easterEggIntroBtn = "Play Retro Intro Animation"
    override val playIntroOnLaunch = "Play intro animation on start"
    override val playIntroOnLaunchDesc = "Display roTOY animation on cold start"

    override val browseModeGallery = "Standard Gallery"
    override val browseModeFolder = "Specific Folder"
    override val galleryModeSubtitle = "All device videos (Gallery)"
    override val switchBrowseModeBtn = "Switch Mode"
    override val startupFastModeReady = "⚡ Auto-scan off at startup • Tap to check duplicates"
}

object UkrainianStrings : AppStrings {
    override val appName = "gal"
    override val back = "Назад"
    override val cancel = "Скасувати"
    override val confirm = "Підтвердити"
    override val save = "Зберегти"
    override val delete = "Видалити"
    override val restore = "Відновити"
    override val select = "Вибрати"
    override val selectAll = "Вибрати все"
    override val deselectAll = "Зняти вибір"
    override fun selectedCount(count: Int) = "Вибрано: $count"
    override val close = "Закрити"
    override val change = "Змінити"
    override val reset = "Скинути"
    override val search = "Пошук"
    override val loading = "Завантаження..."

    override val introSkip = "Пропустити"
    override val introSkipDesc = "Пропустити інтро"
    override val introTapToSkip = "Торкніться екрана, щоб пропустити"
    override val introTagline = "INTERACTIVE MEDIA & STUDIOS"

    override val tabVideos = "Відео"
    override val tabPhotos = "Фото"
    override val tabDuplicates = "Дублікати"

    override val searchVideosPlaceholder = "Пошук відео..."
    override val btnFolder = "Папка"
    override val btnSaveBackup = "Зберегти"
    override val btnLoadBackup = "Завантажити"
    override val btnSettings = "Налаштування"
    override val scanningVideos = "Сканування..."
    override val searchingFilesInFolder = "Пошук файлів у папці..."
    override val noHiddenPhotos = "Немає прихованих фото"
    override val noHiddenPhotosHint = "Натисніть «+», щоб додати фотографії у сховище"
    override val noVideosFound = "Немає відео у вибраній папці"
    override val noVideosHint = "Виберіть іншу папку або додайте відеофайли"
    override val filterAll = "Всі"
    override val filterShort = "Короткі (< 1 хв)"
    override val filterMedium = "Середні (1-10 хв)"
    override val filterLong = "Довгі (> 10 хв)"
    override val deleteConfirmTitle = "Видалити вибрані файли?"
    override val deleteConfirmDesc = "Цю дію неможливо скасувати. Файли будуть безповоротно видалені."
    override val restoreConfirmTitle = "Відновити вибрані файли?"
    override val restoreConfirmDesc = "Файли буде відновлено до їхнього початкового місця."

    override val backupProgressTitle = "Архівування..."
    override val restoreProgressTitle = "Відновлення з архіву..."
    override val backupSuccess = "Архів успішно створено"
    override val restoreSuccess = "Архів успішно відновлено"
    override fun folderSelectedToast(path: String) = "Вибрано папку: $path"
    override val backupEncryptNotice = "Відео упаковуються та шифруються за алгоритмом AES. Їх не можна переглянути зовні."
    override val restoreDecryptNotice = "Архів розшифровується та вивантажується у відкриту папку..."
    override fun fileProgress(current: Int, total: Int) = "Файл $current з $total"

    override val settingsTitle = "Налаштування"
    override val sectionLanguage = "Мова інтерфейсу"
    override val languageSectionDesc = "Визначення за системною мовою або вибір вручну"
    override val langSystem = "Системна (авто)"
    override val langSystemDesc = "Мова системи пристрою (англійська за замовчуванням)"
    override val langUkrainian = "Українська"
    override val langRussian = "Русский"
    override val langEnglish = "English"
    override fun currentEffectiveLanguageLabel(lang: String) = "Активна мова: $lang"

    override val sectionStorage = "Папка сховища"
    override val workingDirLabel = "Робоча папка"
    override val videoDirLabel = "Папка з відео"
    override val sectionBatchQueue = "Пакетна черга"
    override val batchQueueDesc = "Кількість паралельних потоків для обробки медіа"
    override val sectionPermissions = "Доступ до пам'яті"
    override val allFilesAccessLabel = "Повний доступ до файлів (Android 11+)"
    override val permissionGranted = "Надано"
    override val permissionRequired = "Потрібно надати"
    override val btnGrantPermission = "Надати доступ"
    override val sectionStats = "Статистика сховища"
    override val totalMedia = "Всього медіа"
    override val totalSize = "Загальний обсяг"
    override val videoFilesCount = "Відеофайлів"
    override val photosCount = "Фотографій"
    override val sectionRoToy = "Компанія roTOY"
    override val btnPlayRoToyIntro = "Переглянути інтро roTOY"
    override val roToySectionDesc = "Фірмова анімація запуску та кінематографічний логотип"
    override val vaultSecurityNotice = "Файли у сховищі захищені від сканування стандартними галереями та сторонніми додатками."
    override val hidePhotos = "Приховати фото"
    override val allFilesAccessBannerTitle = "Дозволити повний доступ до сховища"
    override val allFilesAccessBannerDesc = "Потрібно для перегляду та збереження відео з будь-яких вибраних папок."
    override val settingsOpenError = "Помилка відкриття налаштувань."

    override val playerLoading = "СТУДІЙНЕ ЗАВАНТАЖЕННЯ..."
    override val badgeVideo = "ВІДЕО"
    override val shuffleOff = "Випадковий порядок: Вимк"
    override val shuffleRandom = "Випадковий порядок: Повний"
    override val shuffleSmart = "Випадковий порядок: Розумний"
    override val play = "Відтворити"
    override val pause = "Пауза"
    override val prev = "Попереднє"
    override val next = "Наступне"
    override val speed = "Швидкість"

    override val badgePhoto = "ФОТО"
    override val photoZoomHint = "Подвійний дотик для зуму • Масштабуйте жестом"

    override val folderPickerTitle = "Вибір папки з відео"
    override val folderPickerDesc = "Додаток автоматично знаходить усі відеофайли всередині вибраної папки та її підпапок, включаючи приховані (з крапкою на початку) та папки з файлом .nomedia."
    override val btnSafPicker = "Системний вибір папки (SAF)"
    override val manualPathHeader = "Або введіть шлях до папки вручну:"
    override val pathFieldLabel = "Шлях до папки"
    override val popularFoldersHeader = "Популярні системні папки:"
    override val btnCreateNomedia = "Створити тестову .nomedia папку"

    override val duplicateFinderTitle = "Пошук дублікатів відео"
    override val duplicateFinderSubtitle = "Розумний аналіз хешів та кадрів для звільнення пам'яті"
    override val btnScanDuplicates = "Сканувати дублікати"
    override val btnScanningDuplicates = "Сканування дублікатів..."
    override fun duplicatesFoundCount(count: Int) = "Знайдено груп: $count"
    override fun wastedStorageLabel(size: String) = "Зайвий обсяг: $size"
    override val noDuplicatesTitle = "Дублікатів не знайдено"
    override val noDuplicatesDesc = "У вибраній папці всі відеофайли унікальні"
    override val btnDeleteDuplicates = "Видалити копії"
    override val btnBatchRename = "Пакетне перейменування"
    override val btnRename = "Перейменувати"
    override val originalFileBadge = "Оригінал (зберігається)"
    override val duplicateFileBadge = "Копія (рекомендується видалити)"
    override val duplicateDeleteConfirmTitle = "Видалити дублікати відео?"
    override val duplicateDeleteConfirmDesc = "Усі знайдені дублікати будуть безповоротно видалені, оригінальний файл збережеться."
    override val renameDialogTitle = "Перейменувати відео"
    override val batchRenameDialogTitle = "Пакетне перейменування дублікатів"
    override val newNamePrefixLabel = "Префікс нової назви"

    override val criteriaContentOnly = "Аналізуються лише байти вмісту та вага. Назва та дата ігноруються."
    override val wastedSpace = "Зайве місце"
    override val duplicatesFound = "Знайдено дублікатів"
    override fun duplicatesCopiesAndGroups(copies: Int, groups: Int) = "$copies копій ($groups груп)"
    override val renameVideo = "Перейменувати відео"
    override fun currentNameLabel(name: String) = "Поточна назва: $name"
    override val newName = "Нова назва"
    override val renameGroupTitle = "Перейменувати групу дублікатів"
    override fun renameGroupDesc(count: Int) = "Буде перейменовано $count однакових відео за шаблоном (наприклад: 123_1, 123_2 або 1, 2, 3...)"
    override val prefixPatternLabel = "Префікс / Шаблон"
    override val apply = "Застосувати"
    override val renameAllDuplicatesTitle = "Перейменувати всі дублікати"
    override fun renameAllDuplicatesDesc(files: Int, groups: Int) = "Усі знайдені однакові відео ($files файлів у $groups групах) будуть послідовно перейменовані."
    override val renameAll = "Перейменувати все"
    override val deleteFileTitle = "Видалити файл?"
    override fun deleteFileConfirmDesc(name: String) = "Ви дійсно бажаєте видалити файл '$name'? Цю дію неможливо скасувати."
    override val deleteRedundantTitle = "Видалити зайві дублікати?"
    override fun deleteRedundantConfirmDesc(original: String, copiesCount: Int, savedSize: String) = "Буде збережено 1 оригінал ($original), а інші $copiesCount ідентичні копії будуть видалені. Ви звільните $savedSize."
    override val keepOneAndDeleteOthers = "Залишити 1 та видалити інші"
    override fun identicalVideosCount(count: Int) = "$count ідентичні відео"
    override fun exactSizeLabel(sizeStr: String, bytes: Long) = "Точна вага: $sizeStr ($bytes B)"
    override fun hashLabel(hash: String) = "Хеш: $hash..."
    override val renameAs123 = "Перейменувати як 123"
    override val keepOnlyOne = "Залишити лише 1"
    override fun hiddenFolderTag(folderName: String) = "прихована $folderName"
    override fun estimatedTimeRemaining(seconds: Int) = if (seconds > 60) "Залишилось: ~${seconds / 60} хв ${seconds % 60} с" else "Залишилось для вердикта: ~$seconds с"
    override fun scanSpeedLabel(speed: Float, cores: Int) = "Швидкість: ${"%.1f".format(java.util.Locale.US, speed)} в/с • $cores ядер CPU"
    override fun turboCpuBadge(cores: Int) = "⚡ Турбо: $cores ядер CPU активно"
    override val matchTypeExact = "Точний збіг (100%)"
    override val matchTypeVisual = "Візуальний клон"
    override val matchTypeModified = "Модифіковане відео"
    override val masterOriginalBadge = "Оригінал (найвища якість)"
    override val redundantCopyBadge = "Дублікат"
    override val btnKeepBestQuality = "Залишити найкращу якість"
    override val verdictLabel = "Вердикт"
    override fun verdictFoundSummary(groups: Int, wasted: String) = "Виявлено $groups груп дублікатів ($wasted зайвого місця)"

    override val toastFolderUpdated = "Робочу папку оновлено"
    override val toastDefaultFolderReset = "Скинуто до стандартної папки"
    override val toastVideoFolderUpdated = "Папку для відео оновлено"
    override val toastDefaultVideoFolderReset = "Скинуто до стандартної папки відео"
    override fun toastQueueSizeSet(size: Int) = "Розмір черги встановлено: $size"
    override val toastNoDuplicates = "Дублікатів відео не виявлено"
    override fun toastDuplicateScanError(err: String) = "Помилка аналізу дублікатів: $err"
    override fun toastRenamed(name: String) = "Перейменовано: $name"
    override val toastRenameError = "Помилка перейменування відео"
    override fun toastGroupRenamed(count: Int, pattern: String) = "Групу ($count відео) перейменовано на '$pattern'"
    override fun toastBatchRenamed(count: Int, pattern: String) = "Перейменовано $count відео за шаблоном '$pattern'"
    override fun toastFileDeleted(name: String) = "Файл видалено: $name"
    override val toastDeleteError = "Не вдалося видалити файл"
    override fun toastDuplicatesRemoved(kept: Int, deleted: Int) = "Залишено $kept оригінал, видалено $deleted дублікатів"
    override fun toastNomediaCreated(name: String) = "Створено папку $name з .nomedia та дублікатами!"
    override val toastShuffleSequential = "Послідовне відтворення"
    override val toastShuffleRandom = "Режим: Повний рандом"
    override val toastShuffleSmart = "Режим: Без повторів (Розумний)"
    override fun toastProcessingFiles(count: Int, chunk: Int) = "Обробка $count файлів (черга по $chunk)..."
    override fun toastHiddenSuccess(count: Int) = "Успішно приховано всі $count файли"
    override fun toastHiddenPartial(success: Int, total: Int) = "Приховано $success з $total файлів. Деякі файли могли не скопіюватися."
    override fun toastRestoringFiles(count: Int) = "Відновлюємо $count файлів..."
    override fun toastRestoredSuccess(count: Int) = "Відновлено $count файлів у публічну галерею"
    override fun toastDeletedSuccess(count: Int) = "Видалено назавжди $count файлів"
    override fun toastBackupSuccess(count: Int) = "Успішно запаковано та зашифровано $count відео!"
    override fun toastRestoreSuccess(count: Int) = "Успішно відновлено $count відео в папку!"

    // Extended Settings & Features
    override val sectionPlayback = "Відтворення та плеєр"
    override val autoPlayNext = "Авто-перехід до наступного"
    override val autoPlayNextDesc = "Автоматично відтворювати наступне відео в списку"
    override val loopVideo = "Зациклювати відео"
    override val loopVideoDesc = "Безперервно повторювати поточне відео по колу"
    override val defaultPlaybackSpeed = "Швидкість за замовчуванням"

    override val sectionAppearance = "Вигляд та галерея"
    override val gridColumns = "Стовпців у сітці"
    override val gridColumnsDesc = "Кількість колонок мініатюр у галереї"
    override val sortOrderLabel = "Сортування файлів"
    override val sortNewest = "Спочатку нові"
    override val sortOldest = "Спочатку старі"
    override val sortNameAZ = "За назвою (А - Я)"
    override val sortNameZA = "За назвою (Я - А)"
    override val sortSizeDesc = "Спочатку великі"
    override val sortSizeAsc = "Спочатку малі"
    override val showMediaBadges = "Позначки форматів"
    override val showMediaBadgesDesc = "Показувати тип файлу та значки на мініатюрах"

    override val sectionCache = "Кеш та пам'ять"
    override val clearCache = "Очистити кеш обкладинок"
    override val clearCacheDesc = "Звільнити місце від згенерованих обкладинок відео"
    override fun cacheSizeLabel(sizeStr: String) = "Розмір кешу: $sizeStr"
    override val toastCacheCleared = "Кеш обкладинок успішно очищено"

    override val searchPlaceholder = "Пошук файлів за назвою..."
    override val noSearchResults = "Файлів за запитом не знайдено"
    override val clearSearch = "Очистити пошук"

    override val aboutSection = "Про сховище"
    override val appVersion = "Версія 2.4.0 Pro"
    override val appVersionDesc = "Повністю автономне зашифроване сховище без збору даних"
    override val easterEggIntroBtn = "Відтворити ретро-заставку"
    override val playIntroOnLaunch = "Анімація інтро при запуску"
    override val playIntroOnLaunchDesc = "Показувати заставку roTOY при холодному старті додатку"

    override val browseModeGallery = "Звичайна галерея"
    override val browseModeFolder = "Певна папка"
    override val galleryModeSubtitle = "Усі відео пристрою (Галерея)"
    override val switchBrowseModeBtn = "Перемкнути"
    override val startupFastModeReady = "⚡ Перевірка при старті вимкнена • Натисніть для сканування"
}

object RussianStrings : AppStrings {
    override val appName = "gal"
    override val back = "Назад"
    override val cancel = "Отмена"
    override val confirm = "Подтвердить"
    override val save = "Сохранить"
    override val delete = "Удалить"
    override val restore = "Восстановить"
    override val select = "Выбрать"
    override val selectAll = "Выбрать все"
    override val deselectAll = "Снять выбор"
    override fun selectedCount(count: Int) = "Выбрано: $count"
    override val close = "Закрыть"
    override val change = "Изменить"
    override val reset = "Сбросить"
    override val search = "Поиск"
    override val loading = "Загрузка..."

    override val introSkip = "Пропустить"
    override val introSkipDesc = "Пропустить интро"
    override val introTapToSkip = "Нажмите на экран, чтобы пропустить"
    override val introTagline = "INTERACTIVE MEDIA & STUDIOS"

    override val tabVideos = "Видео"
    override val tabPhotos = "Фото"
    override val tabDuplicates = "Дубликаты"

    override val searchVideosPlaceholder = "Поиск видео..."
    override val btnFolder = "Папка"
    override val btnSaveBackup = "Сохранить"
    override val btnLoadBackup = "Загрузить"
    override val btnSettings = "Настройки"
    override val scanningVideos = "Сканирование..."
    override val searchingFilesInFolder = "Поиск файлов в папке..."
    override val noHiddenPhotos = "Нет скрытых фото"
    override val noHiddenPhotosHint = "Нажмите «+», чтобы добавить фотографии в хранилище"
    override val noVideosFound = "Нет видео в выбранной папке"
    override val noVideosHint = "Выберите другую папку или добавьте видеофайлы"
    override val filterAll = "Все"
    override val filterShort = "Короткие (< 1 мин)"
    override val filterMedium = "Средние (1-10 мин)"
    override val filterLong = "Длинные (> 10 мин)"
    override val deleteConfirmTitle = "Удалить выбранные файлы?"
    override val deleteConfirmDesc = "Это действие нельзя отменить. Файлы будут безвозвратно удалены."
    override val restoreConfirmTitle = "Восстановить выбранные файлы?"
    override val restoreConfirmDesc = "Файлы будут восстановлены в исходное местоположение."

    override val backupProgressTitle = "Архивация..."
    override val restoreProgressTitle = "Восстановление из архива..."
    override val backupSuccess = "Архив успешно создан"
    override val restoreSuccess = "Архив успешно восстановлен"
    override fun folderSelectedToast(path: String) = "Выбрана папка: $path"
    override val backupEncryptNotice = "Видео упаковываются и шифруются по алгоритму AES. Их нельзя просмотреть снаружи."
    override val restoreDecryptNotice = "Архив расшифровывается и извлекается в открытую папку..."
    override fun fileProgress(current: Int, total: Int) = "Файл $current из $total"

    override val settingsTitle = "Настройки"
    override val sectionLanguage = "Язык интерфейса"
    override val languageSectionDesc = "Определение по системному языку или выбор вручную"
    override val langSystem = "Системный (авто)"
    override val langSystemDesc = "Язык системы устройства (английский по умолчанию)"
    override val langUkrainian = "Українська"
    override val langRussian = "Русский"
    override val langEnglish = "English"
    override fun currentEffectiveLanguageLabel(lang: String) = "Активный язык: $lang"

    override val sectionStorage = "Папка хранилища"
    override val workingDirLabel = "Рабочая папка"
    override val videoDirLabel = "Папка с видео"
    override val sectionBatchQueue = "Пакетная очередь"
    override val batchQueueDesc = "Количество параллельных потоков для обработки медиа"
    override val sectionPermissions = "Доступ к памяти"
    override val allFilesAccessLabel = "Полный доступ к файлам (Android 11+)"
    override val permissionGranted = "Предоставлено"
    override val permissionRequired = "Требуется предоставить"
    override val btnGrantPermission = "Предоставить доступ"
    override val sectionStats = "Статистика хранилища"
    override val totalMedia = "Всего медиа"
    override val totalSize = "Общий объем"
    override val videoFilesCount = "Видеофайлов"
    override val photosCount = "Фотографий"
    override val sectionRoToy = "Компания roTOY"
    override val btnPlayRoToyIntro = "Посмотреть интро roTOY"
    override val roToySectionDesc = "Фирменная анимация запуска и кинематографический логотип"
    override val vaultSecurityNotice = "Файлы в хранилище защищены от сканирования стандартными галереями и сторонними приложениями."
    override val hidePhotos = "Скрыть фото"
    override val allFilesAccessBannerTitle = "Разрешить полный доступ к хранилищу"
    override val allFilesAccessBannerDesc = "Требуется для просмотра и сохранения видео из любых выбранных папок."
    override val settingsOpenError = "Ошибка открытия настроек."

    override val playerLoading = "СТУДИЙНАЯ ЗАГРУЗКА..."
    override val badgeVideo = "ВИДЕО"
    override val shuffleOff = "Случайный порядок: Выкл"
    override val shuffleRandom = "Случайный порядок: Полный"
    override val shuffleSmart = "Случайный порядок: Умный"
    override val play = "Воспроизвести"
    override val pause = "Пауза"
    override val prev = "Предыдущее"
    override val next = "Следующее"
    override val speed = "Скорость"

    override val badgePhoto = "ФОТО"
    override val photoZoomHint = "Двойной тап для зума • Масштабируйте жестом"

    override val folderPickerTitle = "Выбор папки с видео"
    override val folderPickerDesc = "Приложение автоматически находит все видеофайлы внутри выбранной папки и её подпапок, включая скрытые (с точкой в начале) и папки с файлом .nomedia."
    override val btnSafPicker = "Системный выбор папки (SAF)"
    override val manualPathHeader = "Или введите путь к папке вручную:"
    override val pathFieldLabel = "Путь к папке"
    override val popularFoldersHeader = "Популярные системные папки:"
    override val btnCreateNomedia = "Создать тестовую .nomedia папку"

    override val duplicateFinderTitle = "Поиск дубликатов видео"
    override val duplicateFinderSubtitle = "Умный анализ хешей и кадров для освобождения памяти"
    override val btnScanDuplicates = "Сканировать дубликаты"
    override val btnScanningDuplicates = "Сканирование дубликатов..."
    override fun duplicatesFoundCount(count: Int) = "Найдено групп: $count"
    override fun wastedStorageLabel(size: String) = "Лишний объем: $size"
    override val noDuplicatesTitle = "Дубликатов не найдено"
    override val noDuplicatesDesc = "В выбранной папке все видеофайлы уникальны"
    override val btnDeleteDuplicates = "Удалить копии"
    override val btnBatchRename = "Пакетное переименование"
    override val btnRename = "Переименовать"
    override val originalFileBadge = "Оригинал (сохраняется)"
    override val duplicateFileBadge = "Копия (рекомендуется удалить)"
    override val duplicateDeleteConfirmTitle = "Удалить дубликаты видео?"
    override val duplicateDeleteConfirmDesc = "Все найденные дубликаты будут безвозвратно удалены, оригинальный файл сохранится."
    override val renameDialogTitle = "Переименовать видео"
    override val batchRenameDialogTitle = "Пакетное переименование дубликатов"
    override val newNamePrefixLabel = "Префикс нового имени"

    override val criteriaContentOnly = "Анализируются только байты содержимого и размер. Имя и дата игнорируются."
    override val wastedSpace = "Лишнее место"
    override val duplicatesFound = "Найдено дубликатов"
    override fun duplicatesCopiesAndGroups(copies: Int, groups: Int) = "$copies копий ($groups групп)"
    override val renameVideo = "Переименовать видео"
    override fun currentNameLabel(name: String) = "Текущее имя: $name"
    override val newName = "Новое имя"
    override val renameGroupTitle = "Переименовать группу дубликатов"
    override fun renameGroupDesc(count: Int) = "Будет переименовано $count одинаковых видео по шаблону (например: 123_1, 123_2 или 1, 2, 3...)"
    override val prefixPatternLabel = "Префикс / Шаблон"
    override val apply = "Применить"
    override val renameAllDuplicatesTitle = "Переименовать все дубликаты"
    override fun renameAllDuplicatesDesc(files: Int, groups: Int) = "Все найденные одинаковые видео ($files файлов в $groups группах) будут последовательно переименованы."
    override val renameAll = "Переименовать все"
    override val deleteFileTitle = "Удалить файл?"
    override fun deleteFileConfirmDesc(name: String) = "Вы действительно хотите удалить файл '$name'? Это действие нельзя отменить."
    override val deleteRedundantTitle = "Удалить лишние дубликаты?"
    override fun deleteRedundantConfirmDesc(original: String, copiesCount: Int, savedSize: String) = "Будет сохранен 1 оригинал ($original), а остальные $copiesCount идентичных копий будут удалены. Вы освободите $savedSize."
    override val keepOneAndDeleteOthers = "Оставить 1 и удалить копии"
    override fun identicalVideosCount(count: Int) = "$count идентичных видео"
    override fun exactSizeLabel(sizeStr: String, bytes: Long) = "Точный размер: $sizeStr ($bytes B)"
    override fun hashLabel(hash: String) = "Хэш: $hash..."
    override val renameAs123 = "Переименовать как 123"
    override val keepOnlyOne = "Оставить только 1"
    override fun hiddenFolderTag(folderName: String) = "скрытая $folderName"
    override fun estimatedTimeRemaining(seconds: Int) = if (seconds > 60) "Осталось: ~${seconds / 60} мин ${seconds % 60} с" else "Осталось для вердикта: ~$seconds с"
    override fun scanSpeedLabel(speed: Float, cores: Int) = "Скорость: ${"%.1f".format(java.util.Locale.US, speed)} в/с • $cores ядер CPU"
    override fun turboCpuBadge(cores: Int) = "⚡ Турбо: $cores ядер CPU активно"
    override val matchTypeExact = "Точное совпадение (100%)"
    override val matchTypeVisual = "Визуальный клон"
    override val matchTypeModified = "Модифицированное видео"
    override val masterOriginalBadge = "Оригинал (лучшее качество)"
    override val redundantCopyBadge = "Дубликат"
    override val btnKeepBestQuality = "Оставить лучшее качество"
    override val verdictLabel = "Вердикт"
    override fun verdictFoundSummary(groups: Int, wasted: String) = "Обнаружено $groups групп дубликатов ($wasted лишнего места)"

    override val toastFolderUpdated = "Рабочая папка обновлена"
    override val toastDefaultFolderReset = "Сброшено к стандартной папке"
    override val toastVideoFolderUpdated = "Папка для видео обновлена"
    override val toastDefaultVideoFolderReset = "Сброшено к стандартной папке видео"
    override fun toastQueueSizeSet(size: Int) = "Размер очереди установлен: $size"
    override val toastNoDuplicates = "Дубликатов видео не обнаружено"
    override fun toastDuplicateScanError(err: String) = "Ошибка анализа дубликатов: $err"
    override fun toastRenamed(name: String) = "Переименовано: $name"
    override val toastRenameError = "Ошибка переименования видео"
    override fun toastGroupRenamed(count: Int, pattern: String) = "Группа ($count видео) переименована в '$pattern'"
    override fun toastBatchRenamed(count: Int, pattern: String) = "Переименовано $count видео по шаблону '$pattern'"
    override fun toastFileDeleted(name: String) = "Файл удален: $name"
    override val toastDeleteError = "Не удалось удалить файл"
    override fun toastDuplicatesRemoved(kept: Int, deleted: Int) = "Оставлен $kept оригинал, удалено $deleted дубликатов"
    override fun toastNomediaCreated(name: String) = "Создана папка $name с .nomedia и дубликатами!"
    override val toastShuffleSequential = "Последовательное воспроизведение"
    override val toastShuffleRandom = "Режим: Полный рандом"
    override val toastShuffleSmart = "Режим: Без повторов (Умный)"
    override fun toastProcessingFiles(count: Int, chunk: Int) = "Обработка $count файлов (очередь по $chunk)..."
    override fun toastHiddenSuccess(count: Int) = "Успешно скрыты все $count файлов"
    override fun toastHiddenPartial(success: Int, total: Int) = "Скрыто $success из $total файлов. Некоторые файлы могли не скопироваться."
    override fun toastRestoringFiles(count: Int) = "Восстанавливаем $count файлов..."
    override fun toastRestoredSuccess(count: Int) = "Восстановлено $count файлов в публичную галерею"
    override fun toastDeletedSuccess(count: Int) = "Удалено навсегда $count файлов"
    override fun toastBackupSuccess(count: Int) = "Успешно упаковано и зашифровано $count видео!"
    override fun toastRestoreSuccess(count: Int) = "Успешно восстановлено $count видео в папку!"

    // Extended Settings & Features
    override val sectionPlayback = "Воспроизведение и плеер"
    override val autoPlayNext = "Авто-переход к следующему"
    override val autoPlayNextDesc = "Автоматически воспроизводить следующее видео в очереди"
    override val loopVideo = "Зацикливать видео"
    override val loopVideoDesc = "Непрерывно повторять текущее видео по кругу"
    override val defaultPlaybackSpeed = "Скорость по умолчанию"

    override val sectionAppearance = "Вид и галерея"
    override val gridColumns = "Колонок в сетке"
    override val gridColumnsDesc = "Количество колонок миниатюр в галерее"
    override val sortOrderLabel = "Сортировка файлов"
    override val sortNewest = "Сначала новые"
    override val sortOldest = "Сначала старые"
    override val sortNameAZ = "По названию (А - Я)"
    override val sortNameZA = "По названию (Я - А)"
    override val sortSizeDesc = "Сначала большие"
    override val sortSizeAsc = "Сначала маленькие"
    override val showMediaBadges = "Значки форматов"
    override val showMediaBadgesDesc = "Отображать тип файла и значки на миниатюрах"

    override val sectionCache = "Кэш и память"
    override val clearCache = "Очистить кэш обложек"
    override val clearCacheDesc = "Освободить место от сгенерированных обложек видео"
    override fun cacheSizeLabel(sizeStr: String) = "Размер кэша: $sizeStr"
    override val toastCacheCleared = "Кэш обложек успешно очищен"

    override val searchPlaceholder = "Поиск файлов по названию..."
    override val noSearchResults = "Файлов по запросу не найдено"
    override val clearSearch = "Очистить поиск"

    override val aboutSection = "О хранилище"
    override val appVersion = "Версия 2.4.0 Pro"
    override val appVersionDesc = "Полностью автономное зашифрованное хранилище без сбора данных"
    override val easterEggIntroBtn = "Воспроизвести ретро-заставку"
    override val playIntroOnLaunch = "Анимация интро при запуске"
    override val playIntroOnLaunchDesc = "Показывать заставку roTOY при холодном старте приложения"

    override val browseModeGallery = "Обычная галерея"
    override val browseModeFolder = "Определенная папка"
    override val galleryModeSubtitle = "Все видео устройства (Галерея)"
    override val switchBrowseModeBtn = "Переключить"
    override val startupFastModeReady = "⚡ Проверка при старте отключена • Нажмите для сканирования"
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

fun stringsForLanguage(language: AppLanguage): AppStrings {
    val resolved = if (language == AppLanguage.SYSTEM) {
        SettingsManager.resolveEffectiveLanguage(language)
    } else {
        language
    }
    return when (resolved) {
        AppLanguage.UKRAINIAN -> UkrainianStrings
        AppLanguage.RUSSIAN -> RussianStrings
        AppLanguage.ENGLISH, AppLanguage.SYSTEM -> EnglishStrings
    }
}
