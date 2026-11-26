package moe.huiguang.pixiv.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.net.PixivException
import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.data.store.FavoriteStore
import java.util.concurrent.ConcurrentHashMap

enum class EngagementKind { FOLLOW, ILLUST, NOVEL }

sealed interface EngagementEvent {
    val kind: EngagementKind
    val id: String

    data class Succeeded(
        override val kind: EngagementKind,
        override val id: String,
        val active: Boolean,
    ) : EngagementEvent

    data class Failed(
        override val kind: EngagementKind,
        override val id: String,
        val reason: String? = null,
    ) : EngagementEvent
}

/**
 * Single write gateway for author follows and work bookmarks.
 *
 * Uses optimistic UI updates backed by a serial rate-limited queue:
 *  - Rapid taps on the same target coalesce to the latest desired state.
 *  - Writes are spaced by [minGapMs] and back off on HTTP 429.
 *  - Failures roll back the optimistic state and emit an [EngagementEvent.Failed].
 */
class EngagementRepository(
    private val api: PixivApi,
    private val favorites: FavoriteStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {

    private data class WriteTask(
        val key: String,
        val kind: EngagementKind,
        val id: String,
        val previousState: Boolean,
        val targetState: Boolean,
        val entry: FavoriteEntry? = null,
        val epoch: Long = 0L,
    )

    private val _followedAuthors = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val followedAuthors: StateFlow<Map<String, Boolean>> = _followedAuthors.asStateFlow()

    private val _bookmarkedIllusts = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val bookmarkedIllusts: StateFlow<Map<String, Boolean>> = _bookmarkedIllusts.asStateFlow()

    private val _bookmarkedNovels = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val bookmarkedNovels: StateFlow<Map<String, Boolean>> = _bookmarkedNovels.asStateFlow()

    private val _events = MutableSharedFlow<EngagementEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<EngagementEvent> = _events.asSharedFlow()

    private val bookmarkIds = ConcurrentHashMap<String, String>()
    private val queueMutex = Mutex()
    private val pendingQueue = ArrayDeque<WriteTask>()
    private val activeKeys = ConcurrentHashMap.newKeySet<String>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var lastRequestAtMs = 0L

    @Volatile
    private var cooldownUntilMs = 0L

    @Volatile
    private var sessionEpoch = 0L

    init {
        scope.launch { processLoop() }
    }

    fun isFollowed(userId: String): Boolean =
        _followedAuthors.value[userId] ?: false

    fun isBookmarked(id: String, novel: Boolean): Boolean =
        if (novel) _bookmarkedNovels.value[id] == true else _bookmarkedIllusts.value[id] == true

    fun bookmarkIdOf(id: String, novel: Boolean): String? =
        bookmarkIds[bookmarkKey(id, novel)]

    fun syncFollow(userId: String, followed: Boolean) {
        if (userId.isEmpty()) return
        val key = "follow:$userId"
        if (key in activeKeys) return
        _followedAuthors.update { it + (userId to followed) }
        favorites.setFollowed(userId, followed)
    }

    fun syncBookmark(id: String, novel: Boolean, bookmarkId: String?, entry: FavoriteEntry? = null) {
        if (id.isEmpty()) return
        val key = bookmarkKey(id, novel)
        if (key in activeKeys) return
        val active = !bookmarkId.isNullOrEmpty()
        if (active && bookmarkId != null) {
            bookmarkIds[key] = bookmarkId
        } else {
            bookmarkIds.remove(key)
        }
        updateBookmarkState(id, novel, active)
        if (entry != null) {
            favorites.setBookmarked(entry, active)
        }
    }

    fun toggleFollow(userId: String) {
        if (userId.isEmpty()) return
        val current = isFollowed(userId)
        val target = !current
        val key = "follow:$userId"
        _followedAuthors.update { it + (userId to target) }
        favorites.setFollowed(userId, target)
        enqueue(
            WriteTask(
                key = key,
                kind = EngagementKind.FOLLOW,
                id = userId,
                previousState = current,
                targetState = target,
                epoch = sessionEpoch,
            ),
        )
    }

    fun toggleBookmark(entry: FavoriteEntry) {
        val novel = entry.kind == "novel"
        val current = isBookmarked(entry.id, novel)
        val target = !current
        val key = bookmarkKey(entry.id, novel)
        updateBookmarkState(entry.id, novel, target)
        favorites.setBookmarked(entry, target)
        enqueue(
            WriteTask(
                key = key,
                kind = if (novel) EngagementKind.NOVEL else EngagementKind.ILLUST,
                id = entry.id,
                previousState = current,
                targetState = target,
                entry = entry,
                epoch = sessionEpoch,
            ),
        )
    }

    fun clear() {
        sessionEpoch++
        _followedAuthors.value = emptyMap()
        _bookmarkedIllusts.value = emptyMap()
        _bookmarkedNovels.value = emptyMap()
        bookmarkIds.clear()
        activeKeys.clear()
        api.clearCsrfCache()
        scope.launch {
            queueMutex.withLock {
                pendingQueue.clear()
            }
        }
    }

    private fun enqueue(task: WriteTask) {
        activeKeys.add(task.key)
        scope.launch {
            queueMutex.withLock {
                pendingQueue.removeAll { it.key == task.key }
                pendingQueue.addLast(task)
            }
            signal.trySend(Unit)
        }
    }

    private suspend fun processLoop() {
        for (ignored in signal) {
            while (true) {
                val task = queueMutex.withLock {
                    if (pendingQueue.isEmpty()) null else pendingQueue.removeFirst()
                } ?: break

                val now = System.currentTimeMillis()
                val cooldownWait = cooldownUntilMs - now
                if (cooldownWait > 0) delay(cooldownWait)

                val gapWait = (lastRequestAtMs + minGapMs) - System.currentTimeMillis()
                if (gapWait > 0) delay(gapWait)

                lastRequestAtMs = System.currentTimeMillis()
                executeTask(task)
            }
        }
    }

    private suspend fun executeTask(task: WriteTask) {
        if (task.epoch != sessionEpoch) {
            activeKeys.remove(task.key)
            return
        }
        var succeeded = false
        var errorReason: String? = null
        try {
            when (task.kind) {
                EngagementKind.FOLLOW -> {
                    succeeded = if (task.targetState) {
                        api.followUser(task.id)
                    } else {
                        api.unfollowUser(task.id)
                    }
                }

                EngagementKind.ILLUST, EngagementKind.NOVEL -> {
                    val novel = task.kind == EngagementKind.NOVEL
                    val bKey = bookmarkKey(task.id, novel)
                    if (task.targetState) {
                        val newId = api.addBookmark(task.id, novel = novel)
                        if (newId != null) {
                            if (task.epoch == sessionEpoch) {
                                bookmarkIds[bKey] = newId
                            }
                            succeeded = true
                        }
                    } else {
                        val ok = api.removeBookmark(task.id, bookmarkIds[bKey], novel = novel)
                        if (ok) {
                            bookmarkIds.remove(bKey)
                            succeeded = true
                        }
                    }
                }
            }
        } catch (e: PixivException) {
            if (e.code == 429) {
                cooldownUntilMs = System.currentTimeMillis() + cooldownMs
            }
            errorReason = e.message
        } catch (e: Exception) {
            errorReason = e.message
        }

        if (task.epoch != sessionEpoch) {
            activeKeys.remove(task.key)
            return
        }
        val superseded = queueMutex.withLock {
            pendingQueue.any { it.key == task.key }
        }
        if (!superseded) {
            activeKeys.remove(task.key)
            if (succeeded) {
                _events.tryEmit(EngagementEvent.Succeeded(task.kind, task.id, task.targetState))
            } else {
                rollback(task)
                _events.tryEmit(EngagementEvent.Failed(task.kind, task.id, errorReason))
            }
        }
    }

    private fun rollback(task: WriteTask) {
        when (task.kind) {
            EngagementKind.FOLLOW -> {
                _followedAuthors.update { it + (task.id to task.previousState) }
                favorites.setFollowed(task.id, task.previousState)
            }

            EngagementKind.ILLUST, EngagementKind.NOVEL -> {
                val novel = task.kind == EngagementKind.NOVEL
                updateBookmarkState(task.id, novel, task.previousState)
                task.entry?.let { favorites.setBookmarked(it, task.previousState) }
            }
        }
    }

    private fun updateBookmarkState(id: String, novel: Boolean, active: Boolean) {
        if (novel) {
            _bookmarkedNovels.update { it + (id to active) }
        } else {
            _bookmarkedIllusts.update { it + (id to active) }
        }
    }

    private fun bookmarkKey(id: String, novel: Boolean): String =
        if (novel) "novel:$id" else "illust:$id"

    companion object {
        const val DEFAULT_MIN_GAP_MS = 1800L
        const val DEFAULT_COOLDOWN_MS = 15_000L
    }
}
