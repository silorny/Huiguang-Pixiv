package moe.huiguang.pixiv.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import com.itos.coui.scroll.couiVerticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itos.coui.layout.CouiPageColors
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.UserProfile
import moe.huiguang.pixiv.data.net.PixivApi
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.FloatingTabHeader
import moe.huiguang.pixiv.design.components.GlowStatCapsule
import moe.huiguang.pixiv.design.components.SettingSwitchRow
import moe.huiguang.pixiv.design.components.StatValue
import moe.huiguang.pixiv.design.components.isDarkBackdrop

/**
 * "我的" tab: account card (avatar / name / UID), a read-only statistics
 * capsule, then the browse, settings and about entries. Signing out lives in
 * Settings, not here.
 */
@Composable
fun ProfileTab(
    padding: PaddingValues,
    settings: SettingsStore,
    api: PixivApi,
    profileCache: SelfProfileCache,
    onLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenProfileList: (uid: String, kind: String) -> Unit = { _, _ -> },
) {
    val colors = CouiPageDefaults.themeColors()
    val state by settings.state.collectAsState()
    val session = state.session

    var profile by remember(session?.userId) { mutableStateOf<UserProfile?>(null) }
    var worksCount by remember(session?.userId) { mutableStateOf(0) }
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(session?.userId, resumeTick) {
        val uid = session?.userId ?: return@LaunchedEffect
        profileCache.get(uid)?.let {
            profile = it.profile
            worksCount = it.worksCount
        }
        val loaded = runCatching { api.user(uid) }.getOrNull() ?: return@LaunchedEffect
        val works = runCatching { api.userWorkIds(uid).size }.getOrDefault(0)
        profile = loaded
        worksCount = works
        profileCache.put(uid, SelfProfileCache.Entry(loaded, works))
    }

    val backdrop = com.itos.coui.backdrop.rememberCouiBackdrop()
    // Flat sibling source for the account stats capsule.
    val effectBackdrop = com.itos.coui.backdrop.rememberCouiBackdrop()
    val effectDark = isDarkBackdrop(colors.background)
    val scroll = rememberScrollState()
    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val collapsed by remember { androidx.compose.runtime.derivedStateOf { scroll.value > 24 } }

    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .couiBackdrop(effectBackdrop)
            .background(colors.background),
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .couiVerticalScroll(scroll)
            .padding(padding)
            .consumeWindowInsets(padding),
    ) {
        Spacer(Modifier.height(statusTop + 64.dp))
        if (session == null) {
            Group(title = stringResource(R.string.profile_group_account)) {
                Item(
                    summary = {
                        Text(
                            text = stringResource(R.string.profile_login_summary),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    },
                    onClick = onLogin,
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
            }
        } else {
            Group(title = stringResource(R.string.profile_group_account)) {
                AccountHeader(
                    profile = profile,
                    uid = session.userId,
                    colors = colors,
                )
            }

            // Separate from the identity card: same capsule as the segmented
            // control, but without indicator or touch handling.
            GlowStatCapsule(
                stats = listOf(
                    StatValue("${profile?.following ?: 0}", stringResource(R.string.profile_following)) {
                        onOpenProfileList(session.userId, "following")
                    },
                    StatValue("${profile?.mypixivCount ?: 0}", stringResource(R.string.profile_mypixiv)) {
                        onOpenProfileList(session.userId, "mypixiv")
                    },
                    StatValue("$worksCount", stringResource(R.string.profile_works)) {
                        onOpenProfileList(session.userId, "works")
                    },
                ),
                backdrop = effectBackdrop,
                dark = effectDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        Group(title = stringResource(R.string.profile_group_content)) {
            SettingSwitchRow(
                title = stringResource(R.string.profile_r18),
                summary = stringResource(R.string.profile_r18_summary),
                checked = state.revealR18,
                onCheckedChange = { settings.setRevealR18(it) },
            )
        }

        Group(title = stringResource(R.string.profile_group_browse)) {
            Item(onClick = onOpenHistory) {
                Text(
                    text = stringResource(R.string.history_title),
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                )
            }
            Divider()
            Item(onClick = onOpenBookmarks) {
                Text(
                    text = stringResource(R.string.bookmarks_title),
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                )
            }
        }

        Group(title = stringResource(R.string.settings_title)) {
            Item(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.settings_title),
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                )
            }
        }

        // Clear the floating navigation capsule, including at large font scales.
        Spacer(Modifier.height(24.dp))
    }

    FloatingTabHeader(
        title = stringResource(R.string.tab_me),
        backdrop = backdrop,
        collapsed = collapsed,
        statusTop = statusTop,
    )
    }
}

@Composable
private fun AccountHeader(
    profile: UserProfile?,
    uid: String,
    colors: CouiPageColors,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile != null && profile.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.background),
            )
        } else {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = profile?.name?.take(1) ?: "?",
                    style = TextStyle(color = colors.accent, fontSize = 22.sp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = profile?.name ?: stringResource(R.string.search_loading),
                kind = CouiTextKind.ItemTitle,
                color = colors.title,
                maxLines = 1,
            )
            Text(
                text = "UID $uid",
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                maxLines = 1,
            )
        }
    }
}
