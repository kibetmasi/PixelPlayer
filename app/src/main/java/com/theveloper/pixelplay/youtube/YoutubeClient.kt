package com.theveloper.pixelplay.youtube

import com.theveloper.pixelplay.data.model.Song
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
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

    fun loadLikedVideos(limit: Int = 50): List<YoutubeHit> {
        applySession()
        if (!session.isSignedIn()) return emptyList()
        val info = PlaylistInfo.getInfo(youtube(), "https://www.youtube.com/playlist?list=LL")
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() && it.kind == YoutubeHit.Kind.TRACK }
            .take(limit)
            .toList()
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
            artworkUrl = info.thumbnails.maxByOrNull { it.height }?.url,
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

    private fun InfoItem.toHitOrNull(): YoutubeHit? = when (this) {
        is StreamInfoItem -> YoutubeHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Unknown title" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube Music" },
            durationSec = duration.coerceAtLeast(0L),
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
            kind = YoutubeHit.Kind.TRACK,
        )
        is PlaylistInfoItem -> YoutubeHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Playlist" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube Music" },
            durationSec = 0L,
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
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

        fun rememberWatchUrl(songId: String, watchUrl: String) {
            val id = songId.trim()
            val url = watchUrl.trim()
            if (id.isNotEmpty() && url.startsWith("http")) watchUrlBySongId[id] = url
        }

        fun watchUrlForSongId(songId: String): String? = watchUrlBySongId[songId]

        fun songIdForUrl(url: String): String = "yt_${url.trim().hashCode().toUInt()}"

        private fun ensureInitialized(downloader: YoutubeDownloader) {
            if (initialized.compareAndSet(false, true)) {
                NewPipe.init(downloader)
                Timber.i("YouTube: NewPipe Extractor initialized")
            }
        }
    }
}
