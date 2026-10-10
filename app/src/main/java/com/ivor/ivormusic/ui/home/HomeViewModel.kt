package com.ivor.ivormusic.ui.home
import com.ivor.ivormusic.R

import com.ivor.ivormusic.util.KLog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ivor.ivormusic.ui.theme.playlistCoverSeeds
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.FolderInfo
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.PlaylistPageInfo
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.HomeRecommendationCache
import com.ivor.ivormusic.data.usableHomeRecommendations
import com.ivor.ivormusic.sync.reloadAfterEachSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private val localRepository = SongRepository(application)
    private val youtubeRepository = YouTubeRepository(application)
    private val soundCloudRepository = com.ivor.ivormusic.data.SoundCloudRepository(application)
    private val playlistRepository = com.ivor.ivormusic.data.PlaylistRepository(application)
    private val sessionManager = SessionManager(application)
    private val searchHistoryRepository = com.ivor.ivormusic.data.SearchHistoryRepository(application)
    private val recommendationEngine = com.ivor.ivormusic.data.RecommendationEngine(application, youtubeRepository)
    private val homeRecommendationCache = HomeRecommendationCache(application)

    // What the user asked not to be recommended, in either mode. Declared with
    // the other repositories rather than beside the video feeds it started
    // with: the music recommendation flow is initialised further up this file
    // and now filters through it, and a property initialiser cannot reach a
    // declaration below it.
    private val notInterestedRepository =
        com.ivor.ivormusic.data.NotInterestedRepository(application)

    /** Local hide plus best-effort account propagation - see NotInterestedActions. */
    private val notInterestedActions =
        com.ivor.ivormusic.data.NotInterestedActions(notInterestedRepository, youtubeRepository)
    private val themePreferences = com.ivor.ivormusic.data.ThemePreferences(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /**
     * An artist page asked for from outside the Home screen - today, the
     * "Open music artist page" cross-link on a creator's channel page.
     *
     * The Library owns artist detail and is handed one through `initialArtist`,
     * which is state inside `HomeScreen` and therefore unreachable from a
     * NavHost destination sitting beside it. This is the same hand-off, one
     * level up: set from anywhere, consumed by `HomeScreen` the moment it
     * routes to the Library tab, and cleared so returning to Library later
     * lands on the list rather than re-opening the artist.
     */
    private val _pendingArtistPage = MutableStateFlow<String?>(null)
    val pendingArtistPage: StateFlow<String?> = _pendingArtistPage.asStateFlow()

    fun requestArtistPage(artistName: String) {
        _pendingArtistPage.value = artistName.takeIf { it.isNotBlank() }
    }

    fun consumeArtistPageRequest() {
        _pendingArtistPage.value = null
    }

    /**
     * A playlist page asked for from outside the Home screen - today, a
     * playlist link shared or opened into the app.
     *
     * The same hand-off as [pendingArtistPage] and for the same reason: both
     * playlist pages live inside the tab system, which a share intent arriving
     * at `MainActivity` cannot reach. Two flows rather than one tagged value
     * because the two modes land on different tabs and hold different types,
     * and a share names which mode it wants by the link it carries.
     */
    private val _pendingPlaylistPage = MutableStateFlow<PlaylistDisplayItem?>(null)
    val pendingPlaylistPage: StateFlow<PlaylistDisplayItem?> = _pendingPlaylistPage.asStateFlow()


    fun requestPlaylistPage(info: PlaylistPageInfo) {
        _pendingPlaylistPage.value = info.toDisplayItem()
    }

    fun consumePlaylistPageRequest() {
        _pendingPlaylistPage.value = null
    }


    /**
     * What a shared playlist link points at, as the page that opens it needs to
     * describe itself. Null when the id has no page behind it - a generated
     * mix, a private or deleted list - which is the caller's cue to fall back
     * to playing rather than opening.
     */
    suspend fun resolvePlaylistPageFromLink(playlistId: String): PlaylistPageInfo? {
        return try {
            youtubeRepository.getPlaylistHeader(playlistId)
        } catch (e: Exception) {
            null
        }
    }

    private val _searchHistory = MutableStateFlow(searchHistoryRepository.getHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _youtubeSongs = MutableStateFlow(homeRecommendationCache.load())
    /**
     * The music recommendation feed, with dismissed songs and blocked artists
     * removed.
     *
     * A derived flow over the raw fetch, never a write into it - the rule the
     * video feeds already follow: a tap removes the row on the next frame with
     * no refetch, and undo puts it back where it was rather than somewhere
     * else or nowhere. The local library (`songs`) is deliberately not filtered
     * through here: it is music the user chose to have, and "stop recommending
     * this" was never a request to hide their own files.
     */
    val youtubeSongs: StateFlow<List<Song>> = combine(
        _youtubeSongs,
        notInterestedRepository.hiddenVideos,
        notInterestedRepository.blockedChannels
    ) { songs, _, _ -> notInterestedRepository.filterSongs(songs) }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    


    private val _isYouTubeConnected = MutableStateFlow(false)

    /**
     * A YouTube session that actually authenticates. A session YouTube has
     * rejected reads as disconnected, so account-only screens offer the sign-in
     * wall instead of sitting on an empty list with no explanation.
     */
    val isYouTubeConnected: StateFlow<Boolean> =
        combine(_isYouTubeConnected, SessionManager.sessionExpired) { connected, expired ->
            connected && !expired
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    // Combine YouTube liked songs with manually liked songs (local or YT)
    private val likedSongsRepository = LikedSongsRepository(application)
    
    // Combined liked songs: manually liked (full metadata stored on like, so
    // YouTube songs show without a login) + YT-account liked + liked local songs.
    val likedSongs: StateFlow<List<Song>> = combine(
        _likedSongs,                       // YouTube Liked (from API, requires login)
        _songs,                            // Local Songs
        likedSongsRepository.likedSongs,   // Manually liked, with metadata (newest first)
        likedSongsRepository.likedSongIds  // Manually liked IDs (covers legacy likes without metadata)
    ) { ytLiked, localSongs, manuallyLikedSongs, manuallyLikedIds ->
        val manuallyLikedLocalSongs = localSongs.filter { it.id in manuallyLikedIds }
        (manuallyLikedSongs + ytLiked + manuallyLikedLocalSongs).distinctBy { it.id }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // YouTube playlists
    private val _youtubePlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList())
    
    // Playlists and albums saved from search or an artist page: references,
    // fetched live when opened. See SavedPlaylistsRepository.
    private val savedPlaylistsRepository =
        com.ivor.ivormusic.data.SavedPlaylistsRepository(application)

    // Playlists the user told Koda not to show them. Process-wide, because the
    // Library grid, Spotlight's shelves and the player's add-to-playlist sheet
    // are three ViewModels holding three sets of repositories.
    private val hiddenPlaylistsRepository =
        com.ivor.ivormusic.data.HiddenPlaylistsRepository(application)


    // Merged Playlists (Local + Saved + YouTube)
    //
    // Saved sit between the two because that is what they are: not the user's
    // own, but kept deliberately, so they belong above the account's own list
    // rather than lost at the end of it.
    val userPlaylists: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> = combine(
        _youtubePlaylists,
        playlistRepository.userPlaylists,
        savedPlaylistsRepository.savedPlaylists,
        hiddenPlaylistsRepository.hiddenPlaylists
    ) { ytPlaylists, localPlaylists, savedPlaylists, hidden ->
        val localItems = localPlaylists.map { it.toDisplayItem() }
        // A playlist kept locally that also turns up in the account's own
        // library would otherwise appear twice in the grid.
        val accountIds = ytPlaylists.map { it.id }.toSet()
        val savedItems = savedPlaylists
            .filterNot { it.id in accountIds }
            .map { it.toDisplayItem() }
        // Hiding is applied to the merged list rather than to each source, so
        // one hidden id covers the playlist wherever it came from - and it is
        // a filter over the fetch, never a write into it, so un-hiding brings
        // the playlist straight back without a refetch.
        val hiddenIds = hidden.map { it.playlistId }.toSet()
        (localItems + savedItems + ytPlaylists).filterNot { it.id in hiddenIds }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Spotlight's "New for you" shelf ---

    private val _discoverySongs = MutableStateFlow<List<Song>>(emptyList())

    /**
     * Songs the user has not heard, from what they do listen to.
     *
     * Filtered through the same dismissal store the rest of the music feed
     * is: a discovery shelf that keeps offering something the user explicitly
     * said no to is the one place that reads worst.
     */
    val discoverySongs: StateFlow<List<Song>> = combine(
        _discoverySongs,
        notInterestedRepository.hiddenVideos,
        notInterestedRepository.blockedChannels
    ) { discovered, _, _ -> notInterestedRepository.filterSongs(discovered) }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val _discoveryCollections =
        MutableStateFlow(com.ivor.ivormusic.data.RecommendationEngine.DiscoveryCollections())

    /**
     * The albums, playlists and artists under the discovery songs.
     *
     * Filtered against the same two stores the rest of the screen respects, as
     * a derived flow rather than at fetch time: hiding a playlist or blocking
     * an artist has to take it off this shelf on the next frame, not on the
     * next refresh.
     */
    val discoveryCollections: StateFlow<com.ivor.ivormusic.data.RecommendationEngine.DiscoveryCollections> =
        combine(
            _discoveryCollections,
            hiddenPlaylistsRepository.hiddenPlaylists,
            notInterestedRepository.blockedChannels
        ) { collections, _, _ ->
            collections.copy(
                albums = collections.albums.filterNot { hiddenPlaylistsRepository.isHidden(it.id) },
                playlists = collections.playlists
                    .filterNot { hiddenPlaylistsRepository.isHidden(it.id) },
                artists = collections.artists.filterNot {
                    notInterestedRepository.isCreatorBlocked(it.id, it.name)
                }
            )
        }.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            com.ivor.ivormusic.data.RecommendationEngine.DiscoveryCollections()
        )

    private val _isDiscoveryLoading = MutableStateFlow(false)
    val isDiscoveryLoading: StateFlow<Boolean> = _isDiscoveryLoading.asStateFlow()

    private var discoveryJob: kotlinx.coroutines.Job? = null

    /**
     * Fetch the discovery shelf, once, when the tab that shows it is opened.
     *
     * Lazy rather than loaded with the rest of Home: it is several searches
     * and a radio call, and most sessions never open that tab. [force] is the
     * pull-to-refresh path, which must actually re-run rather than see a
     * non-empty list and decline.
     */
    fun loadDiscovery(force: Boolean = false) {
        if (!force && (_discoverySongs.value.isNotEmpty() || _isDiscoveryLoading.value)) return
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _isDiscoveryLoading.value = true
            try {
                // Everything the user already has is what makes this
                // "discovery" rather than a second copy of their library.
                val known = buildSet {
                    _songs.value.forEach { add(it.id) }
                    likedSongsRepository.likedSongIds.value.forEach { add(it) }
                    _recentlyPlayed.value.forEach { add(it.id) }
                }
                // The two halves are fetched together and awaited together:
                // the shelves are what the songs grid scrolls into, and loading
                // them on a second pass would make the tab grow under the
                // finger a second or two after it looked finished.
                val discoveredSongs = async {
                    recommendationEngine.getDiscoveryRecommendations(known)
                }
                val collections = async {
                    recommendationEngine.getDiscoveryCollections(
                        // Nothing already in the library is a discovery, and a
                        // playlist the user saved or made is the clearest case
                        // of that.
                        excludeCollectionIds = buildSet {
                            userPlaylists.value.forEach { add(it.id) }
                            savedPlaylistIds.value.forEach { add(it) }
                        },
                        // The seeds themselves: an "artists you might like"
                        // shelf that opens with the artist the seed came from
                        // is telling the user about themselves.
                        excludeArtistNames = buildSet {
                            _songs.value.forEach { add(it.artist) }
                            _recentlyPlayed.value.forEach { add(it.artist) }
                        }
                    )
                }
                val discovered = discoveredSongs.await()
                // An empty result must not wipe a shelf that is already showing
                // something usable - a failed refresh should look like nothing
                // happened, not like the feature broke.
                if (discovered.isNotEmpty() || _discoverySongs.value.isEmpty()) {
                    _discoverySongs.value = discovered
                }
                val fetchedCollections = collections.await()
                if (!fetchedCollections.isEmpty() || _discoveryCollections.value.isEmpty()) {
                    _discoveryCollections.value = fetchedCollections
                }
            } catch (e: Exception) {
                KLog.w("HomeViewModel", "Discovery recommendations failed", e)
            } finally {
                _isDiscoveryLoading.value = false
            }
        }
    }

    /** Playlists the user told Koda not to show, for the management sheet. */
    val hiddenPlaylists: StateFlow<List<com.ivor.ivormusic.data.HiddenPlaylist>> =
        hiddenPlaylistsRepository.hiddenPlaylists

    fun isPlaylistHidden(playlistId: String?): Boolean =
        hiddenPlaylistsRepository.isHidden(playlistId)

    /**
     * Stop showing [playlist] in Koda. Local only - nothing is unsubscribed,
     * unfollowed or deleted, and the playlist is still reachable by link or
     * search. Synchronous, because the row has to leave in the frame it was
     * dismissed in.
     */
    fun hidePlaylist(playlist: com.ivor.ivormusic.data.PlaylistDisplayItem) {
        hiddenPlaylistsRepository.hide(
            com.ivor.ivormusic.data.HiddenPlaylist(
                playlistId = playlist.id,
                name = playlist.name,
                uploaderName = playlist.uploaderName,
                thumbnailUrl = playlist.thumbnailUrl
            )
        )
    }

    fun unhidePlaylist(playlistId: String) = hiddenPlaylistsRepository.unhide(playlistId)
    
    val localPlaylistIds: StateFlow<Set<String>> = playlistRepository.userPlaylists
        .map { playlists -> playlists.map { it.id }.toSet() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Ids of playlists kept as references rather than owned.
     *
     * The UI needs this and not just "is it in the library": a saved playlist
     * is somebody else's, so the rename and delete actions a library playlist
     * normally offers would be writes against a playlist the user has no rights
     * to. Anything the account genuinely owns is excluded, so an id here is
     * always a reference.
     */
    val savedPlaylistIds: StateFlow<Set<String>> = combine(
        savedPlaylistsRepository.savedPlaylists,
        _youtubePlaylists
    ) { saved, ytPlaylists ->
        val accountIds = ytPlaylists.map { it.id }.toSet()
        saved.map { it.id }.filterNot { it in accountIds }.toSet()
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    fun isPlaylistSaved(playlistId: String?): Boolean = savedPlaylistsRepository.isSaved(playlistId)

    /**
     * Keep or drop [playlist]. Returns the state after the toggle.
     *
     * Synchronous: the store is one preference write, and the button has to
     * settle in the frame it was tapped in.
     */
    fun toggleSavedPlaylist(
        playlist: com.ivor.ivormusic.data.PlaylistDisplayItem,
        isAlbum: Boolean = false
    ): Boolean = savedPlaylistsRepository.toggle(
        com.ivor.ivormusic.data.SavedPlaylist(
            id = playlist.id,
            url = playlist.url,
            name = playlist.name,
            uploaderName = playlist.uploaderName,
            thumbnailUrl = playlist.thumbnailUrl,
            itemCount = playlist.itemCount,
            isAlbum = isAlbum,
            releaseType = playlist.releaseType,
            releaseYear = playlist.releaseYear
        )
    )

    fun removeSavedPlaylist(playlistId: String) = savedPlaylistsRepository.remove(playlistId)

    private val _userAvatar = MutableStateFlow<String?>(sessionManager.getUserAvatar())
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _userName = MutableStateFlow<String?>(sessionManager.getUserName())
    val userName: StateFlow<String?> = _userName.asStateFlow()

    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository.getInstance(application)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // Recently played (from the local play history)
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    // Plays per song id, for the Library's "Most played" sort. Derived from the
    // same history read as the recents rail rather than a second file load.
    private val _playCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playCounts: StateFlow<Map<String, Int>> = _playCounts.asStateFlow()

    // Songs sitting in the stream cache in full, so they play with no network.
    // See ReadyOfflineRepository for why this is a state rather than a playlist.
    private val readyOfflineRepository =
        com.ivor.ivormusic.data.ReadyOfflineRepository(application)
    private val _readyOffline =
        MutableStateFlow(com.ivor.ivormusic.data.ReadyOfflineRepository.Result())
    val readyOffline: StateFlow<com.ivor.ivormusic.data.ReadyOfflineRepository.Result> =
        _readyOffline.asStateFlow()

    /**
     * Re-read the cache and resolve it against the play history.
     *
     * Pulled rather than observed: the cache has no change notification, and
     * polling it would mean walking every key on a timer for a list nobody is
     * looking at. The Library refreshes it on open, which is the only place it
     * is shown.
     */
    fun refreshReadyOffline() {
        viewModelScope.launch {
            _readyOffline.value = readyOfflineRepository.load(
                downloadedIds = downloadedSongs.value.map { it.id }.toSet()
            )
        }
    }

    fun refreshRecentlyPlayed(limit: Int = 15) {
        viewModelScope.launch {
            val history = statsRepository.loadHistory() // newest first
            _playCounts.value = history.groupingBy { it.songId }.eachCount()
            val localSongs = _songs.value
            val seen = mutableSetOf<String>()
            val recents = mutableListOf<Song>()
            for (entry in history) {
                if (!seen.add(entry.songId)) continue
                val song = if (entry.source == com.ivor.ivormusic.data.SongSource.LOCAL) {
                    // Local files need a playable URI — resolve from the scanned library
                    localSongs.find { it.id == entry.songId }
                } else {
                    Song.fromYouTube(
                        videoId = entry.songId,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        duration = entry.duration,
                        thumbnailUrl = entry.thumbnailUrl
                    )
                }
                if (song != null) recents.add(song)
                if (recents.size >= limit) break
            }
            _recentlyPlayed.value = recents
        }
    }


    /**
     * Apply the setting immediately; in-flight requests are prevented from
     * repopulating it.
     *
     * Shorts are a separate shelf with their own setting, so the shelf is
     * loaded either way - switching recommendations off must not silently take
     * the Shorts row away with them.
     */

    
    private val historyPagination = com.ivor.ivormusic.data.DemandPagination<String>()
    private var historyLoadJob: Job? = null
    private var historySession: com.ivor.ivormusic.data.YouTubeSession? = null

    /**
     * Video files on the device, newest first.
     *
     * Loaded only once the Library asks, which is the whole gate on this
     * feature: nothing here runs, and no permission is requested, for a user
     * who never opens the section.
     */

    /** Folder cards, derived rather than queried a second time. */


    /** True once a scan has finished, so an empty list can be told from a pending one. */


    /**
     * Re-read the device's videos.
     *
     * Always a fresh query rather than a cached list: the system's media
     * scanner runs while the app is backgrounded, so a recording made since the
     * tab was last opened must appear on the way back to it. A scan already in
     * flight is left alone, since the two would return the same rows.
     */


    /**
     * Shorts shelf minus individually hidden Shorts.
     *
     * Only video ids can be filtered here: a shelf ShortsItem carries no
     * channel at all (see ShortsItem), so a channel block cannot reach it.
     * The block still applies the moment the Short is opened and enriched -
     * it just cannot pre-empt the shelf.
     */
    
    private val _isHistoryLoading = MutableStateFlow(false)
    

    /**
     * True when the latest empty Home fetch failed without a validated network.
     * Kept separate from an empty online response so the UI can offer completed
     * downloads only when they are actually the useful fallback.
     */


    // When each Home loader last reached the network successfully. HomeScreen
    // is disposed by every real navigation (a channel page, a playlist) and
    // its startup effects run again on the way back, so without these every
    // return to Home re-downloaded the whole account: account info, every
    // liked-songs page (nine for a 300-song library), every library playlist
    // page, the music home, the video feed and the Shorts shelf - about twenty
    // requests per visit in a September 2026 report, three times in under an
    // hour, which is the traffic that gets a device refused by the bot check.
    // Launch paid for the account half twice on top of that: init and
    // HomeScreen's effect both call checkYouTubeConnection, and only the
    // in-flight job below lets the second join the first.
    // Explicit refreshes, sign-in, sign-out and profile switches pass force.
    //
    // Declared above init on purpose: init calls checkYouTubeConnection, whose
    // launch runs up to its first suspension immediately, and an initializer
    // placed after init would reset the job it just stored.
    private var accountLoadJob: Job? = null
    private var accountLoadedAtMs = 0L
    private var recommendationsLoadJob: Job? = null
    private var recommendationsLoadedAtMs = 0L

    init {
        checkYouTubeConnection()
        observeProfileSwitches()
        viewModelScope.launch {
            playlistRepository.autoRepairUnresolvedTitles()
        }
        // Playlists received from the computer are written to disk by another
        // instance of the repository, whose cache this one cannot see.
        viewModelScope.reloadAfterEachSync(application) { playlistRepository.refreshPlaylists() }
    }


    /**
     * Reload everything account-derived when the active profile changes.
     *
     * There is no DI, so a switch cannot reach this ViewModel directly - it
     * watches the process-wide id instead, the same pattern the subscription
     * and blocklist stores use. Without this the app would keep showing the
     * previous account's feeds, playlists and name under the new identity,
     * which is the single most visible way an account switcher can be wrong.
     *
     * `drop(1)` because the flow replays the current profile on subscribe and
     * that is not a switch; `checkYouTubeConnection()` already covers startup.
     */
    private fun observeProfileSwitches() {
        viewModelScope.launch {
            com.ivor.ivormusic.data.ProfileManager(getApplication())
                .activeProfileId
                .drop(1)
                .distinctUntilChanged()
                .collect { resetForProfileChange() }
        }
    }

    /**
     * Drop the previous profile's state and refetch for the new one.
     *
     * Mirrors [logout]'s clearing - the same flows go stale for the same
     * reason - but follows it with a reload rather than leaving the app empty.
     */
    private fun resetForProfileChange() {
        youtubeRepository.clearSessionScopedInstanceCaches()
        // Signed-in search is personalised, so serving one account's results
        // under another is the same mistake as replaying its visitorData.
        clearSearchCaches()

        // Identity first, so the avatar and name change on the next frame
        // rather than after the feeds have finished loading.
        _userAvatar.value = sessionManager.getUserAvatar()
        _userName.value = sessionManager.getUserName()
        _isYouTubeConnected.value = sessionManager.isLoggedIn()

        _youtubeSongs.value = homeRecommendationCache.load()
        _likedSongs.value = emptyList()
        _youtubePlaylists.value = emptyList()

        // The loaders only assign when the result is non-empty, so clearing is
        // what guarantees a failed refetch leaves nothing rather than the
        // previous account's feed.
        forgetHomeLoadTimes()

        checkYouTubeConnection(force = true)
        loadYouTubeRecommendations(force = true)
    }


    /**
     * Whether the device has a usable network right now.
     *
     * Used only to word a failure correctly: an empty feed on a working
     * connection is a real empty feed, not something the user can fix by
     * checking their wifi.
     */
    private fun hasNetworkConnection(): Boolean = try {
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        // Unknown is treated as connected, so a permissions or API oddity does
        // not turn every empty feed into a bogus "check your connection".
        true
    }


    /**
     * Delete a playlist. The local store owns its own list, so there is nothing
     * to roll back there; the account path stays optimistic-and-restore.
     */

    /** Rename a playlist held on this device. */

    /**
     * Remove a video from a playlist, Watch Later ("WL") or Liked videos
     * ("LL", removes the like). Optimistic removal, restored on failure.
     */


    /** Create a playlist on the device, from the save sheet. */


    /** Load the notification inbox. Requires login. */
    
    // --- Download Actions ---
    
    fun toggleDownload(song: Song) {
        viewModelScope.launch {
            if (downloadRepository.isDownloaded(song.id)) {
                downloadRepository.deleteDownload(song.id)
            } else {
                downloadRepository.downloadSong(song)
            }
        }
    }
    
    fun isDownloaded(songId: String): Boolean {
        return downloadRepository.isDownloaded(songId)
    }
    
    fun isDownloading(songId: String): Boolean {
        return downloadingIds.value.contains(songId)
    }
    
    fun isLocalOriginal(song: Song): Boolean {
        return downloadRepository.isLocalOriginal(song)
    }

    fun loadSongs(excludedFolders: Set<String> = emptySet(), manualScan: Boolean = false) {
        viewModelScope.launch {
            _songs.value = localRepository.getSongs(excludedFolders, manualScan)
        }
    }
    
    /**
     * Get all available music folders for the folder exclusion UI.
     */
    suspend fun getAvailableFolders(): List<FolderInfo> {
        return localRepository.getAvailableFolders()
    }

    private fun loadedRecently(atMs: Long): Boolean =
        atMs != 0L && System.currentTimeMillis() - atMs in 0 until HOME_REVISIT_REFRESH_MS

    private fun forgetHomeLoadTimes() {
        accountLoadedAtMs = 0L
        recommendationsLoadedAtMs = 0L
    }

    fun checkYouTubeConnection(force: Boolean = false) {
        if (!force && accountLoadJob?.isActive == true) return
        if (force) accountLoadJob?.cancel()
        accountLoadJob = viewModelScope.launch {
            _isYouTubeConnected.value = sessionManager.isLoggedIn()
            if (_isYouTubeConnected.value) {
                if (!force && loadedRecently(accountLoadedAtMs)) {
                    _userAvatar.value = sessionManager.getUserAvatar()
                    _userName.value = sessionManager.getUserName()
                    return@launch
                }
                youtubeRepository.fetchAccountInfo()
                _userAvatar.value = sessionManager.getUserAvatar()
                _userName.value = sessionManager.getUserName()
                loadLibraryData()
            }
        }
    }

    private suspend fun loadLibraryData() {
        try {
            _likedSongs.value = youtubeRepository.getLikedMusic()
            _youtubePlaylists.value = youtubeRepository.getUserPlaylists()
            accountLoadedAtMs = System.currentTimeMillis()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { }
    }

    fun loadYouTubeRecommendations(force: Boolean = false) {
        if (!force &&
            (recommendationsLoadJob?.isActive == true || loadedRecently(recommendationsLoadedAtMs))
        ) return
        recommendationsLoadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                // Personalize strictly from local taste profile & user playlists
                val recs = usableHomeRecommendations(
                    listOf(recommendationEngine.getHomeRecommendations())
                )
                if (recs.isNotEmpty()) {
                    homeRecommendationCache.clear()
                    _youtubeSongs.value = recs
                    homeRecommendationCache.save(recs)
                    recommendationsLoadedAtMs = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                // The feed keeps whatever it already had - both branches above
                // only assign a non-empty result - so a failure here is not
                // destructive and does not warrant tearing the screen down.
                // It does have to be visible in a bug report though, which is
                // what this was missing.
                KLog.e("HomeViewModel", "Home recommendations failed to load", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search results, kept briefly so moving between the category chips is not
     * a request each way.
     *
     * One cache per result type, all cleared together on a profile switch:
     * signed-in search is personalised, so serving one account's results under
     * another is the same mistake as replaying its visitorData.
     */
    private val songSearchCache = com.ivor.ivormusic.data.SearchResultsCache<Song>()
    private val artistSearchCache =
        com.ivor.ivormusic.data.SearchResultsCache<com.ivor.ivormusic.data.ArtistItem>()
    private val albumSearchCache =
        com.ivor.ivormusic.data.SearchResultsCache<com.ivor.ivormusic.data.PlaylistDisplayItem>()
    private val playlistSearchCache =
        com.ivor.ivormusic.data.SearchResultsCache<com.ivor.ivormusic.data.PlaylistDisplayItem>()

    /** Case and surrounding space are not a different search. */
    private fun searchKey(query: String, vararg parts: String): String =
        (listOf(query.trim().lowercase()) + parts).joinToString("|")


    /**
     * Whether this exact search can be answered without the network.
     *
     * The search screen asks so it can skip both its typing debounce and its
     * spinner: a category switch that resolves from memory should feel like a
     * tab, not like a new search.
     */
    fun hasCachedSearch(query: String, category: String): Boolean {
        if (query.isBlank()) return false
        val key = searchKey(query)
        return when (category) {
            "SONGS" -> songSearchCache.has(key)
            "ARTISTS" -> artistSearchCache.has(key)
            "ALBUMS" -> albumSearchCache.has(key)
            "PLAYLISTS" -> playlistSearchCache.has(key)
            else -> false
        }
    }

    /** Drop every cached search. Called on a profile switch. */
    private fun clearSearchCaches() {
        songSearchCache.clear()
        artistSearchCache.clear()
        albumSearchCache.clear()
        playlistSearchCache.clear()
    }

    suspend fun searchYouTube(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val key = searchKey(query)
        songSearchCache.get(key)?.let { return it }
        return try {
            youtubeRepository.search(query).also { songSearchCache.put(key, it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchSoundCloud(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            soundCloudRepository.search(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadMoreSoundCloud(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            soundCloudRepository.searchNext(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Which services search and the home feed draw from. */
    val contentSource: StateFlow<com.ivor.ivormusic.data.ContentSource> =
        themePreferences.contentSource
    val mixSourcesOnHome: StateFlow<Boolean> = themePreferences.mixSourcesOnHome
    val showSourceBadge: StateFlow<Boolean> = themePreferences.showSourceBadge

    private val _soundCloudHome = MutableStateFlow<List<Song>>(emptyList())
    /** SoundCloud charts blended into the home feed; empty unless the source allows it. */
    val soundCloudHome: StateFlow<List<Song>> = _soundCloudHome.asStateFlow()

    /** Load SoundCloud charts for the home once, when the source includes it. */
    fun refreshSoundCloudHome() {
        if (!themePreferences.contentSource.value.includesSoundCloud) {
            _soundCloudHome.value = emptyList()
            return
        }
        if (_soundCloudHome.value.isNotEmpty()) return
        viewModelScope.launch {
            _soundCloudHome.value = try {
                soundCloudRepository.charts()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun loadMoreResults(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            // Appended rather than replaced: the repository's continuation
            // cursor has moved on, so a cache still holding only the first page
            // would, after a tab switch, show one page while the next load
            // returned the page after the last one fetched.
            youtubeRepository.searchNext(query).also {
                songSearchCache.append(searchKey(query), it)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLikedMusic(): List<Song> {
        return _likedSongs.value
    }

    suspend fun getUserPlaylists(): List<com.ivor.ivormusic.data.PlaylistDisplayItem> {
        return userPlaylists.value
    }

    suspend fun fetchPlaylistSongs(playlistId: String): List<Song> {
        val downloadedPlaylist = downloadRepository.playlistStore.playlists.value.find { it.id == playlistId }
        val offlineSongs = downloadedPlaylist?.offlineSongs(downloadRepository.downloadedSongs.value).orEmpty()
        if (downloadedPlaylist != null &&
            (!hasNetworkConnection() || themePreferences.isLocalOnlyModeEnabled())) {
            return offlineSongs
        }
        // "Liked Songs" is assembled locally so it works without a YouTube
        // login: stored metadata + YT-account likes + liked local songs.
        if (playlistId == "LM" || playlistId == "VLLM") {
            val manuallyLiked = likedSongsRepository.likedSongs.value
            val likedIds = likedSongsRepository.getAllLikedSongIds()
            val likedLocalSongs = _songs.value.filter { it.id in likedIds }
            val ytLiked = _likedSongs.value.ifEmpty {
                if (sessionManager.isLoggedIn()) {
                    try { youtubeRepository.getLikedMusic() } catch (e: Exception) { emptyList() }
                } else emptyList()
            }
            return (manuallyLiked + ytLiked + likedLocalSongs).distinctBy { it.id }
        }

        // Check local first
        val localPlaylist = playlistRepository.userPlaylists.value.find { it.id == playlistId }
        if (localPlaylist != null) {
            return localPlaylist.songs
        }
        // Fallback to the explicit download snapshot when the live list is unavailable.
        return try {
            youtubeRepository.getPlaylist(playlistId).ifEmpty { offlineSongs }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            offlineSongs
        }

    }
    

    
    /**
     * Search Wrapper Functions for UI
     */
    suspend fun searchArtists(query: String): List<ArtistItem> {
        if (query.isBlank()) return emptyList()
        val key = searchKey(query)
        artistSearchCache.get(key)?.let { return it }
        return try {
            youtubeRepository.searchArtists(query).also { artistSearchCache.put(key, it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> {
        if (query.isBlank()) return emptyList()
        val key = searchKey(query)
        albumSearchCache.get(key)?.let { return it }
        return try {
            youtubeRepository.searchAlbums(query).also { albumSearchCache.put(key, it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> {
        if (query.isBlank()) return emptyList()
        val key = searchKey(query)
        playlistSearchCache.get(key)?.let { return it }
        return try {
            youtubeRepository.searchPlaylists(query).also { playlistSearchCache.put(key, it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for songs by a specific artist on YouTube Music.
     */
    suspend fun searchArtistSongs(artistName: String): List<Song> {
        return try {
            youtubeRepository.search(artistName)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> {
        return try {
            youtubeRepository.getArtistDetails(artistId)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    suspend fun getArtistPage(artistId: String): com.ivor.ivormusic.data.ArtistPage? {
        return try {
            youtubeRepository.getArtistPage(artistId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Resolve the album behind a YouTube song id (one music /next call). */
    suspend fun getSongAlbumRef(videoId: String): com.ivor.ivormusic.data.SongAlbumRef? {
        return try {
            youtubeRepository.getSongAlbumRef(videoId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Related-songs radio seeded from a YouTube video id (works logged out). */
    suspend fun getRadioSongs(videoId: String): List<Song> {
        return try {
            youtubeRepository.getRelatedSongs(videoId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveSessionCookies(cookies: String) {
        viewModelScope.launch {
            sessionManager.startSession(cookies)
            _isYouTubeConnected.value = sessionManager.isLoggedIn()
            _userAvatar.value = sessionManager.getUserAvatar()
            _userName.value = sessionManager.getUserName()
            runCatching { youtubeRepository.fetchAccountInfo() }
            loadYouTubeRecommendations(force = true)
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _isYouTubeConnected.value = false
        _userAvatar.value = null
        _userName.value = null
        homeRecommendationCache.clear()
        // Same reason as on a profile switch: results fetched with a session
        // must not survive it.
        clearSearchCaches()
        _youtubeSongs.value = emptyList()
        _likedSongs.value = emptyList()
        _youtubePlaylists.value = emptyList()
        forgetHomeLoadTimes()
        accountLoadJob?.cancel()
        // The Subscriptions tab no longer empties itself on sign-out - local
        // subscriptions outlive the session - so the account's half has to be
        // dropped explicitly, or it would sit there unreachable and stale.
        loadYouTubeRecommendations(force = true)
    }

    fun refresh(excludedFolders: Set<String> = emptySet(), manualScan: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _isYouTubeConnected.value = sessionManager.isLoggedIn()
                if (_isYouTubeConnected.value) {
                    // Fetch account info and avatar sync
                    youtubeRepository.fetchAccountInfo()
                    _userAvatar.value = sessionManager.getUserAvatar()
                    
                    // Fetch personalized recommendations (order preserved from YTM)
                    val recs = usableHomeRecommendations(
                        listOf(youtubeRepository.getRecommendations())
                    )
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs
                        homeRecommendationCache.save(recs)
                        recommendationsLoadedAtMs = System.currentTimeMillis()
                    }

                    // Update library data
                    _likedSongs.value = youtubeRepository.getLikedMusic()
                    _youtubePlaylists.value = youtubeRepository.getUserPlaylists()
                    accountLoadedAtMs = System.currentTimeMillis()
                } else if (_youtubeSongs.value.isNotEmpty()) {
                    // Logged-out YouTube mode: refresh the taste-based feed too.
                    // (Gated on a non-empty feed so local-only users don't pay
                    // for network searches on every pull-to-refresh.)
                    val recs = recommendationEngine.getHomeRecommendations()
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs
                        homeRecommendationCache.save(recs)
                    }
                }
                // Reload local songs with exclusions and playlists
                playlistRepository.refreshPlaylists()
                _songs.value = localRepository.getSongs(excludedFolders, manualScan)
            } catch (e: Exception) {
                // Silently fail
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============== VIDEO MODE FUNCTIONS ==============

    /**
     * Load trending/recommended videos for video mode home screen.
     * Also refreshes the Shorts shelf in parallel.
     */

    /**
     * One page of the video feed, or null if it did not arrive in time.
     *
     * The request is started on the ViewModel's own scope rather than inside
     * the timeout, because [YouTubeRepository.getTrendingVideos] can reach a
     * blocking extractor and a coroutine timeout does not return while an
     * uninterruptible child is still inside one - the same non-fix docs/playback-streams.md
     * describes for music resolution. Detached, the deadline
     * actually fires and the caller can put the downloaded videos up while the
     * orphan finishes against its own client timeouts.
     *
     * An outstanding request is joined rather than duplicated, so repeated
     * pulls on a slow connection cannot pile network work on the server that is
     * already not answering.
     */


    // ---------------- "Don't recommend this" ----------------

    /** The most recent hide/block, for the app-wide undo snackbar. */
    val lastNotInterested: StateFlow<com.ivor.ivormusic.data.NotInterestedRepository.UndoableAction?> =
        notInterestedRepository.lastAction

    val hiddenVideos: StateFlow<List<com.ivor.ivormusic.data.NotInterestedRepository.HiddenVideo>> =
        notInterestedRepository.hiddenVideos

    val blockedChannels: StateFlow<List<com.ivor.ivormusic.data.BlockedChannel>> =
        notInterestedRepository.blockedChannels

    /** Hide one video from every recommendation feed. */

    /** Stop recommending anything from this video's channel. */

    /**
     * Music mode's two dismissals. Local only - see [NotInterestedActions] -
     * and both land in the same store and the same undo snackbar the video
     * ones use, so there is one place to review them and one gesture to take
     * them back.
     */
    fun hideSong(song: Song) = notInterestedActions.hideSong(song)

    fun blockArtist(song: Song) = notInterestedActions.blockArtist(song)

    fun unhideVideo(videoId: String) = notInterestedRepository.unhideVideo(videoId)

    fun unblockChannel(channelId: String, name: String) =
        notInterestedRepository.unblockChannel(channelId, name)

    fun clearHiddenVideos() = notInterestedRepository.clearHiddenVideos()

    fun clearBlockedChannels() = notInterestedRepository.clearBlockedChannels()

    /**
     * Fetch another page when filtering has left too little on screen.
     *
     * Blocking a prolific channel can take a dozen items out of a twenty-item
     * grid at once. Load-more normally fires on scroll, but there is nothing
     * left to scroll after a cut like that, so the feed would just sit there
     * looking broken until the user pulled to refresh.
     */

    /**
     * Record videos as seen, keeping the newest [SHOWN_VIDEO_MEMORY] ids.
     * Bounded because the set only exists to keep consecutive refreshes from
     * repeating themselves, not to be a second watch history.
     */

    /**
     * Load the next page of the video home feed. Called when the grid scrolls
     * near its end (last ~5 items). Logged in this follows the InnerTube
     * browse continuation; logged out it mines older watch-history seeds for
     * more related videos. No-op while a load is already running or once the
     * feed is exhausted.
     */


    /**
     * Load user's watch history. Logged in: YouTube account history
     * (falling back to local). Logged out: locally persisted history.
     */
    /** A history entry just removed, kept so the snackbar can put it back. */
    data class HistoryRemoval(val video: VideoItem, val at: Int, val id: Long)


    /**
     * Take one video out of the watch history.
     *
     * The list on screen is updated first and without waiting: the store write
     * is synchronous and local, and a row that lingers for a frame after being
     * removed is the thing this feature exists to fix.
     */

    /** Put the last removed entry back where it was. */


    /**
     * Empty Koda's watch history.
     *
     * Local only, and the confirmation dialog says so: deleting the account's
     * history needs YouTube's own per-item tokens, and a control that silently
     * left youtube.com untouched would be the more damaging half of a promise
     * it could not keep.
     */

    /** True when this entry has already been taken out of history. */

    /** Library and refresh request one page only, irrespective of account age. */


    /** Next page for the same query, date window and sort as the displayed list. */


    /**
     * A creator's channel identity - banner, avatar, verified tick, subscriber
     * count - for the music artist page.
     *
     * One browse, and the artist page's only reason to make it: it is what lets
     * the same musician look like the same person whichever mode you arrive
     * from. Returns null for anything that is not a channel id, which is the
     * common case in a local library where the "artist" is a tag on a file.
     */
    suspend fun getChannelHeader(
        channelId: String
    ): com.ivor.ivormusic.data.ChannelHeader? {
        if (!channelId.startsWith("UC")) return null
        return try {
            youtubeRepository.getChannelHeader(channelId)
        } catch (e: Exception) {
            null
        }
    }


    // ============= PASTED YOUTUBE LINK RESOLUTION =============

    /**
     * Resolve a pasted YouTube video link into displayable metadata via a
     * single watch-next call (title, channel, view count — the same data the
     * video player enriches from). Returns null when the video can't be
     * loaded (bad id, private video, offline).
     */
    suspend fun resolveVideoFromLink(videoId: String): VideoItem? {
        return try {
            youtubeRepository.resolveVideoMetadata(videoId)
        } catch (e: Exception) {
            null
        }
    }

    /** Resolve a pasted playlist link into songs (music mode). */
    suspend fun resolvePlaylistSongsFromLink(playlistId: String): List<Song> {
        return try {
            youtubeRepository.getPlaylist(playlistId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Resolve a pasted playlist link into videos (video mode). */

    // ============= PLAYLIST MANAGEMENT =============
    
    /** Accent colors for a generated cover - see [playlistCoverSeeds]. */
    private fun coverSeedColors(): Pair<Int, Int>? = playlistCoverSeeds(getApplication())

    fun createLocalPlaylist(name: String, description: String?) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description, coverSeedColors())
        }
    }

    /** Replace a local playlist's artwork with an image the user picked. */
    fun setLocalPlaylistCover(playlistId: String, source: android.net.Uri) {
        viewModelScope.launch {
            playlistRepository.setCustomCover(playlistId, source)
        }
    }

    /**
     * Awaited variant for the playlist studio: the chosen cover is applied
     * before the new playlist's page opens, so it never flashes the generated
     * artwork first.
     */
    suspend fun applyLocalPlaylistCover(playlistId: String, source: android.net.Uri) {
        playlistRepository.setCustomCover(playlistId, source)
    }

    /** Drop a chosen cover and go back to the generated one. */
    fun resetLocalPlaylistCover(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.resetCoverToGenerated(playlistId, coverSeedColors())
        }
    }


    /**
     * Copy a playlist that is not the user's own into one that is - a real
     * local playlist they can rename, reorder, add to and delete from.
     *
     * This is deliberately not what the Save button does, and the two must not
     * be folded together. Saving keeps a *reference*: the playlist is re-fetched
     * live on every open, so it stays whatever its author makes it. That is the
     * right default and the reason saving exists. A copy is the opposite trade,
     * taken knowingly: it freezes the tracklist at this moment and never
     * updates again, in exchange for being editable. Someone who wants to prune
     * a 200-track playlist down to the 20 they like has no other way to do it.
     *
     * Duplicates are dropped rather than carried over. A YouTube playlist may
     * legitimately list the same video twice, but a local playlist cannot
     * represent that - [PlaylistRepository.removeSongFromPlaylist] filters by
     * id, so removing one copy would silently remove all of them, which is a
     * bug the user would meet while doing the editing this copy exists for.
     *
     * The cover is generated from the name like any other local playlist, not
     * lifted from the original: this is the user's playlist now, and the
     * original's artwork belongs to whoever published it.
     *
     * @return the new playlist's id, or null if there was nothing to copy.
     */
    suspend fun copyPlaylistToLocal(
        name: String,
        description: String?,
        songs: List<Song>
    ): String? {
        val tracks = songs.distinctBy { it.id }
        if (tracks.isEmpty()) return null
        val id = playlistRepository.createPlaylist(name, description, coverSeedColors())
        playlistRepository.replacePlaylistSongs(id, tracks)
        return id
    }

    fun addSongToLocalPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }

    /**
     * Append a selection of songs to a local playlist in one write, skipping
     * anything already in it. Suspend rather than fire-and-forget because the
     * import screen reports how many songs actually landed.
     */
    suspend fun addSongsToLocalPlaylist(playlistId: String, songs: List<Song>): Int =
        playlistRepository.addSongsToPlaylist(playlistId, songs)

    /**
     * Create a local playlist already holding [songs] - the playlist studio's
     * create action. Unlike [copyPlaylistToLocal] an empty selection is a
     * valid outcome: naming a playlist first and filling it later is exactly
     * what the flow allows.
     *
     * @return the new playlist's id.
     */
    suspend fun createLocalPlaylistWithSongs(
        name: String,
        description: String?,
        songs: List<Song>
    ): String {
        val id = playlistRepository.createPlaylist(
            name.trim(),
            description?.trim()?.takeIf { it.isNotEmpty() },
            coverSeedColors()
        )
        val tracks = songs.distinctBy { it.id }
        if (tracks.isNotEmpty()) playlistRepository.replacePlaylistSongs(id, tracks)
        return id
    }

    /**
     * What the playlist studio seeds its suggestion chips from: recency-ranked
     * favorites (as playable [Song]s), top artists and recent searches. Built
     * on demand rather than held as state - it reads the whole play history
     * and only the studio ever needs it.
     */
    data class PlaylistSeedProfile(
        val favorites: List<Song>,
        val topArtists: List<String>,
        val recentSearches: List<String>
    )

    suspend fun buildPlaylistSeedProfile(): PlaylistSeedProfile {
        val profile = recommendationEngine.buildTasteProfile()
        return PlaylistSeedProfile(
            favorites = profile.topSongs.map { entry ->
                Song.fromYouTube(
                    videoId = entry.songId,
                    title = entry.title,
                    artist = entry.artist,
                    album = entry.album,
                    duration = entry.duration,
                    thumbnailUrl = entry.thumbnailUrl
                )
            },
            topArtists = profile.topArtists,
            recentSearches = profile.recentSearches
        )
    }

    fun updateLocalPlaylist(playlistId: String, name: String, description: String?) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlistId, name, description, coverSeedColors())
        }
    }

    fun deleteLocalPlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun moveSongInLocalPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playlistRepository.moveSongInPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun replaceLocalPlaylistSongs(playlistId: String, songs: List<Song>) {
        viewModelScope.launch {
            playlistRepository.replacePlaylistSongs(playlistId, songs)
        }
    }

    // --- YouTube Music playlist editing (music.youtube.com side) ---

    /** Rename a YouTube Music playlist; the local list entry updates on success. */
    fun renameYouTubePlaylist(playlistId: String, name: String, description: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val ok = youtubeRepository.renameYouTubePlaylist(
                playlistId, trimmed, music = true, description = description
            )
            if (ok) {
                _youtubePlaylists.value = _youtubePlaylists.value.map {
                    if (it.id == playlistId) it.copy(name = trimmed, description = description) else it
                }
            }
        }
    }

    /** Delete a YouTube Music playlist. Optimistic removal, restored on failure. */
    fun deleteYouTubePlaylist(playlistId: String) {
        val previous = _youtubePlaylists.value
        _youtubePlaylists.value = previous.filterNot { it.id == playlistId }
        viewModelScope.launch {
            if (!youtubeRepository.deleteYouTubePlaylist(playlistId, music = true)) {
                _youtubePlaylists.value = previous
            }
        }
    }

    /** Remove a song from a YouTube Music playlist ("LM" removes the like). */
    fun removeSongFromYouTubePlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            youtubeRepository.removeFromYouTubePlaylist(playlistId, song.id, music = true)
        }
    }

    /**
     * Per-row playlist item ids (videoId -> occurrence-ordered setVideoIds)
     * needed to reorder a YouTube Music playlist. A list is required because
     * the same video may appear more than once; empty when signed out/failure.
     */
    suspend fun fetchYouTubePlaylistSetVideoIds(playlistId: String): Map<String, List<String>> =
        youtubeRepository.getPlaylistSetVideoIds(playlistId)

    /**
     * Move a row of a YouTube Music playlist before the row identified by
     * successorSetVideoId (null appends at the end). Returns false when the
     * server rejected the move so the caller can resync.
     */
    suspend fun moveSongInYouTubePlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?
    ): Boolean = youtubeRepository.moveInYouTubePlaylist(
        playlistId, setVideoId, successorSetVideoId, music = true
    )

    // Stats
    private val statsRepository = com.ivor.ivormusic.data.StatsRepository(application)
    private val _globalStats = MutableStateFlow(com.ivor.ivormusic.data.GlobalStats())
    val globalStats: StateFlow<com.ivor.ivormusic.data.GlobalStats> = _globalStats.asStateFlow()

    // Plays per day for the last 7 days, keyed "M/d" (see StatsRepository.getDailyPlays)
    private val _dailyPlays = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyPlays: StateFlow<Map<String, Int>> = _dailyPlays.asStateFlow()

    fun refreshStats() {
        viewModelScope.launch {
            _globalStats.value = statsRepository.getGlobalStats()
            _dailyPlays.value = statsRepository.getDailyPlays()
        }
    }

    // ---------------- Listening history ----------------

    // The raw play log, newest first - what the listening history screen shows.
    // Distinct from recentlyPlayed, which is the same source deduplicated down
    // to one card per song for the Library rail.
    private val _playHistory =
        MutableStateFlow<List<com.ivor.ivormusic.data.PlayHistoryEntry>>(emptyList())
    val playHistory: StateFlow<List<com.ivor.ivormusic.data.PlayHistoryEntry>> =
        _playHistory.asStateFlow()

    private val _isPlayHistoryLoading = MutableStateFlow(false)
    val isPlayHistoryLoading: StateFlow<Boolean> = _isPlayHistoryLoading.asStateFlow()

    fun loadPlayHistory() {
        viewModelScope.launch {
            _isPlayHistoryLoading.value = true
            _playHistory.value = statsRepository.loadHistory()
            _isPlayHistoryLoading.value = false
        }
    }

    /**
     * Remove one play, or every play of a song when [allPlaysOfSong] is set.
     *
     * [onRemoved] receives the list as it was beforehand, which is what Undo
     * needs: removing a song's whole run takes an unknown number of entries
     * with it, and putting them back in order is not something a single entry
     * can describe.
     */
    fun removePlayHistoryEntry(
        songId: String,
        timestamp: Long,
        allPlaysOfSong: Boolean = false,
        onRemoved: (List<com.ivor.ivormusic.data.PlayHistoryEntry>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val before = _playHistory.value
            _playHistory.value = if (allPlaysOfSong) {
                statsRepository.removeAllPlaysOf(songId)
            } else {
                statsRepository.removeEntry(songId, timestamp)
            }
            onRemoved(before)
            // The rail, the "Most played" sort and the stats screen all read the
            // same file. Leaving them stale is how a song deleted from history
            // stays visible one screen over.
            refreshRecentlyPlayed()
            refreshStats()
        }
    }

    fun restorePlayHistory(entries: List<com.ivor.ivormusic.data.PlayHistoryEntry>) {
        viewModelScope.launch {
            statsRepository.restoreHistory(entries)
            _playHistory.value = entries
            refreshRecentlyPlayed()
            refreshStats()
        }
    }

    fun clearPlayHistory() {
        viewModelScope.launch {
            statsRepository.clearHistory()
            _playHistory.value = emptyList()
            refreshRecentlyPlayed()
            refreshStats()
        }
    }

    // --- Search History Actions ---

    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        searchHistoryRepository.addQuery(query)
        _searchHistory.value = searchHistoryRepository.getHistory()
    }

    fun removeFromSearchHistory(query: String) {
        searchHistoryRepository.removeQuery(query)
        _searchHistory.value = searchHistoryRepository.getHistory()
    }

    fun clearSearchHistory() {
        searchHistoryRepository.clearHistory()
        _searchHistory.value = emptyList()
    }

    private companion object {
        /** Ids kept in [shownVideoIds] before the oldest are forgotten. */
        const val SHOWN_VIDEO_MEMORY = 400

        /**
         * How few visible items it takes for a "not interested" to trigger a
         * top-up page. Roughly one screen of the grid - below that there is
         * nothing left to scroll, so the usual scroll-triggered load-more
         * would never fire.
         */
        const val FEED_TOP_UP_THRESHOLD = 8

        /** A refresh stops walking the feed once it has this many new videos. */
        /**
         * How long video Home waits for its feed before showing what is on the
         * device instead.
         *
         * Long enough that an ordinary slow connection still fills the feed,
         * short enough that a dead one does not hold an empty screen. The
         * request is not cancelled when this expires - it is detached - so a
         * page that arrives late is still there for the next pull.
         */
        const val VIDEO_FEED_DEADLINE_MS = 8_000L

        const val MIN_FRESH_VIDEOS_ON_REFRESH = 15

        /**
         * Cap on continuation fetches per refresh. The live feed ran out of
         * continuation tokens after roughly 50 videos, so this bounds a refresh
         * at about that depth instead of hammering the API.
         */
        const val MAX_REFRESH_PAGES = 3

        /**
         * How long a Home load stands when the user comes back to Home without
         * asking for a refresh. [judgement] Long enough to cover browsing a
         * channel or playlist and returning; short enough that likes and
         * playlists changed elsewhere appear within a session.
         */
        const val HOME_REVISIT_REFRESH_MS = 10 * 60 * 1000L

        /**
         * Channels whose pools a page of the shuffled Home draws on. Each new
         * channel costs two browse requests, so a page is at most sixteen, the
         * same order as one page of the recommendation feed plus its Shorts.
         */
        const val MIX_CHANNELS_PER_PAGE = 8

        /** Pool fetches in flight at once; below the subscriptions feed's six. */
        const val MIX_FETCH_CONCURRENCY = 4

        /** Consecutive empty channel batches one page will try before giving up. */
        const val MIX_EMPTY_BATCH_LIMIT = 3
    }
}
