package moe.huiguang.pixiv.ui.search

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.FloatingTabHeader
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.SearchPill
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
    private val filterState: MutableIntState,
    private val confirmClearState: MutableState<Boolean>,
) {
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
    val filterState = rememberSaveable { mutableIntStateOf(0) }
    val confirmClearState = remember { mutableStateOf(false) }
    return remember(scrollState) {
        SearchTabState(
            scrollState = scrollState,
            filterState = filterState,
            confirmClearState = confirmClearState,
        )
    }
}

@Composable
fun SearchTopChrome(
    tabState: SearchTabState,
    hasHistory: Boolean,
    backdrop: CouiBackdrop,
    statusTop: Dp,
) {
    val colors = CouiPageDefaults.themeColors()
    val effectDark = isDarkBackdrop(colors.background)
    var menuExpanded by remember { mutableStateOf(false) }
    val collapsed by remember(tabState.scrollState) {
        derivedStateOf {
            tabState.scrollState.firstVisibleItemIndex > 0 || tabState.scrollState.firstVisibleItemScrollOffset > 24
        }
    }
    FloatingTabHeader(
        title = stringResource(R.string.tab_search),
        backdrop = backdrop,
        collapsed = collapsed,
        statusTop = statusTop,
        actions = {
            Spacer(Modifier.weight(1f))
            Box {
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
        },
    )
}

/**
 * The search tab: floating header, search pill, keyword/tag switch, a type
 * filter and the on-device history rendered with the same row as "我的".
 */
@Composable
fun SearchTab(
    padding: PaddingValues,
    settings: SettingsStore,
    tabState: SearchTabState = rememberSearchTabState(),
    effectBackdrop: CouiBackdrop,
    scrollToTopTick: Int = 0,
    onSubmit: (String, SearchMode) -> Unit,
    onOpenHistory: (HistoryEntry) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val effectDark = isDarkBackdrop(colors.background)
    val state by settings.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var modeKey by rememberSaveable { mutableStateOf<Any>("keyword") }
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

    val mode = if (modeKey == "tag") SearchMode.TAG else SearchMode.KEYWORD
    val history = remember(state.history, filter) {
        state.history.filter { matchesHistoryFilter(it, filter) }
    }

    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        onSubmit(text, mode)
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
                .overscroll(overscroll),
            contentPadding = PaddingValues(bottom = navBottom + 96.dp),
        ) {
            item(key = "top") { Spacer(Modifier.height(statusTop + 64.dp)) }

            item(key = "search") {
                SearchPill(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { submit() },
                    placeholder = stringResource(R.string.search_placeholder),
                    clearDescription = stringResource(R.string.search_clear),
                    backdrop = effectBackdrop,
                    dark = effectDark,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "modes") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlowChip(
                        text = stringResource(R.string.search_mode_keyword),
                        onClick = { modeKey = "keyword" },
                        selected = modeKey == "keyword",
                        backdrop = effectBackdrop,
                        dark = effectDark,
                    )
                    GlowChip(
                        text = stringResource(R.string.search_mode_tag),
                        onClick = { modeKey = "tag" },
                        selected = modeKey == "tag",
                        backdrop = effectBackdrop,
                        dark = effectDark,
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                item(key = "history-label") {
                    Text(
                        text = stringResource(R.string.search_history_label),
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 4.dp),
                    )
                }
                item(key = "filter") {
                    SegmentedControl(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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

            if (history.isEmpty()) {
                item(key = "empty") {
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
            } else {
                items(
                    items = history,
                    key = { "${it.kind}-${it.pid}" },
                    contentType = { "historyRow" },
                ) { entry ->
                    val rowKey = "${entry.kind}-${entry.pid}"
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
