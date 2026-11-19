package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

fun getPathFromTreeUri(context: Context, uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        val type = parts[0]
        val relative = if (parts.size > 1) parts[1] else ""
        if ("primary".equals(type, ignoreCase = true)) {
            val root = Environment.getExternalStorageDirectory().absolutePath
            if (relative.isNotEmpty()) "$root/$relative" else root
        } else {
            val root = "/storage/$type"
            if (relative.isNotEmpty()) "$root/$relative" else root
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    onPlayIntro: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val videoDirectory by viewModel.videoDirectory.collectAsStateWithLifecycle()
    val isVideoScanning by viewModel.isVideoScanning.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val isDetectingDuplicates by viewModel.isDetectingDuplicates.collectAsStateWithLifecycle()
    val detectionProgress by viewModel.detectionProgress.collectAsStateWithLifecycle()
    val isGalleryBrowseMode by viewModel.isGalleryBrowseMode.collectAsStateWithLifecycle()

    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelect.collectAsStateWithLifecycle()
    val activePhoto by viewModel.activePhoto.collectAsStateWithLifecycle()
    val activeVideo by viewModel.activeVideo.collectAsStateWithLifecycle()

    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()

    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val playedVideoIds by viewModel.playedVideoIds.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()
    val loopVideo by viewModel.loopVideo.collectAsStateWithLifecycle()
    val useBrowserPlayer by viewModel.useBrowserPlayer.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumnCount.collectAsStateWithLifecycle()
    val showMediaBadges by viewModel.showMediaBadges.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }
    var isSearchActive by remember { mutableStateOf(false) }
    var dismissPermissionBanner by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val displayedVideos = remember(videos, searchQuery, sortOrder) {
        viewModel.sortAndFilterMedia(videos, searchQuery, sortOrder)
    }
    val displayedPhotos = remember(photos, searchQuery, sortOrder) {
        viewModel.sortAndFilterMedia(photos, searchQuery, sortOrder)
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val saveArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.backupFolderToUri(uri)
    }

    val openArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreFolderFromUri(uri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val path = getPathFromTreeUri(context, uri)
            if (path != null) {
                viewModel.setVideoDirectory(path)
            } else {
                viewModel.showToast(strings.folderSelectedToast(uri.lastPathSegment ?: ""))
            }
        }
    }

    var hasAllFilesAccess by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = if (Build.VERSION.SDK_INT >= 30) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentList = when (activeTab) {
        0 -> displayedVideos
        1 -> displayedPhotos
        else -> emptyList()
    }
    val allCurrentIds = remember(currentList) { currentList.map { it.id } }
    val isAllCurrentSelected = allCurrentIds.isNotEmpty() && selectedIds.containsAll(allCurrentIds)

    val activeHeaderCount = when (activeTab) {
        0 -> displayedVideos.size
        1 -> displayedPhotos.size
        else -> duplicateGroups.size
    }

    BackHandler(enabled = showSettings || isMultiSelect || activeVideo != null || activePhoto != null || isSearchActive) {
        when {
            showSettings -> showSettings = false
            isSearchActive -> {
                isSearchActive = false
                viewModel.setSearchQuery("")
            }
            isMultiSelect -> viewModel.clearSelection()
            activeVideo != null -> viewModel.viewVideo(null)
            activePhoto != null -> viewModel.viewPhoto(null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(VaultBackground)
                        .statusBarsPadding()
                ) {
                    GalleryTopHeader(
                        strings = strings,
                        isMultiSelect = isMultiSelect,
                        selectedCount = selectedIds.size,
                        isAllSelected = isAllCurrentSelected,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        activeCount = activeHeaderCount,
                        onClearSelection = { viewModel.clearSelection() },
                        onToggleSelectAll = {
                            if (isAllCurrentSelected) viewModel.clearSelection()
                            else viewModel.selectAll(allCurrentIds)
                        },
                        onSearchOpen = { isSearchActive = true },
                        onSearchClose = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        },
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCycleColumns = {
                            val nextCols = when (gridColumns) {
                                2 -> 3
                                3 -> 4
                                else -> 2
                            }
                            viewModel.setGridColumnCount(nextCols)
                        },
                        onOpenSettings = { showSettings = true }
                    )

                    GalleryModeTabs(
                        strings = strings,
                        activeTab = activeTab,
                        videosCount = videos.size,
                        photosCount = photos.size,
                        duplicatesCount = duplicateGroups.size,
                        onSelectTab = { activeTab = it }
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = activeTab == 1 && !isMultiSelect && activePhoto == null && activeVideo == null && !showSettings,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        containerColor = VaultPrimary,
                        contentColor = VaultOnPrimary,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("import_photo_fab")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = strings.hidePhotos)
                            Text(
                                text = strings.hidePhotos,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            containerColor = VaultBackground,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess && !dismissPermissionBanner) {
                        AllFilesAccessBanner(
                            strings = strings,
                            onGrantClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                    } catch (_: Exception) {
                                        viewModel.showToast(strings.settingsOpenError)
                                    }
                                }
                            },
                            onDismiss = { dismissPermissionBanner = true }
                        )
                    }

                    when (activeTab) {
                        0 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                VideoSourceToolbar(
                                    strings = strings,
                                    isGalleryBrowseMode = isGalleryBrowseMode,
                                    videoDirectory = videoDirectory,
                                    isBusy = isVideoScanning || isDetectingDuplicates,
                                    onSetBrowseMode = { viewModel.setGalleryBrowseMode(it) },
                                    onOpenFolderPicker = { showFolderPicker = true },
                                    onRefresh = { viewModel.rescanVideoFolder(runDuplicateScan = false) },
                                    onBackup = {
                                        val folderName = File(videoDirectory).name.ifEmpty { "folder" }
                                        saveArchiveLauncher.launch("${folderName}_backup.vault")
                                    },
                                    onRestore = {
                                        openArchiveLauncher.launch(arrayOf("*/*"))
                                    }
                                )

                                if (isDetectingDuplicates) {
                                    DuplicateBackgroundProgressBar(
                                        label = strings.btnScanningDuplicates,
                                        percentage = detectionProgress?.percentage ?: 0.15f
                                    )
                                }

                                if (displayedVideos.isEmpty()) {
                                    if (videos.isNotEmpty() && searchQuery.isNotEmpty()) {
                                        VaultSearchEmptyState(
                                            message = strings.noSearchResults,
                                            clearLabel = strings.clearSearch,
                                            onClearSearch = { viewModel.setSearchQuery("") }
                                        )
                                    } else {
                                        VaultEmptyState(
                                            icon = Icons.Default.VideoLibrary,
                                            title = strings.noVideosFound,
                                            hint = strings.noVideosHint,
                                            primaryActionLabel = strings.btnFolder,
                                            primaryActionIcon = Icons.Default.FolderOpen,
                                            onPrimaryAction = { showFolderPicker = true }
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(gridColumns),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 12.dp,
                                            end = 12.dp,
                                            top = 4.dp,
                                            bottom = if (isMultiSelect) 96.dp else 24.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(displayedVideos, key = { it.id }) { media ->
                                            val isSelected = selectedIds.contains(media.id)
                                            VideoGridCard(
                                                media = media,
                                                isSelected = isSelected,
                                                isMultiSelect = isMultiSelect,
                                                showMediaBadges = showMediaBadges,
                                                selectContentDescription = strings.select,
                                                badgeContentDescription = strings.badgeVideo,
                                                onLongClick = { viewModel.toggleSelection(media.id) },
                                                onClick = {
                                                    if (isMultiSelect) {
                                                        viewModel.toggleSelection(media.id)
                                                    } else {
                                                        viewModel.viewPhoto(null)
                                                        viewModel.viewVideo(media)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (displayedPhotos.isEmpty()) {
                                if (photos.isNotEmpty() && searchQuery.isNotEmpty()) {
                                    VaultSearchEmptyState(
                                        message = strings.noSearchResults,
                                        clearLabel = strings.clearSearch,
                                        onClearSearch = { viewModel.setSearchQuery("") }
                                    )
                                } else {
                                    VaultEmptyState(
                                        icon = Icons.Default.PhotoLibrary,
                                        title = strings.noHiddenPhotos,
                                        hint = strings.noHiddenPhotosHint
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(gridColumns),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = if (isMultiSelect) 96.dp else 80.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(displayedPhotos, key = { it.id }) { media ->
                                        val isSelected = selectedIds.contains(media.id)
                                        PhotoGridCard(
                                            media = media,
                                            isSelected = isSelected,
                                            isMultiSelect = isMultiSelect,
                                            selectContentDescription = strings.select,
                                            onLongClick = { viewModel.toggleSelection(media.id) },
                                            onClick = {
                                                if (isMultiSelect) {
                                                    viewModel.toggleSelection(media.id)
                                                } else {
                                                    viewModel.viewVideo(null)
                                                    viewModel.viewPhoto(media)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            DuplicateDetectorView(
                                viewModel = viewModel,
                                duplicateGroups = duplicateGroups,
                                isDetecting = isDetectingDuplicates,
                                onOpenVideoInViewer = { videoItem ->
                                    viewModel.viewPhoto(null)
                                    viewModel.viewVideo(videoItem.toHiddenMedia())
                                },
                                onSelectFolderClick = { showFolderPicker = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                MultiSelectActionBar(
                    strings = strings,
                    visible = isMultiSelect && selectedIds.isNotEmpty(),
                    selectedCount = selectedIds.size,
                    onDeleteSelected = { viewModel.deleteSelectedPermanently() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // FULLSCREEN VIDEO PLAYER OVERLAY
        AnimatedVisibility(
            visible = activeVideo != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            activeVideo?.let { currentVideo ->
                VideoPlayerView(
                    media = currentVideo,
                    speed = playbackSpeed,
                    shuffleMode = shuffleMode,
                    playedCount = playedVideoIds.size,
                    totalCount = videos.size,
                    autoPlayNext = autoPlayNext,
                    loopVideo = loopVideo,
                    useBrowserPlayer = useBrowserPlayer,
                    onTogglePlayerEngine = { viewModel.toggleBrowserPlayer() },
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onNext = { viewModel.nextVideo() },
                    onPrev = { viewModel.prevVideo() },
                    onDelete = {
                        viewModel.deleteSinglePermanently(currentVideo)
                        viewModel.viewVideo(null)
                    },
                    onClose = { viewModel.viewVideo(null) },
                    onToggleLoop = { viewModel.setLoopVideo(!loopVideo) }
                )
            }
        }

        // FULLSCREEN PHOTO DETAIL OVERLAY
        AnimatedVisibility(
            visible = activePhoto != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            activePhoto?.let { currentPhoto ->
                PhotoDetailView(
                    media = currentPhoto,
                    onNext = { viewModel.nextPhoto(currentPhoto) },
                    onPrev = { viewModel.prevPhoto(currentPhoto) },
                    onDelete = {
                        viewModel.deleteSinglePermanently(currentPhoto)
                        viewModel.viewPhoto(null)
                    },
                    onClose = { viewModel.viewPhoto(null) }
                )
            }
        }

        // BACKUP & RESTORE PROGRESS MODAL
        if (isBackupBusy && backupProgress != null) {
            BackupProgressDialog(strings = strings, prog = backupProgress!!)
        }

        // FOLDER PICKER DIALOG
        if (showFolderPicker) {
            FolderPickerDialog(
                currentPath = videoDirectory,
                onDismiss = { showFolderPicker = false },
                onFolderSelected = { viewModel.setVideoDirectory(it) },
                onOpenSafPicker = { openDocumentTreeLauncher.launch(null) },
                onCreateTestNomediaFolder = { viewModel.createTestHiddenFolderWithDuplicates() }
            )
        }

        // SETTINGS SCREEN OVERLAY
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false },
                onPlayIntro = {
                    showSettings = false
                    onPlayIntro()
                }
            )
        }
    }
}
