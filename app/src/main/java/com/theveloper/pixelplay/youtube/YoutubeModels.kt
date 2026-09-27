package com.theveloper.pixelplay.youtube

enum class YoutubeSection {
    HOME,
    SEARCH,
    LIKED,
    SAVED,
    PLAYLISTS,
    RADIO,
}

data class YoutubeShelf(
    val id: String,
    val title: String,
    val items: List<YoutubeHit>,
    val startsRadio: Boolean = false,
)

enum class YoutubeSearchFilter {
    SONGS,
    ALBUMS,
    PLAYLISTS,
}

data class YoutubeHit(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val kind: Kind,
) {
    enum class Kind { TRACK, COLLECTION }
}

data class YoutubeResolvedTrack(
    val watchUrl: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val streamUrl: String,
    val mimeType: String?,
    val artworkUrl: String?,
)
