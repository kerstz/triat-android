package com.ivor.ivormusic.ui.downloads
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.ivor.ivormusic.data.DownloadMediaType
import com.ivor.ivormusic.data.DownloadProgress
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.DownloadStatus
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private data class PlaylistDownloadSnapshot(
    val eligibleIds: Set<String>,
    val completedIds: Set<String>,
    val active: Map<String, DownloadProgress>,
    val failedIds: Set<String>,
    val localOfflineCount: Int,
    val skippedCount: Int,
    val repeatedEntryCount: Int,
    val allPagesLoaded: Boolean = true
) {
    val completedCount: Int get() = completedIds.size + localOfflineCount
    val activeCount: Int get() = active.size
    val remainingIds: Set<String>
        get() = eligibleIds - completedIds - active.keys
    val remainingCount: Int get() = remainingIds.size
    val isComplete: Boolean
        get() = allPagesLoaded && (eligibleIds.isNotEmpty() || localOfflineCount > 0) &&
            completedIds.containsAll(eligibleIds) && skippedCount == 0

    val overallProgress: Float
        get() {
            if (eligibleIds.isEmpty()) return if (localOfflineCount > 0) 1f else 0f
            val total = eligibleIds.sumOf { id ->
                when {
                    id in completedIds -> 1.0
                    else -> active[id]?.progress?.toDouble() ?: 0.0
                }
            }
            return (total / eligibleIds.size).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Download entry point for a music playlist or album.
 *
 * Device-local songs are already offline, while repeated YouTube ids share one
 * downloaded file. The action still names the playlist's full row count; file
 * deduplication is explained inside the sheet instead of leaking a smaller,
 * apparently incorrect number into the playlist header.
 */
@Composable
fun MusicPlaylistDownloadAction(
    playlistId: String,
    playlistTitle: String,
    artworkUrl: String? = null,
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { DownloadRepository.getInstance(context) }
    val repositoryScope = rememberCoroutineScope()
    val downloadedSongs by repository.downloadedSongs.collectAsStateWithLifecycle()
    val progress by repository.downloadProgress.collectAsStateWithLifecycle()
    var showSheet by remember(playlistTitle) { mutableStateOf(false) }

    val eligibleSongs = remember(songs) {
        songs.filter { it.source == SongSource.YOUTUBE && it.id.isNotBlank() }
            .distinctBy { it.id }
    }
    val eligibleIds = remember(eligibleSongs) { eligibleSongs.mapTo(linkedSetOf()) { it.id } }
    val downloadedIds = remember(downloadedSongs, eligibleIds) {
        downloadedSongs.asSequence().map { it.id }.filter { it in eligibleIds }.toSet()
    }
    val relevantProgress = remember(progress, eligibleIds) {
        progress.values.filter {
            it.request.type == DownloadMediaType.MUSIC && it.request.id in eligibleIds
        }.associateBy { it.request.id }
    }
    val downloadableEntryCount = remember(songs) {
        songs.count { it.source == SongSource.YOUTUBE && it.id.isNotBlank() }
    }
    val snapshot = remember(eligibleIds, downloadedIds, relevantProgress, songs) {
        PlaylistDownloadSnapshot(
            eligibleIds = eligibleIds,
            completedIds = downloadedIds,
            active = relevantProgress.filterValues {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            },
            failedIds = relevantProgress.filterValues { it.status == DownloadStatus.FAILED }.keys,
            localOfflineCount = songs.count { it.source == SongSource.LOCAL },
            skippedCount = songs.count { it.source == SongSource.YOUTUBE && it.id.isBlank() },
            repeatedEntryCount = (downloadableEntryCount - eligibleSongs.size).coerceAtLeast(0)
        )
    }

    PlaylistDownloadButton(
        snapshot = snapshot,
        totalItemCount = songs.size,
        enabled = songs.isNotEmpty(),
        onClick = {
            if (snapshot.isComplete) {
                repositoryScope.launch {
                    val saved = repository.playlistStore.playlists.value.any { it.id == playlistId } ||
                        repository.rememberDownloadedPlaylist(playlistId, playlistTitle, artworkUrl, songs)
                    android.widget.Toast.makeText(
                        context,
                        if (saved) R.string.vd_added else R.string.sm_write_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else showSheet = true
        },
        modifier = modifier
    )

    if (showSheet) {
        PlaylistDownloadSheet(
            playlistTitle = playlistTitle,
            playlistItemCountLabel = trackCountLabel(songs.size),
            snapshot = snapshot,
            onDismiss = { showSheet = false },
            onQueue = {
                if (repository.rememberDownloadedPlaylist(playlistId, playlistTitle, artworkUrl, songs)) {
                    repository.downloadPlaylist(eligibleSongs)
                    true
                } else false
            }
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistDownloadButton(
    snapshot: PlaylistDownloadSnapshot,
    totalItemCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canOpen = enabled &&
        (!snapshot.allPagesLoaded || snapshot.eligibleIds.isNotEmpty() || snapshot.localOfflineCount > 0)
    val label = when {
        snapshot.isComplete -> stringResource(R.string.vh_available_offline)
        snapshot.activeCount > 0 -> stringResource(R.string.pd_downloading_n, snapshot.activeCount)
        !snapshot.allPagesLoaded -> stringResource(R.string.pd_download_playlist)
        snapshot.remainingCount > 0 && snapshot.remainingIds.all { it in snapshot.failedIds } ->
            stringResource(R.string.pd_retry_n, snapshot.remainingCount)
        snapshot.remainingCount > 0 -> stringResource(R.string.pd_download_n, totalItemCount)
        snapshot.skippedCount > 0 -> stringResource(R.string.pd_unavailable_offline)
        else -> stringResource(R.string.pd_download_tracks)
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = canOpen,
        modifier = modifier,
        shapes = ButtonDefaults.shapes()
    ) {
        val downloadingCd = stringResource(R.string.pd_downloading_playlist)
        AnimatedContent(
            targetState = when {
                snapshot.isComplete -> "complete"
                snapshot.activeCount > 0 -> "active"
                snapshot.eligibleIds.isEmpty() && snapshot.allPagesLoaded -> "unavailable"
                else -> "ready"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "playlistDownloadState"
        ) { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    "complete" -> Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    "active" -> CircularWavyProgressIndicator(
                        progress = { snapshot.overallProgress },
                        modifier = Modifier
                            .size(20.dp)
                            .semantics {
                                contentDescription = downloadingCd
                                progressBarRangeInfo = ProgressBarRangeInfo(
                                    snapshot.overallProgress,
                                    0f..1f
                                )
                            }
                    )
                    "unavailable" -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                    else -> Icon(Icons.Rounded.Download, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
private fun PlaylistDownloadSheet(
    playlistTitle: String,
    playlistItemCountLabel: String,
    snapshot: PlaylistDownloadSnapshot,
    onDismiss: () -> Unit,
    onQueue: suspend () -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val preferences = remember(context) { ThemePreferences(context) }
    // This is a compact task with its own scrolling body and pinned action. Opening
    // halfway hides the quality and summary context while making the first scroll
    // gesture resize the sheet, so enter directly at the expanded anchor.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var queued by remember { mutableStateOf(false) }
    var queueing by remember { mutableStateOf(false) }
    var queueFailed by remember { mutableStateOf(false) }
    val localOnly = remember { ThemePreferences.isLocalOnly(context) }

    LaunchedEffect(queued) {
        if (queued) {
            delay(850)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.pd_download_playlist),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = playlistTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DownloadSummaryRow(stringResource(R.string.pd_in_playlist), playlistItemCountLabel)
                            DownloadSummaryRow(
                                stringResource(R.string.pd_already_offline),
                                trackCountLabel(snapshot.completedCount)
                            )
                            if (snapshot.repeatedEntryCount > 0) {
                                DownloadSummaryRow(
                                    stringResource(R.string.pd_repeated_entries),
                                    trackCountLabel(snapshot.repeatedEntryCount)
                                )
                            }
                            DownloadSummaryRow(
                                stringResource(R.string.pd_to_download),
                                trackCountLabel(snapshot.remainingCount)
                            )
                            if (snapshot.repeatedEntryCount > 0) {
                                Text(
                                    text = stringResource(R.string.pd_repeated_entries_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (snapshot.activeCount > 0) {
                                DownloadSummaryRow(
                                    stringResource(R.string.in_progress),
                                    trackCountLabel(snapshot.activeCount)
                                )
                                val progressCd = stringResource(R.string.pd_progress_cd)
                                LinearWavyProgressIndicator(
                                    progress = { snapshot.overallProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = progressCd
                                            progressBarRangeInfo = ProgressBarRangeInfo(
                                                snapshot.overallProgress,
                                                0f..1f
                                            )
                                        }
                                )
                            }
                        }
                    }
                }

                item {
                    val musicQuality = remember {
                        ThemePreferences.currentMusicQuality(context)
                    }
                    Text(
                        text = stringResource(
                            R.string.dl_music_quality_note,
                            stringResource(
                                when (musicQuality) {
                                    ThemePreferences.MUSIC_QUALITY_NORMAL -> R.string.sp_music_quality_normal
                                    ThemePreferences.MUSIC_QUALITY_LOW -> R.string.dl_music_quality_low
                                    else -> R.string.sp_music_quality_high
                                }
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (snapshot.skippedCount > 0 || localOnly) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (localOnly) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (localOnly) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (localOnly) {
                                        stringResource(R.string.pd_local_only_block)
                                    } else {
                                        pluralStringResource(
                                            R.plurals.pd_skipped_no_source, snapshot.skippedCount,
                                            trackCountLabel(snapshot.skippedCount)
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (localOnly) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (queueFailed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.pd_load_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.pd_background_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        queueing = true
                        queueFailed = false
                        scope.launch {
                            val success = onQueue()
                            queueing = false
                            if (success) {
                                queued = true
                            } else {
                                queueFailed = true
                            }
                        }
                    },
                    enabled = (snapshot.remainingCount > 0 || !snapshot.allPagesLoaded) && !queued && !queueing && !localOnly,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    val label = when {
                        queued -> stringResource(R.string.vd_added)
                        queueing -> stringResource(R.string.pd_adding)
                        localOnly -> stringResource(R.string.local_only_title)
                        snapshot.isComplete -> stringResource(R.string.pd_already_complete)
                        !snapshot.allPagesLoaded -> stringResource(R.string.pd_download_full)
                        snapshot.remainingCount == 0 && snapshot.activeCount > 0 -> stringResource(R.string.dl_preparing)
                        snapshot.remainingIds.all { it in snapshot.failedIds } ->
                            stringResource(R.string.pd_retry_items, trackCountLabel(snapshot.remainingCount))
                        else -> stringResource(R.string.pd_download_full)
                    }
                    if (queueing) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(
                            imageVector = if (queued || snapshot.isComplete) Icons.Rounded.CheckCircle
                                else Icons.Rounded.Download,
                            contentDescription = null
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DownloadSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun trackCountLabel(count: Int): String =
    pluralStringResource(R.plurals.n_tracks, count, count)

