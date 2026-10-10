package com.ivor.ivormusic

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivor.ivormusic.ui.components.hiddenFraction
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.hasWatchableVideo
import com.ivor.ivormusic.data.toVideoItem
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.home.HomeScreen
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.theme.IvorMusicTheme
import com.ivor.ivormusic.ui.theme.PaletteStyle
import com.ivor.ivormusic.ui.theme.ThemeViewModel
import com.ivor.ivormusic.data.MotionArtworkQuality
import com.ivor.ivormusic.data.PlayerStyle
import androidx.compose.ui.unit.dp


import androidx.compose.foundation.isSystemInDarkTheme
import com.ivor.ivormusic.ui.theme.ThemeMode

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.ivor.ivormusic.ui.onboarding.OnboardingScreen
import com.ivor.ivormusic.ui.share.PendingSharedLink
import com.ivor.ivormusic.ui.share.SharedLinkHandler
import com.ivor.ivormusic.ui.share.sharedLinkText

/**
 * Height the floating navigation bar occupies at the bottom of the Home
 * screen, above the system navigation inset: the toolbar itself plus the 20dp
 * it is padded away from the inset.
 *
 * Here rather than in `HomeScreen` because the thing that needs it is the
 * video overlay, which is drawn above the NavHost and cannot see inside the
 * screen that draws the bar.
 */
private val EXPRESSIVE_NAV_BAR_RESERVE = 84.dp

/** Material 3's standard navigation container height, excluding system insets. */
private val NON_EXPRESSIVE_NAV_BAR_RESERVE = 80.dp

/** Height the collapsed music player occupies above the navigation bar. */
private val MUSIC_PILL_RESERVE = 88.dp


class MainActivity : ComponentActivity() {

    // A YouTube link shared or opened into Koda, picked up by SharedLinkHandler
    // inside the composition. Snapshot state so a link arriving while the app is
    // already running reaches the UI without restarting anything.
    private var pendingSharedLink by androidx.compose.runtime.mutableStateOf<PendingSharedLink?>(null)
    private var sharedLinkCounter = 0L

    /**
     * The daily time limit's lock state, evaluated by [tickAppTime] every 30
     * seconds while foregrounded (and once at start). Snapshot state so the
     * overlay in MusicApp reacts without any flow plumbing; the underlying
     * decision always fresh-reads preferences, so budget changes apply on the
     * next tick.
     */
    private var appTimeLocked by androidx.compose.runtime.mutableStateOf(false)
    private var foregroundedAtMs = 0L
    private var appTimeTicker: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        takeSharedLink(intent)

        // Remove splash instantly when ready — the AVD entrance animation is the show
        splashScreen.setOnExitAnimationListener { it.remove() }

        // The app is portrait-only, like YouTube: rotating the device must not
        // rotate the app UI. The only exception is fullscreen video playback,
        // which temporarily requests landscape from VideoPlayerContent and
        // restores portrait when it exits.
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        enableEdgeToEdge()
        // Treat camera cutouts as usable edge-to-edge space everywhere. The
        // old video-only toggle restored DEFAULT on exit, which could
        // letterbox the rest of the app for the remainder of the session.
        // Controls still handle status/navigation bars independently; only
        // the notch-specific exclusion is deliberately ignored.
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val amoledTheme by themeViewModel.amoledTheme.collectAsStateWithLifecycle()
            val colorPalette by themeViewModel.colorPalette.collectAsStateWithLifecycle()
            val paletteStyle by themeViewModel.paletteStyle.collectAsStateWithLifecycle()
            val appIcon by themeViewModel.appIcon.collectAsStateWithLifecycle()
            val loadLocalSongs by themeViewModel.loadLocalSongs.collectAsStateWithLifecycle()
            val ambientBackground by themeViewModel.ambientBackground.collectAsStateWithLifecycle()
            val motionArtwork by themeViewModel.motionArtwork.collectAsStateWithLifecycle()
            val motionArtworkWifiOnly by themeViewModel.motionArtworkWifiOnly.collectAsStateWithLifecycle()
            val motionArtworkQuality by themeViewModel.motionArtworkQuality.collectAsStateWithLifecycle()
            val waveformSeekBar by themeViewModel.waveformSeekBar.collectAsStateWithLifecycle()
            val playerArtworkColors by themeViewModel.playerArtworkColors.collectAsStateWithLifecycle()
            val playerStyle by themeViewModel.playerStyle.collectAsStateWithLifecycle()
            val contentSource by themeViewModel.contentSource.collectAsStateWithLifecycle()
            val mixSourcesOnHome by themeViewModel.mixSourcesOnHome.collectAsStateWithLifecycle()
            val showSourceBadge by themeViewModel.showSourceBadge.collectAsStateWithLifecycle()
            val saveMusicHistory by themeViewModel.saveMusicHistory.collectAsStateWithLifecycle()
            val liveDownloadUpdates by themeViewModel.liveDownloadUpdates.collectAsStateWithLifecycle()
            val livePlaybackUpdates by themeViewModel.livePlaybackUpdates.collectAsStateWithLifecycle()
            val showRecentSearches by themeViewModel.showRecentSearches.collectAsStateWithLifecycle()
            val playlistSwipeEnabled by themeViewModel.playlistSwipeEnabled.collectAsStateWithLifecycle()
            val playlistSwipeStartAction by themeViewModel.playlistSwipeStartAction.collectAsStateWithLifecycle()
            val playlistSwipeEndAction by themeViewModel.playlistSwipeEndAction.collectAsStateWithLifecycle()
            val musicQualityWifi by themeViewModel.musicQualityWifi.collectAsStateWithLifecycle()
            val musicQualityMobile by themeViewModel.musicQualityMobile.collectAsStateWithLifecycle()
            val spotlightHome by themeViewModel.spotlightHome.collectAsStateWithLifecycle()
            val uiScale by themeViewModel.uiScale.collectAsStateWithLifecycle()
            val sponsorBlockEnabled by themeViewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
            val sponsorBlockActions by themeViewModel.sponsorBlockActions.collectAsStateWithLifecycle()
            val sponsorBlockShowOnSeekBar by
                themeViewModel.sponsorBlockShowOnSeekBar.collectAsStateWithLifecycle()
            val sponsorBlockNotice by themeViewModel.sponsorBlockNotice.collectAsStateWithLifecycle()
            val sponsorBlockMinDurationMs by
                themeViewModel.sponsorBlockMinDurationMs.collectAsStateWithLifecycle()
            val nonExpressiveNavigationBar by
                themeViewModel.nonExpressiveNavigationBar.collectAsStateWithLifecycle()
            val excludedFolders by themeViewModel.excludedFolders.collectAsStateWithLifecycle()
            val oemFixEnabled by themeViewModel.oemFixEnabled.collectAsStateWithLifecycle()
            val manualScanEnabled by themeViewModel.manualScanEnabled.collectAsStateWithLifecycle()
            val privateDownloadsEnabled by
                themeViewModel.privateDownloadsEnabled.collectAsStateWithLifecycle()
            val onboardingCompleted by themeViewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val localOnlyMode by themeViewModel.localOnlyMode.collectAsStateWithLifecycle()
            
