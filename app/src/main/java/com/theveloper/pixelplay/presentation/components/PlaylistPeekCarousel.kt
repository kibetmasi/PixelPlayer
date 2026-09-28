package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.youtube.YoutubeHit
import kotlinx.coroutines.launch

/**
 * Playlist covers as a stacked peek: the current art is in front, and the next
 * playlist peeks from the right — same idea as a card deck.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistPeekCarousel(
    playlists: List<YoutubeHit>,
    onPlaylistClick: (YoutubeHit) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (playlists.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { playlists.size })
    val scope = rememberCoroutineScope()
    val focused = playlists.getOrNull(pagerState.currentPage) ?: playlists.first()
    val peek = 72.dp
    val spacing = 12.dp
    val coverShape = RoundedCornerShape(28.dp)

    Column(modifier = modifier.padding(contentPadding)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val hasPeek = playlists.size > 1
            val cover = minOf(
                maxWidth - if (hasPeek) peek else 0.dp,
                360.dp,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cover),
                contentPadding = PaddingValues(end = (maxWidth - cover).coerceAtLeast(0.dp)),
                pageSpacing = if (hasPeek) spacing else 0.dp,
                pageSize = PageSize.Fixed(cover),
                beyondViewportPageCount = 1,
                userScrollEnabled = hasPeek,
            ) { page ->
                val hit = playlists[page]
                val isFocused = pagerState.currentPage == page
                Surface(
                    onClick = {
                        if (isFocused) {
                            onPlaylistClick(hit)
                        } else {
                            scope.launch { pagerState.animateScrollToPage(page) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = coverShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = if (isFocused) 2.dp else 0.dp,
                    tonalElevation = if (isFocused) 2.dp else 0.dp,
                ) {
                    if (hit.thumbnailUrl.isNullOrBlank()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = hit.title,
                                modifier = Modifier.fillMaxSize(0.36f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        AsyncImage(
                            model = hit.thumbnailUrl,
                            contentDescription = hit.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = focused.url,
            label = "playlistPeekMeta",
        ) { focusedUrl ->
            val hit = playlists.firstOrNull { it.url == focusedUrl } ?: focused
            Column {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.artist.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = hit.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
