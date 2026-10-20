package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateDetectorView(
    viewModel: GalleryViewModel,
    duplicateGroups: List<DuplicateGroup>,
    isDetecting: Boolean,
    onOpenVideoInViewer: (VideoItem) -> Unit,
    onSelectFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val detectionProgress by viewModel.detectionProgress.collectAsStateWithLifecycle()
    val lastScanVerdict by viewModel.lastScanVerdict.collectAsStateWithLifecycle()
    val lastScanDurationSec by viewModel.lastScanDurationSec.collectAsStateWithLifecycle()
    val detectionMode by viewModel.detectionMode.collectAsStateWithLifecycle()
    val folderVideos by viewModel.folderVideos.collectAsStateWithLifecycle()
    val isGalleryBrowseMode by viewModel.isGalleryBrowseMode.collectAsStateWithLifecycle()

    val cpuCores = remember { Runtime.getRuntime().availableProcessors().coerceIn(2, 16) }

    // Live 100ms UI stopwatch ticker so the user ALWAYS sees the timer ticking smoothly while scanning
    var liveElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isDetecting) {
        if (isDetecting) {
            val start = System.currentTimeMillis()
            liveElapsedMs = 100L
            while (true) {
                delay(80L)
                liveElapsedMs = System.currentTimeMillis() - start
            }
        }
    }

    var singleRenameTarget by remember { mutableStateOf<File?>(null) }
    var singleRenameInput by remember { mutableStateOf("123") }

    var groupRenameTarget by remember { mutableStateOf<DuplicateGroup?>(null) }
    var groupRenameInput by remember { mutableStateOf("123") }

    var showBatchAllDialog by remember { mutableStateOf(false) }
    var batchAllInput by remember { mutableStateOf("123") }

    var groupToDeleteRedundant by remember { mutableStateOf<DuplicateGroup?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showCleanAllConfirm by remember { mutableStateOf(false) }

    val totalWastedBytes = duplicateGroups.sumOf { it.wastedBytes }
    val totalDuplicateFiles = duplicateGroups.sumOf { it.items.size }

    val activeProgress = detectionProgress ?: DetectionProgress(
        current = if (isDetecting) 0 else folderVideos.size,
        total = max(1, folderVideos.size),
        currentFileName = if (isDetecting) strings.loading else "Готово",
        stage = if (isDetecting) strings.scanningVideos else strings.verdictLabel,
        percentage = if (isDetecting) 0.12f else 1.0f,
        remainingSeconds = if (isDetecting) max(1, (folderVideos.size * 0.35f).toInt()) else 0,
        elapsedMs = liveElapsedMs,
        speedVideosPerSec = cpuCores.toFloat(),
        activeCores = cpuCores
    )

    val displayedRemainingSec = remember(activeProgress.remainingSeconds, liveElapsedMs, activeProgress.total, isDetecting) {
        if (!isDetecting) {
            0
        } else if (activeProgress.remainingSeconds > 0) {
            activeProgress.remainingSeconds
        } else {
            val estTotalSec = max(2, (activeProgress.total * 0.35f).toInt())
            max(1, estTotalSec - (liveElapsedMs / 1000L).toInt())
        }
    }

    val formattedLiveElapsed = String.format(
        Locale.US,
        "%.1f",
        max(liveElapsedMs, activeProgress.elapsedMs) / 1000.0
    )

    val displayElapsedSec = if (isDetecting) formattedLiveElapsed else lastScanDurationSec

    // Single unified LazyColumn so nothing ever gets crushed or clipped vertically on any screen size
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VaultBackground),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. TOP CONTROL & SCANNER CARD
        item(key = "scanner_header_card") {
            Surface(
                color = VaultSurface,
                border = BorderStroke(1.dp, if (isDetecting) VaultAccentAmber else VaultBorder),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Row 1: Icon + Title & Subtitle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = VaultAccentCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.35f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Compare,
                                    contentDescription = null,
                                    tint = VaultAccentCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.duplicateFinderTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = VaultTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = strings.duplicateFinderSubtitle,
                                fontSize = 12.sp,
                                color = VaultTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Row 2: Hardware Turbo Badge + Videos in Folder Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = VaultPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = VaultPrimaryLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Турбо: $cpuCores ядер CPU",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VaultPrimaryLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = VaultSurfaceElevated,
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(1.dp, VaultBorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isDetecting) {
                                    viewModel.toggleGalleryBrowseMode()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGalleryBrowseMode) Icons.Default.VideoLibrary else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = VaultAccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isGalleryBrowseMode) {
                                        "${strings.browseModeGallery} (${folderVideos.size})"
                                    } else {
                                        "${strings.browseModeFolder} (${folderVideos.size})"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VaultTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Row 3: Flexible Matching Algorithm Banner
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = VaultPrimaryLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "Гнучкий алгоритм пошуку дублікатів",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VaultPrimaryLight
                                )
                                Text(
                                    text = "Порівняння за назвою, розміром (±2%) та роздільною здатністю",
                                    fontSize = 10.sp,
                                    color = VaultTextSecondary
                                )
                            }
                        }
                    }

                    // Row 4: Full-Width Prominent Scan Button
                    Button(
                        onClick = { viewModel.runDuplicateDetection() },
                        enabled = !isDetecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultPrimary,
                            contentColor = VaultOnPrimary,
                            disabledContainerColor = VaultAccentAmber.copy(alpha = 0.22f),
                            disabledContentColor = VaultAccentAmber
                        ),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("rescan_duplicates_button")
                    ) {
                        if (isDetecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = VaultAccentAmber,
                                strokeWidth = 2.2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Сканування... ⏱ ${formattedLiveElapsed}с (залишилось ~${displayedRemainingSec}с)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = strings.btnScanDuplicates,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.btnScanDuplicates,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. PERMANENT TELEMETRY, COUNTDOWN & VERDICT CARD (ALWAYS VISIBLE SO USER ALWAYS SEES SCAN TIME)
        item(key = "telemetry_verdict_card") {
            Surface(
                color = VaultSurface,
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(
                    width = if (isDetecting) 1.5.dp else 1.dp,
                    color = if (isDetecting) VaultAccentAmber else VaultAccentCyan.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("duplicate_scan_progress_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Two prominent side-by-side timer boxes: Remaining Time vs Elapsed Check Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Box: Time Remaining for Verdict
                        Surface(
                            color = if (isDetecting) VaultAccentAmber.copy(alpha = 0.15f) else VaultSurfaceElevated,
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isDetecting) VaultAccentAmber.copy(alpha = 0.6f) else VaultBorderSubtle
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassBottom,
                                        contentDescription = null,
                                        tint = if (isDetecting) VaultAccentAmber else VaultAccentCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "ДО ВЕРДИКТУ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = when {
                                        isDetecting -> "~$displayedRemainingSec с"
                                        lastScanVerdict == null -> "Очікування"
                                        else -> "0 с (Готово)"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDetecting) VaultAccentAmber else VaultAccentGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right Box: Elapsed Scan Duration ("ЧАС ПЕРЕВІРКИ")
                        Surface(
                            color = VaultAccentCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.45f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = VaultAccentCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "ЧАС ПЕРЕВІРКИ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (!isDetecting && lastScanVerdict == null) "— (Старт вимк.)" else "${displayElapsedSec} с",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = VaultAccentCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Progress Bar
                    val progressFraction = when {
                        isDetecting -> activeProgress.percentage.coerceIn(0.08f, 1f)
                        lastScanVerdict == null -> 0.0f
                        else -> 1.0f
                    }
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(0.dp)),
                        color = if (isDetecting) VaultAccentAmber else VaultAccentCyan,
                        trackColor = VaultSurfaceElevated
                    )

                    // Verdict / Live Stage Row
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
                                imageVector = if (isDetecting) Icons.Default.Radar else Icons.Default.Gavel,
                                contentDescription = null,
                                tint = if (isDetecting) VaultAccentAmber else VaultPrimaryLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDetecting) {
                                    "${activeProgress.stage} (${activeProgress.current}/${activeProgress.total})"
                                } else {
                                    "${strings.verdictLabel}: ${lastScanVerdict ?: strings.noDuplicatesDesc}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VaultTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDetecting) VaultAccentAmber else VaultAccentCyan
                        )
                    }
                }
            }
        }

        // 3. SUMMARY METRICS & GLOBAL CLEANUP ACTIONS (WHEN DUPLICATES EXIST)
        if (duplicateGroups.isNotEmpty()) {
            item(key = "duplicate_summary_card") {
                Surface(
                    color = VaultSurface,
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(1.dp, VaultBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = VaultErrorContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(0.dp),
                                border = BorderStroke(1.dp, VaultError.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = strings.wastedSpace,
                                        fontSize = 11.sp,
                                        color = VaultError,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatBytes(totalWastedBytes),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = VaultError,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                color = VaultPrimaryDark.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(0.dp),
                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = strings.duplicatesFound,
                                        fontSize = 11.sp,
                                        color = VaultPrimaryLight,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = strings.duplicatesCopiesAndGroups(totalDuplicateFiles, duplicateGroups.size),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showCleanAllConfirm = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VaultError,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(0.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clean_all_duplicates_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.btnKeepBestQuality,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    batchAllInput = "123"
                                    showBatchAllDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultPrimaryLight),
                                border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(0.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("batch_rename_all_duplicates_button")
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.btnBatchRename,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. DUPLICATE GROUPS OR EMPTY STATE CARD
        if (duplicateGroups.isEmpty()) {
            item(key = "empty_or_scanning_state") {
                Surface(
                    color = VaultSurface,
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(1.dp, VaultBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Surface(
                            color = VaultSurfaceElevated,
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(1.dp, VaultBorder),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDetecting) Icons.Default.Radar else Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = if (isDetecting) VaultAccentAmber else VaultAccentCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isDetecting) strings.btnScanningDuplicates else strings.noDuplicatesTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (isDetecting) {
                                "Перевіряємо відео в усіх підпапках на точні копії, змінений бітрейт або обрізані фрагменти..."
                            } else {
                                strings.noDuplicatesDesc
                            },
                            fontSize = 12.sp,
                            color = VaultTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSelectFolderClick,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultPrimaryLight),
                                border = BorderStroke(1.dp, VaultBorder),
                                shape = RoundedCornerShape(0.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.btnFolder,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { viewModel.createTestHiddenFolderWithDuplicates() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VaultAccentCyan.copy(alpha = 0.18f),
                                    contentColor = VaultAccentCyan
                                ),
                                shape = RoundedCornerShape(0.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.btnCreateNomedia,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(duplicateGroups, key = { _, group -> group.id }) { index, group ->
                DuplicateGroupCard(
                    groupIndex = index + 1,
                    group = group,
                    onOpenVideo = onOpenVideoInViewer,
                    onRenameSingle = { file ->
                        singleRenameTarget = file
                        singleRenameInput = "123"
                    },
                    onRenameGroup = {
                        groupRenameTarget = group
                        groupRenameInput = "123"
                    },
                    onDeleteSingle = { file ->
                        fileToDelete = file
                    },
                    onKeepOnlyOne = {
                        groupToDeleteRedundant = group
                    }
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // SINGLE RENAME DIALOG
    singleRenameTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { singleRenameTarget = null },
            title = {
                Text(
                    text = strings.renameVideo,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.currentNameLabel(file.name),
                        fontSize = 12.sp,
                        color = VaultTextSecondary
                    )
                    OutlinedTextField(
                        value = singleRenameInput,
                        onValueChange = { singleRenameInput = it },
                        label = { Text(strings.newName) },
                        placeholder = { Text("123") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPrimary,
                            unfocusedBorderColor = VaultBorder,
                            focusedTextColor = VaultTextPrimary,
                            unfocusedTextColor = VaultTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("123", "1", "2", "3").forEach { suggestion ->
                            FilterChip(
                                selected = singleRenameInput == suggestion,
                                onClick = { singleRenameInput = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = VaultSurface,
                                    labelColor = VaultTextSecondary,
                                    selectedContainerColor = VaultPrimary.copy(alpha = 0.3f),
                                    selectedLabelColor = VaultTextPrimary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = singleRenameTarget
                        val name = singleRenameInput
                        singleRenameTarget = null
                        if (target != null && name.isNotBlank()) {
                            viewModel.renameVideo(target, name)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary)
                ) {
                    Text(strings.btnRename, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { singleRenameTarget = null }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(0.dp)
        )
    }

    // GROUP RENAME DIALOG
    groupRenameTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { groupRenameTarget = null },
            title = {
                Text(
                    text = strings.renameGroupTitle,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.renameGroupDesc(group.items.size),
                        fontSize = 12.sp,
                        color = VaultTextSecondary
                    )
                    OutlinedTextField(
                        value = groupRenameInput,
                        onValueChange = { groupRenameInput = it },
                        label = { Text(strings.prefixPatternLabel) },
                        placeholder = { Text("123") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPrimary,
                            unfocusedBorderColor = VaultBorder,
                            focusedTextColor = VaultTextPrimary,
                            unfocusedTextColor = VaultTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("123", "1", "video_123").forEach { suggestion ->
                            FilterChip(
                                selected = groupRenameInput == suggestion,
                                onClick = { groupRenameInput = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = VaultSurface,
                                    labelColor = VaultTextSecondary,
                                    selectedContainerColor = VaultPrimary.copy(alpha = 0.3f),
                                    selectedLabelColor = VaultTextPrimary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = groupRenameTarget
                        val pattern = groupRenameInput
                        groupRenameTarget = null
                        if (target != null) {
                            viewModel.batchRenameGroup(target, pattern)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary)
                ) {
                    Text(strings.apply, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupRenameTarget = null }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(0.dp)
        )
    }

    // BATCH ALL DUPLICATES DIALOG
    if (showBatchAllDialog) {
        AlertDialog(
            onDismissRequest = { showBatchAllDialog = false },
            title = {
                Text(
                    text = strings.renameAllDuplicatesTitle,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.renameAllDuplicatesDesc(totalDuplicateFiles, duplicateGroups.size),
                        fontSize = 12.sp,
                        color = VaultTextSecondary
                    )
                    OutlinedTextField(
                        value = batchAllInput,
                        onValueChange = { batchAllInput = it },
                        label = { Text(strings.newNamePrefixLabel) },
                        placeholder = { Text("123") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPrimary,
                            unfocusedBorderColor = VaultBorder,
                            focusedTextColor = VaultTextPrimary,
                            unfocusedTextColor = VaultTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pattern = batchAllInput
                        showBatchAllDialog = false
                        viewModel.batchRenameAllDuplicates(pattern)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultPrimary)
                ) {
                    Text(strings.renameAll, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchAllDialog = false }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(0.dp)
        )
    }

    // CONFIRM DELETE SINGLE
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(strings.deleteFileTitle, fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
            text = {
                Text(
                    text = strings.deleteFileConfirmDesc(file.name),
                    color = VaultTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val f = fileToDelete
                        fileToDelete = null
                        if (f != null) viewModel.deleteVideoFile(f)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultError)
                ) {
                    Text(strings.delete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(0.dp)
        )
    }

    // CONFIRM KEEP ONLY ONE (PER GROUP)
    groupToDeleteRedundant?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDeleteRedundant = null },
            title = { Text(strings.deleteRedundantTitle, fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
            text = {
                Text(
                    text = strings.deleteRedundantConfirmDesc(group.items.first().name, group.items.size - 1, group.formattedWastedSize),
                    color = VaultTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val g = groupToDeleteRedundant
                        groupToDeleteRedundant = null
                        if (g != null) viewModel.keepOnlyOneDuplicate(g)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultError)
                ) {
                    Text(strings.keepOneAndDeleteOthers, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDeleteRedundant = null }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(0.dp)
        )
    }

    // CONFIRM CLEAN ALL DUPLICATES (GLOBAL)
    if (showCleanAllConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanAllConfirm = false },
            title = { Text(strings.btnKeepBestQuality, fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
            text = {
                Text(
                    text = "Буде автоматично збережено найкращу якість для кожної з ${duplicateGroups.size} груп дублікатів, а всі гірші копії (${totalDuplicateFiles - duplicateGroups.size} файлів) видалено. Звільниться ${formatBytes(totalWastedBytes)}.",
                    color = VaultTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanAllConfirm = false
                        viewModel.cleanAllDuplicateGroups()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultError)
                ) {
                    Text(strings.delete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanAllConfirm = false }) {
                    Text(strings.cancel, color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(0.dp)
        )
    }
}

@Composable
fun DuplicateGroupCard(
    groupIndex: Int,
    group: DuplicateGroup,
    onOpenVideo: (VideoItem) -> Unit,
    onRenameSingle: (File) -> Unit,
    onRenameGroup: () -> Unit,
    onDeleteSingle: (File) -> Unit,
    onKeepOnlyOne: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    val matchBadgeColor = when (group.matchType) {
        DuplicateMatchType.EXACT_CONTENT -> VaultAccentCyan
        DuplicateMatchType.FLEXIBLE_NAME_SIZE -> VaultPrimaryLight
        DuplicateMatchType.SIZE_AND_DURATION -> VaultAccentGreen
        DuplicateMatchType.RESOLUTION_NAME -> VaultAccentAmber
        DuplicateMatchType.VISUAL_CLONE -> VaultPrimaryLight
        DuplicateMatchType.MODIFIED_VIDEO -> VaultAccentAmber
        DuplicateMatchType.FRAME_SIMILARITY -> VaultPrimary
    }

    val matchBadgeText = when (group.matchType) {
        DuplicateMatchType.EXACT_CONTENT -> strings.matchTypeExact
        DuplicateMatchType.FLEXIBLE_NAME_SIZE -> "Назва + Розмір"
        DuplicateMatchType.SIZE_AND_DURATION -> "Розмір + Хронометраж"
        DuplicateMatchType.RESOLUTION_NAME -> "Роздільність + Назва"
        DuplicateMatchType.VISUAL_CLONE -> "${strings.matchTypeVisual} • ${group.similarityScore}%"
        DuplicateMatchType.MODIFIED_VIDEO -> "${strings.matchTypeModified} • ${group.similarityScore}%"
        DuplicateMatchType.FRAME_SIMILARITY -> "Збіг кадрів • ${group.similarityScore}%"
    }

    Surface(
        color = VaultSurface,
        border = BorderStroke(1.dp, VaultBorder),
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Group Index + Video Count + Wasted Size Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = VaultPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text(
                            text = "#$groupIndex",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultPrimaryLight,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = strings.identicalVideosCount(group.items.size),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (group.wastedBytes > 0) {
                    Surface(
                        color = VaultError.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(0.dp),
                        border = BorderStroke(1.dp, VaultError.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "-${group.formattedWastedSize}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = VaultError,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Row 2: Match Type Badge + Match Description on its own clean row so it never wraps vertically
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = matchBadgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(0.dp),
                    border = BorderStroke(0.8.dp, matchBadgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = matchBadgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = matchBadgeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = group.matchDescription,
                    fontSize = 11.sp,
                    color = VaultTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Group Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onKeepOnlyOne,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultError),
                    border = BorderStroke(1.dp, VaultError.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(0.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.keepOnlyOne,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRenameGroup,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultPrimaryLight),
                    border = BorderStroke(1.dp, VaultPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(0.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.renameAs123,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = VaultBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

            // Row 4: Duplicate Video Items
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.items.forEachIndexed { itemIdx, item ->
                    val isMaster = itemIdx == 0
                    DuplicateItemRow(
                        index = itemIdx + 1,
                        item = item,
                        isMaster = isMaster,
                        onOpen = { onOpenVideo(item) },
                        onRename = { onRenameSingle(item.file) },
                        onDelete = { onDeleteSingle(item.file) }
                    )
                }
            }
        }
    }
}

@Composable
fun DuplicateItemRow(
    index: Int,
    item: VideoItem,
    isMaster: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Surface(
        color = if (isMaster) VaultSurfaceElevated else VaultBackground,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, if (isMaster) VaultPrimary.copy(alpha = 0.4f) else VaultBorder.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Thumbnail preview with cached cover & play icon
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 50.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .clickable { onOpen() },
                contentAlignment = Alignment.Center
            ) {
                VideoCoverThumbnail(
                    videoPath = item.path,
                    fileSize = item.size,
                    lastModified = item.lastModified,
                    contentDescription = item.name,
                    showPlayOverlay = true,
                    playIconSize = 14.dp,
                    cornerRadius = 0.dp,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    color = if (isMaster) VaultPrimary.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (isMaster) "★" else "#$index",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // File Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMaster) VaultTextPrimary else VaultTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val specsText = buildList {
                    add(item.formattedSize)
                    if (item.resolution.isNotEmpty()) add(item.resolution)
                    if (item.formattedDuration.isNotEmpty()) add(item.formattedDuration)
                }.joinToString(" • ")

                Text(
                    text = specsText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VaultAccentCyan,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                val parentDirName = item.file.parentFile?.name ?: ""
                val isHiddenFolder = parentDirName.startsWith(".")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    if (isMaster) {
                        Surface(
                            color = VaultPrimary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Text(
                                text = "Оригінал",
                                fontSize = 9.sp,
                                color = VaultPrimaryLight,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isHiddenFolder) strings.hiddenFolderTag(parentDirName) else "📁 $parentDirName",
                        fontSize = 9.sp,
                        color = if (isHiddenFolder) VaultAccentAmber else VaultTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Actions
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = strings.btnRename,
                        tint = VaultPrimaryLight,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = strings.delete,
                        tint = VaultError,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
