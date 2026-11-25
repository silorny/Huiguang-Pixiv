package moe.huiguang.pixiv.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTab
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarSurface
import moe.huiguang.pixiv.R
import moe.huiguang.pixiv.data.model.ShareFormatter
import moe.huiguang.pixiv.data.store.SettingsStore
import moe.huiguang.pixiv.design.components.CircleIconButton
import moe.huiguang.pixiv.design.components.isDarkBackdrop
import moe.huiguang.pixiv.design.shape.CachedCouiShape
import moe.huiguang.pixiv.design.shape.g2CornerMask

private val ShareCardShape = CachedCouiShape(20.dp, 3f)

private data class ShareVariableOption(
    val labelRes: Int,
    val token: String,
)

private val WORK_VARIABLES = listOf(
    ShareVariableOption(R.string.share_var_title, "{title}"),
    ShareVariableOption(R.string.share_var_user_name, "{user_name}"),
    ShareVariableOption(R.string.share_var_user_id, "{user_id}"),
    ShareVariableOption(R.string.share_var_illust_id, "{illust_id}"),
    ShareVariableOption(R.string.share_var_work_url, "{url}"),
    ShareVariableOption(R.string.share_var_tags, "{tags}"),
)

private val AUTHOR_VARIABLES = listOf(
    ShareVariableOption(R.string.share_var_user_name, "{user_name}"),
    ShareVariableOption(R.string.share_var_user_id, "{user_id}"),
    ShareVariableOption(R.string.share_var_author_url, "{url}"),
)

