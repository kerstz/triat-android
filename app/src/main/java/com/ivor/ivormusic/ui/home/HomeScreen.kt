package com.ivor.ivormusic.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.IconToggleButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.activity.compose.BackHandler
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.components.rememberPermissionState
import com.ivor.ivormusic.ui.components.scrollToTop
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.ExpandablePlayer
import com.ivor.ivormusic.ui.player.PlayerSheetContent
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.compose.material3.MaterialShapes
import androidx.compose.animation.with
import kotlinx.coroutines.launch
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.components.hiddenFraction
import com.ivor.ivormusic.ui.library.LibraryContent
import androidx.compose.animation.ExperimentalAnimationApi
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.R
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.data.UpdateRepository
import com.ivor.ivormusic.data.UpdateResult
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle

/**
 * Height of the video top bar when the shell draws it: a 44dp control row with
 * 16dp above and below. Kept beside the padding that reserves room for it, so
 * the two cannot drift apart.
 */
private val VIDEO_SHELL_TOP_BAR_HEIGHT = 76.dp

/** Gap the mini player keeps above the system navigation bar once the floating toolbar is gone. */
private val MINI_PLAYER_RESTING_GAP = 16.dp


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    onSongClick: (Song) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: HomeViewModel = viewModel(),
    isDarkMode: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {},
    loadLocalSongs: Boolean = false,
    excludedFolders: Set<String> = emptySet(),
    ambientBackground: Boolean = true,
    playerArtworkColors: Boolean = true,
    playerStyle: PlayerStyle = PlayerStyle.EDITORIAL,
    onPlayerStyleChange: (PlayerStyle) -> Unit = {},
    manualScan: Boolean = false,
    localOnly: Boolean = false,
    /** Spotlight: the alternative music Home. Off by default. */
    spotlightHome: Boolean = false,
    /** Use Material 3's compact bar instead of the default floating toolbar. */
    nonExpressiveNavigationBar: Boolean = false,
    /**
     * Hoisted so the video mini player - an overlay above the NavHost, outside
     * this composable - can follow the same toolbar this shell hides on scroll.
     */
    floatingToolbarState: androidx.compose.material3.FloatingToolbarState =
        androidx.compose.material3.rememberFloatingToolbarState(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val homePreferences = remember(context) { com.ivor.ivormusic.data.ThemePreferences(context) }
    val localSongs by viewModel.songs.collectAsStateWithLifecycle()
    val youtubeSongs by viewModel.youtubeSongs.collectAsStateWithLifecycle()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsStateWithLifecycle()
    
    val contentSource by viewModel.contentSource.collectAsStateWithLifecycle()
    val mixSourcesOnHome by viewModel.mixSourcesOnHome.collectAsStateWithLifecycle()
    val soundCloudHome by viewModel.soundCloudHome.collectAsStateWithLifecycle()
    LaunchedEffect(contentSource) { viewModel.refreshSoundCloudHome() }

    // Use local songs or YouTube songs (which includes fallback search results if not logged in).
    // When SoundCloud is a source and "mix on home" is on, blend its charts into
    // the home feed so both services share the page; interleaving keeps either
    // source from dominating.
    val baseSongs = if (loadLocalSongs) localSongs else youtubeSongs
    val songs = remember(baseSongs, soundCloudHome, contentSource, mixSourcesOnHome) {
        if (!loadLocalSongs && mixSourcesOnHome && contentSource.includesSoundCloud && soundCloudHome.isNotEmpty()) {
            interleaveSources(baseSongs, soundCloudHome)
        } else {
            baseSongs
        }
    }

    // Local play history, for the "Jump back in" rail. Free (a file read, no
    // network), and refreshed whenever the Home tab comes back into view so a
    // song played since the last look shows up.
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()

    val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by playerViewModel.isBuffering.collectAsStateWithLifecycle()
    val playWhenReady by playerViewModel.playWhenReady.collectAsStateWithLifecycle()
    val progress by playerViewModel.progress.collectAsStateWithLifecycle()
    val duration by playerViewModel.duration.collectAsStateWithLifecycle()
    
    val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
    
    // Bottom sheet state for player - skip partial expand for direct full-screen
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlayerSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val permissionState = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    // Load songs based on setting
    LaunchedEffect(Unit, loadLocalSongs, excludedFolders, manualScan) {
        viewModel.checkYouTubeConnection()
        if (loadLocalSongs) {
            if (!permissionState.isGranted) {
                permissionState.launchPermissionRequest()
            } else {
                viewModel.loadSongs(excludedFolders, manualScan)
            }
        } else {
            // Load YouTube recommendations when not using local songs
            viewModel.loadYouTubeRecommendations()
        }
    }

    LaunchedEffect(permissionState.isGranted, loadLocalSongs, excludedFolders, manualScan) {
        if (permissionState.isGranted && loadLocalSongs) {
            viewModel.loadSongs(excludedFolders, manualScan)
        }
    }
    
    // Saveable, not just remembered. Opening a channel page is a real
    // navigation, so this composable is disposed and restored on the way back,
    // and a plainly-remembered tab index would drop the user on Home every time
    // they looked at a creator. The scroll states below already survive it,
    // because rememberLazyListState is saveable.
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableIntStateOf(homePreferences.getLastHomeTab())
    }

    val rootTab = 0
    val visualTabOrder = listOf(0, 1, 2)

    LaunchedEffect(selectedTab) {
        homePreferences.setLastHomeTab(selectedTab)
    }

    /** Land on [tab]. */
    fun goToTab(tab: Int) {
        selectedTab = tab
    }

    // Every tab's scroll position, remembered HERE rather than inside the tab
    // content, for two reasons.
    //
    // Anything remembered inside the AnimatedContent below is scoped to that
    // target's own composition and disposed once the transition settles, so a
    // state living down there is discarded on every tab switch - leave Home
    // halfway down, glance at Search, come back, and you are at the top. Above
    // the AnimatedContent the position survives.
    //
    // It also has to be reachable from the nav bar, so re-tapping the current
    // tab can send its list back to the top.
    //
    // One per tab AND per mode where the mode swaps the content: video Home and
    // music Home are different lists, and sharing a state between them would
    // restore one list's index into the other.
    val musicHomeScrollState = rememberLazyListState()
    val searchScrollState = rememberLazyListState()
    val musicLibraryScrollState = rememberLazyListState()

    // Which of the above the visible tab is currently driving.
    val currentTabScrollState = when (selectedTab) {
        0 -> musicHomeScrollState
        1 -> searchScrollState
        else -> musicLibraryScrollState
    }

    // Material owns both the offset range and the settle/fling behavior. The
    // toolbar is bottom-centred, so its exit direction is down and the Home
    // shell only needs to forward nested scroll from whichever tab is active.
    val floatingToolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
        state = floatingToolbarState
    )

    // Handle back button to return to Home tab if on Search or Library
    BackHandler(enabled = selectedTab != rootTab) {
        selectedTab = rootTab
    }

    // Re-read the history when Home becomes the active tab. Playback writes an
    // entry only after 15s, so coming back from the player is exactly when the
    // rail has something new to show.
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) viewModel.refreshRecentlyPlayed()
    }

    // Auth Dialog State
    var showAuthDialog by remember { mutableStateOf(false) }
    var addAuthAsNewProfile by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showCookiePasteSheet by remember { mutableStateOf(false) }

    // The avatar always opens the profile switcher now, signed in or not: with
    // device-only profiles there is always something to switch between, and
    // sending a signed-out user straight to a Google login was the app assuming
    // an account is the only way to have an identity.
    val onProfileClick: () -> Unit = { showAccountSheet = true }

    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Loading state for playlist fetch
    var isPlaylistLoading by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    // Artist screen state (for navigation from player)
    var viewedArtistFromPlayer by remember { mutableStateOf<String?>(null) }
    // Where a Library sub-screen handed over from another tab goes back to:
    // that tab, with the player left exactly as it was. Without it back
    // strands the user in a Library they never navigated through; the older
    // answer, reopening the player sheet, replayed its whole expand animation
    // over a screen the user was trying to leave. Null when the hand-off began
    // in the Library itself, or crossed a mode switch - the tab it began on
    // then belongs to the other mode.
    var libraryReturnTab by remember { mutableStateOf<Int?>(null) }
    fun noteLibraryReturn() {
        libraryReturnTab = selectedTab.takeIf { it != 2 }
    }

    // The album counterpart, from the player's overflow menu. A name rather
    // than a PlaylistDisplayItem, because a device album is identified by its
    // name and the Library builds the rest from the songs it already has.
    var viewedAlbumFromPlayer by remember { mutableStateOf<String?>(null) }

    // The same hand-off, asked for from outside this screen entirely: the
    // "Open music artist page" cross-link on a creator's channel page pops back
    // to home and leaves the request on the ViewModel, because a NavHost
    // destination cannot reach the tab state that lives in here.
    val pendingArtistPage by viewModel.pendingArtistPage.collectAsStateWithLifecycle()
    LaunchedEffect(pendingArtistPage) {
        pendingArtistPage?.let { artist ->
            // The artist page is the music-mode view of this creator, and tab 2
            // belongs to the Subscriptions feed while the video toggle is on.
            // Asking for it is therefore asking to be in music mode.
            noteLibraryReturn()
            viewedArtistFromPlayer = artist
            goToTab(2)
            viewModel.consumeArtistPageRequest()
        }
    }
    // Set by Spotlight's shortcut grid and shelves; consumed by LibraryContent
    // as soon as the tab renders, so returning to Library later lands on the
    // list rather than re-opening the playlist.
    var viewedPlaylistFromHome by remember {
        mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null)
    }

    // A playlist link shared or opened into the app, which lands at
    // MainActivity and cannot reach the tab state from there. Same hand-off as
    // pendingArtistPage, and it sets the mode for the same reason: the Library
    // that can show this playlist only exists on one side of the video toggle,
    // so leaving the toggle alone would land on the wrong tab and read as the
    // share having done nothing.
    val pendingPlaylistPage by viewModel.pendingPlaylistPage.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPlaylistPage) {
        pendingPlaylistPage?.let { playlist ->
            noteLibraryReturn()
            viewedPlaylistFromHome = playlist
            goToTab(2)
            viewModel.consumePlaylistPageRequest()
        }
    }
    // Long-pressed song, for the options sheet. Hosted here, once, rather than
    // per screen: the sheet acts on the PlayerViewModel, which lives at this
    // level, and the Library's sub-routes would each otherwise need their own.
    var songOptionsTarget by remember { mutableStateOf<Song?>(null) }

    // Update check state
    val updateRepository = remember { UpdateRepository() }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    // Held separately so the pill's label survives its exit animation
    var latestVersion by remember { mutableStateOf("") }

    // Check for updates on app launch (only for release builds)
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEBUG) {
            updateResult = updateRepository.checkForUpdate(
                repoPath = BuildConfig.GITHUB_REPO,
                currentVersion = BuildConfig.VERSION_NAME
            )
            (updateResult as? UpdateResult.UpdateAvailable)?.let {
                latestVersion = it.latestVersion
            }
        }
    }

    // How much clearance bottom-anchored UI needs above the nav bar inset to
    // stay clear of the floating overlays: the nav pill always, the music
    // pill (top edge at 180dp) and/or the video mini player (top edge at
    // 188dp, stacked to 284dp when the music pill is also alive). Animated so
    // FABs glide instead of jumping when a mini player appears.
    val musicPillVisible = currentSong != null
    // The standard non-expressive NavigationBar is 80dp tall. The expressive
    // toolbar occupies 84dp including its bottom breathing room. Keep the same
    // clearance above either variant so overlaid controls do not jump or
    // collide when this preference changes.
    val navigationOverlayInset = if (nonExpressiveNavigationBar) 84.dp else 88.dp
    val miniPlayerCollapsedSpacing = if (nonExpressiveNavigationBar) 96.dp else 100.dp
    // Only the floating toolbar hides on scroll; the standard NavigationBar
    // stays pinned, so there is nothing for the pill to follow there.
    val miniPlayerFollowDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        if (nonExpressiveNavigationBar) 0f
        else (miniPlayerCollapsedSpacing - MINI_PLAYER_RESTING_GAP).toPx()
    }
    val miniPlayerFollowOffsetPx: () -> Float = {
        miniPlayerFollowDistancePx * floatingToolbarState.hiddenFraction()
    }
    val bottomOverlayInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (musicPillVisible) navigationOverlayInset + 100.dp
        else navigationOverlayInset,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bottomOverlayInset"
    )
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The one inset every tab list scrolls inside, top and bottom. It belongs
    // in each list's contentPadding and never on a parent modifier: padding a
    // scroll container shrinks its viewport, so content is clipped at the
    // status bar instead of passing under it, and the top of the screen reads
    // as a system title bar. The bottom already worked this way.
    val listContentPadding = PaddingValues(
        top = statusBarInset,
        bottom = bottomOverlayInset + navBarInset + 16.dp
    )

    // Use Box overlay instead of Scaffold for truly floating navbar
    androidx.compose.runtime.CompositionLocalProvider(
        com.ivor.ivormusic.ui.components.LocalBottomOverlayInset provides bottomOverlayInset
    ) {
    // Experiment: flick between the main tabs with a swipe, same contract as
    // the channel page - a fast horizontal flick moves to the next/previous
    // visible destination and the existing AnimatedContent slide carries it,
    // so there is deliberately no follow-the-finger pager here. Observe-only:
    // nothing is consumed, taps and vertical scrolling are untouched, and the
    // same flick gates (fast, far, horizontal) keep shelf browsing from
    // tripping it. Uses the nav bar's own destination order, so hidden
    // destinations are never landed on.
    val gestureHaptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val gestureDensity = LocalDensity.current
    val gestureTabIds = listOf(0, 1, 2)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .then(
                if (nonExpressiveNavigationBar) Modifier
                else Modifier.nestedScroll(floatingToolbarScrollBehavior)
            )
            .pointerInput(selectedTab, gestureTabIds) {
                val minDistancePx = with(gestureDensity) { 96.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    val startTime = down.uptimeMillis
                    var endTime = startTime
                    var aborted = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            aborted = true
                            break
                        }
                        for (change in event.changes) {
                            totalX += change.position.x - change.previousPosition.x
                            totalY += change.position.y - change.previousPosition.y
                        }
                        endTime = event.changes.firstOrNull()?.uptimeMillis ?: endTime
                        if (event.changes.all { !it.pressed }) break
                    }
                    if (aborted) return@awaitEachGesture
                    val duration = endTime - startTime
                    if (duration in 1..400 &&
                        kotlin.math.abs(totalX) >= minDistancePx &&
                        kotlin.math.abs(totalX) >= 1.5f * kotlin.math.abs(totalY)
                    ) {
                        val index = gestureTabIds.indexOf(selectedTab)
                        val target = if (totalX < 0f) gestureTabIds.getOrNull(index + 1)
                        else gestureTabIds.getOrNull(index - 1)
                        if (target != null && target != selectedTab) {
                            gestureHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            selectedTab = target
                        }
                    }
                }
            }
    ) {
        // Main content
        if (!loadLocalSongs || permissionState.isGranted) {
            androidx.compose.animation.AnimatedContent(
                // Keyed on the mode as well as the tab so the spec below can
                // tell a tab move from a mode switch. They deserve different
                // motion and used to share one.
                targetState = selectedTab,
                label = "TabTransition",
                transitionSpec = {
                    val initialRank = visualTabOrder.indexOf(initialState).takeIf { it >= 0 }
                        ?: initialState
                    val targetRank = visualTabOrder.indexOf(targetState).takeIf { it >= 0 }
                        ?: targetState
                    val direction = if (targetRank > initialRank) 1 else -1
                    if (direction > 0) {
                        // Moving forward (Right): New enters from Right, Old leaves to Left
                        (androidx.compose.animation.slideInHorizontally { width -> width } + 
                                androidx.compose.animation.fadeIn()) togetherWith
                                (androidx.compose.animation.slideOutHorizontally { width -> -width / 3 } + 
                                        androidx.compose.animation.fadeOut())
                    } else {
                        // Moving backward (Left): New enters from Left, Old leaves to Right
                        (androidx.compose.animation.slideInHorizontally { width -> -width / 3 } + 
                                androidx.compose.animation.fadeIn()) togetherWith
                                (androidx.compose.animation.slideOutHorizontally { width -> width } + 
                                        androidx.compose.animation.fadeOut())
                    }
                }
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                    // Spin, the wheel, rides over whichever music
                    // Home is showing as a child page: back peels it
                    // away to the Home underneath. Declared here so
                    // leaving the tab closes it, as it closes a
                    // Library sub-screen.
                    var spinOpen by remember { mutableStateOf(false) }
                    var spinSource by remember { mutableStateOf(SpinSource.Mix) }
                    com.ivor.ivormusic.ui.components.PredictiveBackStack(
                        childOpen = spinOpen,
                        onBack = { spinOpen = false },
                        background = {
                            // Music Mode, Spotlight: the shortcut-grid and
                            // shelves alternative. Same flows, same overlays,
                            // same tab system - only the composition of this one
                            // tab differs, which is the move the video toggle
                            // already established.
                            if (spotlightHome) {
                                val spotlightPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
                                val spotlightLiked by viewModel.likedSongs.collectAsStateWithLifecycle()
                                SpotlightHomeContent(
                                    songs = songs,
                                    recentlyPlayed = recentlyPlayed,
                                    likedSongs = spotlightLiked,
                                    playlists = spotlightPlaylists,
                                    isInitialLoading = isLoading && songs.isEmpty(),
                                    onSongLongPress = { song -> songOptionsTarget = song },
                                    onSongClick = { song ->
                                        playerViewModel.playQueue(songs, song)
                                        showPlayerSheet = true
                                    },
                                    onPlaySongs = { queue, start ->
                                        playerViewModel.playQueue(queue, start)
                                        showPlayerSheet = true
                                    },
                                    onRecentClick = { song ->
                                        playerViewModel.playQueue(recentlyPlayed, song)
                                        showPlayerSheet = true
                                    },
                                    // Playlist detail lives in LibraryContent, so
                                    // Spotlight hands the playlist over and
                                    // switches tab: the Library opens straight
                                    // onto it, the same deep-link shape the
                                    // player already uses for artists.
                                    onPlaylistClick = { playlist ->
                                        noteLibraryReturn()
                                        viewedPlaylistFromHome = playlist
                                        selectedTab = 2
                                    },
                                    onOpenLiked = { selectedTab = 2 },
                                    onShowAllInLibrary = { selectedTab = 2 },
                                    onSpinClick = { source ->
                                        spinSource = source
                                        spinOpen = true
                                    },
                                    onProfileClick = onProfileClick,
                                    onSettingsClick = onNavigateToSettings,
                                    onDownloadsClick = onNavigateToDownloads,
                                    isDarkMode = isDarkMode,
                                    contentPadding = listContentPadding,
                                    viewModel = viewModel,
                                    excludedFolders = excludedFolders,
                                    manualScan = manualScan,
                                    listState = musicHomeScrollState
                                )
                            } else {
                                // Music Mode: Show original content. The first load
                                // renders the real screen with placeholders in the
                                // data-backed sections rather than a full-screen
                                // spinner - the top bar, titles and nav have
                                // nothing to wait for.
                                YourMixContent(
                                    songs = songs,
                                    isInitialLoading = isLoading && songs.isEmpty(),
                                    recentlyPlayed = recentlyPlayed,
                                    onRecentClick = { song ->
                                        // Resume from the history rail: the
                                        // recents are the queue, not the mix.
                                        playerViewModel.playQueue(recentlyPlayed, song)
                                        showPlayerSheet = true
                                    },
                                    onShowAllInLibrary = { selectedTab = 2 },
                                    onSpinClick = {
                                        spinSource = SpinSource.Mix
                                        spinOpen = true
                                    },
                                    onSongLongPress = { song -> songOptionsTarget = song },
                                    onSongClick = { song ->
                                        playerViewModel.playQueue(songs, song)
                                        showPlayerSheet = true
                                    },
                                    onPlayClick = {
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playQueue(songs)
                                            showPlayerSheet = true
                                        }
                                    },
                                    onProfileClick = onProfileClick,
                                    onSettingsClick = onNavigateToSettings,
                                    onDownloadsClick = onNavigateToDownloads,
                                    onImportCookiesClick = { showCookiePasteSheet = true },
                                    isDarkMode = isDarkMode,
                                    contentPadding = listContentPadding,
                                    viewModel = viewModel,
                                    excludedFolders = excludedFolders,
                                    manualScan = manualScan,
                                    listState = musicHomeScrollState
                                )
                            }
                        }
                    ) { committedByGesture ->
                        SpinOverlay(
                            open = spinOpen,
                            committedByGesture = committedByGesture,
                            initialSource = spinSource,
                            mix = songs,
                            recent = recentlyPlayed,
                            viewModel = viewModel,
                            contentPadding = listContentPadding,
                            onPlay = { queue, start ->
                                playerViewModel.playQueue(queue, start)
                                showPlayerSheet = true
                            },
                            onSongLongPress = { song -> songOptionsTarget = song },
                            onBack = { spinOpen = false }
                        )
                    }
                    }
                    1 -> SearchContent(
                        songs = songs,
                        onSongClick = { song ->
                            // Fallback: Pass all songs to enable Next/Previous navigation
                            playerViewModel.playQueue(songs, song)
                            showPlayerSheet = true
                        },
                        onPlayQueue = { songList, song ->
                            // Use the visible song list (YouTube results or filtered local songs)
                            playerViewModel.playQueue(songList, song)
                            showPlayerSheet = true
                        },
                        // Wired explicitly even though the parameter has a
                        // default: the default plays in order, and search
                        // opens artists and playlists whose Shuffle buttons
                        // would otherwise have quietly stopped shuffling here
                        // while working everywhere else.
                        onShuffleQueue = { songList ->
                            playerViewModel.playQueueShuffled(songList)
                            showPlayerSheet = true
                        },
                        onPlayRadio = { song ->
                            playerViewModel.playSongRadio(song)
                            showPlayerSheet = true
                        },
                        onProfileClick = onProfileClick,
                        onSongLongPress = { song -> songOptionsTarget = song },
                        onEnqueueSong = { song, playNext ->
                            if (playNext) playerViewModel.playNext(song)
                            else playerViewModel.addToQueue(song)
                        },
                        contentPadding = listContentPadding,
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        localOnly = localOnly,
                        requestInitialFocus = true,
                        listState = searchScrollState
                    )
                    2 -> {
                        LibraryContent(
                            songs = songs,
                            isLocalLibrary = loadLocalSongs,
                            onDownloadsClick = onNavigateToDownloads,
                            onSongClick = { song: Song ->
                                // Pass all songs to enable Next/Previous navigation
                                playerViewModel.playQueue(songs, song)
                                showPlayerSheet = true
                            },
                            onPlaylistClick = { playlist: com.ivor.ivormusic.data.PlaylistDisplayItem ->
                                // Optional: navigate to playlist detail or handled by parent
                            },
                            onPlayQueue = { songs: List<Song>, selectedSong: Song? ->
                                playerViewModel.playQueue(songs, selectedSong)
                                showPlayerSheet = true
                            },
                            onShuffleQueue = { songs: List<Song> ->
                                playerViewModel.playQueueShuffled(songs)
                                showPlayerSheet = true
                            },
                            contentPadding = listContentPadding,
                            viewModel = viewModel,
                            isDarkMode = isDarkMode,
                            initialArtist = viewedArtistFromPlayer,
                            onInitialArtistConsumed = { viewedArtistFromPlayer = null },
                            initialReturnToCaller = libraryReturnTab != null,
                            onReturnToCaller = {
                                // The tab slide reads as going back; the
                                // player keeps whatever state it was in.
                                libraryReturnTab?.let { selectedTab = it }
                                libraryReturnTab = null
                            },
                            initialPlaylist = viewedPlaylistFromHome,
                            onInitialPlaylistConsumed = { viewedPlaylistFromHome = null },
                            initialAlbum = viewedAlbumFromPlayer,
                            onInitialAlbumConsumed = { viewedAlbumFromPlayer = null },
                            onStatsClick = onNavigateToStats,
                            onSongLongPress = { song -> songOptionsTarget = song },
                            onEnqueueSong = { song, playNext ->
                                if (playNext) playerViewModel.playNext(song)
                                else playerViewModel.addToQueue(song)
                            },
                            allSongsListState = musicLibraryScrollState
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.permission_required_title), color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text(stringResource(R.string.action_grant_permission))
                    }
                }
            }
        }
        
        // Playlist Loading Overlay
        if (isPlaylistLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Block clicks
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White
                )
            }
        }
        // Both navigation variants use the same destinations and interaction
        // contract. Only their Material container and item presentation differ.
        val navBarHaptics = com.ivor.ivormusic.util.rememberKodaHaptics()
        // Settings is a NavHost route, not a Home tab. It rides in the bottom
        // bar as an action under this sentinel id: tapping it opens Settings
        // instead of selecting a tab, so it never shows as the current tab.
        val settingsNavId = -99
        val navTabs = listOf(
            Triple(0, stringResource(R.string.tab_home), Pair(Icons.Rounded.Home, Icons.Outlined.Home)),
            Triple(1, stringResource(R.string.tab_search), Pair(Icons.Filled.Search, Icons.Outlined.Search)),
            Triple(2, stringResource(R.string.tab_library), Pair(Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)),
            Triple(settingsNavId, stringResource(R.string.settings_title), Pair(Icons.Filled.Settings, Icons.Outlined.Settings))
        )
        val selectNavTab: (Int) -> Unit = { index ->
            if (index == settingsNavId) {
                navBarHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onNavigateToSettings()
            } else if (selectedTab == index) {
                if (currentTabScrollState.canScrollBackward) {
                    navBarHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    scope.launch { currentTabScrollState.scrollToTop() }
                }
            } else {
                navBarHaptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                selectedTab = index
            }
        }

        if (nonExpressiveNavigationBar) {
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                navTabs.forEach { (index, label, icons) ->
                    val selected = selectedTab == index
                    val (filledIcon, outlinedIcon) = icons
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selected,
                        onClick = { selectNavTab(index) },
                        // Apple Music / Spotify: no capsule behind the icon. The
                        // current tab is simply bright, the rest are grey, and
                        // every tab keeps its label.
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = {
                            Icon(
                                imageVector = if (selected) filledIcon else outlinedIcon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                scrollBehavior = floatingToolbarScrollBehavior,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                content = {
                    navTabs.forEach { (index, label, icons) ->
                        val selected = selectedTab == index
                        val (filledIcon, outlinedIcon) = icons

                        // Quiet and icon-only: the current tab is just the bright
                        // filled icon, the others are dimmed outlines. No accent
                        // pill, no label that slides in - the bar reads as a row
                        // of marks floating over the feed.
                        val animatedContentColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                            label = "navTint"
                        )

                        Surface(
                            selected = selected,
                            onClick = { selectNavTab(index) },
                            shape = CircleShape,
                            color = Color.Transparent,
                            contentColor = animatedContentColor,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) filledIcon else outlinedIcon,
                                    contentDescription = label,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        // Published for the video overlay, which is drawn above the NavHost and
        // therefore above this player. Cleared on dispose as well as on
        // collapse: leaving Home while the player is open drops the remembered
        // flag here, and a stale true would keep the video bar hidden for the
        // rest of the session.
        DisposableEffect(showPlayerSheet) {
            playerViewModel.setPlayerExpanded(showPlayerSheet)
            onDispose { playerViewModel.setPlayerExpanded(false) }
        }

        // A handover from an overlay above the NavHost ("Listen as music")
        // cannot open this sheet itself, because the state that owns it lives
        // here. It asks instead, and the ask is only honoured while Home is
        // composed - the flow has no replay, so a request made from another
        // route is dropped rather than springing the player open on return.
        LaunchedEffect(playerViewModel) {
            playerViewModel.playerExpandRequests.collect {
                if (playerViewModel.currentSong.value != null) showPlayerSheet = true
            }
        }

        // Expandable Player (Mini <-> Full Screen)
        ExpandablePlayer(
            isExpanded = showPlayerSheet,
            onExpandChange = { showPlayerSheet = it },
            currentSong = currentSong,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            playWhenReady = playWhenReady,
            progress = progressFraction,
            duration = playerViewModel.duration.collectAsStateWithLifecycle().value,
            onPlayPauseClick = { playerViewModel.togglePlayPause() },
            onNextClick = { playerViewModel.skipToNext() },
            viewModel = playerViewModel,
            ambientBackground = ambientBackground,
            artworkColors = playerArtworkColors,
            playerStyle = playerStyle,
            onPlayerStyleChange = onPlayerStyleChange,
            collapsedBottomSpacing = miniPlayerCollapsedSpacing,
            collapsedFollowOffsetPx = miniPlayerFollowOffsetPx,
            onArtistClick = { artistName ->
                // Collapse the player and open the artist inside the Library
                // tab. The origin tab is remembered so back returns to it
                // rather than stranding the user in the Library.
                noteLibraryReturn()
                showPlayerSheet = false
                viewedArtistFromPlayer = artistName
                goToTab(2) // Music Library tab
            },
            onAlbumClick = { albumName ->
                noteLibraryReturn()
                showPlayerSheet = false
                viewedAlbumFromPlayer = albumName
                goToTab(2)
            },
            onOpenAlbum = { albumItem ->
                // A stream's album opens the playlist detail the same way a
                // Spotlight shelf does: hand it over and switch tab.
                noteLibraryReturn()
                showPlayerSheet = false
                viewedPlaylistFromHome = albumItem
                goToTab(2)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Update available indicator. Anchored bottom-start above the floating
        // overlays (shared bottomOverlayInset) rather than the top bar, where it
        // used to sit on top of the settings/profile icons.
        androidx.compose.animation.AnimatedVisibility(
            visible = updateResult is UpdateResult.UpdateAvailable,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(
                initialScale = 0.8f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
            ),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(
                targetScale = 0.8f
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = bottomOverlayInset + 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onNavigateToUpdate() },
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.update_badge_version, latestVersion),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
    }

    // Auth Dialog
    // Long-press a song anywhere in music mode: play next, add to queue, add to
    // a playlist, like, download. The only route to addToQueue in the app.
    songOptionsTarget?.let { song ->
        com.ivor.ivormusic.ui.player.SongOptionsSheet(
            song = song,
            viewModel = playerViewModel,
            onDismiss = { songOptionsTarget = null },
            // The same hand-off the player already uses for an artist: the
            // artist page lives inside the Library tab rather than on a route,
            // so it is opened by handing the name over and switching tab.
            // Nothing else passed this, which left the sheet's own artist row
            // unreachable from every surface in the app.
            onArtistClick = { artist ->
                noteLibraryReturn()
                viewedArtistFromPlayer = artist
                goToTab(2)
            },
            onOpenAlbum = { albumItem ->
                noteLibraryReturn()
                viewedPlaylistFromHome = albumItem
                goToTab(2)
            },
            // Only for songs a feed could have recommended. A file on this
            // device was not recommended by anything, so "stop recommending
            // it" would be a control with nothing to act on - and the local
            // library is deliberately never filtered by this store.
            onNotInterested = if (song.source == com.ivor.ivormusic.data.SongSource.YOUTUBE) {
                { viewModel.hideSong(song) }
            } else null,
            onBlockArtist = if (song.source == com.ivor.ivormusic.data.SongSource.YOUTUBE) {
                { viewModel.blockArtist(song) }
            } else null
        )
    }

    if (showAuthDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = {
                showAuthDialog = false
                addAuthAsNewProfile = false
            },
            onAuthSuccess = {
                showAuthDialog = false
                addAuthAsNewProfile = false
                // Refresh login state, account info and the feeds so the UI
                // reflects the account immediately instead of after a restart
                viewModel.checkYouTubeConnection(force = true)
                viewModel.loadYouTubeRecommendations(force = true)
            },
            addAsNewProfile = addAuthAsNewProfile
        )
    }

    // The profile switcher. Replaces the old account sheet, whose "Switch
    // account" logged you out and made you sign in again - with a roster of
    // stored sessions, switching is instant and needs no network at all.
    if (showAccountSheet) {
        com.ivor.ivormusic.ui.account.AccountSwitcherSheet(
            onDismiss = { showAccountSheet = false },
            onAddYouTubeAccount = {
                // Google's login page auto-continues as whoever the WebView jar
                // already holds, so adding a second account without clearing it
                // silently hands back the first one. Stored sessions live in
                // EncryptedSharedPreferences and are untouched by this.
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    // removeAllCookies is asynchronous. Opening the login page
                    // before its callback can immediately recapture the old
                    // account and make "Add account" appear to sign it out.
                    cookieManager.flush()
                    addAuthAsNewProfile = true
                    showAuthDialog = true
                }
            },
            onReconnectProfile = {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    addAuthAsNewProfile = false
                    showAuthDialog = true
                }
            },
            onImportCookies = {
                showAccountSheet = false
                showCookiePasteSheet = true
            }
        )
    }

    if (showCookiePasteSheet) {
        val context = androidx.compose.ui.platform.LocalContext.current
        com.ivor.ivormusic.ui.settings.CookiePasteSheet(
            onDismiss = { showCookiePasteSheet = false },
            onSave = { rawCookies ->
                val normalized = com.ivor.ivormusic.data.YouTubeAuthUtils.normalizeCookieString(rawCookies)
                viewModel.saveSessionCookies(normalized)
                showCookiePasteSheet = false
                android.widget.Toast.makeText(
                    context,
                    "Cookies importés ! Recommandations YouTube Music mises à jour.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixContent(
    songs: List<Song>,
    /**
     * First load with nothing to show yet. The screen still renders in full;
     * only the sections that are actually waiting on data are replaced by
     * placeholders of the same size.
     */
    isInitialLoading: Boolean = false,
    /** Local play history for the "Jump back in" rail. Empty for a new user. */
    recentlyPlayed: List<Song> = emptyList(),
    onRecentClick: (Song) -> Unit = {},
    /**
     * Carousels on a vertically-scrolling page need a way to reach every item
     * without scrolling sideways; this backs the arrow button in each header.
     * The Library tab is that page - it renders the same [songs] list
     * vertically, plus its own recently-played rail.
     */
    onShowAllInLibrary: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onPlayClick: () -> Unit,
    /** Opens Spin, the wheel over this Home. Required: an entry left unwired is a dead card. */
    onSpinClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onImportCookiesClick: (() -> Unit)? = null,
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    excludedFolders: Set<String> = emptySet(),
    manualScan: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    /** Hoisted by HomeScreen: survives tab switches, reachable by the nav bar. */
    listState: LazyListState = rememberLazyListState()
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    val isRefreshing by viewModel.isLoading.collectAsStateWithLifecycle()

    // Do not leave an infinite transition running behind the loaded Home.
    // Reading its value here invalidates this whole composition on every
    // animation frame, which made ordinary vertical scrolling compete with an
    // invisible skeleton pulse. One pulse is still shared while placeholders
    // are actually on screen.
    val skeletonAlpha = if (isInitialLoading) {
        com.ivor.ivormusic.ui.components.rememberSkeletonAlpha()
    } else {
        1f
    }

    ExpressivePullToRefresh(
        // The refresh spinner is for a refresh the user asked for. On first
        // load the placeholders already say "loading", and showing both reads
        // as two competing indicators.
        isRefreshing = isRefreshing && !isInitialLoading,
        onRefresh = { viewModel.refresh(excludedFolders, manualScan) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentPadding = contentPadding
        ) {
            item { 
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                Box(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else -20f
                }.animateContentSize()) {
    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            item { 
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); visible = true }
                Box(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else 40f
                }) {
                    HeroSection(
                        songs = songs,
                        onPlayClick = onPlayClick,
                        onSpinClick = onSpinClick,
                        isDarkMode = isDarkMode,
                        isLoading = isInitialLoading,
                        skeletonAlpha = skeletonAlpha
                    )
                }
            }

            item {
                if (!isInitialLoading && songs.isNotEmpty()) {
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(200); visible = true }
                    Box(Modifier.graphicsLayer {
                        alpha = if (visible) 1f else 0f
                        translationY = if (visible) 0f else 20f
                    }) {
                        MixTrackList(
                            songs = songs,
                            onSongClick = onSongClick,
                            onSongLongPress = onSongLongPress
                        )
                    }
                }
            }
            
            item {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(300); visible = true }
                Column(Modifier.graphicsLayer {
                    alpha = if (visible) 1f else 0f
                    translationY = if (visible) 0f else 30f
                }) {
                    Spacer(modifier = Modifier.height(32.dp))
                    if (isInitialLoading) {
                        HomeCarouselSkeleton(
                            title = stringResource(R.string.home_section_recent_albums),
                            itemWidth = 200.dp,
                            itemHeight = 240.dp,
                            skeletonAlpha = skeletonAlpha
                        )
                    } else {
                        RecentAlbumsSection(
                            songs = songs,
                            onSongClick = onSongClick,
                            onSongLongPress = onSongLongPress,
                            isDarkMode = isDarkMode,
                            onShowAll = onShowAllInLibrary
                        )
                    }
                }
            }
            
            item {
                // No entrance animation on this last section: the staggered
                // fade/slide above only starts its timer when the item scrolls
                // into composition, so this row sat at alpha 0 until scrolled
                // to and popped in late (worse on high DPI). It renders static.
                Column {
                    if (isInitialLoading) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HomeCarouselSkeleton(
                            title = stringResource(R.string.home_section_jump_back_in),
                            itemWidth = 140.dp,
                            itemHeight = 140.dp,
                            captionLines = true,
                            skeletonAlpha = skeletonAlpha
                        )
                    } else if (recentlyPlayed.isNotEmpty()) {
                        // Nothing to resume for a brand new user, and an empty
                        // "Jump back in" is worse than no section at all.
                        Spacer(modifier = Modifier.height(24.dp))
                        JumpBackInSection(
                            songs = recentlyPlayed,
                            onSongClick = onRecentClick,
                            onSongLongPress = onSongLongPress,
                            onShowAll = onShowAllInLibrary
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroSection(
    songs: List<Song>,
    onPlayClick: () -> Unit,
    /** Opens Spin on the mix: the other way to start it, by the wheel. */
    onSpinClick: () -> Unit,
    isDarkMode: Boolean = true,
    /** First load: the artist line has no data yet, the rest of this is static. */
    isLoading: Boolean = false,
    skeletonAlpha: Float = com.ivor.ivormusic.ui.components.rememberSkeletonAlpha()
) {
    val firstSong = songs.firstOrNull()
    val secondSong = songs.getOrNull(1)
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side - Title and subtitle
        Column {
            Text(
                text = stringResource(R.string.your_mix_line1),
                style = MaterialTheme.typography.displayLarge,
                color = textColor
            )
            Text(
                text = stringResource(R.string.your_mix_line2),
                style = MaterialTheme.typography.displayLarge,
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                // Same 20dp band the artist line occupies, so the title above
                // and the layout below do not shift when the names arrive.
                com.ivor.ivormusic.ui.components.SkeletonTextLine(
                    width = 168.dp,
                    height = 14.dp,
                    modifier = Modifier.padding(vertical = 3.dp),
                    alpha = skeletonAlpha
                )
            } else {
                Text(
                    text = (firstSong?.artist.takeIf { !isUnknownArtist(it) } ?: stringResource(R.string.unknown_artist)).let { artist ->
                        (secondSong?.artist.takeIf { !isUnknownArtist(it) })?.let { second -> stringResource(R.string.artists_joined, artist, second) } ?: artist
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        
        // Right side - Play, with Spin as a badge on its lower-right edge: the
        // two ways to start the mix, in order or by the wheel, as one control.
        // It sits the way the incognito badge sits on the profile avatar, and
        // keeps the hero at its own height with Play where it always was.
        Box(modifier = Modifier.padding(top = 32.dp)) {
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                shapes = IconButtonDefaults.shapes(), // Enables shape morphing on press
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize)
                )
            }
            SpinBadge(
                onClick = onSpinClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
            )
        }
    }
}

/**
 * The mix as a plain, scannable track list: the first few songs as upright rows
 * with square artwork, title and artist. Replaces the rotated collage so every
 * cover is a clear, tappable target and nothing is cropped or sliced. A tap
 * plays the song; a long press opens its options.
 */
@Composable
fun MixTrackList(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        songs.take(5).forEach { song ->
            MixTrackRow(
                song = song,
                onClick = { onSongClick(song) },
                onLongClick = onSongLongPress?.let { press -> { press(song) } }
            )
        }
    }
}

@Composable
private fun MixTrackRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .songRowClick(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val imageUrl = song.highResThumbnailUrl ?: song.thumbnailUrl
            val localUri = song.albumArtUri
            if (imageUrl != null || localUri != null) {
                val request = remember(context, localUri, imageUrl) {
                    coil.request.ImageRequest.Builder(context)
                        .data(localUri ?: imageUrl)
                        .crossfade(true)
                        .build()
                }
                coil.compose.SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.takeIf { !isUnknownArtist(it) }
                    ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * [OrganicSongLayout] with the artwork not yet loaded: the same rotated pill
 * and two circles, at the same sizes and offsets, so the collage does not
 * rearrange itself when the songs arrive.
 */
@Composable
private fun OrganicSongLayoutSkeleton(
    skeletonAlpha: Float
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints

        val boxWidth = maxWidth
        val boxHeight = maxHeight

        com.ivor.ivormusic.ui.components.SkeletonBox(
            modifier = Modifier
                .width(260.dp)
                .height(500.dp)
                .align(Alignment.Center)
                .offset(x = 0.dp, y = 30.dp)
                .graphicsLayer { rotationZ = 30f },
            shape = RoundedCornerShape(50),
            alpha = skeletonAlpha
        )

        com.ivor.ivormusic.ui.components.SkeletonBox(
            modifier = Modifier
                .size(boxWidth * 0.29f)
                .align(Alignment.TopStart)
                .offset(x = boxWidth * 0.04f, y = boxHeight * 0.05f)
                .graphicsLayer { rotationZ = -10f },
            shape = CircleShape,
            alpha = skeletonAlpha
        )

        com.ivor.ivormusic.ui.components.SkeletonBox(
            modifier = Modifier
                .size(boxWidth * 0.26f)
                .align(Alignment.BottomEnd)
                .offset(x = boxWidth * (-0.05f), y = 0.dp)
                .graphicsLayer { rotationZ = 5f },
            shape = CircleShape,
            alpha = skeletonAlpha
        )
    }
}

/**
 * Stand-in for one of the home carousels. The section title is real - it never
 * depended on the data - and only the cards are placeholders.
 *
 * A plain Row rather than a carousel: the M3 carousels are driven by an item
 * count and a scroll state that would be thrown away a moment later, and the
 * user cannot meaningfully scroll placeholders anyway.
 */
@Composable
private fun HomeCarouselSkeleton(
    title: String,
    itemWidth: Dp,
    itemHeight: Dp,
    skeletonAlpha: Float,
    /** Quick Picks puts a title/artist under each card; Recent Albums does not. */
    captionLines: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (captionLines) 12.dp else 8.dp)
        ) {
            repeat(4) {
                Column(modifier = Modifier.width(itemWidth)) {
                    com.ivor.ivormusic.ui.components.SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        // Matches the M3 carousel item radius the real cards use
                        shape = RoundedCornerShape(28.dp),
                        alpha = skeletonAlpha
                    )
                    if (captionLines) {
                        Spacer(modifier = Modifier.height(10.dp))
                        com.ivor.ivormusic.ui.components.SkeletonTextLine(
                            width = itemWidth * 0.85f,
                            height = 12.dp,
                            alpha = skeletonAlpha
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        com.ivor.ivormusic.ui.components.SkeletonTextLine(
                            width = itemWidth * 0.55f,
                            height = 10.dp,
                            alpha = skeletonAlpha
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrganicSongLayout(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        // Guard against invalid dimensions during transitions
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) {
            return@BoxWithConstraints
        }
        
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val context = androidx.compose.ui.platform.LocalContext.current
        
        // Circle sizes - percentage of screen width
        val circle1Size = boxWidth * 0.29f  // Top-left circle
        val circle2Size = boxWidth * 0.26f  // Bottom-right circle
        
        // Main: Large Pill shape - rotated diagonally right-to-left
        if (songs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(500.dp)
                    .align(Alignment.Center)
                    .offset(x = 0.dp, y = 30.dp)
                    .graphicsLayer { rotationZ = 30f }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .songRowClick(
                        onClick = { onSongClick(songs[0]) },
                        onLongClick = onSongLongPress?.let { press -> { press(songs[0]) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[0].highResThumbnailUrl ?: songs[0].thumbnailUrl
                val localUri = songs[0].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    val request = remember(context, localUri, imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.SubcomposeAsyncImage(
                        model = request,
                        contentDescription = songs[0].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        // Circle 1 - Top Left (responsive size)
        if (songs.size > 1) {
            Box(
                modifier = Modifier
                    .size(circle1Size)
                    .align(Alignment.TopStart)
                    .offset(x = boxWidth * 0.04f, y = boxHeight * 0.05f)
                    .graphicsLayer { rotationZ = -10f }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .songRowClick(
                        onClick = { onSongClick(songs[1]) },
                        onLongClick = onSongLongPress?.let { press -> { press(songs[1]) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[1].highResThumbnailUrl ?: songs[1].thumbnailUrl
                val localUri = songs[1].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    val request = remember(context, localUri, imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.SubcomposeAsyncImage(
                        model = request,
                        contentDescription = songs[1].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Circle 2 - Bottom Right (responsive size)
        if (songs.size > 2) {
            Box(
                modifier = Modifier
                    .size(circle2Size)
                    .align(Alignment.BottomEnd)
                    .offset(x = boxWidth * (-0.05f), y = boxHeight * (0.0f))
                    .graphicsLayer { rotationZ = 5f }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .songRowClick(
                        onClick = { onSongClick(songs[2]) },
                        onLongClick = onSongLongPress?.let { press -> { press(songs[2]) } }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = songs[2].highResThumbnailUrl ?: songs[2].thumbnailUrl
                val localUri = songs[2].albumArtUri
                
                if (imageUrl != null || localUri != null) {
                    val request = remember(context, localUri, imageUrl) {
                        coil.request.ImageRequest.Builder(context)
                            .data(localUri ?: imageUrl)
                            .crossfade(true)
                            .build()
                    }
                    coil.compose.SubcomposeAsyncImage(
                        model = request,
                        contentDescription = songs[2].title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SongStripCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .songRowClick(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        val imageUrl = song.highResThumbnailUrl ?: song.thumbnailUrl
        val localUri = song.albumArtUri
        
        if (imageUrl != null || localUri != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(context, localUri, imageUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(localUri ?: imageUrl)
                    .crossfade(true)
                    .build()
            }
            coil.compose.SubcomposeAsyncImage(
                model = request,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit = { _, song -> song?.let { onSongClick(it) } },
    /**
     * Play a collection with shuffle mode on. Defaults to playing it in
     * order rather than to a one-time shuffled copy: a host that has not
     * wired a player cannot turn shuffle on, and silently playing a
     * shuffled copy would leave the player's own toggle disagreeing with
     * the queue - the disagreement this parameter exists to end.
     */
    onShuffleQueue: (List<Song>) -> Unit = { songs -> onPlayQueue(songs, null) },
    onPlayRadio: (Song) -> Unit = { song -> onPlayQueue(listOf(song), song) },
    /**
     * Queue a song from a playlist opened through search. Hoisted like
     * [onSongLongPress]: the queue lives in HomeScreen's PlayerViewModel.
     * Null disarms the queue swipe action. The boolean is play-next.
     */
    onEnqueueSong: ((Song, Boolean) -> Unit)? = null,
    onProfileClick: () -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    localOnly: Boolean = false,
    onSongLongPress: ((Song) -> Unit)? = null,
    requestInitialFocus: Boolean = true,
    /** Hoisted by HomeScreen: survives tab switches, reachable by the nav bar. */
    listState: LazyListState = rememberLazyListState()
) {
    var viewedPlaylist by remember { mutableStateOf<com.ivor.ivormusic.data.PlaylistDisplayItem?>(null) }
    var viewedArtist by remember { mutableStateOf<com.ivor.ivormusic.data.ArtistItem?>(null) }

    val currentScreen = when {
        viewedPlaylist != null -> "playlist"
        viewedArtist != null -> "artist"
        else -> "search"
    }

    // Expressive motion physics for screen pushes/pops
    val searchNavSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val searchNavEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // Playlist/album is the deepest layer (search -> artist -> album), so it
    // pops first; backing out of an album returns to the artist page. Only the
    // last step out is previewed: popping an album back to the artist reveals
    // another child, not the search screen underneath.
    com.ivor.ivormusic.ui.components.PredictiveBackStack(
        childOpen = currentScreen != "search",
        onBack = {
            when {
                viewedPlaylist != null -> viewedPlaylist = null
                viewedArtist != null -> viewedArtist = null
            }
        },
        previewable = listOfNotNull(viewedPlaylist, viewedArtist).size == 1,
        background = {
            com.ivor.ivormusic.ui.search.SearchScreen(
                songs = songs,
                onSongClick = onSongClick,
                onPlayQueue = onPlayQueue,
                onPlayRadio = onPlayRadio,
                onArtistClick = { artistItem -> viewedArtist = artistItem },
                onAlbumClick = { albumItem -> viewedPlaylist = albumItem },
                onPlaylistClick = { playlistItem -> viewedPlaylist = playlistItem },
                onProfileClick = onProfileClick,
                onSongLongPress = onSongLongPress,
                contentPadding = contentPadding,
                viewModel = viewModel,
                isDarkMode = isDarkMode,
                localOnly = localOnly,
                requestInitialFocus = requestInitialFocus,
                listState = listState
            )
        }
    ) { committedByGesture ->
    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        label = "SearchNav",
        transitionSpec = {
            val content = when {
                // The finger already performed this exit.
                committedByGesture ->
                    androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
                targetState != "search" ->
                    // Push (Going deeper)
                    (androidx.compose.animation.slideInHorizontally(animationSpec = searchNavSpatialSpec) { width -> width } +
                            androidx.compose.animation.fadeIn(animationSpec = searchNavEffectsSpec)) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(animationSpec = searchNavSpatialSpec) { width -> -width / 3 } +
                                    androidx.compose.animation.fadeOut(animationSpec = searchNavEffectsSpec))
                else ->
                    // Pop (Going back). The search screen is underneath and
                    // staying put, so the child carries the whole movement.
                    androidx.compose.animation.fadeIn(animationSpec = searchNavEffectsSpec) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(animationSpec = searchNavSpatialSpec) { width -> width } +
                                    androidx.compose.animation.fadeOut(animationSpec = searchNavEffectsSpec))
            }
            // The search state of this layer is empty, so the default
            // SizeTransform would animate the container between nothing and
            // full screen and clip the child to it on the way.
            content using androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                androidx.compose.animation.core.snap()
            }
        }
    ) { screen ->
        when (screen) {
            "artist" -> {
                 viewedArtist?.let { artistItem ->
                    com.ivor.ivormusic.ui.artist.ArtistScreen(
                        artistName = artistItem.name,
                        artistId = artistItem.id,
                        songs = emptyList(), // We let the screen fetch songs via viewModel
                        onBack = { viewedArtist = null },
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
                        onSongClick = onSongClick,
                        onAlbumClick = { album, albumSongs ->
                             // Optional: Handle playing album from artist screen
                             onPlayQueue(albumSongs, null)
                        },
                        onOpenAlbum = { albumItem -> viewedPlaylist = albumItem },
                        viewModel = viewModel,
                        onSongLongPress = onSongLongPress,
                        // Another artist replaces this one in place, as in the
                        // Library; a playlist stacks over it and pops back here.
                        onOpenArtist = { name, id ->
                            viewedArtist = com.ivor.ivormusic.data.ArtistItem(id = id, name = name)
                        },
                        onOpenPlaylist = { playlistItem -> viewedPlaylist = playlistItem }
                    )
                }
            }

            "playlist" -> {
                 viewedPlaylist?.let { playlist ->
                     com.ivor.ivormusic.ui.library.PlaylistDetailScreen(
                        playlist = playlist,
                        onBack = { viewedPlaylist = null },
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
                        viewModel = viewModel,
                        onSongLongPress = onSongLongPress,
                        onEnqueueSong = onEnqueueSong,
                        // Search opens other people's playlists; the import
                        // flow belongs to the Library's local ones.
                        onAddSongsRequest = null
                    )
                }
            }

            // Search lives underneath now; this layer is empty over it, and
            // full size so both states measure the same.
            else -> Spacer(Modifier.fillMaxSize())
        }
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentAlbumsSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    isDarkMode: Boolean = true,
    onShowAll: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // We need at least one large, one medium, one small for full effect,
    // but the component handles fewer items gracefully.
    val state = rememberCarouselState { songs.size }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = stringResource(R.string.home_section_recent_albums), onShowAll = onShowAll)

        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 200.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) { index ->
            val song = songs[index]
            Box(
                modifier = Modifier
                    // 28dp is the M3 carousel item radius; shapes.medium (12dp)
                    // made these read as generic cards.
                    .maskClip(RoundedCornerShape(28.dp))
                    .background(cardBgColor)
                    .songRowClick(
                        onClick = { onSongClick(song) },
                        onLongClick = onSongLongPress?.let { press -> { press(song) } }
                    )
            ) {
                if (song.albumArtUri != null || song.thumbnailUrl != null) {
                    AsyncImage(
                        model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section header for a horizontal shelf: the title, plus the arrow button
 * Material requires so every item is reachable without scrolling sideways.
 *
 * [onShowAll] is nullable because the arrow is only honest when there is
 * somewhere fuller to go.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeSectionHeader(
    title: String,
    onShowAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onShowAll != null) {
            // The maquette's "Tout voir" text link rather than an arrow button:
            // a TextButton still carries its own 48dp touch target.
            TextButton(onClick = onShowAll) {
                Text(
                    text = stringResource(R.string.action_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Square artwork edge, and the rail's item width. */
private val ARTWORK_SIZE = 140.dp

/** Space between artwork and the first caption line. */
private val CAPTION_GAP = 8.dp

/**
 * The user's own play history, newest first - the "resume what you were doing"
 * rail. Distinct from the mix above it on purpose: this is the one section on
 * the screen that is not a recommendation.
 *
 * A LazyRow of cards, not a carousel. Both M3 carousel layouts mask their items
 * to a shrinking rect at the container edges, and that mask covers the whole
 * item - so captions under the artwork get sliced mid-word ("Let Down" renders
 * as "et Down"). Material's own guidance points here: if carousel items need
 * real text, use a series of cards instead. Recent Albums above keeps the
 * carousel because its items are pure artwork with nothing to clip.
 */
@Composable
fun JumpBackInSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onShowAll: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(title = stringResource(R.string.home_section_jump_back_in), onShowAll = onShowAll)

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs, key = { "recent_${it.id}" }) { song ->
                // No clip on this column: a rounded clip here is what rounds
                // the corners off the caption text underneath the artwork.
                // Only the artwork itself gets a shape.
                Column(
                    modifier = Modifier
                        .width(ARTWORK_SIZE)
                        .songRowClick(
                            onClick = { onSongClick(song) },
                            onLongClick = onSongLongPress?.let { press -> { press(song) } }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(ARTWORK_SIZE)
                            .clip(RoundedCornerShape(28.dp))
                            .background(cardBgColor)
                    ) {
                        if (song.albumArtUri != null || song.thumbnailUrl != null) {
                            AsyncImage(
                                model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CAPTION_GAP))

                    // No fixed row height: the column wraps its content, so the
                    // captions grow with the user's font scale instead of being
                    // cut off by a hardcoded carousel height.
                    Text(
                        text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    Text(
                        text = song.artist.takeIf { !isUnknownArtist(it) } ?: stringResource(R.string.unknown_artist),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}

/**
 * Interleave two source feeds (YouTube recommendations and SoundCloud charts)
 * so neither owns the top of the home. De-duplicates by id, keeping first seen.
 */
private fun interleaveSources(a: List<Song>, b: List<Song>): List<Song> {
    val out = ArrayList<Song>(a.size + b.size)
    val seen = HashSet<String>()
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        if (i < a.size) {
            val s = a[i++]
            if (seen.add(s.id)) out.add(s)
        }
        if (j < b.size) {
            val s = b[j++]
            if (seen.add(s.id)) out.add(s)
        }
    }
    return out
}
