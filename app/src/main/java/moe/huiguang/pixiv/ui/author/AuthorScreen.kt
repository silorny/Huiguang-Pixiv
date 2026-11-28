package moe.huiguang.pixiv.ui.author

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Precision
import coil3.size.Size
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.scroll.CouiOverscrollEffect
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.ShareFormatter
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.AdaptiveTokens
import moe.huiguang.pixiv.design.computeAdaptiveGridColumns
import moe.huiguang.pixiv.design.rememberAdaptiveGridColumns
import moe.huiguang.pixiv.design.rememberIsLandscapeSplit
import moe.huiguang.pixiv.design.rememberWindowSizeDp
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.ArtworkCardOuterShape
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.FloatingSegmentedControl
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.design.components.rememberCouiCardIndication
import moe.huiguang.pixiv.ui.common.ExternalLinkConfirmDialog
import moe.huiguang.pixiv.ui.common.copyPlainText
import moe.huiguang.pixiv.ui.common.openExternalUrl

/**
 * Author page: unified vertical scroll hosting [ProfileHeader] + [SegmentedControl] above a
 * nested [HorizontalPager] (3 persistent work waterfalls), so dragging/overscroll is uniform
 * across the entire page, the segmented bar scrolls completely off-screen past the top bar,
 * and switching categories never reloads or jumps scroll positions.
 */
