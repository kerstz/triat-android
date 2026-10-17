package com.ivor.ivormusic.ui.player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import com.ivor.ivormusic.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.ui.components.QueueDragHandle
import com.ivor.ivormusic.ui.components.QueueRowContainer
import com.ivor.ivormusic.ui.components.queueDragLongPress
import com.ivor.ivormusic.ui.components.rememberQueueRemoval
import com.ivor.ivormusic.ui.components.queueMoveActions
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import com.ivor.ivormusic.ui.components.rememberFocusedQueueListState
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.queuePlaylistDateText
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Material 3 Expressive Gesture-Based Music Player
 * 
 * Design based on reference UI:
 * - "Playing From" header with album name at top
 * - Large carousel album art (tap to play/pause, swipe to change tracks)
 * - Song title and artist centered below
 * - Slider progress bar
 * - HorizontalFloatingToolbar at the bottom for actions (shuffle, repeat, favorite, download)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GesturePlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    /** Hand this song to the video player; null where there is no video pipeline. */
    onAlbumClick: (String) -> Unit = {},
    onOpenAlbum: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {}
) {
    // Back is handled once by ExpandablePlayer, which previews the collapse
    // as a gesture instead of firing at the end of one. A BackHandler here
    // would be registered later and silently win.
    
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playerHaptics = rememberPlayerHaptics()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val currentQueue by viewModel.playOrderQueue.collectAsStateWithLifecycle()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsStateWithLifecycle()
    val playWhenReady by viewModel.playWhenReady.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsStateWithLifecycle()
    
    // Load more state
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    
    var showQueue by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    // Sleep timer

    val surfaceColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    // Root guard against invalid dimensions during collapse transitions
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Don't render anything if dimensions are too small
        if (maxWidth < 200.dp || maxHeight < 200.dp) {
            return@BoxWithConstraints
        }
        
        Crossfade(targetState = showQueue, label = "GesturePlayerQueueTransition") { isQueueVisible ->
            if (isQueueVisible) {
                GestureQueueView(
                    queue = currentQueue,
                    currentQueueItemId = currentQueueItemId,
                    onQueueItemClick = { item -> viewModel.skipToQueueItem(item.id) },
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    isDownloaded = { id -> viewModel.isDownloaded(id) },
                    isDownloading = { id -> viewModel.isDownloading(id) },
                    isLocalOriginal = { song -> viewModel.isLocalOriginal(song) },
                    onSaveQueue = { name, description, onSaved ->
                        viewModel.saveQueueAsPlaylist(name, description, onSaved)
                    },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                    onRemoveItem = { item -> viewModel.removeQueueItem(item.id) },
                    onMoveSong = { from, to -> viewModel.movePlayOrderItem(from, to, persist = false) },
                    onCommitOrder = { viewModel.commitQueueOrder() },
                    onUndoRemove = { viewModel.undoQueueRemoval() }
                )
            } else {
                GestureNowPlayingView(
                    currentSong = currentSong,
                    queue = currentQueue,
                    currentQueueItemId = currentQueueItemId,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering && playWhenReady,
                    progress = progress,
                    duration = duration,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    onSeekTo = { viewModel.seekTo(it) },
                    ambientBackground = ambientBackground,
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    onShowOptions = { showOptions = true },
                    onArtistClick = onArtistClick,
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onToggleFavorite = { viewModel.toggleCurrentSongLike() },
                    onToggleDownload = { currentSong?.let { viewModel.toggleDownload(it) } },
                    onPlayPauseToggle = {
                        playerHaptics.playPause(!viewModel.isPlaying.value)
                        viewModel.togglePlayPause()
                    },
                    onNext = {
                        playerHaptics.skip()
                        viewModel.skipToNext()
                    },
                    onPrevious = {
                        playerHaptics.skip()
                        viewModel.skipToPrevious()
                    },
                    onQueueItemChange = { item ->
                        playerHaptics.skip()
                        viewModel.skipToQueueItem(item.id)
                    },

                    isDownloaded = currentSong?.let { viewModel.isDownloaded(it.id) } ?: false,
                    isDownloading = currentSong?.let { viewModel.isDownloading(it.id) } ?: false,
                    isLocalOriginal = currentSong?.let { viewModel.isLocalOriginal(it) } ?: false,
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            }
        }
    }


    // The overflow menu, gated on a song so it can never open against nothing.
    if (showOptions) {
        currentSong?.let { song ->
            NowPlayingOptionsSheet(
                song = song,
                viewModel = viewModel,
                onDismiss = { showOptions = false },
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onOpenAlbum = onOpenAlbum
            )
        }
    }
}

