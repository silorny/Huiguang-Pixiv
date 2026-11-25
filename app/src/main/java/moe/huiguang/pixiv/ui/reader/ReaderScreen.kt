@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package moe.huiguang.pixiv.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.couiToolbarFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.sheet.BottomSheet
import com.itos.coui.sheet.CouiSheetButton
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.slider.CouiSliderDefaults
import com.itos.coui.slider.Slider
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.topappbar.TopAppBarSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.model.IllustPage
import moe.huiguang.pixiv.data.model.NovelBlock
import moe.huiguang.pixiv.data.model.NovelBlocks
import moe.huiguang.pixiv.data.model.NovelImage
import moe.huiguang.pixiv.data.model.ReadingMark
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.PaperTheme
import moe.huiguang.pixiv.data.store.ReaderStyle
import moe.huiguang.pixiv.data.store.ReadingStore
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.SecondaryButton
import moe.huiguang.pixiv.design.components.SettingSwitchRow
import moe.huiguang.pixiv.design.shape.CachedCouiShape
import moe.huiguang.pixiv.ui.novel.NovelCache
import moe.huiguang.pixiv.ui.viewer.ImageViewerContent
import kotlin.math.abs
import kotlin.math.roundToInt

private val RailWidth = 42.dp
private val RailHeight = 440.dp
private val RailHairlineHeight = 360.dp
private val RailInset = 14.dp
private val SnapEnter = 20.dp
private val SnapExit = 34.dp
private const val RailHideDelayMs = 1800L

private data class PaperPalette(
    val background: Color,
    val ink: Color,
    val subtle: Color,
    val dark: Boolean,
)

@Composable
private fun paperPalette(theme: PaperTheme, appDark: Boolean): PaperPalette = when (theme) {
    PaperTheme.IVORY -> PaperPalette(
        background = Color(0xFFF7F2E7),
        ink = Color(0xFF2A251D),
        subtle = Color(0x992A251D),
        dark = false,
    )

    PaperTheme.SAGE -> PaperPalette(
        background = Color(0xFFE7F0E6),
        ink = Color(0xFF1F2D21),
        subtle = Color(0x991F2D21),
        dark = false,
    )

    PaperTheme.DARK -> PaperPalette(
        background = Color(0xFF0B0B0C),
        ink = Color(0xFFE8E6E1),
        subtle = Color(0x99E8E6E1),
        dark = true,
    )

    PaperTheme.SYSTEM -> if (appDark) {
        PaperPalette(
            background = Color(0xFF0B0B0C),
            ink = Color(0xFFE8E6E1),
            subtle = Color(0x99E8E6E1),
            dark = true,
        )
    } else {
        PaperPalette(
            background = Color(0xFFF7F5F0),
            ink = Color(0xFF232323),
            subtle = Color(0x99232323),
            dark = false,
        )
    }
}

/**
 * Immersive novel reader:
 * - Paper theme & typography persisted in [SettingsStore]
 * - Screen kept awake while reading
 * - Pixiv markup (`[chapter:]`, `[newpage]`, `[uploadedimage:]`, `[pixivimage:]`, `[[rb:]]`)
 * - Floating COUI [GlowSurface] chrome + interactive progress rail with illustration
 *   anchor snapping and haptic ticks
 * - Series chapter navigation & table of contents
 */
