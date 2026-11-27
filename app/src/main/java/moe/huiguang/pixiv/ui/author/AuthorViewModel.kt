package moe.huiguang.pixiv.ui.author

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.UserProfile
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.SettingsStore

private const val AUTHOR_PAGE_SIZE = 18

@Immutable
internal data class AuthorCategoryState(
    val items: List<SearchItem> = emptyList(),
    val loading: Boolean = false,
    val ended: Boolean = false,
)

@Immutable
internal data class AuthorUiState(
    val profile: UserProfile? = null,
    val bookmarksCount: Int? = null,
    val error: Boolean = false,
    val categories: List<AuthorCategoryState> = List(3) { AuthorCategoryState() },
)

/**
 * Holds author profile and per-category works streams (illusts / mangas / novels) across
 * pager tab switches and configuration changes, preventing redundant reloads on tab changes.
 */
internal class AuthorViewModel(
    private val userId: String,
    private val api: PixivApi,
    private val engagement: EngagementRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorUiState())
    val uiState: StateFlow<AuthorUiState> = _uiState.asStateFlow()

    private var initialized = false
    private val loadingFlags = BooleanArray(3)

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        loadProfileAndBookmarks(recordHistory = true)
        loadMore(0)
        loadMore(1)
        loadMore(2)
    }

    fun refreshOnResume() {
        if (!initialized) return
        loadProfileAndBookmarks(recordHistory = false)
    }

    fun loadMore(categoryIndex: Int) {
        if (categoryIndex !in 0..2) return
        val current = _uiState.value.categories[categoryIndex]
        if (loadingFlags[categoryIndex] || current.loading || current.ended) return
        loadingFlags[categoryIndex] = true
        updateCategory(categoryIndex) { it.copy(loading = true) }

        viewModelScope.launch {
            try {
                val offset = _uiState.value.categories[categoryIndex].items.size
                val page = when (categoryIndex) {
                    1 -> api.userArtworkItemsPage(userId, SearchCategory.MANGA, offset = offset, limit = AUTHOR_PAGE_SIZE)
                    2 -> api.userNovelWorksPage(userId, offset = offset, limit = AUTHOR_PAGE_SIZE)
                    else -> api.userArtworkItemsPage(userId, SearchCategory.ILLUST, offset = offset, limit = AUTHOR_PAGE_SIZE)
                }
                updateCategory(categoryIndex) { prev ->
                    val known = prev.items.mapTo(HashSet(prev.items.size)) { it.id }
                    val fresh = page.filter { it.id !in known }
                    val reachedEnd = page.size < AUTHOR_PAGE_SIZE || fresh.isEmpty()
                    prev.copy(
                        items = prev.items + fresh,
                        loading = false,
                        ended = reachedEnd,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                updateCategory(categoryIndex) { prev ->
                    prev.copy(
                        loading = false,
                        ended = if (prev.items.isEmpty()) true else prev.ended,
                    )
                }
            } finally {
                loadingFlags[categoryIndex] = false
            }
        }
    }

    private fun loadProfileAndBookmarks(recordHistory: Boolean) {
        viewModelScope.launch {
            val loaded = runCatching { api.user(userId) }.getOrNull()
            if (loaded != null) {
                engagement.syncFollow(userId, loaded.isFollowed)
                if (recordHistory) {
                    settings.addHistory(
                        HistoryEntry(
                            pid = loaded.id,
                            kind = HistoryEntry.KIND_AUTHOR,
                            title = loaded.name,
                            thumbUrl = loaded.avatarUrl,
                            at = System.currentTimeMillis(),
                            userName = loaded.name,
                            illustType = -1,
                        ),
                    )
                }
                _uiState.update { it.copy(profile = loaded, error = false) }
            } else {
                _uiState.update { state ->
                    if (state.profile == null) state.copy(error = true) else state
                }
            }
        }
        viewModelScope.launch {
            val total = runCatching { api.userBookmarksTotal(userId) }.getOrNull()
            if (total != null) {
                _uiState.update { it.copy(bookmarksCount = total) }
            }
        }
    }

    private inline fun updateCategory(index: Int, transform: (AuthorCategoryState) -> AuthorCategoryState) {
        _uiState.update { state ->
            val updated = state.categories.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(categories = updated)
        }
    }
}
