package com.doppio.syncdo.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.doppio.syncdo.ui.theme.Mo
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Fill recipe of a glass surface. Near-clear by default, tinted for primary and destructive actions. */
enum class GlassTint { Clear, Card, Accent, Danger, Sheet }

/** What glass surfaces refract. Provided by [GlassRoot]; null falls back to a drawn-only surface. */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Specular light position, as a fraction of each surface's own box: fixed at the top left. */
private val LightPosition = Offset(0.2f, -0.1f)

/**
 * Liquid Glass surface, after Kyant0/AndroidLiquidGlass: vibrancy, a low blur, lens refraction
 * at the edges, a specular rim lit from the top left, and (with [press]) a jelly press stretch.
 */
@Composable
fun Modifier.liquidGlass(
    shape: () -> Shape,
    tint: GlassTint = GlassTint.Clear,
    press: GlassPressState? = null,
    blurRadius: Dp = 2.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 18.dp,
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val layerBlock: (GraphicsLayerScope.() -> Unit)? = press?.let { p -> { p.apply(this) } }
    val surface: DrawScope.() -> Unit = { drawGlassSurface(shape(), tint, LightPosition, press) }
    val input = if (press != null) Modifier.glassPressInput(press) else Modifier

    if (backdrop == null) {
        return this
            .then(input)
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .drawBehind(surface)
    }
    return this
        .then(input)
        .drawBackdrop(
            backdrop = backdrop,
            shape = shape,
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                lens(refractionHeight.toPx(), refractionAmount.toPx())
            },
            highlight = { RimHighlight },
            shadow = { DropShadow },
            innerShadow = { EdgeGlow },
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawSurface = surface,
        )
}

// inset 1.5px 1.5px 0 -0.5px edge: specular rim lit from the top left
private val RimHighlight = Highlight(
    width = 1.dp,
    style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.55f), angle = 45f),
)

// 0 10px 30px -6px rgba(0,0,0,.45)
private val DropShadow = Shadow(radius = 24.dp, offset = DpOffset(0.dp, 10.dp), color = Color.Black.copy(alpha = 0.45f))

// inset 0 0 18px edge-dim
private val EdgeGlow = InnerShadow(radius = 18.dp, offset = DpOffset.Zero, color = Mo.GlassEdgeDim)

private fun DrawScope.drawGlassSurface(shape: Shape, tint: GlassTint, light: Offset, press: GlassPressState?) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    clipPath(path) {
        // Base fill
        drawRect(
            when (tint) {
                GlassTint.Clear -> Mo.GlassFillCardTop.copy(alpha = Mo.GlassFillCardTop.alpha * 0.45f)
                GlassTint.Card -> Mo.GlassFillCardTop.copy(alpha = Mo.GlassFillCardTop.alpha * 0.55f)
                GlassTint.Accent -> Mo.Accent.copy(alpha = 0.62f)
                GlassTint.Danger -> Mo.ErrorDim.copy(alpha = 0.45f)
                GlassTint.Sheet -> Mo.GlassPageFillTop.copy(alpha = Mo.GlassPageFillTop.alpha * 0.70f)
            }
        )
        // Chrome gradient (160deg, or 180deg for the sheet)
        val stops = when (tint) {
            GlassTint.Clear -> arrayOf(0f to Mo.GlassFillChromeTop, 0.55f to Mo.GlassFillChromeBottom)
            GlassTint.Card, GlassTint.Danger -> arrayOf(0f to Mo.GlassFillChromeTop, 0.6f to Color.Transparent)
            GlassTint.Accent -> arrayOf(0f to Mo.GlassSheen, 0.55f to Color.Transparent)
            GlassTint.Sheet -> arrayOf(0f to Mo.GlassFillChromeTop, 0.3f to Color.Transparent)
        }
        drawRect(cssLinearGradient(if (tint == GlassTint.Sheet) 180f else 160f, size, stops))

        // Specular sheen following the light, plus a tint bounce from the bottom right
        val sheen = Mo.GlassSheen
        drawEllipseGlow(
            center = Offset(light.x * size.width, light.y * size.height),
            radii = Size(size.width * 1.2f, size.height * 0.7f),
            color = sheen, stop = 0.45f, alpha = 0.55f, blendMode = BlendMode.Screen,
        )
        drawEllipseGlow(
            center = Offset(size.width, size.height * 1.2f),
            radii = Size(size.width * 0.8f, size.height * 0.6f),
            color = Mo.GlassTint, stop = 0.5f, alpha = 0.55f, blendMode = BlendMode.Screen,
        )

        // Light spot blooming under the finger
        if (press != null) {
            val a = press.spotAlpha.value
            if (a > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.38f), 0.7f to Color.Transparent,
                        center = press.spot, radius = 90.dp.toPx(),
                    ),
                    radius = 90.dp.toPx(), center = press.spot, alpha = a, blendMode = BlendMode.Plus,
                )
            }
        }
        // inset 0 0 0 1px tint ring (half of the stroke falls outside the clip)
        drawPath(path, Mo.GlassTint, style = Stroke(width = 2.dp.toPx()))
    }
}

