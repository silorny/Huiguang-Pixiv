@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package moe.huiguang.pixiv.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.LoadingDialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarSurface
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.model.CaptionTarget
import moe.huiguang.pixiv.data.model.IllustDetail
import moe.huiguang.pixiv.data.model.IllustPage
import moe.huiguang.pixiv.data.model.SearchCategory
import moe.huiguang.pixiv.data.model.SearchItem
import moe.huiguang.pixiv.data.model.ShareFormatter
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.design.AdaptiveTokens
import moe.huiguang.pixiv.design.CenteredReadableBox
import moe.huiguang.pixiv.design.rememberAdaptiveGridColumns
import moe.huiguang.pixiv.design.rememberIsLandscapeSplit
import moe.huiguang.pixiv.design.components.ActionNavItem
import moe.huiguang.pixiv.design.components.ArtworkCard
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.DetailSkeleton
import moe.huiguang.pixiv.design.components.FloatingActionNavBar
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryErrorState
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.design.components.TagChip
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.design.shape.CachedCouiShape
import moe.huiguang.pixiv.ui.common.ExternalLinkConfirmDialog
import moe.huiguang.pixiv.ui.common.IntroCard
import moe.huiguang.pixiv.ui.common.LinkedWorksSection
import moe.huiguang.pixiv.ui.common.SeriesSection
import moe.huiguang.pixiv.ui.common.WorkMetaHeader
import moe.huiguang.pixiv.ui.common.copyPlainText
import moe.huiguang.pixiv.ui.common.openExternalUrl
import java.text.NumberFormat
import java.util.Locale

/**
 * Artwork / manga detail.
 *
 * Order follows the agreed reading flow: cover → type/PID → author → compact
 * stats → caption → tags (wrapped) → actions. The header floats over the cover
 * and blurs in with scroll; overscroll is disabled so the stretch effect cannot
 * clip the header or the cover at the end of the page.
 */
