@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.youtube.auth.YoutubeLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.youtube.YoutubeClient
import com.theveloper.pixelplay.youtube.YoutubeHit
import com.theveloper.pixelplay.youtube.YoutubeSearchFilter
import com.theveloper.pixelplay.youtube.YoutubeSection
import com.theveloper.pixelplay.youtube.YoutubeViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

private const val WIDE_SCREEN_DP = 600

@Composable
fun YoutubeScreen(
    section: YoutubeSection,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    viewModel: YoutubeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liked by viewModel.likedTracks.collectAsStateWithLifecycle()
    val saved by viewModel.savedTracks.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedSongIds.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showChangelog by remember { mutableStateOf(false) }
    var showBeta by remember { mutableStateOf(false) }
    val stablePlayer by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= WIDE_SCREEN_DP
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeight = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = bottomBarHeight + MiniPlayerHeight + 28.dp,
    )
    val currentSongId = stablePlayer.currentSong?.id
    val isPlaying = stablePlayer.isPlaying
    val browseSection = when (section) {
        YoutubeSection.SEARCH -> YoutubeSection.SEARCH
        YoutubeSection.LIKED -> YoutubeSection.LIKED
        YoutubeSection.PLAYLISTS -> YoutubeSection.PLAYLISTS
        YoutubeSection.RADIO -> YoutubeSection.RADIO
        else -> YoutubeSection.HOME
    }
    val title = when (browseSection) {
        YoutubeSection.HOME -> stringResource(R.string.youtube_home_title)
        YoutubeSection.SEARCH -> stringResource(R.string.youtube_search_title)
        YoutubeSection.LIKED -> stringResource(R.string.youtube_liked_title)
        YoutubeSection.SAVED -> stringResource(R.string.youtube_saved_title)
        YoutubeSection.PLAYLISTS -> stringResource(R.string.youtube_playlists_title)
        YoutubeSection.RADIO -> stringResource(R.string.youtube_radio_title)
    }
    val rows = when (browseSection) {
        YoutubeSection.LIKED -> (uiState.results + liked).distinctBy { it.url }
        YoutubeSection.SAVED -> saved
        else -> uiState.results
    }
    var searchActive by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(section) {
        viewModel.openSection(section)
    }

    if (uiState.collectionTitle != null) {
        BackHandler { viewModel.closeCollection() }
    }

    val playHit: (YoutubeHit, Boolean) -> Unit = { hit, asRadio ->
        if (hit.kind == YoutubeHit.Kind.COLLECTION && !asRadio) {
            viewModel.openCollection(hit)
        } else if (asRadio) {
            scope.launch {
                runCatching { viewModel.radioTracks(hit) }
                    .onSuccess { queue ->
                        val playable = queue.ifEmpty { listOf(hit) }
                        val start = viewModel.resolvePlayable(playable.first())
                        playerViewModel.playSongs(
                            songsToPlay = listOf(start),
                            startSong = start,
                            queueName = "YouTube Radio",
                        )
                        viewModel.prefetchAfter(playable.first(), playable) { queued ->
                            playerViewModel.addSongToQueue(queued)
                        }
                    }
                    .onFailure { error ->
                        viewModel.reportError(error.message ?: "Couldn't start radio")
                    }
            }
        } else {
            scope.launch {
                runCatching { viewModel.resolvePlayable(hit) }
                    .onSuccess { song ->
                        playerViewModel.playSongs(
                            songsToPlay = listOf(song),
                            startSong = song,
                            queueName = "YouTube Music",
                        )
                        viewModel.prefetchAfter(hit, rows) { queued ->
                            playerViewModel.addSongToQueue(queued)
                        }
                    }
                    .onFailure { error ->
                        viewModel.reportError(error.message ?: "Couldn't play this track")
                    }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (browseSection == YoutubeSection.HOME && uiState.collectionTitle == null) {
                YoutubeCompactHeader(
                    signedIn = account.isSignedIn,
                    onBeta = { showBeta = true },
                    onChangelog = { showChangelog = true },
                    onAccount = { navController.navigateSafely(Screen.YoutubeSettings.route) },
                    onSettings = { navController.navigateSafely(Screen.Settings.route) },
                )
            } else {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.collectionTitle ?: title,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (uiState.collectionTitle == null && browseSection == YoutubeSection.HOME) {
                            Text(
                                text = stringResource(R.string.youtube_home_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = GoogleSansRounded,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.collectionTitle != null) {
                        IconButton(onClick = viewModel::closeCollection) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.youtube_back))
                        }
                    }
                },
                actions = {
                    if (section == YoutubeSection.LIKED) {
                        TextButton(
                            onClick = {
                                if (account.isSignedIn) viewModel.signOut()
                                else context.startActivity(Intent(context, YoutubeLoginActivity::class.java))
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (account.isSignedIn) R.string.youtube_sign_out else R.string.youtube_sign_in,
                                ),
                                fontFamily = GoogleSansRounded,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            elevation = 0.dp,
                        )
                    },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (section == YoutubeSection.SEARCH && uiState.collectionTitle == null) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = uiState.query,
                                onQueryChange = viewModel::onQueryChange,
                                onSearch = { searchActive = false },
                                expanded = searchActive,
                                onExpandedChange = { searchActive = it },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.youtube_search_placeholder),
                                        fontFamily = GoogleSansRounded,
                                    )
                                },
                                trailingIcon = if (uiState.query.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.youtube_clear_search),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        },
                        expanded = false,
                        onExpandedChange = { searchActive = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {}
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        YoutubeSearchFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (filter) {
                                                YoutubeSearchFilter.SONGS -> R.string.youtube_filter_songs
                                                YoutubeSearchFilter.ALBUMS -> R.string.youtube_filter_albums
                                                YoutubeSearchFilter.PLAYLISTS -> R.string.youtube_filter_playlists
                                            }
                                        ),
                                        fontFamily = GoogleSansRounded,
                                    )
                                },
                            )
                        }
                    }
                }

                uiState.error?.let { message ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontFamily = GoogleSansRounded,
                        )
                    }
                }

                when {
                    browseSection == YoutubeSection.HOME && uiState.collectionTitle == null -> {
                        if (uiState.isLoading && uiState.shelves.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        } else if (uiState.shelves.isEmpty()) {
                            EmptyYoutubeState(message = stringResource(R.string.youtube_home_empty))
                        } else {
                            val mixHits = uiState.shelves
                                .firstOrNull { it.id == "trending" }
                                ?.items
                                ?.filter { it.kind == YoutubeHit.Kind.TRACK }
                                .orEmpty()
                                .ifEmpty {
                                    uiState.shelves.flatMap { it.items }.filter { it.kind == YoutubeHit.Kind.TRACK }
                                }
                            val mixSongs = remember(mixHits) {
                                mixHits.take(8).map { placeholderSong(it) }.toImmutableList()
                            }
                            LazyColumn(contentPadding = contentPadding) {
                                item(key = "your_mix") {
                                    YourMixHeader(
                                        song = stringResource(R.string.youtube_todays_mix),
                                        onPlayShuffled = {
                                            val seed = mixHits.shuffled()
                                            if (seed.isNotEmpty()) playHit(seed.first(), false)
                                        },
                                    )
                                }
                                if (mixSongs.isNotEmpty()) {
                                    item(key = "collage") {
                                        AlbumArtCollage(
                                            songs = mixSongs,
                                            modifier = Modifier.fillMaxWidth(),
                                            height = 280.dp,
                                            padding = 8.dp,
                                            onSongClick = { song ->
                                                mixHits.firstOrNull { YoutubeClient.songIdForUrl(it.url) == song.id }
                                                    ?.let { playHit(it, false) }
                                            },
                                        )
                                    }
                                }
                                items(uiState.shelves, key = { it.id }) { shelf ->
                                    Text(
                                        text = shelf.title,
                                        modifier = Modifier.padding(bottom = 8.dp, top = 12.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(shelf.items, key = { it.url }) { hit ->
                            YoutubeTile(
                                hit = hit,
                                modifier = Modifier.width(168.dp),
                                                isCurrent = currentSongId == YoutubeClient.songIdForUrl(hit.url),
                                                isPlaying = isPlaying,
                                                subtitle = if (shelf.startsRadio) {
                                                    stringResource(R.string.youtube_radio_from, hit.artist)
                                                } else {
                                                    hit.artist
                                                },
                                                onClick = { playHit(hit, shelf.startsRadio) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    uiState.isLoading && rows.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    rows.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EmptyYoutubeState(
                                message = when (browseSection) {
                                    YoutubeSection.LIKED -> stringResource(
                                        if (account.isSignedIn) R.string.youtube_liked_empty else R.string.youtube_liked_signed_out,
                                    )
                                    YoutubeSection.PLAYLISTS -> stringResource(R.string.youtube_playlists_empty)
                                    YoutubeSection.RADIO -> stringResource(R.string.youtube_radio_empty)
                                    YoutubeSection.SEARCH -> stringResource(R.string.youtube_search_empty)
                                    YoutubeSection.HOME -> stringResource(
                                        if (uiState.collectionTitle != null) R.string.youtube_playlist_empty else R.string.youtube_home_empty,
                                    )
                                    YoutubeSection.SAVED -> stringResource(R.string.youtube_saved_empty)
                                }
                            )
                            if (browseSection == YoutubeSection.LIKED && !account.isSignedIn) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        context.startActivity(Intent(context, YoutubeLoginActivity::class.java))
                                    },
                                ) {
                                    Text(stringResource(R.string.youtube_sign_in), fontFamily = GoogleSansRounded)
                                }
                            }
                        }
                    }
                    isWideScreen && rows.any { it.kind == YoutubeHit.Kind.COLLECTION || browseSection == YoutubeSection.HOME } -> {
                        val columns = if (configuration.screenWidthDp >= 840) 4 else 3
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = contentPadding,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(rows, key = { it.url }) { hit ->
                                YoutubeTile(
                                    hit = hit,
                                    isCurrent = currentSongId == YoutubeClient.songIdForUrl(hit.url),
                                    isPlaying = isPlaying,
                                    onClick = { playHit(hit, browseSection == YoutubeSection.RADIO) },
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (browseSection == YoutubeSection.RADIO) {
                                item(key = "radio_explainer") {
                                    Text(
                                        text = stringResource(R.string.youtube_radio_explainer),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(rows, key = { it.url }) { hit ->
                                val song = remember(hit) { placeholderSong(hit) }
                                YoutubeTrackRow(
                                    song = song,
                                    isPlaying = isPlaying && currentSongId == song.id,
                                    isCurrent = currentSongId == song.id,
                                    liked = song.id in likedIds,
                                    saved = song.id in savedIds,
                                    onPlay = { playHit(hit, browseSection == YoutubeSection.RADIO) },
                                    onLike = { viewModel.toggleLike(hit) },
                                    onSave = { viewModel.toggleSave(hit) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showChangelog) {
        ModalBottomSheet(onDismissRequest = { showChangelog = false }) {
            ChangelogBottomSheet(onDismiss = { showChangelog = false })
        }
    }
    if (showBeta) {
        ModalBottomSheet(onDismissRequest = { showBeta = false }) {
            BetaInfoBottomSheet()
        }
    }
}

@Composable
private fun YoutubeCompactHeader(
    signedIn: Boolean,
    onBeta: () -> Unit,
    onChangelog: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
) {
    val headerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val headerShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 0.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = 0.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBL = 36.dp,
        smoothnessAsPercentBL = 70,
        cornerRadiusBR = 36.dp,
        smoothnessAsPercentBR = 70,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(headerShape)
            .background(headerColor)
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onBeta,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.topbar_beta_letter) + " " + stringResource(R.string.topbar_beta_label),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        FilledIconButton(onClick = onAccount) {
            Icon(
                imageVector = if (signedIn) Icons.Rounded.AccountCircle else Icons.Rounded.Login,
                contentDescription = stringResource(R.string.youtube_sign_in),
            )
        }
        FilledIconButton(onClick = onChangelog) {
            Icon(
                painter = painterResource(R.drawable.round_newspaper_24),
                contentDescription = stringResource(R.string.topbar_cd_changelog),
            )
        }
        FilledIconButton(onClick = onSettings) {
            Icon(
                painter = painterResource(R.drawable.rounded_settings_24),
                contentDescription = stringResource(R.string.common_settings),
            )
        }
    }
}

@Composable
private fun YoutubeTrackRow(
    song: Song,
    isPlaying: Boolean,
    isCurrent: Boolean,
    liked: Boolean,
    saved: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EnhancedSongListItem(
            modifier = Modifier.weight(1f),
            song = song,
            isPlaying = isPlaying,
            isCurrentSong = isCurrent,
            showMoreOptionsButton = false,
            onMoreOptionsClick = {},
            onClick = onPlay,
        )
        IconButton(onClick = onLike) {
            Icon(
                imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (liked) R.string.youtube_unlike else R.string.youtube_like),
                tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSave) {
            Icon(
                imageVector = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(if (saved) R.string.youtube_unsave else R.string.youtube_save),
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YoutubeTile(
    hit: YoutubeHit,
    isCurrent: Boolean,
    isPlaying: Boolean,
    subtitle: String = hit.artist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                AsyncImage(
                    model = hit.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (isCurrent && isPlaying) {
                    PlayingEqIcon(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = hit.title,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyYoutubeState(message: String) {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            fontFamily = GoogleSansRounded,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun placeholderSong(hit: YoutubeHit): Song {
    val id = YoutubeClient.songIdForUrl(hit.url)
    YoutubeClient.rememberWatchUrl(id, hit.url)
    return Song(
        id = id,
        title = hit.title,
        artist = hit.artist,
        artistId = -1L,
        artists = emptyList(),
        album = "YouTube Music",
        albumId = -1L,
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
