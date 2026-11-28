package moe.huiguang.pixiv.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.drawCouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.search.CouiSearchBarDefaults
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarSurface
import kotlin.math.roundToInt
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.AdaptiveTokens
import moe.huiguang.pixiv.design.CenteredReadableBox
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.SwipeDeleteRow
import moe.huiguang.pixiv.design.components.SwipeRevealController
import moe.huiguang.pixiv.design.components.WorkRowBody
import moe.huiguang.pixiv.design.components.dismissSwipeOnOutsideTap
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.ui.common.workMeta
import moe.huiguang.pixiv.ui.history.matchesHistoryFilter

@Stable
class SearchTabState internal constructor(
    val scrollState: LazyListState,
    private val queryState: MutableState<String>,
    private val searchingState: MutableState<Boolean>,
    private val modeKeyState: MutableState<String>,
    private val filterState: MutableIntState,
    private val confirmClearState: MutableState<Boolean>,
) {
    var query: String
        get() = queryState.value
        set(value) { queryState.value = value }
    var searching: Boolean
        get() = searchingState.value
        set(value) { searchingState.value = value }
    var modeKey: String
        get() = modeKeyState.value
        set(value) { modeKeyState.value = value }
    var filter: Int
        get() = filterState.intValue
        set(value) { filterState.intValue = value }
    var confirmClear: Boolean
        get() = confirmClearState.value
        set(value) { confirmClearState.value = value }
}

@Composable
fun rememberSearchTabState(): SearchTabState {
    val scrollState = rememberLazyListState()
    val queryState = rememberSaveable { mutableStateOf("") }
    val searchingState = rememberSaveable { mutableStateOf(false) }
    val modeKeyState = rememberSaveable { mutableStateOf("keyword") }
    val filterState = rememberSaveable { mutableIntStateOf(0) }
    val confirmClearState = remember { mutableStateOf(false) }
    return remember(scrollState) {
        SearchTabState(
            scrollState = scrollState,
            queryState = queryState,
            searchingState = searchingState,
            modeKeyState = modeKeyState,
            filterState = filterState,
            confirmClearState = confirmClearState,
        )
    }
}

/**
 * Search overlay chrome: uses the exact Home top-row title geometry (`30.sp` -> `20.sp`
 * GPU scale inside `64.dp`, `20.dp` horizontal padding), keeps the right `(⋮)` action button
 * pinned at top on scroll while shrinking the docked [SearchBar] horizontally to make room for it,
 * and smoothly expands [SearchBar] to full width with COUI's sticky cancel capsule when searching.
 */
