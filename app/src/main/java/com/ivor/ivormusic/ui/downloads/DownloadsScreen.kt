package com.ivor.ivormusic.ui.downloads
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.components.StateView
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.DownloadMediaType
import com.ivor.ivormusic.data.DownloadProgress
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.DownloadRequest
import com.ivor.ivormusic.data.DownloadStatus
import com.ivor.ivormusic.data.Song

/**
 * Outer radius of a connected group. Matches the track list on the album and
 * playlist page (`PlaylistDetailScreen`).
 *
 * Internal rather than private because [DownloadedPlaylistCard] is the head of
 * one of these groups and has to round its top by the same amount; a second
 * literal there is how the header and its tracks drift into two containers.
 */
internal val SEGMENT_CORNER = 28.dp

/**
 * Shape for one row of a connected list: only the group's outer corners are
 * rounded, so consecutive rows read as a single container rather than a stack
 * of separate cards. A lone item is rounded on all four.
 */
private fun segmentedShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(SEGMENT_CORNER)
    index == 0 -> RoundedCornerShape(topStart = SEGMENT_CORNER, topEnd = SEGMENT_CORNER)
    index == count - 1 -> RoundedCornerShape(
        bottomStart = SEGMENT_CORNER,
        bottomEnd = SEGMENT_CORNER
    )
    else -> RectangleShape
}

/**
 * Shape for a row inside a group whose top is already drawn by a header - an
 * expanded playlist's tracks. Only the last row rounds, because the header owns
 * the group's top corners and rounding the first row too would put a seam
 * across the middle of one container.
 */
private fun expandedTrackShape(index: Int, count: Int): Shape =
    if (index == count - 1) {
        RoundedCornerShape(bottomStart = SEGMENT_CORNER, bottomEnd = SEGMENT_CORNER)
    } else {
        RectangleShape
    }

/**
 * Hairline between connected rows, inset past the artwork so it aligns with the
 * text column rather than cutting the whole row in half.
 */
@Composable
private fun SegmentDivider(inset: Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    )
}

