package com.theveloper.pixelplay.youtube

import com.theveloper.pixelplay.data.model.Song
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YoutubeClient @Inject constructor(
    private val session: YoutubeSession,
) {
    private val downloader = YoutubeDownloader()
    private val resolveCache = ConcurrentHashMap<String, CachedResolve>()
    @Volatile var feedShelves: List<YoutubeShelf> = emptyList()
    @Volatile var playlistHits: List<YoutubeHit> = emptyList()
    @Volatile var radioHits: List<YoutubeHit> = emptyList()
    @Volatile var likedHits: List<YoutubeHit> = emptyList()
    @Volatile private var visitorId: String? = null
    @Volatile private var identityChecked = false

    private data class CachedResolve(
        val track: YoutubeResolvedTrack,
        val cachedAtMs: Long,
    )

    init {
        ensureInitialized(downloader)
    }

    fun loadTrendingMusic(limit: Int = 30): List<YoutubeHit> {
        applySession()
        val extractor = youtube().kioskList.getExtractorById("trending_music", null)
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() && it.kind == YoutubeHit.Kind.TRACK }
            .take(limit)
            .toList()
    }

    fun search(
        query: String,
        filter: YoutubeSearchFilter,
        limit: Int = 30,
    ): List<YoutubeHit> {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Query is empty" }
        applySession()
        val contentFilter = when (filter) {
            YoutubeSearchFilter.SONGS -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            YoutubeSearchFilter.ALBUMS -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            YoutubeSearchFilter.PLAYLISTS -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        }
        val handler = youtube().searchQHFactory.fromQuery(trimmed, listOf(contentFilter), "")
        val extractor = youtube().getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    fun loadLikedVideos(limit: Int = 100): List<YoutubeHit> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val hits = linkedSetOf<YoutubeHit>()
        val loggedIn = browseLiked("VLLM", hits, limit)
        if (hits.isEmpty()) {
            throw IllegalStateException(
                if (loggedIn == false) "YouTube Music did not accept this session. Sign in again from Account."
                else "YouTube Music returned no liked songs.",
            )
        }
        return hits.take(limit).toList()
    }

    fun loadLikedMusic(limit: Int = 24): List<YoutubeHit> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val hits = linkedSetOf<YoutubeHit>()
        runCatching { browseLiked("VLLM", hits, limit) }
        return hits.take(limit).toList()
    }

    /**
     * Personalized YouTube Music home (Quick picks, Listen again, Mixed for you, ...).
     * Requires a signed-in session; anonymous sessions only get regional charts.
     */
    fun loadHome(maxShelves: Int = 8): List<YoutubeShelf> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val shelves = mutableListOf<YoutubeShelf>()
        var root = musicBrowse(browseId = "FEmusic_home")
        var sectionList = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
        var page = 0
        while (sectionList != null && shelves.size < maxShelves && page < 3) {
            collectCarouselShelves(sectionList.optJSONArray("contents"), shelves)
            val continuation = findContinuation(sectionList.opt("continuations")) ?: break
            if (shelves.size >= maxShelves) break
            root = musicBrowse(continuation = continuation)
            sectionList = root.optJSONObject("continuationContents")?.optJSONObject("sectionListContinuation")
            page++
        }
        return shelves.take(maxShelves)
    }

    /** Playlists saved in the user's YouTube Music library (FEmusic_liked_playlists). */
    fun loadLibraryPlaylists(limit: Int = 60): List<YoutubeHit> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val hits = linkedSetOf<YoutubeHit>()
        var root = musicBrowse(browseId = "FEmusic_liked_playlists")
        var grid = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents ->
                (0 until contents.length()).firstNotNullOfOrNull { index ->
                    val section = contents.optJSONObject(index) ?: return@firstNotNullOfOrNull null
                    section.optJSONObject("gridRenderer") ?: section.optJSONObject("musicShelfRenderer")
                }
            }
        var page = 0
        while (grid != null && hits.size < limit && page < 4) {
            collectShelfItems(grid.optJSONArray("items") ?: grid.optJSONArray("contents"), hits, limit)
            val continuation = findContinuation(grid.opt("continuations")) ?: break
            root = musicBrowse(continuation = continuation)
            grid = root.optJSONObject("continuationContents")?.let { cont ->
                cont.optJSONObject("gridContinuation") ?: cont.optJSONObject("musicShelfContinuation")
            }
            page++
        }
        return hits.filter { it.kind == YoutubeHit.Kind.COLLECTION && !it.url.contains("list=LM") }
            .take(limit)
    }

    /** Verifies the session against InnerTube and returns the signed-in account name. */
    fun loadAccountName(): String {
        applySession()
        if (!session.isSignedIn()) return ""
        val root = musicPost("account/account_menu", """{"context":${musicContext()}}""")
        val header = root.optJSONArray("actions")
            ?.optJSONObject(0)
            ?.optJSONObject("openPopupAction")
            ?.optJSONObject("popup")
            ?.optJSONObject("multiPageMenuRenderer")
            ?.optJSONObject("header")
            ?.optJSONObject("activeAccountHeaderRenderer")
            ?: throw IllegalStateException("YouTube Music did not recognise this session.")
        return jsonText(header.optJSONObject("accountName"))
    }

    /**
     * Makes sure visitorData/dataSyncId for the signed-in account are known.
     * Mirrors KuroMusic: visitorData from sw.js_data (with cookies), dataSyncId from the page config.
     */
    fun resetIdentity() {
        identityChecked = false
        visitorId = null
    }

    fun refreshIdentity() {
        applySession()
        val account = session.account.value
        if (!account.isSignedIn) return
        identityChecked = true
        var visitor = account.visitorData
        var dataSync = account.dataSyncId
        if (visitor.isBlank()) visitor = fetchVisitorFromServiceWorker()
        if (visitor.isBlank() || dataSync.isBlank()) {
            val html = runCatching {
                downloader.getText("https://music.youtube.com/", MUSIC_USER_AGENT, withCookies = true)
            }.getOrNull().orEmpty()
            if (visitor.isBlank()) visitor = configValue(html, "VISITOR_DATA")
            if (dataSync.isBlank()) dataSync = configValue(html, "DATASYNC_ID")
        }
        session.updateIdentity(visitorData = visitor, dataSyncId = dataSync)
    }

    private fun fetchVisitorFromServiceWorker(): String {
        val body = runCatching {
            downloader.getText("https://music.youtube.com/sw.js_data", MUSIC_USER_AGENT, withCookies = true)
        }.getOrNull().orEmpty()
        if (body.length < 6) return ""
        return runCatching {
            val candidates = JSONArray(body.substring(5)).optJSONArray(0)?.optJSONArray(2) ?: return ""
            (0 until candidates.length()).asSequence()
                .mapNotNull { candidates.opt(it) as? String }
                .firstOrNull { VISITOR_REGEX.containsMatchIn(it) }
                .orEmpty()
        }.getOrDefault("")
    }

    private fun configValue(html: String, key: String): String {
        val raw = Regex(""""$key"\s*:\s*"([^"]*)"""").find(html)?.groupValues?.getOrNull(1).orEmpty()
        return raw.replace("\\/", "/").replace("\\u003d", "=").replace("\\u0026", "&")
    }

    /** Returns null when the response did not say whether the session is logged in. */
    private fun browseLiked(
        browseId: String,
        hits: MutableSet<YoutubeHit>,
        limit: Int,
    ): Boolean? {
        var loggedIn: Boolean? = null
        var continuation: String? = null
        repeat(6) {
            if (hits.size >= limit) return loggedIn
            val root = musicBrowse(browseId = browseId, continuation = continuation)
            if (loggedIn == null) loggedIn = loggedInFlag(root)
            collectLikedShelf(root, hits, limit)
            continuation = findPlaylistContinuation(root) ?: return loggedIn
        }
        return loggedIn
    }

    private fun musicBrowse(
        browseId: String? = null,
        continuation: String? = null,
        params: String? = null,
    ): JSONObject {
        val account = session.account.value
        if (account.isSignedIn && account.visitorData.isBlank() && !identityChecked) {
            identityChecked = true
            runCatching { refreshIdentity() }
        }
        val body = JSONObject()
            .put("context", JSONObject(musicContext()))
            .apply {
                if (!browseId.isNullOrBlank()) put("browseId", browseId)
                if (!params.isNullOrBlank()) put("params", params)
                if (!continuation.isNullOrBlank()) put("continuation", continuation)
            }
        val query = if (continuation.isNullOrBlank()) "" else {
            val token = java.net.URLEncoder.encode(continuation, Charsets.UTF_8.name())
            "&continuation=$token&ctoken=$token&type=next"
        }
        return musicPost("browse", body.toString(), query)
    }

    private fun musicPost(endpoint: String, body: String, query: String = ""): JSONObject {
        val json = downloader.postJson(
            url = "https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false&key=$MUSIC_API_KEY$query",
            json = body,
            origin = "https://music.youtube.com",
            clientName = MUSIC_CLIENT_ID,
            clientVersion = MUSIC_CLIENT_VERSION,
            userAgent = MUSIC_USER_AGENT,
            visitorId = currentVisitor(),
        )
        return JSONObject(json)
    }

    private fun currentVisitor(): String {
        val account = session.account.value
        if (account.isSignedIn && account.visitorData.isNotBlank()) return account.visitorData
        return musicVisitorId().ifBlank { INNER_TUNE_VISITOR }
    }

    private fun musicContext(): String {
        val account = session.account.value
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", MUSIC_CLIENT_VERSION)
            .put("gl", "US")
            .put("hl", "en")
            .put("visitorData", currentVisitor())
        val context = JSONObject().put("client", client)
        val user = JSONObject().put("lockedSafetyMode", false)
        if (account.isSignedIn && account.dataSyncId.isNotBlank()) {
            user.put("onBehalfOfUser", account.dataSyncId)
        }
        context.put("user", user)
        return context.toString()
    }

    private fun loggedInFlag(root: JSONObject): Boolean? {
        val services = root.optJSONObject("responseContext")?.optJSONArray("serviceTrackingParams") ?: return null
        for (index in 0 until services.length()) {
            val params = services.optJSONObject(index)?.optJSONArray("params") ?: continue
            for (p in 0 until params.length()) {
                val param = params.optJSONObject(p) ?: continue
                if (param.optString("key") == "logged_in") return param.optString("value") == "1"
            }
        }
        return null
    }

    private fun collectCarouselShelves(sections: JSONArray?, shelves: MutableList<YoutubeShelf>) {
        if (sections == null) return
        for (index in 0 until sections.length()) {
            val carousel = sections.optJSONObject(index)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val title = jsonText(
                carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title"),
            ).ifBlank { "For you" }
            val hits = linkedSetOf<YoutubeHit>()
            collectShelfItems(carousel.optJSONArray("contents"), hits, 30)
            if (hits.isEmpty()) continue
            val id = "home_${index}_${title.hashCode().toUInt()}"
            if (shelves.any { it.id == id }) continue
            shelves += YoutubeShelf(id = id, title = title, items = hits.toList())
        }
    }

    private fun collectShelfItems(items: JSONArray?, hits: MutableSet<YoutubeHit>, limit: Int) {
        if (items == null) return
        for (index in 0 until items.length()) {
            if (hits.size >= limit) return
            val item = items.optJSONObject(index) ?: continue
            item.optJSONObject("musicTwoRowItemRenderer")?.let { twoRowHit(it) }?.let { hits += it }
            item.optJSONObject("musicResponsiveListItemRenderer")?.let { listItemHit(it) }?.let { hits += it }
        }
    }

    private fun twoRowHit(renderer: JSONObject): YoutubeHit? {
        val title = jsonText(renderer.optJSONObject("title")).trim()
        if (title.isBlank()) return null
        val subtitle = jsonText(renderer.optJSONObject("subtitle")).trim()
        val navigation = renderer.optJSONObject("navigationEndpoint")
        val thumbnail = lastThumbnail(
            renderer.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail"),
        )
        navigation?.optJSONObject("watchEndpoint")?.let { watch ->
            val videoId = watch.optString("videoId")
            val playlistId = watch.optString("playlistId")
            if (videoId.length == 11) {
                val isMix = playlistId.startsWith("RD") && !playlistId.startsWith("RDAMVM")
                return YoutubeHit(
                    url = if (isMix) "https://www.youtube.com/watch?v=$videoId&list=$playlistId"
                    else "https://music.youtube.com/watch?v=$videoId",
                    title = title,
                    artist = subtitle.ifBlank { "YouTube Music" },
                    durationSec = 0,
                    thumbnailUrl = if (isMix) thumbnail else fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                    kind = if (isMix) YoutubeHit.Kind.COLLECTION else YoutubeHit.Kind.TRACK,
                )
            }
        }
        navigation?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }
            ?.let { playlistId ->
                return YoutubeHit(
                    url = "https://music.youtube.com/playlist?list=$playlistId",
                    title = title,
                    artist = subtitle.ifBlank { "YouTube Music" },
                    durationSec = 0,
                    thumbnailUrl = thumbnail,
                    kind = YoutubeHit.Kind.COLLECTION,
                )
            }
        val browseId = navigation?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        val playlistId = renderer.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.let { play ->
                play.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                    ?.takeIf { it.isNotBlank() }
                    ?: play.optJSONObject("watchEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() }
            }
            ?: browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
        if (playlistId.isNullOrBlank()) return null
        if (browseId.startsWith("UC") || browseId.startsWith("MPLA")) return null // artists
        return YoutubeHit(
            url = "https://music.youtube.com/playlist?list=$playlistId",
            title = title,
            artist = subtitle.ifBlank { "YouTube Music" },
            durationSec = 0,
            thumbnailUrl = thumbnail,
            kind = YoutubeHit.Kind.COLLECTION,
        )
    }

    private fun listItemHit(renderer: JSONObject): YoutubeHit? {
        val title = flexColumnText(renderer, 0).trim()
        if (title.isBlank()) return null
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            .ifBlank { firstVideoId(renderer.optJSONObject("overlay")) }
            .ifBlank {
                renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
            }
        if (videoId.length == 11) {
            return YoutubeHit(
                url = "https://music.youtube.com/watch?v=$videoId",
                title = title,
                artist = flexColumnText(renderer, 1).ifBlank { "YouTube Music" },
                durationSec = 0,
                thumbnailUrl = fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                kind = YoutubeHit.Kind.TRACK,
            )
        }
        val browseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            .orEmpty()
        val playlistId = browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            ?: firstPlaylistId(renderer.optJSONObject("overlay"))
        if (playlistId.isBlank()) return null
        return YoutubeHit(
            url = "https://music.youtube.com/playlist?list=$playlistId",
            title = title,
            artist = flexColumnText(renderer, 1).ifBlank { "YouTube Music" },
            durationSec = 0,
            thumbnailUrl = lastThumbnail(
                renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"),
            ),
            kind = YoutubeHit.Kind.COLLECTION,
        )
    }

    private fun firstPlaylistId(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("playlistId").takeIf { it.isNotBlank() }?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            when (val child = node.opt(keys.next())) {
                is JSONObject -> firstPlaylistId(child).takeIf { it.isNotBlank() }?.let { return it }
                is JSONArray -> {
                    for (index in 0 until child.length()) {
                        (child.opt(index) as? JSONObject)?.let { item ->
                            firstPlaylistId(item).takeIf { it.isNotBlank() }?.let { return it }
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun lastThumbnail(thumbnail: JSONObject?): String? {
        val list = thumbnail?.optJSONArray("thumbnails") ?: return null
        var best: String? = null
        var bestSize = -1
        for (index in 0 until list.length()) {
            val entry = list.optJSONObject(index) ?: continue
            val size = entry.optInt("width", 0)
            val url = entry.optString("url").takeIf { it.isNotBlank() } ?: continue
            if (size >= bestSize) {
                bestSize = size
                best = url
            }
        }
        return best?.let { url ->
            // Ask Google's image CDN for a larger square when the URL is a googleusercontent one.
            if (url.contains("googleusercontent.com") && url.contains("=w")) {
                url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
            } else fullBleedArtwork(url)
        }
    }

    private fun collectLikedShelf(root: JSONObject, hits: MutableSet<YoutubeHit>, limit: Int) {
        val rows = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { contents ->
                (0 until contents.length()).firstNotNullOfOrNull { index ->
                    contents.optJSONObject(index)
                        ?.optJSONObject("musicPlaylistShelfRenderer")
                        ?.optJSONArray("contents")
                }
            }
            ?: root.optJSONObject("continuationContents")
                ?.optJSONObject("musicPlaylistShelfContinuation")
                ?.optJSONArray("contents")
        if (rows == null) {
            collectVideoHits(root, hits, limit)
            return
        }
        for (index in 0 until rows.length()) {
            if (hits.size >= limit) return
            val renderer = rows.optJSONObject(index)
                ?.optJSONObject("musicResponsiveListItemRenderer")
                ?: continue
            val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            val title = flexColumnText(renderer, 0)
            if (videoId.length != 11 || title.isBlank()) continue
            hits += YoutubeHit(
                url = "https://music.youtube.com/watch?v=$videoId",
                title = title,
                artist = flexColumnText(renderer, 1).ifBlank { "YouTube Music" },
                durationSec = 0,
                thumbnailUrl = fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                kind = YoutubeHit.Kind.TRACK,
            )
        }
    }

    private fun musicVisitorId(): String {
        visitorId?.takeIf { it.isNotBlank() }?.let { return it }
        val html = runCatching {
            downloader.getText("https://music.youtube.com", MUSIC_USER_AGENT)
        }.getOrNull().orEmpty()
        val found = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (found.isNotBlank()) visitorId = found
        return found
    }

    fun loadSubscriptionFeed(limit: Int = 20): List<YoutubeHit> {
        applySession()
        val extractor = youtube().getFeedExtractor("https://www.youtube.com/feed/subscriptions")
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    fun loadRadio(watchUrl: String, limit: Int = 25): List<YoutubeHit> {
        applySession()
        val videoId = watchUrl.substringAfter("v=", "")
            .substringBefore('&')
            .substringBefore('?')
            .trim()
        require(videoId.length >= 6) { "This track can't start a radio" }
        return loadCollectionTracks("https://www.youtube.com/watch?v=$videoId&list=RD$videoId", limit)
    }

    fun loadCollectionTracks(url: String, limit: Int = 80): List<YoutubeHit> {
        applySession()
        if (url.contains("list=LM")) return loadLikedVideos(limit)
        val info = PlaylistInfo.getInfo(youtube(), url.trim())
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() && it.kind == YoutubeHit.Kind.TRACK }
            .take(limit)
            .toList()
    }

    fun resolveTrack(watchUrl: String): YoutubeResolvedTrack {
        applySession()
        val key = watchUrl.trim()
        resolveCache[key]?.takeIf {
            System.currentTimeMillis() - it.cachedAtMs < RESOLVE_CACHE_TTL_MS
        }?.let { return it.track }

        val info = StreamInfo.getInfo(youtube(), key)
        val stream = pickBestAudioStream(info.audioStreams)
            ?: throw IllegalStateException("No playable audio stream for this track.")
        val resolved = YoutubeResolvedTrack(
            watchUrl = info.url.orEmpty().ifBlank { key },
            title = info.name.orEmpty().ifBlank { "Unknown title" },
            artist = info.uploaderName.orEmpty().ifBlank { "Unknown artist" },
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            streamUrl = stream.content,
            mimeType = stream.format?.mimeType,
            artworkUrl = fullBleedArtwork(info.thumbnails.maxByOrNull { it.height }?.url),
        )
        resolveCache[key] = CachedResolve(resolved, System.currentTimeMillis())
        return resolved
    }

    fun toSong(track: YoutubeResolvedTrack, watchUrl: String = track.watchUrl): Song {
        val permalink = watchUrl.ifBlank { track.watchUrl }.trim()
        val id = songIdForUrl(permalink)
        if (permalink.startsWith("http")) rememberWatchUrl(id, permalink)
        return Song(
            id = id,
            title = track.title,
            artist = track.artist,
            artistId = -1L,
            artists = emptyList(),
            album = "YouTube Music",
            albumId = -1L,
            albumArtist = track.artist,
            path = track.streamUrl,
            contentUriString = track.streamUrl,
            albumArtUriString = track.artworkUrl,
            duration = track.durationMs,
            genre = "YouTube Music",
            mimeType = track.mimeType,
            bitrate = null,
            sampleRate = null,
        )
    }

    fun listSong(hit: YoutubeHit): Song {
        val id = songIdForUrl(hit.url)
        rememberWatchUrl(id, hit.url)
        return Song(
            id = id,
            title = hit.title,
            artist = hit.artist,
            artistId = -1L,
            artists = emptyList(),
            album = "YouTube Music",
            albumId = -1L,
            albumArtist = hit.artist,
            path = hit.url,
            contentUriString = hit.url,
            albumArtUriString = hit.thumbnailUrl,
            duration = hit.durationSec.coerceAtLeast(0L) * 1000L,
            genre = "YouTube Music",
            mimeType = null,
            bitrate = null,
            sampleRate = null,
        )
    }

    private fun youtube(): YoutubeService = ServiceList.YouTube

    private fun applySession() {
        val cookies = session.cookieHeader().takeIf { it.isNotBlank() }
        downloader.cookieHeader = cookies
        injectCookie(cookies)
    }

    private fun injectCookie(cookie: String?) {
        val helper = YoutubeParsingHelper::class.java
        val field = helper.declaredFields.firstOrNull { candidate ->
            java.lang.reflect.Modifier.isStatic(candidate.modifiers) &&
                candidate.type == String::class.java &&
                candidate.name.contains("cookie", ignoreCase = true)
        } ?: return
        field.isAccessible = true
        field.set(null, cookie)
    }

    private fun collectVideoHits(node: Any?, hits: MutableSet<YoutubeHit>, limit: Int) {
        if (hits.size >= limit || node == null) return
        when (node) {
            is JSONObject -> {
                val videoId = node.optString("videoId").ifBlank {
                    node.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
                }.ifBlank {
                    node.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        .orEmpty()
                }.ifBlank {
                    if (node.has("flexColumns")) firstVideoId(node.optJSONObject("overlay")) else ""
                }
                if (videoId.length == 11 && looksLikeTrack(node)) {
                    val title = videoTitle(node).ifBlank { "YouTube Music" }
                    hits += YoutubeHit(
                        url = "https://music.youtube.com/watch?v=$videoId",
                        title = title,
                        artist = jsonText(node.optJSONObject("longBylineText"))
                            .ifBlank { jsonText(node.optJSONObject("shortBylineText")) }
                            .ifBlank { jsonText(node.optJSONObject("ownerText")) }
                            .ifBlank { flexColumnText(node, 1) }
                            .ifBlank { "YouTube Music" },
                        durationSec = 0,
                        thumbnailUrl = fullBleedArtwork("https://i.ytimg.com/vi/$videoId/hq720.jpg"),
                        kind = YoutubeHit.Kind.TRACK,
                    )
                }
                val keys = node.keys()
                while (keys.hasNext() && hits.size < limit) {
                    collectVideoHits(node.opt(keys.next()), hits, limit)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    if (hits.size >= limit) return
                    collectVideoHits(node.opt(index), hits, limit)
                }
            }
        }
    }

    private fun looksLikeTrack(node: JSONObject): Boolean {
        return node.has("playlistItemData") ||
            node.has("flexColumns") ||
            node.has("longBylineText") ||
            node.has("shortBylineText") ||
            node.has("lengthText") ||
            node.optJSONObject("title") != null
    }

    private fun firstVideoId(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("videoId").takeIf { it.length == 11 }?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            when (val child = node.opt(keys.next())) {
                is JSONObject -> firstVideoId(child).takeIf { it.length == 11 }?.let { return it }
                is JSONArray -> {
                    for (index in 0 until child.length()) {
                        val item = child.opt(index)
                        if (item is JSONObject) {
                            firstVideoId(item).takeIf { it.length == 11 }?.let { return it }
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun findPlaylistContinuation(node: JSONObject): String? {
        val shelf = findNamedObject(node, "musicPlaylistShelfRenderer")
            ?: findNamedObject(node, "musicShelfRenderer")
            ?: findNamedObject(node, "playlistPanelRenderer")
        if (shelf != null) {
            findContinuation(shelf.opt("continuations"))?.let { return it }
            findContinuation(shelf.opt("contents"))?.let { return it }
        }
        return findContinuation(node)
    }

    private fun findNamedObject(node: Any?, name: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(name)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findNamedObject(node.opt(keys.next()), name)?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    findNamedObject(node.opt(index), name)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findContinuation(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("nextContinuationData")
                    ?.optString("continuation")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                node.optJSONObject("continuationCommand")
                    ?.optString("token")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findContinuation(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    findContinuation(node.opt(index))?.let { return it }
                }
            }
        }
        return null
    }

    private fun videoTitle(node: JSONObject): String {
        jsonText(node.optJSONObject("title")).takeIf { it.isNotBlank() }?.let { return it }
        node.optString("title").takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }?.let { return it }
        flexColumnText(node, 0).takeIf { it.isNotBlank() }?.let { return it }
        val label = node.optJSONObject("accessibility")
            ?.optJSONObject("accessibilityData")
            ?.optString("label")
            .orEmpty()
        return label.substringBefore(" by ").substringBefore(',').trim()
    }

    private fun flexColumnText(node: JSONObject, column: Int): String {
        val flex = node.optJSONArray("flexColumns") ?: return ""
        val text = flex.optJSONObject(column)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        return jsonText(text)
    }

    private fun jsonText(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return ""
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }
    }

    private fun InfoItem.toHitOrNull(): YoutubeHit? = when (this) {
        is StreamInfoItem -> YoutubeHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Unknown title" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube Music" },
            durationSec = duration.coerceAtLeast(0L),
            thumbnailUrl = fullBleedArtwork(thumbnails.maxByOrNull { it.height }?.url),
            kind = YoutubeHit.Kind.TRACK,
        )
        is PlaylistInfoItem -> YoutubeHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Playlist" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube Music" },
            durationSec = 0L,
            thumbnailUrl = fullBleedArtwork(thumbnails.maxByOrNull { it.height }?.url),
            kind = YoutubeHit.Kind.COLLECTION,
        )
        else -> null
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val progressive = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (progressive.isNotEmpty()) {
            return progressive.maxWithOrNull(
                compareBy<AudioStream> { it.averageBitrate }.thenBy { it.bitrate },
            )
        }
        val hls = streams.filter {
            it.deliveryMethod == DeliveryMethod.HLS && !it.content.contains("encrypted")
        }
        if (hls.isNotEmpty()) {
            return hls.maxWithOrNull(
                compareBy<AudioStream> { it.averageBitrate }.thenBy { it.bitrate },
            )
        }
        return streams.firstOrNull { !it.content.contains("encrypted") }
    }

    companion object {
        const val EXTRA_WATCH_URL = "com.theveloper.pixelplay.youtube.WATCH_URL"
        private val watchUrlBySongId = ConcurrentHashMap<String, String>()
        private val initialized = AtomicBoolean(false)
        private const val RESOLVE_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val MUSIC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val MUSIC_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val MUSIC_CLIENT_ID = "67"
        private const val MUSIC_CLIENT_VERSION = "1.20260804.16.00"
        private const val INNER_TUNE_VISITOR = "CgtsZG1ySnZiQWtSbyiMjuGSBg=="
        private val VISITOR_REGEX = Regex("^Cg[ts]")

        fun rememberWatchUrl(songId: String, watchUrl: String) {
            val id = songId.trim()
            val url = watchUrl.trim()
            if (id.isNotEmpty() && url.startsWith("http")) watchUrlBySongId[id] = url
        }

        fun watchUrlForSongId(songId: String): String? = watchUrlBySongId[songId]

        fun songIdForUrl(url: String): String = "yt_${url.trim().hashCode().toUInt()}"

        fun fullBleedArtwork(url: String?): String? {
            val raw = url?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val videoId = Regex("/vi/([A-Za-z0-9_-]{6,})/").find(raw)?.groupValues?.getOrNull(1)
                ?: return raw
            return "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        }

        private fun ensureInitialized(downloader: YoutubeDownloader) {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(downloader)
                Timber.i("YouTube: NewPipe Extractor initialized")
            }
        }
    }
}