            val cacheEnabled by themeViewModel.cacheEnabled.collectAsStateWithLifecycle()
            val playbackPreloadEnabled by themeViewModel.playbackPreloadEnabled.collectAsStateWithLifecycle()
            val maxCacheSizeMb by themeViewModel.maxCacheSizeMb.collectAsStateWithLifecycle()
            val currentCacheSize by themeViewModel.currentCacheSizeBytes.collectAsStateWithLifecycle()
            val autoLoadQueue by themeViewModel.autoLoadQueue.collectAsStateWithLifecycle()
            val crossfadeEnabled by themeViewModel.crossfadeEnabled.collectAsStateWithLifecycle()
            val crossfadeAuto by themeViewModel.crossfadeAuto.collectAsStateWithLifecycle()
            val crossfadeDurationMs by themeViewModel.crossfadeDurationMs.collectAsStateWithLifecycle()
            val normalizeVolume by themeViewModel.normalizeVolume.collectAsStateWithLifecycle()
            val hapticsLevel by themeViewModel.hapticsLevel.collectAsStateWithLifecycle()
            
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = remember(themeMode, isSystemDark) {
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            }
            
            IvorMusicTheme(
                darkTheme = isDarkTheme,
                colorPalette = colorPalette,
                amoledDark = amoledTheme,
                uiScale = uiScale,
                paletteStyle = paletteStyle
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MusicApp(
                        pendingSharedLink = pendingSharedLink,
                        appTimeLocked = appTimeLocked,
                        currentThemeMode = themeMode,
                        onThemeModeChange = { themeViewModel.setThemeMode(it) },
                        amoledTheme = amoledTheme,
                        onAmoledThemeToggle = { themeViewModel.setAmoledTheme(it) },
                        colorPalette = colorPalette,
                        onColorPaletteChange = { themeViewModel.setColorPalette(it) },
                        paletteStyle = paletteStyle,
                        onPaletteStyleChange = { themeViewModel.setPaletteStyle(it) },
                        appIcon = appIcon,
                        isDarkMode = isDarkTheme, // Derived for compatibility
                        onThemeToggle = { isDark ->
                            themeViewModel.setThemeMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                        },
                        loadLocalSongs = loadLocalSongs,
                        onLoadLocalSongsToggle = { themeViewModel.setLoadLocalSongs(it) },
                        ambientBackground = ambientBackground,
                        onAmbientBackgroundToggle = { themeViewModel.setAmbientBackground(it) },
                        playerArtworkColors = playerArtworkColors,
                        onPlayerArtworkColorsToggle = { themeViewModel.setPlayerArtworkColors(it) },
                        motionArtwork = motionArtwork,
                        onMotionArtworkToggle = themeViewModel::setMotionArtwork,
                        motionArtworkWifiOnly = motionArtworkWifiOnly,
                        onMotionArtworkWifiOnlyToggle = themeViewModel::setMotionArtworkWifiOnly,
                        motionArtworkQuality = motionArtworkQuality,
                        onMotionArtworkQualityChange = themeViewModel::setMotionArtworkQuality,
                        waveformSeekBar = waveformSeekBar,
                        onWaveformSeekBarToggle = themeViewModel::setWaveformSeekBar,
                        onHomeModeToggleEnabledChange = { themeViewModel.setHomeModeToggleEnabled(it) },
                        spotlightHome = spotlightHome,
                        uiScale = uiScale,
                        onUiScaleChange = { themeViewModel.setUiScale(it) },
                        sponsorBlockEnabled = sponsorBlockEnabled,
                        onSponsorBlockEnabledToggle = { themeViewModel.setSponsorBlockEnabled(it) },
                        sponsorBlockActions = sponsorBlockActions,
                        onSponsorBlockActionChange = { category, action ->
                            themeViewModel.setSponsorBlockAction(category, action)
                        },
                        onResetSponsorBlockActions = { themeViewModel.resetSponsorBlockActions() },
                        sponsorBlockShowOnSeekBar = sponsorBlockShowOnSeekBar,
                        onSponsorBlockShowOnSeekBarToggle = {
                            themeViewModel.setSponsorBlockShowOnSeekBar(it)
                        },
                        sponsorBlockNotice = sponsorBlockNotice,
                        onSponsorBlockNoticeToggle = { themeViewModel.setSponsorBlockNotice(it) },
                        sponsorBlockMinDurationMs = sponsorBlockMinDurationMs,
                        onSponsorBlockMinDurationChange = {
                            themeViewModel.setSponsorBlockMinDurationMs(it)
                        },
                        onSpotlightHomeToggle = { themeViewModel.setSpotlightHome(it) },
                        nonExpressiveNavigationBar = nonExpressiveNavigationBar,
                        onNonExpressiveNavigationBarToggle = {
                            themeViewModel.setNonExpressiveNavigationBar(it)
                        },
                        playerStyle = playerStyle,
                        onPlayerStyleChange = { themeViewModel.setPlayerStyle(it) },
                        contentSource = contentSource,
                        onContentSourceChange = { themeViewModel.setContentSource(it) },
                        mixSourcesOnHome = mixSourcesOnHome,
                        onMixSourcesOnHomeToggle = { themeViewModel.setMixSourcesOnHome(it) },
                        showSourceBadge = showSourceBadge,
                        onShowSourceBadgeToggle = { themeViewModel.setShowSourceBadge(it) },
                        saveMusicHistory = saveMusicHistory,
                        onSaveMusicHistoryToggle = { themeViewModel.setSaveMusicHistory(it) },
                        liveDownloadUpdates = liveDownloadUpdates,
                        onLiveDownloadUpdatesToggle = { themeViewModel.setLiveDownloadUpdates(it) },
                        livePlaybackUpdates = livePlaybackUpdates,
                        onLivePlaybackUpdatesToggle = { themeViewModel.setLivePlaybackUpdates(it) },
                        showRecentSearches = showRecentSearches,
                        onShowRecentSearchesToggle = { themeViewModel.setShowRecentSearches(it) },
                        playlistSwipeEnabled = playlistSwipeEnabled,
                        onPlaylistSwipeEnabledToggle = { themeViewModel.setPlaylistSwipeEnabled(it) },
                        playlistSwipeStartAction = playlistSwipeStartAction,
                        onPlaylistSwipeStartActionChange = { themeViewModel.setPlaylistSwipeStartAction(it) },
                        playlistSwipeEndAction = playlistSwipeEndAction,
                        onPlaylistSwipeEndActionChange = { themeViewModel.setPlaylistSwipeEndAction(it) },
                        musicQualityWifi = musicQualityWifi,
                        onMusicQualityWifiChange = { themeViewModel.setMusicQualityWifi(it) },
                        musicQualityMobile = musicQualityMobile,
                        onMusicQualityMobileChange = { themeViewModel.setMusicQualityMobile(it) },
                        excludedFolders = excludedFolders,
                        onAddExcludedFolder = { themeViewModel.addExcludedFolder(it) },
                        onRemoveExcludedFolder = { themeViewModel.removeExcludedFolder(it) },
                        oemFixEnabled = oemFixEnabled,
                        onOemFixEnabledToggle = { themeViewModel.setOemFixEnabled(it) },
                        manualScanEnabled = manualScanEnabled,
                        onManualScanEnabledToggle = { themeViewModel.setManualScanEnabled(it) },
                        privateDownloadsEnabled = privateDownloadsEnabled,
                        onPrivateDownloadsEnabledToggle = {
                            themeViewModel.setPrivateDownloadsEnabled(it)
                        },
                        cacheEnabled = cacheEnabled,
                        onCacheEnabledToggle = { themeViewModel.setCacheEnabled(it) },
                        playbackPreloadEnabled = playbackPreloadEnabled,
                        onPlaybackPreloadEnabledToggle = { themeViewModel.setPlaybackPreloadEnabled(it) },
                        maxCacheSizeMb = maxCacheSizeMb,
                        onMaxCacheSizeMbChange = { themeViewModel.setMaxCacheSizeMb(it) },
                        currentCacheSize = currentCacheSize,
                        onClearCacheClick = { themeViewModel.clearCacheAction() },
                        autoLoadQueue = autoLoadQueue,
                        onAutoLoadQueueToggle = { themeViewModel.setAutoLoadQueue(it) },
                        crossfadeEnabled = crossfadeEnabled,
                        onCrossfadeEnabledToggle = { themeViewModel.setCrossfadeEnabled(it) },
                        crossfadeAuto = crossfadeAuto,
                        onCrossfadeAutoChange = { themeViewModel.setCrossfadeAuto(it) },
                        crossfadeDurationMs = crossfadeDurationMs,
                        onCrossfadeDurationChange = { themeViewModel.setCrossfadeDuration(it) },
                        normalizeVolume = normalizeVolume,
                        onNormalizeVolumeToggle = { themeViewModel.setNormalizeVolume(it) },
                        hapticsLevel = hapticsLevel,
                        onHapticsLevelChange = { themeViewModel.setHapticsLevel(it) },
                        onboardingCompleted = onboardingCompleted,
                        onOnboardingCompleted = { themeViewModel.setOnboardingCompleted(it) },
                        localOnlyMode = localOnlyMode,
                        onLocalOnlyModeToggle = { themeViewModel.setLocalOnlyMode(it) }
                    )
                }
            }
        }
    }

    /**
     * Daily time limit bookkeeping. STARTED is the whole definition of
     * "using Koda" here: foregrounded, on screen, in front of the user.
     * PiP counts as background - the video is a floating window, not app use.
     */
    override fun onStart() {
        super.onStart()
        foregroundedAtMs = android.os.SystemClock.elapsedRealtime()
        tickAppTime()
        appTimeTicker = lifecycleScope.launch {
            while (isActive) {
                delay(30_000L)
                chargeForegroundTime()
                tickAppTime()
            }
        }
    }

    override fun onStop() {
        appTimeTicker?.cancel()
        appTimeTicker = null
        chargeForegroundTime()
        super.onStop()
    }

    /** Flush the time since the last marker into today's total. */
    private fun chargeForegroundTime() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (foregroundedAtMs == 0L || appTimeLocked) {
            // Locked sessions are deliberately uncounted: sitting on the lock
            // screen must not eat the budget, and the marker still moves so a
            // midnight rollover or budget raise resumes cleanly.
            foregroundedAtMs = now
            return
        }
        val prefs = com.ivor.ivormusic.data.ThemePreferences(applicationContext)
        val shouldTrack = prefs.isTimeLimitEnabled() &&
            com.ivor.ivormusic.data.AppTimeLimit.budgetMinutesForToday(
                com.ivor.ivormusic.data.AppTimeLimit.parseBudgets(
                    prefs.getTimeLimitBudgets()
                )
            ) > 0
        if (shouldTrack) {
            com.ivor.ivormusic.data.AppTimeLimit.addForegroundMillis(
                this,
                now - foregroundedAtMs
            )
        }
        foregroundedAtMs = now
    }

    private fun tickAppTime() {
        val prefs = com.ivor.ivormusic.data.ThemePreferences(applicationContext)
        appTimeLocked = com.ivor.ivormusic.data.AppTimeLimit.isLocked(
            this,
            prefs.isTimeLimitEnabled(),
            prefs.getTimeLimitBudgets()
        )
    }

    /**
     * A link shared into Koda while it was already running is delivered here
     * rather than through a fresh onCreate, thanks to singleTop.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSharedLink(intent)
    }

    /**
     * Pick up the YouTube link an intent carries, if it has one, and neutralize
     * the intent so it cannot fire twice - the activity is recreated on theme
     * and locale changes, and would otherwise replay the same link each time.
     */
    private fun takeSharedLink(intent: Intent?) {
        if (intent == null) return
        val navTarget = intent.getStringExtra("navigate_to")
        if (navTarget != null) {
            neutralize(intent)
            pendingNavigation.value = navTarget
            return
        }
        val text = intent.sharedLinkText() ?: return
        neutralize(intent)
        pendingSharedLink = PendingSharedLink(text, ++sharedLinkCounter)
    }

    /**
     * Strip an intent of what made it actionable, so it cannot fire twice. The
     * activity is recreated on theme and locale changes and would otherwise
     * replay the same hand-off each time.
     */
    private fun neutralize(intent: Intent) {
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        intent.type = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.removeExtra("navigate_to")
    }

    companion object {
        var pendingNavigation = androidx.compose.runtime.mutableStateOf<String?>(null)
    }
}

