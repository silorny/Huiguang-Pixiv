package moe.huiguang.pixiv.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * Primary capsule button (46dp high, accent fill, white text) with COUI state
 * feedback and light actuation haptic. When [backdrop] is provided, renders via
 * [GlowSurface] for full COUI 凝光 visuals.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: CouiBackdrop? = null,
) {
    val dark = CouiTheme.isDark
    val accent = CouiTheme.colorScheme.accent
    val haptics = LocalHapticFeedback.current
    if (backdrop != null) {
        GlowSurface(
            backdrop = backdrop,
            dark = dark,
            modifier = modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = CouiCapsuleShape,
            cornerWeight = 1f,
            preset = SpotlightPreset.ChipTab,
            colored = if (enabled) accent else accent.copy(alpha = 0.4f),
            interactive = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            foregroundComposition = true,
            staticSurface = false,
        ) {
            Text(text = text, kind = CouiTextKind.ItemTitle, color = Color.White)
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CouiCapsuleShape)
            .background(if (enabled) accent else accent.copy(alpha = 0.4f))
            .couiButtonFeedback(dark, interaction, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.ItemTitle, color = Color.White)
    }
}