@Composable
fun ReaderScreen(
    novelId: String,
    api: PixivApi,
    cache: NovelCache,
    settings: SettingsStore,
    reading: ReadingStore,
    downloads: ImageDownloader,
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit = {},
    onOpenSeries: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val appDark = CouiTheme.isDark
    val windowBackdrop = rememberCouiWindowBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val state by settings.state.collectAsState()
    val style = state.reader
    val palette = paperPalette(style.paperTheme, appDark)
    val accent = CouiTheme.colorScheme.accent

    // Keep screen on and ensure status bar icons match the active paper canvas
    // (SideEffect overrides HuiguangActivity's theme-level status bar pass).
    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !palette.dark
    }
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = controller?.isAppearanceLightStatusBars
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (prevLightStatus != null) controller.isAppearanceLightStatusBars = prevLightStatus
        }
    }

    var novel by remember(novelId) { mutableStateOf(cache.get(novelId)) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(novelId, reloadKey) {
        if (novel != null && reloadKey == 0) return@LaunchedEffect
        error = null
        runCatching { api.novel(novelId) ?: error(resources.getString(R.string.error_novel_not_found)) }
            .onSuccess {
                cache.put(novelId, it)
                novel = it
            }
            .onFailure { error = it.message ?: resources.getString(R.string.error_load_failed) }
    }

    val work = novel
    if (work == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            if (error != null) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = error!!, kind = CouiTextKind.ItemSummary, color = palette.subtle)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(text = stringResource(R.string.action_retry), onClick = { reloadKey++ })
                }
            } else {
                Text(
                    text = stringResource(R.string.search_loading),
                    kind = CouiTextKind.ItemSummary,
                    color = palette.subtle,
                )
            }
        }
        return
    }

    val blocks = remember(work.id, work.content, work.embeddedImages) {
        NovelBlocks.build(work.content, work.embeddedImages)
    }
    val embeddedImages = remember(blocks) {
        blocks.mapIndexedNotNull { idx, b -> (b as? NovelBlock.Image)?.image?.let { idx to it } }
    }
    val viewerPages = remember(embeddedImages) {
        embeddedImages.map { IllustPage(it.second.displayUrl, it.second.originalUrl, 0, 0) }
    }
    val imageBlocks = remember(embeddedImages) { embeddedImages.map { it.first } }
    val imageFractions = remember(imageBlocks, blocks.size) {
        if (blocks.size <= 1) emptyList() else imageBlocks.map { it.toFloat() / (blocks.size - 1) }
    }
    val chapters = remember(blocks) {
        blocks.withIndex().mapNotNull { (idx, b) ->
            (b as? NovelBlock.Chapter)?.let { idx to it.title }
        }
    }

    val listState = rememberLazyListState()
    var chrome by remember { mutableStateOf(true) }
    val showStyle = remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var previewImageIndex by remember { mutableStateOf<Int?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var bookmarked by remember(work.id) {
        mutableStateOf(reading.mark(work.id)?.bookmarked ?: false)
    }
    var highlightedBlock by remember { mutableStateOf<Int?>(null) }
    var restored by remember(work.id) { mutableStateOf(false) }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val series = work.series
    val prevEp = series?.prev?.takeIf { it.available }
    val nextEp = series?.next?.takeIf { it.available }

    val paged = style.paged
    var pagedContentSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val bodyStyle = remember(style, palette.ink) {
        TextStyle(
            color = palette.ink,
            fontSize = style.fontSize.sp,
            fontWeight = FontWeight(style.weight),
            lineHeight = (style.fontSize * style.lineSpacing).sp,
            textAlign = TextAlign.Start,
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        )
    }
    val untitledTitle = stringResource(R.string.untitled_work)
    val readerPages = remember(blocks, work.title, untitledTitle, work.userName, style, pagedContentSize, paged, series != null) {
        if (!paged || blocks.isEmpty() || pagedContentSize.width <= 0 || pagedContentSize.height <= 0) {
            emptyList()
        } else {
            paginateNovelBlocks(
                blocks = blocks,
                title = work.title.ifBlank { untitledTitle },
                author = work.userName,
                fontSize = style.fontSize,
                indent = style.indent,
                bodyStyle = bodyStyle,
                hasSeriesFooter = series != null,
                widthPx = pagedContentSize.width,
                heightPx = pagedContentSize.height,
                density = density,
                measurer = textMeasurer,
            )
        }
    }
    val pagerState = rememberPagerState(pageCount = { readerPages.size.coerceAtLeast(1) })

    fun openImagePreview(blockIndex: Int) {
        val idx = embeddedImages.indexOfFirst { it.first == blockIndex }
        if (idx >= 0) previewImageIndex = idx
    }

    // Live sub-item progress read inside the rail's draw/text leaves so scroll
    // frames never trigger a full ReaderScreen recomposition.
    val progressState = remember(blocks.size, paged, readerPages.size) {
        derivedStateOf {
            if (paged) {
                val totalPages = readerPages.size
                if (totalPages <= 1) 0f else (pagerState.currentPage.toFloat() / (totalPages - 1)).coerceIn(0f, 1f)
            } else {
                val total = blocks.size
                if (total <= 1) {
                    0f
                } else {
                    val index = listState.firstVisibleItemIndex
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val inner = if (item != null && item.size > 0) {
                        listState.firstVisibleItemScrollOffset.toFloat() / item.size
                    } else {
                        0f
                    }
                    ((index + inner) / (total - 1)).coerceIn(0f, 1f)
                }
            }
        }
    }

    var railDragging by remember { mutableStateOf(false) }
    var railExpanded by remember { mutableStateOf(false) }
    var railTouchToken by remember { mutableLongStateOf(0L) }

    LaunchedEffect(railExpanded, railDragging, railTouchToken) {
        if (!railExpanded || railDragging) return@LaunchedEffect
        delay(RailHideDelayMs)
        railExpanded = false
    }

    LaunchedEffect(work.id, blocks.size, paged, readerPages.size) {
        if (restored || blocks.isEmpty()) return@LaunchedEffect
        val saved = reading.mark(work.id)
        if (saved != null && saved.blockIndex in blocks.indices && saved.blockIndex > 0) {
            if (paged) {
                if (readerPages.isEmpty()) return@LaunchedEffect
                val targetPage = readerPages.indexOfLast { it.firstBlockIndex <= saved.blockIndex }.coerceAtLeast(0)
                pagerState.scrollToPage(targetPage)
            } else {
                listState.scrollToItem(saved.blockIndex)
            }
            val pct = (saved.fraction * 100).roundToInt()
            Toast.makeText(
                context,
                resources.getString(R.string.reader_restored, pct),
                Toast.LENGTH_SHORT,
            ).show()
        }
        restored = true
    }

    // Auto-save debounced so scrolling/paging does not hammer SharedPreferences.
    LaunchedEffect(work.id, blocks.size, paged, readerPages.size) {
        snapshotFlow {
            if (paged) {
                readerPages.getOrNull(pagerState.currentPage)?.firstBlockIndex ?: 0
            } else {
                listState.firstVisibleItemIndex
            }
        }
            .debounce(700)
            .distinctUntilChanged()
            .collect { index ->
                reading.saveAuto(
                    ReadingMark(
                        novelId = work.id,
                        title = work.title,
                        blockIndex = index,
                        totalBlocks = blocks.size,
                        updatedAt = System.currentTimeMillis(),
                        bookmarked = bookmarked,
                    ),
                )
            }
    }

    var lastSeek by remember { mutableIntStateOf(-1) }

    fun seekTo(fraction: Float, snappedBlock: Int?) {
        highlightedBlock = snappedBlock
        if (paged && readerPages.isNotEmpty()) {
            val targetPage = if (snappedBlock != null) {
                readerPages.indexOfLast { it.firstBlockIndex <= snappedBlock }.coerceAtLeast(0)
            } else {
                (fraction * (readerPages.size - 1)).roundToInt().coerceIn(0, readerPages.lastIndex)
            }
            if (targetPage != lastSeek) {
                lastSeek = targetPage
                scope.launch { pagerState.scrollToPage(targetPage) }
            }
            return
        }
        if (blocks.size <= 1) return
        val target = snappedBlock ?: (fraction * (blocks.size - 1)).roundToInt().coerceIn(0, blocks.lastIndex)
        if (target != lastSeek) {
            lastSeek = target
            scope.launch { listState.scrollToItem(target) }
        }
    }

    fun jumpToBlock(blockIndex: Int) {
        if (paged && readerPages.isNotEmpty()) {
            val targetPage = readerPages.indexOfLast { it.firstBlockIndex <= blockIndex }.coerceAtLeast(0)
            scope.launch { pagerState.scrollToPage(targetPage) }
        } else {
            scope.launch { listState.scrollToItem(blockIndex) }
        }
    }

    fun jumpImage(forward: Boolean) {
        if (imageBlocks.isEmpty()) return
        val here = if (paged) {
            readerPages.getOrNull(pagerState.currentPage)?.firstBlockIndex ?: 0
        } else {
            listState.firstVisibleItemIndex
        }
        val target = if (forward) {
            imageBlocks.firstOrNull { it > here } ?: imageBlocks.last()
        } else {
            imageBlocks.lastOrNull { it < here } ?: imageBlocks.first()
        }
        highlightedBlock = target
        jumpToBlock(target)
    }

    fun togglePagedMode() {
        val currentBlock = if (paged) {
            readerPages.getOrNull(pagerState.currentPage)?.firstBlockIndex ?: 0
        } else {
            listState.firstVisibleItemIndex
        }
        val nextPaged = !paged
        settings.setReaderPaged(nextPaged)
        if (!nextPaged) {
            scope.launch { listState.scrollToItem(currentBlock) }
        }
    }

    val contentBackdrop = rememberCouiBackdrop()
    val effectBackdrop = rememberCouiBackdrop()

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        // Opaque backdrop source wrapping the paper background + novel content so
        // floating COUI top/bottom bars and the progress slider blur real text behind them.
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(contentBackdrop)
                .background(palette.background),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .couiBackdrop(effectBackdrop)
                    .background(palette.background),
            )

            if (blocks.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { chrome = !chrome }
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (work.isLoginOnly) R.string.reader_login_only else R.string.reader_no_content,
                        ),
                        kind = CouiTextKind.ItemSummary,
                        color = palette.subtle,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (paged) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = 22.dp,
                            end = 28.dp,
                            top = statusTop + 68.dp,
                            bottom = navBottom + 74.dp,
                        ),
                ) {
                    val widthPx = with(density) { maxWidth.roundToPx() }
                    val heightPx = with(density) { maxHeight.roundToPx() }
                    LaunchedEffect(widthPx, heightPx) {
                        pagedContentSize = androidx.compose.ui.unit.IntSize(widthPx, heightPx)
                    }
                    if (readerPages.isNotEmpty()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { pageIdx ->
                            val page = readerPages[pageIdx]
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(pageIdx, readerPages.size) {
                                        detectTapGestures { offset ->
                                            val w = size.width.toFloat()
                                            when {
                                                offset.x < w * 0.24f && pageIdx > 0 ->
                                                    scope.launch { pagerState.animateScrollToPage(pageIdx - 1) }
                                                offset.x > w * 0.76f && pageIdx < readerPages.lastIndex ->
                                                    scope.launch { pagerState.animateScrollToPage(pageIdx + 1) }
                                                else -> chrome = !chrome
                                            }
                                        }
                                    },
                            ) {
                                SelectionContainer {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        page.slices.forEach { slice ->
                                            when (slice) {
                                                is PageSlice.Heading -> Column(Modifier.padding(bottom = 10.dp)) {
                                                    BasicText(
                                                        text = slice.title.ifBlank { stringResource(R.string.untitled_work) },
                                                        style = TextStyle(
                                                            color = palette.ink,
                                                            fontSize = (style.fontSize + 6).sp,
                                                            fontWeight = FontWeight.Bold,
                                                            lineHeight = (style.fontSize + 14).sp,
                                                        ),
                                                    )
                                                    Spacer(Modifier.height(6.dp))
                                                    BasicText(
                                                        text = slice.author,
                                                        style = TextStyle(color = palette.subtle, fontSize = 13.sp),
                                                    )
                                                }

                                                is PageSlice.Chapter -> ChapterHeading(
                                                    title = slice.title,
                                                    fontSize = style.fontSize,
                                                    ink = palette.ink,
                                                    accent = accent,
                                                )

                                                is PageSlice.Paragraph -> BasicText(
                                                    text = slice.text,
                                                    style = bodyStyle,
                                                )

                                                is PageSlice.Image -> EmbeddedImage(
                                                    image = slice.image,
                                                    active = slice.blockIndex == highlightedBlock,
                                                    accent = accent,
                                                    subtle = palette.subtle,
                                                    onClick = { openImagePreview(slice.blockIndex) },
                                                )

                                                is PageSlice.PageBreak -> PageBreakLine(palette.subtle)

                                                is PageSlice.SeriesFooter -> if (series != null) {
                                                    SeriesFooterCard(
                                                        seriesTitle = series.title,
                                                        order = series.order,
                                                        prevId = prevEp?.id,
                                                        prevTitle = prevEp?.title,
                                                        nextId = nextEp?.id,
                                                        nextTitle = nextEp?.title,
                                                        onOpenEpisode = onOpenEpisode,
                                                        onOpenSeries = { onOpenSeries(series.seriesId, work.id) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                BasicText(
                                    text = "${pageIdx + 1} / ${readerPages.size}",
                                    style = TextStyle(
                                        color = palette.subtle.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { chrome = !chrome },
                ) {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .overscroll(overscroll),
                            contentPadding = PaddingValues(
                                start = 22.dp,
                                end = 28.dp,
                                top = statusTop + 68.dp,
                                bottom = navBottom + 116.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item(key = "novel-heading") {
                                Column(Modifier.padding(bottom = 12.dp)) {
                                    BasicText(
                                        text = work.title.ifBlank { stringResource(R.string.untitled_work) },
                                        style = TextStyle(
                                            color = palette.ink,
                                            fontSize = (style.fontSize + 6).sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = (style.fontSize + 14).sp,
                                        ),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    BasicText(
                                        text = work.userName,
                                        style = TextStyle(
                                            color = palette.subtle,
                                            fontSize = 13.sp,
                                        ),
                                    )
                                }
                            }

                            items(blocks.size, key = { it }) { index ->
                                when (val block = blocks[index]) {
                                    is NovelBlock.Chapter -> ChapterHeading(
                                        title = block.title,
                                        fontSize = style.fontSize,
                                        ink = palette.ink,
                                        accent = accent,
                                    )

                                    is NovelBlock.Paragraph -> ParagraphText(
                                        text = block.text,
                                        first = block.first,
                                        indent = style.indent,
                                        textStyle = bodyStyle,
                                    )

                                    is NovelBlock.Image -> EmbeddedImage(
                                        image = block.image,
                                        active = index == highlightedBlock,
                                        accent = accent,
                                        subtle = palette.subtle,
                                        onClick = { openImagePreview(index) },
                                    )

                                    is NovelBlock.PageBreak -> PageBreakLine(palette.subtle)
                                }
                            }

                            if (series != null) {
                                item(key = "series-footer") {
                                    SeriesFooterCard(
                                        seriesTitle = series.title,
                                        order = series.order,
                                        prevId = prevEp?.id,
                                        prevTitle = prevEp?.title,
                                        nextId = nextEp?.id,
                                        nextTitle = nextEp?.title,
                                        onOpenEpisode = onOpenEpisode,
                                        onOpenSeries = { onOpenSeries(series.seriesId, work.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right-edge hairline indicator while reading; expands into a COUI Continuous
        // slider only when the user presses and drags/holds the edge strip.
        val railExpand by animateFloatAsState(
            targetValue = if (railExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
            label = "railExpand",
        )
        if (blocks.isNotEmpty()) {
            EdgeProgressRail(
                backdrop = contentBackdrop,
                dark = palette.dark,
                ink = palette.ink,
                accent = accent,
                progress = progressState,
                imageFractions = imageFractions,
                imageBlocks = imageBlocks,
                expanded = railExpanded,
                expandProgress = railExpand,
                onActivate = {
                    railExpanded = true
                    railTouchToken = System.currentTimeMillis()
                },
                onDraggingChange = { dragging ->
                    railDragging = dragging
                    if (dragging) railExpanded = true
                    railTouchToken = System.currentTimeMillis()
                },
                onSeek = ::seekTo,
                onPrevImage = {
                    railTouchToken = System.currentTimeMillis()
                    jumpImage(false)
                },
                onNextImage = {
                    railTouchToken = System.currentTimeMillis()
                    jumpImage(true)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
            )
        }

        // Floating top chrome.
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = work.title,
                bookmarked = bookmarked,
                paged = paged,
                backdrop = contentBackdrop,
                paperColor = palette.background,
                dark = palette.dark,
                ink = palette.ink,
                accent = accent,
                exporting = exporting,
                onBack = onBack,
                onTogglePaged = { togglePagedMode() },
                onToggleBookmark = {
                    val currentBlock = if (paged) {
                        readerPages.getOrNull(pagerState.currentPage)?.firstBlockIndex ?: 0
                    } else {
                        listState.firstVisibleItemIndex
                    }
                    val now = reading.toggleBookmark(
                        ReadingMark(
                            novelId = work.id,
                            title = work.title,
                            blockIndex = currentBlock,
                            totalBlocks = blocks.size,
                            updatedAt = System.currentTimeMillis(),
                            bookmarked = bookmarked,
                        ),
                    )
                    bookmarked = now
                    Toast.makeText(
                        context,
                        resources.getString(if (now) R.string.reader_bookmarked else R.string.reader_unbookmarked),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onStyle = { showStyle.value = true },
                onExport = {
                    if (exporting) return@ReaderTopBar
                    exporting = true
                    scope.launch {
                        val body = NovelBlocks.toPlainText(blocks)
                        val header = buildString {
                            append(work.title).append('\n')
                            append(resources.getString(R.string.novel_export_author_line, work.userName)).append('\n')
                            append(PixivApi.NOVEL_PAGE_URL).append(work.id).append("\n\n")
                        }
                        val name = ImageDownloader.safeName(
                            "pixiv_novel_${work.id}_${work.title}.txt",
                            "pixiv_novel_${work.id}.txt",
                        )
                        val result = runCatching { downloads.saveText(context, name, header + body) }
                        exporting = false
                        Toast.makeText(
                            context,
                            resources.getString(
                                if (result.isSuccess) R.string.viewer_saved else R.string.viewer_save_failed,
                                result.getOrNull() ?: "",
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        // Floating bottom chrome when series navigation or chapters exist.
        if (series != null || chapters.isNotEmpty()) {
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBottom + 14.dp, start = 16.dp, end = 16.dp),
            ) {
                ReaderBottomBar(
                    backdrop = contentBackdrop,
                    dark = palette.dark,
                    ink = palette.ink,
                    hasChapters = chapters.isNotEmpty(),
                    hasPrev = prevEp != null,
                    hasNext = nextEp != null,
                    hasSeries = series != null,
                    onPrev = { prevEp?.let { onOpenEpisode(it.id) } },
                    onToc = { showToc = true },
                    onSeries = { series?.let { onOpenSeries(it.seriesId, work.id) } },
                    onNext = { nextEp?.let { onOpenEpisode(it.id) } },
                )
            }
        }
    }

    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(appDark) else null

    TypographySheet(
        visible = showStyle,
        style = style,
        dark = appDark,
        windowBackdrop = windowBackdrop,
        onFontSize = { settings.setReaderFontSize(it) },
        onWeight = { settings.setReaderWeight(it) },
        onIndent = { settings.setReaderIndent(it) },
        onPaperTheme = { settings.setReaderPaperTheme(it) },
        onLineSpacing = { settings.setReaderLineSpacing(it) },
        onReset = { settings.resetReaderStyle() },
    )

    TocDialog(
        visible = showToc,
        chapters = chapters,
        dark = appDark,
        material = dialogMaterial,
        windowBackdrop = windowBackdrop,
        onSelectBlock = { idx ->
            showToc = false
            jumpToBlock(idx + 1)
        },
        onDismiss = { showToc = false },
    )

    val activePreviewIndex = previewImageIndex
    if (activePreviewIndex != null && viewerPages.isNotEmpty()) {
        BackHandler { previewImageIndex = null }
        ImageViewerContent(
            pages = viewerPages,
            initialPage = activePreviewIndex,
            loading = false,
            workId = "novel_${work.id}",
            downloads = downloads,
            onBack = { previewImageIndex = null },
        )
    }
}

@Composable
private fun ChapterHeading(
    title: String,
    fontSize: Int,
    ink: Color,
    accent: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height((fontSize + 6).dp)
                .clip(CouiCapsuleShape)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = title,
            style = TextStyle(
                color = ink,
                fontSize = (fontSize + 3).sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = ((fontSize + 3) * 1.4f).sp,
            ),
        )
    }
}

@Composable
private fun ParagraphText(
    text: String,
    first: Boolean,
    indent: Boolean,
    textStyle: TextStyle,
) {
    val prefix = if (first && indent) "\u3000\u3000" else ""
    BasicText(
        text = prefix + text,
        style = textStyle,
    )
}

@Composable
private fun PageBreakLine(subtle: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.36f)
                .height(1.dp)
                .background(subtle.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun EmbeddedImage(
    image: NovelImage?,
    active: Boolean,
    accent: Color,
    subtle: Color,
    onClick: () -> Unit,
) {
    if (image == null) {
        Text(
            text = stringResource(R.string.reader_image_missing),
            kind = CouiTextKind.ItemSummary,
            color = subtle,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val shape = CouiShape(14.dp, 3f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .heightIn(min = 180.dp)
            .then(if (active) Modifier.border(2.dp, accent, shape) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RetryableImage(
            url = image.displayUrl,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SeriesFooterCard(
    seriesTitle: String,
    order: Int,
    prevId: String?,
    prevTitle: String?,
    nextId: String?,
    nextTitle: String?,
    onOpenEpisode: (String) -> Unit,
    onOpenSeries: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    Spacer(Modifier.height(16.dp))
    Group(title = seriesTitle.ifBlank { stringResource(R.string.detail_series) }) {
        Column(Modifier.padding(16.dp)) {
            if (order > 0) {
                Text(
                    text = stringResource(R.string.detail_series_order, order),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (nextId != null) {
                PrimaryButton(
                    text = if (!nextTitle.isNullOrBlank()) {
                        "${stringResource(R.string.reader_next_episode)} · $nextTitle"
                    } else {
                        stringResource(R.string.reader_next_episode)
                    },
                    onClick = { onOpenEpisode(nextId) },
                )
                Spacer(Modifier.height(10.dp))
            } else {
                Text(
                    text = stringResource(R.string.reader_series_end),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (prevId != null) {
                    SecondaryButton(
                        text = stringResource(R.string.reader_prev_episode),
                        onClick = { onOpenEpisode(prevId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.series_view),
                    onClick = onOpenSeries,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    bookmarked: Boolean,
    paged: Boolean,
    backdrop: CouiBackdrop,
    paperColor: Color,
    dark: Boolean,
    ink: Color,
    accent: Color,
    exporting: Boolean,
    onBack: () -> Unit,
    onTogglePaged: () -> Unit,
    onToggleBookmark: () -> Unit,
    onStyle: () -> Unit,
    onExport: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val modeLabel = stringResource(if (paged) R.string.reader_mode_paged else R.string.reader_mode_scroll)
    val exportLabel = if (exporting) "…" else stringResource(R.string.reader_export)
    val styleLabel = stringResource(R.string.reader_style)
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = 1f,
        gradientColor = paperColor,
        dark = dark,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
            Spacer(Modifier.width(8.dp))
            GlowSurface(
                backdrop = backdrop,
                dark = dark,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = CouiCapsuleShape,
                whiteGlass = true,
                interactive = false,
                foregroundComposition = true,
            ) {
                BasicText(
                    text = title,
                    style = TextStyle(
                        color = if (dark) Color.White else ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            GlowPillButton(
                text = stringResource(if (bookmarked) R.string.reader_bookmarked_short else R.string.reader_bookmark),
                backdrop = backdrop,
                dark = dark,
                selected = bookmarked,
                accent = accent,
                ink = ink,
                onClick = onToggleBookmark,
            )
            Spacer(Modifier.width(6.dp))
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
                    modifier = Modifier.widthIn(min = 168.dp),
                    offset = DpOffset(0.dp, 8.dp),
                    style = CouiMenuDefaults.style(dark = dark),
                    items = listOf(
                        CouiMenuItem(value = 0, text = modeLabel, selected = true),
                        CouiMenuItem(value = 1, text = exportLabel),
                        CouiMenuItem(value = 2, text = styleLabel),
                    ),
                    onItemClick = { item ->
                        menuExpanded = false
                        when (item.value) {
                            0 -> onTogglePaged()
                            1 -> onExport()
                            2 -> onStyle()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    backdrop: CouiBackdrop,
    dark: Boolean,
    ink: Color,
    hasChapters: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    hasSeries: Boolean,
    onPrev: () -> Unit,
    onToc: () -> Unit,
    onSeries: () -> Unit,
    onNext: () -> Unit,
) {
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = CouiCapsuleShape,
        whiteGlass = true,
        interactive = true,
        foregroundComposition = true,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasSeries) {
                BottomBarAction(
                    text = stringResource(R.string.reader_prev_episode),
                    enabled = hasPrev,
                    dark = dark,
                    ink = if (dark) Color.White else ink,
                    onClick = onPrev,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasChapters) {
                BottomBarAction(
                    text = stringResource(R.string.reader_toc),
                    enabled = true,
                    dark = dark,
                    ink = if (dark) Color.White else ink,
                    onClick = onToc,
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasSeries) {
                BottomBarAction(
                    text = stringResource(R.string.series_title),
                    enabled = true,
                    dark = dark,
                    ink = if (dark) Color.White else ink,
                    onClick = onSeries,
                    modifier = Modifier.weight(1f),
                )
                BottomBarAction(
                    text = stringResource(R.string.reader_next_episode),
                    enabled = hasNext,
                    dark = dark,
                    ink = if (dark) Color.White else ink,
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarAction(
    text: String,
    enabled: Boolean,
    dark: Boolean,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = enabled,
        preset = SpotlightPreset.OpaqueSmall2,
        dark = dark,
    )
    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                scaleX = press.scale.value
                scaleY = press.scale.value
            }
            .clip(CouiCapsuleShape)
            .couiPressMotion(press, enabled)
            .then(lightInput)
            .drawBehind { drawLight?.invoke(this) }
            .couiToolbarFeedback(dark, interaction, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (enabled) ink else ink.copy(alpha = 0.35f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun GlowPillButton(
    text: String,
    backdrop: CouiBackdrop,
    dark: Boolean,
    selected: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = Modifier.height(36.dp),
        shape = CouiCapsuleShape,
        preset = SpotlightPreset.OpaqueSmall2,
        whiteGlass = !selected,
        colored = if (selected) accent else null,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        foregroundComposition = true,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = when {
                    selected -> Color.White
                    dark -> Color.White
                    else -> ink
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun EdgeProgressRail(
    backdrop: CouiBackdrop,
    dark: Boolean,
    ink: Color,
    accent: Color,
    progress: State<Float>,
    imageFractions: List<Float>,
    imageBlocks: List<Int>,
    expanded: Boolean,
    expandProgress: Float,
    onActivate: () -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onSeek: (fraction: Float, block: Int?) -> Unit,
    onPrevImage: () -> Unit,
    onNextImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val sliderColors = remember(accent, dark) { CouiSliderDefaults.colors(accent, dark) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var activeSnap by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableLongStateOf(0L) }

    val insetPx = with(density) { RailInset.toPx() }
    val enterPx = with(density) { SnapEnter.toPx() }
    val exitPx = with(density) { SnapExit.toPx() }

    val trackWidthDp by animateDpAsState(
        targetValue = if (dragging) 18.dp else 14.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
        label = "sliderTrackWidth",
    )
    val thumbRadiusDp by animateDpAsState(
        targetValue = if (dragging) 9.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
        label = "sliderThumbRadius",
    )

    val (sliderSpotlightInput, drawSliderSpotlight) = rememberCouiSpotlight(
        interactive = expanded,
        preset = SpotlightPreset.OpaqueMedium1,
        dark = dark,
    )

    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastTick < 55L) return
        lastTick = now
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun nearestWithin(raw: Float, usable: Float, radiusPx: Float): Int? {
        var nearest: Int? = null
        var best = Float.MAX_VALUE
        imageFractions.forEachIndexed { i, f ->
            val d = abs(raw - f) * usable
            if (d < best) {
                best = d
                nearest = i
            }
        }
        return nearest?.takeIf { best <= radiusPx }
    }

    fun applyFraction(raw: Float, usable: Float, commitSnap: Boolean) {
        val clamped = raw.coerceIn(0f, 1f)
        if (commitSnap) {
            val snap = nearestWithin(clamped, usable, exitPx)
            val block = snap?.let { imageBlocks.getOrNull(it) }
            activeSnap = block
            dragFraction = snap?.let { imageFractions[it] } ?: clamped
            onSeek(dragFraction, block)
        } else {
            dragFraction = clamped
            val near = nearestWithin(clamped, usable, enterPx)
            val block = near?.let { imageBlocks.getOrNull(it) }
            if (block != activeSnap) {
                if (block != null) tick()
                activeSnap = block
            }
            onSeek(clamped, null)
        }
    }

    Box(
        modifier = modifier.height(RailHeight),
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 1. Hairline COUI indicator (longer, subtle 凝光 capsule, never blocks text).
        // Keep the touch container mounted even while hairlineAlpha fades to 0 so an in-flight
        // drag from the hairline into the expanded Continuous slider is never cancelled mid-gesture.
        val hairlineAlpha = (1f - expandProgress * 1.4f).coerceIn(0f, 1f)
        val currentExpanded by rememberUpdatedState(expanded)
        Box(
            Modifier
                .width(30.dp)
                .height(RailHairlineHeight + 24.dp)
                .pointerInput(imageFractions, imageBlocks) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (currentExpanded) return@awaitEachGesture
                        val usable = (size.height - 2 * insetPx).coerceAtLeast(1f)
                        var activated = false
                        var anchorY = down.position.y
                        var anchorProgress = progress.value.coerceIn(0f, 1f)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (activated) {
                                    dragging = false
                                    onDraggingChange(false)
                                    applyFraction(dragFraction, usable, commitSnap = true)
                                }
                                break
                            }
                            val movedY = abs(change.position.y - down.position.y)
                            val heldMs = change.uptimeMillis - down.uptimeMillis
                            if (!activated && (movedY > viewConfiguration.touchSlop * 2f || heldMs >= 200L)) {
                                activated = true
                                dragging = true
                                // Anchor to the finger position at activation so subsequent dragging
                                // continues smoothly from the current reading progress without jumping.
                                anchorY = change.position.y
                                anchorProgress = progress.value.coerceIn(0f, 1f)
                                dragFraction = anchorProgress
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onActivate()
                                onDraggingChange(true)
                            }
                            if (activated) {
                                val deltaFraction = (change.position.y - anchorY) / usable
                                applyFraction(anchorProgress + deltaFraction, usable, commitSnap = false)
                                change.consume()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (hairlineAlpha > 0.01f) {
                GlowSurface(
                    backdrop = backdrop,
                    dark = dark,
                    modifier = Modifier
                        .width(4.dp)
                        .height(RailHairlineHeight)
                        .alpha(hairlineAlpha),
                    shape = CouiCapsuleShape,
                    whiteGlass = true,
                    interactive = false,
                    foregroundComposition = true,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val value = progress.value.coerceIn(0f, 1f)
                        val radius = CornerRadius(size.width / 2f)
                        drawRoundRect(
                            color = ink.copy(alpha = 0.14f),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = radius,
                        )
                        drawRoundRect(
                            color = accent.copy(alpha = 0.72f),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height * value),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }

        // 2. Expanded COUI Continuous slider capsule with full 凝光视效.
        if (expandProgress > 0.01f) {
            GlowSurface(
                backdrop = backdrop,
                dark = dark,
                modifier = Modifier
                    .width(RailWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = expandProgress
                        val sx = 0.22f + 0.78f * expandProgress
                        val sy = 0.68f + 0.32f * expandProgress
                        scaleX = sx
                        scaleY = sy
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
                shape = CouiCapsuleShape,
                preset = SpotlightPreset.OpaqueMedium1,
                whiteGlass = true,
                interactive = expanded,
                foregroundComposition = true,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RailArrow(
                        enabled = expanded && imageBlocks.isNotEmpty(),
                        up = true,
                        tint = if (dark) Color.White else ink,
                        description = stringResource(R.string.reader_prev_image),
                        onClick = onPrevImage,
                    )

                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(sliderSpotlightInput)
                            .pointerInput(expanded, imageFractions, imageBlocks) {
                                if (!expanded) return@pointerInput
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        dragging = true
                                        onDraggingChange(true)
                                        val usable = (size.height - 2 * insetPx).coerceAtLeast(1f)
                                        val raw = (offset.y - insetPx) / usable
                                        applyFraction(raw, usable, commitSnap = false)
                                    },
                                    onDragEnd = {
                                        dragging = false
                                        onDraggingChange(false)
                                        val usable = (size.height - 2 * insetPx).coerceAtLeast(1f)
                                        applyFraction(dragFraction, usable, commitSnap = true)
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        onDraggingChange(false)
                                    },
                                ) { change, _ ->
                                    val usable = (size.height - 2 * insetPx).coerceAtLeast(1f)
                                    val raw = (change.position.y - insetPx) / usable
                                    applyFraction(raw, usable, commitSnap = false)
                                }
                            }
                            .pointerInput(expanded, imageFractions, imageBlocks) {
                                if (!expanded) return@pointerInput
                                detectTapGestures { offset ->
                                    onActivate()
                                    val usable = (size.height - 2 * insetPx).coerceAtLeast(1f)
                                    val raw = (offset.y - insetPx) / usable
                                    applyFraction(raw, usable, commitSnap = true)
                                }
                            },
                    ) {
                        val pad = RailInset.toPx()
                        val usable = (size.height - 2 * pad).coerceAtLeast(0f)
                        val trackWidth = trackWidthDp.toPx()
                        val cx = size.width / 2f
                        val radius = CornerRadius(trackWidth / 2f)

                        // COUI Continuous slider capsule track
                        drawRoundRect(
                            color = sliderColors.inactiveTrack,
                            topLeft = Offset(cx - trackWidth / 2f, pad),
                            size = Size(trackWidth, usable),
                            cornerRadius = radius,
                        )

                        val fraction = (if (dragging) dragFraction else progress.value).coerceIn(0f, 1f)
                        val thumbY = pad + fraction * usable
                        drawRoundRect(
                            color = sliderColors.activeTrack,
                            topLeft = Offset(cx - trackWidth / 2f, pad),
                            size = Size(trackWidth, (thumbY - pad).coerceAtLeast(0f)),
                            cornerRadius = radius,
                        )

                        drawSliderSpotlight?.invoke(this)

                        // Illustration anchor ticks
                        imageFractions.forEachIndexed { i, f ->
                            val y = pad + f * usable
                            val active = imageBlocks.getOrNull(i) == activeSnap
                            if (active) {
                                drawCircle(color = accent, radius = 6.dp.toPx(), center = Offset(cx, y))
                                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(cx, y))
                            } else {
                                drawCircle(color = sliderColors.activeTick.copy(alpha = 0.75f), radius = 2.dp.toPx(), center = Offset(cx, y))
                            }
                        }

                        // COUI Continuous slider thumb + soft shadow
                        val thumbRadius = thumbRadiusDp.toPx()
                        drawCircle(
                            color = sliderColors.thumbShadow,
                            radius = thumbRadius + 2.dp.toPx(),
                            center = Offset(cx, thumbY + 1.5.dp.toPx()),
                        )
                        drawCircle(
                            color = sliderColors.thumb,
                            radius = thumbRadius,
                            center = Offset(cx, thumbY),
                        )
                    }

                    RailArrow(
                        enabled = expanded && imageBlocks.isNotEmpty(),
                        up = false,
                        tint = if (dark) Color.White else ink,
                        description = stringResource(R.string.reader_next_image),
                        onClick = onNextImage,
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RailPercent(
                            progress = progress,
                            dragging = dragging,
                            dragFraction = dragFraction,
                            color = if (dark) Color.White else ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailArrow(
    enabled: Boolean,
    up: Boolean,
    tint: Color,
    description: String,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.25f)
    Box(
        Modifier
            .size(32.dp)
            .clip(CouiCapsuleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (up) "▲" else "▼",
            style = TextStyle(color = color, fontSize = 10.sp),
        )
    }
}

@Composable
private fun RailPercent(
    progress: State<Float>,
    dragging: Boolean,
    dragFraction: Float,
    color: Color,
) {
    val fraction = (if (dragging) dragFraction else progress.value).coerceIn(0f, 1f)
    BasicText(
        text = "${(fraction * 100).roundToInt()}%",
        style = TextStyle(color = color.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
}

private val SwatchShape = CachedCouiShape(14.dp, 3f)

@Composable
private fun TypographySheet(
    visible: MutableState<Boolean>,
    style: ReaderStyle,
    dark: Boolean,
    windowBackdrop: CouiBackdrop?,
    onFontSize: (Int) -> Unit,
    onWeight: (Int) -> Unit,
    onIndent: (Boolean) -> Unit,
    onPaperTheme: (PaperTheme) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val haptics = LocalHapticFeedback.current
    val checkIcon = ImageVector.vectorResource(R.drawable.ic_check)
    val restartIcon = ImageVector.vectorResource(R.drawable.ic_restart_alt)
    val sheetMaterial = if (CouiDialogDefaults.isMaterialSupported() && windowBackdrop != null) {
        CouiSheetDefaults.material(dark)
    } else {
        null
    }
    var confirmReset by remember { mutableStateOf(false) }

    BottomSheet(
        visible = visible,
        dark = dark,
        sheetHeight = null,
        showDragHandle = true,
        backdrop = windowBackdrop,
        material = sheetMaterial,
        leadingButton = CouiSheetButton(
            onClick = { visible.value = false },
            enabled = visible.value,
            icon = {
                Image(
                    imageVector = checkIcon,
                    contentDescription = stringResource(R.string.action_ok),
                    colorFilter = ColorFilter.tint(LocalCouiContentColor.current),
                    modifier = Modifier.size(22.dp),
                )
            },
        ),
        titleContent = {
            Text(
                text = stringResource(R.string.reader_style_title),
                kind = CouiTextKind.SheetTitle,
            )
        },
        trailingButton = CouiSheetButton(
            onClick = { confirmReset = true },
            enabled = visible.value,
            icon = {
                Image(
                    imageVector = restartIcon,
                    contentDescription = stringResource(R.string.share_format_reset),
                    colorFilter = ColorFilter.tint(LocalCouiContentColor.current),
                    modifier = Modifier.size(22.dp),
                )
            },
        ),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.reader_paper_theme),
            kind = CouiTextKind.ItemSummary,
            color = colors.summary,
            modifier = Modifier.padding(horizontal = CouiPageDefaults.horizontalMargin + 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiPageDefaults.horizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val themes = listOf(PaperTheme.SYSTEM, PaperTheme.IVORY, PaperTheme.SAGE, PaperTheme.DARK)
            themes.forEach { theme ->
                PaperSwatchChip(
                    theme = theme,
                    appDark = dark,
                    selected = style.paperTheme == theme,
                    accent = colors.accent,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPaperTheme(theme)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Group {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.reader_font_size, style.fontSize),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FontStepButton(
                        label = "A-",
                        enabled = style.fontSize > 13,
                        dark = dark,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFontSize((style.fontSize - 1).coerceAtLeast(13))
                        },
                    )
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = style.fontSize.toFloat(),
                        onValueChange = { onFontSize(it.roundToInt()) },
                        valueRange = 13f..23f,
                        steps = 9,
                    )
                    FontStepButton(
                        label = "A+",
                        enabled = style.fontSize < 23,
                        dark = dark,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFontSize((style.fontSize + 1).coerceAtMost(23))
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                val spacings = listOf(1.6f, 1.9f, 2.2f)
                val spacingIndex = spacings.indices.minByOrNull { abs(spacings[it] - style.lineSpacing) } ?: 1
                Text(
                    text = stringResource(R.string.reader_line_spacing, spacings[spacingIndex].toString()),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    labels = listOf("1.6×", "1.9×", "2.2×"),
                    selectedIndex = spacingIndex,
                    onSelected = { onLineSpacing(spacings[it]) },
                    small = true,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.reader_weight),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Spacer(Modifier.height(8.dp))
                val weights = listOf(300, 400, 500, 700)
                SegmentedControl(
                    modifier = Modifier.fillMaxWidth(),
                    labels = listOf(
                        stringResource(R.string.reader_weight_light),
                        stringResource(R.string.reader_weight_regular),
                        stringResource(R.string.reader_weight_medium),
                        stringResource(R.string.reader_weight_bold),
                    ),
                    selectedIndex = weights.indexOf(style.weight).coerceAtLeast(0),
                    onSelected = { onWeight(weights[it]) },
                    small = true,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Group {
            SettingSwitchRow(
                title = stringResource(R.string.reader_indent),
                checked = style.indent,
                onCheckedChange = onIndent,
            )
        }
        Spacer(Modifier.height(16.dp))

        val sheetDialogBackdrop = rememberCouiWindowBackdrop()
        val sheetDialogMaterial = if (sheetDialogBackdrop != null) CouiDialogDefaults.material(dark) else null
        val destructiveColors = CouiDialogDefaults.primaryActionColors(CouiDialogDefaults.colors(dark).destructive)
        Dialog(
            visible = confirmReset,
            onDismissRequest = { confirmReset = false },
            title = stringResource(R.string.reader_style_reset_title),
            message = stringResource(R.string.reader_style_reset_message),
            dark = dark,
            material = sheetDialogMaterial,
            backdrop = sheetDialogBackdrop,
            confirmAction = CouiDialogAction(
                text = stringResource(R.string.reader_style_reset_confirm),
                onClick = {
                    onReset()
                    confirmReset = false
                },
                style = CouiDialogActionStyle.Primary,
                colors = destructiveColors,
            ),
            dismissAction = CouiDialogAction(
                text = stringResource(R.string.action_cancel),
                onClick = { confirmReset = false },
            ),
        )
    }
}

@Composable
private fun PaperSwatchChip(
    theme: PaperTheme,
    appDark: Boolean,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatchPalette = paperPalette(theme, appDark)
    val label = when (theme) {
        PaperTheme.SYSTEM -> stringResource(R.string.reader_paper_system)
        PaperTheme.IVORY -> stringResource(R.string.reader_paper_ivory)
        PaperTheme.SAGE -> stringResource(R.string.reader_paper_sage)
        PaperTheme.DARK -> stringResource(R.string.reader_paper_dark)
    }
    val interaction = remember { MutableInteractionSource() }
    val border = if (selected) {
        BorderStroke(2.dp, accent)
    } else {
        BorderStroke(0.5.dp, if (swatchPalette.dark) Color(0x33FFFFFF) else Color(0x26000000))
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(SwatchShape)
            .background(swatchPalette.background, SwatchShape)
            .border(border, SwatchShape)
            .couiButtonFeedback(swatchPalette.dark, interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (theme == PaperTheme.SYSTEM) {
                Canvas(Modifier.size(12.dp)) {
                    val strokeColor = if (appDark) Color(0xFFE8E6E1) else Color(0xFF232323)
                    drawCircle(color = Color(0xFFF7F5F0))
                    drawArc(
                        color = Color(0xFF141416),
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true,
                    )
                    drawCircle(
                        color = strokeColor.copy(alpha = 0.45f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            BasicText(
                text = label,
                style = TextStyle(
                    color = swatchPalette.ink,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FontStepButton(
    label: String,
    enabled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(30.dp)
            .clip(CouiCapsuleShape)
            .background(if (dark) Color(0x1FFFFFFF) else Color(0x12000000), CouiCapsuleShape)
            .couiButtonFeedback(dark, interaction, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) colors.title else colors.summary.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun TocDialog(
    visible: Boolean,
    chapters: List<Pair<Int, String>>,
    dark: Boolean,
    material: com.itos.coui.dialog.CouiDialogMaterial?,
    windowBackdrop: CouiBackdrop?,
    onSelectBlock: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    Dialog(
        visible = visible,
        onDismissRequest = onDismiss,
        dark = dark,
        material = material,
        backdrop = windowBackdrop,
        title = stringResource(R.string.reader_toc),
        confirmAction = CouiDialogAction(stringResource(R.string.action_cancel), onClick = onDismiss),
    ) {
        Column(Modifier.heightIn(max = 360.dp)) {
            chapters.forEachIndexed { index, (blockIdx, title) ->
                Item(onClick = { onSelectBlock(blockIdx) }) {
                    Text(
                        text = title,
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index < chapters.lastIndex) Divider()
            }
        }
    }
}

private sealed interface PageSlice {
    data class Heading(val title: String, val author: String) : PageSlice
    data class Chapter(val title: String) : PageSlice
    data class Paragraph(val text: String) : PageSlice
    data class Image(val blockIndex: Int, val image: NovelImage?) : PageSlice
    data object PageBreak : PageSlice
    data object SeriesFooter : PageSlice
}

private data class ReaderPage(
    val firstBlockIndex: Int,
    val slices: List<PageSlice>,
)

private fun paginateNovelBlocks(
    blocks: List<NovelBlock>,
    title: String,
    author: String,
    fontSize: Int,
    indent: Boolean,
    bodyStyle: TextStyle,
    hasSeriesFooter: Boolean,
    widthPx: Int,
    heightPx: Int,
    density: androidx.compose.ui.unit.Density,
    measurer: TextMeasurer,
): List<ReaderPage> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()
    val maxWidth = widthPx.coerceAtLeast(1)
    val gapPx = with(density) { 12.dp.roundToPx() }
    val footerReservePx = with(density) { 26.dp.roundToPx() }
    val pageLimit = (heightPx - footerReservePx).coerceAtLeast(120)
    val pages = mutableListOf<ReaderPage>()
    var currentSlices = mutableListOf<PageSlice>()
    var currentHeight = 0
    var currentFirstBlock = 0

    fun flushPage(nextBlockIndex: Int) {
        if (currentSlices.isNotEmpty()) {
            pages.add(ReaderPage(currentFirstBlock, currentSlices))
            currentSlices = mutableListOf()
            currentHeight = 0
            currentFirstBlock = nextBlockIndex
        }
    }

    fun appendSlice(slice: PageSlice, sliceHeight: Int, blockIndex: Int) {
        val add = if (currentSlices.isEmpty()) sliceHeight else sliceHeight + gapPx
        if (currentSlices.isNotEmpty() && currentHeight + add > pageLimit) {
            flushPage(blockIndex)
        }
        if (currentSlices.isEmpty()) currentFirstBlock = blockIndex
        currentSlices.add(slice)
        currentHeight += if (currentSlices.size == 1) sliceHeight else sliceHeight + gapPx
    }

    val headingResult = measurer.measure(
        text = title,
        style = TextStyle(
            fontSize = (fontSize + 6).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize + 14).sp,
        ),
        constraints = Constraints(maxWidth = maxWidth),
    )
    val headingHeight = headingResult.size.height + with(density) { 36.dp.roundToPx() }
    appendSlice(PageSlice.Heading(title, author), headingHeight, 0)

    blocks.forEachIndexed { index, block ->
        when (block) {
            is NovelBlock.PageBreak -> {
                if (currentSlices.isNotEmpty()) flushPage(index + 1)
            }

            is NovelBlock.Chapter -> {
                val res = measurer.measure(
                    text = block.title,
                    style = TextStyle(
                        fontSize = (fontSize + 3).sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = ((fontSize + 3) * 1.4f).sp,
                    ),
                    constraints = Constraints(maxWidth = (maxWidth - with(density) { 14.dp.roundToPx() }).coerceAtLeast(1)),
                )
                val h = res.size.height + with(density) { 20.dp.roundToPx() }
                appendSlice(PageSlice.Chapter(block.title), h, index)
            }

            is NovelBlock.Image -> {
                val h = with(density) { 220.dp.roundToPx() }
                appendSlice(PageSlice.Image(index, block.image), h, index)
            }

            is NovelBlock.Paragraph -> {
                val fullText = (if (block.first && indent) "\u3000\u3000" else "") + block.text
                if (fullText.isEmpty()) return@forEachIndexed
                val layout = measurer.measure(
                    text = fullText,
                    style = bodyStyle,
                    constraints = Constraints(maxWidth = maxWidth),
                )
                var lineStart = 0
                while (lineStart < layout.lineCount) {
                    val available = pageLimit - (if (currentSlices.isEmpty()) 0 else currentHeight + gapPx)
                    if (available < bodyStyle.lineHeight.value * density.density && currentSlices.isNotEmpty()) {
                        flushPage(index)
                        continue
                    }
                    val startTop = layout.getLineTop(lineStart)
                    var lineEnd = lineStart
                    while (lineEnd + 1 < layout.lineCount &&
                        layout.getLineBottom(lineEnd + 1) - startTop <= available
                    ) {
                        lineEnd++
                    }
                    val charStart = layout.getLineStart(lineStart)
                    val charEnd = layout.getLineEnd(lineEnd, visibleEnd = false).coerceAtLeast(charStart)
                    val chunk = fullText.substring(charStart, charEnd)
                    val chunkHeight = (layout.getLineBottom(lineEnd) - startTop).roundToInt().coerceAtLeast(1)
                    appendSlice(PageSlice.Paragraph(chunk), chunkHeight, index)
                    lineStart = lineEnd + 1
                    if (lineStart < layout.lineCount) flushPage(index)
                }
            }
        }
    }

    if (hasSeriesFooter) {
        val footerHeight = with(density) { 180.dp.roundToPx() }
        appendSlice(PageSlice.SeriesFooter, footerHeight, blocks.lastIndex.coerceAtLeast(0))
    }
    flushPage(blocks.size)
    return pages
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
