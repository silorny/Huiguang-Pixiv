package moe.huiguang.pixiv.ui.pixivision

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarSurface
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.PixivisionDetail
import moe.huiguang.pixiv.data.model.PixivisionWork
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.repo.EngagementEvent
import moe.huiguang.pixiv.data.repo.EngagementKind
import moe.huiguang.pixiv.data.repo.EngagementRepository
import moe.huiguang.pixiv.data.store.FavoriteEntry
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.GlowChip
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.design.components.RetryableImage
import moe.huiguang.pixiv.design.components.TagChip
import moe.huiguang.pixiv.ui.common.IntroCard

private sealed interface PixivisionUiState {
    data object Loading : PixivisionUiState
    data class Content(val detail: PixivisionDetail) : PixivisionUiState
    data class Error(val message: String) : PixivisionUiState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixivisionScreen(
    articleId: String,
    api: PixivApi,
    engagement: EngagementRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenWork: (kind: String, id: String) -> Unit,
    onOpenAuthor: (uid: String) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val effectDark = CouiTheme.isDark
    val context = LocalContext.current
    val resources = LocalResources.current
    val backdrop = rememberCouiBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val listState = rememberLazyListState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val settingsState by settings.state.collectAsState()
    val bookmarkedIllusts by engagement.bookmarkedIllusts.collectAsState()
    val bookmarkedNovels by engagement.bookmarkedNovels.collectAsState()
    val followedAuthors by engagement.followedAuthors.collectAsState()

    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember(articleId) { mutableStateOf<PixivisionUiState>(PixivisionUiState.Loading) }

    LaunchedEffect(engagement) {
        engagement.events.collect { event ->
            when (event) {
                is EngagementEvent.Succeeded -> {
                    if (event.kind != EngagementKind.FOLLOW) {
                        Toast.makeText(
                            context,
                            resources.getString(if (event.active) R.string.detail_favorited else R.string.detail_unfavorited),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                is EngagementEvent.Failed -> {
                    Toast.makeText(
                        context,
                        event.reason?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.action_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(articleId, reloadKey) {
        state = PixivisionUiState.Loading
        runCatching { api.pixivisionDetail(articleId) }
            .onSuccess { detail ->
                state = if (detail != null) {
                    PixivisionUiState.Content(detail)
                } else {
                    PixivisionUiState.Error(resources.getString(R.string.pixivision_works_empty))
                }
            }
            .onFailure { error ->
                state = PixivisionUiState.Error(error.message ?: resources.getString(R.string.error_load_failed))
            }
    }

    val showBarTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }
    val articleUrl = (state as? PixivisionUiState.Content)?.detail?.articleUrl
        ?: "https://www.pixivision.net/zh/a/$articleId"
    val barTitle = (state as? PixivisionUiState.Content)?.detail?.title
        ?: stringResource(R.string.pixivision_title)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when (val current = state) {
            is PixivisionUiState.Loading -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.9f)
                            .background(colors.card, CouiShape(20.dp, 3f)),
                    )
                    repeat(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(colors.card, CouiShape(20.dp, 3f)),
                        )
                    }
                }
            }

            is PixivisionUiState.Error -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = current.message, kind = CouiTextKind.ItemSummary, color = colors.summary)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = stringResource(R.string.action_retry),
                        onClick = { reloadKey++ },
                    )
                }
            }

