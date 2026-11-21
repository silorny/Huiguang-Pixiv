package moe.huiguang.pixiv.ui.bookmarks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.BookmarkItem
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.FavoriteStore
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.SlideSwitcher
import moe.huiguang.pixiv.design.components.WorkRow
import moe.huiguang.pixiv.design.components.WorkRowListSkeleton
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.workMeta

/**
 * Bookmarks: displays the signed-in user's Pixiv cloud bookmarks, filtered by
 * illustration / manga / novel, with a floating COUI blurred top bar.
 */
@Composable
fun BookmarksScreen(
    api: PixivApi,
    settings: SettingsStore,
    favorites: FavoriteStore,
    onBack: () -> Unit,
    onOpen: (String, String) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val state by settings.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var cloud by remember { mutableStateOf<List<BookmarkItem>?>(null) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listTopPadding = statusTop + 56.dp + 8.dp

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val session = state.session
    LaunchedEffect(session?.userId, resumeTick) {
        val uid = session?.userId ?: return@LaunchedEffect
        if (cloud == null) cloudError = null
        runCatching { api.bookmarks(uid, limit = 48, offset = 0) }
            .onSuccess { firstPage ->
                cloud = firstPage
                offset = 48
                ended = firstPage.isEmpty()
                cloudError = null
            }
            .onFailure { if (cloud == null) cloudError = it.message }
    }

    fun loadMore() {
        val uid = session?.userId ?: return
        if (loadingMore || ended) return
        loadingMore = true
        scope.launch {
            runCatching { api.bookmarks(uid, limit = 48, offset = offset) }
                .onSuccess { nextPage ->
                    val existing = cloud.orEmpty()
                    val known = existing.mapTo(HashSet()) { "${it.kind}-${it.id}" }
                    val fresh = nextPage.filter { "${it.kind}-${it.id}" !in known }
                    cloud = existing + fresh
                    offset += 48
                    if (nextPage.isEmpty() || fresh.isEmpty()) ended = true
                }
            loadingMore = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(colors.background),
        ) {
            SlideSwitcher(
                index = filter,
                modifier = Modifier.fillMaxSize(),
            ) { selectedFilter ->
                CloudList(
                    session = session != null,
                    items = cloud?.filter { matchesBookmarkFilter(it, selectedFilter) },
                    error = cloudError,
                    loadingMore = loadingMore,
                    topPadding = listTopPadding,
                    bottomPadding = navBottom + 24.dp,
                    onOpen = onOpen,
                    onLoadMore = ::loadMore,
                    onRetry = { resumeTick++ },
                )
            }
        }

        FloatingSecondaryTopBar(
            title = stringResource(R.string.bookmarks_title),
            backdrop = backdrop,
            onBack = onBack,
            trailing = {
                Box {
                    CircleIconButton(
                        icon = CouiIcons.Filled.More,
                        contentDescription = stringResource(R.string.search_filter_menu),
                        onClick = { menuExpanded = true },
                        backdrop = backdrop,
                        dark = dark,
                        blurRadius = 20f,
                    )
                    Menu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        headerText = stringResource(R.string.search_menu_category_header),
                        headerTextAlign = TextAlign.Start,
                        modifier = Modifier.widthIn(min = 168.dp),
                        offset = DpOffset(0.dp, 8.dp),
                        items = listOf(
                            CouiMenuItem(
                                value = 0,
                                text = stringResource(R.string.filter_all),
                                selected = filter == 0,
                            ),
                            CouiMenuItem(
                                value = 1,
                                text = stringResource(R.string.filter_illust),
                                selected = filter == 1,
                            ),
                            CouiMenuItem(
                                value = 2,
                                text = stringResource(R.string.filter_manga),
                                selected = filter == 2,
                            ),
                            CouiMenuItem(
                                value = 3,
                                text = stringResource(R.string.filter_novel),
                                selected = filter == 3,
                            ),
                        ),
                        onItemClick = { item ->
                            menuExpanded = false
                            filter = item.value
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun CloudList(
    session: Boolean,
    items: List<BookmarkItem>?,
    error: String?,
    loadingMore: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val overscroll = rememberCouiOverscrollEffect()
    val listState = rememberLazyListState()
    val nearEnd by remember(listState, items) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 5
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }
    when {
        !session -> EmptyHint(stringResource(R.string.bookmarks_need_login))
        error != null -> Box(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = error, kind = CouiTextKind.ItemSummary, color = colors.summary)
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
            }
        }
        items == null -> WorkRowListSkeleton(topPadding = topPadding, bottomPadding = bottomPadding)
        items.isEmpty() -> EmptyHint(stringResource(R.string.bookmarks_cloud_empty))
        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(overscroll),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        ) {
            items(items, key = { "${it.kind}-${it.id}" }) { item ->
                WorkRow(
                    thumbUrl = item.thumbUrl,
                    title = item.title,
                    author = item.userName,
                    meta = workMeta(item.kind, item.illustType, item.id),
                    onClick = { onOpen(item.kind, item.id) },
                )
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

private fun matchesBookmarkFilter(item: BookmarkItem, filter: Int): Boolean = when (filter) {
    1 -> item.kind == HistoryEntry.KIND_ILLUST && item.illustType != 1
    2 -> item.kind == HistoryEntry.KIND_ILLUST && item.illustType == 1
    3 -> item.kind == HistoryEntry.KIND_NOVEL
    else -> true
}