@Composable
fun AuthorScreen(
    userId: String,
    api: PixivApi,
    engagement: EngagementRepository,
    onBack: () -> Unit,
    onOpenWork: (String, String) -> Unit,
    onOpenProfileList: (String, String) -> Unit,
    onOpenBookmarks: (String) -> Unit,
    settings: SettingsStore,
) {
    val colors = CouiPageDefaults.themeColors()
    val dark = CouiTheme.isDark
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsState by settings.state.collectAsState()
    val followedAuthors by engagement.followedAuthors.collectAsState()
    val scope = rememberCoroutineScope()

    val vm: AuthorViewModel = viewModel(key = "author-$userId") {
        AuthorViewModel(userId, api, engagement, settings)
    }
    val uiState by vm.uiState.collectAsState()
    val profile = uiState.profile
    val bookmarksCount = uiState.bookmarksCount
    val errorText = if (uiState.error && profile == null) {
        resources.getString(R.string.error_author_load_failed)
    } else {
        null
    }
    val isFollowed = followedAuthors[userId] ?: (profile?.isFollowed == true)

    LaunchedEffect(vm) {
        vm.ensureInitialized()
    }

    DisposableEffect(lifecycleOwner, vm) {
        var leftForeground = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> leftForeground = true
                Lifecycle.Event.ON_RESUME -> if (leftForeground) {
                    leftForeground = false
                    vm.refreshOnResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engagement, userId) {
        engagement.events.collect { event ->
            if (event is EngagementEvent.Failed && event.kind == EngagementKind.FOLLOW && event.id == userId) {
                Toast.makeText(
                    context,
                    event.reason?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.action_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    var savedFilter by rememberSaveable(userId) { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedFilter, pageCount = { 3 })
    val filter by remember { derivedStateOf { pagerState.currentPage } }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { savedFilter = it }
    }

    val gridStates = List(3) { index ->
        rememberSaveable(userId, index, saver = LazyStaggeredGridState.Saver) {
            LazyStaggeredGridState()
        }
    }
    val outerOverscroll = rememberCouiOverscrollEffect()

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

    val backdrop = rememberCouiBackdrop()
    // Flat sibling source for profile chips, external link pills, stats capsule, and SegmentedControl.
    val effectBackdrop = rememberCouiBackdrop()
    val effectDark = isDarkBackdrop(colors.background)
    val windowBackdrop = rememberCouiWindowBackdrop()
    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBarRowHeight = 52.dp
    val topBarBottomDp = statusTop + topBarRowHeight
    val profileTopGap = 10.dp
    val collapseLeadPx = with(density) { 8.dp.toPx() }
    val topGapPx = with(density) { profileTopGap.toPx() }
    val windowSize = rememberWindowSizeDp()
    val isExpandedTwoPane = rememberIsLandscapeSplit()
    val profilePaneScroll = rememberScrollState()

    var nameRowTopPx by remember(userId) { mutableFloatStateOf(0f) }
    var headerHeightPx by remember(userId) { mutableIntStateOf(0) }
    var headerOffsetPx by rememberSaveable(userId) { mutableFloatStateOf(0f) }
    var lastMaxHeaderOffsetPx by remember(userId) { mutableFloatStateOf(0f) }
    val maxHeaderOffsetPx by remember {
        derivedStateOf { headerHeightPx.toFloat().coerceAtLeast(0f) }
    }
    LaunchedEffect(maxHeaderOffsetPx) {
        val prevMax = lastMaxHeaderOffsetPx
        lastMaxHeaderOffsetPx = maxHeaderOffsetPx
        if (maxHeaderOffsetPx <= 0f) {
            headerOffsetPx = 0f
        } else if (prevMax > 0f && headerOffsetPx >= prevMax - 1f) {
            // Keep the works grid pinned to top when profile/bio height changes while collapsed.
            headerOffsetPx = maxHeaderOffsetPx
        } else if (headerOffsetPx > maxHeaderOffsetPx) {
            headerOffsetPx = maxHeaderOffsetPx
        }
    }

    val showTopBarTitle by remember(collapseLeadPx, topGapPx, isExpandedTwoPane) {
        derivedStateOf {
            nameRowTopPx > 0f && if (isExpandedTwoPane) {
                profilePaneScroll.value >= (nameRowTopPx - collapseLeadPx).coerceAtLeast(0f)
            } else {
                headerOffsetPx >= (nameRowTopPx + topGapPx - collapseLeadPx).coerceAtLeast(0f)
            }
        }
    }
    val topBarTitleProgress by animateFloatAsState(
        targetValue = if (showTopBarTitle) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "authorTopBarTitle",
    )

    fun consumeHeaderDelta(deltaPx: Float): Float {
        if (maxHeaderOffsetPx <= 0f || deltaPx == 0f) return 0f
        val current = headerOffsetPx
        val next = (current + deltaPx).coerceIn(0f, maxHeaderOffsetPx)
        val consumed = next - current
        if (consumed != 0f) headerOffsetPx = next
        return consumed
    }

    fun activeGrid(): LazyStaggeredGridState = gridStates[pagerState.currentPage]

    // Monotonic scroll invariant shared by both header drag and pager nested scroll:
    // - Scrolling down (+): collapse header (0 -> max) first, then scroll activeGrid forward.
    // - Scrolling up (-): scroll activeGrid back to 0 first, then expand header (max -> 0).
    fun dispatchUnifiedDelta(delta: Float): Float {
        val grid = activeGrid()
        return if (delta > 0f) {
            val headerConsumed = consumeHeaderDelta(delta)
            val remaining = delta - headerConsumed
            val gridConsumed = if (remaining > 0.01f) {
                grid.dispatchRawDelta(remaining)
            } else {
                0f
            }
            headerConsumed + gridConsumed
        } else {
            val gridConsumed = if (grid.canScrollBackward) {
                grid.dispatchRawDelta(delta)
            } else {
                0f
            }
            val remaining = delta - gridConsumed
            val headerConsumed = if (remaining < -0.01f) {
                consumeHeaderDelta(remaining)
            } else {
                0f
            }
            gridConsumed + headerConsumed
        }
    }

    val headerScrollState = rememberScrollableState { delta ->
        dispatchUnifiedDelta(delta)
    }
    val decaySpec = rememberSplineBasedDecay<Float>()
    val headerFling = remember(userId, decaySpec) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                var remaining = 0f
                var lastValue = 0f
                AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decaySpec) {
                    val step = value - lastValue
                    lastValue = value
                    val consumed = scrollBy(step)
                    if (abs(step - consumed) > 0.5f) {
                        remaining = velocity
                        cancelAnimation()
                    }
                }
                return remaining
            }
        }
    }

    suspend fun decayHeader(initialScreenVelocityY: Float): Float {
        if (abs(initialScreenVelocityY) < 1f) return 0f
        var lastValue = 0f
        var leftoverVelocity = 0f
        AnimationState(initialValue = 0f, initialVelocity = -initialScreenVelocityY).animateDecay(decaySpec) {
            val step = value - lastValue
            lastValue = value
            val consumed = consumeHeaderDelta(step)
            if (abs(step - consumed) > 0.5f) {
                leftoverVelocity = -velocity
                cancelAnimation()
            }
        }
        return leftoverVelocity
    }

    suspend fun decayGrid(grid: LazyStaggeredGridState, initialScreenVelocityY: Float): Float {
        if (abs(initialScreenVelocityY) < 1f) return 0f
        var leftoverVelocity = 0f
        grid.scroll {
            var lastValue = 0f
            AnimationState(initialValue = 0f, initialVelocity = -initialScreenVelocityY).animateDecay(decaySpec) {
                val step = value - lastValue
                lastValue = value
                val consumed = scrollBy(step)
                if (abs(step - consumed) > 0.5f) {
                    leftoverVelocity = -velocity
                    cancelAnimation()
                }
            }
        }
        return leftoverVelocity
    }

    suspend fun triggerOverFling(velocityY: Float) {
        if (abs(velocityY) < 1f) return
        outerOverscroll.applyToFling(Velocity(0f, velocityY)) { vel ->
            val step = (vel.y * 0.016f).coerceIn(-64f, 64f)
            if (step != 0f) {
                outerOverscroll.applyToScroll(Offset(0f, step), NestedScrollSource.SideEffect) { Offset.Zero }
            }
            Velocity.Zero
        }
    }

    val pagerCoordinator = remember(userId, outerOverscroll, decaySpec) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val grid = activeGrid()
                if (outerOverscroll.isInProgress && source == NestedScrollSource.UserInput) {
                    return outerOverscroll.applyToScroll(available, source) { rem ->
                        val consumedY = -dispatchUnifiedDelta(-rem.y)
                        Offset(0f, consumedY)
                    }
                }
                if (available.y < 0f && headerOffsetPx < maxHeaderOffsetPx) {
                    val consumed = consumeHeaderDelta(-available.y)
                    if (consumed != 0f) return Offset(0f, -consumed)
                } else if (
                    available.y > 0f &&
                    headerOffsetPx > 0f &&
                    !grid.canScrollBackward
                ) {
                    val consumed = consumeHeaderDelta(-available.y)
                    if (consumed != 0f) return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val headerConsumed = if (available.y > 0f && headerOffsetPx > 0f) {
                    -consumeHeaderDelta(-available.y)
                } else {
                    0f
                }
                val leftoverY = available.y - headerConsumed
                val overConsumed = if (leftoverY != 0f) {
                    outerOverscroll.applyToScroll(Offset(0f, leftoverY), source) { Offset.Zero }
                } else {
                    Offset.Zero
                }
                return Offset(0f, headerConsumed) + overConsumed
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (outerOverscroll.isInProgress) {
                    outerOverscroll.applyToFling(available) { rem ->
                        var remY = rem.y
                        if (remY < 0f && headerOffsetPx < maxHeaderOffsetPx) {
                            remY = decayHeader(remY)
                        }
                        if (remY < -1f) {
                            remY = decayGrid(activeGrid(), remY)
                        } else if (remY > 1f && activeGrid().canScrollBackward) {
                            remY = decayGrid(activeGrid(), remY)
                            if (remY > 1f && headerOffsetPx > 0f) {
                                remY = decayHeader(remY)
                            }
                        } else if (remY > 1f && headerOffsetPx > 0f) {
                            remY = decayHeader(remY)
                        }
                        Velocity(0f, rem.y - remY)
                    }
                    return available
                }
                if (available.y < 0f && headerOffsetPx < maxHeaderOffsetPx) {
                    val leftover = decayHeader(available.y)
                    return Velocity(0f, available.y - leftover)
                }
                if (available.y > 0f && headerOffsetPx > 0f && !activeGrid().canScrollBackward) {
                    val leftover = decayHeader(available.y)
                    if (leftover > 1f) {
                        triggerOverFling(leftover)
                        return available
                    }
                    return Velocity(0f, available.y - leftover)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && headerOffsetPx > 0f) {
                    val leftover = decayHeader(available.y)
                    if (leftover > 1f) {
                        triggerOverFling(leftover)
                    }
                    return available
                }
                if (abs(available.y) > 1f) {
                    triggerOverFling(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    var pendingExternalLink by remember { mutableStateOf<AuthorLink?>(null) }
    var linkDialogVisible by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
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
            if (isExpandedTwoPane) {
                val rightPaneColumns = computeAdaptiveGridColumns(
                    availableWidth = (windowSize.width - AdaptiveTokens.AuthorPaneWidth).coerceAtLeast(320.dp),
                )
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(AdaptiveTokens.AuthorPaneWidth)
                            .fillMaxHeight()
                            .overscroll(rememberCouiOverscrollEffect())
                            .verticalScroll(profilePaneScroll)
                            .padding(
                                top = topBarBottomDp + profileTopGap,
                                bottom = navBottom + 24.dp,
                                start = 16.dp,
                                end = 8.dp,
                            ),
                    ) {
                        ProfileHeader(
                            userId = userId,
                            profile = profile,
                            bookmarksCount = bookmarksCount,
                            error = errorText,
                            isFollowed = isFollowed,
                            onToggleFollow = {
                                if (settingsState.session == null) {
                                    Toast.makeText(
                                        context,
                                        resources.getString(R.string.bookmarks_need_login),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    engagement.toggleFollow(userId)
                                }
                            },
                            onOpenFollowing = { onOpenProfileList(userId, "following") },
                            onOpenMyPixiv = { onOpenProfileList(userId, "mypixiv") },
                            onOpenBookmarks = { onOpenBookmarks(userId) },
                            backdrop = effectBackdrop,
                            dark = effectDark,
                            titleProgress = topBarTitleProgress,
                            onNameRowPositioned = { nameRowTopPx = it },
                            onRequestExternalLink = { link ->
                                pendingExternalLink = link
                                linkDialogVisible = true
                            },
                        )
                    }
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 2,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { pageFilter ->
                        val categoryState = uiState.categories[pageFilter]
                        AuthorWorksPage(
                            userId = userId,
                            pageFilter = pageFilter,
                            categoryState = categoryState,
                            gridState = gridStates[pageFilter],
                            navBottom = navBottom,
                            revealR18 = settingsState.revealR18,
                            colors = colors,
                            dark = dark,
                            cardIndication = cardIndication,
                            titleStyle = cardTitleStyle,
                            summaryStyle = cardSummaryStyle,
                            accentSummaryStyle = cardAccentSummaryStyle,
                            columns = rightPaneColumns,
                            topPadding = topBarBottomDp + 6.dp,
                            onLoadMore = { vm.loadMore(pageFilter) },
                            onOpenWork = onOpenWork,
                        )
                    }
                }
            } else {
                Layout(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .overscroll(outerOverscroll),
                    content = {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .scrollable(
                                    state = headerScrollState,
                                    orientation = Orientation.Vertical,
                                    reverseDirection = true,
                                    overscrollEffect = outerOverscroll,
                                    flingBehavior = headerFling,
                                )
                                .padding(
                                    top = topBarBottomDp + profileTopGap,
                                    start = 16.dp,
                                    end = 16.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth(),
                            ) {
                                ProfileHeader(
                                    userId = userId,
                                    profile = profile,
                                    bookmarksCount = bookmarksCount,
                                    error = errorText,
                                    isFollowed = isFollowed,
                                    onToggleFollow = {
                                        if (settingsState.session == null) {
                                            Toast.makeText(
                                                context,
                                                resources.getString(R.string.bookmarks_need_login),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            engagement.toggleFollow(userId)
                                        }
                                    },
                                    onOpenFollowing = { onOpenProfileList(userId, "following") },
                                    onOpenMyPixiv = { onOpenProfileList(userId, "mypixiv") },
                                    onOpenBookmarks = { onOpenBookmarks(userId) },
                                    backdrop = effectBackdrop,
                                    dark = effectDark,
                                    titleProgress = topBarTitleProgress,
                                    onNameRowPositioned = { nameRowTopPx = it },
                                    onRequestExternalLink = { link ->
                                        pendingExternalLink = link
                                        linkDialogVisible = true
                                    },
                                )
                            }
                            Text(
                                text = stringResource(R.string.author_works_title),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 10.dp),
                            )
                        }

                        Box(
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(pagerCoordinator),
                        ) {
                            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                                HorizontalPager(
                                    state = pagerState,
                                    beyondViewportPageCount = 2,
                                    modifier = Modifier.fillMaxSize(),
                                ) { pageFilter ->
                                    val categoryState = uiState.categories[pageFilter]
                                    AuthorWorksPage(
                                        userId = userId,
                                        pageFilter = pageFilter,
                                        categoryState = categoryState,
                                        gridState = gridStates[pageFilter],
                                        navBottom = navBottom + 64.dp,
                                        revealR18 = settingsState.revealR18,
                                        colors = colors,
                                        dark = dark,
                                        cardIndication = cardIndication,
                                        titleStyle = cardTitleStyle,
                                        summaryStyle = cardSummaryStyle,
                                        accentSummaryStyle = cardAccentSummaryStyle,
                                        onLoadMore = { vm.loadMore(pageFilter) },
                                        onOpenWork = onOpenWork,
                                    )
                                }
                            }
                        }
                    },
                ) { measurables, constraints ->
                    val headerPlaceable = measurables[0].measure(
                        constraints.copy(
                            minWidth = constraints.maxWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                    if (headerHeightPx != headerPlaceable.height) {
                        headerHeightPx = headerPlaceable.height
                    }
                    val pagerPlaceable = measurables[1].measure(
                        Constraints.fixed(constraints.maxWidth, constraints.maxHeight),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val offsetY = headerOffsetPx.roundToInt().coerceIn(0, headerPlaceable.height)
                        val pagerTop = (headerPlaceable.height - offsetY).coerceAtLeast(0)
                        pagerPlaceable.placeRelative(0, pagerTop)
                        headerPlaceable.placeRelative(0, -offsetY)
                    }
                }
            }
        }

        if (!isExpandedTwoPane) {
            val navItems = listOf(
                CouiNavigationItem(
                    title = stringResource(R.string.filter_illust),
                    icon = CouiIcons.Outlined.Photo,
                    selectedIcon = CouiIcons.Filled.Photo,
                ),
                CouiNavigationItem(
                    title = stringResource(R.string.filter_manga),
                    icon = CouiIcons.Outlined.Album,
                    selectedIcon = CouiIcons.Filled.Album,
                ),
                CouiNavigationItem(
                    title = stringResource(R.string.filter_novel),
                    icon = CouiIcons.Filled.BookOpen,
                    selectedIcon = CouiIcons.Filled.BookOpen,
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
                    selectedIndex = filter,
                    onSelection = { target ->
                        scope.launch { pagerState.animateScrollToPage(target) }
                    },
                )
            }
        }

        val authorUrl = "https://www.pixiv.net/users/$userId"
        val shareText = ShareFormatter.formatAuthor(
            template = settingsState.authorShareFormat,
            userName = profile?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.author_title, userId),
            userId = profile?.id?.takeIf { it.isNotBlank() } ?: userId,
            url = authorUrl,
            fallbackTemplate = stringResource(R.string.share_format_default_author),
        )
        AuthorTopBar(
            backdrop = backdrop,
            dark = effectDark,
            title = profile?.name ?: stringResource(R.string.author_title, userId),
            backgroundColor = colors.background,
            filter = filter,
            onSelectFilter = { target ->
                scope.launch { pagerState.animateScrollToPage(target) }
            },
            showFloatingSwitcher = isExpandedTwoPane,
            leftHanded = settingsState.leftHanded,
            titleProgress = topBarTitleProgress,
            onBack = onBack,
            onOpenBrowser = { openExternalUrl(context, authorUrl) },
            onCopyLink = {
                copyPlainText(context, "pixiv", shareText, resources.getString(R.string.detail_link_copied))
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
        )
    }

    pendingExternalLink?.let { link ->
        ExternalLinkConfirmDialog(
            visible = linkDialogVisible,
            url = link.url,
            dark = dark,
            colors = colors,
            material = dialogMaterial,
            backdrop = windowBackdrop,
            onDismiss = { linkDialogVisible = false },
            onCopyUrl = {
                copyPlainText(context, "author_link", link.url, resources.getString(R.string.detail_link_copied))
                linkDialogVisible = false
            },
            onOpenUrl = {
                linkDialogVisible = false
                openExternalUrl(context, link.url)
            },
        )
    }
}

