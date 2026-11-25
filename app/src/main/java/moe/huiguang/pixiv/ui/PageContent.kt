package moe.huiguang.pixiv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.huiguang.pixiv.AppContainer
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.nav.Route
import moe.huiguang.pixiv.ui.about.AboutDocScreen
import moe.huiguang.pixiv.ui.about.AboutScreen
import moe.huiguang.pixiv.ui.author.AuthorScreen
import moe.huiguang.pixiv.ui.bookmarks.BookmarksScreen
import moe.huiguang.pixiv.ui.common.PlaceholderScreen
import moe.huiguang.pixiv.ui.common.comingSoon
import moe.huiguang.pixiv.ui.detail.DetailScreen
import moe.huiguang.pixiv.ui.history.HistoryScreen
import moe.huiguang.pixiv.ui.login.LoginScreen
import moe.huiguang.pixiv.ui.novel.NovelDetailScreen
import moe.huiguang.pixiv.ui.pixivision.PixivisionScreen
import moe.huiguang.pixiv.ui.profile.ProfileListScreen
import moe.huiguang.pixiv.ui.reader.ReaderScreen
import moe.huiguang.pixiv.ui.search.SearchResultsScreen
import moe.huiguang.pixiv.ui.series.SeriesScreen
import moe.huiguang.pixiv.ui.settings.SettingsScreen
import moe.huiguang.pixiv.ui.settings.ShareFormatScreen
import moe.huiguang.pixiv.ui.viewer.ViewerScreen

/**
 * Maps a [Route] to its screen. This is the body of a [PageActivity]; it owns
 * no stack, so every action is either "open another page" or "finish".
 */
