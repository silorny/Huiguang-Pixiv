package moe.huiguang.pixiv.ui.bookmarks

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.BookmarkItem
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.FavoriteStore
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.CenteredReadableBox
import moe.huiguang.pixiv.design.rememberAdaptiveGridColumns
import moe.huiguang.pixiv.design.rememberIsLandscapeSplit
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.FloatingSegmentedControl
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryErrorState
import moe.huiguang.pixiv.design.components.SwipeActionRow
import moe.huiguang.pixiv.design.components.SwipeRevealController
import moe.huiguang.pixiv.design.components.WaterfallSkeleton
import moe.huiguang.pixiv.design.components.WorkRow
import moe.huiguang.pixiv.design.components.WorkRowBody
import moe.huiguang.pixiv.design.components.WorkRowListSkeleton
import moe.huiguang.pixiv.design.components.blockParentHorizontalDrag
import moe.huiguang.pixiv.design.components.dismissSwipeOnOutsideTap
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.workMeta

private val PurplePrivateDay = Color(0xFF8E44EC)
private val PurplePrivateNight = Color(0xFF9E66FF)

private const val MENU_FILTER_ALL = 0
private const val MENU_FILTER_ILLUST = 1
private const val MENU_FILTER_MANGA = 2
private const val MENU_FILTER_NOVEL = 3
private const val MENU_VISIBILITY_PUBLIC = 10
private const val MENU_VISIBILITY_PRIVATE = 11
private const val MENU_LAYOUT_LIST = 20
private const val MENU_LAYOUT_WATERFALL = 21

private data class BookmarkFeedState(
    val items: List<BookmarkItem>? = null,
    val error: String? = null,
    val offset: Int = 0,
    val ended: Boolean = false,
    val loadingMore: Boolean = false,
)

/**
 * Bookmarks: displays the signed-in user's Pixiv public/private cloud bookmarks,
 * keeping both visibility pages alive in a persistent [HorizontalPager] so
 * SegmentedControl switches never recreate scroll views or trigger redundant reloads.
 */
