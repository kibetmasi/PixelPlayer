package com.theveloper.pixelplay.youtube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YoutubePlaybackQueueTest {

    @Test
    fun remainingTracks_startsAtTappedSongAndKeepsListOrder() {
        val tracks = listOf(
            track("a", "One"),
            track("b", "Two"),
            track("c", "Three"),
        )

        val remaining = YoutubePlaybackQueue.remainingTracks(tracks[1], tracks)

        assertThat(remaining.map { it.title }).containsExactly("Two", "Three").inOrder()
    }

    @Test
    fun remainingTracks_skipsCollectionRows() {
        val mixed = listOf(
            YoutubeHit(
                url = "pl",
                title = "Album",
                artist = "",
                durationSec = 0,
                thumbnailUrl = null,
                kind = YoutubeHit.Kind.COLLECTION,
            ),
            track("a", "One"),
            track("b", "Two"),
        )

        val remaining = YoutubePlaybackQueue.remainingTracks(mixed[1], mixed)

        assertThat(remaining.map { it.title }).containsExactly("One", "Two").inOrder()
    }

    @Test
    fun remainingTracks_usesOnlyTheTappedSongWhenItIsNotOnThePage() {
        val remaining = YoutubePlaybackQueue.remainingTracks(
            start = track("z", "Other"),
            source = listOf(track("a", "One")),
        )

        assertThat(remaining.map { it.title }).containsExactly("Other")
    }

    private fun track(id: String, title: String) = YoutubeHit(
        url = "https://music.youtube.com/watch?v=$id",
        title = title,
        artist = "Artist",
        durationSec = 0,
        thumbnailUrl = null,
        kind = YoutubeHit.Kind.TRACK,
    )
}
