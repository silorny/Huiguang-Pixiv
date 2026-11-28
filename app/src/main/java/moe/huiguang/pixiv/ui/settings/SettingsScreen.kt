package moe.huiguang.pixiv.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.media.ImageDownloader
import moe.huiguang.pixiv.data.store.AppLanguage
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.data.store.ThemeMode
import moe.huiguang.pixiv.design.AdaptiveTokens
import moe.huiguang.pixiv.design.components.NextChevron
import moe.huiguang.pixiv.design.components.SettingSelectRow
import moe.huiguang.pixiv.design.components.SettingSwitchRow
import moe.huiguang.pixiv.ui.common.FloatingSecondaryTopBar

/** Settings: account summary, content, language, storage and about. */
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearCaches: () -> Unit,
    onOpenShareFormat: () -> Unit = {},
    onLoggedOut: () -> Unit = { settings.logout() },
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val backdrop = rememberCouiBackdrop()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val scroll = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by settings.state.collectAsState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun applyLanguage(language: AppLanguage) {
        settings.setLanguage(language)
        // minSdk 34: per-app locales are a platform feature; Activities handle locale configChanges in place.
        runCatching {
            val manager = context.getSystemService(android.app.LocaleManager::class.java)
            manager?.applicationLocales = if (language == AppLanguage.SYSTEM) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(language.tag)
            }
        }
    }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    val downloadDirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val raw = uri.toString()
        val isDefault = ImageDownloader.isDefaultDownloadUri(raw)
        val previousRaw = state.downloadTreeUri
        if (previousRaw.isNotBlank() && previousRaw != raw) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(previousRaw), flags)
            }
        }
        if (isDefault) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            }
            settings.setDownloadTreeUri("")
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            settings.setDownloadTreeUri(raw)
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
                .couiBackdrop(backdrop)
                .background(colors.background),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .couiVerticalScroll(scroll, overscrollEffect = overscroll)
                    .padding(top = statusTop + 56.dp + 8.dp, bottom = navBottom + 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = AdaptiveTokens.ReadableMaxWidth)
                        .fillMaxWidth(),
                ) {
                    Group(title = stringResource(R.string.settings_account)) {
                    Item(
                        trailing = {
                            Text(
                                text = state.session?.let { "UID ${it.userId}" }
                                    ?: stringResource(R.string.settings_not_logged_in),
                                kind = CouiTextKind.ItemValue,
                                color = colors.summary,
                            )
                        },
                    ) {
                        Text(
                            text = if (state.session == null) {
                                stringResource(R.string.settings_not_logged_in)
                            } else {
                                stringResource(R.string.settings_logged_in)
                            },
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    if (state.session != null) {
                        Divider()
                        Item(onClick = { confirmLogout = true }) {
                            Text(
                                text = stringResource(R.string.profile_logout),
                                kind = CouiTextKind.ItemTitle,
                                color = colors.title,
                            )
                        }
                    }
                }

                Group(title = stringResource(R.string.profile_group_content)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.profile_r18),
                        summary = stringResource(R.string.profile_r18_summary),
                        checked = state.revealR18,
                        onCheckedChange = { settings.setRevealR18(it) },
                    )
                }

                Group(title = stringResource(R.string.settings_language)) {
                    Column(Modifier.selectableGroup()) {
                        SettingSelectRow(
                            title = stringResource(R.string.settings_language_system),
                            selected = state.language == AppLanguage.SYSTEM,
                            onSelect = { applyLanguage(AppLanguage.SYSTEM) },
                        )
                        Divider()
                        SettingSelectRow(
                            title = stringResource(R.string.settings_language_zh),
                            selected = state.language == AppLanguage.SIMPLIFIED_CHINESE,
                            onSelect = { applyLanguage(AppLanguage.SIMPLIFIED_CHINESE) },
                        )
                        Divider()
                        SettingSelectRow(
                            title = stringResource(R.string.settings_language_hk),
                            selected = state.language == AppLanguage.TRADITIONAL_CHINESE_HK,
                            onSelect = { applyLanguage(AppLanguage.TRADITIONAL_CHINESE_HK) },
                        )
                        Divider()
                        SettingSelectRow(
                            title = stringResource(R.string.settings_language_en),
                            selected = state.language == AppLanguage.ENGLISH,
                            onSelect = { applyLanguage(AppLanguage.ENGLISH) },
                        )
                    }
                }

                Group(title = stringResource(R.string.settings_appearance)) {
                    Column(Modifier.selectableGroup()) {
                        SettingSelectRow(
                            title = stringResource(R.string.settings_theme_system),
                            selected = state.themeMode == ThemeMode.SYSTEM,
                            onSelect = { settings.setThemeMode(ThemeMode.SYSTEM) },
                        )
                        Divider()
                        SettingSelectRow(
                            title = stringResource(R.string.settings_theme_light),
                            selected = state.themeMode == ThemeMode.LIGHT,
                            onSelect = { settings.setThemeMode(ThemeMode.LIGHT) },
                        )
                        Divider()
                        SettingSelectRow(
                            title = stringResource(R.string.settings_theme_dark),
                            selected = state.themeMode == ThemeMode.DARK,
                            onSelect = { settings.setThemeMode(ThemeMode.DARK) },
                        )
                    }
                }

                Group(title = stringResource(R.string.settings_interaction)) {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_contour_glow),
                        summary = stringResource(R.string.settings_contour_glow_summary),
                        checked = state.contourGlow,
                        onCheckedChange = { settings.setContourGlow(it) },
                    )
                    Divider()
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_left_handed),
                        summary = stringResource(R.string.settings_left_handed_summary),
                        checked = state.leftHanded,
                        onCheckedChange = { settings.setLeftHanded(it) },
                    )
                    Divider()
                    Item(
                        onClick = onOpenShareFormat,
                        trailing = { NextChevron() },
                        summary = {
                            Text(
                                text = stringResource(R.string.settings_share_format_summary),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_share_format),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }

                Group(title = stringResource(R.string.settings_storage)) {
                    Item(
                        onClick = {
                            val initial = state.downloadTreeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                            downloadDirPicker.launch(initial)
                        },
                        trailing = { NextChevron() },
                        summary = {
                            Text(
                                text = ImageDownloader.formatTreeUriPath(state.downloadTreeUri),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_download_path),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                    Divider()
                    Item(
                        onClick = { confirmClear = true },
                        trailing = { NextChevron() },
                        summary = {
                            Text(
                                text = stringResource(
                                    if (cleared) R.string.settings_clear_done else R.string.settings_clear_summary,
                                ),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_clear_cache),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }

                Group {
                    Item(
                        onClick = onOpenAbout,
                        trailing = { NextChevron() },
                    ) {
                        Text(
                            text = stringResource(R.string.about_title),
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                }
            }
        }

        FloatingSecondaryTopBar(
            title = stringResource(R.string.settings_title),
            backdrop = backdrop,
            onBack = onBack,
        )
    }

    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    val destructiveColors = CouiDialogDefaults.primaryActionColors(CouiDialogDefaults.colors(dark).destructive)
    Dialog(
        visible = confirmLogout,
        onDismissRequest = { confirmLogout = false },
        title = stringResource(R.string.profile_logout_confirm),
        message = stringResource(R.string.profile_logout_message),
        material = dialogMaterial,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.action_confirm),
            onClick = {
                onLoggedOut()
                confirmLogout = false
            },
            style = CouiDialogActionStyle.Primary,
            colors = destructiveColors,
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { confirmLogout = false },
        ),
    )

    Dialog(
        visible = confirmClear,
        onDismissRequest = { confirmClear = false },
        title = stringResource(R.string.settings_clear_cache),
        message = stringResource(R.string.settings_clear_message),
        material = dialogMaterial,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.action_confirm),
            onClick = {
                onClearCaches()
                cleared = true
                confirmClear = false
            },
            style = CouiDialogActionStyle.Primary,
            colors = destructiveColors,
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { confirmClear = false },
        ),
    )
}
