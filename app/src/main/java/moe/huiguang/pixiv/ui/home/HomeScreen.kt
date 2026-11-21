package moe.huiguang.pixiv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
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
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.SlideSwitcher
import moe.huiguang.pixiv.design.components.WaterfallSkeleton

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
    onOpenWork: (SearchItem) -> Unit,
    onOpenPixivision: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val settingsState by settings.state.collectAsState()
    val sessionId = settingsState.session?.userId

    val vm: HomeViewModel = viewModel(key = "home") { HomeViewModel(api, cache, sessionId) }
    val state by vm.state.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val endReached by vm.endReached.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    // 3 sections (推荐 / 排行榜 / 关注新作) with independent saveable scroll positions.
    val gridStates = List(3) { rememberLazyStaggeredGridState() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    val activeGrid = gridStates[section]

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
    val backdrop = rememberCouiBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val dark = CouiTheme.isDark
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val collapsed by remember(activeGrid) {
        derivedStateOf {
            activeGrid.firstVisibleItemIndex > 0 || activeGrid.firstVisibleItemScrollOffset > 24
        }
    }

    // Pull-to-refresh state for the 推荐 tab when overscrolling downward at the top.
    val triggerPullPx = with(density) { 72.dp.toPx() }
    val maxPullPx = with(density) { 112.dp.toPx() }
    val refreshingHoldPx = with(density) { 56.dp.toPx() }
    var rawPullPx by remember { mutableFloatStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    val currentSection by rememberUpdatedState(section)
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentSessionId by rememberUpdatedState(sessionId)

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
                if (currentSection == 0 &&
                    source == NestedScrollSource.UserInput &&
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
                if (currentSection == 0 && rawPullPx >= triggerPullPx && !currentRefreshing) {
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
            refreshing && section == 0 -> refreshingHoldPx
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

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
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
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .couiBackdrop(backdrop)
                            .background(colors.background)
                            .nestedScroll(pullConnection),
                    ) {
                        SlideSwitcher(index = section, modifier = Modifier.fillMaxSize()) { selected ->
                        val source = when (selected) {
                            1 -> current.feed.ranking
                            2 -> current.feed.following
                            else -> current.feed.recommended
                        }
                        // Home displays illustrations and manga only (no novels).
                        val visible = remember(source) {
                            source.filter { it.category != SearchCategory.NOVEL }
                        }
                        val bucket = selected to 0
                        val isLoadingMore = bucket in loadingMore
                        val isEnd = bucket in endReached
                        val gridState = gridStates[selected]

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .overscroll(overscroll),
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
                                item(span = StaggeredGridItemSpan.FullLine, key = "empty-$selected") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.home_section_empty),
                                            kind = CouiTextKind.ItemSummary,
                                            color = colors.summary,
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = visible,
                                    key = { "$selected-${it.category.name}-${it.id}" },
                                    contentType = { "artwork" },
                                ) { item ->
                                    ArtworkCard(
                                        item = item,
                                        revealR18 = settingsState.revealR18,
                                        onClick = { onOpenWork(item) },
                                    )
                                }
                            }

                            item(span = StaggeredGridItemSpan.FullLine, key = "footer-$selected") {
                                LaunchedEffect(visible.size, selected, isEnd) {
                                    if (!isEnd) vm.loadMore(selected, 0)
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = when {
                                            isLoadingMore -> stringResource(R.string.search_loading)
                                            isEnd && visible.isNotEmpty() -> stringResource(R.string.search_end)
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

                    if (animatedPullPx > 4f || (section == 0 && refreshing)) {
                        val pullAlpha = (animatedPullPx / (triggerPullPx * 0.6f)).coerceIn(0f, 1f)
                        val pullTop = fixedGridTopPadding +
                            with(density) { (animatedPullPx * 0.65f).toDp() }
                        val pullLabel = when {
                            refreshing -> stringResource(R.string.home_refreshing)
                            rawPullPx >= triggerPullPx -> stringResource(R.string.home_release_refresh)
                            else -> stringResource(R.string.home_pull_refresh)
                        }
                        GlowSurface(
                            backdrop = backdrop,
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

        HomeOverlayHeader(
            appTitle = stringResource(R.string.app_name),
            collapsed = collapsed,
            statusTop = statusTop,
            section = section,
            onSectionSelected = { section = it },
            backdrop = backdrop,
        )
    }
}

@Composable
private fun HomeOverlayHeader(
    appTitle: String,
    collapsed: Boolean,
    statusTop: Dp,
    section: Int,
    onSectionSelected: (Int) -> Unit,
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
    val switcherShiftYPx = with(density) { (12.dp - (64.dp + 6.dp)).toPx() }
    val shrinkPx = collapsedTitleWidthPx + with(density) { (20.dp + 12.dp - 16.dp).toPx() }
    var switcherWidthPx by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxWidth()) {
        HomeTopBarSurface(
            backdrop = backdrop,
            alphaProvider = { collapseProgressState.value },
            gradientColor = colors.background,
            statusTop = statusTop,
            appTitle = appTitle,
            expandedTitleStyle = expandedTitleStyle,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = statusTop + 64.dp + 6.dp,
                )
                .onSizeChanged { switcherWidthPx = it.width }
                .graphicsLayer {
                    val p = collapseProgressState.value
                    val targetScale = if (switcherWidthPx > 0) {
                        ((switcherWidthPx - shrinkPx) / switcherWidthPx).coerceIn(0.65f, 1f)
                    } else {
                        1f
                    }
                    val s = 1f + (targetScale - 1f) * p
                    scaleX = s
                    scaleY = s
                    translationY = switcherShiftYPx * p
                    transformOrigin = TransformOrigin(1f, 0.5f)
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
) {
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = alphaProvider(),
        gradientColor = gradientColor,
    ) {
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
        }
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

@Composable
private fun PixivisionCard(
    item: PixivisionSummary,
    onClick: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val shape = CouiShape(18.dp, 3f)
    Column(
        Modifier
            .width(236.dp)
            .clip(shape)
            .background(colors.card, shape)
            .clickable(onClick = onClick),
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
                modifier = Modifier.fillMaxSize(),
            )
            if (item.category.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            colors.accent.copy(alpha = 0.9f),
                            CouiShape(8.dp, 3f),
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
