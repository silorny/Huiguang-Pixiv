package moe.huiguang.pixiv.design.components

import android.graphics.BlendMode as AndroidBlendMode
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingPair
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.swipereveal.CouiSwipeRevealDefaults
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import moe.huiguang.pixiv.R
import kotlin.math.max
import kotlin.math.roundToInt

private val DeleteActionSize = 52.dp
private val DeleteActionGap = 12.dp
private val DeleteIconSize = 24.dp
private val DeleteCommitStretch = 54.dp

private val SwipeOpenSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 175f)
private val SwipeCloseSpring = spring<Float>(dampingRatio = 0.88f, stiffness = 210f)

/**
 * Shared coordinator for a list of [SwipeDeleteRow]s: ensures only one row is
 * swiped open at a time and provides its root bounds so outside taps dismiss it.
 */
@Stable
class SwipeRevealController {
    var openKey by mutableStateOf<Any?>(null)
        private set
    var openBoundsInRoot by mutableStateOf<Rect?>(null)
        internal set

    fun setOpen(key: Any, open: Boolean) {
        if (open) {
            if (openKey != key) {
                openBoundsInRoot = null
                openKey = key
            }
        } else if (openKey == key) {
            openKey = null
            openBoundsInRoot = null
        }
    }

    fun close() {
        openKey = null
        openBoundsInRoot = null
    }
}

/**
 * Dismisses any currently half-swiped [SwipeDeleteRow] when the user taps
 * anywhere outside that row, consuming the tap so items behind do not trigger.
 */
fun Modifier.dismissSwipeOnOutsideTap(controller: SwipeRevealController): Modifier {
    val activeKey = controller.openKey ?: return this
    var rootOrigin = Offset.Zero
    return this
        .onGloballyPositioned { rootOrigin = it.positionInRoot() }
        .pointerInput(activeKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val downRootPos = down.position + rootOrigin
                if (controller.openBoundsInRoot?.contains(downRootPos) == true) {
                    return@awaitEachGesture
                }
                val pointerId = down.id
                val downPos = down.position
                val slop = viewConfiguration.touchSlop
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        change.consume()
                        controller.close()
                        break
                    }
                    if ((change.position - downPos).getDistance() > slop) {
                        break
                    }
                }
            }
        }
}

/**
 * Card container with COUI's floating-pair metaball swipe-to-delete gesture.
 *
 * Uses `CouiFloatingRenderer.pair()` and `Modifier.couiFloatingPair` (matching
 * `NavigationBar`'s `Show new-item action` metaball emergence) so the red
 * destructive FAB emerges from and collapses into the card with native CONIC
 * metaball fusion, color-range gradient blending, overlap fade, and contour glow.
 */
