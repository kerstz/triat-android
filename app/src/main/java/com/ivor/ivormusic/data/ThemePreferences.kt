package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import com.ivor.ivormusic.ui.theme.ThemeMode
import com.ivor.ivormusic.ui.theme.PaletteStyle
import com.ivor.ivormusic.ui.theme.TRIAT_PALETTE_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * Manages app preferences (theme, local songs toggle, etc.).
 */
class ThemePreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _themeMode = MutableStateFlow(getThemeModePreference())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _amoledTheme = MutableStateFlow(getAmoledThemePreference())
    val amoledTheme: StateFlow<Boolean> = _amoledTheme.asStateFlow()

    private val _colorPalette = MutableStateFlow(getColorPalettePreference())
    val colorPalette: StateFlow<String> = _colorPalette.asStateFlow()

    private val _paletteStyle = MutableStateFlow(getPaletteStylePreference())
    val paletteStyle: StateFlow<PaletteStyle> = _paletteStyle.asStateFlow()

    private val _appIcon = MutableStateFlow(getAppIconPreference())
    val appIcon: StateFlow<String> = _appIcon.asStateFlow()

    private val _loadLocalSongs = MutableStateFlow(getLoadLocalSongsPreference())
    val loadLocalSongs: StateFlow<Boolean> = _loadLocalSongs.asStateFlow()
    
    private val _ambientBackground = MutableStateFlow(getAmbientBackgroundPreference())
    val ambientBackground: StateFlow<Boolean> = _ambientBackground.asStateFlow()

    private val _motionArtwork = MutableStateFlow(getMotionArtworkPreference())
    val motionArtwork: StateFlow<Boolean> = _motionArtwork.asStateFlow()
    private val _motionArtworkWifiOnly = MutableStateFlow(getMotionArtworkWifiOnlyPreference())
    val motionArtworkWifiOnly: StateFlow<Boolean> = _motionArtworkWifiOnly.asStateFlow()
    private val _motionArtworkQuality = MutableStateFlow(getMotionArtworkQualityPreference())
    val motionArtworkQuality: StateFlow<MotionArtworkQuality> = _motionArtworkQuality.asStateFlow()
    private val _waveformSeekBar = MutableStateFlow(getWaveformSeekBarPreference())
    val waveformSeekBar: StateFlow<Boolean> = _waveformSeekBar.asStateFlow()

    private val _playerArtworkColors = MutableStateFlow(getPlayerArtworkColorsPreference())
    val playerArtworkColors: StateFlow<Boolean> = _playerArtworkColors.asStateFlow()
    


    private val _playerStyle = MutableStateFlow(getPlayerStylePreference())
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private val _contentSource = MutableStateFlow(getContentSourcePreference())
    val contentSource: StateFlow<ContentSource> = _contentSource.asStateFlow()

    private val _mixSourcesOnHome = MutableStateFlow(prefs.getBoolean(KEY_MIX_ON_HOME, true))
    /** Interleave YouTube and SoundCloud in the home carousels. */
    val mixSourcesOnHome: StateFlow<Boolean> = _mixSourcesOnHome.asStateFlow()

    private val _showSourceBadge = MutableStateFlow(prefs.getBoolean(KEY_SOURCE_BADGE, true))
    /** Show the small source icon on each cover. */
    val showSourceBadge: StateFlow<Boolean> = _showSourceBadge.asStateFlow()
    

    private val _saveMusicHistory = MutableStateFlow(getSaveMusicHistoryPreference())
    val saveMusicHistory: StateFlow<Boolean> = _saveMusicHistory.asStateFlow()

    private val _liveDownloadUpdates = MutableStateFlow(getLiveDownloadUpdatesPreference())
    val liveDownloadUpdates: StateFlow<Boolean> = _liveDownloadUpdates.asStateFlow()

    private val _livePlaybackUpdates = MutableStateFlow(getLivePlaybackUpdatesPreference())
    val livePlaybackUpdates: StateFlow<Boolean> = _livePlaybackUpdates.asStateFlow()







    private val _captionBackground = MutableStateFlow(getCaptionBackgroundPreference())


    private val _musicQualityWifi = MutableStateFlow(getMusicQualityWifiPreference())
    val musicQualityWifi: StateFlow<String> = _musicQualityWifi.asStateFlow()

    private val _musicQualityMobile = MutableStateFlow(getMusicQualityMobilePreference())
    val musicQualityMobile: StateFlow<String> = _musicQualityMobile.asStateFlow()

    private val _spotlightHome = MutableStateFlow(getSpotlightHomePreference())
    val spotlightHome: StateFlow<Boolean> = _spotlightHome.asStateFlow()

    private val _uiScale = MutableStateFlow(getUiScalePreference())
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    private val _sponsorBlockEnabled = MutableStateFlow(getSponsorBlockEnabledPreference())
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    private val _sponsorBlockActions = MutableStateFlow(getSponsorBlockActionsPreference())
    val sponsorBlockActions: StateFlow<Map<SponsorCategory, SegmentAction>> =
        _sponsorBlockActions.asStateFlow()

    private val _sponsorBlockShowOnSeekBar =
        MutableStateFlow(getSponsorBlockShowOnSeekBarPreference())
    val sponsorBlockShowOnSeekBar: StateFlow<Boolean> =
        _sponsorBlockShowOnSeekBar.asStateFlow()

    private val _sponsorBlockNotice = MutableStateFlow(getSponsorBlockNoticePreference())
    val sponsorBlockNotice: StateFlow<Boolean> = _sponsorBlockNotice.asStateFlow()

    private val _sponsorBlockMinDurationMs =
        MutableStateFlow(getSponsorBlockMinDurationPreference())
    val sponsorBlockMinDurationMs: StateFlow<Long> = _sponsorBlockMinDurationMs.asStateFlow()

    private val _nonExpressiveNavigationBar =
        MutableStateFlow(getNonExpressiveNavigationBarPreference())
    val nonExpressiveNavigationBar: StateFlow<Boolean> =
        _nonExpressiveNavigationBar.asStateFlow()




    
    private val _excludedFolders = MutableStateFlow(getExcludedFoldersPreference())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()
    
    // Cache Settings
    private val _cacheEnabled = MutableStateFlow(getCacheEnabledPreference())
    val cacheEnabled: StateFlow<Boolean> = _cacheEnabled.asStateFlow()


    private val _playbackPreloadEnabled = MutableStateFlow(getPlaybackPreloadEnabledPreference())
    val playbackPreloadEnabled: StateFlow<Boolean> = _playbackPreloadEnabled.asStateFlow()
    
    private val _maxCacheSizeMb = MutableStateFlow(getMaxCacheSizeMbPreference())
    val maxCacheSizeMb: StateFlow<Long> = _maxCacheSizeMb.asStateFlow()
    
    // Queue Settings
    private val _autoLoadQueue = MutableStateFlow(getAutoLoadQueuePreference())
    val autoLoadQueue: StateFlow<Boolean> = _autoLoadQueue.asStateFlow()

    // Crossfade Settings
    private val _crossfadeEnabled = MutableStateFlow(getCrossfadeEnabledPreference())
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeAuto = MutableStateFlow(getCrossfadeAutoPreference())
    val crossfadeAuto: StateFlow<Boolean> = _crossfadeAuto.asStateFlow()
    
    private val _crossfadeDurationMs = MutableStateFlow(getCrossfadeDurationPreference())
    val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(getNormalizeVolumePreference())
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()


    private val _hapticsLevel = MutableStateFlow(getHapticsLevelPreference())
    val hapticsLevel: StateFlow<String> = _hapticsLevel.asStateFlow()


    private val _oemFixEnabled = MutableStateFlow(getOemFixEnabledPreference())
    val oemFixEnabled: StateFlow<Boolean> = _oemFixEnabled.asStateFlow()

    private val _manualScanEnabled = MutableStateFlow(getManualScanEnabledPreference())
    val manualScanEnabled: StateFlow<Boolean> = _manualScanEnabled.asStateFlow()

    private val _privateDownloadsEnabled =
        MutableStateFlow(getPrivateDownloadsEnabledPreference())
    val privateDownloadsEnabled: StateFlow<Boolean> =
        _privateDownloadsEnabled.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(getOnboardingCompletedPreference())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _localOnlyMode = MutableStateFlow(getLocalOnlyModePreference())
    val localOnlyMode: StateFlow<Boolean> = _localOnlyMode.asStateFlow()

    private val _timeLimitEnabled = MutableStateFlow(getTimeLimitEnabledPreference())
    val timeLimitEnabled: StateFlow<Boolean> = _timeLimitEnabled.asStateFlow()

    private val _timeLimitBudgets = MutableStateFlow(getTimeLimitBudgetsPreference())
    val timeLimitBudgets: StateFlow<Set<String>> = _timeLimitBudgets.asStateFlow()

    private val _librarySortOption = MutableStateFlow(getLibrarySortOptionPreference())
    val librarySortOption: StateFlow<String> = _librarySortOption.asStateFlow()

    private val _libraryTab = MutableStateFlow(getLibraryTabPreference())
    val libraryTab: StateFlow<String> = _libraryTab.asStateFlow()

    // The Subscriptions feed's own controls. Persisted for the reason the
    // Library sort is: they were held in composition state, and the tab lives
    // inside Home's AnimatedContent, so leaving the tab disposed the state and
    // the feed came back on "Any time" every time. A filter that forgets is
    // worse than no filter, because the user has to re-check it constantly.



    // Two ways the app suggests things you did not ask for. Stated as what is
    // shown rather than what is hidden, so the switch position and the sentence
    // agree - a toggle called "Hide X" that is on when X is visible is the
    // reliable way to make somebody flip it twice.
    private val _showRecentSearches = MutableStateFlow(getShowRecentSearchesPreference())
    val showRecentSearches: StateFlow<Boolean> = _showRecentSearches.asStateFlow()




    // Swipe actions on playlist song rows. Each direction names its own
    // action so a right swipe can play while a left swipe removes - or
    // either can be off. "remove" only arms where the playlist is editable.
    private val _playlistSwipeEnabled = MutableStateFlow(getPlaylistSwipeEnabledPreference())
    val playlistSwipeEnabled: StateFlow<Boolean> = _playlistSwipeEnabled.asStateFlow()

    private val _playlistSwipeStartAction = MutableStateFlow(getPlaylistSwipeStartActionPreference())
    val playlistSwipeStartAction: StateFlow<String> = _playlistSwipeStartAction.asStateFlow()

    private val _playlistSwipeEndAction = MutableStateFlow(getPlaylistSwipeEndActionPreference())
    val playlistSwipeEndAction: StateFlow<String> = _playlistSwipeEndAction.asStateFlow()

    // Every screen/service news up its own ThemePreferences (no DI), so a setter
    // called on one instance must still reach the flows of every other instance.
    // All instances share the same process-wide SharedPreferences object, so a
    // change listener gives us that propagation. Must be a field: SharedPreferences
    // only holds listeners weakly and would otherwise garbage-collect it.
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME_MODE -> _themeMode.value = getThemeModePreference()
            KEY_AMOLED_THEME -> _amoledTheme.value = getAmoledThemePreference()
            KEY_COLOR_PALETTE -> _colorPalette.value = getColorPalettePreference()
            KEY_APP_ICON -> _appIcon.value = getAppIconPreference()
            KEY_PALETTE_STYLE -> _paletteStyle.value = getPaletteStylePreference()
            KEY_LOAD_LOCAL_SONGS -> _loadLocalSongs.value = getLoadLocalSongsPreference()
            KEY_AMBIENT_BACKGROUND -> _ambientBackground.value = getAmbientBackgroundPreference()
            KEY_MOTION_ARTWORK -> _motionArtwork.value = getMotionArtworkPreference()
            KEY_MOTION_ARTWORK_WIFI_ONLY -> _motionArtworkWifiOnly.value = getMotionArtworkWifiOnlyPreference()
            KEY_MOTION_ARTWORK_QUALITY -> _motionArtworkQuality.value = getMotionArtworkQualityPreference()
            KEY_WAVEFORM_SEEK_BAR -> _waveformSeekBar.value = getWaveformSeekBarPreference()
            KEY_PLAYER_ARTWORK_COLORS -> _playerArtworkColors.value = getPlayerArtworkColorsPreference()
            KEY_PLAYER_STYLE -> _playerStyle.value = getPlayerStylePreference()
            KEY_CONTENT_SOURCE -> _contentSource.value = getContentSourcePreference()
            KEY_MIX_ON_HOME -> _mixSourcesOnHome.value = prefs.getBoolean(KEY_MIX_ON_HOME, true)
            KEY_SOURCE_BADGE -> _showSourceBadge.value = prefs.getBoolean(KEY_SOURCE_BADGE, true)
            KEY_SAVE_MUSIC_HISTORY -> _saveMusicHistory.value = getSaveMusicHistoryPreference()
            KEY_LIVE_DOWNLOAD_UPDATES -> _liveDownloadUpdates.value = getLiveDownloadUpdatesPreference()
            KEY_LIVE_PLAYBACK_UPDATES -> _livePlaybackUpdates.value = getLivePlaybackUpdatesPreference()
            KEY_CAPTION_BACKGROUND -> _captionBackground.value = getCaptionBackgroundPreference()
            KEY_MUSIC_QUALITY_WIFI -> _musicQualityWifi.value = getMusicQualityWifiPreference()
            KEY_MUSIC_QUALITY_MOBILE -> _musicQualityMobile.value = getMusicQualityMobilePreference()
            KEY_SPOTLIGHT_HOME -> _spotlightHome.value = getSpotlightHomePreference()
            KEY_UI_SCALE -> _uiScale.value = getUiScalePreference()
            KEY_SPONSORBLOCK_ENABLED ->
                _sponsorBlockEnabled.value = getSponsorBlockEnabledPreference()
            KEY_SPONSORBLOCK_ACTIONS ->
                _sponsorBlockActions.value = getSponsorBlockActionsPreference()
            KEY_SPONSORBLOCK_SEEKBAR ->
                _sponsorBlockShowOnSeekBar.value = getSponsorBlockShowOnSeekBarPreference()
            KEY_SPONSORBLOCK_NOTICE ->
                _sponsorBlockNotice.value = getSponsorBlockNoticePreference()
            KEY_SPONSORBLOCK_MIN_DURATION ->
                _sponsorBlockMinDurationMs.value = getSponsorBlockMinDurationPreference()
            KEY_NON_EXPRESSIVE_NAVIGATION_BAR ->
                _nonExpressiveNavigationBar.value = getNonExpressiveNavigationBarPreference()
            KEY_EXCLUDED_FOLDERS -> _excludedFolders.value = getExcludedFoldersPreference()
            KEY_CACHE_ENABLED -> _cacheEnabled.value = getCacheEnabledPreference()
            KEY_PLAYBACK_PRELOAD_ENABLED -> _playbackPreloadEnabled.value = getPlaybackPreloadEnabledPreference()
            KEY_MAX_CACHE_SIZE_MB -> _maxCacheSizeMb.value = getMaxCacheSizeMbPreference()
            KEY_AUTO_LOAD_QUEUE -> _autoLoadQueue.value = getAutoLoadQueuePreference()
            KEY_CROSSFADE_ENABLED -> _crossfadeEnabled.value = getCrossfadeEnabledPreference()
            KEY_CROSSFADE_AUTO -> _crossfadeAuto.value = getCrossfadeAutoPreference()
            KEY_CROSSFADE_DURATION -> _crossfadeDurationMs.value = getCrossfadeDurationPreference()
            KEY_NORMALIZE_VOLUME -> _normalizeVolume.value = getNormalizeVolumePreference()
            KEY_HAPTICS_LEVEL -> _hapticsLevel.value = getHapticsLevelPreference()
            KEY_OEM_FIX_ENABLED -> _oemFixEnabled.value = getOemFixEnabledPreference()
            KEY_MANUAL_SCAN_ENABLED -> _manualScanEnabled.value = getManualScanEnabledPreference()
            KEY_PRIVATE_DOWNLOADS ->
                _privateDownloadsEnabled.value = getPrivateDownloadsEnabledPreference()
            KEY_ONBOARDING_COMPLETED -> _onboardingCompleted.value = getOnboardingCompletedPreference()
            KEY_LOCAL_ONLY_MODE -> _localOnlyMode.value = getLocalOnlyModePreference()
            KEY_TIME_LIMIT_ENABLED -> _timeLimitEnabled.value = getTimeLimitEnabledPreference()
            KEY_TIME_LIMIT_BUDGETS -> _timeLimitBudgets.value = getTimeLimitBudgetsPreference()
            KEY_LIBRARY_SORT_OPTION -> _librarySortOption.value = getLibrarySortOptionPreference()
            KEY_LIBRARY_TAB -> _libraryTab.value = getLibraryTabPreference()
            KEY_SHOW_RECENT_SEARCHES -> _showRecentSearches.value = getShowRecentSearchesPreference()
            KEY_PLAYLIST_SWIPE_ENABLED -> _playlistSwipeEnabled.value = getPlaylistSwipeEnabledPreference()
            KEY_PLAYLIST_SWIPE_START_ACTION -> _playlistSwipeStartAction.value = getPlaylistSwipeStartActionPreference()
            KEY_PLAYLIST_SWIPE_END_ACTION -> _playlistSwipeEndAction.value = getPlaylistSwipeEndActionPreference()
        }
    }

    init {
        migrateLoadLocalSongsDefault()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    /**
     * Preserve the old `load_local_songs = true` default for installs that
     * predate it becoming false.
     *
     * The Settings toggle is the *only* writer of that key - onboarding never
     * touches it - so an existing user who simply never opened that screen has
     * no stored value and has been running on the old default. Flipping the
     * default alone would empty their library on update with no explanation.
     *
     * `onboarding_completed` is the signal for "this install existed before
     * now", and it cannot be read lazily inside the getter: a fresh user who
     * finishes onboarding would then start reporting true, which is exactly
     * the behaviour this change exists to remove. So it is resolved once,
     * behind its own marker, and written down.
     */
    private fun migrateLoadLocalSongsDefault() {
        if (prefs.contains(KEY_LOAD_LOCAL_SONGS_DEFAULT_MIGRATED)) return
        val isExistingInstall = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        prefs.edit().apply {
            if (isExistingInstall && !prefs.contains(KEY_LOAD_LOCAL_SONGS)) {
                putBoolean(KEY_LOAD_LOCAL_SONGS, true)
            }
            putBoolean(KEY_LOAD_LOCAL_SONGS_DEFAULT_MIGRATED, true)
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "ivor_music_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode_enum"
        private const val KEY_OLD_DARK_MODE = "dark_mode" // For migration
        private const val KEY_AMOLED_THEME = "amoled_theme"
        private const val KEY_COLOR_PALETTE = "color_palette"
        const val KEY_APP_ICON = "app_icon"
        const val DEFAULT_APP_ICON = "default"
        private const val KEY_PALETTE_STYLE = "palette_style"
        /**
         * Default palette: Triat (true black, one red accent). Was "dynamic"
         * (wallpaper) before; a stored choice always wins, including "dynamic".
         */
        const val DEFAULT_COLOR_PALETTE = TRIAT_PALETTE_ID
        private const val KEY_LOAD_LOCAL_SONGS = "load_local_songs"
        /** One-shot marker for [migrateLoadLocalSongsDefault]. */
        private const val KEY_LOAD_LOCAL_SONGS_DEFAULT_MIGRATED =
            "load_local_songs_default_migrated"
        private const val KEY_AMBIENT_BACKGROUND = "ambient_background"
        private const val KEY_MOTION_ARTWORK = "motion_artwork"
        private const val KEY_MOTION_ARTWORK_WIFI_ONLY = "motion_artwork_wifi_only"
        private const val KEY_MOTION_ARTWORK_QUALITY = "motion_artwork_quality"
        private const val KEY_WAVEFORM_SEEK_BAR = "waveform_seek_bar"
        private const val KEY_PLAYER_ARTWORK_COLORS = "player_artwork_colors"
        private const val KEY_LAST_MUSIC_TAB = "last_music_tab"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_CONTENT_SOURCE = "content_source"
        private const val KEY_MIX_ON_HOME = "mix_sources_on_home"
        private const val KEY_SOURCE_BADGE = "show_source_badge"
        private const val KEY_SAVE_MUSIC_HISTORY = "save_music_history"
        private const val KEY_LIVE_DOWNLOAD_UPDATES = "live_download_updates"

        /**
         * Live Updates (promoted ongoing notifications) are an Android 16 / API 36
         * feature. Below that the setting is meaningless and should not be shown.
         */
        val SUPPORTS_LIVE_UPDATES: Boolean =
            android.os.Build.VERSION.SDK_INT >= 36

        /**
         * Fresh read for the notification helper, which runs off a plain Context
         * rather than holding a ThemePreferences instance. Always false where the
         * platform cannot promote, so callers need only check this one thing.
         */
        fun isLiveDownloadUpdatesEnabled(context: Context): Boolean =
            SUPPORTS_LIVE_UPDATES &&
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_LIVE_DOWNLOAD_UPDATES, true)

        private const val KEY_LIVE_PLAYBACK_UPDATES = "live_playback_updates"

        /**
         * Fresh read for the playback service and its notification provider,
         * which run off a plain Context. Unlike downloads this defaults to
         * OFF: a Live Update for playback is a persistent status bar chip for
         * the whole song, which is a lot of chrome to hand someone who did not
         * ask for it.
         */
        fun isLivePlaybackUpdatesEnabled(context: Context): Boolean =
            SUPPORTS_LIVE_UPDATES &&
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_LIVE_PLAYBACK_UPDATES, false)


        private const val KEY_CAPTION_BACKGROUND = "caption_background"
        private const val KEY_CAPTIONS_ENABLED = "captions_enabled"
        private const val KEY_CAPTION_LANGUAGE_CODE = "caption_language_code"

        /** Sentinel meaning "highest available quality". */
        const val VIDEO_QUALITY_AUTO = "auto"

        /** Quality labels offered in Settings, best first. */
        val VIDEO_QUALITY_OPTIONS = listOf(
            VIDEO_QUALITY_AUTO, "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p",
            "144p"
        )


        private const val KEY_MUSIC_QUALITY_WIFI = "music_quality_wifi"
        private const val KEY_MUSIC_QUALITY_MOBILE = "music_quality_mobile"

        /** Music stream quality values: best available bitrate. */
        const val MUSIC_QUALITY_HIGH = "high"

        /** Music stream quality values: balanced, around 128 kbps. */
        const val MUSIC_QUALITY_NORMAL = "normal"

        /** Music stream quality values: smallest available stream. */
        const val MUSIC_QUALITY_LOW = "low"

        /** Music quality values offered in Settings, best first. */
        val MUSIC_QUALITY_OPTIONS = listOf(
            MUSIC_QUALITY_HIGH, MUSIC_QUALITY_NORMAL, MUSIC_QUALITY_LOW
        )

        /**
         * Highest bitrate matches the app's historical pick, so both network
         * defaults preserve existing behavior until the user chooses otherwise.
         */
        private const val DEFAULT_MUSIC_QUALITY = MUSIC_QUALITY_HIGH

        /**
         * Whether the active network is metered (mobile data, metered
         * hotspots). Drives which half of every per-network quality pair
         * applies. Unknown network state reads as unmetered so quality
         * degrades gracefully to the Wi-Fi choice rather than silently
         * capping streams.
         */
        fun isNetworkMetered(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return false
            return cm.isActiveNetworkMetered
        }

        /**
         * Static fresh read of the music quality for the current network, for
         * the stream-resolution layer which only holds a Context (same
         * pattern as [isLocalOnly]).
         */
        fun currentMusicQuality(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = if (isNetworkMetered(context)) KEY_MUSIC_QUALITY_MOBILE else KEY_MUSIC_QUALITY_WIFI
            return prefs.getString(key, DEFAULT_MUSIC_QUALITY) ?: DEFAULT_MUSIC_QUALITY
        }

        /**
         * Spotlight: the alternative music Home, built from a shortcut grid,
         * paged quick picks and artwork shelves (see
         * ui/home/SpotlightHomeContent.kt). A boolean rather than a home-style
         * enum on purpose - PlayerStyle's constants are persisted by name and are
         * therefore frozen forever, and there is no third Home planned.
         */
        private const val KEY_SPOTLIGHT_HOME = "spotlight_home"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_SPONSORBLOCK_ENABLED = "sponsorblock_enabled"
        private const val KEY_SPONSORBLOCK_ACTIONS = "sponsorblock_actions"
        private const val KEY_SPONSORBLOCK_SEEKBAR = "sponsorblock_seekbar"
        private const val KEY_SPONSORBLOCK_NOTICE = "sponsorblock_notice"
        private const val KEY_SPONSORBLOCK_MIN_DURATION = "sponsorblock_min_duration"
        private const val KEY_NON_EXPRESSIVE_NAVIGATION_BAR =
            "non_expressive_navigation_bar"

        /**
         * Default quality for video downloads, one of [VIDEO_QUALITY_OPTIONS].
         * Set from the download sheet's "remember" toggle rather than the
         * Settings screen, so like the video session state below it has no
         * StateFlow — the sheet and the download worker both read it fresh.
         */

        /**
         * App-only download storage. Off preserves Koda's existing public
         * Downloads/Triat behavior; callers fresh-read this when a transfer
         * starts so separate ThemePreferences instances cannot go stale.
         */
        private const val KEY_PRIVATE_DOWNLOADS = "private_downloads"

        fun usePrivateDownloadStorage(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PRIVATE_DOWNLOADS, false)


        /**
         * Static fresh read of the active palette id, for the playlist cover
         * generator. It runs when a playlist is created or renamed, long after
         * the Settings screen changed the palette through its own
         * ThemePreferences instance, and instance StateFlows do not cross.
         */
        fun currentColorPalette(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_COLOR_PALETTE, DEFAULT_COLOR_PALETTE)
                ?: DEFAULT_COLOR_PALETTE

        /**
         * Video player session state that outlives a single video. Unlike the
         * settings above these have no Settings UI and no StateFlow: nothing
         * observes them reactively, they are read once when the player starts
         * and written when the user changes them in the player itself.
         */
        private const val KEY_VIDEO_REPEAT = "video_repeat"
        private const val KEY_VIDEO_AUTOPLAY = "video_autoplay"
        private const val KEY_VIDEO_BRIGHTNESS = "video_brightness"
        private const val KEY_VIDEO_PLAYBACK_SPEED = "video_playback_speed"

        /** Stored brightness sentinel meaning "never set, follow the system". */
        const val VIDEO_BRIGHTNESS_UNSET = -1f

        private const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_VIDEO_CACHE_ENABLED = "video_cache_enabled"
        private const val KEY_PLAYBACK_PRELOAD_ENABLED = "playback_preload_enabled"

        /**
         * Read a switch without holding an instance.
         *
         * These are asked at decision time from a service, a data source
         * factory and a prefetch loop, none of which owns a [ThemePreferences]
         * and all of which must see a change on the next load rather than on
         * the next process. Null-safe on purpose: the JVM unit tests run
         * against a stubbed Context whose getSharedPreferences returns null,
         * and a caching switch is never worth taking playback down with it.
         */
        private fun switch(context: Context, key: String, default: Boolean): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getBoolean(key, default) ?: default

        fun isVideoCacheEnabled(context: Context): Boolean =
            switch(context, KEY_VIDEO_CACHE_ENABLED, true)


        fun isPlaybackPreloadEnabled(context: Context): Boolean =
            switch(context, KEY_PLAYBACK_PRELOAD_ENABLED, true)

        private const val KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb"
        private const val KEY_AUTO_LOAD_QUEUE = "auto_load_queue"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_AUTO = "crossfade_auto"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_PLAYBACK_SHUFFLE = "playback_shuffle"
        private const val KEY_PLAYBACK_REPEAT_MODE = "playback_repeat_mode"
        private const val KEY_PLAYBACK_SHUFFLE_SEED = "playback_shuffle_seed"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"

        /**
         * Playback speed bounds for music, as a rate where 1f is the recorded
         * speed. The floor is Media3's, not a taste call: DefaultAudioSink
         * constrains PlaybackParameters to 0.1f..8f, so a smaller rate is
         * clamped by the sink while the player keeps reporting the value it
         * was given - the audio would run at 10% while the position clock
         * extrapolated at 5%, drifting the seek bar away from what is
         * audible. [verified September 2026 against media3 1.11.0 bytecode:
         * DefaultAudioSink.MIN_PLAYBACK_SPEED = 0.1f, applied through
         * Util.constrainValue in setPlaybackParameters.]
         */
        const val MIN_PLAYBACK_SPEED = 0.1f
        const val MAX_PLAYBACK_SPEED = 2f
        const val DEFAULT_PLAYBACK_SPEED = 1f

        private const val MIN_CROSSFADE_DURATION_MS = 1_000
        private const val MAX_CROSSFADE_DURATION_MS = 15_000
        private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
        private const val KEY_HAPTICS_LEVEL = "haptics_level"
        private const val KEY_OEM_FIX_ENABLED = "oem_fix_enabled"
        private const val KEY_MANUAL_SCAN_ENABLED = "manual_scan_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LOCAL_ONLY_MODE = "local_only_mode"

        private const val KEY_TIME_LIMIT_ENABLED = "time_limit_enabled"
        private const val KEY_TIME_LIMIT_BUDGETS = "time_limit_budgets"

        /** 5 hours a day, every day - the seed when the limiter is first enabled. */
        val DEFAULT_TIME_LIMIT_BUDGETS: Set<String> =
            (0..6).map { "$it=${AppTimeLimit.DEFAULT_DAILY_MINUTES}" }.toSet()

        private const val KEY_REPORT_VERBOSE_LOGS = "report_verbose_logs"

        private const val KEY_LIBRARY_SORT_OPTION = "library_sort_option"
        private const val KEY_LIBRARY_TAB = "library_tab"
        private const val KEY_SHOW_RECENT_SEARCHES = "show_recent_searches"
        private const val KEY_PLAYLIST_SWIPE_ENABLED = "playlist_swipe_enabled"
        private const val KEY_PLAYLIST_SWIPE_START_ACTION = "playlist_swipe_start_action"
        private const val KEY_PLAYLIST_SWIPE_END_ACTION = "playlist_swipe_end_action"

        /** Swipe actions a playlist row direction can take. "off" arms nothing. */
        const val PLAYLIST_SWIPE_ACTION_OFF = "off"
        const val PLAYLIST_SWIPE_ACTION_REMOVE = "remove"
        const val PLAYLIST_SWIPE_ACTION_PLAY = "play"
        const val PLAYLIST_SWIPE_ACTION_QUEUE = "queue"
        const val PLAYLIST_SWIPE_ACTION_OPTIONS = "options"

        /**
         * Fallback sort order for the Library's All tab. Mirrors the name of
         * LibrarySortOption.Title, which lives in the UI layer because it
         * carries an icon.
         */
        private const val LIBRARY_SORT_DEFAULT = "Title"

        /**
         * Fallback tab for the Library. Mirrors the name of LibraryTab.All,
         * which lives in the UI layer because it carries a label.
         */
        private const val LIBRARY_TAB_DEFAULT = "All"

        /**
         * Static fresh read of the local-only preference for network layers
         * that only hold a Context (OkHttp interceptors, repositories) — no
         * ThemePreferences instance or flow subscription needed.
         */
        fun isLocalOnly(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOCAL_ONLY_MODE, false)

        private const val KEY_LAST_SONG_ID = "last_song_id"
        private const val KEY_LAST_SONG_TITLE = "last_song_title"
        private const val KEY_LAST_SONG_ARTIST = "last_song_artist"
        private const val KEY_LAST_SONG_ALBUM = "last_song_album"
        private const val KEY_LAST_SONG_ARTWORK = "last_song_artwork"
        private const val KEY_LAST_SONG_DURATION = "last_song_duration"
        private const val KEY_LAST_SONG_ALBUM_ID = "last_song_album_id"
        private const val KEY_LAST_SONG_RELEASE_YEAR = "last_song_release_year"
        private const val KEY_LAST_SONG_RELEASE_TYPE = "last_song_release_type"
    }
    
    // --- Last Played Song ---
    
    /**
     * Save the last played song for restoration.
     */
    fun saveLastPlayedSong(song: Song) {
        prefs.edit()
            .putString(KEY_LAST_SONG_ID, song.id)
            .putString(KEY_LAST_SONG_TITLE, song.title)
            .putString(KEY_LAST_SONG_ARTIST, song.artist)
            .putString(KEY_LAST_SONG_ALBUM, song.album)
            .putString(KEY_LAST_SONG_ARTWORK, song.thumbnailUrl ?: song.albumArtUri?.toString() ?: "")
            .putLong(KEY_LAST_SONG_DURATION, song.duration)
            .putString(KEY_LAST_SONG_ALBUM_ID, song.albumId)
            .putInt(KEY_LAST_SONG_RELEASE_YEAR, song.releaseYear ?: 0)
            .putString(KEY_LAST_SONG_RELEASE_TYPE, song.releaseType?.name)
            .apply()
    }
    
    /**
     * Get the last played song, or null if none.
     */
    fun getLastPlayedSong(): Song? {
        val id = prefs.getString(KEY_LAST_SONG_ID, null) ?: return null
        val artwork = prefs.getString(KEY_LAST_SONG_ARTWORK, "") ?: ""
        return Song(
            id = id,
            title = prefs.getString(KEY_LAST_SONG_TITLE, "Unknown") ?: "Unknown",
            artist = prefs.getString(KEY_LAST_SONG_ARTIST, "Unknown Artist") ?: "Unknown Artist",
            album = prefs.getString(KEY_LAST_SONG_ALBUM, "") ?: "",
            thumbnailUrl = artwork.ifEmpty { null },
            duration = prefs.getLong(KEY_LAST_SONG_DURATION, 0L),
            albumId = prefs.getString(KEY_LAST_SONG_ALBUM_ID, null),
            releaseYear = prefs.getInt(KEY_LAST_SONG_RELEASE_YEAR, 0).takeIf { it in 1900..2099 },
            releaseType = MusicReleaseType.entries.firstOrNull { it.name == prefs.getString(KEY_LAST_SONG_RELEASE_TYPE, null) },
            source = SongSource.YOUTUBE
        )
    }
    
    /**
     * Clear the last played song.
     */
    fun clearLastPlayedSong() {
        prefs.edit()
            .remove(KEY_LAST_SONG_ID)
            .remove(KEY_LAST_SONG_TITLE)
            .remove(KEY_LAST_SONG_ARTIST)
            .remove(KEY_LAST_SONG_ALBUM)
            .remove(KEY_LAST_SONG_ARTWORK)
            .remove(KEY_LAST_SONG_DURATION)
            .remove(KEY_LAST_SONG_ALBUM_ID)
            .remove(KEY_LAST_SONG_RELEASE_YEAR)
            .remove(KEY_LAST_SONG_RELEASE_TYPE)
            .apply()
    }

    private fun getThemeModePreference(): ThemeMode {
        if (prefs.contains(KEY_THEME_MODE)) {
            val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            return try {
                ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        }
        
        if (prefs.contains(KEY_OLD_DARK_MODE)) {
            val oldDarkMode = prefs.getBoolean(KEY_OLD_DARK_MODE, true)
            return if (oldDarkMode) ThemeMode.DARK else ThemeMode.LIGHT
        }

        return ThemeMode.SYSTEM
    }

    /**
     * Get the stored AMOLED theme preference. Defaults to true: Triat is pure black by default.
     */
    private fun getAmoledThemePreference(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_THEME, true)
    }

    /**
     * Save AMOLED theme preference and update the flow.
     */
    fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_THEME, enabled).apply()
        _amoledTheme.value = enabled
    }

    /**
     * Get the stored color palette id. Defaults to [DEFAULT_COLOR_PALETTE]
     * ("triat") when the user never picked one; explicit choices are kept.
     */
    private fun getColorPalettePreference(): String {
        return prefs.getString(KEY_COLOR_PALETTE, DEFAULT_COLOR_PALETTE) ?: DEFAULT_COLOR_PALETTE
    }

    private fun getPaletteStylePreference(): PaletteStyle =
        PaletteStyle.fromStorageId(prefs.getString(KEY_PALETTE_STYLE, null))

    fun setPaletteStyle(style: PaletteStyle) {
        prefs.edit().putString(KEY_PALETTE_STYLE, style.storageId).apply()
        _paletteStyle.value = style
    }

    fun setColorPalette(paletteId: String) {
        prefs.edit().putString(KEY_COLOR_PALETTE, paletteId).apply()
        _colorPalette.value = paletteId
    }

    fun getAppIconPreference(): String {
        return prefs.getString(KEY_APP_ICON, DEFAULT_APP_ICON) ?: DEFAULT_APP_ICON
    }

    fun setAppIcon(iconId: String) {
        prefs.edit().putString(KEY_APP_ICON, iconId).apply()
        _appIcon.value = iconId
    }

    /**
     * Get the stored load local songs preference. Defaults to **false**.
     *
     * Off by default because turning it on is what makes `HomeScreen` ask for
     * the audio permission, and it asked on first render - so someone who
     * skipped onboarding entirely was met by a system permission dialog before
     * they had done anything, for a feature they had never asked for. Koda is
     * a YouTube client first; the device library is opt-in from
     * Settings, and turning it on is what should trigger the request.
     *
     * Only the default moved, and [migrateLoadLocalSongsDefault] keeps
     * existing installs on the old `true`: the Settings toggle is the only
     * writer of this key, so an upgrading user who never opened that screen
     * has nothing stored and would otherwise find their library emptied.
     */
    private fun getLoadLocalSongsPreference(): Boolean {
        return prefs.getBoolean(KEY_LOAD_LOCAL_SONGS, false)
    }
    
    /**
     * Get the stored ambient background preference. Defaults to true.
     */
    private fun getAmbientBackgroundPreference(): Boolean {
        return prefs.getBoolean(KEY_AMBIENT_BACKGROUND, true)
    }

    /**
     * Save theme mode preference and update the flow.
     */
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    /**
     * Save load local songs preference and update the flow.
     */
    fun setLoadLocalSongs(load: Boolean) {
        prefs.edit().putBoolean(KEY_LOAD_LOCAL_SONGS, load).apply()
        _loadLocalSongs.value = load
    }

    /**
     * Toggle load local songs setting.
     */
    fun toggleLoadLocalSongs() {
        setLoadLocalSongs(!_loadLocalSongs.value)
    }
    
    /**
     * Save ambient background preference and update the flow.
     */
    fun setAmbientBackground(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMBIENT_BACKGROUND, enabled).apply()
        _ambientBackground.value = enabled
    }

    private fun getMotionArtworkPreference(): Boolean = prefs.getBoolean(KEY_MOTION_ARTWORK, false)
    private fun getMotionArtworkWifiOnlyPreference(): Boolean = prefs.getBoolean(KEY_MOTION_ARTWORK_WIFI_ONLY, true)

    fun setMotionArtwork(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOTION_ARTWORK, enabled).apply()
        _motionArtwork.value = enabled
    }

    fun setMotionArtworkWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOTION_ARTWORK_WIFI_ONLY, enabled).apply()
        _motionArtworkWifiOnly.value = enabled
    }

    /** Unknown names fall back to the default, so an older and a newer build cannot poison each other. */
    private fun getMotionArtworkQualityPreference(): MotionArtworkQuality =
        MotionArtworkQuality.fromName(prefs.getString(KEY_MOTION_ARTWORK_QUALITY, null))

    fun setMotionArtworkQuality(quality: MotionArtworkQuality) {
        prefs.edit().putString(KEY_MOTION_ARTWORK_QUALITY, quality.name).apply()
        _motionArtworkQuality.value = quality
    }

    /**
     * Off by default: it changes the shape of the seek bar in every player style, and the
     * envelope behind it is only measured while it is on, so leaving it off costs nothing.
     */
    private fun getWaveformSeekBarPreference(): Boolean = prefs.getBoolean(KEY_WAVEFORM_SEEK_BAR, false)

    fun setWaveformSeekBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAVEFORM_SEEK_BAR, enabled).apply()
        _waveformSeekBar.value = enabled
    }

    /** Expanded player buttons take their colors from the cover by default. */
    private fun getPlayerArtworkColorsPreference(): Boolean {
        return prefs.getBoolean(KEY_PLAYER_ARTWORK_COLORS, true)
    }

    /**
     * Save the album-art player colors preference and update the flow.
     */
    fun setPlayerArtworkColors(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYER_ARTWORK_COLORS, enabled).apply()
        _playerArtworkColors.value = enabled
    }
    
    /**
     * Toggle ambient background setting.
     */
    fun toggleAmbientBackground() {
        setAmbientBackground(!_ambientBackground.value)
    }
    
    
    

    /** Root Home destination restored after Koda is recreated. */
    fun getLastHomeTab(): Int {
        return prefs.getInt(KEY_LAST_MUSIC_TAB, 0).coerceIn(0, 2)
    }

    fun setLastHomeTab(tab: Int) {
        prefs.edit().putInt(KEY_LAST_MUSIC_TAB, tab.coerceIn(0, 2)).apply()
    }


    /**
     * Get the stored player style preference. Defaults to GESTURE (coverflow).
     *
     * The fallback in the catch is the same constant as the default on purpose:
     * an unreadable stored value should land where a fresh install does, not on
     * a different style than the one someone who never chose would get.
     */
    private fun getPlayerStylePreference(): PlayerStyle {
        val styleName = prefs.getString(KEY_PLAYER_STYLE, PlayerStyle.GESTURE.name)
        return try {
            PlayerStyle.valueOf(styleName ?: PlayerStyle.GESTURE.name)
        } catch (e: IllegalArgumentException) {
            PlayerStyle.GESTURE
        }
    }
    
    private fun getContentSourcePreference(): ContentSource {
        val name = prefs.getString(KEY_CONTENT_SOURCE, ContentSource.BOTH.name)
        return try {
            ContentSource.valueOf(name ?: ContentSource.BOTH.name)
        } catch (e: IllegalArgumentException) {
            ContentSource.BOTH
        }
    }

    /** Save the content-source preference and update the flow. */
    fun setContentSource(source: ContentSource) {
        prefs.edit().putString(KEY_CONTENT_SOURCE, source.name).apply()
        _contentSource.value = source
    }

    fun setMixSourcesOnHome(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIX_ON_HOME, enabled).apply()
        _mixSourcesOnHome.value = enabled
    }

    fun setShowSourceBadge(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOURCE_BADGE, enabled).apply()
        _showSourceBadge.value = enabled
    }

    /**
     * Save player style preference and update the flow.
     */
    fun setPlayerStyle(style: PlayerStyle) {
        prefs.edit().putString(KEY_PLAYER_STYLE, style.name).apply()
        _playerStyle.value = style
    }
    
    

    

    /**
     * Whether songs that play are written to the local listening history.
     *
     * Device-local, works signed out, and feeds the listening history screen,
     * the recently-played rail, the Library
     * "Most played" sort and the taste profile behind recommendations. Turning
     * it off stops all of those growing; it does not erase what is already
     * recorded, which is what Clear history is for.
     */
    private fun getSaveMusicHistoryPreference(): Boolean {
        return prefs.getBoolean(KEY_SAVE_MUSIC_HISTORY, true)
    }

    /**
     * Fresh read of the listening-history preference. Same reasoning as
     * [isSaveVideoHistoryEnabled]: PlayerViewModel holds its own
     * ThemePreferences and decides at playback time, long after the settings
     * screen wrote through a different instance.
     */
    fun isSaveMusicHistoryEnabled(): Boolean = getSaveMusicHistoryPreference()

    fun setSaveMusicHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_MUSIC_HISTORY, enabled).apply()
        _saveMusicHistory.value = enabled
    }

    /**
     * Whether download progress may ask to be promoted to a Live Update.
     * Defaults on, but is inert below API 36 (see [SUPPORTS_LIVE_UPDATES]).
     */
    private fun getLiveDownloadUpdatesPreference(): Boolean {
        return SUPPORTS_LIVE_UPDATES && prefs.getBoolean(KEY_LIVE_DOWNLOAD_UPDATES, true)
    }

    fun setLiveDownloadUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_DOWNLOAD_UPDATES, enabled).apply()
        _liveDownloadUpdates.value = enabled && SUPPORTS_LIVE_UPDATES
    }

    /**
     * Whether music playback may ask to be promoted to a Live Update. Off by
     * default - see [isLivePlaybackUpdatesEnabled] - and inert below API 36.
     */
    private fun getLivePlaybackUpdatesPreference(): Boolean {
        return SUPPORTS_LIVE_UPDATES && prefs.getBoolean(KEY_LIVE_PLAYBACK_UPDATES, false)
    }

    fun setLivePlaybackUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_PLAYBACK_UPDATES, enabled).apply()
        _livePlaybackUpdates.value = enabled && SUPPORTS_LIVE_UPDATES
    }



    








    private fun getCaptionBackgroundPreference(): CaptionBackground =
        prefs.getString(KEY_CAPTION_BACKGROUND, null)
            ?.let { stored -> CaptionBackground.entries.firstOrNull { it.name == stored } }
            ?: CaptionBackground.TRANSLUCENT


    // ---------------- Subscriptions ----------------









    // ---------------- Bug reporting ----------------

    /**
     * Whether the bug reporter's log filter offers "everything" (debug-level
     * lines) or stops at warnings. Sheet-persisted like download video quality
     * rather than a settings page row: it belongs to the report flow, and the
     * choice only ever matters inside it.
     */
    fun getReportVerboseLogs(): Boolean = prefs.getBoolean(KEY_REPORT_VERBOSE_LOGS, false)

    fun setReportVerboseLogs(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REPORT_VERBOSE_LOGS, enabled).apply()
    }

    private fun getMusicQualityWifiPreference(): String {
        return prefs.getString(KEY_MUSIC_QUALITY_WIFI, DEFAULT_MUSIC_QUALITY)
            ?: DEFAULT_MUSIC_QUALITY
    }

    private fun getMusicQualityMobilePreference(): String {
        return prefs.getString(KEY_MUSIC_QUALITY_MOBILE, DEFAULT_MUSIC_QUALITY)
            ?: DEFAULT_MUSIC_QUALITY
    }

    /**
     * Save the Wi-Fi music quality preference and update the flow.
     */
    fun setMusicQualityWifi(quality: String) {
        prefs.edit().putString(KEY_MUSIC_QUALITY_WIFI, quality).apply()
        _musicQualityWifi.value = quality
    }

    /**
     * Save the mobile-data music quality preference and update the flow.
     */
    fun setMusicQualityMobile(quality: String) {
        prefs.edit().putString(KEY_MUSIC_QUALITY_MOBILE, quality).apply()
        _musicQualityMobile.value = quality
    }

    private fun getSpotlightHomePreference(): Boolean {
        return prefs.getBoolean(KEY_SPOTLIGHT_HOME, true)
    }

    /**
     * Save the Spotlight home opt-in and update the flow. Off by default: the
     * classic Home stays what an upgrading user and a first run both get.
     */
    fun setSpotlightHome(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPOTLIGHT_HOME, enabled).apply()
        _spotlightHome.value = enabled
    }

    private fun getUiScalePreference(): Float =
        uiScaleFromStored(prefs.all[KEY_UI_SCALE])

    /**
     * Off until asked for. This is the only third-party service the app
     * contacts, so it is not something to switch on for someone.
     */
    private fun getSponsorBlockEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_SPONSORBLOCK_ENABLED, false)

    private fun getSponsorBlockActionsPreference(): Map<SponsorCategory, SegmentAction> =
        decodeSegmentActions(prefs.getString(KEY_SPONSORBLOCK_ACTIONS, null))

    private fun getSponsorBlockShowOnSeekBarPreference(): Boolean =
        prefs.getBoolean(KEY_SPONSORBLOCK_SEEKBAR, true)

    private fun getSponsorBlockNoticePreference(): Boolean =
        prefs.getBoolean(KEY_SPONSORBLOCK_NOTICE, true)

    private fun getSponsorBlockMinDurationPreference(): Long =
        prefs.getLong(KEY_SPONSORBLOCK_MIN_DURATION, 0L)
            .coerceIn(0L, SPONSORBLOCK_MAX_MIN_DURATION_MS)

    fun setSponsorBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_ENABLED, enabled).apply()
        _sponsorBlockEnabled.value = enabled
    }

    fun setSponsorBlockAction(category: SponsorCategory, action: SegmentAction) {
        val updated = _sponsorBlockActions.value.toMutableMap().apply { put(category, action) }
        prefs.edit().putString(KEY_SPONSORBLOCK_ACTIONS, encodeSegmentActions(updated)).apply()
        _sponsorBlockActions.value = updated
    }

    fun resetSponsorBlockActions() {
        val defaults = SponsorCategory.defaultActions()
        prefs.edit().putString(KEY_SPONSORBLOCK_ACTIONS, encodeSegmentActions(defaults)).apply()
        _sponsorBlockActions.value = defaults
    }

    fun setSponsorBlockShowOnSeekBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_SEEKBAR, enabled).apply()
        _sponsorBlockShowOnSeekBar.value = enabled
    }

    fun setSponsorBlockNotice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSORBLOCK_NOTICE, enabled).apply()
        _sponsorBlockNotice.value = enabled
    }

    fun setSponsorBlockMinDurationMs(durationMs: Long) {
        val safe = durationMs.coerceIn(0L, SPONSORBLOCK_MAX_MIN_DURATION_MS)
        prefs.edit().putLong(KEY_SPONSORBLOCK_MIN_DURATION, safe).apply()
        _sponsorBlockMinDurationMs.value = safe
    }


    /**
     * Save the interface scale and update the flow.
     *
     * Coerced on the way in as well as on the way out: the slider is the only
     * writer today, but a restored backup carries whatever the device that
     * wrote it allowed, and a value outside the range would reach
     * [androidx.compose.ui.unit.Density] unchecked.
     */
    fun setUiScale(scale: Float) {
        val safeScale = scale.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
        prefs.edit().putFloat(KEY_UI_SCALE, safeScale).apply()
        _uiScale.value = safeScale
    }

    private fun getNonExpressiveNavigationBarPreference(): Boolean {
        // Default to the pinned, flat bottom bar (Apple Music / Spotify shape)
        // rather than the floating pill. The expressive toolbar is still one
        // toggle away.
        return prefs.getBoolean(KEY_NON_EXPRESSIVE_NAVIGATION_BAR, true)
    }

    /**
     * Opt into Material 3's standard non-expressive navigation bar. The existing
     * expressive floating toolbar remains the default for new and upgrading
     * users, and this preference intentionally is not part of onboarding.
     */
    fun setNonExpressiveNavigationBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NON_EXPRESSIVE_NAVIGATION_BAR, enabled).apply()
        _nonExpressiveNavigationBar.value = enabled
    }



    private fun getPrivateDownloadsEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_PRIVATE_DOWNLOADS, false)

    fun setPrivateDownloadsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVATE_DOWNLOADS, enabled).apply()
        _privateDownloadsEnabled.value = enabled
    }




    /**
     * Touch feedback intensity for the whole app - one of the values
     * [com.ivor.ivormusic.util.HapticsLevel] writes via [toPref]. Every
     * haptic in the app routes through it, so this is the single switch.
     */
    private fun getHapticsLevelPreference(): String =
        prefs.getString(KEY_HAPTICS_LEVEL, com.ivor.ivormusic.util.HapticsLevel.DEFAULT)
            ?: com.ivor.ivormusic.util.HapticsLevel.DEFAULT

    fun setHapticsLevel(value: String) {
        prefs.edit().putString(KEY_HAPTICS_LEVEL, value).apply()
        _hapticsLevel.value = value
    }




    /**
     * Get the stored excluded folders preference. Defaults to empty set.
     */
    private fun getExcludedFoldersPreference(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet()
    }
    
    /**
     * Save excluded folders preference and update the flow.
     */
    fun setExcludedFolders(folders: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, folders).apply()
        _excludedFolders.value = folders
    }
    
    /**
     * Add a folder to the excluded list.
     */
    fun addExcludedFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.add(folderPath)
        setExcludedFolders(current)
    }
    
    fun removeExcludedFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        current.remove(folderPath)
        setExcludedFolders(current)
    }
    
    // --- Cache Settings ---
    
    private fun getCacheEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_CACHE_ENABLED, true)
    }
    
    fun setCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CACHE_ENABLED, enabled).apply()
        _cacheEnabled.value = enabled
    }
    



    private fun getPlaybackPreloadEnabledPreference(): Boolean = prefs.getBoolean(KEY_PLAYBACK_PRELOAD_ENABLED, true)

    fun setPlaybackPreloadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYBACK_PRELOAD_ENABLED, enabled).apply()
        _playbackPreloadEnabled.value = enabled
    }

    private fun getMaxCacheSizeMbPreference(): Long {
        return prefs.getLong(KEY_MAX_CACHE_SIZE_MB, CacheManager.DEFAULT_CACHE_SIZE_MB)
            .coerceIn(CacheManager.MIN_CACHE_SIZE_MB, CacheManager.MAX_CACHE_SIZE_MB)
    }
    
    fun setMaxCacheSizeMb(sizeMb: Long) {
        // Clamped here rather than at the settings slider, because a restored
        // backup and a build with a different ceiling both write through this
        // one setter and neither passes a slider stop.
        val clamped = sizeMb.coerceIn(
            CacheManager.MIN_CACHE_SIZE_MB,
            CacheManager.MAX_CACHE_SIZE_MB
        )
        prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, clamped).apply()
        _maxCacheSizeMb.value = clamped
    }
    
    // --- Queue Settings ---

    private fun getAutoLoadQueuePreference(): Boolean {
        return prefs.getBoolean(KEY_AUTO_LOAD_QUEUE, true)
    }

    /**
     * Fresh read of the auto-load-queue preference straight from
     * SharedPreferences — the player VM's StateFlow copy goes stale when the
     * toggle is flipped through the settings screen's own instance.
     */
    fun isAutoLoadQueueEnabled(): Boolean = getAutoLoadQueuePreference()

    fun setAutoLoadQueue(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOAD_QUEUE, enabled).apply()
        _autoLoadQueue.value = enabled
    }

    // --- Crossfade Settings ---

    private fun getCrossfadeEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_CROSSFADE_ENABLED, true)
    }
    
    fun setCrossfadeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE_ENABLED, enabled).apply()
        _crossfadeEnabled.value = enabled
    }
    
    fun toggleCrossfadeEnabled() {
        setCrossfadeEnabled(!_crossfadeEnabled.value)
    }

    private fun getCrossfadeAutoPreference(): Boolean {
        return prefs.getBoolean(KEY_CROSSFADE_AUTO, true)
    }

    fun setCrossfadeAuto(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE_AUTO, enabled).apply()
        _crossfadeAuto.value = enabled
    }
    
    private fun getCrossfadeDurationPreference(): Int {
        return prefs.getInt(KEY_CROSSFADE_DURATION, 3000)
            .coerceIn(MIN_CROSSFADE_DURATION_MS, MAX_CROSSFADE_DURATION_MS)
    }
    
    fun setCrossfadeDuration(durationMs: Int) {
        val bounded = durationMs.coerceIn(MIN_CROSSFADE_DURATION_MS, MAX_CROSSFADE_DURATION_MS)
        prefs.edit().putInt(KEY_CROSSFADE_DURATION, bounded).apply()
        _crossfadeDurationMs.value = bounded
    }

    // --- Durable playback modes ---

    fun isPlaybackShuffleEnabled(): Boolean = prefs.getBoolean(KEY_PLAYBACK_SHUFFLE, false)

    fun setPlaybackShuffle(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYBACK_SHUFFLE, enabled).apply()
    }

    fun getPlaybackRepeatMode(): Int = prefs.getInt(
        KEY_PLAYBACK_REPEAT_MODE,
        androidx.media3.common.Player.REPEAT_MODE_OFF
    ).takeIf {
        it == androidx.media3.common.Player.REPEAT_MODE_OFF ||
            it == androidx.media3.common.Player.REPEAT_MODE_ONE ||
            it == androidx.media3.common.Player.REPEAT_MODE_ALL
    } ?: androidx.media3.common.Player.REPEAT_MODE_OFF

    fun setPlaybackRepeatMode(mode: Int) {
        prefs.edit().putInt(KEY_PLAYBACK_REPEAT_MODE, mode).apply()
    }

    /**
     * The music playback rate, shared by both crossfade engines. Read fresh by
     * MusicService rather than collected: it is written from the player's
     * overflow sheet through the UI's own ThemePreferences instance, and the
     * service applies the live value from the session command that carries it.
     */
    fun getPlaybackSpeed(): Float =
        prefs.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
            .coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit()
            .putFloat(
                KEY_PLAYBACK_SPEED,
                speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
            )
            .apply()
    }

    fun getPlaybackShuffleSeed(): Long = prefs.getLong(KEY_PLAYBACK_SHUFFLE_SEED, 0L)

    fun setPlaybackShuffleSeed(seed: Long) {
        prefs.edit().putLong(KEY_PLAYBACK_SHUFFLE_SEED, seed).apply()
    }


    /**
     * Even out the volume between tracks, using the loudness YouTube already
     * measured for each one (see `TrackLoudnessStore`).
     *
     * On by default, as it is in every player that offers it. The correction
     * only ever attenuates, so the effect on an existing install is that the
     * loudest masters stop jumping out - not that anything gets louder than it
     * was.
     */
    private fun getNormalizeVolumePreference(): Boolean {
        return prefs.getBoolean(KEY_NORMALIZE_VOLUME, true)
    }

    fun setNormalizeVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NORMALIZE_VOLUME, enabled).apply()
        _normalizeVolume.value = enabled
    }

    private fun getOemFixEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_OEM_FIX_ENABLED, false)
    }

    fun setOemFixEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OEM_FIX_ENABLED, enabled).apply()
        _oemFixEnabled.value = enabled
    }

    private fun getManualScanEnabledPreference(): Boolean {
        return prefs.getBoolean(KEY_MANUAL_SCAN_ENABLED, false)
    }

    fun setManualScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MANUAL_SCAN_ENABLED, enabled).apply()
        _manualScanEnabled.value = enabled
    }

    /**
     * Get the stored local-only preference. Defaults to false: YouTube
     * features work normally until the user opts into offline-only use.
     */
    private fun getLocalOnlyModePreference(): Boolean {
        return prefs.getBoolean(KEY_LOCAL_ONLY_MODE, false)
    }

    /**
     * Fresh read of the local-only preference straight from SharedPreferences —
     * ViewModels hold their own ThemePreferences instances, so their StateFlow
     * copy goes stale when the toggle flips through the settings screen.
     */
    fun isLocalOnlyModeEnabled(): Boolean = getLocalOnlyModePreference()

    /**
     * Save local-only preference and update the flow.
     */
    fun setLocalOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_ONLY_MODE, enabled).apply()
        _localOnlyMode.value = enabled
    }

    // ---------------- Daily time limit ----------------
    //
    // The enforcement side (usage accrual, lock evaluation) lives in
    // [AppTimeLimit]; this is only where the user's choices are kept.

    private fun getTimeLimitEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_TIME_LIMIT_ENABLED, false)

    /** Fresh read for the activity's lock ticker deciding at tick time. */
    fun isTimeLimitEnabled(): Boolean = getTimeLimitEnabledPreference()

    fun setTimeLimitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIME_LIMIT_ENABLED, enabled).apply()
        _timeLimitEnabled.value = enabled
    }

    /**
     * Per-weekday budgets as "day=minutes" entries, day 0 = Monday .. 6 =
     * Sunday. A day absent from the set, or stored 0, means unlimited.
     */
    private fun getTimeLimitBudgetsPreference(): Set<String> {
        val stored = prefs.getStringSet(KEY_TIME_LIMIT_BUDGETS, null)
        return stored ?: DEFAULT_TIME_LIMIT_BUDGETS
    }

    fun getTimeLimitBudgets(): Set<String> = getTimeLimitBudgetsPreference()

    fun setTimeLimitBudgets(budgets: Set<String>) {
        val canonical = AppTimeLimit.parseBudgets(budgets)
            .map { (day, minutes) -> "$day=$minutes" }
            .toSet()
        prefs.edit().putStringSet(KEY_TIME_LIMIT_BUDGETS, canonical).apply()
        _timeLimitBudgets.value = canonical
    }

    /** Replace one weekday's value without leaving an ambiguous duplicate entry. */
    fun setTimeLimitBudget(day: Int, minutes: Int) {
        require(day in 0..6) { "Weekday must be in 0..6" }
        require(minutes >= 0) { "Budget cannot be negative" }
        val updated = AppTimeLimit.parseBudgets(getTimeLimitBudgetsPreference()).toMutableMap()
        updated[day] = minutes
        setTimeLimitBudgets(updated.map { (storedDay, storedMinutes) ->
            "$storedDay=$storedMinutes"
        }.toSet())
    }

    /** One budget applied to all seven days - the onboarding preset path. */
    fun setAllTimeLimitBudgets(minutesPerDay: Int) {
        setTimeLimitBudgets((0..6).map { "$it=$minutesPerDay" }.toSet())
    }

    /**
     * Get the stored Library sort order, held as a LibrarySortOption name.
     * The caller maps it back to the enum and is responsible for the unknown
     * case, so a sort option dropped in a later version degrades to the
     * default instead of throwing on launch.
     */
    private fun getLibrarySortOptionPreference(): String {
        return prefs.getString(KEY_LIBRARY_SORT_OPTION, LIBRARY_SORT_DEFAULT)
            ?: LIBRARY_SORT_DEFAULT
    }

    /**
     * Save the Library sort order and update the flow. Pass a
     * LibrarySortOption name; existing constants are frozen, since renaming
     * one would silently reset every user's stored choice.
     */
    fun setLibrarySortOption(optionName: String) {
        prefs.edit().putString(KEY_LIBRARY_SORT_OPTION, optionName).apply()
        _librarySortOption.value = optionName
    }

    /**
     * Get the open Library tab, held as a LibraryTab name. Same contract as
     * the sort order: the caller maps it back to the enum and owns the unknown
     * case, so a tab dropped in a later version degrades to All rather than
     * throwing on launch.
     */
    private fun getLibraryTabPreference(): String {
        return prefs.getString(KEY_LIBRARY_TAB, LIBRARY_TAB_DEFAULT) ?: LIBRARY_TAB_DEFAULT
    }

    /**
     * Save the open Library tab and update the flow. Pass a LibraryTab name;
     * existing constants are frozen, since renaming one would silently reset
     * every user's stored choice.
     */
    fun setLibraryTab(tabName: String) {
        prefs.edit().putString(KEY_LIBRARY_TAB, tabName).apply()
        _libraryTab.value = tabName
    }





    private fun getShowRecentSearchesPreference(): Boolean =
        prefs.getBoolean(KEY_SHOW_RECENT_SEARCHES, true)

    fun setShowRecentSearches(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RECENT_SEARCHES, show).apply()
        _showRecentSearches.value = show
    }







    private fun getPlaylistSwipeEnabledPreference(): Boolean =
        prefs.getBoolean(KEY_PLAYLIST_SWIPE_ENABLED, true)

    fun setPlaylistSwipeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAYLIST_SWIPE_ENABLED, enabled).apply()
        _playlistSwipeEnabled.value = enabled
    }

    private fun getPlaylistSwipeStartActionPreference(): String =
        prefs.getString(KEY_PLAYLIST_SWIPE_START_ACTION, PLAYLIST_SWIPE_ACTION_PLAY)
            ?: PLAYLIST_SWIPE_ACTION_PLAY

    fun setPlaylistSwipeStartAction(action: String) {
        prefs.edit().putString(KEY_PLAYLIST_SWIPE_START_ACTION, action).apply()
        _playlistSwipeStartAction.value = action
    }

    private fun getPlaylistSwipeEndActionPreference(): String =
        prefs.getString(KEY_PLAYLIST_SWIPE_END_ACTION, PLAYLIST_SWIPE_ACTION_REMOVE)
            ?: PLAYLIST_SWIPE_ACTION_REMOVE

    fun setPlaylistSwipeEndAction(action: String) {
        prefs.edit().putString(KEY_PLAYLIST_SWIPE_END_ACTION, action).apply()
        _playlistSwipeEndAction.value = action
    }

    private fun getOnboardingCompletedPreference(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompleted.value = completed
    }
}

