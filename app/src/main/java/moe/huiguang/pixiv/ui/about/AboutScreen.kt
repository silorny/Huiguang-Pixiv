package moe.huiguang.pixiv.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.huiguang.pixiv.BuildConfig
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.net.AppUpdateService
import moe.huiguang.pixiv.data.store.AppLanguage
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.NextChevron
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar
import moe.huiguang.pixiv.ui.common.openExternalUrl
import okhttp3.Request
import org.json.JSONObject

private const val HUIGUANG_REPO_URL = "https://github.com/silorny/Huiguang-Pixiv"
private const val TELEGRAM_GROUP_URL = "https://t.me/+PYSc-rhPaWs4Mjhl"
private const val AUTHOR_GITHUB_HANDLE = "silorny"
private const val AUTHOR_GITHUB_URL = "https://github.com/silorny"
private const val AUTHOR_GITHUB_API = "https://api.github.com/users/silorny"
private const val AUTHOR_GITHUB_AVATAR_FALLBACK = "https://github.com/silorny.png"

// Outer group radius is CouiPageDefaults.shape (24.dp, weight 3f);
// with 12.dp inner padding, concentric inner icon/avatar radius is 24 - 12 = 12.dp.
private val ConcentricInnerShape = CouiShape(12.dp, 3f)

/**
 * Isolated, stateless HTTP client for fetching the developer's public GitHub
 * profile and avatar without Pixiv Referer, cookies, locale headers, or
 * device-identifying User-Agent strings.
 */
private object GitHubPublicClient {
    @Volatile
    private var cachedImageLoader: ImageLoader? = null

    fun imageLoader(context: Context): ImageLoader =
        cachedImageLoader ?: synchronized(this) {
            cachedImageLoader ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { AppUpdateService.publicHttp }))
                }
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
                .also { cachedImageLoader = it }
        }
}

/**
 * About screen: WorkRow-style app identity card using the live launcher icon,
 * followed by grouped version, legal/compliance, and community sections.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenDoc: (String) -> Unit,
    settings: SettingsStore? = null,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val showUpdateSheet = remember { mutableStateOf(false) }
    var updateCheckKey by remember { mutableIntStateOf(0) }
    var showRepoDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }

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
            Column(
                Modifier
                    .fillMaxSize()
                    .couiVerticalScroll(scroll, overscrollEffect = overscroll)
                    .padding(top = statusTop + 56.dp, bottom = navBottom + 32.dp),
            ) {
                AboutAppCard()

                AboutAuthorCard(
                    onClick = { openExternalUrl(context, AUTHOR_GITHUB_URL) },
                )

                Group(title = stringResource(R.string.about_group_version)) {
                    Item(
                        trailing = {
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                kind = CouiTextKind.ItemValue,
                                color = colors.summary,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.about_current_version),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    Divider()
                    Item(
                        onClick = {
                            updateCheckKey++
                            showUpdateSheet.value = true
                        },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_check_update),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }

                Group(title = stringResource(R.string.about_group_legal)) {
                    Item(
                        onClick = { onOpenDoc("agreement") },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_user_agreement),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    Divider()
                    Item(
                        onClick = { onOpenDoc("copyright") },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_copyright_disclaimer),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    Divider()
                    Item(
                        onClick = { onOpenDoc("licenses") },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_open_source),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }

                Group(title = stringResource(R.string.about_group_community)) {
                    Item(
                        onClick = { onOpenDoc("ack") },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_acknowledgements),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    Divider()
                    Item(
                        onClick = { showRepoDialog = true },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_github_repo),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    Divider()
                    Item(
                        onClick = { showTelegramDialog = true },
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_telegram_group),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }
            }
        }

        FloatingSecondaryTopBar(
            title = stringResource(R.string.about_title),
            backdrop = backdrop,
            onBack = onBack,
        )
    }

    val appLanguage = settings?.state?.collectAsState()?.value?.language ?: AppLanguage.SYSTEM
    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    UpdateBottomSheet(
        visible = showUpdateSheet,
        checkKey = updateCheckKey,
        dark = dark,
        windowBackdrop = windowBackdrop,
        appLanguage = appLanguage,
        onSkipVersion = { tag -> settings?.setSkippedUpdateTag(tag) },
    )

    Dialog(
        visible = showRepoDialog,
        onDismissRequest = { showRepoDialog = false },
        title = stringResource(R.string.about_github_repo),
        message = stringResource(R.string.about_github_dialog_msg),
        material = dialogMaterial,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.about_github_open_repo),
            onClick = {
                showRepoDialog = false
                openExternalUrl(context, HUIGUANG_REPO_URL)
            },
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.about_github_copy),
            onClick = {
                showRepoDialog = false
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("Huiguang-Pixiv", HUIGUANG_REPO_URL))
                Toast.makeText(context, resources.getString(R.string.about_github_copied), Toast.LENGTH_SHORT).show()
            },
        ),
    )

    Dialog(
        visible = showTelegramDialog,
        onDismissRequest = { showTelegramDialog = false },
        title = stringResource(R.string.about_telegram_group),
        message = stringResource(R.string.about_telegram_dialog_msg),
        material = dialogMaterial,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.about_telegram_open),
            onClick = {
                showTelegramDialog = false
                openExternalUrl(context, TELEGRAM_GROUP_URL)
            },
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.about_github_copy),
            onClick = {
                showTelegramDialog = false
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("Huiguang-Telegram", TELEGRAM_GROUP_URL))
                Toast.makeText(context, resources.getString(R.string.about_telegram_copied), Toast.LENGTH_SHORT).show()
            },
        ),
    )
}

/**
 * App identity card sharing [CouiPageDefaults.shape] (24dp) with the option
 * groups below and using a concentric 12dp inner icon corner (24dp - 12dp).
 */
