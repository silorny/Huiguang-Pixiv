package moe.huiguang.pixiv.ui.novel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.model.CaptionTarget
import moe.huiguang.pixiv.data.model.NovelBlocks
import moe.huiguang.pixiv.data.model.NovelDetail
import moe.huiguang.pixiv.data.model.ShareFormatter
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.design.components.ActionNavItem
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.FloatingActionNavBar
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.design.components.TagChip
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.ui.common.ExternalLinkConfirmDialog
import moe.huiguang.pixiv.ui.common.IntroCard
import moe.huiguang.pixiv.ui.common.LinkedWorksSection
import moe.huiguang.pixiv.ui.common.SeriesSection
import moe.huiguang.pixiv.ui.common.WorkMetaHeader
import moe.huiguang.pixiv.ui.common.copyPlainText
import moe.huiguang.pixiv.ui.common.openExternalUrl
import moe.huiguang.pixiv.ui.detail.plainCaption

/**
 * Novel detail: a floating blurred header over the content, then cover/title,
 * type/PID, author, stats, description, series, tags and the action group.
 */
@Composable
fun NovelDetailScreen(
    novelId: String,
    api: PixivApi,
    cache: NovelCache,
    downloads: ImageDownloader,
    engagement: EngagementRepository,
    onBack: () -> Unit,
    onStartReading: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenLinked: (String, String) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenAsIllust: (String) -> Unit,
    settings: moe.huiguang.pixiv.data.store.SettingsStore,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberCouiBackdrop()
    // Flat sibling source for the stats capsule (the scrolling content is the
    // backdrop of the floating header, so it cannot host the capsule itself).
    val effectBackdrop = rememberCouiBackdrop()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val effectDark = isDarkBackdrop(colors.background)
    val collapseDistancePx = with(density) { 112.dp.toPx() }
    val collapseProgress by remember(scroll, collapseDistancePx) {
        derivedStateOf { (scroll.value / collapseDistancePx).coerceIn(0f, 1f) }
    }

    val cached = remember(novelId) { cache.get(novelId) }
    var novel by remember(novelId) { mutableStateOf(cached) }
    var loading by remember { mutableStateOf(cached == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var avatar by remember(novelId) { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val bookmarkedNovels by engagement.bookmarkedNovels.collectAsState()
    val followedAuthors by engagement.followedAuthors.collectAsState()
    val isFavorite = bookmarkedNovels[novelId] ?: (novel?.bookmarkId != null)
    val settingsState by settings.state.collectAsState()
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var externalLinkDialogVisible by remember { mutableStateOf(false) }
    val dialogDark = CouiTheme.isDark
    val windowBackdrop = rememberCouiWindowBackdrop()
    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dialogDark) else null

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engagement, novelId, novel?.userId) {
        engagement.events.collect { event ->
            val currentWork = novel
            when (event) {
                is EngagementEvent.Succeeded -> {
                    val matchesWork = event.kind == EngagementKind.NOVEL && event.id == novelId
                    if (matchesWork && currentWork != null) {
                        val bid = if (event.active) engagement.bookmarkIdOf(novelId, novel = true) else null
                        val updated = currentWork.copy(bookmarkId = bid)
                        novel = updated
                        cache.put(novelId, updated)
                        Toast.makeText(
                            context,
                            resources.getString(if (event.active) R.string.detail_favorited else R.string.detail_unfavorited),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                is EngagementEvent.Failed -> {
                    val matchesWork = event.kind == EngagementKind.NOVEL && event.id == novelId
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

    LaunchedEffect(novelId, reloadKey) {
        if (novel != null && reloadKey == 0) return@LaunchedEffect
        loading = true
        error = null
        runCatching { api.novel(novelId) ?: error(resources.getString(R.string.error_novel_not_found)) }
            .onSuccess {
                cache.put(novelId, it)
                novel = it
            }
            .onFailure { error = it.message ?: resources.getString(R.string.error_load_failed) }
        loading = false
    }

    LaunchedEffect(novelId, resumeTick) {
        val fresh = runCatching { api.novel(novelId) }.getOrNull() ?: return@LaunchedEffect
        novel = fresh
        cache.put(novelId, fresh)
        engagement.syncBookmark(fresh.id, novel = true, bookmarkId = fresh.bookmarkId)
    }

    val current = novel

    LaunchedEffect(current?.id) {
        val work = current ?: return@LaunchedEffect
        settings.addHistory(
            moe.huiguang.pixiv.data.model.HistoryEntry(
                pid = work.id,
                kind = moe.huiguang.pixiv.data.model.HistoryEntry.KIND_NOVEL,
                title = work.title,
                thumbUrl = work.coverUrl,
                at = System.currentTimeMillis(),
                userName = work.userName,
                illustType = -1,
            ),
        )
    }

    LaunchedEffect(current?.userId, resumeTick) {
        val uid = current?.userId ?: return@LaunchedEffect
        runCatching { api.user(uid) }.getOrNull()?.let { u ->
            engagement.syncFollow(uid, u.isFollowed)
            if (avatar == null && u.avatarUrl.isNotEmpty()) {
                avatar = u.avatarUrl
            }
        }
    }

    LaunchedEffect(current?.id, current?.bookmarkId) {
        val work = current ?: return@LaunchedEffect
        engagement.syncBookmark(
            id = work.id,
            novel = true,
            bookmarkId = work.bookmarkId,
            entry = FavoriteEntry(
                id = work.id,
                title = work.title,
                thumbUrl = work.coverUrl,
                userName = work.userName,
                userId = work.userId,
                kind = "novel",
                illustType = -1,
                savedAt = System.currentTimeMillis(),
            ),
        )
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
            thumbUrl = work.coverUrl,
            userName = work.userName,
            userId = work.userId,
            kind = "novel",
            illustType = -1,
            savedAt = System.currentTimeMillis(),
        )
        engagement.toggleBookmark(entry)
    }

    fun toggleFollow() {
        val uid = current?.userId ?: return
        if (settingsState.session == null) {
            Toast.makeText(context, resources.getString(R.string.bookmarks_need_login), Toast.LENGTH_SHORT).show()
            return
        }
        engagement.toggleFollow(uid)
    }

    fun exportTxt() {
        val work = current ?: return
        exporting = true
        scope.launch {
            val body = NovelBlocks.toPlainText(
                NovelBlocks.build(work.content, work.embeddedImages),
            )
            val header = buildString {
                append(work.title).append('\n')
                append(resources.getString(R.string.novel_export_author_line, work.userName)).append('\n')
                append("https://www.pixiv.net/novel/show.php?id=").append(work.id).append("\n\n")
            }
            val name = ImageDownloader.safeName("pixiv_novel_${work.id}_${work.title}.txt", "pixiv_novel_${work.id}.txt")
            val result = runCatching { downloads.saveText(context, name, header + body) }
            exporting = false
            Toast.makeText(
                context,
                resources.getString(
                    if (result.isSuccess) R.string.viewer_saved else R.string.viewer_save_failed,
                    result.getOrNull() ?: "",
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(effectBackdrop)
                .background(colors.background),
        )
        Box(
            Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(colors.background),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .couiVerticalScroll(scroll),
            ) {
                Spacer(Modifier.height(statusTop + 52.dp + 8.dp))

                if (current == null && loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_loading),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                } else if (current == null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
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
                        Box(Modifier.clickable { onOpenAsIllust(novelId) }) {
                            Text(
                                text = stringResource(R.string.detail_try_illust),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.accent,
                            )
                        }
                    }
                } else {
                    NovelDetailContent(
                        novel = current,
                        avatar = avatar,
                        colors = colors,
                        backdrop = effectBackdrop,
                        dark = effectDark,
                        api = api,
                        isFollowed = followedAuthors[current.userId] == true,
                        onToggleFollow = { toggleFollow() },
                        collapseProgress = { collapseProgress },
                        onOpenAuthor = onOpenAuthor,
                        onOpenSeries = onOpenSeries,
                        onOpenLinked = onOpenLinked,
                        onSearchTag = onSearchTag,
                        onExternalLink = { url ->
                            pendingExternalUrl = url
                            externalLinkDialogVisible = true
                        },
                    )
                }
            }
        }

        val defaultWorkTemplate = stringResource(R.string.share_format_default_work)
        NovelTopBar(
            backdrop = backdrop,
            dark = effectDark,
            title = current?.title ?: "",
            isFavorite = isFavorite,
            hasNovel = current != null,
            backgroundColor = colors.background,
            collapseProgress = { collapseProgress },
            onBack = onBack,
            onToggleFavorite = { toggleFavorite() },
            onExport = { exportTxt() },
            onOpenBrowser = {
                current?.let { work ->
                    openExternalUrl(context, PixivApi.NOVEL_PAGE_URL + work.id)
                }
            },
            onCopyLink = {
                current?.let { work ->
                    val shareText = ShareFormatter.formatWork(
                        template = settingsState.workShareFormat,
                        title = work.title,
                        userName = work.userName,
                        userId = work.userId,
                        illustId = work.id,
                        url = PixivApi.NOVEL_PAGE_URL + work.id,
                        tags = work.tags,
                        fallbackTemplate = defaultWorkTemplate,
                    )
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("pixiv", shareText))
                    Toast.makeText(context, resources.getString(R.string.detail_link_copied), Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                current?.let { work ->
                    val shareText = ShareFormatter.formatWork(
                        template = settingsState.workShareFormat,
                        title = work.title,
                        userName = work.userName,
                        userId = work.userId,
                        illustId = work.id,
                        url = PixivApi.NOVEL_PAGE_URL + work.id,
                        tags = work.tags,
                        fallbackTemplate = defaultWorkTemplate,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            },
        )

        if (current != null) {
            val favoriteColor = if (effectDark) CouiColor.Night.RED else CouiColor.Day.RED
            FloatingActionNavBar(
                backdrop = backdrop,
                dark = effectDark,
                primaryOnLeft = settingsState.leftHanded,
                items = listOf(
                    ActionNavItem(
                        label = stringResource(R.string.reader_export),
                        iconRes = R.drawable.ic_export,
                        onClick = { exportTxt() },
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
                            openExternalUrl(context, PixivApi.NOVEL_PAGE_URL + current.id)
                        },
                    ),
                ),
                primaryIconRes = R.drawable.ic_read_book,
                primaryDescription = stringResource(R.string.novel_start_reading),
                onPrimaryClick = onStartReading,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    LoadingDialog(
        visible = exporting,
        message = stringResource(R.string.novel_exporting),
    )

    pendingExternalUrl?.let { url ->
        ExternalLinkConfirmDialog(
            visible = externalLinkDialogVisible,
            url = url,
            dark = dialogDark,
            colors = colors,
            material = dialogMaterial,
            backdrop = windowBackdrop,
            onDismiss = { externalLinkDialogVisible = false },
            onCopyUrl = {
                copyPlainText(context, "novel_caption_link", url, resources.getString(R.string.detail_link_copied))
                externalLinkDialogVisible = false
            },
            onOpenUrl = {
                externalLinkDialogVisible = false
                openExternalUrl(context, url)
            },
        )
    }
}

/** Floating top bar with smooth scroll-linked title handoff and COUI overflow menu. */
@Composable
private fun NovelTopBar(
    backdrop: CouiBackdrop,
    dark: Boolean,
    title: String,
    isFavorite: Boolean,
    hasNovel: Boolean,
    backgroundColor: Color,
    collapseProgress: () -> Float,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onExport: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
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
                        val t = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * topRisePx
                    },
            )
            if (hasNovel) {
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
                        modifier = Modifier.widthIn(min = 168.dp),
                        offset = DpOffset(0.dp, 8.dp),
                        items = listOf(
                            CouiMenuItem(
                                value = 0,
                                text = stringResource(if (isFavorite) R.string.detail_favorited_short else R.string.detail_favorite),
                            ),
                            CouiMenuItem(value = 1, text = stringResource(R.string.novel_export_txt)),
                            CouiMenuItem(value = 2, text = stringResource(R.string.detail_open_browser)),
                            CouiMenuItem(value = 3, text = stringResource(R.string.detail_copy_link)),
                            CouiMenuItem(value = 4, text = stringResource(R.string.detail_action_share)),
                        ),
                        onItemClick = { item ->
                            menuExpanded = false
                            when (item.value) {
                                0 -> onToggleFavorite()
                                1 -> onExport()
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
}

@Composable
private fun NovelDetailContent(
    novel: NovelDetail,
    avatar: String?,
    colors: CouiPageColors,
    backdrop: CouiBackdrop,
    dark: Boolean,
    api: PixivApi,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    collapseProgress: () -> Float,
    onOpenAuthor: (String) -> Unit,
    onOpenSeries: (String, String) -> Unit,
    onOpenLinked: (String, String) -> Unit,
    onSearchTag: (String) -> Unit,
    onExternalLink: (String) -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val titleShiftPx = with(density) { 26.dp.toPx() }
    val displayTitle = novel.title.ifBlank { stringResource(R.string.untitled_work) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 104.dp + navBottom),
    ) {
        // Cover + title, top aligned.
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            AsyncImage(
                model = novel.coverUrl,
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
                    .clip(CouiShape(16.dp, 3f))
                    .background(colors.card),
            )
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f)) {
                val titleStyle = TextStyle(
                    color = colors.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 28.sp,
                )
                BasicText(
                    text = displayTitle,
                    style = titleStyle,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        val p = collapseProgress()
                        translationY = -titleShiftPx * p
                        alpha = (1f - p / 0.55f).coerceIn(0f, 1f)
                    },
                )
                BasicText(
                    text = displayTitle,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        val p = collapseProgress()
                        translationY = -titleShiftPx * p
                        val fadeIn = ((p - 0.25f) / 0.25f).coerceIn(0f, 1f)
                        val fadeOut = (1f - ((p - 0.52f) / 0.3f).coerceIn(0f, 1f))
                        alpha = fadeIn * fadeOut
                    },
                )
            }
        }

        // Type + PID + publish date sits below the cover, above the author.
        WorkMetaHeader(
            typeLabel = stringResource(R.string.filter_novel),
            id = novel.id,
            createDate = novel.createDate,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(8.dp))
        NovelAuthorRow(
            novel = novel,
            avatar = avatar,
            colors = colors,
            isFollowed = isFollowed,
            backdrop = backdrop,
            dark = dark,
            onToggleFollow = onToggleFollow,
            onClick = { onOpenAuthor(novel.userId) },
        )

        // Two capsules (reading size / reader reactions), no dividers.
        Spacer(Modifier.height(14.dp))
        GlowStatCapsule(
            stats = listOf(
                StatValue("${novel.characterCount}", stringResource(R.string.novel_chars)),
                StatValue(
                    "${(novel.readingTime / 60).coerceAtLeast(if (novel.readingTime > 0) 1 else 0)}",
                    stringResource(R.string.novel_minutes),
                ),
            ),
            backdrop = backdrop,
            dark = dark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        GlowStatCapsule(
            stats = listOf(
                StatValue("${novel.bookmarkCount}", stringResource(R.string.detail_bookmarks)),
                StatValue("${novel.viewCount}", stringResource(R.string.detail_views)),
                StatValue("${novel.likeCount}", stringResource(R.string.detail_likes)),
            ),
            backdrop = backdrop,
            dark = dark,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // Description.
        val description = plainCaption(novel.description)
        if (description.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.detail_intro),
                kind = CouiTextKind.GroupTitle,
                color = colors.summary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            IntroCard(
                text = description,
                colors = colors,
                rawHtml = novel.description,
                onInternalLink = { target ->
                    when (target) {
                        is CaptionTarget.Illust -> onOpenLinked("illust", target.id)
                        is CaptionTarget.Novel -> onOpenLinked("novel", target.id)
                        is CaptionTarget.User -> onOpenAuthor(target.id)
                        is CaptionTarget.External -> onExternalLink(target.url)
                    }
                },
                onExternalLink = onExternalLink,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // Series.
        novel.series?.let { series ->
            Spacer(Modifier.height(16.dp))
            SeriesSection(
                series = series,
                currentId = novel.id,
                isNovel = true,
                api = api,
                onOpenWork = { id -> onOpenLinked("novel", id) },
                onOpenSeries = { onOpenSeries(series.seriesId, novel.id) },
            )
        }

        LinkedWorksSection(
            descriptionHtml = novel.description,
            currentKind = "novel",
            currentId = novel.id,
            series = novel.series,
            api = api,
            onOpen = { work -> onOpenLinked(work.kind, work.id) },
            onOpenAuthor = onOpenAuthor,
        )

        // Tags: no group card, matching the artwork detail page.
        if (novel.tags.isNotEmpty()) {
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
                novel.tags.forEach { tag ->
                    TagChip(text = tag, onClick = { onSearchTag(tag) })
                }
            }
        }
    }
}

@Composable
private fun NovelAuthorRow(
    novel: NovelDetail,
    avatar: String?,
    colors: CouiPageColors,
    isFollowed: Boolean,
    backdrop: CouiBackdrop,
    dark: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(CouiCapsuleShape)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                        text = novel.userName.take(1).ifEmpty { "?" },
                        style = TextStyle(color = colors.accent, fontSize = 18.sp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = novel.userName.ifBlank { stringResource(R.string.unknown_author) },
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "UID ${novel.userId}",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (novel.userId.isNotEmpty()) {
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