/**
 * Ceiling for the "ignore very short segments" filter. Two seconds is already
 * past the point where a skip is less disruptive than the segment, and a
 * larger value would start hiding the sponsor reads the feature exists for.
 */
const val SPONSORBLOCK_MAX_MIN_DURATION_MS = 2_000L

/* ------------------------------------------------------------------ */
/* Interface scale                                                     */
/* ------------------------------------------------------------------ */

/**
 * How far the whole interface may shrink, matching the smallest stop on the
 * platform's own Display size control. It is a floor rather than a preference:
 * the scale multiplies every dp in the app, so 0.85 already takes a 48dp touch
 * target down to the physical size of 40.8dp, and going further would put the
 * app's controls under the minimum a finger can reliably hit. Someone who
 * needs smaller still can stack the system Display size setting underneath,
 * which is their explicit choice rather than one Koda made for them.
 */
const val UI_SCALE_MIN = 0.85f

const val UI_SCALE_DEFAULT = 1f

/**
 * The ceiling is deliberately nearer the default than the floor is. Growing
 * the interface is what the system font and display size settings already do
 * well, and Koda's layouts have far less slack upward - see the bottom-sheet
 * ceiling in VideoOptionsSheet, which scaling up eats into.
 */
const val UI_SCALE_MAX = 1.15f

