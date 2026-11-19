package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.FolderBackupManager
import com.example.data.HiddenMedia
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.*
import java.io.File

@Composable
internal fun VaultHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = VaultSurfaceElevated,
    borderColor: Color = VaultBorderSubtle,
    tint: Color = VaultTextSecondary
) {
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun GalleryTopHeader(
    strings: AppStrings,
    isMultiSelect: Boolean,
    selectedCount: Int,
    isAllSelected: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    activeCount: Int,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCycleColumns: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            isMultiSelect -> {
                VaultHeaderIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = strings.deselectAll,
                    onClick = onClearSelection,
                    tint = VaultTextPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.selectedCount(selectedCount),
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
                TextButton(onClick = onToggleSelectAll) {
                    Text(
                        text = if (isAllSelected) strings.deselectAll else strings.selectAll,
                        color = VaultPrimaryLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            isSearchActive -> {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = VaultSurfaceElevated,
                    border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSearchClose,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.back,
                                tint = VaultTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(strings.searchPlaceholder, fontSize = 12.sp, color = VaultTextMuted)
                            },
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
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.clearSearch,
                                    tint = VaultTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = VaultPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier.size(40.dp)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = VaultTextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Surface(
                                shape = RoundedCornerShape(0.dp),
                                color = VaultSurfaceElevated,
                                border = BorderStroke(1.dp, VaultBorderSubtle)
                            ) {
                                Text(
                                    text = "$activeCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = VaultAccentCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultHeaderIconButton(
                        icon = Icons.Default.Search,
                        contentDescription = strings.search,
                        onClick = onSearchOpen,
                        modifier = Modifier.testTag("search_btn")
                    )
                    VaultHeaderIconButton(
                        icon = Icons.Default.GridView,
                        contentDescription = strings.gridColumns,
                        onClick = onCycleColumns,
                        modifier = Modifier.testTag("columns_btn")
                    )
                    VaultHeaderIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = strings.btnSettings,
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_btn")
                    )
                }
            }
        }
    }
}

