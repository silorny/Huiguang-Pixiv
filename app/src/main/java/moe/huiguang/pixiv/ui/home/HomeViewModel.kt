package moe.huiguang.pixiv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.data.model.HomeFeed
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.HomeFeedCache

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/**
 * Holds the home feed for the Activity lifetime, mirrors it to disk via
 * [HomeFeedCache], and paginates each (section, category) stream on demand so
 * the waterfall scrolls seamlessly without hitting a hard item cap.
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

    private val _loadingMore = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val loadingMore: StateFlow<Set<Pair<Int, Int>>> = _loadingMore.asStateFlow()

    private val _endReached = MutableStateFlow<Set<Pair<Int, Int>>>(emptySet())
    val endReached: StateFlow<Set<Pair<Int, Int>>> = _endReached.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val pageByBucket = HashMap<Pair<Int, Int>, Int>()
    private val dryBatches = HashMap<Pair<Int, Int>, Int>()

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
        _endReached.value = emptySet()
        load(manual = true)
    }

    /** Appends the next page for ([section], [category]) when the grid reaches bottom. */
    fun loadMore(section: Int, category: Int) {
        val current = (_state.value as? HomeUiState.Content)?.feed ?: return
        val bucket = section to category
        if (bucket in _loadingMore.value || bucket in _endReached.value) return

        val nextPage = (pageByBucket[bucket] ?: 1) + 1
        _loadingMore.value = _loadingMore.value + bucket

        viewModelScope.launch {
            val result = runCatching { api.homeSectionMore(section, category, nextPage) }
            result.onSuccess { fetched ->
                val latest = (_state.value as? HomeUiState.Content)?.feed ?: current
                val targetList = when (section) {
                    1 -> latest.ranking
                    2 -> latest.following
                    else -> latest.recommended
                }
                val known = targetList.mapTo(HashSet()) { it.category to it.id }
                val fresh = fetched.filter { (it.category to it.id) !in known }
                if (fresh.isNotEmpty()) {
                    dryBatches[bucket] = 0
                    pageByBucket[bucket] = nextPage
                    val updated = when (section) {
                        1 -> latest.copy(ranking = latest.ranking + fresh)
                        2 -> latest.copy(following = latest.following + fresh)
                        else -> latest.copy(recommended = latest.recommended + fresh)
                    }
                    _state.value = HomeUiState.Content(updated)
                } else {
                    val misses = (dryBatches[bucket] ?: 0) + 1
                    dryBatches[bucket] = misses
                    // Ranking/following pages end when empty; discovery can overlap once before stopping.
                    if (fetched.isEmpty() || misses >= 2 || section != 0) {
                        _endReached.value = _endReached.value + bucket
                    }
                }
            }.onFailure {
                // Keep existing items; allow user/scroll to retry later without marking ended.
            }
            _loadingMore.value = _loadingMore.value - bucket
        }
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
    }
}