/** The stops the slider snaps to, and the only values ever written. */
val UI_SCALE_STEPS: List<Float> =
    listOf(0.85f, 0.90f, 0.95f, 1.00f, 1.05f, 1.10f, 1.15f)

/**
 * Read the stored scale, tolerating anything. Kept outside SharedPreferences
 * so it is deterministic and unit-testable without an Android Context, the
 * same way [captionTextScaleFromStored] is.
 */
internal fun uiScaleFromStored(stored: Any?): Float = when (stored) {
    is Number -> stored.toFloat()
    else -> UI_SCALE_DEFAULT
}.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)

/** Snaps an arbitrary slider position onto the nearest stop. */
fun nearestUiScaleStep(value: Float): Float =
    UI_SCALE_STEPS.minByOrNull { kotlin.math.abs(it - value) } ?: UI_SCALE_DEFAULT

const val CAPTION_TEXT_SCALE_MIN = 0.75f
const val CAPTION_TEXT_SCALE_DEFAULT = 1f

/** Twice the former Large preset (1.25x), for high-density displays. */
const val CAPTION_TEXT_SCALE_MAX = 2.5f

/**
 * Read both the new float and the three names written by Koda 4.6. Keeping the
 * conversion outside SharedPreferences makes the migration deterministic and
 * unit-testable without an Android Context.
 */