@Composable
internal fun GalleryModeTabs(
    strings: AppStrings,
    activeTab: Int,
    videosCount: Int,
    photosCount: Int,
    duplicatesCount: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(VaultSurfaceElevated)
            .border(1.dp, VaultBorderSubtle, RoundedCornerShape(0.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GalleryTabItem(
            title = strings.tabVideos,
            icon = Icons.Default.PlayCircle,
            badgeCount = videosCount,
            showBadgeAlways = true,
            selected = activeTab == 0,
            inactiveIconTint = VaultTextSecondary,
            inactiveBadgeBg = VaultSurfaceHighlight,
            inactiveBadgeText = VaultTextMuted,
            onClick = { onSelectTab(0) },
            modifier = Modifier
                .weight(1f)
                .testTag("tab_videos")
        )
        GalleryTabItem(
            title = strings.tabPhotos,
            icon = Icons.Default.Photo,
            badgeCount = photosCount,
            showBadgeAlways = true,
            selected = activeTab == 1,
            inactiveIconTint = VaultTextSecondary,
            inactiveBadgeBg = VaultSurfaceHighlight,
            inactiveBadgeText = VaultTextMuted,
            onClick = { onSelectTab(1) },
            modifier = Modifier
                .weight(1f)
                .testTag("tab_photos")
        )
        GalleryTabItem(
            title = strings.tabDuplicates,
            icon = Icons.Default.Compare,
            badgeCount = duplicatesCount,
            showBadgeAlways = false,
            selected = activeTab == 2,
            inactiveIconTint = VaultAccentAmber,
            inactiveBadgeBg = VaultAccentAmber.copy(alpha = 0.2f),
            inactiveBadgeText = VaultAccentAmber,
            onClick = { onSelectTab(2) },
            modifier = Modifier
                .weight(1.1f)
                .testTag("tab_detect")
        )
    }
}

@Composable
private fun GalleryTabItem(
    title: String,
    icon: ImageVector,
    badgeCount: Int,
    showBadgeAlways: Boolean,
    selected: Boolean,
    inactiveIconTint: Color,
    inactiveBadgeBg: Color,
    inactiveBadgeText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (selected) VaultPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) VaultOnPrimary else inactiveIconTint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) VaultOnPrimary else VaultTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showBadgeAlways || badgeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = if (selected) VaultPrimaryDark.copy(alpha = 0.5f) else inactiveBadgeBg
                ) {
                    Text(
                        text = "$badgeCount",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) VaultOnPrimary else inactiveBadgeText,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun AllFilesAccessBanner(
    strings: AppStrings,
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = VaultErrorContainer,
        border = BorderStroke(1.dp, VaultError.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(0.dp),
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
            Column(modifier = Modifier.weight(1f)) {
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
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultError,
                    contentColor = VaultOnError
                ),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = strings.btnGrantPermission,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
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

/**
 * Streamlined single-surface toolbar for selecting video source (Device Gallery vs Folder)
 * plus quick Refresh and AES Archive Backup/Restore controls.
 */
@Composable
internal fun VideoSourceToolbar(
    strings: AppStrings,
    isGalleryBrowseMode: Boolean,
    videoDirectory: String,
    isBusy: Boolean,
    onSetBrowseMode: (Boolean) -> Unit,
    onOpenFolderPicker: () -> Unit,
    onRefresh: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Surface(
        color = VaultSurface,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, VaultBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Segmented Source Switcher: Gallery vs Folder
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(VaultBackground)
                        .border(1.dp, VaultBorderSubtle, RoundedCornerShape(0.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ModeTogglePill(
                        label = strings.browseModeGallery,
                        icon = Icons.Default.VideoLibrary,
                        selected = isGalleryBrowseMode,
                        accentColor = VaultPrimaryLight,
                        bgColor = VaultPrimary.copy(alpha = 0.22f),
                        borderColor = VaultPrimary,
                        onClick = { onSetBrowseMode(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_gallery_btn")
                    )
                    ModeTogglePill(
                        label = strings.browseModeFolder,
                        icon = Icons.Default.Folder,
                        selected = !isGalleryBrowseMode,
                        accentColor = VaultAccentCyan,
                        bgColor = VaultAccentCyan.copy(alpha = 0.18f),
                        borderColor = VaultAccentCyan,
                        onClick = { onSetBrowseMode(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_folder_btn")
                    )
                }

                // Refresh button
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = VaultSurfaceElevated,
                    border = BorderStroke(1.dp, VaultBorderSubtle),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onRefresh)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBusy) {
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
                }

                // Backup button
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = VaultPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onBackup)
                        .testTag("save_to_drive_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = strings.save,
                            tint = VaultPrimaryLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Restore button
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = VaultAccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onRestore)
                        .testTag("load_from_drive_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = strings.btnLoadBackup,
                            tint = VaultAccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Show current folder bar only when Folder mode is active
            if (!isGalleryBrowseMode) {
                HorizontalDivider(color = VaultBorderSubtle, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenFolderPicker)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = VaultAccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = File(videoDirectory).name.ifEmpty { videoDirectory },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = strings.change,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultAccentCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTogglePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    bgColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(if (selected) bgColor else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) borderColor else Color.Transparent,
                shape = RoundedCornerShape(0.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentColor else VaultTextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else VaultTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DuplicateBackgroundProgressBar(
    label: String,
    percentage: Float
) {
    val pct = percentage.coerceIn(0.05f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = VaultAccentAmber,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(pct * 100).toInt()}%",
                fontSize = 10.sp,
                color = VaultAccentAmber,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = VaultAccentAmber,
            trackColor = VaultSurfaceHighlight
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoGridCard(
    media: HiddenMedia,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    showMediaBadges: Boolean,
    selectContentDescription: String,
    badgeContentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(VaultSurfaceElevated)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                shape = RoundedCornerShape(0.dp)
            )
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            )
            .testTag("video_item_${media.id}")
    ) {
        VideoCoverThumbnail(
            videoPath = media.internalPath,
            fileSize = media.size,
            lastModified = media.dateHidden,
            contentDescription = media.originalFilename,
            cornerRadius = 0.dp,
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
                        contentDescription = badgeContentDescription,
                        tint = VaultPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

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
            SelectionCheckBadge(
                isSelected = isSelected,
                contentDescription = selectContentDescription,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoGridCard(
    media: HiddenMedia,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    selectContentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(VaultSurfaceElevated)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) VaultPrimaryLight else VaultBorderSubtle,
                shape = RoundedCornerShape(0.dp)
            )
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
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
            SelectionCheckBadge(
                isSelected = isSelected,
                contentDescription = selectContentDescription,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun SelectionCheckBadge(
    isSelected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (isSelected) VaultPrimary else VaultBackground.copy(alpha = 0.7f))
            .border(
                1.5.dp,
                if (isSelected) VaultPrimaryLight else Color.White.copy(alpha = 0.6f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = contentDescription,
                tint = VaultOnPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
internal fun VaultSearchEmptyState(
    message: String,
    clearLabel: String,
    onClearSearch: () -> Unit
) {
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
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = VaultTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onClearSearch,
            shape = RoundedCornerShape(0.dp)
        ) {
            Text(clearLabel)
        }
    }
}

@Composable
internal fun VaultEmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    primaryActionLabel: String? = null,
    primaryActionIcon: ImageVector? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = VaultPrimary.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.3f)),
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VaultPrimaryLight,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = VaultTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hint,
            fontSize = 13.sp,
            color = VaultTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onPrimaryAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultPrimary,
                    contentColor = VaultOnPrimary
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                if (primaryActionIcon != null) {
                    Icon(primaryActionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(primaryActionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun MultiSelectActionBar(
    strings: AppStrings,
    visible: Boolean,
    selectedCount: Int,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = VaultSurfaceElevated,
            border = BorderStroke(1.dp, VaultBorder),
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
                Text(
                    text = strings.selectedCount(selectedCount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = VaultTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultErrorContainer,
                        contentColor = VaultError
                    ),
                    shape = RoundedCornerShape(0.dp),
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

@Composable
internal fun BackupProgressDialog(
    strings: AppStrings,
    prog: FolderBackupManager.BackupProgress
) {
    val accent = if (prog.isPacking) VaultPrimary else VaultAccentCyan
    AlertDialog(
        onDismissRequest = {},
        containerColor = VaultSurfaceElevated,
        shape = RoundedCornerShape(0.dp),
        titleContentColor = VaultTextPrimary,
        textContentColor = VaultTextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
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
                        color = accent,
                        trackColor = VaultSurfaceHighlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        color = accent,
                        trackColor = VaultSurfaceHighlight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
                Text(
                    text = if (prog.isPacking) strings.backupEncryptNotice else strings.restoreDecryptNotice,
                    fontSize = 11.sp,
                    color = VaultTextMuted
                )
            }
        },
        confirmButton = {}
    )
}
