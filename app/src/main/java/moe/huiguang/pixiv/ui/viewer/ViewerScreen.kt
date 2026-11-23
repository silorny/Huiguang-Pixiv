package moe.huiguang.pixiv.ui.viewer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.icons.CouiIcons
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import moe.huiguang.pixiv.design.components.CircleIconButton
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.model.IllustPage
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.ui.detail.ArtworkCache

/**
 * Full-screen original-image viewer.
 *
 * Chrome is a plain translucent black row instead of a COUI TopAppBar: the
 * app bar's gradient is white in light mode, which looked like a white smudge
 * over a black canvas. Pinch / double-tap zooms; while zoomed the pager stops
 * accepting horizontal drags.
 */
@Composable
fun ViewerScreen(
    workId: String,
    initialPage: Int,
    api: PixivApi,
    cache: ArtworkCache,
    downloads: ImageDownloader,
    onBack: () -> Unit,
) {
    val cached = remember(workId) { cache.get(workId) }
    var pages by remember(workId) { mutableStateOf(cached?.pages ?: emptyList()) }
    var loading by remember { mutableStateOf(cached == null) }

    LaunchedEffect(workId) {
        if (pages.isNotEmpty()) return@LaunchedEffect
        runCatching { api.illustPages(workId) }
            .onSuccess { loaded ->
                pages = loaded
                val detail = runCatching { api.illust(workId) }.getOrNull()
                if (detail != null) cache.put(workId, ArtworkCache.Entry(detail, loaded))
            }
        loading = false
    }

    ImageViewerContent(
        pages = pages,
        initialPage = initialPage,
        loading = loading,
        workId = workId,
        downloads = downloads,
        onBack = onBack,
    )
}

/**
 * Reusable full-screen image viewer shared by [ViewerScreen] and novel
 * embedded-illustration previews in `ReaderScreen`.
 */