@Composable
fun SearchTopChrome(
    tabState: SearchTabState,
    hasHistory: Boolean,
    backdrop: CouiBackdrop,
    statusTop: Dp,
    onSubmit: (String, SearchMode) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val effectDark = isDarkBackdrop(colors.background)
    val density = LocalDensity.current
    var menuExpanded by remember { mutableStateOf(false) }
    val scrollState = tabState.scrollState
    val collapsed by remember(scrollState) {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 8 }
    }
    val scrollProgress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "searchScrollCollapse",
    )
    val searchActiveProgress by animateFloatAsState(
        targetValue = if (tabState.searching) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "searchActiveState",
    )
    val search = rememberCouiSearchBarState(tabState.searching)
    val mode = if (tabState.modeKey == "tag") SearchMode.TAG else SearchMode.KEYWORD

    val riseProgress = maxOf(scrollProgress, searchActiveProgress).coerceIn(0f, 1f)
    val shrinkProgress = (scrollProgress * (1f - searchActiveProgress)).coerceIn(0f, 1f)
    val titleRowHeightDp = statusTop + 64.dp
    val searchBaseTopDp = titleRowHeightDp + 6.dp
    val searchShiftYDp = 6.dp - (64.dp + 6.dp)
    val expandedTotalDp = searchBaseTopDp + CouiSearchBarDefaults.height
    val collapsedTotalDp = titleRowHeightDp
    val collapseSpanDp = (expandedTotalDp - collapsedTotalDp).coerceAtLeast(0.dp)
    val searchBaseTopPx = with(density) { searchBaseTopDp.roundToPx() }
    val searchShiftYPx = with(density) { searchShiftYDp.toPx() }
    // Right More button sits at end = 20.dp (40.dp wide -> left edge at end = 60.dp).
    // SearchBar has built-in 16.dp horizontal margin, so +52.dp leaves an 8.dp gap beside More.
    val collapsedEndReservePx = with(density) { 52.dp.roundToPx() }
    val searchMaxWidthPx = with(density) { AdaptiveTokens.SearchBarMaxWidth.roundToPx() }

    val baseTitleStyle = remember(colors.title) {
        TextStyle(
            color = colors.title,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    // coui-0.0.1-alpha15's SearchBar passes blurRadius = 0f in systemMaterial(), so supply a
    // pre-blurred backdrop matching COUI's 14.dp frosted glass blur for both the SearchBar and
    // its Cancel capsule, while keeping SearchBar outside TopAppBarSurface's LocalCouiToolbarBackdrop.
    val searchBlurPx = with(density) { 14.dp.toPx() }
    val searchBackdrop = rememberCouiBackdrop()

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0.001f }
                .couiBackdrop(searchBackdrop)
                .drawCouiBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(searchBlurPx, TileMode.Mirror) },
                ),
        )

        TopAppBarSurface(
            modifier = Modifier.fillMaxWidth(),
            backdrop = backdrop,
            fadeHeight = 112.dp,
            referenceBlurRadius = 16.dp,
            bottomExtension = 40.dp,
            backgroundAlpha = riseProgress,
            gradientColor = colors.background,
            dark = dark,
            topExtension = { collapseSpanDp * riseProgress },
        ) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(collapsedTotalDp + collapseSpanDp * (1f - riseProgress)),
            )
        }

        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = statusTop)
                        .height(64.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = stringResource(R.string.tab_search),
                        style = baseTitleStyle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            val s = 1f + (20f / 30f - 1f) * riseProgress
                            scaleX = s
                            scaleY = s
                            alpha = (1f - riseProgress * 1.45f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    val actionOutsets = remember { LayerOutsets(all = 48.dp) }
                    val actionAlpha = (1f - searchActiveProgress * 3.5f).coerceIn(0f, 1f)
                    val actionScale = 0.85f + 0.15f * actionAlpha
                    if (actionAlpha > 0.01f) {
                        Box(
                            Modifier
                                .graphicsLayer {
                                    alpha = actionAlpha
                                    scaleX = actionScale
                                    scaleY = actionScale
                                    outsets = actionOutsets
                                }
                                .then(if (tabState.searching) Modifier.clearAndSetSemantics {} else Modifier),
                        ) {
                            CircleIconButton(
                                icon = CouiIcons.Filled.More,
                                contentDescription = stringResource(R.string.search_filter_menu),
                                onClick = { menuExpanded = true },
                                backdrop = backdrop,
                                dark = effectDark,
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
                                        selected = tabState.filter == 0,
                                    ),
                                    CouiMenuItem(
                                        value = 1,
                                        text = stringResource(R.string.filter_illust),
                                        selected = tabState.filter == 1,
                                    ),
                                    CouiMenuItem(
                                        value = 2,
                                        text = stringResource(R.string.filter_manga),
                                        selected = tabState.filter == 2,
                                    ),
                                    CouiMenuItem(
                                        value = 3,
                                        text = stringResource(R.string.filter_novel),
                                        selected = tabState.filter == 3,
                                    ),
                                    CouiMenuItem(
                                        value = 4,
                                        text = stringResource(R.string.search_menu_clear_history),
                                        dividerBefore = true,
                                        enabled = hasHistory,
                                    ),
                                ),
                                onItemClick = { item ->
                                    menuExpanded = false
                                    if (item.value in 0..3) {
                                        tabState.filter = item.value
                                    } else if (item.value == 4) {
                                        tabState.confirmClear = true
                                    }
                                },
                            )
                        }
                    }
                }

                SearchBar(
                    state = search,
                    query = tabState.query,
                    onQueryChange = { tabState.query = it },
                    onExpandedChange = { expanded ->
                        tabState.searching = expanded
                        if (!expanded) tabState.query = ""
                    },
                    onSearch = {
                        val text = tabState.query.trim()
                        if (text.isNotEmpty()) {
                            onSubmit(text, mode)
                        }
                    },
                    placeholder = stringResource(R.string.search_placeholder),
                    cancelText = stringResource(R.string.action_cancel),
                    clearDescription = stringResource(R.string.search_clear),
                    backdrop = searchBackdrop,
                    dark = dark,
                    shadowProgress = 1f,
                )
            },
        ) { measurables, constraints ->
            val headerPlaceable = measurables[0].measure(
                constraints.copy(minHeight = 0),
            )
            val endReserve = (collapsedEndReservePx * shrinkProgress).roundToInt()
            val availableW = (constraints.maxWidth - endReserve).coerceAtLeast(0)
            val searchW = availableW.coerceAtMost(searchMaxWidthPx)
            val searchX = (availableW - searchW) / 2
            val searchPlaceable = measurables[1].measure(
                Constraints.fixedWidth(searchW),
            )
            val expandedH = searchBaseTopPx + searchPlaceable.height
            val searchY = (searchBaseTopPx + searchShiftYPx * riseProgress).roundToInt()
            val collapsedH = maxOf(headerPlaceable.height, searchY + searchPlaceable.height)
            val totalH = (expandedH + (collapsedH - expandedH) * riseProgress).roundToInt()
                .coerceAtLeast(headerPlaceable.height)
            layout(constraints.maxWidth, totalH) {
                headerPlaceable.placeRelative(0, 0)
                searchPlaceable.placeRelative(searchX, searchY)
            }
        }
    }
}