@Composable
private fun AuthorWorksPage(
    userId: String,
    pageFilter: Int,
    categoryState: AuthorCategoryState,
    gridState: LazyStaggeredGridState,
    navBottom: Dp,
    revealR18: Boolean,
    colors: com.itos.coui.layout.CouiPageColors,
    dark: Boolean,
    cardIndication: androidx.compose.foundation.IndicationNodeFactory,
    titleStyle: TextStyle,
    summaryStyle: TextStyle,
    accentSummaryStyle: TextStyle,
    onLoadMore: () -> Unit,
    onOpenWork: (String, String) -> Unit,
    columns: Int = rememberAdaptiveGridColumns(),
    topPadding: Dp = 0.dp,
    horizontalContentPadding: Dp = 16.dp,
) {
    val appContext = LocalContext.current
    val visibleWorks = categoryState.items
    val isLoading = categoryState.loading
    val isEnded = categoryState.ended
    val latestWorks by rememberUpdatedState(visibleWorks)
    val latestLoading by rememberUpdatedState(isLoading)
    val latestEnded by rememberUpdatedState(isEnded)

    LaunchedEffect(gridState, userId, pageFilter) {
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
                val list = latestWorks
                if (list.isEmpty()) return@collectLatest
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
                    val url = moe.huiguang.pixiv.design.components.downgradeWaterfallThumbUrl(raw)
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

    LaunchedEffect(gridState, userId, pageFilter) {
        snapshotFlow {
            if (latestWorks.isEmpty() || latestLoading || latestEnded) {
                false
            } else {
                val info = gridState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                total > 1 && lastVisible >= total - 8
            }
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalContentPadding,
            end = horizontalContentPadding,
            top = topPadding,
            bottom = 24.dp + navBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        // Keep a permanent non-zero index-0 anchor so LazyVerticalStaggeredGrid never anchors
        // onto the footer during initial load and always reports canScrollBackward == false at top.
        item(span = StaggeredGridItemSpan.FullLine, key = "top-anchor-$pageFilter") {
            Spacer(Modifier.fillMaxWidth().height(1.dp))
        }

        if (visibleWorks.isEmpty() && !isLoading) {
            item(span = StaggeredGridItemSpan.FullLine, key = "empty-$pageFilter") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 112.dp, bottom = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_category_empty),
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                    )
                }
            }
        } else {
            items(
                items = visibleWorks,
                key = { "${it.category}-${it.id}" },
                contentType = { if (it.category == SearchCategory.NOVEL) "novel" else "artwork" },
            ) { item ->
                ArtworkCard(
                    item = item,
                    showAuthor = false,
                    revealR18 = revealR18,
                    lowQualityThumb = true,
                    colors = colors,
                    dark = dark,
                    cardIndication = cardIndication,
                    titleStyle = titleStyle,
                    summaryStyle = summaryStyle,
                    accentSummaryStyle = accentSummaryStyle,
                    onClick = {
                        val kind = if (item.category == SearchCategory.NOVEL) {
                            "novel"
                        } else {
                            "illust"
                        }
                        onOpenWork(kind, item.id)
                    },
                )
            }
        }

        item(span = StaggeredGridItemSpan.FullLine, key = "author-footer-$pageFilter") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        isLoading -> stringResource(R.string.search_loading)
                        isEnded && visibleWorks.isNotEmpty() -> stringResource(R.string.search_end)
                        else -> ""
                    },
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
            }
        }
    }
}

