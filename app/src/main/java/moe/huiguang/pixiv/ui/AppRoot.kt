package moe.huiguang.pixiv.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Scaffold
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
import moe.huiguang.pixiv.ui.profile.ProfileTab
import moe.huiguang.pixiv.ui.search.SearchTab

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
    val pageStates = rememberSaveableStateHolder()
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Each tab draws its own floating blurred header (see FloatingTabHeader),
        // so the shell does not reserve a top bar slot.
        topBar = {},
        bottomBar = {
            // A little breathing room so the floating capsule's shadow and
            // rounded corners are not clipped by the Scaffold slot.
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                NavigationBar(
                    items = items,
                    selectedIndex = page,
                    onSelection = { page = it },
                )
            }
        },
    ) { padding ->
        pageStates.SaveableStateProvider(page) {
            when (page) {
                0 -> HomeScreen(
                    padding = padding,
                    api = container.api,
                    settings = container.settings,
                    cache = container.homeFeedCache,
                    onOpenWork = { item -> open(Route.Detail(kindOf(item), item.id)) },
                    onOpenPixivision = { articleId -> open(Route.Pixivision(articleId)) },
                    onLogin = { open(Route.Login) },
                )

                1 -> SearchTab(
                    padding = padding,
                    settings = container.settings,
                    onSubmit = { query, mode -> searchRoute(query, mode)?.let(open) },
                    onOpenHistory = { entry -> open(entry.toRoute()) },
                )

                else -> ProfileTab(
                    padding = padding,
                    settings = container.settings,
                    api = container.api,
                    profileCache = container.selfProfileCache,
                    onLogin = { open(Route.Login) },
                    onOpenSettings = { open(Route.Settings) },
                    onOpenHistory = { open(Route.History) },
                    onOpenBookmarks = { open(Route.Bookmarks) },
                    onOpenProfileList = { uid, kind -> open(Route.ProfileList(uid, kind)) },
                )
            }
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
