package com.doppio.syncdo.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.doppio.syncdo.ui.theme.LocalMonoStyles
import com.doppio.syncdo.ui.theme.Mo
import kotlinx.coroutines.delay

/** Text over glass gets a soft drop shadow to stay legible on any backdrop. */
val GlassTextShadow = Shadow(color = Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 2f), blurRadius = 4f)

private val Pill = RoundedCornerShape(percent = 50)

/** Clickable without the ripple: glass answers with its own press stretch and light spot. */
private fun Modifier.glassClickable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit) =
    clickable(interactionSource = null, indication = null, enabled = enabled, role = role, onClick = onClick)

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: GlassTint = GlassTint.Accent,
    icon: ImageVector? = null,
) {
    val press = rememberGlassPressState()
    val effectiveTint = if (enabled) tint else GlassTint.Clear
    val contentColor = when {
        !enabled -> Mo.TextSecondary
        tint == GlassTint.Danger -> Mo.ErrorOnDim
        tint == GlassTint.Accent -> Color.White
        else -> Mo.TextPrimary
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .liquidGlass({ Pill }, effectiveTint, press = if (enabled) press else null)
            .glassClickable(enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (tint == GlassTint.Danger) 15.sp else 16.sp,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
fun GlassFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberGlassPressState()
    Box(
        modifier = modifier
            .size(48.dp)
            .liquidGlass({ CircleShape }, GlassTint.Accent, press = press)
            .glassClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * Completion toggle: a clear pill when open, an accent squircle (r12) when done.
 * The corner morphs on the spring curve and the check pops (0.4 to 1.18 to 1).
 */
@Composable
fun GlassToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val press = rememberGlassPressState()
    val radius by animateDpAsState(if (checked) 12.dp else 20.dp, tween(420, easing = GlassMotion.Spring))
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) first = false
        else {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(520, easing = LinearEasing))
        }
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .liquidGlass({ RoundedCornerShape(radius) }, if (checked) GlassTint.Accent else GlassTint.Clear, press = press)
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = if (checked) "Completed" else "Not completed",
            tint = if (checked) Color.White else Mo.TextSecondary,
            modifier = Modifier.size(24.dp).graphicsLayer { applyPop(pop.value) },
        )
    }
}

/**
 * Sync switch, 52 x 32. While the thumb travels it turns into a clear glass lens
 * (40 dp, rimmed), then settles back to solid white.
 */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) Mo.Accent.copy(alpha = 0.7f) else Mo.GlassControlFill, tween(400))
    val travel by animateDpAsState(if (checked) 20.dp else 0.dp, tween(560, easing = GlassMotion.Spring))
    val morph = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        morph.animateTo(1f, tween(380, easing = GlassMotion.Spring))
        delay(40)
        morph.animateTo(0f, tween(380, easing = GlassMotion.Spring))
    }
    Box(
        modifier = modifier
            .size(52.dp, 32.dp)
            .toggleable(checked, interactionSource = null, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .drawBehind {
                val r = size.height / 2f
                drawRoundRect(track, cornerRadius = CornerRadius(r))
                drawRoundRect(
                    Mo.GlassControlBorder, cornerRadius = CornerRadius(r),
                    style = Stroke(1.dp.toPx()),
                )
                drawRoundRect(
                    Brush.linearGradient(listOf(Mo.GlassEdge, Color.Transparent), end = Offset(size.width * 0.4f, size.height)),
                    cornerRadius = CornerRadius(r), style = Stroke(0.75.dp.toPx()),
                )
            },
    ) {
        Box(
            Modifier
                .offset(x = 4.dp + travel, y = 4.dp)
                .size(24.dp)
                .graphicsLayer {
                    // 24 dp thumb to a 40 dp lens, same center
                    val s = lerp(1f, 40f / 24f, morph.value)
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    val m = morph.value.coerceIn(0f, 1f)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    // Soft shadow under the thumb
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent,
                            center = c + Offset(0f, 2.dp.toPx()), radius = r + 6.dp.toPx(),
                        ),
                        radius = r + 6.dp.toPx(), center = c + Offset(0f, 2.dp.toPx()),
                    )
                    // Solid white to a clear lens
                    drawCircle(Color.White.copy(alpha = lerp(1f, 0.06f, m)), r, c)
                    if (m > 0f) {
                        drawCircle(
                            Brush.linearGradient(listOf(Mo.GlassEdge, Color.Transparent, Mo.GlassSheen)),
                            r - 0.5.dp.toPx(), c, alpha = m, style = Stroke(1.dp.toPx()),
                        )
                        drawCircle(Mo.GlassTint, r - 1.dp.toPx(), c, alpha = m, style = Stroke(1.dp.toPx()))
                    }
                }
        )
    }
}

