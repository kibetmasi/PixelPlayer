package com.theveloper.pixelplay.soundcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject

data class SoundCloudUiState(
    val section: SoundCloudSection = SoundCloudSection.FEED,
    val query: String = "",
    val clientId: String = "",
    val username: String = "",
    val isSignedIn: Boolean = false,
    val displayName: String = "",
    val isLoading: Boolean = false,
    val results: List<SoundCloudSearchHit> = emptyList(),
    val feedShelves: List<SoundCloudFeedShelf> = emptyList(),
    val downloads: List<Song> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val browsingPlaylistTitle: String? = null,
    val browsingPlaylistUrl: String? = null,
    /** null = follow device default (tiles on wide/foldable, list on phone). */
    val tilesOverride: Boolean? = null,
    val resolvingKey: String? = null,
    /** True while a playlist (or nested) browse can be popped with system Back. */
    val canNavigateBack: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val likedTrackUrls: Set<String> = emptySet(),
    val likingUrl: String? = null,
)

@HiltViewModel
class SoundCloudViewModel @Inject constructor(
    private val client: SoundCloudClient,
    private val settings: SoundCloudSettings,
    private val downloader: SoundCloudDownloadService,
) : ViewModel() {

    private data class NavFrame(
        val section: SoundCloudSection,
        val results: List<SoundCloudSearchHit>,
        val browsingPlaylistTitle: String?,
        val browsingPlaylistUrl: String?,
        val query: String,
    )

    private val _uiState = MutableStateFlow(SoundCloudUiState())
    val uiState: StateFlow<SoundCloudUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var artworkJob: Job? = null
    private val artworkAttempts = mutableSetOf<String>()
    private val navStack = ArrayDeque<NavFrame>()

    init {
        viewModelScope.launch {
            settings.clientId.collect { id ->
                _uiState.update { it.copy(clientId = id) }
                client.setClientIdOverride(id.ifBlank { null })
            }
        }
        viewModelScope.launch {
            settings.username.collect { user ->
                _uiState.update { it.copy(username = user) }
            }
        }
        viewModelScope.launch {
            settings.session.collect { session ->
                client.setSession(session.oauthToken, session.cookieHeader)
                _uiState.update {
                    it.copy(
                        isSignedIn = session.isSignedIn,
                        displayName = session.displayName.ifBlank { session.permalink },
                    )
                }
            }
        }
        viewModelScope.launch {
            delay(350)
            val state = _uiState.value
            if (state.clientId.isNotBlank()) {
                selectSection(
                    if (state.isSignedIn) SoundCloudSection.FEED else SoundCloudSection.DISCOVER,
                )
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, errorMessage = null) }
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            if (_uiState.value.section != SoundCloudSection.SEARCH) {
                searchJob?.cancel()
                loadJob?.cancel()
                _uiState.update {
                    it.copy(
                        section = SoundCloudSection.SEARCH,
                        browsingPlaylistTitle = null,
                        browsingPlaylistUrl = null,
                        statusMessage = null,
                    )
                }
            }
            scheduleSearch(value)
            return
        }
        // Cleared / too short while searching — leave search mode empty (don't reload other tabs).
        if (_uiState.value.section == SoundCloudSection.SEARCH) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    statusMessage = null,
                )
            }
        }
    }

    fun setUseTiles(useTiles: Boolean) {
        _uiState.update { it.copy(tilesOverride = useTiles) }
    }

    fun selectSection(section: SoundCloudSection) {
        searchJob?.cancel()
        clearNavStack()
        _uiState.update {
            it.copy(
                section = section,
                browsingPlaylistTitle = null,
                browsingPlaylistUrl = null,
                feedShelves = if (section == SoundCloudSection.FEED) it.feedShelves else emptyList(),
                errorMessage = null,
                statusMessage = null,
                canNavigateBack = false,
                isRefreshing = false,
                isLoadingMore = false,
                endReached = false,
            )
        }
        when (section) {
            SoundCloudSection.FEED -> loadFeed()
            SoundCloudSection.DISCOVER -> loadDiscover()
            SoundCloudSection.SEARCH -> {
                val q = _uiState.value.query
                if (q.trim().length >= 2) {
                    scheduleSearch(q)
                } else {
                    _uiState.update {
                        it.copy(results = emptyList(), statusMessage = null)
                    }
                }
            }
            SoundCloudSection.LIKES -> loadLikes()
            SoundCloudSection.TRACKS -> loadTracks()
            SoundCloudSection.PLAYLISTS -> loadPlaylists()
            SoundCloudSection.DOWNLOADS -> refreshDownloads()
        }
    }

    fun openPlaylist(hit: SoundCloudSearchHit) {
        if (hit.kind != SoundCloudSearchHit.Kind.PLAYLIST) return
        pushNavFrame()
        runLoad("Opening playlist…") {
            _uiState.update {
                it.copy(
                    browsingPlaylistTitle = hit.title,
                    browsingPlaylistUrl = hit.url,
                    canNavigateBack = navStack.isNotEmpty(),
                )
            }
            client.loadPlaylistTracks(hit.url)
        }
    }

    /** Pull-to-refresh: reload current section / open playlist without leaving the tab. */
    fun refresh() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoading) return
        val playlistUrl = state.browsingPlaylistUrl
        if (!playlistUrl.isNullOrBlank()) {
            runLoad("Refreshing playlist…", clearResults = false, refreshing = true) {
                client.loadPlaylistTracks(playlistUrl)
            }
            return
        }
        when (state.section) {
            SoundCloudSection.FEED -> loadFeed(refreshing = true)
            SoundCloudSection.DISCOVER ->
                runLoad("Refreshing discover…", clearResults = false, refreshing = true) { client.loadDiscover() }
            SoundCloudSection.SEARCH -> {
                val q = state.query.trim()
                if (q.length >= 2) scheduleSearch(q, refreshing = true)
            }
            SoundCloudSection.LIKES -> loadLikes(refreshing = true)
            SoundCloudSection.TRACKS -> loadTracks(refreshing = true)
            SoundCloudSection.PLAYLISTS -> loadPlaylists(refreshing = true)
            SoundCloudSection.DOWNLOADS -> {
                _uiState.update { it.copy(isRefreshing = true) }
                refreshDownloads()
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Pops playlist browse (or nested) without leaving the SoundCloud tab. */
    fun navigateBack(): Boolean {
        val frame = navStack.removeLastOrNull() ?: return false
        loadJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                section = frame.section,
                results = frame.results,
                browsingPlaylistTitle = frame.browsingPlaylistTitle,
                browsingPlaylistUrl = frame.browsingPlaylistUrl,
                query = frame.query,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null,
                statusMessage = null,
                canNavigateBack = navStack.isNotEmpty(),
                isLoadingMore = false,
                endReached = false,
            )
        }
        return true
    }

    private fun pushNavFrame() {
        val state = _uiState.value
        navStack.addLast(
            NavFrame(
                section = state.section,
                results = state.results,
                browsingPlaylistTitle = state.browsingPlaylistTitle,
                browsingPlaylistUrl = state.browsingPlaylistUrl,
                query = state.query,
            ),
        )
        _uiState.update { it.copy(canNavigateBack = true) }
    }

    private fun clearNavStack() {
        navStack.clear()
    }

    suspend fun resolveToSong(hit: SoundCloudSearchHit) = withContext(Dispatchers.IO) {
        ensureClient()
        client.toSong(client.resolveTrack(hit.url))
    }

    /**
     * Builds an ordered queue around the selected track so normal player completion advances
     * to the next playable SoundCloud track. Unplayable/DRM entries are skipped.
     */
    suspend fun resolveQueueForPlayback(
        startHit: SoundCloudSearchHit,
        queueHits: List<SoundCloudSearchHit>,
        limit: Int = 40,
    ): Pair<List<Song>, Song> {
        val candidates = (listOf(startHit) + queueHits)
            .asSequence()
            .filter { it.kind == SoundCloudSearchHit.Kind.TRACK }
            .distinctBy { it.url }
            .take(limit)
            .toList()
        setBusy(startHit.url)
        val resolved = withContext(Dispatchers.IO) {
            ensureClient()
            val gate = Semaphore(4)
            coroutineScope {
                candidates.map { hit ->
                    async {
                        gate.withPermit {
                            runCatching { hit to client.toSong(client.resolveTrack(hit.url)) }
                                .onFailure { Timber.w(it, "Skip unplayable SoundCloud track: %s", hit.url) }
                                .getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
        val startSong = resolved.firstOrNull { it.first.url == startHit.url }?.second
            ?: throw IllegalStateException("This SoundCloud track has no playable stream")
        val songsByUrl = resolved.associate { it.first.url to it.second }
        val orderedSongs = queueHits
            .asSequence()
            .filter { it.kind == SoundCloudSearchHit.Kind.TRACK }
            .mapNotNull { songsByUrl[it.url] }
            .distinctBy { it.id }
            .toMutableList()
            .apply {
                if (none { it.id == startSong.id }) add(0, startSong)
            }
        return orderedSongs to startSong
    }

    /**
     * Resolves playable tracks from the current results list for shuffle/queue playback.
     * Skips playlists and tracks that fail to resolve (e.g. DRM).
     */
    suspend fun resolveTracksForPlayback(limit: Int = 30): List<Song> {
        val tracks = _uiState.value.results
            .asSequence()
            .filter { it.kind == SoundCloudSearchHit.Kind.TRACK }
            .take(limit)
            .toList()
        if (tracks.isEmpty()) {
            throw IllegalStateException("No tracks to play — open a playlist or pick a feed with songs")
        }
        setBusy()
        return withContext(Dispatchers.IO) {
            ensureClient()
            val gate = Semaphore(4)
            coroutineScope {
                tracks.map { hit ->
                    async {
                        gate.withPermit {
                            try {
                                client.toSong(client.resolveTrack(hit.url))
                            } catch (t: Throwable) {
                                Timber.w(t, "Skip unplayable SoundCloud track: %s", hit.url)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }.also { songs ->
            if (songs.isEmpty()) {
                setIdle(error = "No playable streams in this list")
            }
        }
    }

    fun download(hit: SoundCloudSearchHit) {
        if (hit.kind != SoundCloudSearchHit.Kind.TRACK) {
            _uiState.update { it.copy(errorMessage = "Open the playlist, then download a track") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null, errorMessage = null) }
            try {
                ensureClient()
                val file = withContext(Dispatchers.IO) {
                    val track = client.resolveTrack(hit.url)
                    downloader.downloadToMusicFolder(track)
                }
                refreshDownloads()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "SoundCloud download failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: t::class.java.simpleName)
                }
            }
        }
    }

    fun toggleLike(hit: SoundCloudSearchHit) {
        if (hit.kind != SoundCloudSearchHit.Kind.TRACK || _uiState.value.likingUrl != null) return
        if (!_uiState.value.isSignedIn) {
            _uiState.update { it.copy(errorMessage = "Sign in to SoundCloud to like tracks") }
            return
        }
        val shouldLike = hit.url !in _uiState.value.likedTrackUrls
        viewModelScope.launch {
            _uiState.update { it.copy(likingUrl = hit.url, errorMessage = null) }
            try {
                withContext(Dispatchers.IO) { client.setTrackLiked(hit.url, shouldLike) }
                _uiState.update { state ->
                    val liked = if (shouldLike) {
                        state.likedTrackUrls + hit.url
                    } else {
                        state.likedTrackUrls - hit.url
                    }
                    state.copy(
                        likedTrackUrls = liked,
                        likingUrl = null,
                        results = if (!shouldLike && state.section == SoundCloudSection.LIKES) {
                            state.results.filterNot { it.url == hit.url }
                        } else {
                            state.results
                        },
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "SoundCloud like toggle failed")
                _uiState.update {
                    it.copy(likingUrl = null, errorMessage = t.message ?: t::class.java.simpleName)
                }
            }
        }
    }

    fun setBusy(key: String? = null) {
        _uiState.update {
            it.copy(isLoading = true, statusMessage = null, errorMessage = null, resolvingKey = key)
        }
    }

    fun setIdle(error: String? = null) {
        _uiState.update {
            it.copy(isLoading = false, statusMessage = null, errorMessage = error, resolvingKey = null)
        }
    }

    fun refreshDownloads() {
        val songs = downloader.listDownloadedSongs()
        _uiState.update {
            it.copy(
                downloads = songs,
                results = emptyList(),
                isLoading = false,
                statusMessage = null,
            )
        }
        hydrateMissingDownloadArtwork(songs)
    }

    private fun hydrateMissingDownloadArtwork(songs: List<Song>) {
        if (_uiState.value.clientId.isBlank()) return
        val missing = songs.filter {
            it.albumArtUriString.isNullOrBlank() && artworkAttempts.add(it.id)
        }
        if (missing.isEmpty()) return
        artworkJob?.cancel()
        artworkJob = viewModelScope.launch(Dispatchers.IO) {
            ensureClient()
            val hydrated = missing.mapNotNull { song ->
                val query = "${song.artist} ${song.title}".trim()
                val candidates = runCatching { client.search(query, 5) }.getOrDefault(emptyList())
                val normalizedTitle = song.title.normalizedForArtworkMatch()
                val match = candidates.firstOrNull {
                    it.kind == SoundCloudSearchHit.Kind.TRACK &&
                        it.title.normalizedForArtworkMatch() == normalizedTitle &&
                        !it.thumbnailUrl.isNullOrBlank()
                } ?: candidates.firstOrNull {
                    it.kind == SoundCloudSearchHit.Kind.TRACK && !it.thumbnailUrl.isNullOrBlank()
                }
                val artwork = match?.thumbnailUrl ?: return@mapNotNull null
                downloader.rememberArtwork(song, artwork)
                song.id to artwork
            }.toMap()
            if (hydrated.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        downloads = state.downloads.map { song ->
                            hydrated[song.id]?.let { song.copy(albumArtUriString = it) } ?: song
                        },
                    )
                }
            }
        }
    }

    private fun String.normalizedForArtworkMatch(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "")

    private fun scheduleSearch(rawQuery: String, refreshing: Boolean = false) {
        searchJob?.cancel()
        val query = rawQuery.trim()
        if (query.length < 2) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    isRefreshing = false,
                    statusMessage = null,
                    errorMessage = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (!refreshing) delay(SEARCH_DEBOUNCE_MS)
            runLoad("Searching…", cancelPrevious = false, clearResults = !refreshing, refreshing = refreshing) {
                if (query.contains("soundcloud.com/", ignoreCase = true) &&
                    !query.contains("/sets/", ignoreCase = true) &&
                    !query.contains("/likes", ignoreCase = true)
                ) {
                    val track = client.resolveTrack(query)
                    listOf(
                        SoundCloudSearchHit(
                            url = track.url,
                            title = track.title,
                            artist = track.artist,
                            durationSec = track.durationMs / 1000L,
                            thumbnailUrl = track.artworkUrl,
                        ),
                    )
                } else {
                    client.search(query)
                }
            }
        }
    }

    /** Requests a larger result window when the current list reaches its end. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || state.endReached) return
        val nextLimit = (state.results.size + PAGE_SIZE).coerceAtMost(MAX_RESULTS)
        if (nextLimit <= state.results.size) {
            _uiState.update { it.copy(endReached = true) }
            return
        }
        val playlistUrl = state.browsingPlaylistUrl
        if (!playlistUrl.isNullOrBlank()) {
            runLoad("Loading more…", clearResults = false, loadingMore = true) {
                client.loadPlaylistTracks(playlistUrl, nextLimit)
            }
            return
        }
        when (state.section) {
            SoundCloudSection.FEED -> loadFeed(limit = nextLimit, loadingMore = true)
            SoundCloudSection.DISCOVER ->
                runLoad("Loading more…", clearResults = false, loadingMore = true) {
                    client.loadDiscover(nextLimit)
                }
            SoundCloudSection.SEARCH -> {
                val query = state.query.trim()
                if (query.length >= 2) {
                    runLoad("Loading more…", clearResults = false, loadingMore = true) {
                        client.search(query, nextLimit)
                    }
                }
            }
            SoundCloudSection.LIKES -> loadLikes(limit = nextLimit, loadingMore = true)
            SoundCloudSection.TRACKS -> loadTracks(limit = nextLimit, loadingMore = true)
            SoundCloudSection.PLAYLISTS -> loadPlaylists(limit = nextLimit, loadingMore = true)
            SoundCloudSection.DOWNLOADS -> _uiState.update { it.copy(endReached = true) }
        }
    }

    private fun loadFeed(
        refreshing: Boolean = false,
        limit: Int = INITIAL_FEED_SIZE,
        loadingMore: Boolean = false,
    ) = runLoad(
        "Loading feed…",
        clearResults = !refreshing && !loadingMore,
        refreshing = refreshing,
        loadingMore = loadingMore,
    ) {
        if (!client.hasSession) {
            throw IllegalStateException("Sign in to SoundCloud to see your personal feed")
        }
        val shelfLimit = limit.coerceAtMost(40)
        val feed = client.loadFeed(limit)
        val likes = runCatching { client.loadMyLikes(shelfLimit) }.getOrDefault(emptyList())
        val history = runCatching { client.loadRecentlyPlayed(shelfLimit) }.getOrDefault(emptyList())
        val playlists = runCatching { client.loadMyPlaylists(shelfLimit) }.getOrDefault(emptyList())
        val discover = runCatching { client.loadDiscover(shelfLimit) }.getOrDefault(emptyList())
        val mixed = (likes.take(6) + feed.take(8) + discover.take(6))
            .distinctBy { it.url }
            .take(16)
        val user = _uiState.value.displayName.ifBlank { _uiState.value.username }
        val shelves = buildList {
            if (likes.isNotEmpty()) add(
                SoundCloudFeedShelf("more_like", "More of what you like", likes),
            )
            if (history.isNotEmpty()) add(
                SoundCloudFeedShelf("recent", "Recently played", history),
            )
            if (mixed.isNotEmpty()) add(
                SoundCloudFeedShelf(
                    "mixed",
                    if (user.isBlank()) "Mixed for you" else "Mixed for $user",
                    mixed,
                ),
            )
            if (discover.isNotEmpty()) add(
                SoundCloudFeedShelf("curated", "Curated by SoundCloud", discover),
            )
            if (playlists.isNotEmpty()) add(
                SoundCloudFeedShelf("playlists", "Playlists for you", playlists),
            )
            if (feed.isNotEmpty()) add(
                SoundCloudFeedShelf("following", "Latest from people you follow", feed),
            )
        }
        _uiState.update { it.copy(feedShelves = shelves) }
        feed
    }

    private fun loadDiscover() = runLoad("Loading discover…") { client.loadDiscover() }

    private fun loadLikes(
        refreshing: Boolean = false,
        limit: Int = INITIAL_LIST_SIZE,
        loadingMore: Boolean = false,
    ) = runLoad(
        "Loading likes…",
        clearResults = !refreshing && !loadingMore,
        refreshing = refreshing,
        loadingMore = loadingMore,
    ) {
        val hits = if (client.hasSession) {
            client.loadMyLikes(limit)
        } else {
            client.loadUserLikes(_uiState.value.username, limit)
        }
        if (client.hasSession) {
            _uiState.update { state ->
                state.copy(
                    likedTrackUrls = state.likedTrackUrls +
                        hits.filter { it.kind == SoundCloudSearchHit.Kind.TRACK }.map { it.url },
                )
            }
        }
        hits
    }

    private fun loadTracks(
        refreshing: Boolean = false,
        limit: Int = INITIAL_LIST_SIZE,
        loadingMore: Boolean = false,
    ) = runLoad(
        "Loading tracks…",
        clearResults = !refreshing && !loadingMore,
        refreshing = refreshing,
        loadingMore = loadingMore,
    ) {
        if (client.hasSession) {
            client.loadMyTracks(limit)
        } else {
            client.loadUserTracks(_uiState.value.username, limit)
        }
    }

    private fun loadPlaylists(
        refreshing: Boolean = false,
        limit: Int = INITIAL_LIST_SIZE,
        loadingMore: Boolean = false,
    ) = runLoad(
        "Loading playlists…",
        clearResults = !refreshing && !loadingMore,
        refreshing = refreshing,
        loadingMore = loadingMore,
    ) {
        if (client.hasSession) {
            client.loadMyPlaylists(limit)
        } else {
            client.loadUserPlaylists(_uiState.value.username, limit)
        }
    }

    private fun runLoad(
        @Suppress("UNUSED_PARAMETER") status: String,
        cancelPrevious: Boolean = true,
        clearResults: Boolean = true,
        refreshing: Boolean = false,
        loadingMore: Boolean = false,
        block: suspend () -> List<SoundCloudSearchHit>,
    ) {
        if (cancelPrevious) {
            loadJob?.cancel()
        }
        val job = viewModelScope.launch {
            val previousCount = _uiState.value.results.size
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && !loadingMore,
                    isRefreshing = refreshing,
                    isLoadingMore = loadingMore,
                    errorMessage = null,
                    statusMessage = null,
                    results = if (clearResults) emptyList() else it.results,
                )
            }
            try {
                ensureClient()
                val hits = withContext(Dispatchers.IO) { block() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        results = hits,
                        endReached = loadingMore && hits.size <= previousCount,
                        statusMessage = null,
                    )
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.w(t, "SoundCloud load failed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = t.message ?: t::class.java.simpleName,
                        statusMessage = null,
                    )
                }
            }
        }
        if (cancelPrevious) {
            loadJob = job
        }
    }

    private fun ensureClient() {
        val id = _uiState.value.clientId
        if (id.isBlank()) throw IllegalStateException("Set client_id in Settings → Experimental")
        client.setClientIdOverride(id)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val INITIAL_FEED_SIZE = 40
        private const val INITIAL_LIST_SIZE = 40
        private const val PAGE_SIZE = 30
        private const val MAX_RESULTS = 200
    }
}