@Composable
private fun AboutAppCard() {
    val colors = CouiPageDefaults.themeColors()
    Group {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(ConcentricInnerShape)
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.24f
                            scaleY = 1.24f
                        },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_app_tagline),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Author GitHub card grouped under "作者", displaying the fetched GitHub
 * avatar (rounded rectangle, concentric 12dp) and username.
 */
@Composable
private fun AboutAuthorCard(
    onClick: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val githubImageLoader = remember(context) { GitHubPublicClient.imageLoader(context) }
    var username by rememberSaveable { mutableStateOf(AUTHOR_GITHUB_HANDLE) }
    var avatarUrl by rememberSaveable { mutableStateOf(AUTHOR_GITHUB_AVATAR_FALLBACK) }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(AUTHOR_GITHUB_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Huiguang")
                    .build()
                AppUpdateService.publicHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    JSONObject(resp.body.string())
                }
            }
        }.getOrNull()?.let { json ->
            val resolvedName = json.optString("name").takeIf { it.isNotBlank() }
                ?: json.optString("login").takeIf { it.isNotBlank() }
            val resolvedAvatar = json.optString("avatar_url").takeIf {
                it.startsWith("https://avatars.githubusercontent.com/") ||
                    it.startsWith("https://github.com/")
            }
            if (resolvedName != null) username = resolvedName
            if (resolvedAvatar != null) avatarUrl = resolvedAvatar
        }
    }

    Group(title = stringResource(R.string.about_group_author)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .couiButtonFeedback(dark, interaction, list = true)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = username,
                imageLoader = githubImageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ConcentricInnerShape)
                    .background(colors.background),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = username,
                    kind = CouiTextKind.ItemTitle,
                    color = colors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!username.equals(AUTHOR_GITHUB_HANDLE, ignoreCase = true)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "@$AUTHOR_GITHUB_HANDLE",
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class DocSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

private data class OpenSourceComponent(
    val name: String,
    val licenseShort: String,
    val licenseAsset: String,
    val homepageHost: String,
    val homepageUrl: String,
    val copyright: String,
    @param:StringRes val notesRes: Int? = null,
    val extraLicenseAsset: String? = null,
    val noticeAsset: String? = null,
)

