package moe.huiguang.pixiv.ui.author

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.IllustDetail
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.UserProfile
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.ui.common.IntroCard

/** Author page: profile header, category switch and paged works waterfall. */
@Composable
fun AuthorScreen(
    userId: String,
    api: PixivApi,
    engagement: EngagementRepository,
    onBack: () -> Unit,
    onOpenWork: (String, String) -> Unit,
    settings: SettingsStore,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsState by settings.state.collectAsState()
    val followedAuthors by engagement.followedAuthors.collectAsState()
    val scope = rememberCoroutineScope()
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    val isFollowed = followedAuthors[userId] ?: (profile?.isFollowed == true)
    var resumeTick by remember { mutableIntStateOf(0) }
    var works by remember(userId) { mutableStateOf<List<IllustDetail>>(emptyList()) }
    var worksEnded by remember(userId) { mutableStateOf(false) }
    var loadingWorks by remember(userId) { mutableStateOf(false) }
    var novels by remember(userId) { mutableStateOf<List<SearchItem>>(emptyList()) }
    var novelsEnded by remember(userId) { mutableStateOf(false) }
    var loadingNovels by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val gridStates = List(3) { rememberLazyStaggeredGridState() }
    val activeGridState = gridStates[filter]
    val overscroll = rememberCouiOverscrollEffect()
    val backdrop = rememberCouiBackdrop()
    // Flat sibling source for the profile stats capsule.
    val effectBackdrop = rememberCouiBackdrop()
    val effectDark = isDarkBackdrop(colors.background)
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val collapseDistancePx = with(density) { 64.dp.toPx() }
    val collapseProgress by remember(activeGridState, collapseDistancePx) {
        derivedStateOf {
            if (activeGridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (activeGridState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }

    suspend fun loadMoreWorks() {
        if (loadingWorks || worksEnded) return
        loadingWorks = true
        runCatching { api.userWorksPage(userId, offset = works.size, limit = 18) }
            .onSuccess { page ->
                val known = works.mapTo(HashSet()) { it.id }
                val fresh = page.filter { it.id !in known }
                works = works + fresh
                if (page.size < 18 || fresh.isEmpty()) worksEnded = true
            }
            .onFailure { if (works.isEmpty()) worksEnded = true }
        loadingWorks = false
    }

    suspend fun loadMoreNovels() {
        if (loadingNovels || novelsEnded) return
        loadingNovels = true
        runCatching { api.userNovelWorksPage(userId, offset = novels.size, limit = 18) }
            .onSuccess { page ->
                val known = novels.mapTo(HashSet()) { it.id }
                val fresh = page.filter { it.id !in known }
                novels = novels + fresh
                if (page.size < 18 || fresh.isEmpty()) novelsEnded = true
            }
            .onFailure { if (novels.isEmpty()) novelsEnded = true }
        loadingNovels = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engagement, userId) {
        engagement.events.collect { event ->
            if (event is EngagementEvent.Failed && event.kind == EngagementKind.FOLLOW && event.id == userId) {
                Toast.makeText(
                    context,
                    event.reason?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.action_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(userId) {
        val loaded = runCatching { api.user(userId) }.getOrNull()
        if (loaded != null) {
            profile = loaded
            engagement.syncFollow(userId, loaded.isFollowed)
            settings.addHistory(
                moe.huiguang.pixiv.data.model.HistoryEntry(
                    pid = loaded.id,
                    kind = moe.huiguang.pixiv.data.model.HistoryEntry.KIND_AUTHOR,
                    title = loaded.name,
                    thumbUrl = loaded.avatarUrl,
                    at = System.currentTimeMillis(),
                    userName = loaded.name,
                    illustType = -1,
                ),
            )
        } else {
            error = resources.getString(R.string.error_author_load_failed)
        }
        loadMoreWorks()
    }

    LaunchedEffect(userId, resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        val loaded = runCatching { api.user(userId) }.getOrNull() ?: return@LaunchedEffect
        profile = loaded
        engagement.syncFollow(userId, loaded.isFollowed)
    }

    LaunchedEffect(userId) {
        loadMoreNovels()
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
            Box(
                Modifier
                    .matchParentSize()
                    .couiBackdrop(effectBackdrop)
                    .background(colors.background),
            )
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = activeGridState,
                modifier = Modifier
                    .fillMaxSize()
                    .overscroll(overscroll),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = statusTop + 52.dp + 12.dp,
                    bottom = 24.dp + navBottom,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine, key = "header") {
                    ProfileHeader(
                        profile = profile,
                        error = error,
                        isFollowed = isFollowed,
                        onToggleFollow = {
                            if (settingsState.session == null) {
                                Toast.makeText(
                                    context,
                                    resources.getString(R.string.bookmarks_need_login),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                engagement.toggleFollow(userId)
                            }
                        },
                        backdrop = effectBackdrop,
                        dark = effectDark,
                        collapseProgress = { collapseProgress },
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "filter") {
                    SegmentedControl(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        labels = listOf(
                            stringResource(R.string.filter_illust),
                            stringResource(R.string.filter_manga),
                            stringResource(R.string.filter_novel),
                        ),
                        selectedIndex = filter,
                        onSelected = { filter = it },
                    )
                }

                val visibleWorks: List<SearchItem> = when (filter) {
                    1 -> works.filter { it.illustType == 1 }.map { it.toSearchItem() }
                    2 -> novels
                    else -> works.filter { it.illustType != 1 }.map { it.toSearchItem() }
                }
                val isNovelTab = filter == 2
                val isLoading = if (isNovelTab) loadingNovels else loadingWorks
                val isEnded = if (isNovelTab) novelsEnded else worksEnded

                if (visibleWorks.isEmpty() && !isLoading) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                        Text(
                            text = stringResource(R.string.home_category_empty),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    items(visibleWorks, key = { "${it.category}-${it.id}" }) { item ->
                        ArtworkCard(
                            item = item,
                            revealR18 = settingsState.revealR18,
                            onClick = {
                                val kind = if (item.category == SearchCategory.NOVEL) {
                                    "novel"
                                } else {
                                    "illust"
                                }
                                onOpenWork(kind, item.id)
                            },
                        )
                    }
                }

                item(span = StaggeredGridItemSpan.FullLine, key = "author-footer-$filter") {
                    LaunchedEffect(filter, works.size, novels.size, isEnded) {
                        if (!isEnded) {
                            scope.launch {
                                if (isNovelTab) loadMoreNovels() else loadMoreWorks()
                            }
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                isLoading -> stringResource(R.string.search_loading)
                                isEnded && visibleWorks.isNotEmpty() -> stringResource(R.string.search_end)
                                else -> ""
                            },
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                }
            }
        }

        val authorUrl = "https://www.pixiv.net/users/$userId"
        AuthorTopBar(
            backdrop = backdrop,
            dark = effectDark,
            title = profile?.name ?: stringResource(R.string.author_title, userId),
            backgroundColor = colors.background,
            filter = filter,
            onSelectFilter = { filter = it },
            collapseProgress = { collapseProgress },
            onBack = onBack,
            onOpenBrowser = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorUrl)))
            },
            onCopyLink = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("pixiv", authorUrl))
                Toast.makeText(context, resources.getString(R.string.detail_link_copied), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun AuthorTopBar(
    backdrop: CouiBackdrop,
    dark: Boolean,
    title: String,
    backgroundColor: Color,
    filter: Int,
    onSelectFilter: (Int) -> Unit,
    collapseProgress: () -> Float,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val barColors = CouiTopAppBarDefaults.colors(dark = CouiTheme.isDark)
    val density = LocalDensity.current
    val topRisePx = with(density) { 16.dp.toPx() }
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = 1f,
        gradientColor = backgroundColor,
        dark = dark,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = CouiIcons.Filled.Back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                backdrop = backdrop,
                dark = dark,
                blurRadius = 20f,
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = title,
                style = TextStyle(
                    color = barColors.titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        val p = collapseProgress()
                        val t = ((p - 0.52f) / 0.48f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * topRisePx
                    },
            )
            Box {
                CircleIconButton(
                    icon = CouiIcons.Filled.More,
                    contentDescription = stringResource(R.string.action_more),
                    onClick = { menuExpanded = true },
                    backdrop = backdrop,
                    dark = dark,
                    blurRadius = 20f,
                )
                Menu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    headerText = stringResource(R.string.search_menu_category_header),
                    headerTextAlign = TextAlign.Start,
                    modifier = Modifier.widthIn(min = 168.dp),
                    offset = DpOffset(0.dp, 8.dp),
                    items = listOf(
                        CouiMenuItem(
                            value = 0,
                            text = stringResource(R.string.filter_illust),
                            selected = filter == 0,
                        ),
                        CouiMenuItem(
                            value = 1,
                            text = stringResource(R.string.filter_manga),
                            selected = filter == 1,
                        ),
                        CouiMenuItem(
                            value = 2,
                            text = stringResource(R.string.filter_novel),
                            selected = filter == 2,
                        ),
                        CouiMenuItem(
                            value = 3,
                            text = stringResource(R.string.detail_open_browser),
                            dividerBefore = true,
                        ),
                        CouiMenuItem(value = 4, text = stringResource(R.string.detail_copy_link)),
                    ),
                    onItemClick = { item ->
                        menuExpanded = false
                        when (item.value) {
                            in 0..2 -> onSelectFilter(item.value)
                            3 -> onOpenBrowser()
                            4 -> onCopyLink()
                        }
                    },
                )
            }
        }
    }
}

private fun IllustDetail.toSearchItem(): SearchItem = SearchItem(
    id = id,
    category = if (illustType == 1) SearchCategory.MANGA else SearchCategory.ILLUST,
    title = title,
    thumbUrl = thumbUrl,
    fullUrl = previewUrl,
    userId = userId,
    userName = userName,
    xRestrict = xRestrict,
    width = width,
    height = height,
    pageCount = pageCount,
    textCount = 0,
    tags = tags,
    seriesTitle = null,
)

@Composable
private fun ProfileHeader(
    profile: UserProfile?,
    error: String?,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    backdrop: CouiBackdrop,
    dark: Boolean,
    collapseProgress: () -> Float,
) {
    val colors = CouiPageDefaults.themeColors()
    val density = LocalDensity.current
    val nameShiftPx = with(density) { 14.dp.toPx() }
    val uidShiftPx = with(density) { 22.dp.toPx() }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profile != null) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.card),
                )
            } else {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.card),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile?.name ?: error ?: stringResource(R.string.search_loading),
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val p = collapseProgress()
                        translationY = -nameShiftPx * p
                        alpha = (1f - p / 0.72f).coerceIn(0f, 1f)
                    },
                )
                if (profile != null) {
                    Text(
                        text = "UID ${profile.id}",
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        modifier = Modifier.graphicsLayer {
                            val p = collapseProgress()
                            translationY = -uidShiftPx * p
                            alpha = (1f - ((p - 0.55f) / 0.45f).coerceIn(0f, 1f))
                        },
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            GlowChip(
                text = stringResource(if (isFollowed) R.string.author_followed else R.string.author_following),
                selected = isFollowed,
                onClick = onToggleFollow,
                backdrop = backdrop,
                dark = dark,
            )
        }
        if (profile != null && profile.comment.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            IntroCard(
                text = profile.comment.trim(),
                colors = colors,
                collapsedLines = 3,
            )
        }
        if (profile != null) {
            Spacer(Modifier.height(12.dp))
            GlowStatCapsule(
                stats = listOf(
                    StatValue("${profile.following}", stringResource(R.string.author_following)),
                    StatValue("${profile.mypixivCount}", stringResource(R.string.author_mypixiv)),
                ),
                backdrop = backdrop,
                dark = dark,
            )
        }
    }
}
