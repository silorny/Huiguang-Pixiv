package moe.huiguang.pixiv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarSurface
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.PixivisionSummary
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.HomeFeedCache
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.ArtworkCardOuterShape
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.WaterfallSkeleton
import moe.huiguang.pixiv.design.components.downgradeWaterfallThumbUrl
import moe.huiguang.pixiv.design.components.rememberCouiCardIndication

internal const val HOME_FILTER_ILLUST = 1
internal const val HOME_FILTER_MANGA = 2
internal const val HOME_FILTER_NOVEL = 4
internal const val DEFAULT_HOME_WORK_FILTER = HOME_FILTER_ILLUST or HOME_FILTER_MANGA

@Stable
class HomeTabState internal constructor(
    val gridStates: List<LazyStaggeredGridState>,
    private val sectionState: MutableIntState,
    private val recommendedFilterState: MutableIntState,
    private val rankingTabState: MutableIntState,
    private val followingFilterState: MutableIntState,
) {
    var section: Int
        get() = sectionState.intValue
        set(value) { sectionState.intValue = value }
    var recommendedFilter: Int
        get() = recommendedFilterState.intValue
        set(value) { recommendedFilterState.intValue = value }
    var rankingTab: Int
        get() = rankingTabState.intValue
        set(value) { rankingTabState.intValue = value }
    var followingFilter: Int
        get() = followingFilterState.intValue
        set(value) { followingFilterState.intValue = value }
}

@Composable
fun rememberHomeTabState(): HomeTabState {
    val gridStates = List(3) { rememberLazyStaggeredGridState() }
    val sectionState = rememberSaveable { mutableIntStateOf(0) }
    val recommendedFilterState = rememberSaveable { mutableIntStateOf(DEFAULT_HOME_WORK_FILTER) }
    val rankingTabState = rememberSaveable { mutableIntStateOf(0) }
    val followingFilterState = rememberSaveable { mutableIntStateOf(DEFAULT_HOME_WORK_FILTER) }
    return remember(gridStates) {
        HomeTabState(
            gridStates = gridStates,
            sectionState = sectionState,
            recommendedFilterState = recommendedFilterState,
            rankingTabState = rankingTabState,
            followingFilterState = followingFilterState,
        )
    }
}

@Composable
fun HomeTopChrome(
    tabState: HomeTabState,
    backdrop: CouiBackdrop,
    effectBackdrop: CouiBackdrop = backdrop,
    statusTop: Dp,
) {
    val section = tabState.section
    val rankingTab = tabState.rankingTab
    val recommendedFilter = tabState.recommendedFilter
    val followingFilter = tabState.followingFilter
    val activeGrid = tabState.gridStates[section]
    val collapsed by remember(activeGrid) {
        derivedStateOf {
            activeGrid.firstVisibleItemIndex > 0 || activeGrid.firstVisibleItemScrollOffset > 24
        }
    }
    val categoryItems = if (section == 1) {
        listOf(
            CouiMenuItem(0, stringResource(R.string.ranking_daily), selected = rankingTab == 0),
            CouiMenuItem(1, stringResource(R.string.ranking_weekly), selected = rankingTab == 1),
            CouiMenuItem(2, stringResource(R.string.ranking_monthly), selected = rankingTab == 2),
            CouiMenuItem(3, stringResource(R.string.ranking_rookie), selected = rankingTab == 3),
            CouiMenuItem(4, stringResource(R.string.filter_illust), selected = rankingTab == 4, dividerBefore = true),
            CouiMenuItem(5, stringResource(R.string.filter_manga), selected = rankingTab == 5),
        )
    } else {
        val activeWorkFilter = (if (section == 2) followingFilter else recommendedFilter)
            .takeIf { (it and (HOME_FILTER_ILLUST or HOME_FILTER_MANGA or HOME_FILTER_NOVEL)) != 0 }
            ?: DEFAULT_HOME_WORK_FILTER
        val selectedCount = Integer.bitCount(activeWorkFilter)
        val illustSelected = (activeWorkFilter and HOME_FILTER_ILLUST) != 0
        val mangaSelected = (activeWorkFilter and HOME_FILTER_MANGA) != 0
        val novelSelected = (activeWorkFilter and HOME_FILTER_NOVEL) != 0
        listOf(
            CouiMenuItem(
                value = HOME_FILTER_ILLUST,
                text = stringResource(R.string.filter_illust),
                selected = illustSelected,
                checked = illustSelected,
                enabled = !(illustSelected && selectedCount == 1),
            ),
            CouiMenuItem(
                value = HOME_FILTER_MANGA,
                text = stringResource(R.string.filter_manga),
                selected = mangaSelected,
                checked = mangaSelected,
                enabled = !(mangaSelected && selectedCount == 1),
            ),
            CouiMenuItem(
                value = HOME_FILTER_NOVEL,
                text = stringResource(R.string.filter_novel),
                selected = novelSelected,
                checked = novelSelected,
                enabled = !(novelSelected && selectedCount == 1),
            ),
        )
    }
    HomeOverlayHeader(
        appTitle = stringResource(R.string.app_name),
        collapsed = collapsed,
        statusTop = statusTop,
        section = section,
        onSectionSelected = { tabState.section = it },
        categoryItems = categoryItems,
        onCategorySelected = { selectedKey ->
            when (section) {
                1 -> tabState.rankingTab = selectedKey
                2 -> {
                    val next = tabState.followingFilter xor selectedKey
                    if (next != 0) tabState.followingFilter = next
                }
                else -> {
                    val next = tabState.recommendedFilter xor selectedKey
                    if (next != 0) tabState.recommendedFilter = next
                }
            }
        },
        backdrop = backdrop,
    )
}