/** CSS `linear-gradient(<angle>deg, ...)` over a box of [size]. */
internal fun cssLinearGradient(angleDeg: Float, size: Size, stops: Array<Pair<Float, Color>>): Brush {
    val rad = angleDeg * PI.toFloat() / 180f
    val dir = Offset(sin(rad), -cos(rad))
    val len = abs(size.width * sin(rad)) + abs(size.height * cos(rad))
    val c = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(*stops, start = c - dir * (len / 2f), end = c + dir * (len / 2f))
}

/** Elliptical `radial-gradient(<rx> <ry> at <center>, color, transparent <stop>)`. */
internal fun DrawScope.drawEllipseGlow(
    center: Offset,
    radii: Size,
    color: Color,
    stop: Float,
    alpha: Float = 1f,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (radii.width <= 0f || radii.height <= 0f) return
    val ratio = radii.height / radii.width
    scale(scaleX = 1f, scaleY = ratio, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to color, stop to Color.Transparent, center = center, radius = radii.width),
            radius = radii.width, center = center, alpha = alpha, blendMode = blendMode,
        )
    }
}

/** Root of the glass UI: paints the scenery that glass refracts and exposes it as the backdrop. */
@Composable
fun GlassRoot(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scenery = rememberLayerBackdrop()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().layerBackdrop(scenery)) { drawScenery() }
        CompositionLocalProvider(LocalGlassBackdrop provides scenery) {
            content()
        }
    }
}

private fun DrawScope.drawScenery() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Mo.BgMuted, Mo.ShadowInk)))
    drawEllipseGlow(Offset(w * 0.5f, h * 0.6f), Size(300.dp.toPx(), 300.dp.toPx()), Mo.GlowPurple, 0.7f)
    // Hard-edged color drops: the lens has something to bend
    drawOval(Mo.AccentTealDim, topLeft = Offset(w * 0.15f - 96.dp.toPx(), h * 0.95f - 96.dp.toPx()), size = Size(192.dp.toPx(), 192.dp.toPx()))
    drawOval(Mo.AccentPinkDim, topLeft = Offset(w * 0.85f - 144.dp.toPx(), h * 0.95f - 120.dp.toPx()), size = Size(288.dp.toPx(), 240.dp.toPx()))
    drawOval(Mo.AccentBlueDim, topLeft = Offset(w - 108.dp.toPx(), h * 0.4f - 108.dp.toPx()), size = Size(216.dp.toPx(), 216.dp.toPx()))
    drawOval(Mo.AccentDim, topLeft = Offset(-132.dp.toPx(), h * 0.55f - 120.dp.toPx()), size = Size(264.dp.toPx(), 240.dp.toPx()))
    // Diagonal hairlines, 24 dp apart
    val step = 24.dp.toPx() * 1.4142f
    val stroke = 1.dp.toPx()
    var x = -h
    while (x < w) {
        drawLine(Color.White.copy(alpha = 0.025f), Offset(x, h), Offset(x + h, 0f), strokeWidth = stroke)
        x += step
    }
    drawRect(Mo.ShadowInk.copy(alpha = 0.12f))
    // Top fade under the header
    val fade = 230.dp.toPx()
    drawRect(
        Brush.verticalGradient(0f to Mo.ShadowInk, 0.35f to Mo.ShadowInk, 1f to Color.Transparent, endY = fade),
        size = Size(w, fade), alpha = 0.7f,
    )
}
