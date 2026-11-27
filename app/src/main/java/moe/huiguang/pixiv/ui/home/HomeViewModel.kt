package moe.huiguang.pixiv.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        cache.load(initialOwner)?.let { HomeUiState.Content(it.deduplicated()) } ?: HomeUiState.Loading,
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
    private var loadJob: Job? = null
    private val bucketJobs = HashMap<Pair<Int, Int>, Job>()
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
        loadJob?.cancel()
        bucketJobs.values.forEach { it.cancel() }
        bucketJobs.clear()
        pageByBucket.clear()
        dryBatches.clear()
        discoveryFallbackCursor = 0
        _loadingMore.value = emptySet()
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
        bucketJobs[bucket] = viewModelScope.launch {
            runCatching { api.homeSectionMore(section = 1, category = tab, page = 1) }
                .onSuccess { fetched ->
                    val unique = fetched.distinctBy { it.category to it.id }
                    if (BuildConfig.DEBUG) {
                        Log.d("HuiguangHome", "ranking tab=$tab page=1 count=${unique.size}")
                    }
                    _rankingBuckets.value = _rankingBuckets.value + (tab to unique)
                    pageByBucket[bucket] = 1
                    if (unique.isEmpty()) _endReached.value = _endReached.value + bucket
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

        bucketJobs[bucket] = viewModelScope.launch {
            if (section == 1) {
                val result = runCatching {
                    fetchSectionBatch(section = 1, category = category, nextPage = nextPage, feed = current)
                }
                result.onSuccess { fetched ->
                    val latest = (_state.value as? HomeUiState.Content)?.feed ?: current
                    val targetList = if (category == 0) latest.ranking else _rankingBuckets.value[category].orEmpty()
                    val fresh = filterFresh(targetList, fetched)
                    if (fresh.isNotEmpty()) {
                        dryBatches[bucket] = 0
                        pageByBucket[bucket] = nextPage
                        if (category != 0) {
                            _rankingBuckets.value = _rankingBuckets.value + (category to (targetList + fresh))
                        } else {
                            _state.value = HomeUiState.Content(latest.copy(ranking = latest.ranking + fresh))
                        }
                    } else {
                        val misses = (dryBatches[bucket] ?: 0) + 1
                        dryBatches[bucket] = misses
                        if (fetched.isEmpty() || misses >= 3) {
                            _endReached.value = _endReached.value + bucket
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    _loadMoreErrors.value = _loadMoreErrors.value + bucket
                }
            } else {
                val showIllust = (category and HOME_FILTER_ILLUST) != 0
                val showManga = (category and HOME_FILTER_MANGA) != 0
                val showNovel = (category and HOME_FILTER_NOVEL) != 0
                val wantsArtwork = showIllust || showManga
                val artworkApiCategory = when {
                    showIllust && showManga -> 0
                    showIllust -> 1
                    else -> 2
                }
                val result = runCatching {
                    coroutineScope {
                        val artworkJob = if (wantsArtwork) {
                            async { fetchSectionBatch(section, artworkApiCategory, nextPage, current) }
                        } else {
                            null
                        }
                        val novelJob = if (showNovel) {
                            async { api.homeSectionMore(section, 3, nextPage) }
                        } else {
                            null
                        }
                        (artworkJob?.await().orEmpty()) to (novelJob?.await().orEmpty())
                    }
                }
                result.onSuccess { (fetchedArtworks, fetchedNovels) ->
                    val latest = (_state.value as? HomeUiState.Content)?.feed ?: current
                    val existingArtworks = if (section == 2) latest.following else latest.recommended
                    val existingNovels = if (section == 2) latest.novelFollowing else latest.novelRecommended

                    var freshArtworks = if (wantsArtwork) filterFresh(existingArtworks, fetchedArtworks) else emptyList()
                    if (wantsArtwork && freshArtworks.isEmpty() && section == 0) {
                        val supplement = runCatching { fetchDiscoverySupplement(latest) }.getOrDefault(emptyList())
                        val filteredSupplement = when {
                            showIllust && !showManga -> supplement.filter { it.category == SearchCategory.ILLUST }
                            showManga && !showIllust -> supplement.filter { it.category == SearchCategory.MANGA }
                            else -> supplement
                        }
                        freshArtworks = filterFresh(existingArtworks, filteredSupplement)
                    }
                    val freshNovels = if (showNovel) filterFresh(existingNovels, fetchedNovels) else emptyList()

                    if (freshArtworks.isNotEmpty() || freshNovels.isNotEmpty()) {
                        dryBatches[bucket] = 0
                        pageByBucket[bucket] = nextPage
                        val updated = if (section == 2) {
                            latest.copy(
                                following = latest.following + freshArtworks,
                                novelFollowing = latest.novelFollowing + freshNovels,
                            )
                        } else {
                            latest.copy(
                                recommended = latest.recommended + freshArtworks,
                                novelRecommended = latest.novelRecommended + freshNovels,
                            )
                        }
                        _state.value = HomeUiState.Content(updated)
                    } else {
                        val misses = (dryBatches[bucket] ?: 0) + 1
                        dryBatches[bucket] = misses
                        val emptyFetch = fetchedArtworks.isEmpty() && fetchedNovels.isEmpty()
                        if (emptyFetch || misses >= 3 || section != 0) {
                            _endReached.value = _endReached.value + bucket
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    _loadMoreErrors.value = _loadMoreErrors.value + bucket
                }
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
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Loading
            val result = runCatching { (api.homeFeed() ?: api.fallbackFeed()).deduplicated() }
            result.onSuccess { feed ->
                withContext(Dispatchers.IO) {
                    cache.save(owner, feed)
                }
                _recommendedHeadCutoff.value = feed.recommended.size
                _followingHeadCutoff.value = feed.following.size
                _state.value = HomeUiState.Content(feed)
            }.onFailure { error ->
                if (error is CancellationException) throw error
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
            return fetched.filter { known.add(it.category to it.id) }
        }

        fun HomeFeed.deduplicated(): HomeFeed = copy(
            recommended = recommended.distinctBy { it.category to it.id },
            ranking = ranking.distinctBy { it.category to it.id },
            following = following.distinctBy { it.category to it.id },
            novelRecommended = novelRecommended.distinctBy { it.category to it.id },
            novelFollowing = novelFollowing.distinctBy { it.category to it.id },
            pixivision = pixivision.distinctBy { it.id },
        )
    }
}