@Composable
fun SwipeDeleteRow(
    rowKey: Any,
    controller: SwipeRevealController,
    backdrop: CouiBackdrop,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors()
    val destructiveColor = if (dark) CouiColor.Night.RED else CouiColor.Day.RED
    val contour = LocalCouiContourGlowEnabled.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestOnDelete by rememberUpdatedState(onDelete)
    val isOpen = controller.openKey == rowKey

    val collapseHeight = remember { Animatable(1f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val collapsing = collapseHeight.targetValue == 0f
    val rowModifier = if (collapsing) {
        Modifier.height(with(density) { (rowHeightPx * collapseHeight.value).toDp() })
    } else {
        Modifier
    }

    Box(
        modifier
            .fillMaxWidth()
            .then(rowModifier)
            .clipToBounds()
            .onSizeChanged { if (!collapsing) rowHeightPx = it.height.toFloat() }
            .onGloballyPositioned { coords ->
                if (isOpen) controller.openBoundsInRoot = coords.boundsInRoot()
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        SwipeRevealSurface(
            backdrop = backdrop,
            destructiveColor = destructiveColor,
            dark = dark,
            contourGlowEnabled = contour,
            isOpen = isOpen,
            onOpenChange = { open -> controller.setOpen(rowKey, open) },
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            minHeight = minHeight,
            onCommitDelete = {
                controller.setOpen(rowKey, false)
                scope.launch {
                    collapseHeight.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                    latestOnDelete()
                }
            },
            endActionContent = { committed, confirmation, interactive, requestDelete ->
                val label = stringResource(
                    if (confirmation) R.string.history_confirm_delete else R.string.history_delete,
                )
                val fabInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = label }
                        .then(
                            if (interactive) {
                                Modifier
                                    .couiFabFeedback(dark, fabInteraction)
                                    .clickable(
                                        interactionSource = fabInteraction,
                                        indication = null,
                                        role = Role.Button,
                                        onClickLabel = label,
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            requestDelete()
                                        },
                                    )
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DeleteActionIcon(
                        modifier = Modifier.size(DeleteIconSize),
                        confirmation = confirmation,
                        committed = committed,
                        color = Color.White,
                    )
                }
            },
            content = content,
        )
    }
}

@Composable
private fun SwipeRevealSurface(
    backdrop: CouiBackdrop,
    destructiveColor: Color,
    dark: Boolean,
    contourGlowEnabled: Boolean,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    minHeight: Dp,
    onCommitDelete: () -> Unit,
    endActionContent: @Composable (
        committed: Boolean,
        confirmation: Boolean,
        interactive: Boolean,
        requestDelete: () -> Unit,
    ) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val colors = CouiPageDefaults.themeColors()
    val layoutDirection = LocalLayoutDirection.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val maxVelocityPx = with(density) { CouiSwipeRevealDefaults.itemMaxVelocity.toPx() }
    val minHeightPx = with(density) { minHeight.toPx() }
    var contentHeightPx by remember(minHeight) { mutableFloatStateOf(minHeightPx) }
    val rowHeightPx = max(minHeightPx, contentHeightPx)
    val deleteActionSizePx = with(density) { DeleteActionSize.toPx() }
    val actionGapPx = with(density) { DeleteActionGap.toPx() }
    val openRestPx = deleteActionSizePx + actionGapPx
    val openThresholdPx = with(density) { CouiSwipeRevealDefaults.openTriggerDistance.toPx() }
    val deleteCommitPx = openRestPx + with(density) { DeleteCommitStretch.toPx() }
    val rightSwipeLimitPx = openRestPx * 1.5f
    val cardCornerRadiusPx = with(density) { 18.dp.toPx() }

    val offsetPx = remember { Animatable(0f) }
    val deleteAlpha = remember { Animatable(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var deleteAnimating by remember { mutableStateOf(false) }
    var deleteConfirmationRequested by remember { mutableStateOf(false) }
    var deleteEndPx by remember { mutableFloatStateOf(0f) }
    var dragStartOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var motionJob by remember { mutableStateOf<Job?>(null) }
    var settlingTargetPx by remember { mutableStateOf<Float?>(null) }
    var deleteHapticPerformed by remember { mutableStateOf(false) }

    val latestIsOpen by rememberUpdatedState(isOpen)
    val latestOnOpenChange by rememberUpdatedState(onOpenChange)
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnCommitDelete by rememberUpdatedState(onCommitDelete)
    val showActionsLabel = stringResource(R.string.history_open_actions)
    val swipeActive by remember(isOpen) {
        derivedStateOf { isOpen || isDragging || deleteAnimating || offsetPx.value != 0f }
    }

    fun settleTo(target: Float, velocity: Float = offsetPx.velocity, dragEndOffset: Float? = null) {
        val previousMotion = motionJob
        settlingTargetPx = target
        val spec = if (target < 0f) SwipeOpenSpring else SwipeCloseSpring
        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            previousMotion?.cancelAndJoin()
            if (dragEndOffset != null) offsetPx.snapTo(dragEndOffset)
            offsetPx.animateTo(target, spec, initialVelocity = velocity)
        }
    }

    val runDeleteAnimation = {
        if (!deleteAnimating) {
            deleteConfirmationRequested = false
            latestOnOpenChange(false)
            deleteAnimating = true
            val previousMotion = motionJob
            settlingTargetPx = null
            scope.launch {
                previousMotion?.cancelAndJoin()
                val moveJob = launch {
                    offsetPx.animateTo(
                        targetValue = -deleteEndPx,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    )
                }
                val fadeJob = launch {
                    deleteAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    )
                }
                moveJob.join()
                fadeJob.join()
                latestOnCommitDelete()
            }
        }
    }

    val requestDelete = {
        if (!deleteAnimating) {
            if (deleteConfirmationRequested) {
                runDeleteAnimation()
            } else {
                deleteConfirmationRequested = true
            }
        }
    }

    LaunchedEffect(isOpen, openRestPx) {
        if (!isOpen && !deleteAnimating) {
            deleteConfirmationRequested = false
        }
        if (isDragging || deleteAnimating) return@LaunchedEffect
        val target = if (isOpen) -openRestPx else 0f
        if (settlingTargetPx != target || (motionJob?.isActive != true && offsetPx.value != target)) {
            settleTo(target)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .pointerInput(openRestPx, rightSwipeLimitPx, deleteCommitPx, layoutDirection, maxVelocityPx) {
                val velocityTracker = VelocityTracker()
                var lastPointerPosition = Offset.Zero
                var hasVelocitySample = false
                val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (deleteAnimating) return@detectHorizontalDragGestures
                        isDragging = true
                        dragStartOffsetPx = offsetPx.value
                        dragOffsetPx = offsetPx.value
                        settlingTargetPx = null
                        velocityTracker.resetTracking()
                        hasVelocitySample = false
                        val previousMotion = motionJob
                        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            previousMotion?.cancelAndJoin()
                            offsetPx.stop()
                        }
                        latestOnOpenChange(true)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (deleteAnimating || !isDragging) return@detectHorizontalDragGestures
                        change.consume()
                        if (!hasVelocitySample) {
                            velocityTracker.addPosition(change.previousUptimeMillis, change.previousPosition)
                            hasVelocitySample = true
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        lastPointerPosition = change.position
                        val next = (dragOffsetPx + dragAmount * direction).coerceAtMost(rightSwipeLimitPx)
                        dragOffsetPx = next
                        if (next < 0f && !latestIsOpen) latestOnOpenChange(true)
                        val deleteCommitReached = -next >= deleteCommitPx
                        if (deleteCommitReached && !deleteHapticPerformed) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            deleteHapticPerformed = true
                        } else if (!deleteCommitReached) {
                            deleteHapticPerformed = false
                        }
                        val previousMotion = motionJob
                        motionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            previousMotion?.cancelAndJoin()
                            offsetPx.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        if (deleteAnimating || !isDragging) return@detectHorizontalDragGestures
                        isDragging = false
                        val velocity = if (hasVelocitySample) {
                            velocityTracker.addPosition(SystemClock.uptimeMillis(), lastPointerPosition)
                            velocityTracker.calculateVelocity().x.coerceIn(-maxVelocityPx, maxVelocityPx) * direction
                        } else {
                            0f
                        }
                        if (dragOffsetPx > 0f) {
                            settleTo(0f, velocity, dragOffsetPx)
                            latestOnOpenChange(false)
                            return@detectHorizontalDragGestures
                        }
                        val currentRevealPx = -dragOffsetPx
                        if (currentRevealPx >= deleteCommitPx) {
                            runDeleteAnimation()
                            return@detectHorizontalDragGestures
                        }
                        val pulledBackFromOpen = dragStartOffsetPx < -openThresholdPx &&
                            dragOffsetPx - dragStartOffsetPx >= openThresholdPx
                        val target = when {
                            pulledBackFromOpen -> 0f
                            currentRevealPx >= openThresholdPx -> -openRestPx
                            else -> 0f
                        }
                        settleTo(target, velocity, dragOffsetPx)
                        latestOnOpenChange(target != 0f)
                    },
                    onDragCancel = {
                        if (deleteAnimating || !isDragging) return@detectHorizontalDragGestures
                        isDragging = false
                        val target = if (latestIsOpen) -openRestPx else 0f
                        settleTo(target, velocity = 0f, dragEndOffset = dragOffsetPx)
                        latestOnOpenChange(target != 0f)
                    },
                )
            },
    ) {
        val fullContentWidth = maxWidth
        val fullWidthPx = with(density) { fullContentWidth.toPx() }
        LaunchedEffect(fullWidthPx, deleteActionSizePx) {
            deleteEndPx = fullWidthPx + deleteActionSizePx
        }
        val leftShrinkPx = (-offsetPx.value).coerceAtLeast(0f)
        val rightShrinkPx = offsetPx.value.coerceAtLeast(0f)
        val deleteCommitted = leftShrinkPx >= deleteCommitPx
        val revealFraction = (leftShrinkPx / openRestPx).coerceIn(0f, 1f)
        val visibleShrinkPx = max(leftShrinkPx, rightShrinkPx)
        val visibleCardWidthPx = (fullWidthPx - visibleShrinkPx).coerceAtLeast(1f)
        val visibleCardWidth = with(density) { visibleCardWidthPx.toDp() }
        val deleteStretchPx = (leftShrinkPx - openRestPx).coerceAtLeast(0f)
        val deleteActionWidthPx = deleteActionSizePx + deleteStretchPx
        val deleteActionWidth = with(density) { deleteActionWidthPx.toDp() }
        val fullContentWidthPx = with(density) { fullContentWidth.roundToPx() }

        if (!swipeActive) {
            val idleBackgroundModifier =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && contourGlowEnabled) {
                    remember(colors.card, dark) {
                        val contour = CouiContour(style = CouiContourStyle.Button)
                        Modifier.drawWithCache {
                            if (size.minDimension <= 0f) {
                                onDrawBehind { }
                            } else {
                                val outline = WorkRowCardShape.createOutline(size, layoutDirection, this)
                                val brush = ShaderBrush(
                                    contour.shader(
                                        size = size,
                                        density = density.density,
                                        radiusPx = cardCornerRadiusPx,
                                        cornerWeight = 3f,
                                        surfaceColor = colors.card,
                                        innerGlowAlpha = if (dark) 0.05f else 0.07f,
                                        lineAlpha = if (dark) 0.24f else 0.14f,
                                        effectAlpha = 1f,
                                    ),
                                )
                                onDrawBehind { drawOutline(outline, brush) }
                            }
                        }
                    }
                } else {
                    Modifier.background(colors.card, WorkRowCardShape)
                }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .then(idleBackgroundModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !deleteAnimating,
                        onClickLabel = stringResource(R.string.history_view_details),
                        onClick = { latestOnClick() },
                    )
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(showActionsLabel) {
                                latestOnOpenChange(true)
                                true
                            },
                        )
                    }
                    .onSizeChanged { size ->
                        contentHeightPx = max(minHeightPx, size.height.toFloat())
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                content()
            }
        } else {
            val renderer = remember { CouiFloatingRenderer.pair() }
            val extension = with(density) { 4.dp.roundToPx() }
            val cardMaterial = remember(colors.card, extension) {
                CouiFloatingMaterial(
                    firstColor = colors.card,
                    firstBlendMode = AndroidBlendMode.SRC_OVER,
                    secondColor = Color.Transparent,
                    edgeAlpha = 0f,
                    innerGlowAlpha = 0f,
                    secondBlendMode = AndroidBlendMode.DST,
                    blurRadius = 0f,
                    strokeType = 0,
                    index = 1,
                    extension = extension,
                    lightColor = Color.Transparent,
                    themeTint = Color.Transparent,
                )
            }
            val fabMaterial = remember(destructiveColor, dark, extension) {
                CouiFabDefaults.material(destructiveColor, dark, extension)
            }
            val materials = remember(cardMaterial, fabMaterial) { listOf(cardMaterial, fabMaterial) }
            val cardShadow = remember {
                CouiFloatingShadow(
                    elevation = 0f,
                    color = Color.Transparent,
                    lightY = 0f,
                    lightZ = 0f,
                    lightRadius = 0f,
                    causticColor = Color.Transparent,
                    layout = Rect.Zero,
                    sdf = 0f,
                    smoothCorner = false,
                )
            }
            val baseFabShadow = remember(destructiveColor, density, contourGlowEnabled) {
                CouiFabDefaults.shadow(destructiveColor, density.density, contourGlowEnabled).copy(smoothCorner = true)
            }
            val shapes = remember { listOf(WorkRowCardShape, CouiCapsuleShape) }
            SwipeRevealActivePair(
                backdrop = backdrop,
                renderer = renderer,
                materials = materials,
                cardShadow = cardShadow,
                baseFabShadow = baseFabShadow,
                shapes = shapes,
                destructiveColor = destructiveColor,
                contourGlowEnabled = contourGlowEnabled,
                minHeight = minHeight,
                minHeightPx = minHeightPx,
                rowHeightPx = rowHeightPx,
                fullContentWidthPx = fullContentWidthPx,
                visibleCardWidth = visibleCardWidth,
                visibleCardWidthPx = visibleCardWidthPx,
                deleteActionSizePx = deleteActionSizePx,
                deleteActionWidth = deleteActionWidth,
                deleteActionWidthPx = deleteActionWidthPx,
                openRestPx = openRestPx,
                deleteCommitPx = deleteCommitPx,
                leftShrinkPx = leftShrinkPx,
                rightShrinkPx = rightShrinkPx,
                cardCornerRadiusPx = cardCornerRadiusPx,
                deleteAlpha = deleteAlpha.value,
                deleteAnimating = deleteAnimating,
                deleteConfirmationRequested = deleteConfirmationRequested,
                isOpen = isOpen,
                showActionsLabel = showActionsLabel,
                onContentHeightChange = { contentHeightPx = it },
                onClickCard = { if (latestIsOpen) latestOnOpenChange(false) else latestOnClick() },
                onRequestOpen = { latestOnOpenChange(true) },
                requestDelete = requestDelete,
                endActionContent = endActionContent,
                content = content,
            )
        }
    }
}

