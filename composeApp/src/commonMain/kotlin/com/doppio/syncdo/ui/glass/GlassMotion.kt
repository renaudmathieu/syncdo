package com.doppio.syncdo.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Liquid Glass motion, after Kyant0/AndroidLiquidGlass. Glass behaves like a gel:
 * it swells under the finger, follows the drag with resistance and springs back.
 */
object GlassMotion {
    /** Overshooting curve (overshoot 1.56) used for every release and settle. */
    val Spring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** Long, soft deceleration used for entrances. */
    val Lux: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    const val PressMillis = 220
    const val ReleaseMillis = 620
    const val SpotMillis = 250
    const val MaterializeMillis = 760
    const val StaggerMillis = 60
}

/**
 * CSS-style keyframes: [frames] are (offset, value) pairs sorted by offset in 0..1,
 * and [easing] applies to each segment, like `animation-timing-function`.
 */
internal fun keyframes(t: Float, easing: Easing, vararg frames: Pair<Float, Float>): Float {
    if (t <= frames.first().first) return frames.first().second
    for (i in 1 until frames.size) {
        val (t1, v1) = frames[i]
        if (t <= t1) {
            val (t0, v0) = frames[i - 1]
            val local = if (t1 == t0) 1f else (t - t0) / (t1 - t0)
            return lerp(v0, v1, easing.transform(local))
        }
    }
    return frames.last().second
}

/** Keyframe timeline driven by a linear 0..1 progress, played once on first composition. */
@Composable
internal fun rememberTimeline(durationMillis: Int, delayMillis: Int = 0, play: Boolean = true): Animatable<Float, *> {
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) progress.animateTo(1f, tween(durationMillis, delayMillis, LinearEasing))
    }
    return progress
}

/**
 * Materialize: glass enters squashed (0.92 / 0.88), rises 18 dp and overshoots slightly.
 * Pass [index] to stagger siblings by 60 ms; pass `play = false` to skip (already seen).
 */
@Composable
fun Modifier.materialize(index: Int = 0, baseDelayMillis: Int = 0, play: Boolean = true): Modifier {
    val p = rememberTimeline(GlassMotion.MaterializeMillis, baseDelayMillis + index * GlassMotion.StaggerMillis, play)
    return graphicsLayer { applyMaterialize(p.value) }
}

internal fun GraphicsLayerScope.applyMaterialize(t: Float) {
    val e = GlassMotion.Lux
    alpha = keyframes(t, e, 0f to 0f, 0.65f to 1f, 1f to 1f)
    translationY = keyframes(t, e, 0f to 18f, 0.65f to -2f, 1f to 0f) * density
    scaleX = keyframes(t, e, 0f to 0.92f, 0.65f to 1.015f, 1f to 1f)
    scaleY = keyframes(t, e, 0f to 0.88f, 0.65f to 1.02f, 1f to 1f)
}

/** Fade only, for surfaceless rows (done items). */
@Composable
fun Modifier.fadeIn(durationMillis: Int = 600, delayMillis: Int = 360, play: Boolean = true): Modifier {
    val p = rememberTimeline(durationMillis, delayMillis, play)
    return graphicsLayer { alpha = p.value }
}

/** Snackbar: rises from a small bead and stretches to full width. */
internal fun GraphicsLayerScope.applyBead(t: Float) {
    val e = GlassMotion.Spring
    transformOrigin = TransformOrigin(0.5f, 1f)
    alpha = keyframes(t, e, 0f to 0f, 0.55f to 1f, 1f to 1f).coerceIn(0f, 1f)
    translationY = keyframes(t, e, 0f to 40f, 0.55f to -4f, 1f to 0f) * density
    scaleX = keyframes(t, e, 0f to 0.35f, 0.55f to 1.04f, 1f to 1f)
    scaleY = keyframes(t, e, 0f to 0.7f, 0.55f to 0.96f, 1f to 1f)
}

/** Toggle icon pop: 0.4 to 1.18 to 1. */
internal fun GraphicsLayerScope.applyPop(t: Float) {
    val e = GlassMotion.Spring
    val s = keyframes(t, e, 0f to 0.4f, 0.6f to 1.18f, 1f to 1f)
    scaleX = s
    scaleY = s
    alpha = keyframes(t, e, 0f to 0f, 0.6f to 1f, 1f to 1f).coerceIn(0f, 1f)
}

/**
 * Press and drag state of one glass element: scale 1 + 4 dp / height, a light spot
 * under the finger, a rubber-band drag (size * tanh(0.05 * drag / size)) with
 * anisotropic stretch, and a spring release.
 */
@Stable
class GlassPressState {
    internal val translationX = Animatable(0f)
    internal val translationY = Animatable(0f)
    internal val scaleX = Animatable(1f)
    internal val scaleY = Animatable(1f)
    internal val spotAlpha = Animatable(0f)
    var spot by mutableStateOf(Offset.Zero)
        internal set

    internal fun apply(scope: GraphicsLayerScope) = with(scope) {
        translationX = this@GlassPressState.translationX.value
        translationY = this@GlassPressState.translationY.value
        scaleX = this@GlassPressState.scaleX.value
        scaleY = this@GlassPressState.scaleY.value
    }

    internal fun press(scope: CoroutineScope, scale: Float) {
        val spec = tween<Float>(GlassMotion.PressMillis, easing = GlassMotion.Spring)
        scope.launch { scaleX.animateTo(scale, spec) }
        scope.launch { scaleY.animateTo(scale, spec) }
        scope.launch { spotAlpha.animateTo(1f, tween(GlassMotion.SpotMillis)) }
    }

    internal suspend fun drag(tx: Float, ty: Float, sx: Float, sy: Float) {
        translationX.snapTo(tx)
        translationY.snapTo(ty)
        scaleX.snapTo(sx)
        scaleY.snapTo(sy)
    }

    internal fun release(scope: CoroutineScope) {
        val spec = tween<Float>(GlassMotion.ReleaseMillis, easing = GlassMotion.Spring)
        scope.launch { translationX.animateTo(0f, spec) }
        scope.launch { translationY.animateTo(0f, spec) }
        scope.launch { scaleX.animateTo(1f, spec) }
        scope.launch { scaleY.animateTo(1f, spec) }
        scope.launch { spotAlpha.animateTo(0f, tween(GlassMotion.SpotMillis)) }
    }
}

@Composable
fun rememberGlassPressState(): GlassPressState = remember { GlassPressState() }

/**
 * Observes the pointer without consuming it, so `clickable`, swipe and scroll keep working.
 * When a parent consumes the drag (list scroll, swipe to dismiss), the glass springs back.
 */
internal fun Modifier.glassPressInput(state: GlassPressState): Modifier = pointerInput(state) {
    val swell = 4.dp.toPx()
    coroutineScope {
        val scope = this
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.pressed && !it.previousPressed } ?: continue
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                val base = 1f + swell / h
                state.spot = down.position
                state.press(scope, base)

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.isConsumed && change.positionChanged()) break
                    state.spot = change.position
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    val m = min(w, h)
                    val tx = m * tanh(0.05f * dx / m) * 4f
                    val ty = m * tanh(0.05f * dy / m) * 4f
                    val d = swell / h
                    val a = atan2(dy, dx)
                    val big = max(w, h)
                    val sx = base + d * abs(cos(a) * dx / big) * min(w / h, 1f) * 6f
                    val sy = base + d * abs(sin(a) * dy / big) * min(h / w, 1f) * 6f
                    scope.launch { state.drag(tx, ty, sx, sy) }
                }
                state.release(scope)
            }
        }
    }
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChanged(): Boolean =
    position != previousPosition
