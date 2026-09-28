package com.theveloper.pixelplay.youtube

/**
 * Queue for a YouTube list is the tracks on that page, from the song you tapped
 * through the end. Related / radio tracks are never mixed in.
 */
object YoutubePlaybackQueue {
    fun remainingTracks(start: YoutubeHit, source: List<YoutubeHit>): List<YoutubeHit> {
        if (start.kind != YoutubeHit.Kind.TRACK) return emptyList()
        val tracks = source.filter { it.kind == YoutubeHit.Kind.TRACK }
        val index = tracks.indexOfFirst { it.url == start.url }
        return if (index >= 0) tracks.drop(index) else listOf(start)
    }
}