@Composable
fun MusicApp(
    pendingSharedLink: PendingSharedLink?,
    /** A video file handed in by another app, waiting to be played. */

    /**
     * The daily time limit's lock. Owned by the activity's ticker rather
     * than a flow: the decision fresh-reads preferences every tick, and the
     * only consumer is this overlay.
     */
    appTimeLocked: Boolean = false,
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    amoledTheme: Boolean,
    onAmoledThemeToggle: (Boolean) -> Unit,
    colorPalette: String,
    onColorPaletteChange: (String) -> Unit,
    paletteStyle: PaletteStyle,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    appIcon: String = com.ivor.ivormusic.data.ThemePreferences.DEFAULT_APP_ICON,
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    playerArtworkColors: Boolean,
    onPlayerArtworkColorsToggle: (Boolean) -> Unit,
    motionArtwork: Boolean,
    onMotionArtworkToggle: (Boolean) -> Unit,
    motionArtworkWifiOnly: Boolean,
    onMotionArtworkWifiOnlyToggle: (Boolean) -> Unit,
    motionArtworkQuality: MotionArtworkQuality,
    onMotionArtworkQualityChange: (MotionArtworkQuality) -> Unit,
    waveformSeekBar: Boolean,
    onWaveformSeekBarToggle: (Boolean) -> Unit,
    onHomeModeToggleEnabledChange: (Boolean) -> Unit,
    spotlightHome: Boolean,
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    sponsorBlockEnabled: Boolean,
    onSponsorBlockEnabledToggle: (Boolean) -> Unit,
    sponsorBlockActions: Map<com.ivor.ivormusic.data.SponsorCategory, com.ivor.ivormusic.data.SegmentAction>,
    onSponsorBlockActionChange: (com.ivor.ivormusic.data.SponsorCategory, com.ivor.ivormusic.data.SegmentAction) -> Unit,
    onResetSponsorBlockActions: () -> Unit,
    sponsorBlockShowOnSeekBar: Boolean,
    onSponsorBlockShowOnSeekBarToggle: (Boolean) -> Unit,
    sponsorBlockNotice: Boolean,
    onSponsorBlockNoticeToggle: (Boolean) -> Unit,
    sponsorBlockMinDurationMs: Long,
    onSponsorBlockMinDurationChange: (Long) -> Unit,
    onSpotlightHomeToggle: (Boolean) -> Unit,
    nonExpressiveNavigationBar: Boolean,
    onNonExpressiveNavigationBarToggle: (Boolean) -> Unit,
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    contentSource: com.ivor.ivormusic.data.ContentSource,
    onContentSourceChange: (com.ivor.ivormusic.data.ContentSource) -> Unit,
    mixSourcesOnHome: Boolean,
    onMixSourcesOnHomeToggle: (Boolean) -> Unit,
    showSourceBadge: Boolean,
    onShowSourceBadgeToggle: (Boolean) -> Unit,
    saveMusicHistory: Boolean,
    onSaveMusicHistoryToggle: (Boolean) -> Unit,
    liveDownloadUpdates: Boolean,
    onLiveDownloadUpdatesToggle: (Boolean) -> Unit,
    livePlaybackUpdates: Boolean,
    onLivePlaybackUpdatesToggle: (Boolean) -> Unit,
    showRecentSearches: Boolean,
    onShowRecentSearchesToggle: (Boolean) -> Unit,
    playlistSwipeEnabled: Boolean,
    onPlaylistSwipeEnabledToggle: (Boolean) -> Unit,
    playlistSwipeStartAction: String,
    onPlaylistSwipeStartActionChange: (String) -> Unit,
    playlistSwipeEndAction: String,
    onPlaylistSwipeEndActionChange: (String) -> Unit,
    musicQualityWifi: String,
    onMusicQualityWifiChange: (String) -> Unit,
    musicQualityMobile: String,
    onMusicQualityMobileChange: (String) -> Unit,
    excludedFolders: Set<String>,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    cacheEnabled: Boolean,
    onCacheEnabledToggle: (Boolean) -> Unit,
    playbackPreloadEnabled: Boolean,
    onPlaybackPreloadEnabledToggle: (Boolean) -> Unit,
    maxCacheSizeMb: Long,
    onMaxCacheSizeMbChange: (Long) -> Unit,
    currentCacheSize: Long,
    onClearCacheClick: () -> Unit,
    autoLoadQueue: Boolean,
    onAutoLoadQueueToggle: (Boolean) -> Unit,
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit,
    crossfadeAuto: Boolean,
    onCrossfadeAutoChange: (Boolean) -> Unit,
    crossfadeDurationMs: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
    normalizeVolume: Boolean,
    onNormalizeVolumeToggle: (Boolean) -> Unit,
    hapticsLevel: String,
    onHapticsLevelChange: (String) -> Unit,
    oemFixEnabled: Boolean,
    onOemFixEnabledToggle: (Boolean) -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit,
    privateDownloadsEnabled: Boolean,
    onPrivateDownloadsEnabledToggle: (Boolean) -> Unit,
    onboardingCompleted: Boolean,
    onOnboardingCompleted: (Boolean) -> Unit,
    localOnlyMode: Boolean,
    onLocalOnlyModeToggle: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val pendingNav by MainActivity.pendingNavigation
    LaunchedEffect(pendingNav) {
        val target = pendingNav
        if (target != null) {
            MainActivity.pendingNavigation.value = null
            navController.navigate(target)
        }
    }
    // Scope the player VM to the ViewModelStore so it survives configuration
    // changes and onCleared() actually runs (releasing the MediaController).
    // Application context is used so the Activity isn't retained.
    val playerViewModel: PlayerViewModel = viewModel {
        PlayerViewModel(context.applicationContext)
    }
    val homeViewModel: HomeViewModel = viewModel()

    val isMusicPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val pendingSongDownload by playerViewModel.pendingSongDownload.collectAsStateWithLifecycle()

    val musicPillVisible = playerViewModel.currentSong.collectAsStateWithLifecycle().value != null

    // Opens YouTube links shared into the app.
    SharedLinkHandler(
        pendingLink = pendingSharedLink,
        enabled = onboardingCompleted,
        localOnlyMode = localOnlyMode,
        homeViewModel = homeViewModel,
        playerViewModel = playerViewModel,
        onNavigateHome = {
            navController.navigate("home") {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) "home" else "onboarding"
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                    spotlightHome = spotlightHome,
                    onSpotlightHomeChange = onSpotlightHomeToggle,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    crossfadeEnabled = crossfadeEnabled,
                    onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                    manualScanEnabled = manualScanEnabled,
                    onManualScanEnabledToggle = onManualScanEnabledToggle,
                    onFinish = {
                        onOnboardingCompleted(true)
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onSongClick = { song ->
                        playerViewModel.playSong(song)
                    },
                    playerViewModel = playerViewModel,
                    viewModel = homeViewModel,
                    isDarkMode = isDarkMode,
                    onThemeToggle = onThemeToggle,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToDownloads = { navController.navigate("downloads") },
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToUpdate = { navController.navigate("update") },
                    loadLocalSongs = loadLocalSongs,
                    excludedFolders = excludedFolders,
                    ambientBackground = ambientBackground,
                    playerArtworkColors = playerArtworkColors,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    manualScan = manualScanEnabled,
                    localOnly = localOnlyMode,
                    spotlightHome = spotlightHome,
                    nonExpressiveNavigationBar = nonExpressiveNavigationBar
                )
            }
            composable(
                route = "settings",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.settings.SettingsScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    amoledTheme = amoledTheme,
                    onAmoledThemeToggle = onAmoledThemeToggle,
                    colorPalette = colorPalette,
                    onNavigateToColorPalette = { navController.navigate("color_palette") },
                    paletteStyle = paletteStyle,
                    onNavigateToNotInterested = { navController.navigate("not_interested") },
                    onNavigateToBackup = { navController.navigate("backup") },
                    onNavigateToReportBug = { navController.navigate("report") },
                    onNavigateToTimeLimit = { navController.navigate("app_time_limit") },
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                    playerArtworkColors = playerArtworkColors,
                    onPlayerArtworkColorsToggle = onPlayerArtworkColorsToggle,
                    motionArtwork = motionArtwork,
                    onMotionArtworkToggle = onMotionArtworkToggle,
                    motionArtworkWifiOnly = motionArtworkWifiOnly,
                    onMotionArtworkWifiOnlyToggle = onMotionArtworkWifiOnlyToggle,
                    motionArtworkQuality = motionArtworkQuality,
                    onMotionArtworkQualityChange = onMotionArtworkQualityChange,
                    waveformSeekBar = waveformSeekBar,
                    onWaveformSeekBarToggle = onWaveformSeekBarToggle,
                    spotlightHome = spotlightHome,
                    onSpotlightHomeToggle = onSpotlightHomeToggle,
                    nonExpressiveNavigationBar = nonExpressiveNavigationBar,
                    onNonExpressiveNavigationBarToggle =
                        onNonExpressiveNavigationBarToggle,
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    contentSource = contentSource,
                    onContentSourceChange = onContentSourceChange,
                    mixSourcesOnHome = mixSourcesOnHome,
                    onMixSourcesOnHomeToggle = onMixSourcesOnHomeToggle,
                    showSourceBadge = showSourceBadge,
                    onShowSourceBadgeToggle = onShowSourceBadgeToggle,
                    saveMusicHistory = saveMusicHistory,
                    onSaveMusicHistoryToggle = onSaveMusicHistoryToggle,
                    liveDownloadUpdates = liveDownloadUpdates,
                    onLiveDownloadUpdatesToggle = onLiveDownloadUpdatesToggle,
                    livePlaybackUpdates = livePlaybackUpdates,
                    onLivePlaybackUpdatesToggle = onLivePlaybackUpdatesToggle,
                    showRecentSearches = showRecentSearches,
                    onShowRecentSearchesToggle = onShowRecentSearchesToggle,
                    playlistSwipeEnabled = playlistSwipeEnabled,
                    onPlaylistSwipeEnabledToggle = onPlaylistSwipeEnabledToggle,
                    playlistSwipeStartAction = playlistSwipeStartAction,
                    onPlaylistSwipeStartActionChange = onPlaylistSwipeStartActionChange,
                    playlistSwipeEndAction = playlistSwipeEndAction,
                    onPlaylistSwipeEndActionChange = onPlaylistSwipeEndActionChange,
                    musicQualityWifi = musicQualityWifi,
                    onMusicQualityWifiChange = onMusicQualityWifiChange,
                    musicQualityMobile = musicQualityMobile,
                    onMusicQualityMobileChange = onMusicQualityMobileChange,
                    excludedFolders = excludedFolders,
                    onAddExcludedFolder = onAddExcludedFolder,
                    onRemoveExcludedFolder = onRemoveExcludedFolder,
                    homeViewModel = homeViewModel,
                    onLogoutClick = { 
                        homeViewModel.logout()
                    },
                    onBackClick = { navController.popBackStack() },
                    cacheEnabled = cacheEnabled,
                    onCacheEnabledToggle = onCacheEnabledToggle,
                    playbackPreloadEnabled = playbackPreloadEnabled,
                    onPlaybackPreloadEnabledToggle = onPlaybackPreloadEnabledToggle,
                    maxCacheSizeMb = maxCacheSizeMb,
                    onMaxCacheSizeMbChange = onMaxCacheSizeMbChange,
                    currentCacheSize = currentCacheSize,
                    onClearCacheClick = onClearCacheClick,
                    autoLoadQueue = autoLoadQueue,
                    onAutoLoadQueueToggle = onAutoLoadQueueToggle,
                    crossfadeEnabled = crossfadeEnabled,
                    onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                    crossfadeAuto = crossfadeAuto,
                    onCrossfadeAutoChange = onCrossfadeAutoChange,
                    crossfadeDurationMs = crossfadeDurationMs,
                    onCrossfadeDurationChange = onCrossfadeDurationChange,
                    normalizeVolume = normalizeVolume,
                    onNormalizeVolumeToggle = onNormalizeVolumeToggle,
                    hapticsLevel = hapticsLevel,
                    onHapticsLevelChange = onHapticsLevelChange,
                    oemFixEnabled = oemFixEnabled,
                    onOemFixEnabledToggle = onOemFixEnabledToggle,
                    manualScanEnabled = manualScanEnabled,
                    onManualScanEnabledToggle = onManualScanEnabledToggle,
                    privateDownloadsEnabled = privateDownloadsEnabled,
                    onPrivateDownloadsEnabledToggle = onPrivateDownloadsEnabledToggle,
                    onNavigateToUpdate = { navController.navigate("update") },
                    localOnlyMode = localOnlyMode,
                    onLocalOnlyModeToggle = onLocalOnlyModeToggle,
                    uiScale = uiScale,
                    onUiScaleChange = onUiScaleChange,
                    sponsorBlockEnabled = sponsorBlockEnabled,
                    onSponsorBlockEnabledToggle = onSponsorBlockEnabledToggle,
                    sponsorBlockActions = sponsorBlockActions,
                    onSponsorBlockActionChange = onSponsorBlockActionChange,
                    onResetSponsorBlockActions = onResetSponsorBlockActions,
                    sponsorBlockShowOnSeekBar = sponsorBlockShowOnSeekBar,
                    onSponsorBlockShowOnSeekBarToggle = onSponsorBlockShowOnSeekBarToggle,
                    sponsorBlockNotice = sponsorBlockNotice,
                    onSponsorBlockNoticeToggle = onSponsorBlockNoticeToggle,
                    sponsorBlockMinDurationMs = sponsorBlockMinDurationMs,
                    onSponsorBlockMinDurationChange = onSponsorBlockMinDurationChange
                )
            }
            composable(
                route = "color_palette",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.theme.ColorPaletteScreen(
                    currentPalette = colorPalette,
                    onPaletteSelected = onColorPaletteChange,
                    paletteStyle = paletteStyle,
                    onPaletteStyleSelected = onPaletteStyleChange,
                    amoledDark = amoledTheme,
                    isDarkMode = isDarkMode,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "backup",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.settings.BackupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "not_interested",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.moderation.NotInterestedScreen(
                    viewModel = homeViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            // A creator's page. The argument is normally a UC id, may be an
            // @handle/full URL from a shared link, or a video:<id> fallback
            // when a modern feed card omitted its creator endpoint. The screen
            // resolves whichever it was given without starting that video.
            composable(
                route = "downloads",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                val downloadedSongs by playerViewModel.downloadedSongs.collectAsStateWithLifecycle()
                val downloadProgress by playerViewModel.downloadProgress.collectAsStateWithLifecycle()
                com.ivor.ivormusic.ui.downloads.DownloadsScreen(
                    downloadedSongs = downloadedSongs,
                    activeDownloads = downloadProgress,
                    onPauseDownload = playerViewModel::pauseDownload,
                    onResumeDownload = playerViewModel::resumeDownload,
                    onBack = { navController.popBackStack() },
                    onPlaySong = { song ->
                        playerViewModel.playSong(song)
                    },
                    onPlayQueue = { songs, song ->
                        playerViewModel.playQueue(songs, song)
                    },
                    onDeleteDownload = { songId ->
                        playerViewModel.deleteDownload(songId)
                    },
                    onCancelDownload = { songId ->
                        playerViewModel.cancelDownload(songId)
                    },
                    onRetryDownload = { request ->
                        playerViewModel.retryDownload(request)
                    },
                    onCancelAll = { playerViewModel.cancelAllDownloads() }
                )
            }
            composable(
                route = "stats",
                enterTransition = { 
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                },
                exitTransition = { 
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                },
                popEnterTransition = { 
                    slideInHorizontally(
                        initialOffsetX = { -it / 3 },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                },
                popExitTransition = { 
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                }
            ) {
                com.ivor.ivormusic.ui.library.StatsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = homeViewModel,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 160.dp)
                )
            }
            composable(
                route = "update",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                // Local Only is handled inside the screen rather than by
                // replacing it. The installed version, build, ABI and Android
                // version are on the device already, and the full-screen notice
                // hid all of it behind a page with no visible way back.
                com.ivor.ivormusic.ui.settings.UpdateScreen(
                    onBack = { navController.popBackStack() },
                    localOnlyMode = localOnlyMode
                )
            }
            composable(
                route = "report",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.report.ReportBugScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "app_time_limit",
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
            ) {
                com.ivor.ivormusic.ui.applock.AppTimeLimitScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
        
        // One confirmation host covers the player controls and every song
        // options sheet. Download requests carry the chosen song through the
        // PlayerViewModel so a non-playing row works even when no mini player
        // exists to host UI of its own.
        pendingSongDownload?.let {
            com.ivor.ivormusic.ui.downloads.SongDownloadSheet(
                song = it,
                onConfirm = playerViewModel::confirmPendingSongDownload,
                onDismiss = playerViewModel::dismissPendingSongDownload
            )
        }

        // Undo for "don't recommend", app-wide and last in the stack.
        //
        // One host for the whole app rather than one per screen: the action can
        // be taken from the home grid, search and the player's Up Next list,
        // and the player is an overlay drawn above the NavHost. A per-screen
        // snackbar could show twice when a screen and the overlay are both
        // alive.
        NotInterestedUndoHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (musicPillVisible) 96.dp else 16.dp)
        )

        // Offer to report the crash from the previous run. Read once per
        // composition of MusicApp (an activity recreation re-reads the file,
        // but either answer has deleted it by then). Only after onboarding -
        // a crash during first-run setup would otherwise interrupt it again.
        if (onboardingCompleted) {
            var showCrashPrompt by remember {
                mutableStateOf(
                    com.ivor.ivormusic.data.CrashReporter.shouldPromptForPendingCrash(context)
                )
            }
            if (showCrashPrompt) {
                CrashReportPrompt(
                    onViewReport = {
                        showCrashPrompt = false
                        navController.navigate("report")
                    },
                    onDismiss = {
                        showCrashPrompt = false
                        com.ivor.ivormusic.data.CrashReporter.dismissPendingCrashPrompt(context)
                    }
                )
            }
        }

        // The daily time limit's enforcement surface, last in the stack so it
        // covers the NavHost, both player overlays and every prompt. Locking
        // also stands playback down: a limiter that locks the screen while
        // music keeps playing would be counting nothing and stopping nothing.
        androidx.compose.runtime.LaunchedEffect(appTimeLocked) {
            if (appTimeLocked) {
                playerViewModel.pause()
            }
        }
        if (appTimeLocked) {
            val limitPrefs = remember { com.ivor.ivormusic.data.ThemePreferences(context) }
            val usedSeconds = remember(appTimeLocked) {
                com.ivor.ivormusic.data.AppTimeLimit.usedSecondsToday(context)
            }
            val budgetMinutes = remember(appTimeLocked) {
                com.ivor.ivormusic.data.AppTimeLimit.budgetMinutesForToday(
                    com.ivor.ivormusic.data.AppTimeLimit.parseBudgets(
                        limitPrefs.getTimeLimitBudgets()
                    )
                )
            }
            com.ivor.ivormusic.ui.applock.AppLockOverlay(
                usedSecondsToday = usedSeconds,
                budgetMinutes = budgetMinutes
            )
        }
    }
}