/**
 * Downloads library: finished songs plus the in-flight transfers, which the
 * user can pause, resume, retry or cancel one by one or all at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    activeDownloads: Map<String, DownloadProgress>,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song) -> Unit = { _, song -> onPlaySong(song) },
    onDeleteDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (DownloadRequest) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Split by the media type of the request itself rather than by which list
    // the finished item lands in, so queued and failed entries route correctly
    // before anything has completed.
    val musicProgress = activeDownloads.values
        .filter { it.request.type == DownloadMediaType.MUSIC }
        .sortedBy { it.status.ordinal }

    val activeCount = activeDownloads.values.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val songsSummary = pluralStringResource(R.plurals.n_songs, downloadedSongs.size, downloadedSongs.size)
                        val activeSummary = pluralStringResource(R.plurals.dl_n_active, activeCount, activeCount)
                        Text(stringResource(R.string.fab_downloads))
                        Text(
                            text = buildString {
                                append(songsSummary)
                                if (activeCount > 0) append(" • $activeSummary")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (activeCount > 0) {
                        TextButton(onClick = onCancelAll) { Text(stringResource(R.string.stop_all)) }
                    }
                }
            )
        }
    ) { paddingValues ->
        MusicTab(
            songs = downloadedSongs,
            progress = musicProgress,
            onPlayQueue = onPlayQueue,
            onDelete = onDeleteDownload,
            onCancel = onCancelDownload,
            onRetry = onRetryDownload,
            onPause = onPauseDownload,
            onResume = onResumeDownload,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun MusicTab(
    songs: List<Song>,
    progress: List<DownloadProgress>,
    onPlayQueue: (List<Song>, Song) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (DownloadRequest) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { DownloadRepository.getInstance(context) }
    val playlists by repository.playlistStore.playlists.collectAsStateWithLifecycle()
    var expandedPlaylist by rememberSaveable { mutableStateOf<String?>(null) }

    // Resolved once per change rather than inside the lazy scope: offlineSongs
    // indexes the whole downloads list, and doing that per playlist on every
    // recomposition of the list body is a pass over every download for every
    // card on screen.
    val offline = remember(playlists, songs) {
        playlists.map { playlist -> playlist to playlist.offlineSongs(songs) }
    }

    // No spacedBy: rows in a connected group sit flush and are separated by
    // dividers, not gaps. Section spacing is added explicitly instead.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (offline.isNotEmpty()) {
            item(key = "playlists_header") {
                SectionHeader(stringResource(R.string.section_playlists))
            }
            offline.forEach { (playlist, available) ->
                val isExpanded = expandedPlaylist == playlist.id
                item(key = "pl_${playlist.id}") {
                    DownloadedPlaylistCard(
                        playlist = playlist,
                        available = available,
                        expanded = isExpanded,
                        onClick = {
                            // An accordion: opening one closes the other, so a
                            // long playlist can never bury the rest of the tab.
                            expandedPlaylist = if (isExpanded) null else playlist.id
                        },
                        onPlay = {
                            available.firstOrNull()?.let { onPlayQueue(available, it) }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
                if (isExpanded) {
                    if (available.isEmpty()) {
                        item(key = "pl_${playlist.id}_empty") {
                            DownloadedPlaylistEmptyRow(modifier = Modifier.animateItem())
                        }
                    } else {
                        // The tracks stay separate lazy items rather than living
                        // inside the card behind an AnimatedVisibility: a
                        // downloaded playlist runs to hundreds of rows, and an
                        // expand animation that composes all of them at once is
                        // paid on every open. animateItem gives the same fade
                        // and the same settle of everything below, one row at a
                        // time, off the list itself.
                        itemsIndexed(
                            items = available,
                            // Occurrence-qualified: a playlist may legitimately
                            // list the same track twice, so the id alone would
                            // collide and crash the list.
                            key = { index, song -> "pl_${playlist.id}_${song.id}_$index" }
                        ) { index, song ->
                            Column(modifier = Modifier.animateItem()) {
                                SegmentDivider(inset = 74.dp)
                                DownloadedRow(
                                    title = song.title,
                                    subtitle = song.artist,
                                    artworkUrl = song.albumArtUri?.toString()
                                        ?: song.thumbnailUrl,
                                    fallbackIcon = Icons.Rounded.MusicNote,
                                    shape = expandedTrackShape(index, available.size),
                                    onPlay = { onPlayQueue(available, song) },
                                    onDelete = { onDelete(song.id) }
                                )
                            }
                        }
                    }
                }
                item(key = "pl_${playlist.id}_gap") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            item(key = "playlists_gap") { Spacer(modifier = Modifier.height(12.dp)) }
        }

        if (progress.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.in_progress)) }
            itemsIndexed(progress, key = { _, it -> "p_${it.songId}" }) { index, item ->
                ProgressCard(
                    item = item,
                    shape = segmentedShape(index, progress.size),
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onPause = onPause,
                    onResume = onResume
                )
                if (index < progress.lastIndex) SegmentDivider(inset = 46.dp)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (songs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.song_options_downloaded)) }
            itemsIndexed(songs, key = { _, it -> "s_${it.id}" }) { index, song ->
                DownloadedRow(
                    title = song.title,
                    subtitle = song.artist,
                    artworkUrl = song.thumbnailUrl ?: song.albumArtUri?.toString(),
                    fallbackIcon = Icons.Rounded.MusicNote,
                    shape = segmentedShape(index, songs.size),
                    onPlay = { onPlayQueue(songs, song) },
                    onDelete = { onDelete(song.id) }
                )
                if (index < songs.lastIndex) SegmentDivider(inset = 74.dp)
            }
        }

        if (songs.isEmpty() && progress.isEmpty() && offline.isEmpty()) {
            item {
                StateView.Empty(
                    title = stringResource(R.string.dl_empty_music),
                    icon = Icons.Rounded.MusicNote,
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun ProgressCard(
    item: DownloadProgress,
    shape: Shape,
    onCancel: (String) -> Unit,
    onRetry: (DownloadRequest) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit
) {
    val failed = item.status == DownloadStatus.FAILED
    val queued = item.status == DownloadStatus.QUEUED
    val paused = item.status == DownloadStatus.PAUSED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        failed -> Icons.Rounded.ErrorOutline
                        paused -> Icons.Rounded.Pause
                        queued -> Icons.Rounded.Schedule
                        else -> Icons.Rounded.Download
                    },
                    contentDescription = null,
                    tint = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.request.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            failed -> stringResource(R.string.dl_failed)
                            paused -> stringResource(R.string.dl_paused)
                            queued -> stringResource(R.string.dl_waiting)
                            item.totalBytes > 0 -> "%.1f / %.1f MB".format(
                                item.bytesDownloaded / (1024 * 1024f),
                                item.totalBytes / (1024 * 1024f)
                            )
                            else -> stringResource(R.string.dl_preparing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (paused || queued || item.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = {
                        if (paused) onResume(item.songId) else onPause(item.songId)
                    }) {
                        Icon(
                            if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = stringResource(if (paused) R.string.dl_resume else R.string.cd_pause)
                        )
                    }
                }
                if (failed) {
                    IconButton(onClick = { onRetry(item.request) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                }
                IconButton(onClick = { onCancel(item.songId) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }

            if (!failed) {
                Spacer(modifier = Modifier.height(10.dp))
                if (queued) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: Shape,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    wideArtwork: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Clip before clickable so the ripple follows the segment shape
            // instead of bleeding past the rounded corners.
            .clip(shape)
            .clickable(onClick = onPlay),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = if (wideArtwork) 88.dp else 52.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