@Composable
fun PageContent(
    container: AppContainer,
    route: Route,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (route) {
            is Route.SearchResults -> SearchResultsScreen(
                query = route.query,
                mode = if (route.mode == "tag") SearchMode.TAG else SearchMode.KEYWORD,
                api = container.api,
                cache = container.searchCache,
                settings = container.settings,
                onBack = onBack,
                onOpenWork = { item -> onOpen(Route.Detail(kindOf(item), item.id)) },
                onOpenAuthor = { uid -> onOpen(Route.Author(uid)) },
                onLogin = { onOpen(Route.Login) },
            )

            is Route.Detail -> if (route.kind == "novel") {
                NovelDetailScreen(
                    novelId = route.id,
                    api = container.api,
                    cache = container.novelCache,
                    downloads = container.downloads,
                    engagement = container.engagement,
                    onBack = onBack,
                    onStartReading = { onOpen(Route.Reader(route.id)) },
                    onOpenAuthor = { uid -> onOpen(Route.Author(uid)) },
                    onOpenSeries = { seriesId, startId ->
                        onOpen(Route.Series("novel", seriesId, startId))
                    },
                    onOpenLinked = { kind, id -> onOpen(Route.Detail(kind, id)) },
                    onSearchTag = { tag -> onOpen(Route.SearchResults(tag, "tag")) },
                    onOpenAsIllust = { id -> onOpen(Route.Detail("illust", id)) },
                    settings = container.settings,
                )
            } else {
                DetailScreen(
                    workId = route.id,
                    api = container.api,
                    cache = container.artworkCache,
                    downloads = container.downloads,
                    engagement = container.engagement,
                    settings = container.settings,
                    onBack = onBack,
                    onOpenViewer = { page -> onOpen(Route.Viewer(route.id, page)) },
                    onOpenAuthor = { uid -> onOpen(Route.Author(uid)) },
                    onOpenSeries = { seriesId, startId ->
                        onOpen(Route.Series("illust", seriesId, startId))
                    },
                    onOpenLinked = { kind, id -> onOpen(Route.Detail(kind, id)) },
                    onSearchTag = { tag -> onOpen(Route.SearchResults(tag, "tag")) },
                    onOpenAsNovel = { id -> onOpen(Route.Detail("novel", id)) },
                )
            }

            is Route.Viewer -> ViewerScreen(
                workId = route.illustId,
                initialPage = route.page,
                api = container.api,
                cache = container.artworkCache,
                downloads = container.downloads,
                onBack = onBack,
            )

            is Route.Reader -> ReaderScreen(
                novelId = route.novelId,
                api = container.api,
                cache = container.novelCache,
                settings = container.settings,
                reading = container.reading,
                downloads = container.downloads,
                onBack = onBack,
                onOpenEpisode = { id -> onOpen(Route.Reader(id)) },
                onOpenSeries = { seriesId, startId ->
                    onOpen(Route.Series("novel", seriesId, startId))
                },
            )

            is Route.Series -> SeriesScreen(
                kind = route.kind,
                seriesId = route.seriesId,
                startId = route.startId,
                api = container.api,
                onBack = onBack,
                onOpen = { link ->
                    if (route.kind == "novel") {
                        onOpen(Route.Reader(link.id))
                    } else {
                        onOpen(Route.Detail("illust", link.id))
                    }
                },
            )

            is Route.Author -> AuthorScreen(
                userId = route.id,
                api = container.api,
                engagement = container.engagement,
                onBack = onBack,
                onOpenWork = { kind, id -> onOpen(Route.Detail(kind, id)) },
                settings = container.settings,
            )

            is Route.Pixivision -> PixivisionScreen(
                articleId = route.id,
                api = container.api,
                engagement = container.engagement,
                settings = container.settings,
                onBack = onBack,
                onOpenWork = { kind, id -> onOpen(Route.Detail(kind, id)) },
                onOpenAuthor = { uid -> onOpen(Route.Author(uid)) },
                onSearchTag = { tag -> onOpen(Route.SearchResults(tag, "tag")) },
            )

            is Route.ProfileList -> ProfileListScreen(
                uid = route.uid,
                kind = route.kind,
                api = container.api,
                onBack = onBack,
                onOpenAuthor = { uid -> onOpen(Route.Author(uid)) },
                onOpenWork = { kind, id -> onOpen(Route.Detail(kind, id)) },
            )

            Route.Login -> LoginScreen(
                api = container.api,
                settingsStore = container.settings,
                onBack = onBack,
                onLoggedIn = {
                    container.engagement.clear()
                    onBack()
                },
            )

            Route.Settings -> SettingsScreen(
                settings = container.settings,
                onBack = onBack,
                onOpenAbout = { onOpen(Route.About) },
                onClearCaches = { container.clearCaches() },
                onOpenShareFormat = { onOpen(Route.ShareFormat) },
                onLoggedOut = { container.logout() },
            )

            Route.ShareFormat -> ShareFormatScreen(
                settings = container.settings,
                onBack = onBack,
            )

            Route.History -> HistoryScreen(
                settings = container.settings,
                onBack = onBack,
                onOpen = { entry -> onOpen(entry.toRoute()) },
            )

            Route.Bookmarks -> BookmarksScreen(
                api = container.api,
                settings = container.settings,
                favorites = container.favorites,
                onBack = onBack,
                onOpen = { kind, id ->
                    onOpen(Route.Detail(if (kind == HistoryEntry.KIND_NOVEL) "novel" else "illust", id))
                },
            )

            Route.About -> AboutScreen(
                onBack = onBack,
                onOpenDoc = { doc -> onOpen(Route.AboutDoc(doc)) },
                settings = container.settings,
            )

            is Route.AboutDoc -> AboutDocScreen(
                doc = route.doc,
                onBack = onBack,
                onOpenDoc = { nextDoc -> onOpen(Route.AboutDoc(nextDoc)) },
            )

            else -> PlaceholderScreen(
                title = stringResource(R.string.coming_soon),
                hint = comingSoon(),
                onBack = onBack,
            )
        }
    }
}

/** Maps a search hit to the detail route for its work type. */
internal fun kindOf(item: SearchItem): String = when (item.category) {
    SearchCategory.MANGA -> "manga"
    SearchCategory.NOVEL -> "novel"
    else -> "illust"
}

/** Opens a history row on the page that produced it. */
internal fun HistoryEntry.toRoute(): Route = when (kind) {
    HistoryEntry.KIND_AUTHOR -> Route.Author(pid)
    HistoryEntry.KIND_NOVEL -> Route.Detail("novel", pid)
    else -> Route.Detail("illust", pid)
}