private val OpenSourceComponents = listOf(
    OpenSourceComponent(
        name = "COUI-Kit (coui & coui-icons)",
        licenseShort = "MPL-2.0",
        licenseAsset = "mpl-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/ItosEO/COUI-Kit",
        copyright = "ItosEO & COUI-Kit Contributors",
        notesRes = R.string.license_coui_notes,
        extraLicenseAsset = "bsd-3-clause",
        noticeAsset = "coui-notice",
    ),
    OpenSourceComponent(
        name = "Kotlin & kotlinx.coroutines",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/JetBrains/kotlin",
        copyright = "JetBrains s.r.o. and Kotlin Contributors",
    ),
    OpenSourceComponent(
        name = "kotlinx.serialization",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/Kotlin/kotlinx.serialization",
        copyright = "JetBrains s.r.o.",
    ),
    OpenSourceComponent(
        name = "JetBrains Compose Multiplatform",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/JetBrains/compose-multiplatform",
        copyright = "JetBrains s.r.o. and Compose Multiplatform Contributors",
    ),
    OpenSourceComponent(
        name = "AndroidX & Jetpack Compose",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "developer.android.com",
        homepageUrl = "https://developer.android.com/jetpack/compose",
        copyright = "The Android Open Source Project",
    ),
    OpenSourceComponent(
        name = "Accompanist (DrawablePainter)",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "google.github.io",
        homepageUrl = "https://google.github.io/accompanist/",
        copyright = "Google LLC",
    ),
    OpenSourceComponent(
        name = "OkHttp",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/square/okhttp",
        copyright = "Square, Inc.",
    ),
    OpenSourceComponent(
        name = "Okio",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "square.github.io",
        homepageUrl = "https://square.github.io/okio/",
        copyright = "Square, Inc.",
    ),
    OpenSourceComponent(
        name = "Coil",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "coil-kt.github.io",
        homepageUrl = "https://coil-kt.github.io/coil/",
        copyright = "Coil Contributors",
    ),
    OpenSourceComponent(
        name = "JSpecify",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "jspecify.dev",
        homepageUrl = "https://jspecify.dev/",
        copyright = "The JSpecify Authors",
    ),
    OpenSourceComponent(
        name = "Guava ListenableFuture",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/google/guava",
        copyright = "Google LLC",
    ),
    OpenSourceComponent(
        name = "JetBrains Annotations",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/JetBrains/java-annotations",
        copyright = "JetBrains s.r.o.",
    ),
    OpenSourceComponent(
        name = "Material Icons (Vectors)",
        licenseShort = "Apache-2.0",
        licenseAsset = "apache-2.0",
        homepageHost = "github.com",
        homepageUrl = "https://github.com/google/material-design-icons",
        copyright = "Google LLC",
        notesRes = R.string.license_material_icons_notes,
    ),
)

/**
 * Structured secondary reader for User Agreement & Privacy, Copyright & Disclaimer,
 * Open-Source Licenses, Special Acknowledgements, and verbatim license texts.
 */
