package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.switch.CouiSwitchDefaults
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * Full-row switch preference matching `DemoSwitchPreference`: the whole `Item`
 * owns `toggleable` and shares its [MutableInteractionSource] with [Switch].
 */
@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    Item(
        modifier = modifier
            .couiButtonFeedback(dark, source, enabled, list = true)
            .toggleable(checked, source, null, enabled, Role.Switch) { target ->
                view.performCouiHapticFeedback(CouiHapticFeedback.Switch)
                onCheckedChange(target)
            },
        enabled = enabled,
        title = {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.summary,
            )
        },
        summary = summary?.takeIf { it.isNotEmpty() }?.let { text ->
            @Composable {
                Text(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
            }
        },
        trailing = {
            Switch(
                modifier = Modifier.padding(start = 16.dp),
                checked = checked,
                onCheckedChange = null,
                dark = dark,
                colors = CouiSwitchDefaults.colors(colors.accent, dark),
                enabled = enabled,
                interactionSource = source,
                onDragCheckedChange = onCheckedChange,
            )
        },
    )
}

/**
 * Full-row radio option matching `DemoSelectPreference`: the row carries
 * `selectable` with `Role.RadioButton` and COUI state feedback.
 */
@Composable
fun SettingSelectRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val source = remember { MutableInteractionSource() }
    Item(
        modifier = modifier
            .couiButtonFeedback(dark, source, enabled, list = true)
            .selectable(selected, source, null, enabled, Role.RadioButton, onSelect),
        enabled = enabled,
        title = {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.summary,
            )
        },
        summary = summary?.takeIf { it.isNotEmpty() }?.let { text ->
            @Composable {
                Text(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
            }
        },
        trailing = {
            RadioButton(
                modifier = Modifier.padding(start = 16.dp).size(24.dp),
                selected = selected,
                enabled = enabled,
            )
        },
    )
}

/** Standard COUI trailing disclosure chevron for clickable setting/navigation rows. */
@Composable
fun NextChevron(modifier: Modifier = Modifier) {
    val dark = CouiTheme.isDark
    val tint = Color(if (dark) 0x4DFFFFFF else 0x42000000)
    Image(
        imageVector = CouiIcons.Filled.Next,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
            .padding(start = 4.dp)
            .size(width = 12.dp, height = 24.dp),
    )
}
