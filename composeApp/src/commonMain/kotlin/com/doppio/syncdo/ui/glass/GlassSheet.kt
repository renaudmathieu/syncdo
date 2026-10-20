package com.doppio.syncdo.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.doppio.syncdo.ui.theme.Mo
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private const val SheetEnterMillis = 820
private const val SheetEnterDelayMillis = 120
private const val SheetExitMillis = 280

/**
 * Modal glass sheet. It unfolds from a narrow, round drop at the bottom (translate 70%,
 * scaleX 0.86, corner 80 dp), overshoots on the spring curve, and settles at r36.
 * Drag down or go back to dismiss; a short drag springs back like gel.
 *
 * [pageBackdrop] is the recorded page under the sheet, so the sheet refracts the list too.
 * Content is laid out in a column and wrapped in its own backdrop, so cards inside refract the sheet.
 */
@Composable
fun BoxScope.GlassSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    pageBackdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var shown by remember { mutableStateOf(visible) }
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }

    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            exit.snapTo(0f)
            dragY.snapTo(0f)
            enter.snapTo(0f)
            enter.animateTo(1f, tween(SheetEnterMillis, SheetEnterDelayMillis, LinearEasing))
        } else if (shown) {
            exit.animateTo(1f, tween(SheetExitMillis, easing = FastOutLinearInEasing))
            shown = false
        }
    }
    if (!shown) return

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = visible,
        onBackCompleted = onDismissRequest,
    )

    // Scrim: fades in over 400 ms, out with the sheet
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                val fadeIn = (enter.value * SheetEnterMillis / 400f).coerceIn(0f, 1f)
                alpha = fadeIn * (1f - exit.value)
            }
            .background(Mo.ShadowInk.copy(alpha = 0.25f))
            .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
    )

    val scenery = LocalGlassBackdrop.current
    val backdrop = if (scenery != null) rememberCombinedBackdrop(scenery, pageBackdrop) else pageBackdrop
    val sheetLayer = rememberLayerBackdrop()
    val dragState = rememberDraggableState { delta ->
        scope.launch { dragY.snapTo((dragY.value + delta).coerceAtLeast(0f)) }
    }

    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Column(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Caps the height: the sheet never climbs under the status bar
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 48.dp)
                .graphicsLayer {
                    val t = enter.value
                    val e = GlassMotion.Spring
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val rise = keyframes(t, e, 0f to 0.70f, 0.7f to -0.015f, 1f to 0f)
                    translationY = rise * size.height + dragY.value + exit.value * size.height
                    scaleX = keyframes(t, e, 0f to 0.86f, 0.7f to 1.01f, 1f to 1f)
                    alpha = if (t == 0f) 0f else 1f - exit.value
                }
                .draggable(
                    dragState,
                    Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (dragY.value > dismissDistance || velocity > 1800f) onDismissRequest()
                        else dragY.animateTo(0f, tween(GlassMotion.ReleaseMillis, easing = GlassMotion.Spring))
                    },
                )
                .liquidGlass(
                    shape = {
                        val r = lerp(80f, 36f, GlassMotion.Spring.transform(enter.value)).dp
                        RoundedCornerShape(topStart = r, topEnd = r)
                    },
                    tint = GlassTint.Sheet,
                    blurRadius = 14.dp,
                    refractionHeight = 24.dp,
                    refractionAmount = 32.dp,
                    exportedBackdrop = sheetLayer,
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 22.dp, bottom = 24.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp, 4.dp)
                    .background(Mo.GlassEdge, RoundedCornerShape(2.dp))
            )
            CompositionLocalProvider(LocalGlassBackdrop provides sheetLayer) {
                content()
            }
        }
    }
}