@Composable
private fun AuthorTopBar(
    backdrop: CouiBackdrop,
    dark: Boolean,
    title: String,
    backgroundColor: Color,
    filter: Int,
    onSelectFilter: (Int) -> Unit,
    showFloatingSwitcher: Boolean,
    leftHanded: Boolean,
    titleProgress: Float,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
) {
    val barColors = CouiTopAppBarDefaults.colors(dark = CouiTheme.isDark)
    val density = LocalDensity.current
    val topRisePx = with(density) { 12.dp.toPx() }
    var menuExpanded by remember { mutableStateOf(false) }
    val resolvedAlpha = titleProgress.coerceIn(0f, 1f)
    val floatingSwitcher: @Composable () -> Unit = {
        FloatingSegmentedControl(
            labels = listOf(
                stringResource(R.string.filter_illust),
                stringResource(R.string.filter_manga),
                stringResource(R.string.filter_novel),
            ),
            selectedIndex = filter,
            onSelected = onSelectFilter,
            backdrop = backdrop,
            dark = dark,
        )
    }
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        fadeHeight = 72.dp,
        bottomExtension = 8.dp,
        backgroundAlpha = 1f,
        gradientColor = backgroundColor,
        dark = dark,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = CouiIcons.Filled.Back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                backdrop = backdrop,
                dark = dark,
                blurRadius = 20f,
            )
            if (showFloatingSwitcher && leftHanded) {
                Spacer(Modifier.width(8.dp))
                floatingSwitcher()
            }
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = title,
                style = TextStyle(
                    color = barColors.titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        alpha = resolvedAlpha
                        translationY = (1f - resolvedAlpha) * topRisePx
                    },
            )
            if (showFloatingSwitcher && !leftHanded) {
                floatingSwitcher()
                Spacer(Modifier.width(8.dp))
            }
            Box {
                CircleIconButton(
                    icon = CouiIcons.Filled.More,
                    contentDescription = stringResource(R.string.action_more),
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
                            text = stringResource(R.string.filter_illust),
                            selected = filter == 0,
                        ),
                        CouiMenuItem(
                            value = 1,
                            text = stringResource(R.string.filter_manga),
                            selected = filter == 1,
                        ),
                        CouiMenuItem(
                            value = 2,
                            text = stringResource(R.string.filter_novel),
                            selected = filter == 2,
                        ),
                        CouiMenuItem(
                            value = 3,
                            text = stringResource(R.string.detail_open_browser),
                            dividerBefore = true,
                        ),
                        CouiMenuItem(value = 4, text = stringResource(R.string.detail_copy_link)),
                        CouiMenuItem(value = 5, text = stringResource(R.string.detail_action_share)),
                    ),
                    onItemClick = { item ->
                        menuExpanded = false
                        when (item.value) {
                            in 0..2 -> onSelectFilter(item.value)
                            3 -> onOpenBrowser()
                            4 -> onCopyLink()
                            5 -> onShare()
                        }
                    },
                )
            }
        }
    }
}
