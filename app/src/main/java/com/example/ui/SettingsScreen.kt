package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppLanguage
import com.example.data.MediaSortOrder
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPlayIntro: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val workingDir by viewModel.workingDirectory.collectAsStateWithLifecycle()
    val videoDirectory by viewModel.videoDirectory.collectAsStateWithLifecycle()
    val queueSize by viewModel.batchQueueSize.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    // Extended settings flows
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()
    val loopVideo by viewModel.loopVideo.collectAsStateWithLifecycle()
    val defaultSpeed by viewModel.defaultPlaybackSpeed.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumnCount.collectAsStateWithLifecycle()
    val showMediaBadges by viewModel.showMediaBadges.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val cacheSize by viewModel.thumbnailCacheSize.collectAsStateWithLifecycle()
    val playIntroOnLaunch by viewModel.playIntroOnLaunch.collectAsStateWithLifecycle()

    var showEditFolderDialog by remember { mutableStateOf(false) }
    var showFolderPickerDialog by remember { mutableStateOf(false) }
    var tempFolderPath by remember { mutableStateOf(workingDir) }

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
                viewModel.refreshThumbnailCacheSize()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val totalSize = (photos.sumOf { it.size } + videos.sumOf { it.size })
    val formattedTotalSize = when {
        totalSize > 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", totalSize.toDouble() / (1024 * 1024 * 1024))
        totalSize > 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", totalSize.toDouble() / (1024 * 1024))
        else -> "${totalSize / 1024} KB"
    }

    val formattedCacheSize = when {
        cacheSize > 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", cacheSize.toDouble() / (1024 * 1024))
        cacheSize > 0 -> "${cacheSize / 1024} KB"
        else -> "0 KB"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settingsTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = VaultTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = strings.back,
                            tint = VaultTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultBackground
                )
            )
        },
        containerColor = VaultBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STATS OVERVIEW CARD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        icon = Icons.Default.Photo,
                        value = "${photos.size}",
                        label = strings.photosCount,
                        accentColor = VaultPrimaryLight
                    )
                    Divider(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp),
                        color = VaultBorderSubtle
                    )
                    StatItem(
                        icon = Icons.Default.Videocam,
                        value = "${videos.size}",
                        label = strings.videoFilesCount,
                        accentColor = VaultAccentCyan
                    )
                    Divider(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp),
                        color = VaultBorderSubtle
                    )
                    StatItem(
                        icon = Icons.Default.Storage,
                        value = formattedTotalSize,
                        label = strings.totalSize,
                        accentColor = VaultAccentGreen
                    )
                }
            }

            // SECTION 1: LANGUAGE SELECTION
            SettingsSectionHeader(title = strings.sectionLanguage, icon = Icons.Default.Language)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.languageSectionDesc,
                        fontSize = 12.sp,
                        color = VaultTextSecondary
                    )

                    val languageOptions = listOf(
                        AppLanguage.SYSTEM to strings.langSystem,
                        AppLanguage.UKRAINIAN to strings.langUkrainian,
                        AppLanguage.RUSSIAN to strings.langRussian,
                        AppLanguage.ENGLISH to strings.langEnglish
                    )

                    languageOptions.forEach { (lang, label) ->
                        val isSelected = appLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) VaultPrimary.copy(alpha = 0.15f) else VaultSurface,
                            border = BorderStroke(1.dp, if (isSelected) VaultPrimary else VaultBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setLanguage(lang) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (isSelected) VaultPrimaryLight else VaultTextPrimary
                                    )
                                    if (lang == AppLanguage.SYSTEM) {
                                        Text(
                                            text = strings.langSystemDesc,
                                            fontSize = 11.sp,
                                            color = VaultTextMuted
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = VaultPrimaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: PLAYBACK & PLAYER SETTINGS (NEW)
            SettingsSectionHeader(title = strings.sectionPlayback, icon = Icons.Default.PlayCircleOutline)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Auto-play Next Video Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.autoPlayNext,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = strings.autoPlayNextDesc,
                                fontSize = 11.sp,
                                color = VaultTextSecondary
                            )
                        }
                        Switch(
                            checked = autoPlayNext,
                            onCheckedChange = { viewModel.setAutoPlayNext(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultPrimaryLight,
                                checkedTrackColor = VaultPrimary,
                                uncheckedTrackColor = VaultSurface
                            )
                        )
                    }

                    Divider(color = VaultBorderSubtle)

                    // Loop Video Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.loopVideo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = strings.loopVideoDesc,
                                fontSize = 11.sp,
                                color = VaultTextSecondary
                            )
                        }
                        Switch(
                            checked = loopVideo,
                            onCheckedChange = { viewModel.setLoopVideo(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultPrimaryLight,
                                checkedTrackColor = VaultPrimary,
                                uncheckedTrackColor = VaultSurface
                            )
                        )
                    }

                    Divider(color = VaultBorderSubtle)

                    // Default Playback Speed Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.defaultPlaybackSpeed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = VaultTextPrimary
                        )

                        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            speedOptions.forEach { sp ->
                                val isSelected = defaultSpeed == sp
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) VaultPrimary else VaultBackground)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setDefaultPlaybackSpeed(sp) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sp}x",
                                        color = if (isSelected) VaultOnPrimary else VaultTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: APPEARANCE & GALLERY SETTINGS (NEW)
            SettingsSectionHeader(title = strings.sectionAppearance, icon = Icons.Default.GridView)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Grid Columns (2, 3, 4)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = strings.gridColumns,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = VaultTextPrimary
                                )
                                Text(
                                    text = strings.gridColumnsDesc,
                                    fontSize = 11.sp,
                                    color = VaultTextSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VaultPrimary.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "$gridColumns",
                                    color = VaultPrimaryLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 3, 4).forEach { cols ->
                                val isSelected = gridColumns == cols
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) VaultPrimary else VaultBackground)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setGridColumnCount(cols) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$cols columns",
                                        color = if (isSelected) VaultOnPrimary else VaultTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = VaultBorderSubtle)

                    // Show Media Badges Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.showMediaBadges,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = strings.showMediaBadgesDesc,
                                fontSize = 11.sp,
                                color = VaultTextSecondary
                            )
                        }
                        Switch(
                            checked = showMediaBadges,
                            onCheckedChange = { viewModel.setShowMediaBadges(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultPrimaryLight,
                                checkedTrackColor = VaultPrimary,
                                uncheckedTrackColor = VaultSurface
                            )
                        )
                    }

                    Divider(color = VaultBorderSubtle)

                    // Sort Order Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.sortOrderLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = VaultTextPrimary
                        )

                        val sortOptions = listOf(
                            MediaSortOrder.NEWEST_FIRST to strings.sortNewest,
                            MediaSortOrder.OLDEST_FIRST to strings.sortOldest,
                            MediaSortOrder.NAME_AZ to strings.sortNameAZ,
                            MediaSortOrder.SIZE_DESC to strings.sortSizeDesc
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sortOptions.forEach { (order, label) ->
                                val isSelected = sortOrder == order
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) VaultPrimary else VaultBackground)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setSortOrder(order) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) VaultOnPrimary else VaultTextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: STORAGE & CACHE CONFIGURATION
            SettingsSectionHeader(title = strings.sectionStorage, icon = Icons.Default.FolderSpecial)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Working Directory
                    Column {
                        Text(
                            text = strings.workingDirLabel,
                            fontSize = 12.sp,
                            color = VaultTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VaultBackground,
                            border = BorderStroke(1.dp, VaultBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = VaultAccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = workingDir,
                                    fontSize = 12.sp,
                                    color = VaultTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    tempFolderPath = workingDir
                                    showEditFolderDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.change, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetWorkingDirectory() },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, VaultBorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = VaultTextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.reset, fontSize = 12.sp, color = VaultTextPrimary)
                            }
                        }
                    }

                    Divider(color = VaultBorderSubtle)

                    // Video Folder
                    Column {
                        Text(
                            text = strings.videoDirLabel,
                            fontSize = 12.sp,
                            color = VaultTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VaultBackground,
                            border = BorderStroke(1.dp, VaultBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = VaultAccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = videoDirectory,
                                    fontSize = 12.sp,
                                    color = VaultTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showFolderPickerDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.btnFolder, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.rescanVideoFolder() },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, VaultBorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = VaultTextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.reset, fontSize = 12.sp, color = VaultTextPrimary)
                            }
                        }
                    }

                    Divider(color = VaultBorderSubtle)

                    // THUMBNAIL CACHE CLEANER (NEW)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.clearCache,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = strings.cacheSizeLabel(formattedCacheSize),
                                fontSize = 11.sp,
                                color = VaultAccentCyan
                            )
                        }
                        Button(
                            onClick = { viewModel.clearThumbnailCache() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VaultSurface,
                                contentColor = VaultAccentCyan
                            ),
                            border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.clearCache, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = VaultBorderSubtle)

                    // BATCH PROCESSING QUEUE SIZE
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.sectionBatchQueue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = VaultTextPrimary
                                )
                                Text(
                                    text = strings.batchQueueDesc,
                                    fontSize = 11.sp,
                                    color = VaultTextMuted
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VaultPrimary.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "$queueSize",
                                    color = VaultPrimaryLight,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = queueSize.toFloat(),
                            onValueChange = { viewModel.setBatchQueueSize(it.toInt()) },
                            valueRange = 1f..20f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = VaultPrimaryLight,
                                activeTrackColor = VaultPrimary,
                                inactiveTrackColor = VaultSurfaceHighlight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Selection Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 5, 10, 15, 20).forEach { sizeOption ->
                                val isSelected = queueSize == sizeOption
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) VaultPrimary else VaultBackground)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setBatchQueueSize(sizeOption) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$sizeOption",
                                        color = if (isSelected) VaultOnPrimary else VaultTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: PERMISSIONS (STREAMLINED & CLEAN)
            SettingsSectionHeader(title = strings.sectionPermissions, icon = Icons.Default.Security)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (hasAllFilesAccess) VaultAccentGreen.copy(alpha = 0.15f) else VaultErrorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasAllFilesAccess) VaultAccentGreen else VaultError,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.allFilesAccessLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = VaultTextPrimary
                        )
                        Text(
                            text = if (hasAllFilesAccess) strings.permissionGranted else strings.permissionRequired,
                            fontSize = 11.sp,
                            color = if (hasAllFilesAccess) VaultAccentGreen else VaultError
                        )
                    }
                    if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= 30) {
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(strings.btnGrantPermission, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SECTION 6: SECURITY & ABOUT VAULT (STREAMLINED)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceElevated,
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = VaultPrimaryLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.vaultSecurityNotice,
                            fontSize = 11.sp,
                            color = VaultTextSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    Divider(color = VaultBorderSubtle)

                    // Play Intro on Launch Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.playIntroOnLaunch,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = strings.playIntroOnLaunchDesc,
                                fontSize = 11.sp,
                                color = VaultTextSecondary
                            )
                        }
                        Switch(
                            checked = playIntroOnLaunch,
                            onCheckedChange = { viewModel.setPlayIntroOnLaunch(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VaultPrimaryLight,
                                checkedTrackColor = VaultPrimary,
                                uncheckedTrackColor = VaultSurface
                            )
                        )
                    }

                    Divider(color = VaultBorderSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = strings.appVersion,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = VaultTextPrimary
                            )
                            Text(
                                text = strings.appVersionDesc,
                                fontSize = 10.sp,
                                color = VaultTextMuted
                            )
                        }

                        if (onPlayIntro != null) {
                            OutlinedButton(
                                onClick = { onPlayIntro.invoke() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = VaultPrimaryLight)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.easterEggIntroBtn, fontSize = 10.sp, color = VaultPrimaryLight)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Edit Folder Path Dialog
    if (showEditFolderDialog) {
        AlertDialog(
            onDismissRequest = { showEditFolderDialog = false },
            title = {
                Text(
                    text = strings.workingDirLabel,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = strings.workingDirLabel,
                        fontSize = 12.sp,
                        color = VaultTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempFolderPath,
                        onValueChange = { tempFolderPath = it },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPrimary,
                            unfocusedBorderColor = VaultBorderSubtle,
                            focusedTextColor = VaultTextPrimary,
                            unfocusedTextColor = VaultTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempFolderPath.isNotBlank()) {
                            viewModel.setWorkingDirectory(tempFolderPath.trim())
                        }
                        showEditFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary)
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditFolderDialog = false }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurfaceElevated
        )
    }

    if (showFolderPickerDialog) {
        FolderPickerDialog(
            currentPath = videoDirectory,
            onDismiss = { showFolderPickerDialog = false },
            onFolderSelected = { path ->
                viewModel.setVideoDirectory(path)
            },
            onCreateTestNomediaFolder = {
                viewModel.createTestHiddenFolderWithDuplicates()
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VaultPrimaryLight,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = VaultTextSecondary
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    accentColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = VaultTextPrimary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = VaultTextMuted
        )
    }
}
