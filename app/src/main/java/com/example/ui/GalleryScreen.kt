package com.example.ui

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.example.data.MediaSortOrder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.HiddenMedia
import com.example.data.VideoItem
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

fun getPathFromTreeUri(context: android.content.Context, uri: Uri): String? {
    try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        val type = parts[0]
        val relative = if (parts.size > 1) parts[1] else ""
        if ("primary".equals(type, ignoreCase = true)) {
            val root = Environment.getExternalStorageDirectory().absolutePath
            return if (relative.isNotEmpty()) "$root/$relative" else root
        } else {
            val root = "/storage/$type"
            return if (relative.isNotEmpty()) "$root/$relative" else root
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val lastScanDurationSec by viewModel.lastScanDurationSec.collectAsStateWithLifecycle()
    val lastScanVerdict by viewModel.lastScanVerdict.collectAsStateWithLifecycle()
    val isGalleryBrowseMode by viewModel.isGalleryBrowseMode.collectAsStateWithLifecycle()
    var dismissPermissionBanner by remember { mutableStateOf(false) }

    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelect.collectAsStateWithLifecycle()
    val activePhoto by viewModel.activePhoto.collectAsStateWithLifecycle()
    val activeVideo by viewModel.activeVideo.collectAsStateWithLifecycle()

    // Backup & Restore state
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()

    // Player Options
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val playedVideoIds by viewModel.playedVideoIds.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()
    val loopVideo by viewModel.loopVideo.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumnCount.collectAsStateWithLifecycle()
    val showMediaBadges by viewModel.showMediaBadges.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }

    val displayedVideos = remember(videos, searchQuery, sortOrder) {
        viewModel.sortAndFilterMedia(videos, searchQuery, sortOrder)
    }
    val displayedPhotos = remember(photos, searchQuery, sortOrder) {
        viewModel.sortAndFilterMedia(photos, searchQuery, sortOrder)
    }

    // Toast updates
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    // 3 Tabs: 0 = Videos (default), 1 = Photos, 2 = Duplicates
    var activeTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }

    // Manage toast notifications
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 1. Save (Pack and encrypt folder to Google Drive / Storage)
    val saveArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.backupFolderToUri(uri)
        }
    }

    // 2. Load (Unpack and decrypt archive from Google Drive / Storage)
    val openArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFolderFromUri(uri)
        }
    }

    // Photo picker launcher (for Photos tab)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    // System folder tree picker (SAF)
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if permission not persistable
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
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 30) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = if (Build.VERSION.SDK_INT >= 30) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentList = if (activeTab == 0) displayedVideos else if (activeTab == 1) displayedPhotos else emptyList()
    val allCurrentIds = currentList.map { it.id }
    val isAllCurrentSelected = allCurrentIds.isNotEmpty() && selectedIds.containsAll(allCurrentIds)

    // Handle system back navigation
    BackHandler(enabled = showSettings || isMultiSelect || activeVideo != null || activePhoto != null || isSearchActive) {
        if (showSettings) {
            showSettings = false
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.setSearchQuery("")
        } else if (isMultiSelect) {
            viewModel.clearSelection()
        } else if (activeVideo != null) {
            viewModel.viewVideo(null)
        } else if (activePhoto != null) {
            viewModel.viewPhoto(null)
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
                    // TOP APP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMultiSelect) {
                            IconButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(VaultSurfaceElevated)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = strings.deselectAll,
                                    tint = VaultTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.selectedCount(selectedIds.size),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = VaultPrimaryLight
                                )
                                Text(
                                    text = strings.select,
                                    fontSize = 11.sp,
                                    color = VaultTextMuted
                                )
                            }
                            // Select / Deselect All
                            TextButton(
                                onClick = {
                                    if (isAllCurrentSelected) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll(allCurrentIds)
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isAllCurrentSelected) strings.deselectAll else strings.selectAll,
                                    color = VaultPrimaryLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (isSearchActive) {
                            // Search Active Top Bar
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VaultSurfaceElevated,
                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            isSearchActive = false
                                            viewModel.setSearchQuery("")
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = VaultTextPrimary, modifier = Modifier.size(18.dp))
                                    }
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        placeholder = { Text(strings.searchPlaceholder, fontSize = 12.sp, color = VaultTextMuted) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = VaultTextPrimary,
                                            unfocusedTextColor = VaultTextPrimary
                                        )
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.setSearchQuery("") },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = strings.clearSearch, tint = VaultTextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            // Standard Header with logo and title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = VaultPrimary.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.4f)),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = VaultPrimaryLight,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = strings.appName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = VaultTextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Vault Media",
                                        fontSize = 11.sp,
                                        color = VaultTextSecondary
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Search Toggle Action Button
                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(VaultSurfaceElevated)
                                        .testTag("search_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = strings.search,
                                        tint = VaultTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Quick Columns Cycle Button (2 -> 3 -> 4 -> 2)
                                IconButton(
                                    onClick = {
                                        val nextCols = if (gridColumns == 2) 3 else if (gridColumns == 3) 4 else 2
                                        viewModel.setGridColumnCount(nextCols)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(VaultSurfaceElevated)
                                        .testTag("columns_btn")
                                ) {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = strings.gridColumns,
                                        tint = VaultTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Settings Action Button
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(VaultSurfaceElevated)
                                        .testTag("settings_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = strings.btnSettings,
                                        tint = VaultTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3 CLEAN SEGMENTED TABS (Videos, Photos, Duplicates)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VaultSurfaceElevated)
                            .border(1.dp, VaultBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Videos Tab (Default)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (activeTab == 0) VaultPrimary else Color.Transparent
                                )
                                .clickable { activeTab = 0 }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("tab_videos"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = if (activeTab == 0) VaultOnPrimary else VaultTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = strings.tabVideos,
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == 0) VaultOnPrimary else VaultTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeTab == 0) VaultPrimaryDark.copy(alpha = 0.5f) else VaultSurfaceHighlight
                                ) {
                                    Text(
                                        text = "${videos.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == 0) VaultOnPrimary else VaultTextMuted,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Photos Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (activeTab == 1) VaultPrimary else Color.Transparent
                                )
                                .clickable { activeTab = 1 }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("tab_photos"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null,
                                    tint = if (activeTab == 1) VaultOnPrimary else VaultTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = strings.tabPhotos,
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == 1) VaultOnPrimary else VaultTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeTab == 1) VaultPrimaryDark.copy(alpha = 0.5f) else VaultSurfaceHighlight
                                ) {
                                    Text(
                                        text = "${photos.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == 1) VaultOnPrimary else VaultTextMuted,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Duplicate Detect Tab
                        Box(
                            modifier = Modifier
                                .weight(1.15f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (activeTab == 2) VaultPrimary else Color.Transparent
                                )
                                .clickable { activeTab = 2 }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("tab_detect"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Compare,
                                    contentDescription = null,
                                    tint = if (activeTab == 2) VaultOnPrimary else VaultAccentAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = strings.tabDuplicates,
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == 2) VaultOnPrimary else VaultTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (duplicateGroups.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (activeTab == 2) VaultPrimaryDark.copy(alpha = 0.5f) else VaultAccentAmber.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "${duplicateGroups.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeTab == 2) VaultOnPrimary else VaultAccentAmber,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                        shape = RoundedCornerShape(16.dp),
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
                    // PERMISSION BANNER (COMPACT & DISMISSIBLE)
                    if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess && !dismissPermissionBanner) {
                        Surface(
                            color = VaultErrorContainer,
                            border = BorderStroke(1.dp, VaultError.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SecurityUpdateWarning,
                                    contentDescription = null,
                                    tint = VaultError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(
                                        text = strings.allFilesAccessBannerTitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = strings.allFilesAccessBannerDesc,
                                        fontSize = 10.sp,
                                        color = VaultTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                context.startActivity(intent)
                                            } catch (ex: Exception) {
                                                viewModel.showToast(strings.settingsOpenError)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VaultError,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = strings.btnGrantPermission,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { dismissPermissionBanner = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.cancel,
                                        tint = VaultTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // TABS CONTENT
                    when (activeTab) {
                        0 -> {
                            // TAB 0: VIDEO FOLDER BROWSER (COMPACT & STREAMLINED)
                            Column(modifier = Modifier.fillMaxSize()) {
                                // COMPACT MODE SWITCHER & SOURCE BAR
                                Surface(
                                    color = VaultSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, VaultBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Row 0: Clean Segmented Mode Switcher (Gallery vs Folder)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(VaultBackground)
                                                .border(1.dp, VaultBorderSubtle, RoundedCornerShape(10.dp))
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Option 1: Standard Gallery
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isGalleryBrowseMode) VaultPrimary.copy(alpha = 0.25f) else Color.Transparent
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isGalleryBrowseMode) VaultPrimary else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { viewModel.setGalleryBrowseMode(true) }
                                                    .padding(vertical = 7.dp, horizontal = 8.dp)
                                                    .testTag("mode_gallery_btn"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.VideoLibrary,
                                                        contentDescription = null,
                                                        tint = if (isGalleryBrowseMode) VaultPrimaryLight else VaultTextSecondary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = strings.browseModeGallery,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isGalleryBrowseMode) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isGalleryBrowseMode) VaultPrimaryLight else VaultTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Option 2: Specific Folder
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (!isGalleryBrowseMode) VaultAccentCyan.copy(alpha = 0.2f) else Color.Transparent
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (!isGalleryBrowseMode) VaultAccentCyan else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { viewModel.setGalleryBrowseMode(false) }
                                                    .padding(vertical = 7.dp, horizontal = 8.dp)
                                                    .testTag("mode_folder_btn"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = if (!isGalleryBrowseMode) VaultAccentCyan else VaultTextSecondary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = strings.browseModeFolder,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (!isGalleryBrowseMode) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (!isGalleryBrowseMode) VaultAccentCyan else VaultTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        // Row 1: Source Info + Folder Selector / Refresh + Compact Backup Buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (isGalleryBrowseMode) Icons.Default.VideoLibrary else Icons.Default.FolderOpen,
                                                    contentDescription = null,
                                                    tint = if (isGalleryBrowseMode) VaultPrimaryLight else VaultAccentCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = if (isGalleryBrowseMode) strings.galleryModeSubtitle else File(videoDirectory).name.ifEmpty { videoDirectory },
                                                    fontSize = 12.sp,
                                                    color = VaultTextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (!isGalleryBrowseMode) {
                                                    Button(
                                                        onClick = { showFolderPicker = true },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = VaultSurfaceElevated,
                                                            contentColor = VaultAccentCyan
                                                        ),
                                                        border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.4f)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(
                                                            text = strings.btnFolder,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { viewModel.rescanVideoFolder(runDuplicateScan = false) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    if (isVideoScanning || isDetectingDuplicates) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(14.dp),
                                                            color = VaultAccentAmber,
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = strings.reset,
                                                            tint = VaultPrimaryLight,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                // Backup Button (Compact)
                                                IconButton(
                                                    onClick = {
                                                        val folderName = File(videoDirectory).name.ifEmpty { "folder" }
                                                        val defaultFileName = "${folderName}_backup.vault"
                                                        saveArchiveLauncher.launch(defaultFileName)
                                                    },
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(VaultPrimary.copy(alpha = 0.15f))
                                                        .border(1.dp, VaultPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                        .testTag("save_to_drive_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudUpload,
                                                        contentDescription = strings.save,
                                                        tint = VaultPrimaryLight,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }

                                                // Restore Button (Compact)
                                                IconButton(
                                                    onClick = {
                                                        openArchiveLauncher.launch(arrayOf("*/*"))
                                                    },
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(VaultAccentCyan.copy(alpha = 0.12f))
                                                        .border(1.dp, VaultAccentCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                        .testTag("load_from_drive_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudDownload,
                                                        contentDescription = strings.btnLoadBackup,
                                                        tint = VaultAccentCyan,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Background Scan Progress (Slim, only when detection is running)
                                if (isDetectingDuplicates) {
                                    val prog = detectionProgress
                                    val pct = (prog?.percentage ?: 0.15f).coerceIn(0.05f, 1f)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "⚡ Перевірка дублікатів у фоні...",
                                                fontSize = 10.sp,
                                                color = VaultAccentAmber,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${(pct * 100).toInt()}%",
                                                fontSize = 10.sp,
                                                color = VaultAccentAmber,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        LinearProgressIndicator(
                                            progress = { pct },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp)),
                                            color = VaultAccentAmber,
                                            trackColor = VaultSurfaceHighlight
                                        )
                                    }
                                }

                                if (displayedVideos.isEmpty()) {
                                    if (videos.isNotEmpty() && searchQuery.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SearchOff,
                                                contentDescription = null,
                                                tint = VaultTextSecondary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = strings.noSearchResults,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VaultTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = { viewModel.setSearchQuery("") },
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(strings.clearSearch)
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = VaultPrimary.copy(alpha = 0.1f),
                                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.25f)),
                                                modifier = Modifier.size(90.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.VideoLibrary,
                                                        contentDescription = null,
                                                        tint = VaultPrimaryLight,
                                                        modifier = Modifier.size(42.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Text(
                                                text = strings.noVideosFound,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VaultTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = strings.noVideosHint,
                                                fontSize = 13.sp,
                                                color = VaultTextSecondary,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = { showFolderPicker = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(strings.btnFolder)
                                                }

                                                OutlinedButton(
                                                    onClick = { viewModel.createTestHiddenFolderWithDuplicates() },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultAccentCyan),
                                                    border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(strings.btnCreateNomedia)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(gridColumns),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = if (isMultiSelect) 96.dp else 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(displayedVideos, key = { it.id }) { media ->
                                            val isSelected = selectedIds.contains(media.id)

                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(VaultSurfaceElevated)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .combinedClickable(
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
                                                    .testTag("video_item_${media.id}")
                                            ) {
                                                // Mid-frame cached cover with excessive black avoidance
                                                VideoCoverThumbnail(
                                                    videoPath = media.internalPath,
                                                    fileSize = media.size,
                                                    lastModified = media.dateHidden,
                                                    contentDescription = media.originalFilename,
                                                    cornerRadius = 10.dp,
                                                    showPlayOverlay = false,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(Color.Transparent, VaultBackground.copy(alpha = 0.85f)),
                                                                startY = 60f
                                                            )
                                                        )
                                                )

                                                if (showMediaBadges) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = VaultBackground.copy(alpha = 0.75f),
                                                        border = BorderStroke(1.dp, VaultBorderSubtle),
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .align(Alignment.Center)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = strings.badgeVideo,
                                                                tint = VaultPrimary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Filename label
                                                Text(
                                                    text = media.originalFilename,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = VaultTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                                )

                                                if (isMultiSelect) {
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isSelected) VaultPrimary else VaultBackground.copy(alpha = 0.7f)
                                                            )
                                                            .border(1.5.dp, if (isSelected) VaultPrimaryLight else Color.White.copy(alpha = 0.6f), CircleShape)
                                                            .align(Alignment.TopEnd),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = strings.select,
                                                                tint = VaultOnPrimary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // TAB 1: PHOTOS TAB
                            if (displayedPhotos.isEmpty()) {
                                if (photos.isNotEmpty() && searchQuery.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = VaultTextSecondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = strings.noSearchResults,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VaultTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = { viewModel.setSearchQuery("") },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(strings.clearSearch)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = VaultPrimary.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.25f)),
                                            modifier = Modifier.size(90.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PhotoLibrary,
                                                    contentDescription = null,
                                                    tint = VaultPrimaryLight,
                                                    modifier = Modifier.size(42.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Text(
                                            text = strings.noHiddenPhotos,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VaultTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = strings.noHiddenPhotosHint,
                                            fontSize = 13.sp,
                                            color = VaultTextSecondary,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(gridColumns),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (isMultiSelect) 96.dp else 80.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(displayedPhotos, key = { it.id }) { media ->
                                        val isSelected = selectedIds.contains(media.id)

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(VaultSurfaceElevated)
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .combinedClickable(
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
                                                .testTag("photo_item_${media.id}")
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(File(media.internalPath))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = media.originalFilename,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            if (isMultiSelect) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(6.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) VaultPrimary else VaultBackground.copy(alpha = 0.7f)
                                                        )
                                                        .border(1.5.dp, if (isSelected) VaultPrimaryLight else Color.White.copy(alpha = 0.6f), CircleShape)
                                                        .align(Alignment.TopEnd),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = strings.select,
                                                            tint = VaultOnPrimary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // TAB 2: DUPLICATE DETECTION TAB
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

                // FLOATING CONTEXTUAL ACTION BAR FOR MULTI-SELECT
                AnimatedVisibility(
                    visible = isMultiSelect && selectedIds.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.dp, VaultBorderSubtle),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.selectedCount(selectedIds.size),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = VaultTextPrimary
                                )
                            }

                            // Delete Action
                            Button(
                                onClick = { viewModel.deleteSelectedPermanently() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VaultErrorContainer,
                                    contentColor = VaultError
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("multi_delete_btn")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.delete, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // FULLSCREEN VIDEO PLAYER OVERLAY (Classic Analog/Retro Styling)
        AnimatedVisibility(
            visible = activeVideo != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Fully consume touches to prevent leaking to list underneath */ }
        ) {
            if (activeVideo != null) {
                VideoPlayerView(
                    media = activeVideo!!,
                    speed = playbackSpeed,
                    shuffleMode = shuffleMode,
                    playedCount = playedVideoIds.size,
                    totalCount = videos.size,
                    autoPlayNext = autoPlayNext,
                    loopVideo = loopVideo,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onNext = { viewModel.nextVideo() },
                    onPrev = { viewModel.prevVideo() },
                    onRestore = {
                        val video = activeVideo!!
                        viewModel.restoreSingle(video)
                        viewModel.viewVideo(null)
                    },
                    onDelete = {
                        val video = activeVideo!!
                        viewModel.deleteSinglePermanently(video)
                        viewModel.viewVideo(null)
                    },
                    onClose = {
                        viewModel.viewVideo(null)
                    },
                    onToggleLoop = {
                        viewModel.setLoopVideo(!loopVideo)
                    },
                    embeddedInTab = false
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
                ) { /* Fully consume touches to prevent leaking to list underneath */ }
        ) {
            if (activePhoto != null) {
                PhotoDetailView(
                    media = activePhoto!!,
                    onNext = { viewModel.nextPhoto(activePhoto!!) },
                    onPrev = { viewModel.prevPhoto(activePhoto!!) },
                    onRestore = {
                        val photo = activePhoto!!
                        viewModel.restoreSingle(photo)
                        viewModel.viewPhoto(null)
                    },
                    onDelete = {
                        val photo = activePhoto!!
                        viewModel.deleteSinglePermanently(photo)
                        viewModel.viewPhoto(null)
                    },
                    onClose = {
                        viewModel.viewPhoto(null)
                    },
                    embeddedInTab = false
                )
            }
        }

        // BACKUP & RESTORE PROGRESS MODAL
        if (isBackupBusy && backupProgress != null) {
            val prog = backupProgress!!
            AlertDialog(
                onDismissRequest = {}, // Non-cancellable during atomic operation
                containerColor = VaultSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                titleContentColor = VaultTextPrimary,
                textContentColor = VaultTextSecondary,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = (if (prog.isPacking) VaultPrimary else VaultAccentCyan).copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (prog.isPacking) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = if (prog.isPacking) VaultPrimaryLight else VaultAccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = if (prog.isPacking) strings.backupProgressTitle else strings.restoreProgressTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = prog.currentFileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = VaultTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (prog.totalFiles > 0) {
                            Text(
                                text = strings.fileProgress(prog.currentFile, prog.totalFiles),
                                fontSize = 12.sp,
                                color = VaultTextSecondary
                            )
                            LinearProgressIndicator(
                                progress = { prog.currentFile.toFloat() / maxOf(1, prog.totalFiles) },
                                color = if (prog.isPacking) VaultPrimary else VaultAccentCyan,
                                trackColor = VaultSurfaceHighlight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                color = if (prog.isPacking) VaultPrimary else VaultAccentCyan,
                                trackColor = VaultSurfaceHighlight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                        Text(
                            text = if (prog.isPacking)
                                strings.backupEncryptNotice
                            else
                                strings.restoreDecryptNotice,
                            fontSize = 11.sp,
                            color = VaultTextMuted
                        )
                    }
                },
                confirmButton = {}
            )
        }

        // FOLDER PICKER DIALOG
        if (showFolderPicker) {
            FolderPickerDialog(
                currentPath = videoDirectory,
                onDismiss = { showFolderPicker = false },
                onFolderSelected = { path ->
                    viewModel.setVideoDirectory(path)
                },
                onOpenSafPicker = {
                    openDocumentTreeLauncher.launch(null)
                },
                onCreateTestNomediaFolder = {
                    viewModel.createTestHiddenFolderWithDuplicates()
                }
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
