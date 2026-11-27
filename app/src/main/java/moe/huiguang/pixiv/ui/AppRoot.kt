package moe.huiguang.pixiv.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val pageBackdrop = rememberCouiBackdrop()
    val flatEffectBackdrop = rememberCouiBackdrop()
    val contentPadding = PaddingValues(top = statusTop + 64.dp, bottom = navBottom + 88.dp)
    val horizontalInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
    val haptics = LocalHapticFeedback.current
    val latestPage by rememberUpdatedState(page)
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var lastTapTab by remember { mutableIntStateOf(-1) }
    val open: (Route) -> Unit = { route -> PageActivity.open(context, route) }

    LaunchedEffect(page) {
        if (page != 1 && searchTabState.searching) {
            searchTabState.searching = false
        }
    }
    BackHandler(page == 1 && searchTabState.searching) {
        searchTabState.searching = false
        searchTabState.query = ""
    }

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

    // Single pageBackdrop sibling for TopChrome and NavigationBar relying on COUI's
    // native in-window RenderNode dependency tracking, paired with a static flat
    // sibling backdrop for static chrome buttons (e.g. HomeTopChrome's More button).
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
                .fillMaxSize()
                .couiBackdrop(pageBackdrop)
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
                backdrop = pageBackdrop,
                effectBackdrop = flatEffectBackdrop,
                statusTop = statusTop,
            )

            1 -> SearchTopChrome(
                tabState = searchTabState,
                hasHistory = settingsState.history.isNotEmpty(),
                backdrop = pageBackdrop,
                statusTop = statusTop,
                onSubmit = { query, mode -> searchRoute(query, mode)?.let(open) },
            )

            else -> ProfileTopChrome(
                scroll = profileScrollState,
                backdrop = pageBackdrop,
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
                backdrop = pageBackdrop,
                items = items,
                selectedIndex = page,
                onSelection = { page = it },
            )
        }
    }
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