/**
 * Shows "Video hidden - Undo" whenever something is dismissed.
 *
 * Keyed on the action's id rather than the action itself so two identical
 * dismissals in a row still re-show the snackbar instead of the second one
 * silently doing nothing.
 */
@Composable
private fun NotInterestedUndoHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) {
        com.ivor.ivormusic.data.NotInterestedRepository(context)
    }
    // Undo also takes back the account-side dismissal when there was one, so it
    // goes through the actions layer rather than straight to the local store.
    //
    // Built lazily: this host is composed for the whole life of the app, while
    // a YouTubeRepository carries its own OkHttp pool and opens
    // EncryptedSharedPreferences. Nobody should pay that at startup for a path
    // that only runs when someone actually taps Undo.
    val actions = remember(context) {
        lazy {
            com.ivor.ivormusic.data.NotInterestedActions(
                repository,
                com.ivor.ivormusic.data.YouTubeRepository(context)
            )
        }
    }
    // Deliberately not the LaunchedEffect's own scope: that is cancelled the
    // moment another dismissal replaces this one, which would drop the undo
    // request mid-flight exactly when the user is undoing in a hurry.
    val undoScope = androidx.compose.runtime.rememberCoroutineScope()
    val lastAction by repository.lastAction.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(lastAction?.id) {
        val action = lastAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = action.message,
            actionLabel = context.getString(R.string.undo),
            withDismissAction = false,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            actions.value.undo(action, undoScope)
        } else {
            // Timed out or was replaced. The hide stands; just stop offering
            // an undo for something the user has moved on from.
            repository.clearLastAction()
        }
    }

    DismissibleSnackbarHost(hostState = snackbarHostState, modifier = modifier)
}

/**
 * One-time offer to report the crash the app died with on its previous run.
 *
 * A dialog at the MusicApp root rather than a card inside Home: it must be
 * answerable before any of the tab content, overlays or mini players settle,
 * and both answers are one tap - "Report" opens the reporter route (which
 * carries the crash file's contents), while "Not now" keeps it available from
 * the manual reporter and suppresses only this prompt. Either way it never
 * appears twice for the same crash.
 */
@Composable
private fun CrashReportPrompt(
    onViewReport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.crash_dialog_title),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = stringResource(R.string.crash_dialog_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onViewReport) {
                Text(stringResource(R.string.crash_dialog_view_report))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.crash_dialog_not_now))
            }
        }
    )
}

/**
 * Music-mode representation of a video handed over by "Listen as music".
 * Same shape as the search and shared-link converters: the channel stands in
 * for the artist, and there is no album to name.
 */
private fun VideoItem.toSong(): Song = Song.fromYouTube(
    videoId = videoId,
    title = title,
    artist = channelName,
    album = "",
    duration = duration * 1000,
    thumbnailUrl = thumbnailUrl
)

