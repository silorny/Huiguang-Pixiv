package moe.huiguang.pixiv.ui.common

import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import moe.huiguang.pixiv.R

/**
 * First-launch welcome & privacy consent sheet styled with COUI-Kit shapes,
 * 凝光 contour glow, spotlight, and spring touch/press feedback.
 */
@Composable
fun WelcomeConsentDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val dialogColors = CouiDialogDefaults.themeColors(dark)
    val accent = CouiTheme.colorScheme.accent
    val contourEnabled = LocalCouiContourGlowEnabled.current
    val density = LocalDensity.current.density
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sheetCornerRadius = 38.dp
    val sheetShape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius)
    val sheetContour = remember(contourEnabled) {
        if (contourEnabled && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Dialog) else null
    }
    val edgePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(if (dark) 0.6f else 0.25f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTop + 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(sheetShape)
                    .drawBehind {
                        drawRect(dialogColors.surface)
                        if (Build.VERSION.SDK_INT >= 33 && sheetContour != null) {
                            val radiusPx = sheetCornerRadius.toPx()
                            drawIntoCanvas { canvas ->
                                edgePaint.shader = sheetContour.shader(
                                    size = size,
                                    density = density,
                                    radiusPx = radiusPx,
                                    cornerWeight = 3f,
                                    surfaceColor = Color.Transparent,
                                    innerGlowAlpha = if (dark) 0.03f else 0.4f,
                                    lineAlpha = if (dark) 0.3f else 0.6f,
                                    effectAlpha = 1f,
                                    lineWidthPx = (if (dark) 2f else 4f) * density,
                                )
                                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, edgePaint)
                            }
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(0.9f))

                    // App Icon
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CouiShape(20.dp, 3f))
                            .background(colorResource(R.color.ic_launcher_background)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground_art),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    BasicText(
                        text = stringResource(R.string.welcome_heading),
                        style = TextStyle(
                            color = dialogColors.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        text = stringResource(R.string.app_name),
                        style = TextStyle(
                            color = dialogColors.title,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = stringResource(R.string.welcome_subtitle),
                        style = TextStyle(
                            color = dialogColors.message,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )

                    Spacer(Modifier.heightIn(min = 32.dp).weight(1.4f))

                    BasicText(
                        text = stringResource(R.string.welcome_privacy_body),
                        style = TextStyle(
                            color = dialogColors.message,
                            fontSize = 13.5.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Start,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = navBottom + 24.dp, top = 4.dp),
                ) {
                    WelcomeActionButton(
                        text = stringResource(R.string.welcome_agree),
                        primary = true,
                        dark = dark,
                        surfaceColor = accent,
                        textColor = Color.White,
                        onClick = onAgree,
                    )
                    Spacer(Modifier.height(12.dp))
                    WelcomeActionButton(
                        text = stringResource(R.string.welcome_disagree),
                        primary = false,
                        dark = dark,
                        surfaceColor = if (dark) Color(0xFF353535) else Color(0xFFEBEBEB),
                        textColor = dialogColors.title,
                        onClick = onDisagree,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeActionButton(
    text: String,
    primary: Boolean,
    dark: Boolean,
    surfaceColor: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val motion = rememberCouiTouchMotion(true)
    val press = rememberCouiPressMotion()
    val haptics = LocalHapticFeedback.current
    val glow = LocalCouiContourGlowEnabled.current
    val density = LocalDensity.current.density
    val contour = remember(glow) {
        if (glow && Build.VERSION.SDK_INT >= 33) CouiContour(style = CouiContourStyle.Button) else null
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = true,
        dark = dark,
        preset = if (primary) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.OpaqueMedium2,
        cancelOnConsumedMove = true,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = motion.scaleX.value + press.scale.value - 1f
                scaleY = motion.scaleY.value + press.scale.value - 1f
                translationX = motion.translationX.value
                translationY = motion.translationY.value
            }
            .couiTouchMotion(motion, true)
            .couiPressMotion(press, true)
            .then(lightInput)
            .clip(CouiCapsuleShape)
            .drawWithContent {
                drawRect(surfaceColor)
                if (Build.VERSION.SDK_INT >= 33 && contour != null) {
                    val alpha = if (primary) {
                        if (dark) 0.25f else 0.3f
                    } else if (dark) {
                        0.1f
                    } else {
                        0.8f
                    }
                    val inner = if (primary) 0.1f else if (dark) 0.01f else 0.05f
                    paint.shader = contour.shader(
                        size = size,
                        density = density,
                        radiusPx = size.minDimension / 2f,
                        cornerWeight = 1f,
                        surfaceColor = Color.Transparent,
                        innerGlowAlpha = inner,
                        lineAlpha = alpha,
                        effectAlpha = 1f,
                        lineWidthPx = (if (primary) 2.5f else 3f) * density,
                    )
                    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
                }
                drawContent()
                drawLight?.invoke(this)
            }
            .couiButtonFeedback(dark, source, true)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}
