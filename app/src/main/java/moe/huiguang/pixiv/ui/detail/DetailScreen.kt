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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import com.itos.coui.scroll.couiVerticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.LoadingDialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiColor
import com.itos.coui.topappbar.TopAppBarSurface
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.model.IllustDetail
import moe.huiguang.pixiv.data.model.IllustPage
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.design.components.ActionNavItem
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.DetailSkeleton
import moe.huiguang.pixiv.design.components.FloatingActionNavBar
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.GlowSurface
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.design.components.TagChip
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.ui.common.IntroCard
import moe.huiguang.pixiv.ui.common.LinkedWorksSection
import moe.huiguang.pixiv.ui.common.SeriesSection
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

    val bookmarkedIllusts by engagement.bookmarkedIllusts.collectAsState()
    val followedAuthors by engagement.followedAuthors.collectAsState()
    val isFavorite = bookmarkedIllusts[workId] ?: (detail?.bookmarkId != null)
    val settingsState by settings.state.collectAsState()

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
        val uid = detail?.userId ?: return@LaunchedEffect
        avatar = cache.avatar(uid) ?: detail?.userAvatarUrl?.takeIf { it.isNotEmpty() }
        runCatching { api.user(uid) }.getOrNull()?.let { u ->
            engagement.syncFollow(uid, u.isFollowed)
            if (avatar == null && u.avatarUrl.isNotEmpty()) {
                cache.putAvatar(uid, u.avatarUrl)
                avatar = u.avatarUrl
            }
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Crossfade(
            targetState = detailPhase,
            animationSpec = tween(200),
            label = "detailState",
        ) { phase ->
            when (phase) {
                0 -> DetailSkeleton(topPadding = statusTop + 52.dp + 8.dp)

                1 -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error ?: stringResource(R.string.error_load_failed),
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = stringResource(R.string.action_retry),
                        onClick = { reloadKey++ },
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clickable { onOpenAsNovel(workId) }) {
                        Text(
                            text = stringResource(R.string.detail_try_novel),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.accent,
                        )
                    }
                }

                else -> {
                    val workDetail = current ?: return@Crossfade
                    val backdrop = rememberCouiBackdrop()
                    // Flat sibling source for the stats capsule; it must not sample
                    // the scrolling content that hosts it.
                    val effectBackdrop = rememberCouiBackdrop()
                    val effectDark = isDarkBackdrop(colors.background)
                    val scroll = rememberScrollState()
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    var currentCoverPage by rememberSaveable(workId) { mutableIntStateOf(0) }

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

                        Column(
                            Modifier
                                .fillMaxSize()
                                .couiVerticalScroll(scroll),
                        ) {
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
                        )

                        Spacer(Modifier.height(14.dp))
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            // 1. work type + PID
                            Text(
                                text = stringResource(
                                    R.string.detail_type_pid,
                                    typeLabel(current.illustType),
                                    current.id,
                                ),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                            Spacer(Modifier.height(6.dp))

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
                            // 5. caption
                            val description = plainCaption(current.description)
                            if (description.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                IntroCard(text = description, colors = colors)
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
                            )
                        }

                        LinkedWorksSection(
                            descriptionHtml = current.description,
                            currentKind = "illust",
                            currentId = current.id,
                            series = current.series,
                            api = api,
                            onOpen = { work -> onOpenLinked(work.kind, work.id) },
                        )

                        // 6. tags, wrapped
                        if (current.tags.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.detail_tags),
                                kind = CouiTextKind.GroupTitle,
                                color = colors.summary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                            )
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                current.tags.forEach { tag ->
                                    TagChip(text = tag, onClick = { onSearchTag(tag) })
                                }
                            }
                        }

                        // Clear space for the floating bottom Navigation bar.
                        Spacer(Modifier.height(104.dp + navBottom))
                    }
                }

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
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PixivApi.PAGE_URL + current.id)),
                        )
                    },
                    onCopyLink = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("pixiv", PixivApi.PAGE_URL + current.id))
                        Toast.makeText(context, resources.getString(R.string.detail_link_copied), Toast.LENGTH_SHORT).show()
                    },
                )

                val favoriteColor = if (effectDark) CouiColor.Night.RED else CouiColor.Day.RED
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
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(PixivApi.PAGE_URL + current.id)),
                                )
                            },
                        ),
                    ),
                    primaryIconRes = R.drawable.ic_view_large,
                    primaryDescription = stringResource(R.string.detail_action_view_image),
                    onPrimaryClick = { onOpenViewer(currentCoverPage) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                }
            }
        }
    }

    LoadingDialog(
        visible = downloading != null,
        message = downloading ?: stringResource(R.string.search_loading),
    )
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
                    ),
                    onItemClick = { item ->
                        menuExpanded = false
                        when (item.value) {
                            0 -> onToggleFavorite()
                            1 -> onDownload()
                            2 -> onOpenBrowser()
                            3 -> onCopyLink()
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
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val coverHeight = (maxWidth / ratio).coerceIn(180.dp, 520.dp)
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