@Composable
private fun SwipeRevealActivePair(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    materials: List<CouiFloatingMaterial>,
    cardShadow: CouiFloatingShadow,
    baseFabShadow: CouiFloatingShadow,
    shapes: List<androidx.compose.ui.graphics.Shape>,
    destructiveColor: Color,
    contourGlowEnabled: Boolean,
    minHeight: Dp,
    minHeightPx: Float,
    rowHeightPx: Float,
    fullContentWidthPx: Int,
    visibleCardWidth: Dp,
    visibleCardWidthPx: Float,
    deleteActionSizePx: Float,
    deleteActionWidth: Dp,
    deleteActionWidthPx: Float,
    openRestPx: Float,
    deleteCommitPx: Float,
    leftShrinkPx: Float,
    rightShrinkPx: Float,
    cardCornerRadiusPx: Float,
    deleteAlpha: Float,
    deleteAnimating: Boolean,
    deleteConfirmationRequested: Boolean,
    isOpen: Boolean,
    showActionsLabel: String,
    onContentHeightChange: (Float) -> Unit,
    onClickCard: () -> Unit,
    onRequestOpen: () -> Unit,
    requestDelete: () -> Unit,
    endActionContent: @Composable (
        committed: Boolean,
        confirmation: Boolean,
        interactive: Boolean,
        requestDelete: () -> Unit,
    ) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val contentLayers = List(2) { rememberGraphicsLayer() }
    val deleteCommitted = leftShrinkPx >= deleteCommitPx
    val revealFraction = (leftShrinkPx / openRestPx).coerceIn(0f, 1f)

    val cardBounds = if (rightShrinkPx > 0f) {
        if (isRtl) Rect(0f, 0f, visibleCardWidthPx, rowHeightPx)
        else Rect(fullContentWidthPx - visibleCardWidthPx, 0f, fullContentWidthPx.toFloat(), rowHeightPx)
    } else {
        if (isRtl) Rect(fullContentWidthPx - visibleCardWidthPx, 0f, fullContentWidthPx.toFloat(), rowHeightPx)
        else Rect(0f, 0f, visibleCardWidthPx, rowHeightPx)
    }
    val stickGapPx = with(density) { 1.dp.toPx() }
    val stickWidthPx = deleteActionSizePx + stickGapPx
    val (fabScaleX, fabScaleY, fabCenterX) = when {
        leftShrinkPx <= stickWidthPx -> {
            val availWidthPx = (leftShrinkPx - stickGapPx).coerceIn(1f, deleteActionSizePx)
            val sx = (availWidthPx / deleteActionSizePx).coerceIn(0f, 1f)
            val syProgress = (leftShrinkPx / stickWidthPx).coerceIn(0f, 1f)
            val sy = (0.58f + 0.42f * syProgress).coerceIn(0f, 1f)
            val cx = if (isRtl) availWidthPx * 0.5f else fullContentWidthPx - availWidthPx * 0.5f
            Triple(sx, sy, cx)
        }
        leftShrinkPx <= openRestPx -> {
            val halfSizePx = deleteActionSizePx * 0.5f
            val cx = if (isRtl) halfSizePx else fullContentWidthPx - halfSizePx
            Triple(1f, 1f, cx)
        }
        else -> {
            val halfWidthPx = deleteActionWidthPx * 0.5f
            val cx = if (isRtl) halfWidthPx else fullContentWidthPx - halfWidthPx
            Triple(1f, 1f, cx)
        }
    }
    val actionTopPx = ((rowHeightPx - deleteActionSizePx) * 0.5f).coerceAtLeast(0f)
    val fabHalfWidthPx = deleteActionWidthPx * 0.5f
    val fabBounds = Rect(
        left = fabCenterX - fabHalfWidthPx,
        top = actionTopPx,
        right = fabCenterX + fabHalfWidthPx,
        bottom = actionTopPx + deleteActionSizePx,
    )
    val fabRevealStartPx = with(density) { 4.dp.toPx() }
    val fabRevealSolidPx = with(density) { 8.dp.toPx() }
    val fabVisible = leftShrinkPx > fabRevealStartPx && deleteAlpha > 0.005f
    val fabSurfaceAppear = ((leftShrinkPx - fabRevealStartPx) / fabRevealSolidPx).coerceIn(0f, 1f)
    val fabAlpha = if (fabVisible) deleteAlpha * fabSurfaceAppear else 0f
    val fabIconStartPx = with(density) { 36.dp.toPx() }
    val fabIconFullPx = with(density) { 16.dp.toPx() }
    val fabIconAlpha = if (fabVisible) {
        ((leftShrinkPx - fabIconStartPx) / fabIconFullPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fabInteractive = fabVisible && revealFraction >= 0.55f && fabIconAlpha > 0.2f && !deleteAnimating
    val shadowStartPx = with(density) { 48.dp.toPx() }
    val shadowT = ((leftShrinkPx - shadowStartPx) / (openRestPx - shadowStartPx).coerceAtLeast(1f))
        .coerceIn(0f, 1f)
    val shadows = listOf(
        cardShadow,
        baseFabShadow.copy(
            color = if (shadowT > 0f) {
                destructiveColor.copy(alpha = (if (contourGlowEnabled) 0.16f else 0.24f) * shadowT)
            } else {
                Color.Transparent
            },
            causticColor = if (shadowT > 0f && contourGlowEnabled) {
                destructiveColor.copy(alpha = 0.35f * shadowT)
            } else {
                Color.Transparent
            },
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .couiFloatingPair(
                backdrop = backdrop,
                renderer = renderer,
                materials = materials,
                shadows = shadows,
                shapes = shapes,
                contentLayers = contentLayers,
                contourGlowEnabled = contourGlowEnabled,
            ) {
                val cardAlpha = if (deleteAnimating) deleteAlpha else 1f
                listOf(
                    CouiFloatingSurface(
                        bounds = cardBounds,
                        scaleX = 1f,
                        scaleY = 1f,
                        cornerWeight = 3f,
                        alpha = cardAlpha,
                        visible = visibleCardWidthPx > 2f && cardAlpha > 0.005f,
                        cornerRadius = cardCornerRadiusPx,
                    ),
                    CouiFloatingSurface(
                        bounds = fabBounds,
                        scaleX = fabScaleX,
                        scaleY = fabScaleY,
                        cornerWeight = 1f,
                        alpha = fabAlpha,
                        visible = fabVisible,
                        cornerRadius = null,
                    ),
                )
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(deleteActionWidth)
                .height(DeleteActionSize)
                .drawWithContent {
                    contentLayers[1].record { this@drawWithContent.drawContent() }
                },
        ) {
            if (fabVisible) {
                Box(Modifier.fillMaxSize().alpha(fabIconAlpha)) {
                    endActionContent(deleteCommitted, deleteConfirmationRequested, fabInteractive, requestDelete)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(if (rightShrinkPx > 0f) Alignment.CenterEnd else Alignment.CenterStart)
                .width(visibleCardWidth)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !deleteAnimating,
                    onClickLabel = if (isOpen) {
                        stringResource(R.string.history_close_actions)
                    } else {
                        stringResource(R.string.history_view_details)
                    },
                    onClick = onClickCard,
                )
                .semantics {
                    if (!isOpen && !deleteAnimating) {
                        customActions = listOf(
                            CustomAccessibilityAction(showActionsLabel) {
                                onRequestOpen()
                                true
                            },
                        )
                    }
                }
                .onSizeChanged { size ->
                    onContentHeightChange(max(minHeightPx, size.height.toFloat()))
                }
                .drawWithContent {
                    contentLayers[0].record { this@drawWithContent.drawContent() }
                },
        ) {
            ShiftedFixedWidthContent(
                fullWidthPx = fullContentWidthPx,
                shiftPx = if (rightShrinkPx > 0f) 0 else -leftShrinkPx.roundToInt(),
                content = content,
            )
        }
    }
}

@Composable
private fun ShiftedFixedWidthContent(
    fullWidthPx: Int,
    shiftPx: Int,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val contentConstraints = Constraints(
            minWidth = fullWidthPx,
            maxWidth = fullWidthPx,
            minHeight = 0,
            maxHeight = constraints.maxHeight,
        )
        val placeable = measurables.first().measure(contentConstraints)
        layout(width = constraints.maxWidth, height = placeable.height) {
            placeable.placeRelative(x = shiftPx, y = 0)
        }
    }
}

@Composable
private fun DeleteActionIcon(
    modifier: Modifier,
    confirmation: Boolean,
    committed: Boolean,
    color: Color,
) {
    val lidRotation by animateFloatAsState(
        targetValue = if (committed) -34f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "swipe_trash_lid_rotation",
    )
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.095f
        if (confirmation) {
            val checkPath = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.72f)
                lineTo(size.width * 0.78f, size.height * 0.30f)
            }
            drawPath(
                path = checkPath,
                color = color,
                style = Stroke(width = strokeWidth * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val bodyLeft = size.width * 0.26f
            val bodyTop = size.height * 0.38f
            val bodyWidth = size.width * 0.48f
            val bodyHeight = size.height * 0.42f
            val lidY = size.height * 0.28f
            val lidStart = Offset(size.width * 0.22f, lidY)
            val lidWidth = size.width * 0.56f
            drawRoundRect(
                color = color,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(size.minDimension * 0.06f),
                style = stroke,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.41f, bodyTop + size.height * 0.08f),
                end = Offset(size.width * 0.41f, bodyTop + bodyHeight - size.height * 0.08f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.59f, bodyTop + size.height * 0.08f),
                end = Offset(size.width * 0.59f, bodyTop + bodyHeight - size.height * 0.08f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            rotate(degrees = lidRotation, pivot = lidStart) {
                drawLine(
                    color = color,
                    start = lidStart,
                    end = Offset(lidStart.x + lidWidth, lidStart.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.43f, lidY - size.height * 0.08f),
                    end = Offset(size.width * 0.57f, lidY - size.height * 0.08f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
