@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionCountPill
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.MultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.EditMultipleSongsSheet
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.LibraryActionRow
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.soundcloud.SoundCloudSearchHit
import com.theveloper.pixelplay.soundcloud.SoundCloudFeedShelf
import com.theveloper.pixelplay.soundcloud.SoundCloudSection
import com.theveloper.pixelplay.soundcloud.SoundCloudViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.itemsIndexed

/** Material medium width — foldables / tablets / landscape. */
private const val WIDE_SCREEN_DP = 600

@Composable
fun SoundCloudScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    viewModel: SoundCloudViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayer by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var showBatchEditSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val downloadsSelectionActive =
        isSelectionMode && uiState.section == SoundCloudSection.DOWNLOADS
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val onDownloadLongPress: (Song) -> Unit = remember(multiSelectionState, haptic) {
        { song ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            multiSelectionState.toggleSelection(song)
        }
    }
    val onDownloadSelectionToggle: (Song) -> Unit = remember(multiSelectionState) {
        { song -> multiSelectionState.toggleSelection(song) }
    }
    val sections = remember {
        SoundCloudSection.entries.filter { it != SoundCloudSection.SEARCH }
    }
    val selectedIndex = sections.indexOf(uiState.section).let { if (it < 0) -1 else it }
    val libraryNavigationMode by playerViewModel.libraryNavigationMode.collectAsStateWithLifecycle()
    val isCompactNavigation = libraryNavigationMode == LibraryNavigationMode.COMPACT_PILL
    var showSectionSwitcherSheet by remember { mutableStateOf(false) }
    val currentSection = if (selectedIndex >= 0) sections[selectedIndex] else SoundCloudSection.FEED
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= WIDE_SCREEN_DP
    val useTiles = uiState.tilesOverride ?: isWideScreen
    val remoteTrackCount = remember(uiState.results) {
        uiState.results.count { it.kind == SoundCloudSearchHit.Kind.TRACK }
    }
    val canShuffle = !uiState.isLoading && when (uiState.section) {
        SoundCloudSection.DOWNLOADS -> uiState.downloads.isNotEmpty()
        else -> remoteTrackCount > 0
    }
    val gridColumns = when {
        configuration.screenWidthDp >= 840 -> 4
        isWideScreen -> 3
        else -> 2
    }
    val contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = MiniPlayerHeight + 36.dp,
    )
    val currentSongId = stablePlayer.currentSong?.id
    val isPlaying = stablePlayer.isPlaying

    val onHitPlay: (SoundCloudSearchHit, List<SoundCloudSearchHit>) -> Unit =
        remember(viewModel, playerViewModel, scope) {
        { hit, queueHits ->
            if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                viewModel.openPlaylist(hit)
            } else {
                scope.launch {
                    try {
                        val (songs, startSong) = viewModel.resolveQueueForPlayback(hit, queueHits)
                        playerViewModel.playSongs(
                            songsToPlay = songs,
                            startSong = startSong,
                            queueName = "SoundCloud",
                        )
                        viewModel.setIdle()
                    } catch (t: Throwable) {
                        viewModel.setIdle(error = t.message ?: t::class.java.simpleName)
                    }
                }
            }
        }
    }

    val onDownloadPlay: (Song) -> Unit = remember(uiState.downloads, playerViewModel) {
        { song ->
            playerViewModel.playSongs(
                songsToPlay = uiState.downloads,
                startSong = song,
                queueName = "SoundCloud · Downloads",
            )
        }
    }

    val onShuffle: () -> Unit = {
        scope.launch {
            try {
                if (uiState.section == SoundCloudSection.DOWNLOADS) {
                    val songs = uiState.downloads
                    if (songs.isEmpty()) return@launch
                    playerViewModel.playSongsShuffled(
                        songsToPlay = songs,
                        queueName = "SoundCloud · Downloads",
                        startAtZero = true,
                    )
                } else {
                    val songs = viewModel.resolveTracksForPlayback()
                    if (songs.isEmpty()) return@launch
                    val queueName = uiState.browsingPlaylistTitle
                        ?.let { "SoundCloud · $it" }
                        ?: "SoundCloud · ${uiState.section.name.lowercase().replaceFirstChar { it.titlecase() }}"
                    playerViewModel.playSongsShuffled(
                        songsToPlay = songs,
                        queueName = queueName,
                        startAtZero = true,
                    )
                    viewModel.setIdle()
                }
            } catch (t: Throwable) {
                viewModel.setIdle(error = t.message ?: t::class.java.simpleName)
            }
        }
    }

    // Keep Back inside SoundCloud when browsing a playlist (don't jump to Library).
    BackHandler(
        enabled = downloadsSelectionActive,
    ) {
        multiSelectionState.clearSelection()
        showMultiSelectionSheet = false
    }
    BackHandler(
        enabled = !downloadsSelectionActive &&
            (uiState.canNavigateBack || uiState.browsingPlaylistUrl != null),
    ) {
        viewModel.navigateBack()
    }

    LaunchedEffect(uiState.section) {
        if (uiState.section != SoundCloudSection.DOWNLOADS) {
            multiSelectionState.clearSelection()
            showMultiSelectionSheet = false
            showPlaylistBottomSheet = false
            showBatchEditSheet = false
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            multiSelectionState.clearSelection()
        }
    }

    val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(headerContainerColor),
        topBar = {
            Column(modifier = Modifier.background(headerContainerColor)) {
                TopAppBar(
                    title = {
                        if (isCompactNavigation) {
                            LibraryNavigationPill(
                                modifier = Modifier,
                                title = stringResource(currentSection.labelRes),
                                isExpanded = showSectionSwitcherSheet,
                                showIcon = true,
                                iconRes = currentSection.iconRes,
                                pageIndex = selectedIndex.coerceAtLeast(0),
                                compressForWatchTransfer = false,
                                onClick = { showSectionSwitcherSheet = true },
                                onArrowClick = { showSectionSwitcherSheet = true },
                            )
                        } else {
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = stringResource(R.string.soundcloud_tab_title),
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 40.sp,
                                letterSpacing = 1.sp,
                            )
                        }
                    },
                    actions = {
                        FilledIconButton(
                            modifier = Modifier.padding(end = 14.dp),
                            onClick = { navController.navigateSafely(Screen.Experimental.route) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_settings_24),
                                contentDescription = stringResource(R.string.soundcloud_cd_open_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )

                if (!isCompactNavigation) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedIndex.coerceAtLeast(0),
                        containerColor = Color.Transparent,
                        edgePadding = 12.dp,
                        indicator = {},
                        divider = {},
                    ) {
                        sections.forEachIndexed { index, section ->
                            TabAnimation(
                                index = index,
                                title = section.name,
                                selectedIndex = if (selectedIndex < 0) -1 else selectedIndex,
                                onClick = { viewModel.selectSection(section) },
                            ) {
                                Text(
                                    text = stringResource(section.labelRes).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                } else {
                    CompactLibraryPagerIndicator(
                        currentIndex = selectedIndex.coerceAtLeast(0),
                        pageCount = sections.size,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .background(headerContainerColor),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 34.dp,
                    cornerRadiusTR = 34.dp,
                    cornerRadiusBL = 0.dp,
                    cornerRadiusBR = 0.dp,
                    smoothnessAsPercentTL = 60,
                    smoothnessAsPercentTR = 60,
                    smoothnessAsPercentBL = 60,
                    smoothnessAsPercentBR = 60,
                ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    AnimatedContent(
                        targetState = downloadsSelectionActive,
                        label = "SoundCloudActionRowMode",
                        transitionSpec = {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 10.dp, end = 12.dp)
                            .heightIn(min = 56.dp),
                    ) { inSelectionMode ->
                        if (inSelectionMode) {
                            SelectionActionRow(
                                selectedCount = selectedSongs.size,
                                onSelectAll = {
                                    multiSelectionState.selectAll(uiState.downloads)
                                },
                                onDeselect = { multiSelectionState.clearSelection() },
                                onOptionsClick = { showMultiSelectionSheet = true },
                            )
                        } else {
                            LibraryActionRow(
                                modifier = Modifier.fillMaxWidth(),
                                onMainActionClick = onShuffle,
                                mainActionEnabled = canShuffle,
                                iconRotation = 0f,
                                onSortClick = {},
                                showSortButton = false,
                                isPlaylistTab = false,
                                isFoldersTab = false,
                                currentFolder = null,
                                folderRootPath = "",
                                folderRootLabel = "",
                                onFolderClick = {},
                                onNavigateBack = {},
                                trailingContent = {
                                    ToggleSegmentButton(
                                        modifier = Modifier.size(42.dp),
                                        active = useTiles,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        activeCornerRadius = 26.dp,
                                        onClick = { viewModel.setUseTiles(true) },
                                        imageVector = Icons.Rounded.ViewModule,
                                        contentDesc = stringResource(R.string.soundcloud_cd_view_tiles),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    ToggleSegmentButton(
                                        modifier = Modifier.size(42.dp),
                                        active = !useTiles,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        activeCornerRadius = 26.dp,
                                        onClick = { viewModel.setUseTiles(false) },
                                        imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                        contentDesc = stringResource(R.string.soundcloud_cd_view_list),
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val keyboardController = LocalSoftwareKeyboardController.current
                    val searchBarCornerRadius = 28.dp
                    val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    )
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = uiState.query,
                                onQueryChange = viewModel::onQueryChange,
                                onSearch = { query ->
                                    if (query.isNotBlank()) {
                                        viewModel.onQueryChange(query)
                                    }
                                    keyboardController?.hide()
                                },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        stringResource(R.string.soundcloud_query_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.search_cd_search_icon),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.query.isNotBlank()) {
                                        IconButton(
                                            onClick = { viewModel.onQueryChange("") },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.search_cd_clear_search_query),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors,
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(searchBarCornerRadius)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors,
                        ),
                        content = {},
                    )

                    Spacer(modifier = Modifier.height(8.dp))

        uiState.browsingPlaylistTitle?.let { title ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Text(
                    text = stringResource(R.string.soundcloud_playlist_browsing, title),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }


        val pullToRefreshState = rememberPullToRefreshState()
        val showContentLoading =
            uiState.isLoading && uiState.resolvingKey == null && !uiState.isRefreshing
        Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Color.Transparent,
                    elevation = 0.dp,
                )
            },
        ) {
            if (
                uiState.section == SoundCloudSection.FEED &&
                uiState.browsingPlaylistUrl == null &&
                uiState.feedShelves.isNotEmpty()
            ) {
                SoundCloudFeedHome(
                    shelves = uiState.feedShelves,
                    useTiles = useTiles,
                    enabled = !uiState.isLoading,
                    resolvingKey = uiState.resolvingKey,
                    contentPadding = contentPadding,
                    isWideScreen = isWideScreen,
                    likedTrackUrls = uiState.likedTrackUrls,
                    likingUrl = uiState.likingUrl,
                    onPlay = onHitPlay,
                    onDownload = viewModel::download,
                    onToggleLike = viewModel::toggleLike,
                    onLoadMore = viewModel::loadMore,
                )
            } else if (useTiles) {
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState, uiState.results.size, uiState.downloads.size) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        val total = info.totalItemsCount
                        total > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - gridColumns
                    }
                        .distinctUntilChanged()
                        .filter { it }
                        .collect { viewModel.loadMore() }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.section == SoundCloudSection.DOWNLOADS) {
                        items(uiState.downloads, key = { it.id }) { song ->
                            SoundCloudSongTile(
                                song = song,
                                isCurrent = currentSongId == song.id,
                                isPlaying = currentSongId == song.id && isPlaying,
                                isSelectionMode = downloadsSelectionActive,
                                isSelected = song.id in selectedSongIds,
                                selectionIndex = if (downloadsSelectionActive) {
                                    multiSelectionState.getSelectionIndex(song.id)
                                } else {
                                    null
                                },
                                onPlay = { onDownloadPlay(song) },
                                onLongPress = { onDownloadLongPress(song) },
                                onSelectionToggle = { onDownloadSelectionToggle(song) },
                            )
                        }
                    } else {
                        items(uiState.results, key = { it.url + it.kind.name }) { hit ->
                            SoundCloudResultTile(
                                hit = hit,
                                enabled = !uiState.isLoading,
                                isResolving = uiState.resolvingKey == hit.url,
                                isLiked = hit.url in uiState.likedTrackUrls,
                                isLiking = uiState.likingUrl == hit.url,
                                onPlay = { onHitPlay(hit, uiState.results) },
                                onDownload = { viewModel.download(hit) },
                                onToggleLike = { viewModel.toggleLike(hit) },
                            )
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(listState, uiState.results.size, uiState.downloads.size) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val total = info.totalItemsCount
                        total > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 3
                    }
                        .distinctUntilChanged()
                        .filter { it }
                        .collect { viewModel.loadMore() }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (uiState.section == SoundCloudSection.DOWNLOADS) {
                        items(uiState.downloads, key = { it.id }) { song ->
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = currentSongId == song.id && isPlaying,
                                isCurrentSong = currentSongId == song.id,
                                showMoreOptionsButton = false,
                                onMoreOptionsClick = {},
                                isSelectionMode = downloadsSelectionActive,
                                isSelected = song.id in selectedSongIds,
                                selectionIndex = if (downloadsSelectionActive) {
                                    multiSelectionState.getSelectionIndex(song.id)
                                } else {
                                    null
                                },
                                onLongPress = { onDownloadLongPress(song) },
                                onClick = {
                                    if (downloadsSelectionActive) {
                                        onDownloadSelectionToggle(song)
                                    } else {
                                        onDownloadPlay(song)
                                    }
                                },
                            )
                        }
                    } else {
                        items(uiState.results, key = { it.url + it.kind.name }) { hit ->
                            SoundCloudResultRow(
                                hit = hit,
                                enabled = !uiState.isLoading,
                                isResolving = uiState.resolvingKey == hit.url,
                                isLiked = hit.url in uiState.likedTrackUrls,
                                isLiking = uiState.likingUrl == hit.url,
                                onPlay = { onHitPlay(hit, uiState.results) },
                                onDownload = { viewModel.download(hit) },
                                onToggleLike = { viewModel.toggleLike(hit) },
                            )
                        }
                    }
                }
            }
        }
        if (showContentLoading) {
            LoadingIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (downloadsSelectionActive && selectedSongs.isNotEmpty()) {
            SelectionCountPill(
                selectedCount = selectedSongs.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = MiniPlayerHeight + 24.dp)
                    .zIndex(2f),
            )
        }
        } // Box (content + loading overlay)
                } // Column (surface content)
            } // Surface
        } // header Column
    } // Scaffold

    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val activity = context as? android.app.Activity
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteSongIds.toSet(),
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                playerViewModel.playSelectedSongs(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                showMultiSelectionSheet = false
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                showMultiSelectionSheet = false
            },
            onAddToPlaylist = {
                playlistSheetSongs = selectedSongs
                showMultiSelectionSheet = false
                showPlaylistBottomSheet = true
            },
            onToggleLikeAll = { shouldLike ->
                if (shouldLike) {
                    playerViewModel.likeSelectedSongs(selectedSongs)
                } else {
                    playerViewModel.unlikeSelectedSongs(selectedSongs)
                }
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedAsZip(selectedSongs)
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onComplete ->
                activity?.let {
                    playerViewModel.deleteSelectedFromDevice(it, selectedSongs) {
                        showMultiSelectionSheet = false
                        viewModel.refreshDownloads()
                        onComplete(true)
                    }
                }
            },
            onBatchEdit = {
                showMultiSelectionSheet = false
                showBatchEditSheet = true
            },
        )
    }

    if (showPlaylistBottomSheet && playlistSheetSongs.isNotEmpty()) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = {
                showPlaylistBottomSheet = false
                playlistSheetSongs = emptyList()
            },
            bottomBarHeight = bottomBarHeightDp,
            playerViewModel = playerViewModel,
            playlistViewModel = playlistViewModel,
        )
    }

    if (showBatchEditSheet && selectedSongs.isNotEmpty()) {
        EditMultipleSongsSheet(
            visible = showBatchEditSheet,
            songs = selectedSongs,
            onDismiss = { showBatchEditSheet = false },
            onSave = { songs, title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.saveBatchMetadata(
                    songs = songs,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    composer = composer,
                    genre = genre,
                    lyrics = lyrics,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    replayGainTrackGainDb = replayGainTrackGainDb,
                    replayGainAlbumGainDb = replayGainAlbumGainDb,
                    coverArtUpdate = coverArtUpdate,
                )
                showBatchEditSheet = false
                multiSelectionState.clearSelection()
            },
        )
    }

    if (showSectionSwitcherSheet) {
        SoundCloudSectionSwitcherSheet(
            sections = sections,
            currentIndex = selectedIndex.coerceAtLeast(0),
            onSectionSelected = { index ->
                sections.getOrNull(index)?.let(viewModel::selectSection)
                showSectionSwitcherSheet = false
            },
            onDismiss = { showSectionSwitcherSheet = false },
        )
    }
}