/** Add-task field: a clear glass pill. */
@Composable
fun GlassPillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = rememberGlassPressState()
    val style = MaterialTheme.typography.bodyLarge.copy(color = Mo.TextPrimary, letterSpacing = 0.sp, shadow = GlassTextShadow)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Mo.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.height(56.dp).liquidGlass({ Pill }, GlassTint.Clear, press = press),
        decorationBox = { inner ->
            Box(Modifier.padding(start = 20.dp, end = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Mo.TextTertiary))
                inner()
            }
        },
    )
}

/**
 * Server host field (r12). The label floats up to the top edge on focus or once filled,
 * following the spring curve.
 */
@Composable
fun GlassHostField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val floated = focused || value.isNotEmpty()
    val lift = remember { Animatable(if (floated) 1f else 0f) }
    LaunchedEffect(floated) { lift.animateTo(if (floated) 1f else 0f, tween(300, easing = GlassMotion.Spring)) }
    val labelColor by animateColorAsState(if (focused) Mo.Accent else Mo.TextTertiary, tween(220))
    val style = MaterialTheme.typography.bodyLarge.copy(color = Mo.TextPrimary, letterSpacing = 0.sp)

    // The glass is a sibling so the floated label is not clipped by the glass layer
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Box(Modifier.matchParentSize().liquidGlass({ RoundedCornerShape(12.dp) }, GlassTint.Clear))
        HostTextField(value, onValueChange, interaction, style, keyboardOptions, keyboardActions) { inner ->
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (focused && value.isEmpty()) Text(placeholder, style = style.copy(color = Mo.TextSecondary))
                inner()
                Text(
                    text = label,
                    color = labelColor,
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        val t = lift.value
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = lerp(1f, 0.75f, t)
                        scaleY = scaleX
                        // From the vertical center up to sit just above the top edge
                        translationY = lerp(0f, -38.dp.toPx(), t)
                    },
                )
            }
        }
    }
}

@Composable
private fun HostTextField(
    value: String,
    onValueChange: (String) -> Unit,
    interaction: MutableInteractionSource,
    style: androidx.compose.ui.text.TextStyle,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    decoration: @Composable (inner: @Composable () -> Unit) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Mo.Accent),
        interactionSource = interaction,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxSize(),
        decorationBox = decoration,
    )
}

@Composable
fun GlassBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Mo.ErrorOnDim,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
        maxLines = 1,
        modifier = modifier
            .liquidGlass({ Pill }, GlassTint.Danger, refractionHeight = 6.dp, refractionAmount = 8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Overline label in JetBrains Mono, as used by cards and section headers. */
@Composable
fun GlassOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = LocalMonoStyles.current.overline.copy(shadow = GlassTextShadow),
        color = Mo.TextPrimary.copy(alpha = 0.78f),
        modifier = modifier,
    )
}

/** Detail field card: r24 glass, mono overline label, then content. */
@Composable
fun GlassCard(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val press = rememberGlassPressState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass({ RoundedCornerShape(24.dp) }, GlassTint.Card, press = press)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassOverline(label)
        content()
    }
}

/** Snackbar: rises from a small bead and stretches to full width. */
@Composable
fun GlassSnackbar(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val p = rememberTimeline(durationMillis = 700, delayMillis = 0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { applyBead(p.value) }
            .liquidGlass({ Pill }, GlassTint.Clear, blurRadius = 6.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Mo.TextPrimary, style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp))
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.height(40.dp).glassClickable(onClick = onAction).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(actionLabel, color = Mo.AccentBlue, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** List section header: mono overline, count, optional trailing icon. */
@Composable
fun GlassSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.glassClickable(onClick = onClick) else Modifier)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassOverline(title)
        Text(count.toString(), style = LocalMonoStyles.current.caption, color = Mo.TextTertiary)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Icon(trailing, contentDescription = null, tint = Mo.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
