package moe.huiguang.pixiv.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.BuildConfig
import moe.huiguang.pixiv.data.model.HomeFeed
import moe.huiguang.pixiv.data.model.RankingMode
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.SearchMode
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.HomeFeedCache

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/**
 * Holds the home feed for the Activity lifetime, mirrors it to disk via
 * [HomeFeedCache], manages ranking dimension streams (`rankingBuckets`),
 * and paginates each (section, category) stream on demand so the waterfall
 * scrolls seamlessly across illustrations, manga, novels and ranking tabs.
 */
class HomeViewModel(
    private val api: PixivApi,
    private val cache: HomeFeedCache,
    initialOwner: String?,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(
        cache.load(initialOwner)?.let { HomeUiState.Content(it) } ?: HomeUiState.Loading,
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Extra ranking tabs (1=weekly, 2=monthly, 3=rookie, 4=illust, 5=manga); tab 0 uses feed.ranking. */
    private val _rankingBuckets = MutableStateFlow<Map<Int, List<SearchItem>>>(emptyMap())
    val rankingBuckets: StateFlow<Map<Int, List<SearchItem>>> = _rankingBuckets.asStateFlow()

    private val _rankingErrors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val rankingErrors: StateFlow<Map<Int, String>> = _rankingErrors.asStateFlow()

    private val _loadingMore = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val loadingMore: StateFlow<Set<Pair<Int, Int>>> = _loadingMore.asStateFlow()

    private val _loadMoreErrors = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val loadMoreErrors: StateFlow<Set<Pair<Int, Int>>> = _loadMoreErrors.asStateFlow()

    private val _endReached = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val endReached: StateFlow<Set<Pair<Int, Int>>> = _endReached.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * Initial artwork count per feed snapshot so interleaved `全部` streams keep a fixed
     * prefix and subsequent `loadMore` batches strictly append at the tail (preventing
     * cards from jumping mid-scroll).
     */
    private val _recommendedHeadCutoff = MutableStateFlow(
        (_state.value as? HomeUiState.Content)?.feed?.recommended?.size ?: 0,
    )
    val recommendedHeadCutoff: StateFlow<Int> = _recommendedHeadCutoff.asStateFlow()

    private val _followingHeadCutoff = MutableStateFlow(
        (_state.value as? HomeUiState.Content)?.feed?.following?.size ?: 0,
    )
    val followingHeadCutoff: StateFlow<Int> = _followingHeadCutoff.asStateFlow()

    private val pageByBucket = HashMap<Pair<Int, Int>, Int>()
    private val dryBatches = HashMap<Pair<Int, Int>, Int>()
    private var discoveryFallbackCursor = 0

    private var loadedKey: String? = null
    private var owner: String? = initialOwner
    private var inFlight = false
    /** A cached feed paints instantly, then refreshes once per process. */
    private var refreshPending = _state.value is HomeUiState.Content

    /** Loads once per account; a no-op while content for the same key exists. */
    fun ensureLoaded(sessionId: String?) {
        owner = sessionId
        val key = sessionId ?: GUEST
        if (loadedKey == key && _state.value is HomeUiState.Content) {
            if (refreshPending) {
                refreshPending = false
                load()
            }
            return
        }
        if (inFlight) return
        load()
    }

    fun refresh(sessionId: String?) {
        owner = sessionId
        refreshPending = false
        pageByBucket.clear()
        dryBatches.clear()
        discoveryFallbackCursor = 0
        _rankingBuckets.value = emptyMap()
        _rankingErrors.value = emptyMap()
        _loadMoreErrors.value = emptySet()
        _endReached.value = emptySet()
        load(manual = true)
    }

    /** Loads page 1 on demand for non-default ranking tabs (`1..5`). */
    fun ensureRankingTab(tab: Int, forceRetry: Boolean = false) {
        if (tab == 0 || (tab in _rankingBuckets.value && !forceRetry)) return
        val bucket = 1 to tab
        if (bucket in _loadingMore.value || (!forceRetry && bucket in _endReached.value)) return
        _rankingErrors.value = _rankingErrors.value - tab
        if (forceRetry) _endReached.value = _endReached.value - bucket
        _loadingMore.value = _loadingMore.value + bucket
        viewModelScope.launch {
            runCatching { api.homeSectionMore(section = 1, category = tab, page = 1) }
                .onSuccess { fetched ->
                    if (BuildConfig.DEBUG) {
                        Log.d("HuiguangHome", "ranking tab=$tab page=1 count=${fetched.size}")
                    }
                    _rankingBuckets.value = _rankingBuckets.value + (tab to fetched)
                    pageByBucket[bucket] = 1
                    if (fetched.isEmpty()) _endReached.value = _endReached.value + bucket
                }
                .onFailure { e ->
                    if (BuildConfig.DEBUG) {
                        Log.w("HuiguangHome", "ranking tab=$tab failed", e)
                    }
                    _rankingErrors.value = _rankingErrors.value + (tab to (e.message ?: e.javaClass.simpleName))
                }
            _loadingMore.value = _loadingMore.value - bucket
        }
    }

    /** Appends the next page for ([section], [category]) when the grid approaches bottom. */
    fun loadMore(section: Int, category: Int, retryFromError: Boolean = false) {
        val current = (_state.value as? HomeUiState.Content)?.feed ?: return
        val bucket = section to category
        if (bucket in _loadingMore.value || bucket in _endReached.value) return
        if (!retryFromError && bucket in _loadMoreErrors.value) return
        if (section == 1 && category != 0 && category !in _rankingBuckets.value) {
            ensureRankingTab(category)
            return
        }

        _loadMoreErrors.value = _loadMoreErrors.value - bucket
        val nextPage = (pageByBucket[bucket] ?: 1) + 1
        _loadingMore.value = _loadingMore.value + bucket

        viewModelScope.launch {
            val result = runCatching {
                fetchSectionBatch(section, category, nextPage, current)
            }
            result.onSuccess { fetched ->
                val latest = (_state.value as? HomeUiState.Content)?.feed ?: current
                val targetList = when {
                    section == 1 && category == 0 -> latest.ranking
                    section == 1 -> _rankingBuckets.value[category].orEmpty()
                    section == 2 && category == 3 -> latest.novelFollowing
                    section == 2 -> latest.following
                    category == 3 -> latest.novelRecommended
                    else -> latest.recommended
                }
                var fresh = filterFresh(targetList, fetched)
                // If Pixiv discovery repeats existing items, seamlessly draw from tags/rankings
                // so endless browsing in Home Recommended doesn't stall early.
                if (fresh.isEmpty() && section == 0 && category != 3) {
                    val supplement = runCatching { fetchDiscoverySupplement(latest) }.getOrDefault(emptyList())
                    fresh = filterFresh(targetList, supplement)
                }
                if (fresh.isNotEmpty()) {
                    dryBatches[bucket] = 0
                    pageByBucket[bucket] = nextPage
                    if (section == 1 && category != 0) {
                        _rankingBuckets.value = _rankingBuckets.value + (category to (targetList + fresh))
                    } else {
                        val updated = when {
                            section == 1 -> latest.copy(ranking = latest.ranking + fresh)
                            section == 2 && category == 3 -> latest.copy(novelFollowing = latest.novelFollowing + fresh)
                            section == 2 -> latest.copy(following = latest.following + fresh)
                            category == 3 -> latest.copy(novelRecommended = latest.novelRecommended + fresh)
                            else -> latest.copy(recommended = latest.recommended + fresh)
                        }
                        _state.value = HomeUiState.Content(updated)
                    }
                } else {
                    val misses = (dryBatches[bucket] ?: 0) + 1
                    dryBatches[bucket] = misses
                    if (fetched.isEmpty() || misses >= 3 || section != 0) {
                        _endReached.value = _endReached.value + bucket
                    }
                }
            }.onFailure {
                _loadMoreErrors.value = _loadMoreErrors.value + bucket
            }
            _loadingMore.value = _loadingMore.value - bucket
        }
    }

    private suspend fun fetchSectionBatch(
        section: Int,
        category: Int,
        nextPage: Int,
        feed: HomeFeed,
    ): List<SearchItem> {
        val raw = api.homeSectionMore(section, category, nextPage)
        // Lock the category match on filtered streams so filtered tabs actually get matching items.
        return when {
            section == 0 && category == 1 -> raw.filter { it.category == SearchCategory.ILLUST }
            section == 0 && category == 2 -> {
                val manga = raw.filter { it.category == SearchCategory.MANGA }
                if (manga.isNotEmpty()) manga else {
                    val tag = feed.tags.getOrNull(nextPage % feed.tags.size.coerceAtLeast(1)) ?: "漫画"
                    api.search(tag, SearchMode.TAG, page = nextPage, limit = 30)
                        .filter { it.category == SearchCategory.MANGA }
                }
            }
            section == 2 && category == 1 -> raw.filter { it.category == SearchCategory.ILLUST }
            section == 2 && category == 2 -> raw.filter { it.category == SearchCategory.MANGA }
            else -> raw
        }
    }

    private suspend fun fetchDiscoverySupplement(feed: HomeFeed): List<SearchItem> {
        val poolTags = (feed.tags + DEFAULT_DISCOVERY_TAGS).distinct()
        val cursor = discoveryFallbackCursor++
        val batch = if (cursor % 3 == 0) {
            val mode = if ((cursor / 3) % 2 == 0) RankingMode.WEEKLY else RankingMode.MONTHLY
            val page = (cursor / 6) + 1
            api.rankingPage(page = page, category = null, mode = mode)
        } else {
            val tag = poolTags[cursor % poolTags.size]
            val page = (cursor / poolTags.size) + 1
            api.search(tag, SearchMode.TAG, page = page, limit = 30)
        }
        return batch.filter { it.category != SearchCategory.NOVEL }
    }

    private fun load(manual: Boolean = false) {
        loadedKey = owner ?: GUEST
        refreshPending = false
        inFlight = true
        if (manual) _refreshing.value = true
        val key = loadedKey
        viewModelScope.launch {
            if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Loading
            val result = runCatching { api.homeFeed() ?: api.fallbackFeed() }
            result.onSuccess { feed ->
                cache.save(owner, feed)
                _recommendedHeadCutoff.value = feed.recommended.size
                _followingHeadCutoff.value = feed.following.size
                _state.value = HomeUiState.Content(feed)
            }.onFailure { error ->
                if (_state.value !is HomeUiState.Content) {
                    _state.value = HomeUiState.Error(error.message.orEmpty())
                }
            }
            inFlight = false
            _refreshing.value = false
            if (key == loadedKey) loadedKey = key
        }
    }

    private companion object {
        const val GUEST = "guest"
        val DEFAULT_DISCOVERY_TAGS = listOf("オリジナル", "風景", "女の子", "創作", "ファンタジー", "初音ミク")

        fun filterFresh(existing: List<SearchItem>, fetched: List<SearchItem>): List<SearchItem> {
            val known = existing.mapTo(HashSet()) { it.category to it.id }
            return fetched.filter { (it.category to it.id) !in known }
        }
    }
}
