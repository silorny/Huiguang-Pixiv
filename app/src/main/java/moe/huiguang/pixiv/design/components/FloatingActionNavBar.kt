package moe.huiguang.pixiv.design.components

import android.graphics.BlendMode
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingPair
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.floatingactionbutton.CouiFabColor
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.motion.CouiPressMotion
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.launch

/**
 * Trigger-mode action item inside [FloatingActionNavBar].
 *
 * Unlike tab-based `CouiNavigationItem`, items here are stateless or
 * toggle-triggered actions without a persistent "selected tab" highlight.
 */
data class ActionNavItem(
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val iconTint: Color? = null,
    val haptic: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    val onClick: () -> Unit,
)

/**
 * Floating COUI action navigation bar for Artwork and Novel detail screens.
 *
 * Assembled directly with `CouiFloatingRenderer.pair()` + `couiFloatingPair` so the
 * continuous action capsule (left, 3 icon+label triggers) and the independent blue
 * circular primary button (right, icon-only) share COUI's full 凝光 pipeline:
 *  - ordinary shadow + caustic projection,
 *  - frosted floating surface material with sibling/body backdrop sampling,
 *  - contour edge + touch-follow spotlight,
 *  - COUI touch deformation, press motion, and spring rebound.
 */
@RequiresApi(34)
@Composable
fun FloatingActionNavBar(
    backdrop: CouiBackdrop,
    items: List<ActionNavItem>,
    @DrawableRes primaryIconRes: Int,
    primaryDescription: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryOnLeft: Boolean = false,
    dark: Boolean = CouiTheme.isDark,
    lightColor: Color = Color.White,
    themeTint: Color = CouiTheme.materialTintFor(dark),
) {
    require(items.isNotEmpty())
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val contour = LocalCouiContourGlowEnabled.current
    val renderer = remember { CouiFloatingRenderer.pair() }

    val capsuleMaterial = remember(dark, lightColor, themeTint, density) {
        CouiFloatingMaterial(
            firstColor = if (dark) Color(0x80666666) else Color(0xA3666666),
            firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
            secondColor = if (dark) Color(0xB3262626) else Color(0x80F0F0F0),
            edgeAlpha = if (dark) 0.8f else 0.25f,
            innerGlowAlpha = if (dark) 0.03f else 0.1f,
            index = 1,
            extension = with(density) { 4.dp.roundToPx() },
            lightColor = lightColor,
            themeTint = themeTint,
        )
    }
    val coloredFab = CouiFabDefaults.containerColor(CouiFabColor.InfraredRemote, dark)
    val materials = remember(capsuleMaterial, coloredFab, dark, density) {
        val extension = with(density) { 4.dp.roundToPx() }
        listOf(capsuleMaterial, CouiFabDefaults.material(coloredFab, dark, extension))
    }

    val shadows = remember(coloredFab, density, lightColor, dark, contour) {
        fun pixelOffset(dp: Float): Float = (dp * density.density).toInt().toFloat()
        fun pixelSize(dp: Float): Float {
            val px = dp * density.density
            return (if (px >= 0f) px + 0.5f else px - 0.5f).toInt().toFloat()
        }
        val projectionColor = when {
            contour -> Color(0x2D000000)
            dark -> Color(0x78000000)
            else -> Color(0x28000000)
        }
        val capsuleShadow = CouiFloatingShadow(
            elevation = pixelSize(if (contour) 24f else 350f),
            color = projectionColor,
            lightY = pixelSize(if (contour) -320f else -160f),
            lightZ = if (contour) 0f else pixelSize(10000f),
            lightRadius = pixelSize(if (contour) 1600f else 2000f),
            causticColor = lightColor.copy(alpha = 0x42 / 255f),
            layout = Rect(0f, 0f, 0f, pixelOffset(4.9f)),
            sdf = pixelOffset(10.4f),
            smoothCorner = true,
        )
        listOf(capsuleShadow, CouiFabDefaults.shadow(coloredFab, density.density, contour))
    }

    val capsuleShape = CouiCapsuleShape
    val circleShape = CouiFabDefaults.circleShape
    val shapes = remember(capsuleShape, circleShape) { listOf(capsuleShape, circleShape) }
    val contentLayers = List(2) { rememberGraphicsLayer() }

    val menuMotion = rememberCouiTouchMotion(true)
    val fabMotion = rememberCouiTouchMotion(true)
    val menuPress = rememberCouiPressMotion()
    val fabPress = rememberCouiPressMotion()

    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuBounds by remember { mutableStateOf(Rect.Zero) }
    var fabBounds by remember { mutableStateOf(Rect.Zero) }

    val currentItems by rememberUpdatedState(items)
    val currentPrimaryClick by rememberUpdatedState(onPrimaryClick)

    val itemForeground = (if (dark) Color.White else Color.Black).copy(alpha = 230f / 255f)
    val maxMenuWidth = 24.dp + 80.dp * items.size + 8.dp * (items.size - 1)
    val fabSlotWidth = 56.dp + 12.dp
    val bottomMargin = if (WindowInsets.tappableElement.getBottom(density) > 0) 0.dp else 16.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomMargin),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = maxMenuWidth + fabSlotWidth)
                .fillMaxWidth(),
        ) {
            val menuWidth = (maxWidth - fabSlotWidth).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .onGloballyPositioned { rowCoordinates = it }
                    .couiFloatingPair(
                        backdrop = backdrop,
                        renderer = renderer,
                        materials = materials,
                        shadows = shadows,
                        shapes = shapes,
                        contentLayers = contentLayers,
                        contourGlowEnabled = contour,
                    ) {
                        listOf(
                            floatingSurface(menuBounds, menuMotion, menuPress, cornerWeight = 1f),
                            floatingSurface(fabBounds, fabMotion, fabPress, cornerWeight = 2f),
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val capsuleSlot: @Composable () -> Unit = {
                    ActionFloatingNode(
                        width = menuWidth,
                        motion = menuMotion,
                        press = menuPress,
                        dark = dark,
                        shape = capsuleShape,
                        lightColor = lightColor,
                        contentLayer = contentLayers[0],
                        coloredFab = false,
                        onBounds = { node -> rowCoordinates?.let { menuBounds = it.localBoundingBoxOf(node, false) } },
                    ) {
                        val itemBounds = remember(items.size) { mutableMapOf<Int, Rect>() }
                        val interactionSources = remember(items.size) { List(items.size) { MutableInteractionSource() } }
                        var menuCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Row(
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { menuCoordinates = it }
                                .pointerInput(items.size) {
                                    var active: PointerId? = null
                                    var downItem: Int? = null
                                    var downOrigin = Offset.Zero
                                    var dragged = false
                                    var activePress: Pair<Int, PressInteraction.Press>? = null

                                    fun hit(position: Offset): Int? =
                                        itemBounds.entries.firstOrNull { it.value.contains(position) }?.key

                                    fun clearPress(commit: Boolean) {
                                        val pair = activePress ?: return
                                        activePress = null
                                        val src = interactionSources.getOrNull(pair.first) ?: return
                                        scope.launch {
                                            src.emit(
                                                if (commit) PressInteraction.Release(pair.second)
                                                else PressInteraction.Cancel(pair.second),
                                            )
                                        }
                                    }

                                    try {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                if (event.isTouchCancelled()) {
                                                    clearPress(commit = false)
                                                    active = null
                                                    downItem = null
                                                    dragged = false
                                                    continue
                                                }
                                                val tracked = event.changes.firstOrNull { it.id == active }
                                                val pointer = tracked?.takeIf { it.pressed }
                                                    ?: event.changes.firstOrNull { it.pressed }
                                                    ?: tracked ?: continue

                                                if (active == null) {
                                                    active = pointer.id
                                                    downOrigin = pointer.position
                                                    dragged = false
                                                    downItem = hit(pointer.position)
                                                    downItem?.let { idx ->
                                                        val pressEvent = PressInteraction.Press(pointer.position)
                                                        activePress = idx to pressEvent
                                                        interactionSources.getOrNull(idx)?.let { src ->
                                                            scope.launch { src.emit(pressEvent) }
                                                        }
                                                    }
                                                } else if (pointer.pressed) {
                                                    if (!dragged &&
                                                        (pointer.position - downOrigin).getDistance() > viewConfiguration.touchSlop
                                                    ) {
                                                        dragged = true
                                                        clearPress(commit = false)
                                                    }
                                                }

                                                if (dragged && pointer.pressed) {
                                                    // Keep drag inside the floating bar for liquid follow/rebound
                                                    // without scrolling the underlying detail content.
                                                    event.changes.forEach { if (it.pressed) it.consume() }
                                                }

                                                if (!pointer.pressed) {
                                                    val targetIdx = downItem
                                                    val upItem = hit(pointer.position)
                                                    val shouldTrigger = !dragged && targetIdx != null && upItem == targetIdx
                                                    clearPress(commit = shouldTrigger)
                                                    if (shouldTrigger) {
                                                        currentItems.getOrNull(targetIdx)?.let { triggered ->
                                                            haptics.performHapticFeedback(triggered.haptic)
                                                            triggered.onClick()
                                                        }
                                                    }
                                                    active = null
                                                    downItem = null
                                                    dragged = false
                                                }
                                            }
                                        }
                                    } finally {
                                        clearPress(commit = false)
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items.forEachIndexed { index, item ->
                                val interactionSource = interactionSources[index]
                                val tint = item.iconTint ?: itemForeground
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .onGloballyPositioned { node ->
                                            menuCoordinates?.let { itemBounds[index] = it.localBoundingBoxOf(node, false) }
                                        }
                                        .semantics(mergeDescendants = true) {
                                            role = Role.Button
                                            contentDescription = item.label
                                            onClick {
                                                currentItems.getOrNull(index)?.let { triggered ->
                                                    haptics.performHapticFeedback(triggered.haptic)
                                                    triggered.onClick()
                                                }
                                                true
                                            }
                                        }
                                        .onKeyEvent {
                                            if (it.type == KeyEventType.KeyUp &&
                                                (it.key == Key.Enter || it.key == Key.Spacebar)
                                            ) {
                                                currentItems.getOrNull(index)?.let { triggered ->
                                                    haptics.performHapticFeedback(triggered.haptic)
                                                    triggered.onClick()
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .focusable(interactionSource = interactionSource),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .couiNavigationFeedback(dark, interactionSource)
                                            .padding(top = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Top,
                                    ) {
                                        Image(
                                            painter = painterResource(item.iconRes),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(tint),
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        BasicText(
                                            text = item.label,
                                            style = TextStyle(
                                                color = itemForeground,
                                                fontSize = 10.sp,
                                                lineHeight = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val primarySlot: @Composable () -> Unit = {
                    ActionFloatingNode(
                        width = 56.dp,
                        motion = fabMotion,
                        press = fabPress,
                        dark = dark,
                        shape = circleShape,
                        lightColor = lightColor,
                        contentLayer = contentLayers[1],
                        coloredFab = true,
                        onBounds = { node -> rowCoordinates?.let { fabBounds = it.localBoundingBoxOf(node, false) } },
                    ) {
                        val fabInteraction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .couiFabFeedback(dark, fabInteraction)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = primaryDescription
                                    onClick {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentPrimaryClick()
                                        true
                                    }
                                }
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyUp &&
                                        (it.key == Key.Enter || it.key == Key.Spacebar)
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentPrimaryClick()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .focusable(interactionSource = fabInteraction)
                                .pointerInput(Unit) {
                                    var active: PointerId? = null
                                    var downOrigin = Offset.Zero
                                    var dragged = false
                                    var activePress: PressInteraction.Press? = null

                                    fun clearPress(commit: Boolean) {
                                        val p = activePress ?: return
                                        activePress = null
                                        scope.launch {
                                            fabInteraction.emit(
                                                if (commit) PressInteraction.Release(p)
                                                else PressInteraction.Cancel(p),
                                            )
                                        }
                                    }

                                    try {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                if (event.isTouchCancelled()) {
                                                    clearPress(commit = false)
                                                    active = null
                                                    dragged = false
                                                    continue
                                                }
                                                val tracked = event.changes.firstOrNull { it.id == active }
                                                val pointer = tracked?.takeIf { it.pressed }
                                                    ?: event.changes.firstOrNull { it.pressed }
                                                    ?: tracked ?: continue

                                                if (active == null) {
                                                    active = pointer.id
                                                    downOrigin = pointer.position
                                                    dragged = false
                                                    val p = PressInteraction.Press(pointer.position)
                                                    activePress = p
                                                    scope.launch { fabInteraction.emit(p) }
                                                } else if (pointer.pressed) {
                                                    if (!dragged &&
                                                        (pointer.position - downOrigin).getDistance() > viewConfiguration.touchSlop
                                                    ) {
                                                        dragged = true
                                                        clearPress(commit = false)
                                                    }
                                                }

                                                if (dragged && pointer.pressed) {
                                                    event.changes.forEach { if (it.pressed) it.consume() }
                                                }

                                                if (!pointer.pressed) {
                                                    val inside = pointer.position.x in 0f..size.width.toFloat() &&
                                                        pointer.position.y in 0f..size.height.toFloat()
                                                    val trigger = !dragged && inside
                                                    clearPress(commit = trigger)
                                                    if (trigger) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        currentPrimaryClick()
                                                    }
                                                    active = null
                                                    dragged = false
                                                }
                                            }
                                        }
                                    } finally {
                                        clearPress(commit = false)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(primaryIconRes),
                                contentDescription = primaryDescription,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                if (primaryOnLeft) {
                    primarySlot()
                    capsuleSlot()
                } else {
                    capsuleSlot()
                    primarySlot()
                }
            }
        }
    }
}

@Composable
private fun ActionFloatingNode(
    width: Dp,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
    dark: Boolean,
    shape: Shape,
    lightColor: Color,
    contentLayer: GraphicsLayer,
    coloredFab: Boolean,
    onBounds: (LayoutCoordinates) -> Unit,
    content: @Composable () -> Unit,
) {
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = true,
        preset = if (coloredFab) SpotlightPreset.OpaqueSmall1 else SpotlightPreset.OpaqueSmall2,
        dark = dark,
        color = if (coloredFab) Color.White else lightColor,
    )
    Box(
        Modifier
            .width(width)
            .height(56.dp)
            .onGloballyPositioned(onBounds),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = motion.translationX.value
                    translationY = motion.translationY.value
                    scaleX = motion.scaleX.value + press.scale.value - 1f
                    scaleY = motion.scaleY.value + press.scale.value - 1f
                }
                .couiTouchMotion(motion, true)
                .couiPressMotion(press)
                .then(lightInput)
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                }
                .clip(shape)
                .drawBehind { drawLight?.invoke(this) },
        ) {
            content()
        }
    }
}

private fun floatingSurface(
    bounds: Rect,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
    cornerWeight: Float,
): CouiFloatingSurface = CouiFloatingSurface(
    bounds = bounds.translate(Offset(motion.translationX.value, motion.translationY.value)),
    scaleX = motion.scaleX.value + press.scale.value - 1f,
    scaleY = motion.scaleY.value + press.scale.value - 1f,
    cornerWeight = cornerWeight,
)

private fun PointerEvent.isTouchCancelled(): Boolean =
    changes.any { it.previousPressed } && changes.all {
        !it.pressed && (!it.previousPressed ||
            (it.isConsumed && it.uptimeMillis == it.previousUptimeMillis &&
                it.position == it.previousPosition))
    }
