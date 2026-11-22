package moe.huiguang.pixiv.ui.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import java.text.NumberFormat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.model.SearchRatingFilter
import moe.huiguang.pixiv.data.model.SearchSortOrder
import moe.huiguang.pixiv.data.model.TagMetadata
import moe.huiguang.pixiv.data.model.UserSearchItem
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.SlideSwitcher
import moe.huiguang.pixiv.design.components.UserCard
import moe.huiguang.pixiv.design.components.WaterfallSkeleton
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar

private val TagCardOuterRadius = 24.dp
private val TagCardPadding = 12.dp
private val TagCoverInnerRadius = 12.dp // Concentric: 24.dp outer radius - 12.dp padding == 12.dp
private val TagCardShape = CouiShape(TagCardOuterRadius, 3f)
private val TagCoverShape = CouiShape(TagCoverInnerRadius, 3f)

/**
 * Search results: 全部 / 插画 / 漫画 / 小说 / 作者.
 *
 * Work categories render as a paged two-column staggered grid; 作者 renders as
 * a paged list of [UserCard]s. Every filter tab preserves its own scroll state.
 */
@Composable
fun SearchResultsScreen(
    query: String,
    mode: SearchMode,
    api: PixivApi,
    cache: SearchResultsCache,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenWork: (SearchItem) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val resources = LocalResources.current
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val settingsState by settings.state.collectAsState()
    val overscroll = rememberCouiOverscrollEffect()
    val scope = rememberCoroutineScope()
    var sortOrder by rememberSaveable { mutableStateOf(SearchSortOrder.NEWEST) }
    var ratingFilter by rememberSaveable { mutableStateOf(SearchRatingFilter.ALL) }
    var optionsMenuOpen by remember { mutableStateOf(false) }
    val cacheKey = "${mode.name}|${sortOrder.name}|${ratingFilter.name}|$query"
    val cached = remember(cacheKey) { cache.get(cacheKey) }

    var items by remember(cacheKey) { mutableStateOf(cached?.items ?: emptyList()) }
    var tagMetadata by remember(cacheKey) { mutableStateOf(cached?.tagMetadata) }
    var nextPage by remember(cacheKey) { mutableIntStateOf(cached?.nextPage ?: 1) }
    var endReached by remember(cacheKey) { mutableStateOf(cached?.endReached ?: false) }
    var loading by remember(cacheKey) { mutableStateOf(cached == null) }
    var inFlight by remember(cacheKey) { mutableStateOf(false) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }

    var users by remember(cacheKey) { mutableStateOf<List<UserSearchItem>>(emptyList()) }
    var userPage by remember(cacheKey) { mutableIntStateOf(1) }
    var userEnded by remember(cacheKey) { mutableStateOf(false) }
    var userLoading by remember(cacheKey) { mutableStateOf(false) }
    var userRequiresLogin by remember(cacheKey) { mutableStateOf(false) }
    var userError by remember(cacheKey) { mutableStateOf<String?>(null) }

    var filter by rememberSaveable { mutableIntStateOf(0) }
    val gridStates = List(4) { rememberLazyStaggeredGridState() }
    val userListState = rememberLazyListState()

    suspend fun loadNextPage() {
        if (inFlight || endReached) return
        inFlight = true
        loading = true
        error = null
        runCatching {
            api.searchPage(
                word = query,
                mode = mode,
                page = nextPage,
                limit = 30,
                order = sortOrder,
                rating = ratingFilter,
            )
        }
            .onSuccess { pageResult ->
                val known = items.map { it.category to it.id }.toSet()
                val fresh = pageResult.items.filter { (it.category to it.id) !in known }
                items = items + fresh
                if (tagMetadata == null && pageResult.tagMetadata != null) {
                    tagMetadata = pageResult.tagMetadata
                }
                endReached = fresh.isEmpty()
                nextPage += 1
                cache.put(
                    cacheKey,
                    SearchResultsCache.Entry(items, nextPage, endReached, tagMetadata),
                )
            }
            .onFailure { error = it.message ?: resources.getString(R.string.error_search_failed) }
        loading = false
        inFlight = false
    }

    suspend fun loadNextUsers() {
        if (userLoading || userEnded || userRequiresLogin) return
        userLoading = true
        userError = null
        runCatching { api.searchUsers(query, page = userPage) }
            .onSuccess { result ->
                if (result.requiresLogin) {
                    userRequiresLogin = true
                    userEnded = true
                } else {
                    val known = users.map { it.userId }.toSet()
                    val fresh = result.users.filter { it.userId !in known }
                    users = users + fresh
                    userEnded = fresh.isEmpty()
                    userPage += 1
                }
            }
            .onFailure { userError = it.message ?: resources.getString(R.string.error_search_failed) }
        userLoading = false
    }

    LaunchedEffect(cacheKey) {
        if (cached == null) loadNextPage()
        gridStates.forEach { state ->
            if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) {
                scope.launch { state.scrollToItem(0) }
            }
        }
        if (userListState.firstVisibleItemIndex > 0 || userListState.firstVisibleItemScrollOffset > 0) {
            scope.launch { userListState.scrollToItem(0) }
        }
    }

    LaunchedEffect(filter, settingsState.session?.userId) {
        if (filter == 4 && users.isEmpty() && !userLoading && !userEnded) {
            loadNextUsers()
        } else if (filter == 4 && userRequiresLogin && settingsState.session != null) {
            userRequiresLogin = false
            userEnded = false
            loadNextUsers()
        }
    }

    val backdrop = rememberCouiBackdrop()
    val surfaceBackdrop = rememberCouiBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listTopPadding = statusTop + 56.dp + 8.dp

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
            // Flat opaque sibling source for GlowChip inside TagHeaderCard (avoids render-graph cycle).
            Box(
                Modifier
                    .fillMaxSize()
                    .couiBackdrop(surfaceBackdrop)
                    .background(colors.background),
            )
            SlideSwitcher(
                index = filter,
                modifier = Modifier.fillMaxSize(),
            ) { selected ->
                if (selected == 4) {
                    AuthorResultsList(
                        users = users,
                        loading = userLoading,
                        ended = userEnded,
                        requiresLogin = userRequiresLogin,
                        error = userError,
                        listState = userListState,
                        overscroll = overscroll,
                        topPadding = listTopPadding,
                        bottomPadding = navBottom + 24.dp,
                        onOpenAuthor = onOpenAuthor,
                        onLogin = onLogin,
                        onLoadMore = { scope.launch { loadNextUsers() } },
                    )
                } else {
                    val visible = remember(items, selected) {
                        when (selected) {
                            1 -> items.filter { it.category == SearchCategory.ILLUST }
                            2 -> items.filter { it.category == SearchCategory.MANGA }
                            3 -> items.filter { it.category == SearchCategory.NOVEL }
                            else -> items
                        }
                    }
                    val phase = when {
                        items.isEmpty() && loading -> 0
                        items.isEmpty() && error != null -> 1
                        else -> 2
                    }
                    Crossfade(
                        targetState = phase,
                        animationSpec = tween(200),
                        label = "searchResultsState",
                    ) { targetPhase ->
                        when (targetPhase) {
                            0 -> WaterfallSkeleton(
                                topPadding = listTopPadding,
                                bottomPadding = navBottom + 24.dp,
                            )

                            1 -> Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = error ?: "",
                                    kind = CouiTextKind.ItemSummary,
                                    color = colors.summary,
                                )
                                Spacer(Modifier.height(16.dp))
                                PrimaryButton(
                                    text = stringResource(R.string.action_retry),
                                    onClick = { scope.launch { loadNextPage() } },
                                )
                            }

                            else -> {
                                val gridState = gridStates[selected]
                                val tagCoverUrl = remember(tagMetadata, visible, items, settingsState.revealR18) {
                                    tagMetadata?.officialCoverUrl?.takeIf { it.isNotBlank() }
                                        ?: (visible.firstOrNull { (!settingsState.revealR18 || !it.isAdult) && it.thumbUrl.isNotBlank() }
                                            ?: items.firstOrNull { (!settingsState.revealR18 || !it.isAdult) && it.thumbUrl.isNotBlank() }
                                            ?: visible.firstOrNull { it.thumbUrl.isNotBlank() }
                                            ?: items.firstOrNull { it.thumbUrl.isNotBlank() })
                                            ?.thumbUrl
                                        ?: ""
                                }
                                LaunchedEffect(gridState, items.size, endReached, loading) {
                                    if (items.isEmpty() || endReached || loading) return@LaunchedEffect
                                    snapshotFlow {
                                        val info = gridState.layoutInfo
                                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                                        info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 6
                                    }
                                        .distinctUntilChanged()
                                        .collect { nearEnd ->
                                            if (nearEnd) loadNextPage()
                                        }
                                }
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(2),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .overscroll(overscroll),
                                    state = gridState,
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = listTopPadding,
                                        bottom = navBottom + 24.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalItemSpacing = 12.dp,
                                ) {
                                    if (mode == SearchMode.TAG) {
                                        item(span = StaggeredGridItemSpan.FullLine, key = "tag-header") {
                                            TagHeaderCard(
                                                tag = query,
                                                coverUrl = tagCoverUrl,
                                                metadata = tagMetadata,
                                                favorited = query in settingsState.favoriteTags,
                                                backdrop = surfaceBackdrop,
                                                dark = dark,
                                                onToggleFavorite = { settings.toggleFavoriteTag(query) },
                                            )
                                        }
                                    }

                                    if (visible.isEmpty() && !loading) {
                                        item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.search_no_result),
                                                    kind = CouiTextKind.ItemSummary,
                                                    color = colors.summary,
                                                )
                                            }
                                        }
                                    }

                                    items(visible, key = { "${it.category}-${it.id}" }) { item ->
                                        ArtworkCard(
                                            item = item,
                                            revealR18 = settingsState.revealR18,
                                            onClick = { onOpenWork(item) },
                                        )
                                    }

                                    item(span = StaggeredGridItemSpan.FullLine, key = "footer") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = when {
                                                    error != null -> error!!
                                                    loading -> stringResource(R.string.search_loading)
                                                    endReached -> stringResource(R.string.search_end)
                                                    else -> ""
                                                },
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

        FloatingSecondaryTopBar(
            title = query,
            backdrop = backdrop,
            onBack = onBack,
            trailing = {
                Box {
                    CircleIconButton(
                        icon = CouiIcons.Filled.More,
                        contentDescription = stringResource(R.string.search_options_menu),
                        onClick = { optionsMenuOpen = true },
                        backdrop = backdrop,
                        dark = dark,
                        blurRadius = 20f,
                    )
                    val menuItems = buildList {
                        add(CouiMenuItem(0, stringResource(R.string.search_filter_all), selected = filter == 0))
                        add(CouiMenuItem(1, stringResource(R.string.search_filter_illust), selected = filter == 1))
                        add(CouiMenuItem(2, stringResource(R.string.search_filter_manga), selected = filter == 2))
                        add(CouiMenuItem(3, stringResource(R.string.search_filter_novel), selected = filter == 3))
                        add(CouiMenuItem(4, stringResource(R.string.search_filter_author), selected = filter == 4))
                        add(
                            CouiMenuItem(
                                value = 10,
                                text = stringResource(R.string.search_sort_newest),
                                selected = sortOrder == SearchSortOrder.NEWEST,
                                dividerBefore = true,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = 11,
                                text = stringResource(R.string.search_sort_oldest),
                                selected = sortOrder == SearchSortOrder.OLDEST,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = 20,
                                text = stringResource(R.string.search_rating_all),
                                selected = ratingFilter == SearchRatingFilter.ALL,
                                dividerBefore = true,
                            ),
                        )
                        add(
                            CouiMenuItem(
                                value = 21,
                                text = stringResource(R.string.search_rating_safe),
                                selected = ratingFilter == SearchRatingFilter.SAFE,
                            ),
                        )
                        if (settingsState.revealR18) {
                            add(
                                CouiMenuItem(
                                    value = 22,
                                    text = stringResource(R.string.search_rating_r18),
                                    selected = ratingFilter == SearchRatingFilter.R18,
                                ),
                            )
                        }
                    }
                    Menu(
                        expanded = optionsMenuOpen,
                        onDismissRequest = { optionsMenuOpen = false },
                        headerText = stringResource(R.string.search_menu_category_header),
                        headerTextAlign = TextAlign.Start,
                        modifier = Modifier.widthIn(min = 172.dp),
                        offset = DpOffset(0.dp, 8.dp),
                        items = menuItems,
                        onItemClick = { item ->
                            optionsMenuOpen = false
                            when (item.value) {
                                in 0..4 -> filter = item.value
                                10 -> sortOrder = SearchSortOrder.NEWEST
                                11 -> sortOrder = SearchSortOrder.OLDEST
                                20 -> ratingFilter = SearchRatingFilter.ALL
                                21 -> ratingFilter = SearchRatingFilter.SAFE
                                22 -> ratingFilter = SearchRatingFilter.R18
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun AuthorResultsList(
    users: List<UserSearchItem>,
    loading: Boolean,
    ended: Boolean,
    requiresLogin: Boolean,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    overscroll: com.itos.coui.scroll.CouiOverscrollEffect,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenAuthor: (String) -> Unit,
    onLogin: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    when {
        requiresLogin -> Box(
            Modifier
                .fillMaxSize()
                .padding(top = topPadding + 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Group {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.search_author_login_hint),
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = stringResource(R.string.login_title),
                        onClick = onLogin,
                    )
                }
            }
        }

        users.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_loading),
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
            )
        }

        users.isEmpty() && error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = error,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
            )
        }

        users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_author_empty),
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
            )
        }

        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overscroll(overscroll),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        ) {
            items(users, key = { it.userId }) { user ->
                UserCard(
                    user = user,
                    onClick = { onOpenAuthor(user.userId) },
                )
            }
            item(key = "user-footer") {
                LaunchedEffect(users.size, ended) {
                    if (!ended) onLoadMore()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            error != null -> error
                            loading -> stringResource(R.string.search_loading)
                            ended -> stringResource(R.string.search_end)
                            else -> ""
                        },
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagHeaderCard(
    tag: String,
    coverUrl: String,
    metadata: TagMetadata?,
    favorited: Boolean,
    backdrop: CouiBackdrop,
    dark: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val englishName = metadata?.englishName?.trim()?.takeIf { it.isNotEmpty() }
    val countText = if (metadata != null) {
        stringResource(
            R.string.tag_header_works_count,
            NumberFormat.getIntegerInstance().format(metadata.totalWorks),
        )
    } else {
        stringResource(R.string.tag_header_works_loading)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TagCardShape)
            .background(colors.card)
            .padding(TagCardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl.isBlank()) {
            Box(
                Modifier
                    .size(108.dp)
                    .clip(TagCoverShape)
                    .background(colors.background),
            )
        } else {
            RetryableImage(
                url = coverUrl,
                contentDescription = tag,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(108.dp)
                    .clip(TagCoverShape)
                    .background(colors.background),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag,
                kind = CouiTextKind.ItemTitle,
                color = colors.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (englishName != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = englishName,
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = countText,
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            GlowChip(
                text = stringResource(if (favorited) R.string.tag_favorite_added else R.string.tag_favorite_add),
                selected = favorited,
                whiteGlass = !favorited,
                onClick = onToggleFavorite,
                backdrop = backdrop,
                dark = dark,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