/**
 * The search tab: docked COUI search pill with sticky cancel capsule,
 * keyword/tag switch, history type filter, and on-device history list.
 */
@Composable
fun SearchTab(
    padding: PaddingValues,
    settings: SettingsStore,
    tabState: SearchTabState = rememberSearchTabState(),
    effectBackdrop: CouiBackdrop,
    scrollToTopTick: Int = 0,
    onOpenHistory: (HistoryEntry) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val effectDark = isDarkBackdrop(colors.background)
    val state by settings.state.collectAsState()
    val filter = tabState.filter
    val swipeController = remember { SwipeRevealController() }
    val scrollState = tabState.scrollState
    val overscroll = com.itos.coui.scroll.rememberCouiOverscrollEffect()

    LaunchedEffect(filter) { swipeController.close() }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) swipeController.close()
    }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) scrollState.animateScrollToItem(0)
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val activeTopMin = statusTop + 64.dp
    val defaultTop = statusTop + 64.dp + 6.dp + CouiSearchBarDefaults.height + 4.dp
    val activeProgress by animateFloatAsState(
        targetValue = if (tabState.searching) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
        label = "searchListTop",
    )
    val listTopPadding = defaultTop - (defaultTop - activeTopMin) * activeProgress
    val imePadding = if (LocalWindowInfo.current.isWindowFocused) Modifier.imePadding() else Modifier

    val history = remember(state.history, filter) {
        state.history.filter { matchesHistoryFilter(it, filter) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(padding)
            .dismissSwipeOnOutsideTap(swipeController),
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .then(imePadding)
                .overscroll(overscroll),
            contentPadding = PaddingValues(
                top = listTopPadding,
                bottom = navBottom + 96.dp,
            ),
        ) {
            item(key = "modes") {
                CenteredReadableBox {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlowChip(
                            text = stringResource(R.string.search_mode_keyword),
                            onClick = { tabState.modeKey = "keyword" },
                            selected = tabState.modeKey == "keyword",
                            backdrop = effectBackdrop,
                            dark = effectDark,
                        )
                        GlowChip(
                            text = stringResource(R.string.search_mode_tag),
                            onClick = { tabState.modeKey = "tag" },
                            selected = tabState.modeKey == "tag",
                            backdrop = effectBackdrop,
                            dark = effectDark,
                        )
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item(key = "history-label") {
                    CenteredReadableBox {
                        Text(
                            text = stringResource(R.string.search_history_label),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 4.dp),
                        )
                    }
                }
                item(key = "filter") {
                    CenteredReadableBox {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            SegmentedControl(
                                modifier = Modifier
                                    .widthIn(max = AdaptiveTokens.SwitcherMaxWidth)
                                    .fillMaxWidth(),
                                labels = listOf(
                                    stringResource(R.string.filter_all),
                                    stringResource(R.string.filter_illust),
                                    stringResource(R.string.filter_manga),
                                    stringResource(R.string.filter_novel),
                                ),
                                selectedIndex = filter,
                                onSelected = { tabState.filter = it },
                            )
                        }
                    }
                }
            }

            if (history.isEmpty()) {
                item(key = "empty") {
                    CenteredReadableBox {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.history.isEmpty()) R.string.search_tip else R.string.history_empty,
                                ),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = history,
                    key = { "${it.kind}-${it.pid}" },
                    contentType = { "historyRow" },
                ) { entry ->
                    val rowKey = "${entry.kind}-${entry.pid}"
                    CenteredReadableBox {
                        SwipeDeleteRow(
                            rowKey = rowKey,
                            controller = swipeController,
                            backdrop = effectBackdrop,
                            onClick = { onOpenHistory(entry) },
                            onDelete = { settings.removeHistory(entry.pid, entry.kind) },
                        ) {
                            WorkRowBody(
                                thumbUrl = entry.thumbUrl,
                                title = entry.title,
                                author = if (entry.kind == HistoryEntry.KIND_AUTHOR) "" else entry.userName,
                                meta = workMeta(entry.kind, entry.illustType, entry.pid),
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    Dialog(
        visible = tabState.confirmClear,
        onDismissRequest = { tabState.confirmClear = false },
        title = stringResource(R.string.history_clear),
        message = stringResource(R.string.history_clear_message),
        material = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.history_clear),
            onClick = {
                settings.clearHistory()
                tabState.confirmClear = false
            },
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiDialogDefaults.colors(dark).destructive),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { tabState.confirmClear = false },
        ),
    )
}