internal fun captionTextScaleFromStored(stored: Any?): Float = when (stored) {
    is Number -> stored.toFloat()
    "SMALL" -> CAPTION_TEXT_SCALE_MIN
    "MEDIUM" -> CAPTION_TEXT_SCALE_DEFAULT
    "LARGE" -> 1.25f
    else -> CAPTION_TEXT_SCALE_DEFAULT
}.coerceIn(CAPTION_TEXT_SCALE_MIN, CAPTION_TEXT_SCALE_MAX)

/** Video-safe foreground choices, paired with the caption background. */
enum class CaptionTextColor {
    WHITE,
    YELLOW
}

/** Strength of the black caption plate drawn over the video frame. */
enum class CaptionBackground {
    NONE,
    TRANSLUCENT,
    SOLID
}

/**
 * Player UI Style options
 */
/**
 * Which services feed search and the home feed. Persisted by name, so the
 * constants are frozen. [BOTH] is the default - the point of a second source is
 * to have more music, not to pick one.
 */
enum class ContentSource {
    YOUTUBE,
    SOUNDCLOUD,
    BOTH;

    val includesYouTube: Boolean get() = this != SOUNDCLOUD
    val includesSoundCloud: Boolean get() = this != YOUTUBE
}

enum class PlayerStyle {
    /** Classic button-based player with play/pause/next/previous controls */
    CLASSIC,
    /** Gesture-based carousel player with swipe navigation */
    GESTURE,
    /** Two-tone magazine player with die-cut art and a word-pill transport */
    EDITORIAL,
    /** Kinetic type player where the title itself is the progress display */
    POSTER,
    /** Squish grid of flat tonal tiles with press physics */
    BENTO,
    /** Die-cut sticker with drag, peel and squash-and-stretch physics */
    STICKER,
    /** Living hero shape that cycles organic cuts while playing */
    MORPH,
    /** Rotary instrument: a tick-ring dial spun to scrub */
    DIAL,
    /** Full-bleed artwork over a tonal deck with an oversized play disc */
    HERO
}
