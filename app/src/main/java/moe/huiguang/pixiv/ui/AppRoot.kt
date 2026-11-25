package moe.huiguang.pixiv.ui

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import moe.huiguang.pixiv.AppContainer
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.search.SearchIntent
import moe.huiguang.pixiv.data.search.SearchIntentParser
import moe.huiguang.pixiv.nav.PageActivity
import moe.huiguang.pixiv.nav.Route
import moe.huiguang.pixiv.ui.common.WelcomeConsentDialog
import moe.huiguang.pixiv.ui.home.HomeScreen
import moe.huiguang.pixiv.ui.home.HomeTopChrome
import moe.huiguang.pixiv.ui.home.rememberHomeTabState
import moe.huiguang.pixiv.ui.profile.ProfileTab
import moe.huiguang.pixiv.ui.profile.ProfileTopChrome
import moe.huiguang.pixiv.ui.search.SearchTab
import moe.huiguang.pixiv.ui.search.SearchTopChrome
import moe.huiguang.pixiv.ui.search.rememberSearchTabState

/**
 * Bottom-tab shell, assembled the same way as the COUI demo:
 *
 * - the bar only owns `selectedIndex`; `page` is a single saveable Int;
 * - only the selected tab is composed, wrapped in a [SaveableStateHolder] so
 *   its input and scroll position survive a switch;
 * - secondary pages are real Activities ([PageActivity]), so this composable
 *   never keeps a back stack and never intercepts back.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    val settingsState by container.settings.state.collectAsState()

    // Defer composing tabs/HomeScreen until consent is granted so zero network
    // requests fire before the user agrees to the privacy notice.
    if (!settingsState.welcomeAccepted) {
        val colors = CouiPageDefaults.themeColors()
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background),
        )
        WelcomeConsentDialog(
            onAgree = { container.settings.setWelcomeAccepted(true) },
            onDisagree = { (context as? Activity)?.finishAffinity() },
        )
        return
    }

    var page by rememberSaveable { mutableIntStateOf(0) }
    val scrollToTopTicks = remember { mutableStateListOf(0, 0, 0) }
    val pageStates = rememberSaveableStateHolder()
    val homeTabState = rememberHomeTabState()
    val searchTabState = rememberSearchTabState()
    val profileScrollState = rememberScrollState()
    val colors = CouiPageDefaults.themeColors()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topBandHeight = statusTop + 216.dp
    val bottomBandHeight = navBottom + 120.dp
    val snapLayer = rememberGraphicsLayer()
    val snapController = remember { BackdropSnapController() }
    var snapSyncTick by remember { mutableIntStateOf(0) }
    val activeScrollInProgress by remember(homeTabState, searchTabState, profileScrollState) {
        derivedStateOf {
            when (page) {
                0 -> homeTabState.gridStates[homeTabState.section].isScrollInProgress
                1 -> searchTabState.scrollState.isScrollInProgress
                else -> profileScrollState.isScrollInProgress
            }
        }
    }
    var wasScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(activeScrollInProgress, page) {
        if (!activeScrollInProgress && (wasScrolling || snapController.lastPage != page)) {
            snapController.lastPage = page
            snapController.forceNextCapture = true
            snapSyncTick++
        } else if (snapController.lastPage != page) {
            snapController.lastPage = page
            snapController.forceNextCapture = true
            snapSyncTick++
        }
        wasScrolling = activeScrollInProgress
        if (activeScrollInProgress) {
            while (true) {
                kotlinx.coroutines.delay(22L)
                snapController.forceNextCapture = true
                snapSyncTick++
            }
        }
    }
    // Split pageBackdrop into small top & bottom strip offscreen layers (-66% FBO RAM/DDR vs 1272x2772).
    val topBackdrop = rememberCouiBackdrop {
        snapSyncTick
        if (snapLayer.size.width > 0 && snapLayer.size.height > 0) {
            drawLayer(snapLayer)
        }
    }
    val bottomBackdrop = rememberCouiBackdrop {
        snapSyncTick
        val layerH = snapLayer.size.height
        if (snapLayer.size.width > 0 && layerH > 0) {
            val yOffset = size.height - layerH.toFloat()
            translate(top = yOffset) {
                drawLayer(snapLayer)
            }
        }
    }
    val flatEffectBackdrop = rememberCouiBackdrop()
    val contentPadding = PaddingValues(top = statusTop + 64.dp, bottom = navBottom + 88.dp)
    val horizontalInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
    val haptics = LocalHapticFeedback.current
    val latestPage by rememberUpdatedState(page)
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var lastTapTab by remember { mutableIntStateOf(-1) }
    val open: (Route) -> Unit = { route -> PageActivity.open(context, route) }

    val items = listOf(
        CouiNavigationItem(
            title = stringResource(R.string.tab_home),
            icon = CouiIcons.Outlined.Album,
            selectedIcon = CouiIcons.Filled.Album,
        ),
        CouiNavigationItem(
            title = stringResource(R.string.tab_search),
            icon = CouiIcons.Outlined.Search,
            selectedIcon = CouiIcons.Outlined.Search,
        ),
        CouiNavigationItem(
            title = stringResource(R.string.tab_me),
            icon = CouiIcons.Filled.UserCircle,
            selectedIcon = CouiIcons.Filled.UserCircle,
        ),
    )

    // Decoupled foreground + clipped/throttled pageBackdrop sibling for TopChrome and NavigationBar,
    // paired with a static flat sibling backdrop for in-content floating controls.
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(horizontalInsets),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .couiBackdrop(flatEffectBackdrop)
                .background(colors.background),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topBandHeight)
                .couiBackdrop(topBackdrop),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomBandHeight)
                .couiBackdrop(bottomBackdrop),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    snapSyncTick
                    val nowNs = System.nanoTime()
                    val shouldCapture = !activeScrollInProgress ||
                        snapController.forceNextCapture ||
                        (nowNs - snapController.lastCaptureNs) >= BACKDROP_SCROLL_CAPTURE_INTERVAL_NS
                    if (shouldCapture) {
                        snapLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        snapController.lastCaptureNs = nowNs
                        snapController.forceNextCapture = false
                        drawLayer(snapLayer)
                    } else {
                        drawContent()
                    }
                }
                .background(colors.background),
        ) {
            pageStates.SaveableStateProvider(page) {
                when (page) {
                    0 -> HomeScreen(
                        padding = contentPadding,
                        api = container.api,
                        settings = container.settings,
                        cache = container.homeFeedCache,
                        tabState = homeTabState,
                        effectBackdrop = flatEffectBackdrop,
                        refreshBackdrop = flatEffectBackdrop,
                        scrollToTopTick = scrollToTopTicks[0],
                        onOpenWork = { item -> open(Route.Detail(kindOf(item), item.id)) },
                        onOpenPixivision = { articleId -> open(Route.Pixivision(articleId)) },
                        onLogin = { open(Route.Login) },
                    )

                    1 -> SearchTab(
                        padding = contentPadding,
                        settings = container.settings,
                        tabState = searchTabState,
                        effectBackdrop = flatEffectBackdrop,
                        scrollToTopTick = scrollToTopTicks[1],
                        onSubmit = { query, mode -> searchRoute(query, mode)?.let(open) },
                        onOpenHistory = { entry -> open(entry.toRoute()) },
                    )

                    else -> ProfileTab(
                        padding = contentPadding,
                        settings = container.settings,
                        api = container.api,
                        profileCache = container.selfProfileCache,
                        scroll = profileScrollState,
                        effectBackdrop = flatEffectBackdrop,
                        scrollToTopTick = scrollToTopTicks[2],
                        onLogin = { open(Route.Login) },
                        onOpenSettings = { open(Route.Settings) },
                        onOpenHistory = { open(Route.History) },
                        onOpenBookmarks = { open(Route.Bookmarks) },
                        onOpenProfileList = { uid, kind -> open(Route.ProfileList(uid, kind)) },
                    )
                }
            }
        }

        when (page) {
            0 -> HomeTopChrome(
                tabState = homeTabState,
                backdrop = topBackdrop,
                statusTop = statusTop,
            )

            1 -> SearchTopChrome(
                tabState = searchTabState,
                hasHistory = settingsState.history.isNotEmpty(),
                backdrop = topBackdrop,
                statusTop = statusTop,
            )

            else -> ProfileTopChrome(
                scroll = profileScrollState,
                backdrop = topBackdrop,
                statusTop = statusTop,
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .pointerInput(items.size) {
                    val slop = viewConfiguration.touchSlop
                    val maxDoubleTapMs = 400L
                    val menuMaxPx = 250.dp.toPx()
                    val sideReservePx = 168.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val downPos = down.position
                        var dragged = false
                        var upPos = downPos
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - downPos).getDistance() > slop) {
                                dragged = true
                            }
                            upPos = change.position
                            if (!change.pressed) break
                        }
                        if (!dragged) {
                            val menuWidthPx = minOf(
                                (size.width - sideReservePx).coerceAtLeast(1f),
                                menuMaxPx,
                            )
                            val relX = (upPos.x - (size.width - menuWidthPx) * 0.5f) / menuWidthPx
                            if (relX in 0f..1f) {
                                val tappedTab = (relX * items.size).toInt().coerceIn(0, items.lastIndex)
                                val current = latestPage
                                if (tappedTab == current) {
                                    val now = SystemClock.uptimeMillis()
                                    if (lastTapTab == current && now - lastTapAt <= maxDoubleTapMs) {
                                        lastTapAt = 0L
                                        lastTapTab = -1
                                        scrollToTopTicks[current] = scrollToTopTicks[current] + 1
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else {
                                        lastTapAt = now
                                        lastTapTab = current
                                    }
                                } else {
                                    lastTapAt = 0L
                                    lastTapTab = -1
                                }
                            }
                        }
                    }
                },
        ) {
            NavigationBar(
                backdrop = bottomBackdrop,
                items = items,
                selectedIndex = page,
                onSelection = { page = it },
            )
        }
    }
}

private const val BACKDROP_SCROLL_CAPTURE_INTERVAL_NS = 22_000_000L

private class BackdropSnapController {
    var lastCaptureNs: Long = 0L
    var lastPage: Int = -1
    var forceNextCapture: Boolean = true
}

/** PID / author links open directly; everything else becomes a keyword or tag search. */
private fun searchRoute(query: String, mode: SearchMode): Route? =
    when (val intent = SearchIntentParser.parse(query)) {
        is SearchIntent.Direct -> when (intent.kind) {
            SearchIntent.Kind.NOVEL -> Route.Detail("novel", intent.id)
            SearchIntent.Kind.AUTHOR -> Route.Author(intent.id)
            else -> Route.Detail("illust", intent.id)
        }

        is SearchIntent.Text -> Route.SearchResults(
            intent.query,
            if (mode == SearchMode.TAG) "tag" else "keyword",
        )

        null -> null
    }