/** Secondary settings page for customizing work & author share/copy text formats. */
@Composable
fun ShareFormatScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val effectDark = isDarkBackdrop(colors.background)
    val backdrop = rememberCouiBackdrop()
    // Flat sibling backdrop so ChipTab components never form a cyclic render graph.
    val effectBackdrop = rememberCouiBackdrop()
    val windowBackdrop = rememberCouiWindowBackdrop()
    val overscroll = rememberCouiOverscrollEffect()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val state by settings.state.collectAsState()

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val defaultWork = stringResource(R.string.share_format_default_work)
    val defaultAuthor = stringResource(R.string.share_format_default_author)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    var workTfv by remember {
        val initial = state.workShareFormat.ifBlank { defaultWork }
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var authorTfv by remember {
        val initial = state.authorShareFormat.ifBlank { defaultAuthor }
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    val editorFocusRequester = remember { FocusRequester() }
    val chipRenderers = remember { List(6) { CouiFloatingRenderer.standalone() } }

    val activeTfv = if (tab == 0) workTfv else authorTfv
    val variables = if (tab == 0) WORK_VARIABLES else AUTHOR_VARIABLES
    val previewText = if (tab == 0) {
        ShareFormatter.formatWork(
            template = workTfv.text,
            title = ShareFormatter.SAMPLE_TITLE,
            userName = ShareFormatter.SAMPLE_USER_NAME,
            userId = ShareFormatter.SAMPLE_USER_ID,
            illustId = ShareFormatter.SAMPLE_ILLUST_ID,
            url = ShareFormatter.SAMPLE_WORK_URL,
            tags = ShareFormatter.SAMPLE_TAGS,
            fallbackTemplate = "",
        )
    } else {
        ShareFormatter.formatAuthor(
            template = authorTfv.text,
            userName = ShareFormatter.SAMPLE_USER_NAME,
            userId = ShareFormatter.SAMPLE_USER_ID,
            url = ShareFormatter.SAMPLE_AUTHOR_URL,
            fallbackTemplate = "",
        )
    }

    fun updateActiveTemplate(next: TextFieldValue) {
        if (tab == 0) {
            workTfv = next
            settings.setWorkShareFormat(next.text)
        } else {
            authorTfv = next
            settings.setAuthorShareFormat(next.text)
        }
    }

    fun insertVariable(token: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val (updatedText, nextCursor) = ShareFormatter.insertToken(
            text = activeTfv.text,
            selectionStart = activeTfv.selection.start,
            selectionEnd = activeTfv.selection.end,
            token = token,
        )
        updateActiveTemplate(TextFieldValue(updatedText, TextRange(nextCursor)))
        runCatching { editorFocusRequester.requestFocus() }
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
                    .couiVerticalScroll(scroll, overscrollEffect = overscroll)
                    .imePadding()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusTop + 56.dp + 12.dp,
                        bottom = navBottom + 32.dp,
                    ),
            ) {
                SectionSubtitle(
                    text = stringResource(R.string.share_format_section_edit),
                    color = colors.summary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)
                        .background(colors.card, ShareCardShape)
                        .g2CornerMask(ShareCardShape, colors.background)
                        .padding(16.dp),
                ) {
                    BasicTextField(
                        value = activeTfv,
                        onValueChange = ::updateActiveTemplate,
                        cursorBrush = SolidColor(colors.accent),
                        textStyle = TextStyle(
                            color = colors.title,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .focusRequester(editorFocusRequester),
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionSubtitle(
                    text = stringResource(R.string.share_format_section_vars),
                    color = colors.summary,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    variables.forEachIndexed { index, option ->
                        ChipTab(
                            selected = false,
                            onClick = { insertVariable(option.token) },
                            label = stringResource(option.labelRes),
                            backdrop = effectBackdrop,
                            dark = effectDark,
                            renderer = chipRenderers[index],
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionSubtitle(
                    text = stringResource(R.string.share_format_section_preview),
                    color = colors.summary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .background(colors.card, ShareCardShape)
                        .g2CornerMask(ShareCardShape, colors.background)
                        .padding(16.dp),
                ) {
                    BasicText(
                        text = previewText,
                        style = TextStyle(
                            color = colors.title,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        ShareFormatTopBar(
            backdrop = backdrop,
            dark = effectDark,
            backgroundColor = colors.background,
            selectedTab = tab,
            onSelectTab = { tab = it },
            onBack = onBack,
            onRequestReset = { confirmReset = true },
        )
    }

    val dialogMaterial = if (windowBackdrop != null) CouiDialogDefaults.material(dark) else null
    val destructiveColors = CouiDialogDefaults.primaryActionColors(CouiDialogDefaults.colors(dark).destructive)
    Dialog(
        visible = confirmReset,
        onDismissRequest = { confirmReset = false },
        title = stringResource(R.string.share_format_reset_title),
        message = stringResource(
            if (tab == 0) R.string.share_format_reset_work_message else R.string.share_format_reset_author_message,
        ),
        material = dialogMaterial,
        backdrop = windowBackdrop,
        confirmAction = CouiDialogAction(
            text = stringResource(R.string.share_format_reset),
            onClick = {
                if (tab == 0) {
                    settings.resetWorkShareFormat()
                    workTfv = TextFieldValue(defaultWork, TextRange(defaultWork.length))
                } else {
                    settings.resetAuthorShareFormat()
                    authorTfv = TextFieldValue(defaultAuthor, TextRange(defaultAuthor.length))
                }
                confirmReset = false
                Toast.makeText(context, resources.getString(R.string.share_format_reset_done), Toast.LENGTH_SHORT).show()
            },
            style = CouiDialogActionStyle.Primary,
            colors = destructiveColors,
        ),
        dismissAction = CouiDialogAction(
            text = stringResource(R.string.action_cancel),
            onClick = { confirmReset = false },
        ),
    )
}

@Composable
private fun SectionSubtitle(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        kind = CouiTextKind.ItemSummary,
        color = color,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ShareFormatTopBar(
    backdrop: CouiBackdrop,
    dark: Boolean,
    backgroundColor: androidx.compose.ui.graphics.Color,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onBack: () -> Unit,
    onRequestReset: () -> Unit,
) {
    val restartIcon = ImageVector.vectorResource(R.drawable.ic_restart_alt)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleIconButton(
                icon = CouiIcons.Filled.Back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                backdrop = backdrop,
                dark = dark,
                blurRadius = 20f,
            )
            SegmentedControl(
                modifier = Modifier.weight(1f),
                labels = listOf(
                    stringResource(R.string.share_format_tab_work),
                    stringResource(R.string.share_format_tab_author),
                ),
                selectedIndex = selectedTab,
                onSelected = onSelectTab,
                enabled = true,
            )
            CircleIconButton(
                icon = restartIcon,
                contentDescription = stringResource(R.string.share_format_reset),
                onClick = onRequestReset,
                backdrop = backdrop,
                dark = dark,
                blurRadius = 20f,
            )
        }
    }
}