/**
 * Gesture-based Now Playing View matching the reference design
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GestureNowPlayingView(
    currentSong: Song?,
    queue: List<MusicQueueItem>,
    currentQueueItemId: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    onSeekTo: (Long) -> Unit,
    ambientBackground: Boolean,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowOptions: () -> Unit,
    onArtistClick: (String) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onQueueItemChange: (MusicQueueItem) -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocalOriginal: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    // Simple current song index for the custom carousel
    val currentIndex = remember(currentQueueItemId, queue) {
        if (queue.isEmpty()) 0
        else queue.indexOfFirst { it.id == currentQueueItemId }
            .coerceIn(0, queue.lastIndex.coerceAtLeast(0))
    }

    // Swipe-to-skip on the song information. It steps the queue rather than
    // calling the ViewModel's skip, because that is what the carousel above
    // does — and the carousel follows currentIndex, so it slides across to
    // meet the new song instead of the two disagreeing. Both ends clamp: this
    // style has no prev/next buttons, so running off the queue must be a
    // no-op, not a wrap.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { queue.getOrNull(currentIndex + 1)?.let(onQueueItemChange) },
        onPrevious = { queue.getOrNull(currentIndex - 1)?.let(onQueueItemChange) }
    )
    
    // Get album info
    val albumName = currentSong?.album?.takeIf { !isUnknownAlbum(it) } ?: stringResource(R.string.unknown_album)
    val albumArtUrl = currentSong?.highResThumbnailUrl 
        ?: currentSong?.thumbnailUrl 
        ?: currentSong?.albumArtUri?.toString()
    
    // Use BoxWithConstraints to guard against invalid dimensions during transitions
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Don't render content if dimensions are too small (during collapse animation)
        if (maxWidth < 100.dp || maxHeight < 100.dp) {
            return@BoxWithConstraints
        }

        // Calculate width to match album art exactly (Pre-calculated for nested scopes)
        val progressBarWidth = if (queue.isNotEmpty()) maxWidth * 0.90f else maxWidth * 0.98f

        
        Box(modifier = Modifier.fillMaxSize()) {
            // Chromatic Mist ambient background
            ChromaticMistBackground(
                albumArtUrl = albumArtUrl,
                enabled = ambientBackground,
                modifier = Modifier.fillMaxSize()
            )
            
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ========== 1. TOP BAR ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collapse and queue are glyphs, not pills: the accent and the
                // only filled container on this screen belong to playback.
                IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        stringResource(R.string.cd_collapse),
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                // Queue and overflow sit together because they are the same
                // kind of thing - ways out of the now-playing screen - and the
                // overflow used to hold a whole row of its own below the
                // transport for one glyph.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShowQueue, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            stringResource(R.string.cd_queue),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onShowOptions, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            stringResource(R.string.cd_more_options),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // "Playing From / album" header removed to keep the player minimal.

            // ========== CENTER CONTENT (vertically centered) ==========
            // Centred in the space it is given, not pinned to the top of it.
            // Artwork, title and bar are one block, and with the overflow row
            // gone the slack all fell between that block and the transport -
            // a hole in the middle of the screen. Centred, the slack splits
            // above and below and reads as breathing room.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Push album down slightly
                Spacer(modifier = Modifier.height(16.dp))
                
                // ========== 3. ALBUM ART ==========
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Guard against invalid dimensions during collapse transitions
                    // Use minimum usable size (not just zero) for high DPI compatibility
                    if (maxWidth < 50.dp || maxHeight < 50.dp) {
                        return@BoxWithConstraints
                    }
                    
                    // Use FULL available width for HUGE album art - ensure positive size
                    val albumSize = maxWidth.coerceAtLeast(1.dp)
                    
                    Box(
                        modifier = Modifier.size(albumSize),
                        contentAlignment = Alignment.Center
                    ) {
                        // Album Art Carousel
                        if (queue.isNotEmpty()) {
                            SwipeableAlbumCarousel(
                                queue = queue,
                                currentIndex = currentIndex,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onPlayPauseToggle = onPlayPauseToggle,
                                onQueueItemChange = onQueueItemChange
                            )
                        } else if (currentSong != null) {
                            // Single album art
                            SingleAlbumArt(
                                song = currentSong,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onPlayPauseToggle = onPlayPauseToggle
                            )
                        }
                    }
                }
                
                // Centered song info and progress area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ========== 4. SONG INFO ==========
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .swipeToSkip(swipeToSkip)
                                .swipeToSkipFollow(swipeToSkip)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentSong?.title?.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled),
                                // The artwork is this player's subject; a bold
                                // headline over it made two things shout at
                                // once and neither won.
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = onSurfaceColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val artistName = currentSong?.artist?.takeIf { !isUnknownArtist(it) } ?: stringResource(R.string.unknown_artist)
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariantColor,
                                modifier = Modifier
                                    .clickable(enabled = artistName != stringResource(R.string.unknown_artist)) { onArtistClick(artistName) },
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        // ========== 5. WAVY PROGRESS BAR (Expressive Style) ==========
                        
                        Column(
                            modifier = Modifier
                                .width(progressBarWidth)
                        ) {
                            // Track the finger locally while scrubbing; seek once on
                            // release instead of on every drag frame (rebuffer storms).
                            var scrubPosition by remember { mutableStateOf<Float?>(null) }
                            val displayedProgress = scrubPosition?.toLong() ?: progress
                            // Advanced from the clock each frame rather than
                            // sprung toward a once-a-second sample - see
                            // rememberSmoothProgress.
                            val smoothProgress = rememberSmoothProgress(
                                positionMs = progress,
                                durationMs = duration,
                                isPlaying = isPlaying,
                                scrubPositionMs = scrubPosition
                            )

                            val thickStroke = Stroke(width = with(LocalDensity.current) { 6.dp.toPx() }, cap = StrokeCap.Round)

                            // Wavy progress with invisible slider overlay for touch
                            Box(contentAlignment = Alignment.Center) {
                                ExpressiveScrubber(
                                    progress = { smoothProgress.value },
                                    modifier = Modifier.fillMaxWidth().height(scrubberTrackHeight(14.dp)),
                                    stroke = thickStroke,
                                    trackStroke = thickStroke,
                                    color = primaryColor,
                                    trackColor = onSurfaceVariantColor.copy(alpha = 0.15f)
                                )

                                // Invisible slider for touch interaction
                                Slider(
                                    interactionSource = LocalPlayerScrubInteraction.current,
                                    value = scrubPosition ?: progress.toFloat(),
                                    onValueChange = { scrubPosition = it },
                                    onValueChangeFinished = {
                                        scrubPosition?.let { onSeekTo(it.toLong()) }
                                        scrubPosition = null
                                    },
                                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Transparent,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Reference, not content: small and quiet, so
                                // the eye reads the bar and finds the numbers
                                // only when it goes looking for them.
                                Text(
                                    text = formatDuration(displayedProgress),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariantColor.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariantColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            PlayerVisualizerSlot(modifier = Modifier.fillMaxWidth())

            // ========== 6. TRANSPORT ROW (coverflow) + secondary toolbar ==========
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CoverflowTransportRow(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    primaryColor = primaryColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
                // Lifts the transport toward the bar it belongs to. The
                // column spreads its slack between the two, so the way to
                // close the gap above the controls is to leave more below
                // them - and the gesture bar wants that room anyway.
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
        }
    }
}

/**
 * Coverflow transport, pared back to the essentials: previous, a large
 * play/pause disc, next. The disc is the one bold element; the two skips stay
 * quiet beside it. Shuffle and repeat moved off the face for a minimal player.
 */
