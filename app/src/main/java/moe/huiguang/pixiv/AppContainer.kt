package moe.huiguang.pixiv

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.size.Precision
import okio.Path.Companion.toOkioPath
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.FavoriteStore
import moe.huiguang.pixiv.data.store.HomeFeedCache
import moe.huiguang.pixiv.data.store.ReadingStore
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.data.store.SharedPreferencesStore
import moe.huiguang.pixiv.ui.detail.ArtworkCache
import moe.huiguang.pixiv.ui.novel.NovelCache
import moe.huiguang.pixiv.ui.profile.SelfProfileCache
import moe.huiguang.pixiv.ui.search.SearchResultsCache

/**
 * Manual dependency container — no Hilt, no KSP.
 *
 * Everything is created once per process. Stores are constructed first so that
 * [SettingsStore] can publish the saved cookie to `SessionHolder` before the
 * API client issues its first request.
 */
class AppContainer(context: Context) {

    private val app: Context = context.applicationContext

    private val mainPrefs = app.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
    private val readingPrefs = app.getSharedPreferences(PREFS_READING, Context.MODE_PRIVATE)
    private val mainStore = CookieEncryptingKeyValueStore(SharedPreferencesStore(mainPrefs))

    val settings: SettingsStore = SettingsStore(mainStore)
    val reading: ReadingStore = ReadingStore(SharedPreferencesStore(readingPrefs))

    val api: PixivApi = PixivApi(debugLogging = BuildConfig.DEBUG)
    val downloads: ImageDownloader = ImageDownloader(
        client = api.client,
        downloadTreeUriProvider = { settings.current.downloadTreeUri },
    )

    /** Keeps the last few search result pages so rotation never refetches them. */
    val searchCache: SearchResultsCache = SearchResultsCache()

    /** Last home feed on disk, so a cold start is not an empty screen. */
    val homeFeedCache: HomeFeedCache = HomeFeedCache(SharedPreferencesStore(mainPrefs))

    /** Recently opened works, so detail ↔ viewer transitions never refetch. */
    val artworkCache: ArtworkCache = ArtworkCache()

    /** Cached profile card for the signed-in account. */
    val selfProfileCache: SelfProfileCache = SelfProfileCache()

    /** On-device favourites fallback/cache. */
    val favorites: FavoriteStore = FavoriteStore(SharedPreferencesStore(mainPrefs))

    /** Single write gateway for follow/bookmark actions with optimistic UI & rollback. */
    val engagement: EngagementRepository = EngagementRepository(api, favorites)

    /** Recently opened novels, shared by the detail page and the reader. */
    val novelCache: NovelCache = NovelCache()

    /** Coil shares the API client, which is what makes `i.pximg.net` accept us. */
    val imageLoader: ImageLoader = ImageLoader.Builder(app)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { api.client }))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(app, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(app.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()

    /** Drops in-memory/disk caches but keeps session, settings, history and favourites. */
    fun clearCaches() {
        searchCache.clear()
        homeFeedCache.clear()
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        runCatching { app.cacheDir.resolve("updates").deleteRecursively() }
    }

    /** Clears stored session, WebView cookies/storage, and session-scoped caches. */
    fun logout() {
        settings.logout()
        favorites.clear()
        engagement.clear()
        api.clearCsrfCache()
        api.clearSeriesCache()
        selfProfileCache.clear()
        artworkCache.clear()
        novelCache.clear()
        searchCache.clear()
        homeFeedCache.clear()
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        runCatching { app.cacheDir.resolve("updates").deleteRecursively() }
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
        }
    }

    private companion object {
        const val PREFS_MAIN = "huiguang"
        const val PREFS_READING = "huiguang_reading"
    }
}
