package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/**
 * The search tab's inline search pill.
 *
 * Geometry and interaction are unchanged (44dp capsule, live text field), but
 * the surface is now a real COUI floating surface: ordinary shadow, caustic,
 * contour edge, touch-follow light and press/rebound motion. It samples the
 * page's flat backdrop instead of the list, which keeps it legible and avoids
 * the render-graph cycle a self-sampling glass surface would create.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    clearDescription: String,
    backdrop: CouiBackdrop,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CouiPageDefaults.themeColors()
    val haptics = LocalHapticFeedback.current
    val icon = CouiIcons.Outlined.Search
    val close = CouiIcons.Filled.Close

    GlowSurface(
        backdrop = backdrop,
        dark = dark,
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = CouiCapsuleShape,
        cornerWeight = 1f,
        preset = SpotlightPreset.OpaqueMedium2,
        search = true,
        cancelLightOnScroll = true,
        foregroundComposition = false,
        staticSurface = true,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.summary),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.title, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CouiCapsuleShape)
                        .background(colors.background)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onQueryChange("")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = close,
                        contentDescription = clearDescription,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.summary),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