@Composable
private fun SoundCloudFeedHome(
    shelves: List<SoundCloudFeedShelf>,
    useTiles: Boolean,
    enabled: Boolean,
    resolvingKey: String?,
    contentPadding: PaddingValues,
    isWideScreen: Boolean,
    likedTrackUrls: Set<String>,
    likingUrl: String?,
    onPlay: (SoundCloudSearchHit, List<SoundCloudSearchHit>) -> Unit,
    onDownload: (SoundCloudSearchHit) -> Unit,
    onToggleLike: (SoundCloudSearchHit) -> Unit,
    onLoadMore: () -> Unit,
) {
    var selectedShelfId by remember(shelves.map { it.id }) {
        mutableStateOf(shelves.firstOrNull()?.id.orEmpty())
    }
    val visibleShelves = if (isWideScreen) {
        shelves
    } else {
        listOfNotNull(shelves.firstOrNull { it.id == selectedShelfId } ?: shelves.firstOrNull())
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, shelves.sumOf { it.items.size }) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            total > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 2
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (!isWideScreen) {
            item(key = "feed-submenu") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shelves, key = { it.id }) { shelf ->
                        FilterChip(
                            selected = shelf.id == selectedShelfId,
                            onClick = { selectedShelfId = shelf.id },
                            label = {
                                Text(
                                    text = shelf.title,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                        )
                    }
                }
            }
        }
        items(visibleShelves, key = { it.id }) { shelf ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = shelf.title,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (useTiles) {
                    val tileColumns = if (isWideScreen) 3 else 2
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        shelf.items.chunked(tileColumns).forEach { rowHits ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowHits.forEach { hit ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        SoundCloudResultTile(
                                            hit = hit,
                                            enabled = enabled,
                                            isResolving = resolvingKey == hit.url,
                                            isLiked = hit.url in likedTrackUrls,
                                            isLiking = likingUrl == hit.url,
                                            onPlay = { onPlay(hit, shelf.items) },
                                            onDownload = { onDownload(hit) },
                                            onToggleLike = { onToggleLike(hit) },
                                        )
                                    }
                                }
                                repeat(tileColumns - rowHits.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        shelf.items.take(6).forEach { hit ->
                            SoundCloudResultRow(
                                hit = hit,
                                enabled = enabled,
                                isResolving = resolvingKey == hit.url,
                                isLiked = hit.url in likedTrackUrls,
                                isLiking = likingUrl == hit.url,
                                onPlay = { onPlay(hit, shelf.items) },
                                onDownload = { onDownload(hit) },
                                onToggleLike = { onToggleLike(hit) },
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundCloudSectionSwitcherSheet(
    sections: List<SoundCloudSection>,
    currentIndex: Int,
    onSectionSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.soundcloud_tabs_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded,
            )
            Text(
                text = stringResource(R.string.soundcloud_tabs_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
            ) {
                itemsIndexed(
                    items = sections,
                    key = { index, section -> "${section.name}-$index" },
                ) { index, section ->
                    SoundCloudSectionGridItem(
                        section = section,
                        isSelected = index == currentIndex,
                        onClick = { onSectionSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundCloudSectionGridItem(
    section: SoundCloudSection,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconContainer =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondaryContainer
    val textColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconContainer.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = section.iconRes),
                    contentDescription = stringResource(section.labelRes),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Text(
                text = stringResource(section.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
            )
        }
    }
}

private val SoundCloudSection.iconRes: Int
    get() = when (this) {
        SoundCloudSection.FEED -> R.drawable.rounded_home_24
        SoundCloudSection.DISCOVER -> R.drawable.rounded_instant_mix_24
        SoundCloudSection.SEARCH -> R.drawable.rounded_search_24
        SoundCloudSection.LIKES -> R.drawable.rounded_favorite_24
        SoundCloudSection.TRACKS -> R.drawable.rounded_music_note_24
        SoundCloudSection.PLAYLISTS -> R.drawable.rounded_playlist_play_24
        SoundCloudSection.DOWNLOADS -> R.drawable.rounded_drive_export_24
    }

private val SoundCloudSection.labelRes: Int
    get() = when (this) {
        SoundCloudSection.FEED -> R.string.soundcloud_pill_feed
        SoundCloudSection.DISCOVER -> R.string.soundcloud_pill_discover
        SoundCloudSection.SEARCH -> R.string.soundcloud_pill_search
        SoundCloudSection.LIKES -> R.string.soundcloud_pill_likes
        SoundCloudSection.TRACKS -> R.string.soundcloud_pill_tracks
        SoundCloudSection.PLAYLISTS -> R.string.soundcloud_pill_playlists
        SoundCloudSection.DOWNLOADS -> R.string.soundcloud_pill_downloads
    }

@Composable
private fun SoundCloudSongTile(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(isSelectionMode) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = {
                        if (isSelectionMode) onSelectionToggle() else onPlay()
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (!song.albumArtUriString.isNullOrBlank()) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(28.dp)
                            .align(Alignment.Center),
                    )
                }
            }
            if (isSelectionMode && isSelected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (selectionIndex ?: 1).toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (isCurrent && !isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = song.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SoundCloudResultTile(
    hit: SoundCloudSearchHit,
    enabled: Boolean,
    isResolving: Boolean = false,
    isLiked: Boolean = false,
    isLiking: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleLike: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isResolving, onClick = onPlay),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (!hit.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = hit.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                            Icons.Rounded.PlaylistPlay
                        } else {
                            Icons.Rounded.Cloud
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .padding(28.dp)
                            .align(Alignment.Center),
                    )
                }
            }
            if (isResolving) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center),
                    )
                }
            } else if (hit.kind == SoundCloudSearchHit.Kind.TRACK) {
                IconButton(
                    onClick = onToggleLike,
                    enabled = enabled && !isLiking,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isLiking) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(
                                if (isLiked) R.string.soundcloud_unlike else R.string.soundcloud_like,
                            ),
                        )
                    }
                }
                IconButton(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.soundcloud_download),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hit.title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = hit.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SoundCloudResultRow(
    hit: SoundCloudSearchHit,
    enabled: Boolean,
    isResolving: Boolean = false,
    isLiked: Boolean = false,
    isLiking: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleLike: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !isResolving, onClick = onPlay),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                if (!hit.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = hit.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                                Icons.Rounded.PlaylistPlay
                            } else {
                                Icons.Rounded.Cloud
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (isResolving) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LoadingIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = hit.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val meta = when {
                    hit.kind == SoundCloudSearchHit.Kind.PLAYLIST && hit.streamCount > 0 ->
                        stringResource(R.string.soundcloud_playlist_count, hit.streamCount)
                    hit.durationSec > 0 -> formatDuration(hit.durationSec)
                    else -> null
                }
                meta?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hit.kind == SoundCloudSearchHit.Kind.TRACK && !isResolving) {
                IconButton(onClick = onToggleLike, enabled = enabled && !isLiking) {
                    if (isLiking) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(
                                if (isLiked) R.string.soundcloud_unlike else R.string.soundcloud_like,
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onDownload, enabled = enabled) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.soundcloud_download),
                    )
                }
            }
            if (isResolving) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                        Icons.Rounded.PlaylistPlay
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = stringResource(R.string.soundcloud_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