            is PixivisionUiState.Content -> {
                val detail = current.detail
                Box(
                    Modifier
                        .fillMaxSize()
                        .couiBackdrop(backdrop)
                        .background(colors.background),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overscroll(overscroll),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = navBottom + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 1. 特辑封面
                        item(key = "cover") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(top = 64.dp, start = 16.dp, end = 16.dp),
                            ) {
                                RetryableImage(
                                    url = detail.coverUrl,
                                    contentDescription = detail.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.9f)
                                        .clip(CouiShape(20.dp, 3f)),
                                )
                            }
                        }

                        // 2. 特辑类型（插画/漫画/小说） + 时间
                        if (detail.category.isNotEmpty() || detail.date.isNotEmpty()) {
                            item(key = "meta") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (detail.category.isNotEmpty()) {
                                        Box(
                                            Modifier
                                                .background(
                                                    colors.accent.copy(alpha = 0.12f),
                                                    CouiShape(10.dp, 3f),
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) {
                                            BasicText(
                                                text = detail.category,
                                                style = TextStyle(
                                                    color = colors.accent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }
                                    if (detail.date.isNotEmpty()) {
                                        Text(
                                            text = detail.date,
                                            kind = CouiTextKind.ItemSummary,
                                            color = colors.summary,
                                        )
                                    }
                                }
                            }
                        }

                        // 3. 标题
                        item(key = "title") {
                            BasicText(
                                text = detail.title,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = TextStyle(
                                    color = colors.title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 30.sp,
                                ),
                            )
                        }

                        // 4. 标签
                        if (detail.tags.isNotEmpty()) {
                            item(key = "tags") {
                                FlowRow(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    detail.tags.forEach { tag ->
                                        TagChip(text = tag, onClick = { onSearchTag(tag) })
                                    }
                                }
                            }
                        }

                        // 5. 简介（白色圆角卡片 + 蓝色展开简介 ChipTab）
                        if (detail.intro.isNotEmpty()) {
                            item(key = "intro") {
                                IntroCard(
                                    text = detail.intro,
                                    colors = colors,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    collapsedLines = 6,
                                )
                            }
                        }

                        // 6. 作者头像 + 作品名称 / 作者名称 + 关注按钮 + 特辑作品 + 收藏按钮
                        if (detail.works.isEmpty()) {
                            item(key = "works-empty") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.pixivision_works_empty),
                                        kind = CouiTextKind.ItemSummary,
                                        color = colors.summary,
                                    )
                                }
                            }
                        } else {
                            items(detail.works, key = { "${it.kind}-${it.workId}-${it.imageUrl}" }) { work ->
                                val isNovel = work.kind == "novel"
                                val isFollowed = work.authorId.isNotEmpty() && followedAuthors[work.authorId] == true
                                val isFavorite = if (isNovel) {
                                    bookmarkedNovels[work.workId] == true
                                } else {
                                    bookmarkedIllusts[work.workId] == true
                                }
                                PixivisionWorkCard(
                                    work = work,
                                    isFollowed = isFollowed,
                                    isFavorite = isFavorite,
                                    dark = effectDark,
                                    onOpenWork = { onOpenWork(work.kind, work.workId) },
                                    onOpenAuthor = {
                                        if (work.authorId.isNotEmpty()) onOpenAuthor(work.authorId)
                                    },
                                    onToggleFollow = {
                                        if (settingsState.session == null) {
                                            Toast.makeText(context, resources.getString(R.string.bookmarks_need_login), Toast.LENGTH_SHORT).show()
                                        } else {
                                            engagement.toggleFollow(work.authorId)
                                        }
                                    },
                                    onToggleFavorite = {
                                        if (settingsState.session == null) {
                                            Toast.makeText(context, resources.getString(R.string.bookmarks_need_login), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val entry = FavoriteEntry(
                                                id = work.workId,
                                                title = work.title,
                                                thumbUrl = work.imageUrl,
                                                userName = work.authorName,
                                                userId = work.authorId,
                                                kind = if (isNovel) "novel" else "illust",
                                                savedAt = System.currentTimeMillis(),
                                                illustType = if (work.kind == "manga") 1 else if (isNovel) -1 else 0,
                                            )
                                            engagement.toggleBookmark(entry)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        PixivisionTopBar(
            title = barTitle,
            showTitle = showBarTitle,
            backdrop = backdrop,
            onBack = onBack,
            onOpenInBrowser = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(articleUrl)))
                }
            },
            onCopyLink = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("pixivision", articleUrl))
                Toast.makeText(context, resources.getString(R.string.detail_link_copied), Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun PixivisionWorkCard(
    work: PixivisionWork,
    isFollowed: Boolean,
    isFavorite: Boolean,
    dark: Boolean,
    onOpenWork: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val cardShape = CouiShape(20.dp, 3f)
    val headerBackdrop = rememberCouiBackdrop()
    val imageBackdrop = rememberCouiBackdrop()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(cardShape)
            .background(colors.card, cardShape),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .couiBackdrop(headerBackdrop)
                    .background(colors.card),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenWork)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RetryableImage(
                    url = work.authorAvatarUrl,
                    contentDescription = work.authorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = work.authorId.isNotEmpty(), onClick = onOpenAuthor),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        text = work.title.ifEmpty { stringResource(R.string.untitled_work) },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = colors.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    if (work.authorName.isNotEmpty()) {
                        BasicText(
                            text = work.authorName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = work.authorId.isNotEmpty(), onClick = onOpenAuthor),
                            style = TextStyle(
                                color = colors.summary,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
                if (work.authorId.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    GlowChip(
                        text = stringResource(if (isFollowed) R.string.author_followed else R.string.author_following),
                        selected = isFollowed,
                        onClick = onToggleFollow,
                        backdrop = headerBackdrop,
                        dark = dark,
                    )
                }
            }
        }
        if (work.imageUrl.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .couiBackdrop(imageBackdrop)
                        .clickable(onClick = onOpenWork),
                ) {
                    RetryableImage(
                        url = work.imageUrl,
                        contentDescription = work.title,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                GlowChip(
                    text = stringResource(if (isFavorite) R.string.detail_favorited_short else R.string.detail_favorite),
                    selected = isFavorite,
                    onClick = onToggleFavorite,
                    backdrop = imageBackdrop,
                    dark = dark,
                    blurRadius = 24f,
                    staticSurface = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PixivisionTopBar(
    title: String,
    showTitle: Boolean,
    backdrop: CouiBackdrop,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val effectDark = CouiTheme.isDark
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBarSurface(
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop,
        backgroundAlpha = 1f,
        gradientColor = colors.background,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = CouiIcons.Filled.Back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                backdrop = backdrop,
                dark = effectDark,
                blurRadius = 20f,
            )
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                BasicText(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = colors.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            if (!showTitle) {
                Spacer(Modifier.weight(1f))
            }
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
                        CouiMenuItem(value = 0, text = stringResource(R.string.detail_open_browser)),
                        CouiMenuItem(value = 1, text = stringResource(R.string.detail_copy_link)),
                    ),
                    onItemClick = { item ->
                        menuExpanded = false
                        when (item.value) {
                            0 -> onOpenInBrowser()
                            1 -> onCopyLink()
                        }
                    },
                )
            }
        }
    }
}