@Composable
fun DetailScreen(
    workId: String,
    api: PixivApi,
    cache: ArtworkCache,
    downloads: ImageDownloader,
    engagement: EngagementRepository,
    settings: moe.huiguang.pixiv.data.store.SettingsStore,
    onBack: () -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenLinked: (String, String) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenAsNovel: (String) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val cached = remember(workId) { cache.get(workId) }
    var detail by remember(workId) { mutableStateOf(cached?.detail) }
    var pages by remember(workId) { mutableStateOf(cached?.pages ?: emptyList()) }
    var loading by remember(workId) { mutableStateOf(cached == null) }
    var error by remember(workId) { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var avatar by remember(workId) { mutableStateOf<String?>(null) }
    var authorWorks by remember(workId) {
        mutableStateOf(cached?.detail?.let { mergeCurrentIntoNeighbors(it.authorNeighborWorks, it) } ?: emptyList())
    }
    var relatedWorks by remember(workId) { mutableStateOf<List<SearchItem>>(emptyList()) }
    var relatedNextIds by remember(workId) { mutableStateOf<List<String>>(emptyList()) }
    var relatedLoading by remember(workId) { mutableStateOf(false) }
    var relatedLoadingMore by remember(workId) { mutableStateOf(false) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var externalLinkDialogVisible by remember { mutableStateOf(false) }

    val dark = CouiTheme.isDark
    val windowBackdrop = rememberCouiWindowBackdrop()
    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    val bookmarkedIllusts by engagement.bookmarkedIllusts.collectAsState()
    val followedAuthors by engagement.followedAuthors.collectAsState()
    val isFavorite = bookmarkedIllusts[workId] ?: (detail?.bookmarkId != null)
    val settingsState by settings.state.collectAsState()

    fun handleCaptionLink(target: CaptionTarget) {
        when (target) {
            is CaptionTarget.Illust -> onOpenLinked("illust", target.id)
            is CaptionTarget.Novel -> onOpenLinked("novel", target.id)
            is CaptionTarget.User -> onOpenAuthor(target.id)
            is CaptionTarget.External -> {
                pendingExternalUrl = target.url
                externalLinkDialogVisible = true
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engagement, workId, detail?.userId) {
        engagement.events.collect { event ->
            val currentWork = detail
            when (event) {
                is EngagementEvent.Succeeded -> {
                    val matchesWork = event.kind == EngagementKind.ILLUST && event.id == workId
                    if (matchesWork && currentWork != null) {
                        val bid = if (event.active) engagement.bookmarkIdOf(workId, novel = false) else null
                        val updated = currentWork.copy(bookmarkId = bid)
                        detail = updated
                        cache.put(workId, ArtworkCache.Entry(updated, pages))
                        Toast.makeText(
                            context,
                            resources.getString(if (event.active) R.string.detail_favorited else R.string.detail_unfavorited),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                is EngagementEvent.Failed -> {
                    val matchesWork = event.kind == EngagementKind.ILLUST && event.id == workId
                    val matchesAuthor = event.kind == EngagementKind.FOLLOW && event.id == currentWork?.userId
                    if (matchesWork || matchesAuthor) {
                        Toast.makeText(
                            context,
                            event.reason?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.action_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(detail?.id, detail?.bookmarkId) {
        val work = detail ?: return@LaunchedEffect
        engagement.syncBookmark(
            id = work.id,
            novel = false,
            bookmarkId = work.bookmarkId,
            entry = FavoriteEntry(
                id = work.id,
                title = work.title,
                thumbUrl = work.thumbUrl,
                userName = work.userName,
                userId = work.userId,
                kind = "illust",
                illustType = work.illustType,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    LaunchedEffect(workId, reloadKey) {
        if (cached != null && reloadKey == 0) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            val loaded = api.illust(workId) ?: error(resources.getString(R.string.error_illust_not_found))
            val loadedPages = api.illustPages(workId)
            cache.put(workId, ArtworkCache.Entry(loaded, loadedPages))
            loaded to loadedPages
        }.onSuccess { (d, p) ->
            detail = d
            pages = p
        }.onFailure {
            error = it.message ?: resources.getString(R.string.error_load_failed)
        }
        loading = false
    }

    // Silent status refresh on resume / cached entry so cloud changes sync back.
    LaunchedEffect(workId, resumeTick) {
        val fresh = runCatching { api.illust(workId) }.getOrNull() ?: return@LaunchedEffect
        detail = fresh
        cache.put(workId, ArtworkCache.Entry(fresh, pages))
        engagement.syncBookmark(fresh.id, novel = false, bookmarkId = fresh.bookmarkId)
    }

    LaunchedEffect(detail?.userId, resumeTick) {
        val currentWork = detail ?: return@LaunchedEffect
        val uid = currentWork.userId
        avatar = cache.avatar(uid) ?: currentWork.userAvatarUrl.takeIf { it.isNotEmpty() }
        if (currentWork.authorNeighborWorks.isNotEmpty() && authorWorks.size <= 1) {
            authorWorks = mergeCurrentIntoNeighbors(currentWork.authorNeighborWorks, currentWork)
        }
        runCatching { api.user(uid) }.getOrNull()?.let { u ->
            engagement.syncFollow(uid, u.isFollowed)
            if (avatar == null && u.avatarUrl.isNotEmpty()) {
                cache.putAvatar(uid, u.avatarUrl)
                avatar = u.avatarUrl
            }
        }
        if (authorWorks.size < 5 && uid.isNotEmpty()) {
            val fetched = runCatching { api.authorNeighborWorks(uid, workId, radius = 6) }.getOrNull()
            if (!fetched.isNullOrEmpty() && fetched.size >= authorWorks.size) {
                authorWorks = mergeCurrentIntoNeighbors(fetched, currentWork)
            }
        }
    }

    LaunchedEffect(workId, reloadKey) {
        relatedLoading = true
        runCatching { api.relatedIllustsInit(workId, limit = 18) }
            .onSuccess { page ->
                val seen = HashSet<String>().apply { add(workId) }
                relatedWorks = page.items.filter { seen.add(it.id) }
                relatedNextIds = page.nextIds.filter { it !in seen }
            }
        relatedLoading = false
    }

    fun toggleFollow() {
        val uid = detail?.userId ?: return
        if (settingsState.session == null) {
            Toast.makeText(context, resources.getString(R.string.bookmarks_need_login), Toast.LENGTH_SHORT).show()
            return
        }
        engagement.toggleFollow(uid)
    }

    val current = detail

    // Record the visit so the history page has something to show.
    LaunchedEffect(current?.id) {
        val work = current ?: return@LaunchedEffect
        settings.addHistory(
            moe.huiguang.pixiv.data.model.HistoryEntry(
                pid = work.id,
                kind = moe.huiguang.pixiv.data.model.HistoryEntry.KIND_ILLUST,
                title = work.title,
                thumbUrl = work.thumbUrl,
                at = System.currentTimeMillis(),
                userName = work.userName,
                illustType = work.illustType,
            ),
        )
    }

    fun downloadAll() {
        val work = current ?: return
        val targets = pages.ifEmpty {
            listOf(IllustPage(work.previewUrl, work.originalUrl, work.width, work.height))
        }
        downloading = resources.getString(R.string.detail_downloading, 0, targets.size)
        scope.launch {
            var ok = 0
            targets.forEachIndexed { index, page ->
                downloading = resources.getString(R.string.detail_downloading, index + 1, targets.size)
                val name = ImageDownloader.fileNameFor(page.originalUrl, work.id, index)
                runCatching { downloads.download(context, page.originalUrl, name) }
                    .onSuccess { ok++ }
            }
            downloading = null
            Toast.makeText(
                context,
                resources.getString(R.string.detail_download_done, ok, targets.size),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun toggleFavorite() {
        val work = current ?: return
        if (settingsState.session == null) {
            Toast.makeText(context, resources.getString(R.string.bookmarks_need_login), Toast.LENGTH_SHORT).show()
            return
        }
        val entry = FavoriteEntry(
            id = work.id,
            title = work.title,
            thumbUrl = work.thumbUrl,
            userName = work.userName,
            userId = work.userId,
            kind = "illust",
            illustType = work.illustType,
            savedAt = System.currentTimeMillis(),
        )
        engagement.toggleBookmark(entry)
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val detailPhase = when {
        current == null && loading -> 0
        current == null -> 1
        else -> 2
    }

    val errorBackdrop = rememberCouiBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(errorBackdrop)
                .background(colors.background),
        )
        Crossfade(
            targetState = detailPhase,
            animationSpec = tween(200),
            label = "detailState",
        ) { phase ->
            when (phase) {
                0 -> DetailSkeleton(topPadding = statusTop + 52.dp + 8.dp)

                1 -> RetryErrorState(
                    error = error,
                    onRetry = { reloadKey++ },
                    backdrop = errorBackdrop,
                    extraAction = {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.clickable { onOpenAsNovel(workId) }) {
                            Text(
                                text = stringResource(R.string.detail_try_novel),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.accent,
                            )
                        }
                    },
                )

                else -> {
                    val workDetail = current ?: return@Crossfade
                    val backdrop = rememberCouiBackdrop()
                    // Flat sibling source for the stats capsule; it must not sample
                    // the scrolling content that hosts it.
                    val effectBackdrop = rememberCouiBackdrop()
                    val effectDark = isDarkBackdrop(colors.background)
                    val gridState = rememberLazyStaggeredGridState()
                    val overscroll = rememberCouiOverscrollEffect()
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    var currentCoverPage by rememberSaveable(workId) { mutableIntStateOf(0) }
                    val isExpanded = rememberIsLandscapeSplit()
                    val singlePaneColumns = rememberAdaptiveGridColumns()
                    val rightPaneColumns = if (isExpanded) 2 else singlePaneColumns
                    val favoriteColor = if (effectDark) CouiColor.Night.RED else CouiColor.Day.RED

                    val nearRelatedEnd by remember(gridState, relatedWorks, relatedNextIds) {
                        derivedStateOf {
                            val info = gridState.layoutInfo
                            info.totalItemsCount > 0 &&
                                relatedNextIds.isNotEmpty() &&
                                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
                        }
                    }
                    LaunchedEffect(nearRelatedEnd, relatedNextIds.size, relatedLoading, relatedLoadingMore) {
                        if (nearRelatedEnd && !relatedLoading && !relatedLoadingMore && relatedNextIds.isNotEmpty()) {
                            val batch = relatedNextIds.take(18)
                            relatedLoadingMore = true
                            runCatching { api.relatedIllustsByIds(batch) }
                                .onSuccess { fresh ->
                                    val known = relatedWorks.mapTo(HashSet()) { it.id }.apply { add(workId) }
                                    relatedWorks = relatedWorks + fresh.filter { known.add(it.id) }
                                    relatedNextIds = relatedNextIds.drop(batch.size)
                                }
                            relatedLoadingMore = false
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .couiBackdrop(effectBackdrop)
                            .background(colors.background),
                    )
                    val renderFeedGrid: @Composable (Boolean) -> Unit = { includeCoverInGrid ->
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(rightPaneColumns),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .overscroll(overscroll),
                            contentPadding = PaddingValues(
                                start = if (isExpanded) 0.dp else 16.dp,
                                end = if (isExpanded) 0.dp else 16.dp,
                                top = if (includeCoverInGrid) 0.dp else statusTop + 52.dp + 8.dp,
                                bottom = 104.dp + navBottom,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "detail-header-body") {
                                CenteredReadableBox(maxWidth = if (isExpanded) Dp.Unspecified else 640.dp) {
                                    Column(Modifier.fillMaxWidth()) {
                                        if (includeCoverInGrid) {
                                            // Leave the status bar and the 52dp top bar clear, so the
                                            // cover starts below the two floating buttons.
                                            Spacer(Modifier.height(statusTop + 52.dp + 8.dp))
                                            CoverPager(
                                                detail = workDetail,
                                                pages = pages,
                                                colors = colors,
                                                backdrop = effectBackdrop,
                                                onPageChanged = { currentCoverPage = it },
                                                onOpenViewer = onOpenViewer,
                                                horizontalPadding = 0.dp,
                                            )
                                            Spacer(Modifier.height(14.dp))
                                        }
                                        Column(Modifier.padding(horizontal = 4.dp)) {
                                            // 1. work type + PID + publish date
                                            WorkMetaHeader(
                                                typeLabel = typeLabel(current.illustType),
                                                id = current.id,
                                                createDate = current.createDate,
                                            )
                                            Spacer(Modifier.height(8.dp))

                                            // 2. title
                                            BasicText(
                                                text = current.title.ifBlank { stringResource(R.string.untitled_work) },
                                                style = TextStyle(
                                                    color = colors.title,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    lineHeight = 28.sp,
                                                ),
                                            )
                                            Spacer(Modifier.height(12.dp))

                                            // 3. author
                                            AuthorRow(
                                                detail = current,
                                                avatar = avatar,
                                                colors = colors,
                                                isFollowed = followedAuthors[current.userId] == true,
                                                backdrop = effectBackdrop,
                                                dark = effectDark,
                                                onToggleFollow = { toggleFollow() },
                                                onClick = { onOpenAuthor(current.userId) },
                                            )
                                            Spacer(Modifier.height(12.dp))

                                            // 4. stats capsule (read-only segmented look)
                                            GlowStatCapsule(
                                                stats = listOf(
                                                    StatValue(number(current.viewCount), stringResource(R.string.detail_views)),
                                                    StatValue(number(current.bookmarkCount), stringResource(R.string.detail_bookmarks)),
                                                    StatValue(number(current.likeCount), stringResource(R.string.detail_likes)),
                                                ),
                                                backdrop = effectBackdrop,
                                                dark = effectDark,
                                            )
                                            // 5. caption with clickable internal & external links
                                            val description = plainCaption(current.description)
                                            if (description.isNotEmpty()) {
                                                Spacer(Modifier.height(12.dp))
                                                IntroCard(
                                                    text = description,
                                                    colors = colors,
                                                    rawHtml = current.description,
                                                    onInternalLink = ::handleCaptionLink,
                                                    onExternalLink = { url ->
                                                        pendingExternalUrl = url
                                                        externalLinkDialogVisible = true
                                                    },
                                                )
                                            }
                                        }

                                        current.series?.let { series ->
                                            Spacer(Modifier.height(16.dp))
                                            SeriesSection(
                                                series = series,
                                                currentId = current.id,
                                                isNovel = false,
                                                api = api,
                                                onOpenWork = { id -> onOpenLinked("illust", id) },
                                                onOpenSeries = { onOpenSeries(series.seriesId, current.id) },
                                                insetInGrid = true,
                                            )
                                        }

                                        LinkedWorksSection(
                                            descriptionHtml = current.description,
                                            currentKind = "illust",
                                            currentId = current.id,
                                            series = current.series,
                                            api = api,
                                            onOpen = { work -> onOpenLinked(work.kind, work.id) },
                                            onOpenAuthor = onOpenAuthor,
                                            titleHorizontalPadding = 8.dp,
                                            cardHorizontalPadding = 0.dp,
                                        )

                                        // 6. tags, wrapped
                                        if (current.tags.isNotEmpty()) {
                                            Spacer(Modifier.height(18.dp))
                                            Text(
                                                text = stringResource(R.string.detail_tags),
                                                kind = CouiTextKind.GroupTitle,
                                                color = colors.summary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            )
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                current.tags.forEach { tag ->
                                                    TagChip(text = tag, onClick = { onSearchTag(tag) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 7. author's neighboring works (3-card horizontal carousel centered on current)
                            if (authorWorks.size > 1) {
                                item(span = StaggeredGridItemSpan.FullLine, key = "author-other-works") {
                                    CenteredReadableBox(maxWidth = if (isExpanded) Dp.Unspecified else 640.dp) {
                                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            Text(
                                                text = stringResource(R.string.detail_author_other_works),
                                                kind = CouiTextKind.GroupTitle,
                                                color = colors.summary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            )
                                            AuthorOtherWorksRow(
                                                items = authorWorks,
                                                currentId = current.id,
                                                revealR18 = settingsState.revealR18,
                                                colors = colors,
                                                onOpenWork = { item ->
                                                    if (item.id != current.id) {
                                                        onOpenLinked(
                                                            if (item.category == SearchCategory.NOVEL) "novel" else "illust",
                                                            item.id,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // 8. related works waterfall
                            if (relatedWorks.isNotEmpty() || relatedLoading) {
                                item(span = StaggeredGridItemSpan.FullLine, key = "related-works-header") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.detail_related_works),
                                            kind = CouiTextKind.GroupTitle,
                                            color = colors.summary,
                                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
                                        )
                                    }
                                }
                            }

                            if (relatedLoading && relatedWorks.isEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine, key = "related-loading") {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.search_loading),
                                            kind = CouiTextKind.ItemSummary,
                                            color = colors.summary,
                                        )
                                    }
                                }
                            } else {
                                items(
                                    items = relatedWorks,
                                    key = { "rel-${it.id}" },
                                    contentType = { if (it.category == SearchCategory.NOVEL) "novel" else "artwork" },
                                ) { item ->
                                    ArtworkCard(
                                        item = item,
                                        revealR18 = settingsState.revealR18,
                                        lowQualityThumb = true,
                                        onClick = {
                                            onOpenLinked(
                                                if (item.category == SearchCategory.NOVEL) "novel" else "illust",
                                                item.id,
                                            )
                                        },
                                    )
                                }
                                if (relatedLoadingMore) {
                                    item(span = StaggeredGridItemSpan.FullLine, key = "related-loading-more") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.search_loading),
                                                kind = CouiTextKind.ItemSummary,
                                                color = colors.summary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .couiBackdrop(backdrop)
                            .background(colors.background),
                    ) {
                        if (isExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(top = statusTop + 52.dp + 8.dp, bottom = 24.dp + navBottom),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CoverPager(
                                        detail = workDetail,
                                        pages = pages,
                                        colors = colors,
                                        backdrop = effectBackdrop,
                                        onPageChanged = { currentCoverPage = it },
                                        onOpenViewer = onOpenViewer,
                                        horizontalPadding = 0.dp,
                                        fillPane = true,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    renderFeedGrid(false)
                                }
                            }
                        } else {
                            renderFeedGrid(true)
                        }
                    }

                val workUrl = PixivApi.PAGE_URL + current.id
                val shareText = ShareFormatter.formatWork(
                    template = settingsState.workShareFormat,
                    title = current.title,
                    userName = current.userName,
                    userId = current.userId,
                    illustId = current.id,
                    url = workUrl,
                    tags = current.tags,
                    fallbackTemplate = stringResource(R.string.share_format_default_work),
                )
                DetailTopBar(
                    backdrop = backdrop,
                    effectDark = effectDark,
                    title = current.title,
                    isFavorite = isFavorite,
                    backgroundColor = colors.background,
                    onBack = onBack,
                    onToggleFavorite = { toggleFavorite() },
                    onDownload = { downloadAll() },
                    onOpenBrowser = {
                        openExternalUrl(context, workUrl)
                    },
                    onCopyLink = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("pixiv", shareText))
                        Toast.makeText(context, resources.getString(R.string.detail_link_copied), Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                )

                val actionNavBar: @Composable BoxScope.() -> Unit = {
                    FloatingActionNavBar(
                        backdrop = backdrop,
                        dark = effectDark,
                        primaryOnLeft = settingsState.leftHanded,
                        items = listOf(
                            ActionNavItem(
                                label = stringResource(R.string.detail_action_download),
                                iconRes = R.drawable.ic_download,
                                onClick = { downloadAll() },
                            ),
                            ActionNavItem(
                                label = stringResource(
                                    if (isFavorite) R.string.detail_favorited_short else R.string.detail_favorite,
                                ),
                                iconRes = if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outlined,
                                iconTint = if (isFavorite) favoriteColor else null,
                                haptic = HapticFeedbackType.LongPress,
                                onClick = { toggleFavorite() },
                            ),
                            ActionNavItem(
                                label = stringResource(R.string.detail_action_browser),
                                iconRes = R.drawable.ic_open_browser,
                                onClick = {
                                    openExternalUrl(context, PixivApi.PAGE_URL + current.id)
                                },
                            ),
                        ),
                        primaryIconRes = R.drawable.ic_view_large,
                        primaryDescription = stringResource(R.string.detail_action_view_image),
                        onPrimaryClick = { onOpenViewer(currentCoverPage) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (settingsState.leftHanded) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                actionNavBar()
                            }
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                actionNavBar()
                            }
                        }
                    }
                } else {
                    actionNavBar()
                }
                }
            }
        }
    }

    LoadingDialog(
        visible = downloading != null,
        message = downloading ?: stringResource(R.string.search_loading),
    )

    pendingExternalUrl?.let { url ->
        ExternalLinkConfirmDialog(
            visible = externalLinkDialogVisible,
            url = url,
            dark = dark,
            colors = colors,
            material = dialogMaterial,
            backdrop = windowBackdrop,
            onDismiss = { externalLinkDialogVisible = false },
            onCopyUrl = {
                copyPlainText(context, "caption_link", url, resources.getString(R.string.detail_link_copied))
                externalLinkDialogVisible = false
            },
            onOpenUrl = {
                externalLinkDialogVisible = false
                openExternalUrl(context, url)
            },
        )
    }
}

@Composable
private fun typeLabel(illustType: Int): String = when (illustType) {
    1 -> stringResource(R.string.filter_manga)
    2 -> stringResource(R.string.work_type_ugoira)
    else -> stringResource(R.string.filter_illust)
}

@Composable
private fun DetailTopBar(
    backdrop: com.itos.coui.backdrop.CouiBackdrop,
    effectDark: Boolean,
    title: String,
    isFavorite: Boolean,
    backgroundColor: Color,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
) {
    val barColors = com.itos.coui.topappbar.CouiTopAppBarDefaults.colors(
        dark = com.itos.coui.theme.CouiTheme.isDark,
    )
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = 1f,
        gradientColor = backgroundColor,
        dark = effectDark,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                // Inset enough that the blurred backdrop is not clipped by the
                // screen edge (and aligns with the cover's 16dp margin).
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backIcon = CouiIcons.Filled.Back
            CircleIconButton(
                icon = backIcon,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                backdrop = backdrop,
                dark = effectDark,
                blurRadius = 20f,
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = title,
                style = TextStyle(
                    color = barColors.titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Box {
                CircleIconButton(
                    icon = CouiIcons.Filled.More,
                    contentDescription = stringResource(R.string.action_more),
                    onClick = { menuExpanded = true },
                    backdrop = backdrop,
                    dark = effectDark,
                    blurRadius = 20f,
                )
                Menu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.widthIn(min = 168.dp),
                    offset = DpOffset(0.dp, 8.dp),
                    items = listOf(
                        CouiMenuItem(
                            value = 0,
                            text = stringResource(if (isFavorite) R.string.detail_favorited_short else R.string.detail_favorite),
                        ),
                        CouiMenuItem(value = 1, text = stringResource(R.string.detail_action_download)),
                        CouiMenuItem(value = 2, text = stringResource(R.string.detail_open_browser)),
                        CouiMenuItem(value = 3, text = stringResource(R.string.detail_copy_link)),
                        CouiMenuItem(value = 4, text = stringResource(R.string.detail_action_share)),
                    ),
                    onItemClick = { item ->
                        menuExpanded = false
                        when (item.value) {
                            0 -> onToggleFavorite()
                            1 -> onDownload()
                            2 -> onOpenBrowser()
                            3 -> onCopyLink()
                            4 -> onShare()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CoverPager(
    detail: IllustDetail,
    pages: List<IllustPage>,
    colors: CouiPageColors,
    backdrop: com.itos.coui.backdrop.CouiBackdrop,
    onPageChanged: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    horizontalPadding: Dp = 16.dp,
    fillPane: Boolean = false,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    val ratio = pages.firstOrNull()?.let {
        if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height.toFloat() else null
    } ?: if (detail.width > 0 && detail.height > 0) {
        detail.width.toFloat() / detail.height
    } else {
        0.75f
    }

    BoxWithConstraints(
        Modifier
            .then(if (fillPane) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        val coverHeight = if (fillPane) {
            maxHeight
        } else {
            (maxWidth / ratio).coerceIn(180.dp, 520.dp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(CouiShape(20.dp, 3f))
                .background(colors.card),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val page = pages.getOrNull(index)
                RetryableImage(
                    url = page?.regularUrl ?: detail.previewUrl,
                    contentDescription = detail.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpenViewer(index) },
                )
            }
            if (pages.size > 1) {
                GlowSurface(
                    backdrop = backdrop,
                    dark = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .height(26.dp),
                    shape = CouiCapsuleShape,
                    cornerWeight = 1f,
                    interactive = false,
                    foregroundComposition = true,
                ) {
                    BasicText(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(
    detail: IllustDetail,
    avatar: String?,
    colors: CouiPageColors,
    isFollowed: Boolean,
    backdrop: CouiBackdrop,
    dark: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(CouiCapsuleShape)
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp, horizontal = 2.dp),
        ) {
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.card),
                )
            } else {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = detail.userName.take(1).ifEmpty { "?" },
                        style = TextStyle(color = colors.accent, fontSize = 18.sp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail.userName.ifBlank { stringResource(R.string.unknown_author) },
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "UID ${detail.userId}",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (detail.userId.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            GlowChip(
                text = stringResource(if (isFollowed) R.string.author_followed else R.string.author_following),
                selected = isFollowed,
                onClick = onToggleFollow,
                backdrop = backdrop,
                dark = dark,
            )
        }
    }
}

@Composable
private fun number(value: Int): String =
    NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale).format(value)

private val OtherWorkCardShape = CachedCouiShape(14.dp, 3f)
private val OtherWorkBadgeShape = CachedCouiShape(7.dp, 3f)

@Composable
private fun AuthorOtherWorksRow(
    items: List<SearchItem>,
    currentId: String,
    revealR18: Boolean,
    colors: CouiPageColors,
    onOpenWork: (SearchItem) -> Unit,
) {
    val currentIndex = remember(items, currentId) { items.indexOfFirst { it.id == currentId } }
    val targetInitialIndex = remember(items, currentIndex) {
        if (currentIndex < 0) 0 else (currentIndex - 1).coerceIn(0, (items.size - 3).coerceAtLeast(0))
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = targetInitialIndex)
    var centeredKey by remember(currentId) { mutableStateOf("") }
    LaunchedEffect(currentId, items.size, targetInitialIndex) {
        val keyStr = "$currentId:${items.size}:$targetInitialIndex"
        if (items.isNotEmpty() && centeredKey != keyStr && !rowState.isScrollInProgress) {
            centeredKey = keyStr
            rowState.scrollToItem(targetInitialIndex)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = 10.dp
        val cardWidth = ((maxWidth - spacing * 2) / 3f).coerceAtLeast(88.dp)
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = { it.id }) { item ->
                val isCurrent = item.id == currentId
                val maskR18 = item.isAdult && !revealR18
                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .clip(OtherWorkCardShape)
                        .clickable { onOpenWork(item) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(OtherWorkCardShape)
                            .background(colors.card)
                            .then(
                                if (isCurrent) Modifier.border(2.dp, colors.accent, OtherWorkCardShape)
                                else Modifier,
                            ),
                    ) {
                        RetryableImage(
                            url = item.thumbUrl.ifBlank { item.fullUrl },
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (maskR18) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xCC141418)),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    text = "R-18",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .background(colors.accent, OtherWorkBadgeShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                BasicText(
                                    text = stringResource(R.string.detail_current_work),
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        } else if (item.pageCount > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .background(Color(0x99000000), OtherWorkBadgeShape)
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                BasicText(
                                    text = "P·${item.pageCount}",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        text = item.title.ifBlank { stringResource(R.string.untitled_work) },
                        style = TextStyle(
                            color = if (isCurrent) colors.accent else colors.title,
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            lineHeight = 16.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

private fun mergeCurrentIntoNeighbors(
    neighbors: List<SearchItem>,
    current: IllustDetail,
): List<SearchItem> {
    if (neighbors.any { it.id == current.id }) return neighbors
    val self = SearchItem(
        id = current.id,
        category = if (current.illustType == 1) SearchCategory.MANGA else SearchCategory.ILLUST,
        title = current.title,
        thumbUrl = current.thumbUrl,
        fullUrl = current.previewUrl,
        userId = current.userId,
        userName = current.userName,
        xRestrict = current.xRestrict,
        width = current.width,
        height = current.height,
        pageCount = current.pageCount,
        textCount = 0,
        tags = current.tags,
        seriesTitle = current.series?.title,
    )
    return (neighbors + self)
        .distinctBy { it.id }
        .sortedByDescending { it.id.toLongOrNull() ?: 0L }
}