/**
 * Home: COUI collapsing [LargeTopAppBar] ("绘光") with the section
 * [SegmentedControl] (推荐 / 排行榜 / 关注新作) tucked cleanly underneath,
 * over a two-column illustration & manga waterfall. The 排行榜 tab leads with
 * a horizontal Pixivision 特辑 row above the ranking waterfall.
 */
@Composable
fun HomeScreen(
    padding: PaddingValues,
    api: PixivApi,
    settings: SettingsStore,
    cache: HomeFeedCache,
    tabState: HomeTabState = rememberHomeTabState(),
    effectBackdrop: CouiBackdrop,
    refreshBackdrop: CouiBackdrop = effectBackdrop,
    scrollToTopTick: Int = 0,
    onOpenWork: (SearchItem) -> Unit,
    onOpenPixivision: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val settingsState by settings.state.collectAsState()
    val sessionId = settingsState.session?.userId

    val vm: HomeViewModel = viewModel(key = "home") { HomeViewModel(api, cache, sessionId) }
    val state by vm.state.collectAsState()
    val rankingBuckets by vm.rankingBuckets.collectAsState()
    val rankingErrors by vm.rankingErrors.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val loadMoreErrors by vm.loadMoreErrors.collectAsState()
    val endReached by vm.endReached.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val recommendedHeadCutoff by vm.recommendedHeadCutoff.collectAsState()
    val followingHeadCutoff by vm.followingHeadCutoff.collectAsState()

    val gridStates = tabState.gridStates
    val section = tabState.section
    val recommendedFilter = tabState.recommendedFilter
    val rankingTab = tabState.rankingTab
    val followingFilter = tabState.followingFilter
    val activeGrid = gridStates[section]

    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            if (activeGrid.firstVisibleItemIndex > 12) {
                activeGrid.scrollToItem(8)
            }
            activeGrid.animateScrollToItem(0)
        }
    }

    LaunchedEffect(section, rankingTab, state) {
        if (section == 1 && rankingTab != 0 && state is HomeUiState.Content) {
            vm.ensureRankingTab(rankingTab)
        }
    }

    val localeTag = LocalLocale.current.platformLocale.toLanguageTag()
    var lastLocaleTag by rememberSaveable { mutableStateOf(localeTag) }
    LaunchedEffect(sessionId, localeTag) {
        if (localeTag != lastLocaleTag) {
            lastLocaleTag = localeTag
            vm.refresh(sessionId)
        } else {
            vm.ensureLoaded(sessionId)
        }
    }

    val density = LocalDensity.current
    val dark = CouiTheme.isDark
    val cardIndication = rememberCouiCardIndication(ArtworkCardOuterShape, dark)
    val baseTitleStyle = rememberCouiTextStyle(CouiTextKind.ItemTitle)
    val baseSummaryStyle = rememberCouiTextStyle(CouiTextKind.ItemSummary)
    val cardTitleStyle = remember(baseTitleStyle, colors.title) {
        baseTitleStyle.copy(color = colors.title, textAlign = TextAlign.Start)
    }
    val cardSummaryStyle = remember(baseSummaryStyle, colors.summary) {
        baseSummaryStyle.copy(color = colors.summary, textAlign = TextAlign.Start)
    }
    val cardAccentSummaryStyle = remember(cardSummaryStyle, colors.accent) {
        cardSummaryStyle.copy(color = colors.accent)
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Pull-to-refresh state across all sections when overscrolling downward at the top.
    val triggerPullPx = with(density) { 72.dp.toPx() }
    val maxPullPx = with(density) { 112.dp.toPx() }
    val refreshingHoldPx = with(density) { 56.dp.toPx() }
    var rawPullPx by remember { mutableFloatStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentSessionId by rememberUpdatedState(sessionId)

    LaunchedEffect(section) {
        isPulling = false
        rawPullPx = 0f
    }

    val pullConnection = remember(activeGrid) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && rawPullPx > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-rawPullPx)
                    rawPullPx = (rawPullPx + consumedY).coerceAtLeast(0f)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    activeGrid.firstVisibleItemIndex == 0 &&
                    activeGrid.firstVisibleItemScrollOffset == 0
                ) {
                    isPulling = true
                    rawPullPx = (rawPullPx + available.y * 0.5f).coerceIn(0f, maxPullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (rawPullPx >= triggerPullPx && !currentRefreshing) {
                    vm.refresh(currentSessionId)
                }
                isPulling = false
                rawPullPx = 0f
                return Velocity.Zero
            }
        }
    }

    val animatedPullPx by animateFloatAsState(
        targetValue = when {
            isPulling -> rawPullPx
            refreshing -> refreshingHoldPx
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "homePullRefresh",
    )

    val fixedGridTopPadding = statusTop + 64.dp + 54.dp

    val statePhase = when (state) {
        is HomeUiState.Loading -> 0
        is HomeUiState.Error -> 1
        is HomeUiState.Content -> 2
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = statePhase,
            animationSpec = tween(200),
            label = "homeUiState",
        ) { phase ->
            when (phase) {
                0 -> WaterfallSkeleton(
                    topPadding = fixedGridTopPadding,
                    bottomPadding = navBottom + 104.dp,
                )

                1 -> ErrorState((state as? HomeUiState.Error)?.message ?: "") { vm.refresh(sessionId) }

                else -> {
                    val current = state as? HomeUiState.Content ?: return@Crossfade
                    val pagerState = rememberPagerState(initialPage = section, pageCount = { 3 })
                    var animatingTo by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(section) {
                        if (pagerState.currentPage != section || pagerState.currentPageOffsetFraction != 0f) {
                            animatingTo = section
                            try {
                                pagerState.animateScrollToPage(section)
                            } finally {
                                animatingTo = null
                            }
                        }
                    }
                    LaunchedEffect(pagerState) {
                        snapshotFlow { Triple(pagerState.currentPage, pagerState.isScrollInProgress, animatingTo) }
                            .distinctUntilChanged()
                            .collect { (page, scrolling, target) ->
                                if (target != null) {
                                    if (!scrolling && page == target) {
                                        animatingTo = null
                                    }
                                } else if (tabState.section != page) {
                                    tabState.section = page
                                }
                            }
                    }
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(pullConnection),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 0,
                            modifier = Modifier.fillMaxSize(),
                        ) { selected ->
                        val activeCategory = when (selected) {
                            1 -> rankingTab
                            2 -> followingFilter
                            else -> recommendedFilter
                        }
                        val visible = remember(
                            selected,
                            activeCategory,
                            current.feed,
                            rankingBuckets[rankingTab],
                            recommendedHeadCutoff,
                            followingHeadCutoff,
                        ) {
                            when (selected) {
                                1 -> if (rankingTab == 0) current.feed.ranking else rankingBuckets[rankingTab].orEmpty()
                                2 -> filterHomeWorkStream(
                                    artworks = current.feed.following,
                                    novels = current.feed.novelFollowing,
                                    filter = followingFilter,
                                    headCutoff = followingHeadCutoff,
                                )
                                else -> filterHomeWorkStream(
                                    artworks = current.feed.recommended,
                                    novels = current.feed.novelRecommended,
                                    filter = recommendedFilter,
                                    headCutoff = recommendedHeadCutoff,
                                )
                            }
                        }
                        val bucket = selected to activeCategory
                        val isLoadingMore = bucket in loadingMore
                        val hasLoadError = bucket in loadMoreErrors
                        val isEnd = bucket in endReached
                        val gridState = gridStates[selected]
                        val pageOverscroll = rememberCouiOverscrollEffect()
                        val appContext = LocalContext.current

                        val latestVisible by rememberUpdatedState(visible)
                        LaunchedEffect(gridState, selected, activeCategory) {
                            val enqueuedUrls = HashSet<String>(96)
                            val activePreloads = ArrayDeque<Pair<Int, Disposable>>(24)
                            snapshotFlow {
                                val visibleInfo = gridState.layoutInfo.visibleItemsInfo
                                val first = visibleInfo.firstOrNull()?.index ?: 0
                                val last = visibleInfo.lastOrNull()?.index ?: 0
                                first to last
                            }
                                .distinctUntilChanged()
                                .collectLatest { (firstIndex, lastIndex) ->
                                    while (activePreloads.isNotEmpty() && activePreloads.first().first < firstIndex - 2) {
                                        activePreloads.removeFirst().second.dispose()
                                    }
                                    val list = latestVisible
                                    if (list.isEmpty()) return@collectLatest
                                    // Wait briefly through peak fling velocity so stale HTTP/decode
                                    // requests don't saturate OkHttp/Coil ahead of scrolled-past rows.
                                    delay(65L)
                                    if (enqueuedUrls.size > 240) enqueuedUrls.clear()
                                    val start = (lastIndex + 1).coerceAtLeast(0)
                                    val end = (lastIndex + 9).coerceAtMost(list.size)
                                    if (start >= end) return@collectLatest
                                    val loader = appContext.imageLoader
                                    for (i in start until end) {
                                        val item = list[i]
                                        val raw = item.thumbUrl.ifBlank { item.fullUrl }
                                        if (raw.isBlank()) continue
                                        val url = downgradeWaterfallThumbUrl(raw)
                                        if (!enqueuedUrls.add(url)) continue
                                        val disposable = loader.enqueue(
                                            ImageRequest.Builder(appContext)
                                                .data(url)
                                                .allowRgb565(true)
                                                .size(Size(360, 360))
                                                .precision(Precision.INEXACT)
                                                .build(),
                                        )
                                        activePreloads.addLast(i to disposable)
                                    }
                                }
                        }

                        LaunchedEffect(gridState, selected, activeCategory, isLoadingMore, isEnd, hasLoadError) {
                            if (isEnd || isLoadingMore || hasLoadError) return@LaunchedEffect
                            snapshotFlow {
                                val info = gridState.layoutInfo
                                val total = info.totalItemsCount
                                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                                total > 0 && lastVisible >= total - 8
                            }
                                .distinctUntilChanged()
                                .filter { it }
                                .collect {
                                    vm.loadMore(selected, activeCategory)
                                }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .overscroll(pageOverscroll),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = fixedGridTopPadding,
                                bottom = navBottom + 104.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            if (sessionId == null || current.feed.isFallback) {
                                item(span = StaggeredGridItemSpan.FullLine, key = "login-hint") {
                                    Group {
                                        Item(
                                            summary = {
                                                Text(
                                                    text = stringResource(R.string.home_login_hint_summary),
                                                    kind = CouiTextKind.ItemSummary,
                                                    color = colors.summary,
                                                )
                                            },
                                            onClick = onLogin,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.home_login_hint),
                                                kind = CouiTextKind.ItemTitle,
                                                color = colors.title,
                                            )
                                        }
                                    }
                                }
                            }

                            if (selected == 1 && current.feed.pixivision.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine, key = "pixivision-header") {
                                    SectionHeader(text = stringResource(R.string.home_section_pixivision))
                                }
                                item(span = StaggeredGridItemSpan.FullLine, key = "pixivision-row") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        items(current.feed.pixivision, key = { it.id }) { feature ->
                                            PixivisionCard(
                                                item = feature,
                                                onClick = { onOpenPixivision(feature.id) },
                                            )
                                        }
                                    }
                                }
                                item(span = StaggeredGridItemSpan.FullLine, key = "ranking-header") {
                                    SectionHeader(
                                        text = stringResource(R.string.home_section_ranking),
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }

                            if (visible.isEmpty() && !isLoadingMore) {
                                val tabError = if (selected == 1 && rankingTab != 0) rankingErrors[rankingTab] else null
                                item(span = StaggeredGridItemSpan.FullLine, key = "empty-$selected-$activeCategory") {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = tabError ?: stringResource(R.string.home_section_empty),
                                            kind = CouiTextKind.ItemSummary,
                                            color = colors.summary,
                                        )
                                        if (tabError != null) {
                                            PrimaryButton(
                                                text = stringResource(R.string.action_retry),
                                                onClick = { vm.ensureRankingTab(rankingTab, forceRetry = true) },
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = visible,
                                    key = { "$selected-$activeCategory-${it.category.name}-${it.id}" },
                                    contentType = { if (it.category == SearchCategory.NOVEL) "novel" else "artwork" },
                                ) { item ->
                                    ArtworkCard(
                                        item = item,
                                        revealR18 = settingsState.revealR18,
                                        lowQualityThumb = true,
                                        colors = colors,
                                        dark = dark,
                                        cardIndication = cardIndication,
                                        titleStyle = cardTitleStyle,
                                        summaryStyle = cardSummaryStyle,
                                        accentSummaryStyle = cardAccentSummaryStyle,
                                        onClick = { onOpenWork(item) },
                                    )
                                }
                            }

                            item(span = StaggeredGridItemSpan.FullLine, key = "footer-$selected-$activeCategory") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .then(
                                            if (hasLoadError) {
                                                Modifier.clickable {
                                                    vm.loadMore(selected, activeCategory, retryFromError = true)
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = when {
                                            hasLoadError -> stringResource(R.string.action_retry)
                                            isLoadingMore -> stringResource(R.string.search_loading)
                                            isEnd && visible.isNotEmpty() -> stringResource(R.string.search_end)
                                            else -> ""
                                        },
                                        kind = CouiTextKind.ItemSummary,
                                        color = if (hasLoadError) colors.accent else colors.summary,
                                    )
                                }
                            }
                        }
                    }
                    }

                    if (animatedPullPx > 4f || refreshing) {
                        val pullAlpha = (animatedPullPx / (triggerPullPx * 0.6f)).coerceIn(0f, 1f)
                        val pullTop = fixedGridTopPadding +
                            with(density) { (animatedPullPx * 0.65f).toDp() }
                        val pullLabel = when {
                            refreshing -> stringResource(R.string.home_refreshing)
                            rawPullPx >= triggerPullPx -> stringResource(R.string.home_release_refresh)
                            else -> stringResource(R.string.home_pull_refresh)
                        }
                        GlowSurface(
                            backdrop = refreshBackdrop,
                            dark = dark,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = pullTop)
                                .alpha(pullAlpha)
                                .height(34.dp),
                            shape = CouiCapsuleShape,
                            cornerWeight = 1f,
                            whiteGlass = true,
                            interactive = false,
                            foregroundComposition = true,
                        ) {
                            BasicText(
                                text = pullLabel,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = TextStyle(
                                    color = colors.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HomeOverlayHeader(
    appTitle: String,
    collapsed: Boolean,
    statusTop: Dp,
    section: Int,
    onSectionSelected: (Int) -> Unit,
    categoryItems: List<CouiMenuItem<Int>>,
    onCategorySelected: (Int) -> Unit,
    backdrop: CouiBackdrop,
) {
    val colors = CouiPageDefaults.themeColors()
    val density = LocalDensity.current
    val collapseProgressState = animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "homeHeaderCollapse",
    )
    val collapsedTitleStyle = remember(colors.title) {
        TextStyle(
            color = colors.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val expandedTitleStyle = remember(colors.title) {
        TextStyle(
            color = colors.title,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val collapsedTitleWidthPx = remember(appTitle, collapsedTitleStyle, density) {
        textMeasurer.measure(appTitle, collapsedTitleStyle).size.width.toFloat()
    }
    val collapsedTitleWidthDp = with(density) { collapsedTitleWidthPx.toDp() }
    val collapsedStartPad = 20.dp + collapsedTitleWidthDp + 8.dp
    val collapsedEndPad = 20.dp + 40.dp + 8.dp
    val switcherShiftYPx = with(density) { (12.dp - (64.dp + 6.dp)).toPx() }
    val switcherBaseTopPx = with(density) { (statusTop + 64.dp + 6.dp).roundToPx() }
    val defaultSidePadPx = with(density) { 16.dp.roundToPx() }
    val collapsedStartPx = with(density) { collapsedStartPad.roundToPx() }
    val collapsedEndPx = with(density) { collapsedEndPad.roundToPx() }

    Box(Modifier.fillMaxWidth()) {
        HomeTopBarSurface(
            backdrop = backdrop,
            alphaProvider = { collapseProgressState.value.coerceIn(0f, 1f) },
            gradientColor = colors.background,
            statusTop = statusTop,
            appTitle = appTitle,
            expandedTitleStyle = expandedTitleStyle,
            categoryItems = categoryItems,
            onCategorySelected = onCategorySelected,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val p = collapseProgressState.value.coerceIn(0f, 1f)
                    val startPx = (defaultSidePadPx + (collapsedStartPx - defaultSidePadPx) * p).toInt()
                    val endPx = (defaultSidePadPx + (collapsedEndPx - defaultSidePadPx) * p).toInt()
                    val childW = (constraints.maxWidth - startPx - endPx).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = childW, maxWidth = childW),
                    )
                    layout(constraints.maxWidth, switcherBaseTopPx + placeable.height) {
                        placeable.placeRelative(startPx, switcherBaseTopPx)
                    }
                }
                .graphicsLayer {
                    translationY = switcherShiftYPx * collapseProgressState.value.coerceIn(0f, 1f)
                },
        ) {
            SectionSwitcher(
                selected = section,
                onSelected = onSectionSelected,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun HomeTopBarSurface(
    backdrop: CouiBackdrop,
    alphaProvider: () -> Float,
    gradientColor: Color,
    statusTop: Dp,
    appTitle: String,
    expandedTitleStyle: TextStyle,
    categoryItems: List<CouiMenuItem<Int>>,
    onCategorySelected: (Int) -> Unit,
) {
    val dark = CouiTheme.isDark
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {},
    ) {
        HomeTopBarBackdrop(
            backdrop = backdrop,
            alphaProvider = alphaProvider,
            gradientColor = gradientColor,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = appTitle,
                style = expandedTitleStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val s = 1f + (20f / 30f - 1f) * alphaProvider()
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
            Spacer(Modifier.weight(1f))
            Box {
                CircleIconButton(
                    icon = CouiIcons.Filled.More,
                    contentDescription = stringResource(R.string.search_menu_category_header),
                    onClick = { menuOpen = true },
                    backdrop = backdrop,
                    dark = dark,
                    blurRadius = 20f,
                )
                Menu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    headerText = stringResource(R.string.search_menu_category_header),
                    headerTextAlign = TextAlign.Start,
                    modifier = Modifier.widthIn(min = 168.dp),
                    offset = DpOffset(0.dp, 8.dp),
                    items = categoryItems,
                    onItemClick = { item ->
                        if (item.checked == null) {
                            menuOpen = false
                        }
                        onCategorySelected(item.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeTopBarBackdrop(
    backdrop: CouiBackdrop,
    alphaProvider: () -> Float,
    gradientColor: Color,
    modifier: Modifier = Modifier,
) {
    val alpha = alphaProvider()
    if (alpha <= 0.001f) return
    TopAppBarSurface(
        modifier = modifier,
        backdrop = backdrop,
        backgroundAlpha = alpha,
        gradientColor = gradientColor,
    ) {
        Spacer(Modifier.fillMaxSize())
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = CouiPageDefaults.themeColors()
    BasicText(
        text = text,
        modifier = modifier.padding(vertical = 2.dp),
        style = TextStyle(
            color = colors.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

private val PixivisionCardShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
private val PixivisionBadgeShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)

@Composable
private fun PixivisionCard(
    item: PixivisionSummary,
    onClick: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val interaction = remember { MutableInteractionSource() }
    val cardIndication = rememberCouiCardIndication(PixivisionCardShape, dark)
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .width(236.dp)
            .clip(PixivisionCardShape)
            .background(colors.card, PixivisionCardShape)
            .clickable(
                interactionSource = interaction,
                indication = cardIndication,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.75f),
        ) {
            RetryableImage(
                url = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                placeholderColor = colors.background.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(),
            )
            if (item.category.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            colors.accent.copy(alpha = 0.9f),
                            PixivisionBadgeShape,
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    BasicText(
                        text = item.category,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun SectionSwitcher(
    selected: Int,
    onSelected: (Int) -> Unit,
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
) {
    SegmentedControl(
        modifier = modifier.fillMaxWidth(),
        labels = listOf(
            stringResource(R.string.home_section_recommend),
            stringResource(R.string.home_section_ranking),
            stringResource(R.string.home_section_following),
        ),
        selectedIndex = selected,
        onSelected = onSelected,
        backdrop = backdrop,
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val colors = CouiPageDefaults.themeColors()
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.ifBlank { stringResource(R.string.error_load_failed) },
            kind = CouiTextKind.ItemSummary,
            color = colors.summary,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

internal fun filterHomeWorkStream(
    artworks: List<SearchItem>,
    novels: List<SearchItem>,
    filter: Int,
    headCutoff: Int = artworks.size,
): List<SearchItem> {
    val mask = filter.takeIf { (it and (HOME_FILTER_ILLUST or HOME_FILTER_MANGA or HOME_FILTER_NOVEL)) != 0 }
        ?: DEFAULT_HOME_WORK_FILTER
    val showIllust = (mask and HOME_FILTER_ILLUST) != 0
    val showManga = (mask and HOME_FILTER_MANGA) != 0
    val showNovel = (mask and HOME_FILTER_NOVEL) != 0
    val split = headCutoff.coerceIn(0, artworks.size)
    val matchesArtwork: (SearchItem) -> Boolean = { item ->
        (showIllust && item.category == SearchCategory.ILLUST) ||
            (showManga && item.category == SearchCategory.MANGA)
    }
    val filteredArtworks = when {
        showIllust && showManga -> artworks
        showIllust || showManga -> artworks.filter(matchesArtwork)
        else -> emptyList()
    }
    if (!showNovel) return filteredArtworks
    if (filteredArtworks.isEmpty()) return novels
    val filteredHeadCutoff = if (showIllust && showManga) {
        split
    } else {
        artworks.take(split).count(matchesArtwork)
    }
    return interleaveHomeWorks(filteredArtworks, novels, filteredHeadCutoff)
}

private fun interleaveHomeWorks(
    artworks: List<SearchItem>,
    novels: List<SearchItem>,
    headCutoff: Int,
): List<SearchItem> {
    if (novels.isEmpty()) return artworks
    if (artworks.isEmpty()) return novels
    // Only interleave inside the initial head slice so loadMore strictly appends at the
    // end without shifting already-placed cards between columns.
    val split = headCutoff.coerceIn(0, artworks.size)
    val out = ArrayList<SearchItem>(artworks.size + novels.size)
    var nIdx = 0
    for (i in 0 until split) {
        out.add(artworks[i])
        if ((i + 1) % 3 == 0 && nIdx < novels.size) {
            out.add(novels[nIdx++])
        }
    }
    while (nIdx < novels.size) out.add(novels[nIdx++])
    for (i in split until artworks.size) {
        out.add(artworks[i])
    }
    return out
}
