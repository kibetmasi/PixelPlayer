package com.theveloper.pixelplay.youtube

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liked YouTube Music tracks. These lists are not device files.
 */
@Singleton
class YoutubeMusicStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _liked = MutableStateFlow(read(KEY_LIKED))
    private val _recent = MutableStateFlow(read(KEY_RECENT))
    private val _likedSongIds = MutableStateFlow(idsOf(_liked.value))

    val liked: StateFlow<List<YoutubeHit>> = _liked.asStateFlow()
    val recent: StateFlow<List<YoutubeHit>> = _recent.asStateFlow()
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    fun toggleLike(hit: YoutubeHit) {
        _liked.value = toggle(_liked.value, hit)
        _likedSongIds.value = idsOf(_liked.value)
        persist(KEY_LIKED, _liked.value)
    }

    fun toggleLike(song: Song) {
        hitFromSong(song)?.let(::toggleLike)
    }

    fun rememberRecent(hit: YoutubeHit) {
        if (hit.url.isBlank() || hit.kind != YoutubeHit.Kind.TRACK) return
        val next = listOf(hit) + _recent.value.filterNot { it.url == hit.url }
        _recent.value = next.take(MAX_RECENT)
        persist(KEY_RECENT, _recent.value)
    }

    fun rememberRecent(song: Song) {
        hitFromSong(song)?.let(::rememberRecent)
    }

    private fun idsOf(hits: List<YoutubeHit>): Set<String> =
        hits.map { YoutubeClient.songIdForUrl(it.url) }.toSet()

    private fun toggle(current: List<YoutubeHit>, hit: YoutubeHit): List<YoutubeHit> {
        if (hit.url.isBlank() || hit.kind != YoutubeHit.Kind.TRACK) return current
        val without = current.filterNot { it.url == hit.url }
        return if (without.size == current.size) listOf(hit) + current else without
    }

    private fun hitFromSong(song: Song): YoutubeHit? {
        val url = YoutubeClient.watchUrlForSongId(song.id) ?: return null
        return YoutubeHit(
            url = url,
            title = song.title.ifBlank { "Unknown title" },
            artist = song.displayArtist.ifBlank { "YouTube Music" },
            durationSec = song.duration.coerceAtLeast(0L) / 1000L,
            thumbnailUrl = song.albumArtUriString,
            kind = YoutubeHit.Kind.TRACK,
        )
    }

    private fun read(key: String): List<YoutubeHit> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { decode(raw) }.getOrDefault(emptyList())
    }

    private fun persist(key: String, hits: List<YoutubeHit>) {
        prefs.edit().putString(key, encode(hits.take(MAX_TRACKS))).apply()
    }

    private fun encode(hits: List<YoutubeHit>): String {
        val array = JSONArray()
        hits.forEach { hit ->
            array.put(
                JSONObject()
                    .put("url", hit.url)
                    .put("title", hit.title)
                    .put("artist", hit.artist)
                    .put("durationSec", hit.durationSec)
                    .put("thumbnailUrl", hit.thumbnailUrl ?: "")
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<YoutubeHit> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val url = item.optString("url")
                if (url.isBlank()) continue
                add(
                    YoutubeHit(
                        url = url,
                        title = item.optString("title").ifBlank { "Unknown title" },
                        artist = item.optString("artist").ifBlank { "YouTube Music" },
                        durationSec = item.optLong("durationSec"),
                        thumbnailUrl = item.optString("thumbnailUrl").ifBlank { null },
                        kind = YoutubeHit.Kind.TRACK,
                    )
                )
            }
        }
    }

    companion object {
        private const val PREFS = "youtube_music"
        private const val KEY_LIKED = "liked"
        private const val KEY_RECENT = "recent"
        private const val MAX_TRACKS = 200
        private const val MAX_RECENT = 24
    }
}
