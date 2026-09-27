package com.theveloper.pixelplay.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class YoutubeUiState(
    val section: YoutubeSection = YoutubeSection.HOME,
    val query: String = "",
    val filter: YoutubeSearchFilter = YoutubeSearchFilter.SONGS,
    val results: List<YoutubeHit> = emptyList(),
    val shelves: List<YoutubeShelf> = emptyList(),
    val collectionTitle: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class YoutubeViewModel @Inject constructor(
    private val client: YoutubeClient,
    private val musicStore: YoutubeMusicStore,
    private val session: YoutubeSession,
) : ViewModel() {
    private val _uiState = MutableStateFlow(YoutubeUiState())
    val uiState: StateFlow<YoutubeUiState> = _uiState.asStateFlow()
    val likedTracks: StateFlow<List<YoutubeHit>> = musicStore.liked
    val savedTracks: StateFlow<List<YoutubeHit>> = musicStore.saved
    val likedSongIds: StateFlow<Set<String>> = musicStore.likedSongIds
    val savedSongIds: StateFlow<Set<String>> = musicStore.savedSongIds
    val account: StateFlow<YoutubeAccount> = session.account
    private var searchJob: Job? = null
    private var likedJob: Job? = null

    init {
        viewModelScope.launch {
            session.account.collect { account ->
                if (_uiState.value.section == YoutubeSection.LIKED) {
                    loadLiked(refreshing = account.isSignedIn)
                }
            }
        }
    }

    fun openSection(section: YoutubeSection) {
        val current = _uiState.value
        if (current.section == section && current.collectionTitle == null) {
            val hasContent = when (section) {
                YoutubeSection.HOME -> current.shelves.isNotEmpty()
                YoutubeSection.SEARCH -> current.query.trim().length < 2 || current.results.isNotEmpty()
                YoutubeSection.SAVED -> true
                else -> current.results.isNotEmpty()
            }
            if (hasContent) return
        }
        _uiState.update {
            it.copy(section = section, collectionTitle = null, error = null)
        }
        when (section) {
            YoutubeSection.HOME -> {
                if (client.feedShelves.isNotEmpty()) {
                    _uiState.update { it.copy(shelves = client.feedShelves, isLoading = false) }
                }
                loadFeed(refreshing = client.feedShelves.isNotEmpty())
            }
            YoutubeSection.SEARCH -> {
                if (_uiState.value.query.trim().length >= 2) search(_uiState.value.query)
                else _uiState.update { it.copy(results = emptyList(), shelves = emptyList(), isLoading = false) }
            }
            YoutubeSection.LIKED -> {
                if (client.likedHits.isNotEmpty()) {
                    _uiState.update { it.copy(results = client.likedHits, isLoading = false) }
                }
                loadLiked(refreshing = client.likedHits.isNotEmpty())
            }
            YoutubeSection.PLAYLISTS -> {
                if (client.playlistHits.isNotEmpty()) {
                    _uiState.update { it.copy(results = client.playlistHits, isLoading = false) }
                }
                loadPlaylists(refreshing = client.playlistHits.isNotEmpty())
            }
            YoutubeSection.RADIO -> {
                if (client.radioHits.isNotEmpty()) {
                    _uiState.update { it.copy(results = client.radioHits, isLoading = false) }
                }
                loadRadioStations(refreshing = client.radioHits.isNotEmpty())
            }
            YoutubeSection.SAVED -> _uiState.update { it.copy(results = emptyList(), shelves = emptyList(), isLoading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null, collectionTitle = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            search(query)
        }
    }

    fun setFilter(filter: YoutubeSearchFilter) {
        _uiState.update { it.copy(filter = filter, collectionTitle = null) }
        val query = _uiState.value.query
        if (query.trim().length >= 2) search(query)
    }

    fun refresh() {
        when (_uiState.value.section) {
            YoutubeSection.HOME -> loadFeed(refreshing = true)
            YoutubeSection.SEARCH -> {
                val query = _uiState.value.query
                if (query.trim().length >= 2) search(query, refreshing = true)
            }
            YoutubeSection.LIKED -> loadLiked(refreshing = true)
            YoutubeSection.PLAYLISTS -> loadPlaylists(refreshing = true)
            YoutubeSection.RADIO -> loadRadioStations(refreshing = true)
            YoutubeSection.SAVED -> Unit
        }
    }

    fun openCollection(hit: YoutubeHit) {
        if (hit.kind != YoutubeHit.Kind.COLLECTION) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, collectionTitle = hit.title, results = emptyList())
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) { client.loadCollectionTracks(hit.url) }
            }
            loaded.fold(
                onSuccess = { tracks ->
                    _uiState.update { it.copy(isLoading = false, results = tracks) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "Couldn't open this collection")
                    }
                },
            )
        }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    fun closeCollection() {
        val state = _uiState.value
        _uiState.update { it.copy(collectionTitle = null, error = null) }
        if (state.section == YoutubeSection.SEARCH && state.query.trim().length >= 2) {
            search(state.query)
        } else if (state.section == YoutubeSection.HOME) {
            loadFeed()
        } else if (state.section == YoutubeSection.PLAYLISTS) {
            loadPlaylists()
        }
    }

    suspend fun resolvePlayable(hit: YoutubeHit): Song = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(2) {
            val resolved = runCatching {
                val track = client.resolveTrack(hit.url)
                client.toSong(track, hit.url)
            }
            if (resolved.isSuccess) return@withContext resolved.getOrThrow()
            lastError = resolved.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("Couldn't play this track")
    }

    fun toggleLike(hit: YoutubeHit) {
        musicStore.toggleLike(hit)
    }

    fun toggleSave(hit: YoutubeHit) {
        musicStore.toggleSave(hit)
    }

    fun importSession(cookieHeader: String): Boolean {
        val normalized = cookieHeader.trim()
        val signedIn = listOf("LOGIN_INFO", "SAPISID", "__Secure-1PSID", "__Secure-3PSID")
            .any { key -> normalized.contains("$key=", ignoreCase = true) }
        if (!signedIn) return false
        session.save(normalized)
        if (_uiState.value.section == YoutubeSection.LIKED) loadLiked(refreshing = true)
        if (_uiState.value.section == YoutubeSection.HOME) loadFeed(refreshing = true)
        return true
    }

    fun signOut() {
        likedJob?.cancel()
        session.clear()
        client.likedHits = emptyList()
        client.feedShelves = emptyList()
        _uiState.update {
            it.copy(error = null, results = emptyList(), isLoading = false, isRefreshing = false)
        }
        when (_uiState.value.section) {
            YoutubeSection.LIKED -> loadLiked(refreshing = false)
            YoutubeSection.HOME -> loadFeed(refreshing = true)
            else -> Unit
        }
    }

    private fun loadLiked(refreshing: Boolean = false) {
        likedJob?.cancel()
        val epoch = session.generation()
        likedJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && it.results.isEmpty(),
                    isRefreshing = refreshing,
                    error = null,
                )
            }
            val local = musicStore.liked.value
            val remote = if (session.isSignedIn() && session.generation() == epoch) {
                try {
                    Result.success(withContext(Dispatchers.IO) { client.loadLikedVideos() })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            } else {
                Result.success(emptyList())
            }
            if (session.generation() != epoch) return@launch
            remote.fold(
                onSuccess = { accountLikes ->
                    if (session.generation() != epoch) return@launch
                    val account = if (session.isSignedIn()) accountLikes else emptyList()
                    val merged = (account + local).distinctBy { it.url }
                    client.likedHits = account
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = null, results = merged)
                    }
                },
                onFailure = { error ->
                    if (session.generation() != epoch || !session.isSignedIn()) {
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, error = null, results = local)
                        }
                        return@fold
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            results = local,
                            error = error.message ?: "Couldn't load liked videos",
                        )
                    }
                },
            )
        }
    }

    fun prefetchAfter(start: YoutubeHit, queue: List<YoutubeHit>, onSong: (Song) -> Unit) {
        val startIndex = queue.indexOfFirst { it.url == start.url }
        if (startIndex < 0) return
        val upcoming = queue.drop(startIndex + 1)
            .filter { it.kind == YoutubeHit.Kind.TRACK }
            .take(4)
        viewModelScope.launch {
            upcoming.forEach { hit ->
                val song = runCatching { resolvePlayable(hit) }.getOrNull() ?: return@forEach
                onSong(song)
            }
        }
    }

    suspend fun radioTracks(seed: YoutubeHit): List<YoutubeHit> = withContext(Dispatchers.IO) {
        client.loadRadio(seed.url)
    }

    private fun loadFeed(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && it.shelves.isEmpty(),
                    isRefreshing = refreshing,
                    error = null,
                    collectionTitle = null,
                )
            }
            val built = runCatching {
                withContext(Dispatchers.IO) {
                    val trending = runCatching { client.loadTrendingMusic(18) }.getOrDefault(emptyList())
                    val playlists = runCatching {
                        client.search("top music", YoutubeSearchFilter.PLAYLISTS, 12)
                    }.getOrDefault(emptyList())
                    val followed = if (session.isSignedIn()) {
                        runCatching { client.loadSubscriptionFeed(12) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    listOfNotNull(
                        followed.takeIf { it.isNotEmpty() }?.let {
                            YoutubeShelf("followed", "From your feed", it)
                        },
                        trending.takeIf { it.isNotEmpty() }?.let {
                            YoutubeShelf("trending", "Trending songs", it)
                        },
                        trending.take(10).takeIf { it.isNotEmpty() }?.let {
                            YoutubeShelf("radio", "Radio", it, startsRadio = true)
                        },
                        playlists.takeIf { it.isNotEmpty() }?.let {
                            YoutubeShelf("playlists", "Playlists", it)
                        },
                    )
                }
            }
            built.fold(
                onSuccess = { shelves ->
                    client.feedShelves = shelves
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, shelves = shelves) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Couldn't load the feed",
                        )
                    }
                },
            )
        }
    }

    private fun loadPlaylists(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !refreshing && it.results.isEmpty(), isRefreshing = refreshing, error = null, shelves = emptyList())
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    listOf("today's hits", "workout", "focus", "party").flatMap { query ->
                        runCatching { client.search(query, YoutubeSearchFilter.PLAYLISTS, 6) }.getOrDefault(emptyList())
                    }.distinctBy { it.url }
                }
            }
            loaded.fold(
                onSuccess = { hits ->
                    client.playlistHits = hits
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, results = hits) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = error.message ?: "Couldn't load playlists")
                    }
                },
            )
        }
    }

    private fun loadRadioStations(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !refreshing && it.results.isEmpty(), isRefreshing = refreshing, error = null, shelves = emptyList())
            }
            val loaded = runCatching { withContext(Dispatchers.IO) { client.loadTrendingMusic(16) } }
            loaded.fold(
                onSuccess = { hits ->
                    client.radioHits = hits
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, results = hits) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = error.message ?: "Couldn't load radio")
                    }
                },
            )
        }
    }

    private fun search(query: String, refreshing: Boolean = false) {
        val filter = _uiState.value.filter
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing,
                    isRefreshing = refreshing,
                    error = null,
                    collectionTitle = null,
                )
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) { client.search(query, filter) }
            }
            if (_uiState.value.query.trim() != query.trim()) return@launch
            loaded.fold(
                onSuccess = { hits ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, results = hits) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Search failed",
                        )
                    }
                },
            )
        }
    }
}
