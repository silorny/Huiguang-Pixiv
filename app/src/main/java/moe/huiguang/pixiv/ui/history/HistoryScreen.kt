package moe.huiguang.pixiv.ui.history

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
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
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.SwipeDeleteRow
import moe.huiguang.pixiv.design.components.SwipeRevealController
import moe.huiguang.pixiv.design.components.WorkRowBody
import moe.huiguang.pixiv.design.components.dismissSwipeOnOutsideTap
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.workMeta

/**
 * On-device lookup history, newest first, filtered by work type across a
 * persistent 4-page [HorizontalPager] so switching filters never recreates
 * or reloads list rows or thumbnails.
 */
@Composable
fun HistoryScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val effectBackdrop = rememberCouiBackdrop()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val scope = rememberCoroutineScope()
    val state by settings.state.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var savedFilter by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedFilter, pageCount = { 4 })
    val filter by remember { derivedStateOf { pagerState.currentPage } }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { savedFilter = it }
    }

    val swipeController = remember { SwipeRevealController() }
    val listStates = List(4) { index ->
        rememberSaveable(index, saver = LazyListState.Saver) { LazyListState() }
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(filter) { swipeController.close() }
    val activeListState = listStates[filter]
    LaunchedEffect(activeListState.isScrollInProgress) {
        if (activeListState.isScrollInProgress) swipeController.close()
    }

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
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(statusTop + 56.dp + 8.dp))
                SegmentedControl(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    backdrop = effectBackdrop,
                    labels = listOf(
                        stringResource(R.string.filter_all),
                        stringResource(R.string.filter_illust),
                        stringResource(R.string.filter_manga),
                        stringResource(R.string.filter_novel),
                    ),
                    selectedIndex = filter,
                    onSelected = { target ->
                        scope.launch { pagerState.animateScrollToPage(target) }
                    },
                )
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { pageFilter ->
                    val overscroll = rememberCouiOverscrollEffect()
                    val pageListState = listStates[pageFilter]
                    val entries = remember(state.history, pageFilter) {
                        state.history.filter { matchesHistoryFilter(it, pageFilter) }
                    }
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overscroll(overscroll),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = navBottom + 24.dp,
                        ),
                    ) {
                        if (entries.isEmpty()) {
                            item(key = "empty-$pageFilter") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 72.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.history_empty),
                                        kind = CouiTextKind.ItemSummary,
                                        color = colors.summary,
                                    )
                                }
                            }
                        } else {
                            items(
                                items = entries,
                                key = { "${it.kind}-${it.pid}" },
                                contentType = { "historyRow" },
                            ) { entry ->
                                val rowKey = "${entry.kind}-${entry.pid}"
                                SwipeDeleteRow(
                                    rowKey = rowKey,
                                    controller = swipeController,
                                    backdrop = effectBackdrop,
                                    onClick = { onOpen(entry) },
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
                            item(key = "bottom-spacer-$pageFilter") { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }

        FloatingSecondaryTopBar(
            title = stringResource(R.string.history_title),
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
                            CouiMenuItem(
                                value = 4,
                                text = stringResource(R.string.search_menu_clear_history),
                                dividerBefore = true,
                                enabled = state.history.isNotEmpty(),
                            ),
                        ),
                        onItemClick = { item ->
                            menuExpanded = false
                            if (item.value in 0..3) {
                                scope.launch { pagerState.animateScrollToPage(item.value) }
                            } else if (item.value == 4) {
                                confirmClear = true
                            }
                        },
                    )
                }
            },
        )
    }

    Dialog(
        visible = confirmClear,
        onDismissRequest = { confirmClear = false },
        title = stringResource(R.string.history_clear),
        message = stringResource(R.string.history_clear_message),
        material = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.history_clear),
            onClick = {
                settings.clearHistory()
                confirmClear = false
            },
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiDialogDefaults.colors(dark).destructive),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { confirmClear = false },
        ),
    )
}

/** 0 = all, 1 = illustration, 2 = manga, 3 = novel. Authors only show under "all". */
internal fun matchesHistoryFilter(entry: HistoryEntry, filter: Int): Boolean = when (filter) {
    1 -> entry.kind == HistoryEntry.KIND_ILLUST && entry.illustType != 1
    2 -> entry.kind == HistoryEntry.KIND_ILLUST && entry.illustType == 1
    3 -> entry.kind == HistoryEntry.KIND_NOVEL
    else -> true
}