@Composable
fun BookmarksScreen(
    api: PixivApi,
    settings: SettingsStore,
    favorites: FavoriteStore,
    onBack: () -> Unit,
    onOpen: (String, String) -> Unit,
    targetUserId: String? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val effectBackdrop = rememberCouiBackdrop()
    val state by settings.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val isOtherUser = targetUserId != null
    val pageCount = if (isOtherUser) 1 else 2
    var savedVisibility by rememberSaveable { mutableIntStateOf(0) }
    val visibilityPager = rememberPagerState(
        initialPage = if (isOtherUser) 0 else savedVisibility.coerceIn(0, 1),
        pageCount = { pageCount },
    )
    val activeVisibility by remember {
        derivedStateOf { if (isOtherUser) 0 else visibilityPager.currentPage }
    }
    LaunchedEffect(visibilityPager) {
        snapshotFlow { visibilityPager.settledPage }
            .distinctUntilChanged()
            .collect { savedVisibility = it }
    }

    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 = all, 1 = illust, 2 = manga, 3 = novel
    var isWaterfall by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var publicFeed by remember(targetUserId) { mutableStateOf(BookmarkFeedState()) }
    var privateFeed by remember(targetUserId) { mutableStateOf(BookmarkFeedState()) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val swipeController = remember { SwipeRevealController() }
    val listStates = List(pageCount) { index ->
        rememberSaveable(targetUserId, index, saver = LazyListState.Saver) { LazyListState() }
    }
    val gridStates = List(pageCount) { index ->
        rememberSaveable(targetUserId, index, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    DisposableEffect(lifecycleOwner) {
        var leftForeground = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> leftForeground = true
                Lifecycle.Event.ON_RESUME -> if (leftForeground) {
                    leftForeground = false
                    resumeTick++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeVisibility, filter, isWaterfall) {
        swipeController.close()
    }

    val session = state.session
    val effectiveUid = targetUserId ?: session?.userId

    suspend fun fetchInitial(privateOnly: Boolean, force: Boolean) {
        if (session == null) return
        val uid = effectiveUid ?: return
        val current = if (privateOnly) privateFeed else publicFeed
        if (!force && (current.items != null || current.error != null)) return
        if (privateOnly) privateFeed = current.copy(error = null)
        else publicFeed = current.copy(error = null)
        runCatching { api.bookmarks(uid, limit = 48, offset = 0, privateOnly = privateOnly) }
            .onSuccess { firstPage ->
                val unique = firstPage.distinctBy { "${it.kind}-${it.id}" }
                val updated = BookmarkFeedState(
                    items = unique,
                    error = null,
                    offset = 48,
                    ended = unique.isEmpty(),
                    loadingMore = false,
                )
                if (privateOnly) privateFeed = updated else publicFeed = updated
            }
            .onFailure { err ->
                if (err is CancellationException) throw err
                val latest = if (privateOnly) privateFeed else publicFeed
                if (latest.items == null) {
                    val errored = latest.copy(error = err.message ?: "error")
                    if (privateOnly) privateFeed = errored else publicFeed = errored
                }
            }
    }

    // Load initial page only when needed; toggling visibility tabs never re-fetches loaded feeds.
    LaunchedEffect(effectiveUid, session?.userId, activeVisibility) {
        val privateOnly = !isOtherUser && activeVisibility == 1
        fetchInitial(privateOnly = privateOnly, force = false)
    }

    LaunchedEffect(resumeTick) {
        if (resumeTick > 0) {
            val privateOnly = !isOtherUser && activeVisibility == 1
            fetchInitial(privateOnly = privateOnly, force = true)
        }
    }

    fun loadMore(targetVisibility: Int) {
        if (session == null) return
        val uid = effectiveUid ?: return
        val privateOnly = !isOtherUser && targetVisibility == 1
        val current = if (privateOnly) privateFeed else publicFeed
        if (current.loadingMore || current.ended) return
        if (privateOnly) privateFeed = current.copy(loadingMore = true)
        else publicFeed = current.copy(loadingMore = true)
        scope.launch {
            runCatching { api.bookmarks(uid, limit = 48, offset = current.offset, privateOnly = privateOnly) }
                .onSuccess { nextPage ->
                    val latest = if (privateOnly) privateFeed else publicFeed
                    val existing = latest.items.orEmpty()
                    val known = existing.mapTo(HashSet()) { "${it.kind}-${it.id}" }
                    val fresh = nextPage.filter { known.add("${it.kind}-${it.id}") }
                    val updated = latest.copy(
                        items = existing + fresh,
                        offset = latest.offset + 48,
                        ended = nextPage.isEmpty() || fresh.isEmpty(),
                        loadingMore = false,
                    )
                    if (privateOnly) privateFeed = updated else publicFeed = updated
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    val latest = if (privateOnly) privateFeed else publicFeed
                    val updated = latest.copy(loadingMore = false, ended = true)
                    if (privateOnly) privateFeed = updated else publicFeed = updated
                }
        }
    }

    fun toggleVisibility(item: BookmarkItem, fromPrivate: Boolean) {
        val uid = session?.userId ?: return
        val makePrivate = !fromPrivate
        val sourceState = if (fromPrivate) privateFeed else publicFeed
        val sourceList = sourceState.items ?: return
        val sourceIndex = sourceList.indexOfFirst { it.kind == item.kind && it.id == item.id }
        if (sourceIndex < 0) return

        val remainingSource = sourceList.filterNot { it.kind == item.kind && it.id == item.id }
        if (fromPrivate) {
            privateFeed = sourceState.copy(items = remainingSource)
        } else {
            publicFeed = sourceState.copy(items = remainingSource)
        }

        val targetState = if (makePrivate) privateFeed else publicFeed
        val targetItems = targetState.items
        val prependedToTarget = targetItems != null &&
            targetItems.none { it.kind == item.kind && it.id == item.id }
        if (prependedToTarget) {
            val nextTarget = listOf(item) + targetItems.orEmpty()
            if (makePrivate) {
                privateFeed = targetState.copy(items = nextTarget)
            } else {
                publicFeed = targetState.copy(items = nextTarget)
            }
        }

        scope.launch {
            val ok = runCatching {
                api.setBookmarkVisibility(
                    id = item.id,
                    novel = item.kind == HistoryEntry.KIND_NOVEL,
                    bookmarkId = item.bookmarkId,
                    makePrivate = makePrivate,
                    userId = uid,
                )
            }.getOrDefault(false)
            if (ok) {
                val msgRes = if (makePrivate) {
                    R.string.bookmarks_moved_private
                } else {
                    R.string.bookmarks_moved_public
                }
                Toast.makeText(context, resources.getString(msgRes), Toast.LENGTH_SHORT).show()
            } else {
                val latestSource = if (fromPrivate) privateFeed else publicFeed
                val currentItems = latestSource.items.orEmpty()
                if (currentItems.none { it.kind == item.kind && it.id == item.id }) {
                    val restored = currentItems.toMutableList().apply {
                        add(sourceIndex.coerceIn(0, size), item)
                    }
                    if (fromPrivate) {
                        privateFeed = latestSource.copy(items = restored)
                    } else {
                        publicFeed = latestSource.copy(items = restored)
                    }
                }
                if (prependedToTarget) {
                    val latestTarget = if (makePrivate) privateFeed else publicFeed
                    val revertedTarget = latestTarget.items?.filterNot { it.kind == item.kind && it.id == item.id }
                    if (makePrivate) {
                        privateFeed = latestTarget.copy(items = revertedTarget)
                    } else {
                        publicFeed = latestTarget.copy(items = revertedTarget)
                    }
                }
                Toast.makeText(context, resources.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val visibilityLabels = listOf(
        stringResource(R.string.bookmarks_visibility_public),
        stringResource(R.string.bookmarks_visibility_private),
    )
    val isLandscapeSplit = rememberIsLandscapeSplit()
    val showVisibilitySwitcher = !isOtherUser && session != null

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .dismissSwipeOnOutsideTap(swipeController),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(effectBackdrop)
                .background(colors.background),
        )
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(colors.background),
        ) {
            HorizontalPager(
                state = visibilityPager,
                beyondViewportPageCount = 1,
                userScrollEnabled = isLandscapeSplit && !isOtherUser,
                modifier = Modifier.fillMaxSize(),
            ) { selectedVisibility ->
                val isPrivate = !isOtherUser && selectedVisibility == 1
                val feed = if (isPrivate) privateFeed else publicFeed
                val visibleItems = remember(feed.items, filter) {
                    feed.items?.filter { matchesBookmarkFilter(it, filter) }
                }
                BookmarksContent(
                    session = session != null,
                    showVisibilitySwitch = !isOtherUser,
                    isOtherUser = isOtherUser,
                    isPrivate = isPrivate,
                    isWaterfall = isWaterfall,
                    revealR18 = state.revealR18,
                    items = visibleItems,
                    error = feed.error,
                    loadingMore = feed.loadingMore,
                    listState = listStates[selectedVisibility],
                    gridState = gridStates[selectedVisibility],
                    topPadding = statusTop + 56.dp + 8.dp,
                    bottomPadding = navBottom + if (!isLandscapeSplit && showVisibilitySwitcher) 88.dp else 24.dp,
                    effectBackdrop = effectBackdrop,
                    swipeController = swipeController,
                    onOpen = onOpen,
                    onToggleVisibility = { item -> toggleVisibility(item, fromPrivate = isPrivate) },
                    onLoadMore = { loadMore(selectedVisibility) },
                    onRetry = {
                        scope.launch { fetchInitial(privateOnly = isPrivate, force = true) }
                    },
                )
            }
        }

        if (!isLandscapeSplit && showVisibilitySwitcher) {
            val navItems = listOf(
                CouiNavigationItem(
                    title = stringResource(R.string.bookmarks_visibility_public),
                    icon = CouiIcons.Outlined.Favorite,
                    selectedIcon = CouiIcons.Filled.Favorite,
                ),
                CouiNavigationItem(
                    title = stringResource(R.string.bookmarks_visibility_private),
                    icon = CouiIcons.Filled.Lock,
                    selectedIcon = CouiIcons.Filled.Lock,
                ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                NavigationBar(
                    backdrop = backdrop,
                    items = navItems,
                    selectedIndex = activeVisibility,
                    onSelection = { target ->
                        scope.launch { visibilityPager.animateScrollToPage(target) }
                    },
                )
            }
        }

        val landscapeSwitcher: (@Composable () -> Unit)? = if (isLandscapeSplit && showVisibilitySwitcher) {
            {
                FloatingSegmentedControl(
                    labels = visibilityLabels,
                    selectedIndex = activeVisibility,
                    onSelected = { target ->
                        scope.launch { visibilityPager.animateScrollToPage(target) }
                    },
                    backdrop = backdrop,
                    dark = dark,
                )
            }
        } else {
            null
        }
        FloatingSecondaryTopBar(
            title = stringResource(R.string.bookmarks_title),
            backdrop = backdrop,
            onBack = onBack,
            afterTitle = if (state.leftHanded && landscapeSwitcher != null) {
                { landscapeSwitcher() }
            } else {
                null
            },
            trailing = {
                if (!state.leftHanded && landscapeSwitcher != null) {
                    landscapeSwitcher()
                    Spacer(Modifier.width(8.dp))
                }
                Box {
                    CircleIconButton(
                        icon = CouiIcons.Filled.More,
                        contentDescription = stringResource(R.string.search_filter_menu),
                        onClick = { menuExpanded = true },
                        backdrop = backdrop,
                        dark = dark,
                        blurRadius = 20f,
                    )
                    val menuItems = buildList {
                        if (!isOtherUser) {
                            add(
                                CouiMenuItem(
                                    value = MENU_VISIBILITY_PUBLIC,
                                    text = stringResource(R.string.bookmarks_visibility_public),
                                    selected = activeVisibility == 0,
                                ),
                            )
                            add(
                                CouiMenuItem(
                                    value = MENU_VISIBILITY_PRIVATE,
                                    text = stringResource(R.string.bookmarks_visibility_private),
                                    selected = activeVisibility == 1,
                                ),
                            )
                        }
                        add(
                            CouiMenuItem(
                                value = MENU_LAYOUT_LIST,
                                text = stringResource(R.string.bookmarks_layout_list),
                                selected = !isWaterfall,
                                dividerBefore = !isOtherUser,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = MENU_LAYOUT_WATERFALL,
                                text = stringResource(R.string.bookmarks_layout_waterfall),
                                selected = isWaterfall,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = MENU_FILTER_ALL,
                                text = stringResource(R.string.filter_all),
                                selected = filter == 0,
                                dividerBefore = true,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = MENU_FILTER_ILLUST,
                                text = stringResource(R.string.filter_illust),
                                selected = filter == 1,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = MENU_FILTER_MANGA,
                                text = stringResource(R.string.filter_manga),
                                selected = filter == 2,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = MENU_FILTER_NOVEL,
                                text = stringResource(R.string.filter_novel),
                                selected = filter == 3,
                            ),
                        )
                    }
                    Menu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        headerText = stringResource(R.string.search_menu_category_header),
                        headerTextAlign = TextAlign.Start,
                        modifier = Modifier.widthIn(min = 168.dp),
                        offset = DpOffset(0.dp, 8.dp),
                        items = menuItems,
                        onItemClick = { item ->
                            menuExpanded = false
                            when (item.value) {
                                MENU_VISIBILITY_PUBLIC -> scope.launch { visibilityPager.animateScrollToPage(0) }
                                MENU_VISIBILITY_PRIVATE -> scope.launch { visibilityPager.animateScrollToPage(1) }
                                MENU_LAYOUT_LIST -> isWaterfall = false
                                MENU_LAYOUT_WATERFALL -> isWaterfall = true
                                MENU_FILTER_ALL,
                                MENU_FILTER_ILLUST,
                                MENU_FILTER_MANGA,
                                MENU_FILTER_NOVEL,
                                -> filter = item.value
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun BookmarksContent(
    session: Boolean,
    showVisibilitySwitch: Boolean,
    isOtherUser: Boolean,
    isPrivate: Boolean,
    isWaterfall: Boolean,
    revealR18: Boolean,
    items: List<BookmarkItem>?,
    error: String?,
    loadingMore: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    topPadding: Dp,
    bottomPadding: Dp,
    effectBackdrop: CouiBackdrop,
    swipeController: SwipeRevealController,
    onOpen: (String, String) -> Unit,
    onToggleVisibility: (BookmarkItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val overscroll = rememberCouiOverscrollEffect()
    val latestItems by rememberUpdatedState(items)

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) swipeController.close()
    }

    LaunchedEffect(listState, gridState, isWaterfall) {
        snapshotFlow {
            if (latestItems.isNullOrEmpty()) {
                false
            } else if (isWaterfall) {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 1 && last >= info.totalItemsCount - 5
            } else {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 1 && last >= info.totalItemsCount - 5
            }
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    val emptyMessage = stringResource(
        when {
            isOtherUser -> R.string.bookmarks_author_empty
            isPrivate -> R.string.bookmarks_private_empty
            else -> R.string.bookmarks_cloud_empty
        },
    )

    when {
        !session -> EmptyHint(stringResource(R.string.bookmarks_need_login))
        isWaterfall -> {
            val columns = rememberAdaptiveGridColumns()
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .overscroll(overscroll),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine, key = "top-anchor") {
                    Spacer(Modifier.height(0.dp))
                }
                when {
                    error != null -> item(span = StaggeredGridItemSpan.FullLine, key = "error") {
                        ErrorHint(error = error, backdrop = effectBackdrop, onRetry = onRetry)
                    }
                    items == null -> item(span = StaggeredGridItemSpan.FullLine, key = "skeleton") {
                        WaterfallSkeleton(topPadding = 8.dp, bottomPadding = bottomPadding)
                    }
                    items.isEmpty() -> item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                        InlineEmptyHint(emptyMessage)
                    }
                    else -> {
                        items(
                            items = items,
                            key = { "${if (isPrivate) "priv" else "pub"}-${it.kind}-${it.id}" },
                            contentType = { if (it.kind == HistoryEntry.KIND_NOVEL) "novel" else "artwork" },
                        ) { item ->
                            ArtworkCard(
                                item = item.toSearchItem(),
                                revealR18 = revealR18,
                                modifier = Modifier.blockParentHorizontalDrag(),
                                onClick = { onOpen(item.kind, item.id) },
                            )
                        }
                        if (loadingMore) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "loading-more") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_loading),
                                        kind = CouiTextKind.ItemSummary,
                                        color = colors.summary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            val actionColor = if (isPrivate) {
                if (dark) CouiColor.Night.GREEN else CouiColor.Day.GREEN
            } else {
                if (dark) PurplePrivateNight else PurplePrivateDay
            }
            val actionLabel = stringResource(
                if (isPrivate) R.string.bookmarks_action_make_public else R.string.bookmarks_action_make_private,
            )
            val confirmLabel = stringResource(
                if (isPrivate) {
                    R.string.bookmarks_action_confirm_public
                } else {
                    R.string.bookmarks_action_confirm_private
                },
            )
            val iconRes = if (isPrivate) R.drawable.ic_eye else R.drawable.ic_eye_off

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overscroll(overscroll),
                contentPadding = PaddingValues(
                    top = topPadding,
                    bottom = bottomPadding,
                ),
            ) {
                item(key = "top-anchor") {
                    Spacer(Modifier.height(0.dp))
                }
                when {
                    error != null -> item(key = "error") {
                        ErrorHint(error = error, backdrop = effectBackdrop, onRetry = onRetry)
                    }
                    items == null -> item(key = "skeleton") {
                        WorkRowListSkeleton(topPadding = 8.dp, bottomPadding = bottomPadding)
                    }
                    items.isEmpty() -> item(key = "empty") {
                        InlineEmptyHint(emptyMessage)
                    }
                    else -> {
                        items(
                            items = items,
                            key = { "${if (isPrivate) "priv" else "pub"}-${it.kind}-${it.id}" },
                            contentType = { "bookmarkRow" },
                        ) { item ->
                            CenteredReadableBox {
                                if (showVisibilitySwitch) {
                                    val rowKey = "${if (isPrivate) "priv" else "pub"}-${item.kind}-${item.id}"
                                    SwipeActionRow(
                                        rowKey = rowKey,
                                        controller = swipeController,
                                        backdrop = effectBackdrop,
                                        actionColor = actionColor,
                                        actionLabel = actionLabel,
                                        confirmActionLabel = confirmLabel,
                                        onClick = { onOpen(item.kind, item.id) },
                                        onCommit = { onToggleVisibility(item) },
                                        actionIcon = { _, _ ->
                                            Image(
                                                painter = painterResource(iconRes),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(Color.White),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                    ) {
                                        WorkRowBody(
                                            thumbUrl = item.thumbUrl,
                                            title = item.title,
                                            author = item.userName,
                                            meta = workMeta(item.kind, item.illustType, item.id),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                        )
                                    }
                                } else {
                                    WorkRow(
                                        thumbUrl = item.thumbUrl,
                                        title = item.title,
                                        author = item.userName,
                                        meta = workMeta(item.kind, item.illustType, item.id),
                                        modifier = Modifier.blockParentHorizontalDrag(),
                                        onClick = { onOpen(item.kind, item.id) },
                                    )
                                }
                            }
                        }
                        if (loadingMore) {
                            item(key = "loading-more") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_loading),
                                        kind = CouiTextKind.ItemSummary,
                                        color = colors.summary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorHint(error: String, backdrop: CouiBackdrop, onRetry: () -> Unit) {
    RetryErrorState(
        error = error,
        onRetry = onRetry,
        backdrop = backdrop,
        fillScreen = false,
        modifier = Modifier.padding(vertical = 40.dp),
    )
}

@Composable
private fun InlineEmptyHint(text: String) {
    val colors = CouiPageDefaults.themeColors()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp, horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.ItemSummary, color = colors.summary)
    }
}

@Composable
private fun EmptyHint(text: String) {
    val colors = CouiPageDefaults.themeColors()
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.ItemSummary, color = colors.summary)
    }
}

internal fun matchesBookmarkFilter(item: BookmarkItem, filter: Int): Boolean = when (filter) {
    1 -> item.kind == HistoryEntry.KIND_ILLUST && item.illustType != 1
    2 -> item.kind == HistoryEntry.KIND_ILLUST && item.illustType == 1
    3 -> item.kind == HistoryEntry.KIND_NOVEL
    else -> true
}
