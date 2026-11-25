package moe.huiguang.pixiv.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.sheet.BottomSheet
import com.itos.coui.sheet.CouiSheetButton
import com.itos.coui.sheet.CouiSheetDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.LocalCouiContentColor
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.BuildConfig
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.net.AppReleaseInfo
import moe.huiguang.pixiv.data.net.AppUpdateService
import moe.huiguang.pixiv.data.net.UpdateCheckResult
import moe.huiguang.pixiv.design.components.PrimaryButton
import moe.huiguang.pixiv.ui.common.CategoryBadge
import moe.huiguang.pixiv.ui.common.ExpandableText
import java.io.File
import kotlin.math.roundToInt

private sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data class Available(val release: AppReleaseInfo) : UpdateUiState
    data class UpToDate(val release: AppReleaseInfo?) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

private val UpdateIconShape = CouiShape(14.dp, 3f)
private val UpdateCardShape = CouiShape(20.dp, 3f)

/**
 * Half-height update bottom sheet modeled after COUI's BottomSheet sample:
 * supports checking GitHub Releases, live changelog preview with expandable
 * `GlowChip`, in-app APK download with progress, and `FileProvider` install.
 */
@Composable
fun UpdateBottomSheet(
    visible: MutableState<Boolean>,
    checkKey: Int,
    dark: Boolean,
    windowBackdrop: CouiBackdrop?,
    onSkipVersion: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = CouiPageDefaults.themeColors()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val service = remember { AppUpdateService() }

    var uiState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }
    var downloadPercent by remember { mutableStateOf<Int?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = pendingInstallApk
        if (apk != null && apk.isFile && context.packageManager.canRequestPackageInstalls()) {
            pendingInstallApk = null
            launchApkInstaller(context, apk)
        }
    }

    LaunchedEffect(visible.value, checkKey, retryTick) {
        if (!visible.value) return@LaunchedEffect
        AppUpdateService.clearDownloadedUpdates(context.cacheDir)
        uiState = UpdateUiState.Checking
        downloadPercent = null
        downloadedApk = null
        runCatching { service.checkForUpdate(BuildConfig.VERSION_NAME) }
            .onSuccess { result ->
                uiState = when (result) {
                    is UpdateCheckResult.UpdateAvailable -> UpdateUiState.Available(result.release)
                    is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.release)
                }
            }
            .onFailure { err ->
                uiState = UpdateUiState.Error(
                    err.message?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.error_load_failed),
                )
            }
    }

    fun triggerInstall(apkFile: File) {
        if (context.packageManager.canRequestPackageInstalls()) {
            launchApkInstaller(context, apkFile)
        } else {
            pendingInstallApk = apkFile
            Toast.makeText(
                context,
                resources.getString(R.string.update_permission_install),
                Toast.LENGTH_LONG,
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
            installPermissionLauncher.launch(intent)
        }
    }

    fun startDownload(release: AppReleaseInfo) {
        val existing = downloadedApk
        if (existing != null && existing.isFile) {
            triggerInstall(existing)
            return
        }
        val apkUrl = release.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            Toast.makeText(context, resources.getString(R.string.update_no_apk_asset), Toast.LENGTH_SHORT).show()
            val targetUrl = release.htmlUrl
                .takeIf(AppUpdateService::isAllowedReleaseHtmlUrl)
                ?: AppUpdateService.GITHUB_RELEASES_HTML
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            }
            return
        }
        if (downloadPercent != null) return
        downloadPercent = 0
        scope.launch {
            val dest = File(context.cacheDir, "updates/huiguang-update.apk")
            runCatching {
                service.downloadApk(apkUrl, dest) { downloaded, total ->
                    val pct = if (total > 0L) {
                        ((downloaded * 100.0) / total).roundToInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    downloadPercent = pct
                }
            }.onSuccess { file ->
                downloadPercent = null
                downloadedApk = file
                triggerInstall(file)
            }.onFailure { err ->
                downloadPercent = null
                Toast.makeText(
                    context,
                    resources.getString(
                        R.string.update_download_failed,
                        err.message ?: resources.getString(R.string.action_failed),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val availableRelease = (uiState as? UpdateUiState.Available)?.release
    val isForceUpdate = availableRelease?.isForceUpdate == true
    val sheetMaterial = if (CouiDialogDefaults.isMaterialSupported() && windowBackdrop != null) {
        CouiSheetDefaults.material(dark)
    } else {
        null
    }

    BottomSheet(
        visible = visible,
        onDismissRequest = {
            if (!isForceUpdate) visible.value = false
        },
        dark = dark,
        sheetHeight = null,
        draggable = !isForceUpdate,
        dismissOnClickOutside = !isForceUpdate,
        showDragHandle = !isForceUpdate,
        backdrop = windowBackdrop,
        material = sheetMaterial,
        leadingButton = if (!isForceUpdate) {
            CouiSheetButton(
                onClick = { visible.value = false },
                enabled = visible.value,
                icon = {
                    Image(
                        imageVector = CouiIcons.Filled.Back,
                        contentDescription = stringResource(R.string.action_close),
                        colorFilter = ColorFilter.tint(LocalCouiContentColor.current),
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(UpdateIconShape)
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
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val headerTitle = when (uiState) {
                        UpdateUiState.Checking -> stringResource(R.string.update_checking)
                        is UpdateUiState.Available -> stringResource(R.string.update_sheet_title)
                        is UpdateUiState.UpToDate -> stringResource(R.string.update_latest_title)
                        is UpdateUiState.Error -> stringResource(R.string.update_check_failed_title)
                    }
                    BasicText(
                        text = headerTitle,
                        style = TextStyle(
                            color = colors.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CategoryBadge(
                            text = BuildConfig.VERSION_NAME,
                            accent = colors.accent,
                        )
                        if (availableRelease != null) {
                            BasicText(
                                text = "→",
                                style = TextStyle(
                                    color = colors.summary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            CategoryBadge(
                                text = availableRelease.versionName,
                                accent = colors.accent,
                            )
                        }
                    }
                    val metaText = when (val s = uiState) {
                        is UpdateUiState.Available -> listOf(
                            s.release.publishedDate,
                            s.release.formattedSize,
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        is UpdateUiState.UpToDate -> listOfNotNull(
                            s.release?.publishedDate?.takeIf { it.isNotBlank() },
                            s.release?.formattedSize?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        is UpdateUiState.Error -> s.message
                        UpdateUiState.Checking -> ""
                    }
                    if (metaText.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = metaText,
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val fallbackNotes = stringResource(R.string.update_current_release_notes)
            val changelogBody = when (val s = uiState) {
                is UpdateUiState.Available -> s.release.releaseNotes.ifBlank { fallbackNotes }
                is UpdateUiState.UpToDate -> s.release?.releaseNotes?.takeIf { it.isNotBlank() } ?: fallbackNotes
                is UpdateUiState.Error -> s.message
                UpdateUiState.Checking -> stringResource(R.string.update_checking)
            }
            val cardBackdrop = rememberCouiBackdrop()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(UpdateCardShape)
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
                    ),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .couiBackdrop(cardBackdrop)
                        .background(colors.card, UpdateCardShape),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    BasicText(
                        text = stringResource(R.string.update_whats_new),
                        style = TextStyle(
                            color = colors.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    ExpandableText(
                        text = changelogBody,
                        colors = colors,
                        collapsedLines = 5,
                        backdrop = cardBackdrop,
                        dark = dark,
                        expandLabel = stringResource(R.string.license_notes_expand),
                        collapseLabel = stringResource(R.string.license_notes_collapse),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            when (val s = uiState) {
                is UpdateUiState.Available -> {
                    val pct = downloadPercent
                    val buttonLabel = when {
                        downloadedApk != null -> stringResource(R.string.update_action_install)
                        pct != null && pct >= 0 -> stringResource(R.string.update_downloading_pct, pct)
                        pct != null -> stringResource(R.string.update_downloading)
                        else -> stringResource(R.string.update_action_now)
                    }
                    PrimaryButton(
                        text = buttonLabel,
                        enabled = pct == null,
                        onClick = { startDownload(s.release) },
                    )
                    if (!s.release.isForceUpdate && pct == null) {
                        Spacer(Modifier.height(6.dp))
                        val skipInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = skipInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSkipVersion(s.release.tagName)
                                        visible.value = false
                                    },
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.update_skip_version),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        }
                    }
                }

                is UpdateUiState.UpToDate -> {
                    PrimaryButton(
                        text = stringResource(R.string.action_close),
                        onClick = { visible.value = false },
                    )
                }

                is UpdateUiState.Error -> {
                    PrimaryButton(
                        text = stringResource(R.string.action_retry),
                        onClick = { retryTick++ },
                    )
                    Spacer(Modifier.height(6.dp))
                    val closeInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = closeInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = { visible.value = false },
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                }

                UpdateUiState.Checking -> {
                    PrimaryButton(
                        text = stringResource(R.string.action_close),
                        onClick = { visible.value = false },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun launchApkInstaller(context: Context, apkFile: File) {
    val contentUri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        apkFile,
    )
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(installIntent)
}