@Composable
fun AboutDocScreen(
    doc: String,
    onBack: () -> Unit,
    onOpenDoc: (String) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val scroll = rememberScrollState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val title = when (doc) {
        "agreement" -> stringResource(R.string.about_user_agreement)
        "copyright" -> stringResource(R.string.about_copyright_disclaimer)
        "licenses" -> stringResource(R.string.about_open_source)
        "ack" -> stringResource(R.string.about_acknowledgements)
        "license:mpl-2.0" -> "Mozilla Public License 2.0"
        "license:apache-2.0" -> "Apache License 2.0"
        "license:bsd-3-clause" -> "BSD 3-Clause License"
        "license:coui-notice" -> "COUI-Kit NOTICE"
        else -> stringResource(R.string.about_title)
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
            Column(
                Modifier
                    .fillMaxSize()
                    .couiVerticalScroll(scroll, overscrollEffect = overscroll)
                    .padding(top = statusTop + 56.dp + 8.dp, bottom = navBottom + 32.dp),
            ) {
                when {
                    doc == "agreement" -> SectionedDocContent(
                        introRes = R.string.about_agreement_intro,
                        sections = listOf(
                            DocSection(R.string.about_agreement_sec_service_title, R.string.about_agreement_sec_service_body),
                            DocSection(R.string.about_agreement_sec_network_title, R.string.about_agreement_sec_network_body),
                            DocSection(R.string.about_agreement_sec_storage_title, R.string.about_agreement_sec_storage_body),
                            DocSection(R.string.about_agreement_sec_privacy_title, R.string.about_agreement_sec_privacy_body),
                        ),
                    )

                    doc == "copyright" -> SectionedDocContent(
                        introRes = null,
                        sections = listOf(
                            DocSection(R.string.about_copyright_sec_works_title, R.string.about_copyright_sec_works_body),
                            DocSection(R.string.about_copyright_sec_ui_title, R.string.about_copyright_sec_ui_body),
                            DocSection(R.string.about_copyright_sec_disclaimer_title, R.string.about_copyright_sec_disclaimer_body),
                        ),
                    )

                    doc == "ack" -> SectionedDocContent(
                        introRes = R.string.about_ack_intro,
                        sections = listOf(
                            DocSection(R.string.about_ack_coui_title, R.string.about_ack_coui_body),
                            DocSection(R.string.about_ack_pixiv_title, R.string.about_ack_pixiv_body),
                            DocSection(R.string.about_ack_oss_title, R.string.about_ack_oss_body),
                            DocSection(R.string.about_ack_testers_title, R.string.about_ack_testers_body),
                        ),
                    )

                    doc == "licenses" -> OpenSourceLicensesContent(onOpenDoc = onOpenDoc)

                    doc.startsWith("license:") -> VerbatimLicenseContent(
                        assetName = doc.removePrefix("license:"),
                    )

                    else -> Group {
                        BasicText(
                            text = stringResource(R.string.about_body),
                            style = TextStyle(color = colors.title, fontSize = 14.5.sp, lineHeight = 23.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }

        FloatingSecondaryTopBar(
            title = title,
            backdrop = backdrop,
            onBack = onBack,
        )
    }
}

@Composable
private fun SectionedDocContent(
    @StringRes introRes: Int?,
    sections: List<DocSection>,
) {
    val colors = CouiPageDefaults.themeColors()
    if (introRes != null) {
        Text(
            text = stringResource(introRes),
            kind = CouiTextKind.ItemSummary,
            color = colors.summary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    sections.forEach { section ->
        Group(title = stringResource(section.titleRes)) {
            BasicText(
                text = stringResource(section.bodyRes),
                style = TextStyle(
                    color = colors.title,
                    fontSize = 14.5.sp,
                    lineHeight = 23.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun OpenSourceLicensesContent(
    onOpenDoc: (String) -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current

    Text(
        text = stringResource(R.string.about_licenses_intro),
        kind = CouiTextKind.ItemSummary,
        color = colors.summary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )

    OpenSourceComponents.forEach { component ->
        var notesExpanded by rememberSaveable(component.name) { mutableStateOf(false) }

        Group(title = component.name) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 360f)),
            ) {
                Item(
                    trailing = {
                        Text(
                            text = component.licenseShort,
                            kind = CouiTextKind.ItemValue,
                            color = colors.accent,
                        )
                    },
                    onClick = { onOpenDoc("license:${component.licenseAsset}") },
                ) {
                    Text(
                        text = stringResource(R.string.license_field_type),
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
                Divider()
                Item(
                    trailing = {
                        Text(
                            text = component.homepageHost,
                            kind = CouiTextKind.ItemValue,
                            color = colors.summary,
                        )
                    },
                    onClick = { openExternalUrl(context, component.homepageUrl) },
                ) {
                    Text(
                        text = stringResource(R.string.license_field_homepage),
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
                Divider()
                Item(
                    summary = {
                        Text(
                            text = component.copyright,
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.license_field_copyright),
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }

                if (component.notesRes != null) {
                    Divider()
                    Item(
                        trailing = {
                            Text(
                                text = stringResource(
                                    if (notesExpanded) R.string.license_notes_collapse else R.string.license_notes_expand,
                                ),
                                kind = CouiTextKind.ItemValue,
                                color = colors.accent,
                            )
                        },
                        onClick = { notesExpanded = !notesExpanded },
                    ) {
                        Text(
                            text = stringResource(R.string.license_field_notes),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    AnimatedVisibility(visible = notesExpanded) {
                        Column(Modifier.fillMaxWidth()) {
                            BasicText(
                                text = stringResource(component.notesRes),
                                style = TextStyle(
                                    color = colors.summary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 21.sp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            )
                            if (component.extraLicenseAsset != null) {
                                Divider()
                                Item(onClick = { onOpenDoc("license:${component.extraLicenseAsset}") }) {
                                    Text(
                                        text = stringResource(R.string.license_view_bsd_text),
                                        kind = CouiTextKind.ItemTitle,
                                        color = colors.accent,
                                    )
                                }
                            }
                            if (component.noticeAsset != null) {
                                Divider()
                                Item(onClick = { onOpenDoc("license:${component.noticeAsset}") }) {
                                    Text(
                                        text = stringResource(R.string.license_view_coui_notice),
                                        kind = CouiTextKind.ItemTitle,
                                        color = colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerbatimLicenseContent(assetName: String) {
    val colors = CouiPageDefaults.themeColors()
    val context = LocalContext.current
    val licenseText = remember(assetName) {
        runCatching {
            context.assets.open("licenses/$assetName.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { "" }
    }
    Group {
        BasicText(
            text = licenseText,
            style = TextStyle(
                color = colors.title,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}
