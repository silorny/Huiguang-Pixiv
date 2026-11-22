package moe.huiguang.pixiv.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.HistoryEntry
import moe.huiguang.pixiv.data.model.UserSearchItem
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.WorkRow
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.workMeta

private data class ProfileWorkRowItem(
    val id: String,
    val kind: String,
    val illustType: Int,
    val title: String,
    val thumbUrl: String,
    val author: String,
)

/**
 * Secondary list screen opened from "我的" stat capsule (关注 / 好P友 / 作品),
 * displaying authors or works using the same [WorkRow] control as search history.
 */
@Composable
fun ProfileListScreen(
    uid: String,
    kind: String,
    api: PixivApi,
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenWork: (kind: String, id: String) -> Unit,
) {
    val resources = LocalResources.current
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listTopPadding = statusTop + 56.dp + 8.dp
    val titleRes = when (kind) {
        "mypixiv" -> R.string.profile_mypixiv
        "works" -> R.string.profile_works
        else -> R.string.profile_following
    }

    var users by remember(uid, kind) { mutableStateOf<List<UserSearchItem>>(emptyList()) }
    var works by remember(uid, kind) { mutableStateOf<List<ProfileWorkRowItem>>(emptyList()) }
    var artOffset by remember(uid, kind) { mutableIntStateOf(0) }
    var novelOffset by remember(uid, kind) { mutableIntStateOf(0) }
    var artEnded by remember(uid, kind) { mutableStateOf(false) }
    var novelEnded by remember(uid, kind) { mutableStateOf(false) }
    var loading by remember(uid, kind) { mutableStateOf(true) }
    var loadingMore by remember(uid, kind) { mutableStateOf(false) }
    var endReached by remember(uid, kind) { mutableStateOf(false) }
    var error by remember(uid, kind) { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(uid, kind, reloadKey) {
        loading = true
        error = null
        endReached = false
        artOffset = 0
        novelOffset = 0
        artEnded = false
        novelEnded = false
        runCatching {
            when (kind) {
                "mypixiv" -> api.userMyPixiv(uid, offset = 0, limit = PAGE_SIZE) to emptyList<ProfileWorkRowItem>()
                "works" -> coroutineScope {
                    val artDeferred = async { runCatching { api.userWorksPage(uid, offset = 0, limit = PAGE_SIZE) }.getOrDefault(emptyList()) }
                    val novelDeferred = async { runCatching { api.userNovelWorksPage(uid, offset = 0, limit = PAGE_SIZE) }.getOrDefault(emptyList()) }
                    val art = artDeferred.await()
                    val novel = novelDeferred.await()
                    artOffset = art.size
                    novelOffset = novel.size
                    artEnded = art.size < PAGE_SIZE
                    novelEnded = novel.size < PAGE_SIZE
                    val mapped = art.map {
                        ProfileWorkRowItem(
                            id = it.id,
                            kind = HistoryEntry.KIND_ILLUST,
                            illustType = it.illustType,
                            title = it.title,
                            thumbUrl = it.thumbUrl,
                            author = it.userName,
                        )
                    } + novel.map {
                        ProfileWorkRowItem(
                            id = it.id,
                            kind = HistoryEntry.KIND_NOVEL,
                            illustType = -1,
                            title = it.title,
                            thumbUrl = it.thumbUrl,
                            author = it.userName,
                        )
                    }
                    emptyList<UserSearchItem>() to mapped
                }
                else -> api.userFollowing(uid, offset = 0, limit = PAGE_SIZE) to emptyList<ProfileWorkRowItem>()
            }
        }.onSuccess { (u, w) ->
            users = u
            works = w
            endReached = if (kind == "works") (artEnded && novelEnded) else u.size < PAGE_SIZE
        }.onFailure { e ->
            error = e.message ?: resources.getString(R.string.error_load_failed)
        }
        loading = false
    }

    suspend fun loadMore() {
        if (loading || loadingMore || endReached) return
        loadingMore = true
        if (kind == "works") {
            runCatching {
                coroutineScope {
                    val artDeferred = if (!artEnded) {
                        async { runCatching { api.userWorksPage(uid, offset = artOffset, limit = PAGE_SIZE) }.getOrDefault(emptyList()) }
                    } else null
                    val novelDeferred = if (!novelEnded) {
                        async { runCatching { api.userNovelWorksPage(uid, offset = novelOffset, limit = PAGE_SIZE) }.getOrDefault(emptyList()) }
                    } else null
                    val art = artDeferred?.await().orEmpty()
                    val novel = novelDeferred?.await().orEmpty()
                    art to novel
                }
            }.onSuccess { (art, novel) ->
                if (!artEnded) {
                    artOffset += art.size
                    if (art.size < PAGE_SIZE) artEnded = true
                }
                if (!novelEnded) {
                    novelOffset += novel.size
                    if (novel.size < PAGE_SIZE) novelEnded = true
                }
                val known = works.mapTo(HashSet()) { "${it.kind}-${it.id}" }
                val fresh = (
                    art.map {
                        ProfileWorkRowItem(
                            id = it.id,
                            kind = HistoryEntry.KIND_ILLUST,
                            illustType = it.illustType,
                            title = it.title,
                            thumbUrl = it.thumbUrl,
                            author = it.userName,
                        )
                    } + novel.map {
                        ProfileWorkRowItem(
                            id = it.id,
                            kind = HistoryEntry.KIND_NOVEL,
                            illustType = -1,
                            title = it.title,
                            thumbUrl = it.thumbUrl,
                            author = it.userName,
                        )
                    }
                ).filter { "${it.kind}-${it.id}" !in known }
                works = works + fresh
                if ((artEnded && novelEnded) || fresh.isEmpty()) endReached = true
            }
        } else {
            runCatching {
                val offset = users.size
                if (kind == "mypixiv") {
                    api.userMyPixiv(uid, offset = offset, limit = PAGE_SIZE)
                } else {
                    api.userFollowing(uid, offset = offset, limit = PAGE_SIZE)
                }
            }.onSuccess { more ->
                val known = users.mapTo(HashSet()) { it.userId }
                val fresh = more.filter { it.userId !in known }
                users = users + fresh
                if (more.size < PAGE_SIZE || fresh.isEmpty()) endReached = true
            }
        }
        loadingMore = false
    }

    var loadMoreTrigger by remember(uid, kind) { mutableIntStateOf(0) }
    LaunchedEffect(loadMoreTrigger) {
        if (loadMoreTrigger > 0) loadMore()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(colors.background),
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.search_loading),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                }

                error != null -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = listTopPadding, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(64.dp))
                        Text(text = error!!, kind = CouiTextKind.ItemSummary, color = colors.summary)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(text = stringResource(R.string.action_retry), onClick = { reloadKey++ })
                    }
                }

                (kind == "works" && works.isEmpty()) || (kind != "works" && users.isEmpty()) -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.profile_list_empty),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                }

                kind == "works" -> {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .overscroll(overscroll),
                        contentPadding = PaddingValues(top = listTopPadding, bottom = navBottom + 24.dp),
                    ) {
                        items(works, key = { "${it.kind}-${it.id}" }) { item ->
                            WorkRow(
                                thumbUrl = item.thumbUrl,
                                title = item.title,
                                author = item.author,
                                meta = workMeta(item.kind, item.illustType, item.id),
                                onClick = {
                                    val routeKind = if (item.kind == HistoryEntry.KIND_NOVEL) "novel" else "illust"
                                    onOpenWork(routeKind, item.id)
                                },
                            )
                        }
                        item(key = "works-footer") {
                            LaunchedEffect(works.size, endReached) {
                                if (!endReached) loadMoreTrigger++
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = when {
                                        loadingMore -> stringResource(R.string.search_loading)
                                        endReached -> stringResource(R.string.search_end)
                                        else -> ""
                                    },
                                    kind = CouiTextKind.ItemSummary,
                                    color = colors.summary,
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .overscroll(overscroll),
                        contentPadding = PaddingValues(top = listTopPadding, bottom = navBottom + 24.dp),
                    ) {
                        items(users, key = { it.userId }) { user ->
                            WorkRow(
                                thumbUrl = user.avatarUrl,
                                title = user.userName.ifBlank { user.userId },
                                author = "",
                                meta = workMeta(HistoryEntry.KIND_AUTHOR, 0, user.userId),
                                onClick = { onOpenAuthor(user.userId) },
                            )
                        }
                        item(key = "footer") {
                            LaunchedEffect(users.size, endReached) {
                                if (!endReached) loadMoreTrigger++
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = when {
                                        loadingMore -> stringResource(R.string.search_loading)
                                        endReached -> stringResource(R.string.search_end)
                                        else -> ""
                                    },
                                    kind = CouiTextKind.ItemSummary,
                                    color = colors.summary,
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingSecondaryTopBar(
            title = stringResource(titleRes),
            backdrop = backdrop,
            onBack = onBack,
        )
    }
}

private const val PAGE_SIZE = 24