@Composable
private fun CoverflowTransportRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    primaryColor: Color,
    onSurfaceVariantColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                tint = onSurfaceVariantColor,
                modifier = Modifier.size(28.dp)
            )
        }
        // No elevation: this is the only filled shape on the screen, so the
        // fill already carries it, and a drop shadow was the one place the
        // player stopped being flat.
        Surface(
            onClick = onPlayPauseToggle,
            shape = CircleShape,
            color = primaryColor,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = onSurfaceVariantColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}


/**
 * Custom Swipeable Album Carousel with smooth sliding animations
 * 
 * Architecture:
 * - Uses a continuous float position for the carousel
 * - Pre-renders 5 albums (-2 to +2) so transitions are instant
 * - External changes animate smoothly via spring physics
 * - No flickering, no delayed loading
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwipeableAlbumCarousel(
    queue: List<MusicQueueItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit,
    onQueueItemChange: (MusicQueueItem) -> Unit
) {
    val styleWheel = LocalPlayerStyleWheelController.current
    // The carousel position as a continuous float
    // Position 0 = first song centered, Position 1 = second song centered, etc.
    var targetPosition by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    val animatedPosition by animateFloatAsState(
        targetValue = targetPosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CarouselPosition"
    )
    
    // Track if user is dragging (to pause external sync during drag)
    var isDragging by remember { mutableStateOf(false) }
    
    // Sync with external index changes (e.g., from queue click, skip buttons)
    LaunchedEffect(currentIndex) {
        if (!isDragging && targetPosition.roundToInt() != currentIndex) {
            // Smoothly animate to the new position
            targetPosition = currentIndex.toFloat()
        }
    }
    
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Guard against invalid dimensions during transitions
        // Use minimum usable size (not just zero) for high DPI compatibility
        if (maxWidth < 50.dp || maxHeight < 50.dp) {
            return@BoxWithConstraints
        }
        
        // Sizing calculations - BIGGER for better visibility
        val albumSize = (minOf(maxWidth * 0.90f, maxHeight * 0.95f)).coerceAtLeast(1.dp)
        val cornerRadius = albumSize * 0.08f
        
        // Spacing between album centers. Tight so the rotated side covers peek
        // in at the edges, coverflow-style, rather than sitting apart.
        val spacing = albumSize * 0.62f
        val spacingPx = with(LocalDensity.current) { spacing.toPx() }
        val coverflowCameraDistance = with(LocalDensity.current) { 56.dp.toPx() }
        
        // Track starting position when drag begins
        var dragStartPosition by remember { mutableFloatStateOf(0f) }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(albumSize)
                .pointerInput(queue.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            isDragging = true
                            dragStartPosition = targetPosition
                        },
                        onDragEnd = {
                            isDragging = false
                            // Snap to nearest integer position
                            val nearestIndex = targetPosition.roundToInt().coerceIn(0, queue.lastIndex)
                            val startIndex = dragStartPosition.roundToInt()
                            
                            // Always trigger song change if we moved to different index
                            if (nearestIndex != startIndex && nearestIndex in 0..queue.lastIndex) {
                                onQueueItemChange(queue[nearestIndex])
                            }
                            targetPosition = nearestIndex.toFloat()
                        },
                        onDragCancel = {
                            isDragging = false
                            // Return to start position on cancel
                            targetPosition = dragStartPosition.roundToInt().toFloat().coerceIn(0f, queue.lastIndex.toFloat())
                        },
                        onHorizontalDrag = { _, amount ->
                            val delta = -amount / spacingPx // Negative: drag right = go to lower index
                            val newPosition = (targetPosition + delta).coerceIn(0f, queue.lastIndex.toFloat())
                            targetPosition = newPosition
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Render albums in range: visible ones around the current position
            // Pre-render 2 extra on each side for smooth transitions
            val centerIndex = animatedPosition.roundToInt()
            val visibleRange = (centerIndex - 2).coerceAtLeast(0)..(centerIndex + 2).coerceAtMost(queue.lastIndex)
            
            visibleRange.forEach { songIndex ->
                val queueItem = queue[songIndex]
                val song = queueItem.song
                
                // Position relative to center (0 = centered)
                val relativePosition = songIndex.toFloat() - animatedPosition
                val distanceFromCenter = relativePosition.absoluteValue
                
                // Skip rendering if too far off (optimization)
                if (distanceFromCenter > 2.5f) return@forEach
                
                // Coverflow: center cover full and flat, side covers shrink to
                // ~0.82 and rotate up to 42 degrees around Y for the 3D turn.
                val sizeMultiplier = (1f - distanceFromCenter * 0.18f).coerceIn(0.82f, 1f)
                val currentSize = albumSize * sizeMultiplier
                val currentCornerRadius = cornerRadius * sizeMultiplier

                // Alpha: full at center, faded at sides
                val alpha = (1f - distanceFromCenter * 0.35f).coerceIn(0.4f, 1f)

                // Turn around the vertical axis: right covers face left, left
                // covers face right. Clamped so a far cover does not over-rotate.
                val rotationYValue = (-relativePosition * 42f).coerceIn(-42f, 42f)

                // Z-index: center album on top
                val zIndex = (10f - distanceFromCenter * 3f).coerceAtLeast(0f)

                // X position from center
                val xOffset = spacing * relativePosition

                // Y offset for depth (side albums drop down slightly)
                val yOffset = 6.dp * distanceFromCenter
                
                // Is this the "current" centered album?
                val isCentered = distanceFromCenter < 0.5f
                
                val imgUrl = song.highResThumbnailUrl 
                    ?: song.thumbnailUrl 
                    ?: song.albumArtUri?.toString()
                
                // Use key for stable identity
                key(queueItem.id) {
                    Surface(
                        modifier = Modifier
                            .size(currentSize)
                            .offset(x = xOffset, y = yOffset)
                            .zIndex(zIndex)
                            .alpha(alpha)
                            .graphicsLayer {
                                rotationY = rotationYValue
                                cameraDistance = coverflowCameraDistance
                            }
                            .then(
                                if (isCentered) {
                                    Modifier
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { onPlayPauseToggle() })
                                        }
                                        .styleWheelHold(styleWheel)
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(currentCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = if (isCentered) 16.dp else 4.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Album art with stable loading
                            if (imgUrl != null) {
                                PlayerArtwork(
                                    song = song,
                                    motionEligible = isCentered,
                                    contentDescription = song.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(currentCornerRadius)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // Fallback gradient if no image
                            if (imgUrl == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(currentSize * 0.35f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            
                            // Only buffering dims the artwork. A paused state
                            // already reads from the transport button right
                            // below, so a second giant play glyph over the
                            // cover was noise on top of the one thing this
                            // player exists to show.
                            if (isCentered && songIndex == currentIndex && isBuffering) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(currentCornerRadius))
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(60.dp),
                                        color = Color.White,
                                        polygons = listOf(
                                            MaterialShapes.SoftBurst,
                                            MaterialShapes.Cookie9Sided,
                                            MaterialShapes.Pill,
                                            MaterialShapes.Sunny
                                        )
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



/**
 * Single Album Art (fallback when no queue)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SingleAlbumArt(
    song: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit
) {
    val styleWheel = LocalPlayerStyleWheelController.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Guard against invalid dimensions during transitions
        // Use minimum usable size (not just zero) for high DPI compatibility
        if (maxWidth < 50.dp || maxHeight < 50.dp) {
            return@BoxWithConstraints
        }
        
        // Use 98% of available space for HUGE album art - ensure positive size
        val albumSize = (minOf(maxWidth, maxHeight) * 0.98f).coerceAtLeast(1.dp)
        val cornerRadius = (albumSize * 0.10f).coerceAtLeast(0.dp)
        
        Surface(
            modifier = Modifier
                .size(albumSize)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onPlayPauseToggle() })
                }
                .styleWheelHold(styleWheel),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 16.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val artSong = song?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }

                if (artSong != null) {
                    PlayerArtwork(
                        song = artSong,
                        contentDescription = artSong.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(albumSize * 0.35f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
                
                // Buffering only - see the queue variant above.
                if (isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(64.dp),
                            color = Color.White,
                            polygons = listOf(
                                MaterialShapes.SoftBurst,
                                MaterialShapes.Cookie9Sided,
                                MaterialShapes.Pill,
                                MaterialShapes.Sunny
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gesture Queue View
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GestureQueueView(
    queue: List<MusicQueueItem>,
    currentQueueItemId: String?,
    onQueueItemClick: (MusicQueueItem) -> Unit,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    isLocalOriginal: (Song) -> Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    onRemoveItem: (MusicQueueItem) -> Unit = {},
    onMoveSong: (from: Int, to: Int) -> Unit = { _, _ -> },
    onCommitOrder: () -> Unit = {},
    onUndoRemove: () -> Unit = {},
    onSaveQueue: (name: String, description: String?, onSaved: (String, Int) -> Unit) -> Unit,
) {
    val currentSong = queue.find { it.id == currentQueueItemId }?.song
    val currentQueueIndex = remember(queue, currentQueueItemId) {
        queue.indexOfFirst { it.id == currentQueueItemId }
    }
    val listState = rememberFocusedQueueListState(
        currentQueueIndex = currentQueueIndex,
        leadingItemCount = 1,
    )
    val rowKeys = remember(queue) { queue.map { it.id } }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = onMoveSong,
        onSettle = onCommitOrder
    )
    val removal = rememberQueueRemoval(onUndo = onUndoRemove)
    var showSaveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Guard against invalid dimensions during transitions
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (maxWidth < 100.dp || maxHeight < 100.dp) {
            return@BoxWithConstraints
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        stringResource(R.string.cd_collapse),
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // The title is a title, not a chip.
                Text(
                    text = stringResource(R.string.ps_up_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = queue.isNotEmpty(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            stringResource(R.string.queue_save_as_playlist),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onBackToPlayer, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            stringResource(R.string.cd_now_playing),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Queue Content
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.ps_queue_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurfaceVariantColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current song header
                    currentSong?.let { song ->
                        item(key = "now_playing_gesture_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Featured album art
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val hasArt = song.thumbnailUrl != null || song.albumArtUri != null

                                    Surface(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clickable { onBackToPlayer() },
                                        shape = RoundedCornerShape(28.dp),
                                        shadowElevation = 8.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        if (hasArt) {
                                            SongArtwork(
                                                song = song,
                                                contentDescription = stringResource(R.string.cd_now_playing),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(28.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Text(
                                    text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onSurfaceColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.artist.takeIf { !isUnknownArtist(it) } ?: stringResource(R.string.unknown_artist),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurfaceVariantColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = onSurfaceVariantColor.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = stringResource(R.string.ps_queue),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceVariantColor,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = onSurfaceVariantColor.copy(alpha = 0.1f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                    
                    // Queue items. Occurrence-qualified keys, so a reorder moves
                    // one row rather than re-keying every row it passed.
                    itemsIndexed(
                        queue,
                        key = { index, _ -> rowKeys.getOrElse(index) { "gesture_queue_$index" } }
                    ) { index, queueItem ->
                        val song = queueItem.song
                        val isCurrent = queueItem.id == currentQueueItemId
                        val key = rowKeys.getOrElse(index) { "gesture_queue_$index" }
                        val isDragging = reorder.draggingKey == key

                        QueueRowContainer(
                            isDragging = isDragging,
                            dragOffset = reorder.offsetFor(key),
                            removeEnabled = queue.size > 1,
                            onRemove = {
                                onRemoveItem(queueItem)
                                removal.onRemoved(song.title)
                            },
                            modifier = if (isDragging) Modifier else Modifier.animateItem()
                        ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .queueDragLongPress(reorder, key)
                                .queueMoveActions(reorder, key, index, queue.size),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCurrent) primaryColor.copy(alpha = 0.12f) 
                                   else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(
                                1.5.dp, 
                                primaryColor.copy(alpha = 0.3f)
                            ) else null,
                            onClick = { onQueueItemClick(queueItem) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = stringResource(R.string.cd_playing),
                                            tint = primaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = onSurfaceVariantColor
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                val songImgUrl = song.thumbnailUrl ?: song.albumArtUri?.toString()
                                
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = if (isCurrent) 4.dp else 2.dp
                                ) {
                                    if (songImgUrl != null) {
                                        AsyncImage(
                                            model = songImgUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(16.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) primaryColor else onSurfaceColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrent) primaryColor.copy(alpha = 0.7f) else onSurfaceVariantColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                // Download Status Icon
                                if (isDownloading(song.id)) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = if (isCurrent) primaryColor else onSurfaceVariantColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isDownloaded(song.id)) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.song_options_downloaded),
                                        tint = if (isCurrent) primaryColor else onSurfaceVariantColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isLocalOriginal(song)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Smartphone,
                                        contentDescription = stringResource(R.string.cd_local_file),
                                        tint = if (isCurrent) primaryColor.copy(alpha = 0.7f) else onSurfaceVariantColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                QueueDragHandle(
                                    state = reorder,
                                    rowKey = key,
                                    tint = onSurfaceVariantColor.copy(alpha = 0.7f)
                                )

                                IconButton(
                                    onClick = {
                                        onRemoveItem(queueItem)
                                        removal.onRemoved(song.title)
                                    },
                                    enabled = queue.size > 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cd_remove_from_queue),
                                        tint = onSurfaceVariantColor
                                    )
                                }
                            }
                        }
                        }
                    }

                    // Load More Button with expressive shape morphing
                    item(key = "gesture_queue_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onLoadMore,
                                enabled = !isLoadingMore,
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                            ) {
                                if (isLoadingMore) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        polygons = listOf(
                                            MaterialShapes.Cookie9Sided,
                                            MaterialShapes.Pill,
                                            MaterialShapes.Sunny
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleSmall)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.load_more), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        DismissibleSnackbarHost(
            hostState = removal.hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )

        if (showSaveDialog) {
            CreatePlaylistDialog(
                initialName = stringResource(
                    R.string.queue_save_default_name,
                    remember { queuePlaylistDateText() }
                ),
                onDismiss = { showSaveDialog = false },
                onCreate = { name, description ->
                    showSaveDialog = false
                    onSaveQueue(name, description) { savedName, trackCount ->
                        removal.announce(
                            context.resources.getQuantityString(
                                R.plurals.queue_saved_to_playlist,
                                trackCount,
                                trackCount,
                                savedName
                            )
                        )
                    }
                }
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