@Composable
fun ImageViewerContent(
    pages: List<IllustPage>,
    initialPage: Int,
    loading: Boolean,
    workId: String,
    downloads: ImageDownloader,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = CouiTheme.isDark
    val canvasBg = if (dark) Color.Black else Color.White

    // Match status-bar icon contrast to the current light/dark viewer canvas;
    // restore the previous appearance when leaving.
    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
    }
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size.coerceAtLeast(1) },
    )
    // Multi-page works read top-to-bottom by default (manga); the chrome toggle
    // switches back to page-by-page horizontal flipping.
    var vertical by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
    )
    var verticalIndex by remember { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { verticalIndex = it }
    }
    val currentPage = if (vertical) verticalIndex else pagerState.currentPage
    var zoomed by remember { mutableStateOf(false) }
    var showChrome by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(currentPage) { zoomed = false }

    // Preload the next two pages so scrolling/flipping manga feels seamless.
    LaunchedEffect(currentPage, pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        listOf(currentPage + 1, currentPage + 2).forEach { idx ->
            pages.getOrNull(idx)?.originalUrl?.let { url ->
                loader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
        }
    }

    fun switchMode() {
        val page = currentPage
        vertical = !vertical
        scope.launch {
            if (vertical) listState.scrollToItem(page) else pagerState.scrollToPage(page)
        }
    }

    fun saveCurrent() {
        val page = pages.getOrNull(currentPage) ?: return
        scope.launch {
            val name = ImageDownloader.fileNameFor(page.originalUrl, workId, currentPage)
            val result = runCatching { downloads.download(context, page.originalUrl, name) }
            Toast.makeText(
                context,
                resources.getString(
                    if (result.isSuccess) R.string.viewer_saved else R.string.viewer_save_failed,
                    result.getOrNull() ?: "",
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val backdrop = rememberCouiBackdrop()
    val effectBackdrop = rememberCouiBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inkColor = if (dark) Color.White else CouiPresets.Light.onSurface
    val subtleColor = if (dark) Color(0xFF9A9A9A) else CouiPresets.Light.onSurfaceVariant

    Box(
        Modifier
            .fillMaxSize()
            .background(canvasBg),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(canvasBg),
        ) {
            // Flat sibling source for CircleIconButton and page badge so glass tone
            // matches the canvas rather than sampling the underlying artwork colors.
            Box(
                Modifier
                    .matchParentSize()
                    .couiBackdrop(effectBackdrop)
                    .background(canvasBg),
            )

            if (pages.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_loading),
                    kind = CouiTextKind.ItemSummary,
                    color = subtleColor,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (pages.size == 1) {
                // Single-page work: always center inside the viewport when shorter than screen height.
                ZoomableImage(
                    page = pages[0],
                    dark = dark,
                    onZoomChanged = { zoomed = it },
                    onToggleChrome = { showChrome = !showChrome },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (vertical) {
                // Multi-page vertical reading: clear the floating top bar initially, then glide beneath it.
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = statusTop + 52.dp + 12.dp,
                        bottom = navBottom + 24.dp,
                    ),
                    userScrollEnabled = !zoomed,
                ) {
                    items(count = pages.size, key = { pages[it].originalUrl }) { index ->
                        SeamlessPage(
                            page = pages[index],
                            dark = dark,
                            onZoomChanged = { zoomed = it },
                            onToggleChrome = { showChrome = !showChrome },
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !zoomed,
                ) { index ->
                    ZoomableImage(
                        page = pages[index],
                        dark = dark,
                        onZoomChanged = { zoomed = it },
                        onToggleChrome = { showChrome = !showChrome },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBarSurface(
                modifier = Modifier.fillMaxWidth(),
                backdrop = backdrop,
                backgroundAlpha = 1f,
                gradientColor = canvasBg,
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
                        backdrop = effectBackdrop,
                        dark = dark,
                        blurRadius = 20f,
                        staticSurface = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    GlowSurface(
                        backdrop = effectBackdrop,
                        dark = dark,
                        modifier = Modifier.height(30.dp),
                        shape = CouiCapsuleShape,
                        cornerWeight = 1f,
                        preset = SpotlightPreset.OpaqueSmall2,
                        whiteGlass = true,
                        interactive = false,
                        foregroundComposition = true,
                        staticSurface = true,
                    ) {
                        BasicText(
                            text = if (pages.isEmpty()) "" else "${currentPage + 1} / ${pages.size}",
                            style = TextStyle(color = inkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    ChromeButton(
                        backdrop = effectBackdrop,
                        dark = dark,
                        onClick = { saveCurrent() },
                        content = {
                            Image(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.viewer_save),
                                colorFilter = ColorFilter.tint(inkColor),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        icon = CouiIcons.Filled.FlipPhone,
                        contentDescription = stringResource(
                            if (vertical) R.string.viewer_mode_horizontal
                            else R.string.viewer_mode_vertical,
                        ),
                        onClick = { switchMode() },
                        backdrop = effectBackdrop,
                        dark = dark,
                        blurRadius = 20f,
                        staticSurface = true,
                    )
                }
            }
        }

        if (loading) {
            Text(
                text = stringResource(R.string.search_loading),
                kind = CouiTextKind.ItemSummary,
                color = subtleColor,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ChromeButton(
    backdrop: com.itos.coui.backdrop.CouiBackdrop,
    dark: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = Modifier.size(40.dp),
        shape = CouiTopAppBarButtonDefaults.circleShape,
        cornerWeight = 2f,
        preset = SpotlightPreset.OpaqueSmall2,
        blurRadius = 20f,
        whiteGlass = true,
        onClick = onClick,
        foregroundComposition = true,
        staticSurface = true,
    ) {
        content()
    }
}

@Composable
private fun SeamlessPage(
    page: IllustPage,
    dark: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val ratio = if (page.width > 0 && page.height > 0) {
        page.width.toFloat() / page.height.toFloat()
    } else {
        // Unknown dimensions: a conservative portrait slot, the image still
        // fills the width via ContentScale.Fit.
        0.7f
    }
    ZoomableImage(
        page = page,
        dark = dark,
        onZoomChanged = onZoomChanged,
        onToggleChrome = onToggleChrome,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    )
}

@Composable
private fun ZoomableImage(
    page: IllustPage,
    dark: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(page.originalUrl) { mutableFloatStateOf(1f) }
    var offset by remember(page.originalUrl) { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    // Keep the zoomed image inside its viewport: panning past the edge left a
    // black void before, because the offset was unbounded.
    fun clamp(next: Offset, nextScale: Float): Offset {
        if (nextScale <= 1.01f) return Offset.Zero
        val maxX = bounds.width * (nextScale - 1f) / 2f
        val maxY = bounds.height * (nextScale - 1f) / 2f
        return Offset(next.x.coerceIn(-maxX, maxX), next.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { bounds = it }
            // Only claim the gesture once it is a real zoom (2+ pointers) or the
            // image is already zoomed; otherwise a one-finger drag must reach
            // the pager, which is what flipping pages relies on.
            .pointerInput(page.originalUrl) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) zooming = true
                        if (zooming || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = nextScale
                            offset = if (nextScale > 1.01f) {
                                clamp(offset + panChange, nextScale)
                            } else {
                                Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(page.originalUrl) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        RetryableImage(
            url = page.originalUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            darkPlaceholder = dark,
            constrainToBounds = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}