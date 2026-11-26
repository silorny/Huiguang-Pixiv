package moe.huiguang.pixiv.ui.author

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
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
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.ShareFormatter
import moe.huiguang.pixiv.data.model.UserProfile
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.design.shape.CachedCouiShape
import moe.huiguang.pixiv.design.shape.g2CornerMask
import moe.huiguang.pixiv.ui.common.ExternalLinkConfirmDialog
import moe.huiguang.pixiv.ui.common.copyPlainText
import moe.huiguang.pixiv.ui.common.openExternalUrl

/** Author page: fused cover/avatar header, bio card, glowing external link chips, category switch, and works waterfall. */
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
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var bookmarksCount by remember(userId) { mutableStateOf<Int?>(null) }
    val isFollowed = followedAuthors[userId] ?: (profile?.isFollowed == true)
    var resumeTick by remember { mutableIntStateOf(0) }
    var illusts by remember(userId) { mutableStateOf<List<SearchItem>>(emptyList()) }
    var illustsEnded by remember(userId) { mutableStateOf(false) }
    var loadingIllusts by remember(userId) { mutableStateOf(false) }
    var mangas by remember(userId) { mutableStateOf<List<SearchItem>>(emptyList()) }
    var mangasEnded by remember(userId) { mutableStateOf(false) }
    var loadingMangas by remember(userId) { mutableStateOf(false) }
    var novels by remember(userId) { mutableStateOf<List<SearchItem>>(emptyList()) }
    var novelsEnded by remember(userId) { mutableStateOf(false) }
    var loadingNovels by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var savedFilter by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedFilter, pageCount = { 3 })
    val filter = pagerState.currentPage
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { savedFilter = it }
    }
    val gridStates = remember { List(3) { androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState() } }
    val backdrop = rememberCouiBackdrop()
    // Flat sibling source for profile chips, external link pills, and the stats capsule.
    val effectBackdrop = rememberCouiBackdrop()
    val effectDark = isDarkBackdrop(colors.background)
    val windowBackdrop = rememberCouiWindowBackdrop()
    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInsetPx = with(density) { (statusTop + 52.dp).toPx() }
    val topGapPx = with(density) { 12.dp.toPx() }
    val collapseLeadPx = with(density) { 8.dp.toPx() }
    var nameRowTopPx by remember(userId) { mutableFloatStateOf(0f) }
    var headerHeightPx by remember(userId) { mutableIntStateOf(0) }
    var headerOffsetPx by remember(userId) { mutableFloatStateOf(0f) }
    val maxHeaderOffsetPx by remember {
        derivedStateOf { (headerHeightPx - topInsetPx).coerceAtLeast(0f) }
    }
    LaunchedEffect(maxHeaderOffsetPx) {
        if (headerOffsetPx > maxHeaderOffsetPx) {
            headerOffsetPx = maxHeaderOffsetPx
        }
    }

    fun consumeHeaderDelta(deltaPx: Float): Float {
        if (maxHeaderOffsetPx <= 0f) return 0f
        val current = headerOffsetPx
        val next = (current + deltaPx).coerceIn(0f, maxHeaderOffsetPx)
        val consumed = next - current
        if (consumed != 0f) headerOffsetPx = next
        return consumed
    }

    val headerScrollState = rememberScrollableState { delta -> consumeHeaderDelta(delta) }
    val decaySpec = rememberSplineBasedDecay<Float>()
    val headerCollapseConnection = remember(decaySpec) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val activeGrid = gridStates[pagerState.currentPage]
                if (available.y < 0f && headerOffsetPx < maxHeaderOffsetPx) {
                    val consumed = consumeHeaderDelta(-available.y)
                    if (consumed != 0f) return Offset(0f, -consumed)
                } else if (
                    available.y > 0f &&
                    headerOffsetPx > 0f &&
                    activeGrid.firstVisibleItemIndex == 0 &&
                    activeGrid.firstVisibleItemScrollOffset == 0
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
                if (available.y > 0f && headerOffsetPx > 0f) {
                    val didConsume = consumeHeaderDelta(-available.y)
                    if (didConsume != 0f) return Offset(0f, -didConsume)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val activeGrid = gridStates[pagerState.currentPage]
                val collapsing = available.y < 0f && headerOffsetPx < maxHeaderOffsetPx
                val expandingAtTop = available.y > 0f &&
                    headerOffsetPx > 0f &&
                    activeGrid.firstVisibleItemIndex == 0 &&
                    activeGrid.firstVisibleItemScrollOffset == 0
                if (!collapsing && !expandingAtTop) return Velocity.Zero
                var lastValue = headerOffsetPx
                var leftoverVel = 0f
                val anim = AnimationState(initialValue = lastValue, initialVelocity = -available.y)
                anim.animateDecay(decaySpec) {
                    val step = value - lastValue
                    lastValue = value
                    val actuallyConsumed = consumeHeaderDelta(step)
                    if (kotlin.math.abs(actuallyConsumed) < kotlin.math.abs(step) - 0.5f) {
                        leftoverVel = velocity
                        cancelAnimation()
                    }
                }
                return Velocity(0f, available.y + leftoverVel)
            }
        }
    }

    val showTopBarTitle by remember(collapseLeadPx, topGapPx) {
        derivedStateOf {
            nameRowTopPx > 0f &&
                headerOffsetPx >= (nameRowTopPx + topGapPx - collapseLeadPx).coerceAtLeast(0f)
        }
    }
    val topBarTitleProgress by animateFloatAsState(
        targetValue = if (showTopBarTitle) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "authorTopBarTitle",
    )

    var pendingExternalLink by remember { mutableStateOf<AuthorLink?>(null) }
    var linkDialogVisible by remember { mutableStateOf(false) }

    suspend fun loadMoreIllusts() {
        if (loadingIllusts || illustsEnded) return
        loadingIllusts = true
        runCatching { api.userArtworkItemsPage(userId, SearchCategory.ILLUST, offset = illusts.size, limit = 18) }
            .onSuccess { page ->
                val known = illusts.mapTo(HashSet()) { it.id }
                val fresh = page.filter { it.id !in known }
                illusts = illusts + fresh
                if (page.size < 18 || fresh.isEmpty()) illustsEnded = true
            }
            .onFailure { if (illusts.isEmpty()) illustsEnded = true }
        loadingIllusts = false
    }

    suspend fun loadMoreMangas() {
        if (loadingMangas || mangasEnded) return
        loadingMangas = true
        runCatching { api.userArtworkItemsPage(userId, SearchCategory.MANGA, offset = mangas.size, limit = 18) }
            .onSuccess { page ->
                val known = mangas.mapTo(HashSet()) { it.id }
                val fresh = page.filter { it.id !in known }
                mangas = mangas + fresh
                if (page.size < 18 || fresh.isEmpty()) mangasEnded = true
            }
            .onFailure { if (mangas.isEmpty()) mangasEnded = true }
        loadingMangas = false
    }

    suspend fun loadMoreNovels() {
        if (loadingNovels || novelsEnded) return
        loadingNovels = true
        runCatching { api.userNovelWorksPage(userId, offset = novels.size, limit = 18) }
            .onSuccess { page ->
                val known = novels.mapTo(HashSet()) { it.id }
                val fresh = page.filter { it.id !in known }
                novels = novels + fresh
                if (page.size < 18 || fresh.isEmpty()) novelsEnded = true
            }
            .onFailure { if (novels.isEmpty()) novelsEnded = true }
        loadingNovels = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
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

    LaunchedEffect(userId) {
        val loaded = runCatching { api.user(userId) }.getOrNull()
        if (loaded != null) {
            profile = loaded
            engagement.syncFollow(userId, loaded.isFollowed)
            settings.addHistory(
                moe.huiguang.pixiv.data.model.HistoryEntry(
                    pid = loaded.id,
                    kind = moe.huiguang.pixiv.data.model.HistoryEntry.KIND_AUTHOR,
                    title = loaded.name,
                    thumbUrl = loaded.avatarUrl,
                    at = System.currentTimeMillis(),
                    userName = loaded.name,
                    illustType = -1,
                ),
            )
        } else {
            error = resources.getString(R.string.error_author_load_failed)
        }
        loadMoreIllusts()
    }

    LaunchedEffect(userId, resumeTick) {
        if (resumeTick > 0) {
            val loaded = runCatching { api.user(userId) }.getOrNull()
            if (loaded != null) {
                profile = loaded
                engagement.syncFollow(userId, loaded.isFollowed)
            }
        }
        bookmarksCount = runCatching { api.userBookmarksTotal(userId) }.getOrNull() ?: bookmarksCount
    }

    LaunchedEffect(userId) {
        loadMoreMangas()
    }

    LaunchedEffect(userId) {
        loadMoreNovels()
    }

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
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scrollable(
                                state = headerScrollState,
                                orientation = Orientation.Vertical,
                                reverseDirection = true,
                            )
                            .padding(start = 16.dp, end = 16.dp, top = statusTop + 52.dp + 12.dp),
                    ) {
                        ProfileHeader(
                            userId = userId,
                            profile = profile,
                            bookmarksCount = bookmarksCount,
                            error = error,
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
                        SegmentedControl(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            labels = listOf(
                                stringResource(R.string.filter_illust),
                                stringResource(R.string.filter_manga),
                                stringResource(R.string.filter_novel),
                            ),
                            selectedIndex = filter,
                            onSelected = { target ->
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(headerCollapseConnection),
                    ) { pageFilter ->
                        val pageGridState = gridStates[pageFilter]
                        val pageOverscroll = rememberCouiOverscrollEffect()
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = pageGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .overscroll(pageOverscroll),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 2.dp,
                                bottom = 24.dp + navBottom,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            val visibleWorks: List<SearchItem> = when (pageFilter) {
                                1 -> mangas
                                2 -> novels
                                else -> illusts
                            }
                            val isLoading = when (pageFilter) {
                                1 -> loadingMangas
                                2 -> loadingNovels
                                else -> loadingIllusts
                            }
                            val isEnded = when (pageFilter) {
                                1 -> mangasEnded
                                2 -> novelsEnded
                                else -> illustsEnded
                            }

                            if (visibleWorks.isEmpty() && !isLoading) {
                                item(span = StaggeredGridItemSpan.FullLine, key = "empty-$pageFilter") {
                                    Text(
                                        text = stringResource(R.string.home_category_empty),
                                        kind = CouiTextKind.ItemSummary,
                                        color = colors.summary,
                                        modifier = Modifier.padding(32.dp),
                                    )
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
                                        revealR18 = settingsState.revealR18,
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
                                LaunchedEffect(pageFilter, illusts.size, mangas.size, novels.size, isEnded) {
                                    if (!isEnded) {
                                        scope.launch {
                                            when (pageFilter) {
                                                1 -> loadMoreMangas()
                                                2 -> loadMoreNovels()
                                                else -> loadMoreIllusts()
                                            }
                                        }
                                    }
                                }
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
                },
            ) { measurables, constraints ->
                val headerPlaceable = measurables[0].measure(
                    constraints.copy(minWidth = 0, minHeight = 0),
                )
                if (headerHeightPx != headerPlaceable.height) {
                    headerHeightPx = headerPlaceable.height
                }
                val topInsetRound = topInsetPx.toInt()
                val pagerHeight = (constraints.maxHeight - topInsetRound).coerceAtLeast(0)
                val pagerPlaceable = measurables[1].measure(
                    Constraints.fixed(constraints.maxWidth, pagerHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val offsetY = headerOffsetPx.toInt()
                    val pagerTop = (headerPlaceable.height - offsetY).coerceAtLeast(topInsetRound)
                    pagerPlaceable.placeRelative(0, pagerTop)
                    headerPlaceable.placeRelative(0, -offsetY)
                }
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
            titleProgress = topBarTitleProgress,
            onBack = onBack,
            onOpenBrowser = {
                openExternalUrl(context, authorUrl)
            },
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
private fun AuthorTopBar(
    backdrop: CouiBackdrop,
    dark: Boolean,
    title: String,
    backgroundColor: Color,
    filter: Int,
    onSelectFilter: (Int) -> Unit,
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
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
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

private val AuthorCoverShape = CachedCouiShape(20.dp, 3f)
private val UidChipShape = CachedCouiShape(6.dp, 3f)
private val BioCardShape = CouiShape(20.dp, 3f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(
    userId: String,
    profile: UserProfile?,
    bookmarksCount: Int?,
    error: String?,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenMyPixiv: () -> Unit,
    onOpenBookmarks: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
    titleProgress: Float,
    onNameRowPositioned: (Float) -> Unit,
    onRequestExternalLink: (AuthorLink) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val nameShiftPx = with(density) { 10.dp.toPx() }
    val displayUid = profile?.id?.takeIf { it.isNotBlank() } ?: userId
    val coverUrl = profile?.backgroundUrl.orEmpty()
    val webpageFallback = stringResource(R.string.author_webpage_label)
    val commentText = profile?.comment?.trim().orEmpty()
    val profileLinks = remember(commentText, profile?.webpage, webpageFallback) {
        extractProfileLinks(commentText, profile?.webpage, webpageFallback)
    }
    val inlineLinks = remember(commentText, webpageFallback) {
        extractInlineLinkSpans(commentText, webpageFallback)
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (coverUrl.isNotBlank()) {
            // Fused cover + overlapping avatar + far-right UID strip.
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 38.dp)
                        .aspectRatio(1.9f)
                        .background(colors.card, AuthorCoverShape)
                        .g2CornerMask(AuthorCoverShape, colors.background),
                ) {
                    RetryableImage(
                        url = coverUrl,
                        contentDescription = profile?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colors.background.copy(alpha = 0.28f)),
                                ),
                            ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .height(38.dp)
                        .padding(start = 94.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    UidCopyChip(
                        uid = displayUid,
                        summaryColor = colors.summary,
                        onCopy = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            copyPlainText(context, "uid", displayUid, resources.getString(R.string.author_uid_copied))
                        },
                    )
                }

                AuthorAvatar(
                    avatarUrl = profile?.avatarUrl.orEmpty(),
                    size = 72.dp,
                    ringWidth = 3.5.dp,
                    ringColor = colors.background,
                    placeholderColor = colors.card,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced { coords ->
                        if (coords.isAttached) onNameRowPositioned(coords.positionInParent().y)
                    },
            ) {
                BasicText(
                    text = profile?.name ?: error ?: stringResource(R.string.search_loading),
                    style = TextStyle(
                        color = colors.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .graphicsLayer {
                            val p = titleProgress.coerceIn(0f, 1f)
                            translationY = -nameShiftPx * p
                            alpha = (1f - p * 0.7f).coerceIn(0.3f, 1f)
                        },
                )
                Spacer(Modifier.width(12.dp))
                AuthorFollowChip(
                    isFollowed = isFollowed,
                    onClick = onToggleFollow,
                    backdrop = backdrop,
                    dark = dark,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UidCopyChip(
                    uid = displayUid,
                    summaryColor = colors.summary,
                    onCopy = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copyPlainText(context, "uid", displayUid, resources.getString(R.string.author_uid_copied))
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced { coords ->
                        if (coords.isAttached) onNameRowPositioned(coords.positionInParent().y)
                    },
            ) {
                AuthorAvatar(
                    avatarUrl = profile?.avatarUrl.orEmpty(),
                    size = 64.dp,
                    ringWidth = 0.dp,
                    ringColor = colors.background,
                    placeholderColor = colors.card,
                )
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = profile?.name ?: error ?: stringResource(R.string.search_loading),
                    style = TextStyle(
                        color = colors.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val p = titleProgress.coerceIn(0f, 1f)
                            translationY = -nameShiftPx * p
                            alpha = (1f - p * 0.7f).coerceIn(0.3f, 1f)
                        },
                )
                Spacer(Modifier.width(10.dp))
                AuthorFollowChip(
                    isFollowed = isFollowed,
                    onClick = onToggleFollow,
                    backdrop = backdrop,
                    dark = dark,
                )
            }
        }

        if (profile != null && commentText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AuthorBioCard(
                comment = commentText,
                inlineLinks = inlineLinks,
                colors = colors,
                dark = dark,
                onLinkClick = onRequestExternalLink,
            )
        }
        if (profile != null && profileLinks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profileLinks.forEach { link ->
                    key(link.url) {
                        AuthorLinkChip(
                            link = link,
                            backdrop = backdrop,
                            dark = dark,
                            accent = colors.accent,
                            onClick = { onRequestExternalLink(link) },
                        )
                    }
                }
            }
        }
        if (profile != null) {
            Spacer(Modifier.height(12.dp))
            GlowStatCapsule(
                stats = listOf(
                    StatValue("${profile.following}", stringResource(R.string.author_following), onClick = onOpenFollowing),
                    StatValue("${profile.mypixivCount}", stringResource(R.string.author_mypixiv), onClick = onOpenMyPixiv),
                    StatValue(bookmarksCount?.toString() ?: "—", stringResource(R.string.author_bookmarks), onClick = onOpenBookmarks),
                ),
                backdrop = backdrop,
                dark = dark,
            )
        }
    }
}

/**
 * Author follow button: blue CTA when unfollowed, luminous white capsule when followed
 * (using [GlowSurface] white pill in dark mode too so the state contrast stays unmistakable).
 */
@Composable
private fun AuthorFollowChip(
    isFollowed: Boolean,
    onClick: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
) {
    val label = stringResource(if (isFollowed) R.string.author_followed else R.string.author_following)
    if (!isFollowed || !dark) {
        GlowChip(
            text = label,
            selected = !isFollowed,
            whiteGlass = isFollowed,
            onClick = onClick,
            backdrop = backdrop,
            dark = dark,
        )
    } else {
        val haptics = LocalHapticFeedback.current
        GlowSurface(
            backdrop = backdrop,
            dark = true,
            modifier = Modifier.height(32.dp),
            shape = CouiCapsuleShape,
            cornerWeight = 1f,
            preset = SpotlightPreset.ChipTab,
            colored = Color(0xEAEFF3F6),
            cancelLightOnScroll = true,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            foregroundComposition = true,
            staticSurface = true,
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = Color(0xFF14171A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

@Composable
private fun AuthorAvatar(
    avatarUrl: String,
    size: Dp,
    ringWidth: Dp,
    ringColor: Color,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (ringWidth > 0.dp) ringColor else placeholderColor)
            .then(if (ringWidth > 0.dp) Modifier.padding(ringWidth) else Modifier),
    ) {
        if (avatarUrl.isNotBlank()) {
            RetryableImage(
                url = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(placeholderColor),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(placeholderColor),
            )
        }
    }
}

@Composable
private fun UidCopyChip(
    uid: String,
    summaryColor: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "UID $uid",
        kind = CouiTextKind.ItemSummary,
        color = summaryColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(UidChipShape)
            .clickable(onClick = onCopy)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun AuthorBioCard(
    comment: String,
    inlineLinks: List<AuthorLink>,
    colors: CouiPageColors,
    dark: Boolean,
    onLinkClick: (AuthorLink) -> Unit,
) {
    val cardBackdrop = rememberCouiBackdrop()
    var expanded by remember(comment) { mutableStateOf(false) }
    var canExpand by remember(comment) { mutableStateOf(false) }

    val annotatedComment = remember(comment, inlineLinks, colors.accent) {
        buildLinkedBioAnnotatedString(
            text = comment,
            inlineLinks = inlineLinks,
            linkColor = colors.accent,
            onLinkClick = onLinkClick,
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(BioCardShape)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .couiBackdrop(cardBackdrop)
                .background(colors.card, BioCardShape),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            BasicText(
                text = annotatedComment,
                style = TextStyle(
                    color = colors.title.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Start,
                ),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = { result ->
                    if (!expanded) {
                        canExpand = result.hasVisualOverflow
                    }
                },
            )

            if (canExpand) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlowChip(
                        text = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand),
                        selected = true,
                        onClick = { expanded = !expanded },
                        backdrop = cardBackdrop,
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorLinkChip(
    link: AuthorLink,
    backdrop: CouiBackdrop,
    dark: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val chipColor = platformChipColor(link.platform, dark = dark, fallbackAccent = accent)
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = Modifier.height(34.dp),
        shape = CouiCapsuleShape,
        cornerWeight = 1f,
        preset = SpotlightPreset.ChipTab,
        colored = chipColor,
        cancelLightOnScroll = true,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        foregroundComposition = true,
        staticSurface = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ExternalLinkGlyph(Color.White)
            BasicText(
                text = link.label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

@Composable
private fun ExternalLinkGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val strokeWidth = 1.6.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.45f, h * 0.22f)
            lineTo(w * 0.18f, h * 0.22f)
            lineTo(w * 0.18f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.84f)
            lineTo(w * 0.78f, h * 0.55f)
        }
        drawPath(path, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(w * 0.48f, h * 0.52f),
            end = Offset(w * 0.84f, h * 0.16f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        val arrowHead = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.58f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.42f)
        }
        drawPath(arrowHead, color = tint, style = stroke)
    }
}
