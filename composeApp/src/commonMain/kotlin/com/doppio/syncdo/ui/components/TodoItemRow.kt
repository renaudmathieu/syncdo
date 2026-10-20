package com.doppio.syncdo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.ui.glass.GlassTextShadow
import com.doppio.syncdo.ui.glass.GlassTint
import com.doppio.syncdo.ui.glass.GlassToggle
import com.doppio.syncdo.ui.glass.liquidGlass
import com.doppio.syncdo.ui.glass.rememberGlassPressState
import com.doppio.syncdo.ui.theme.Mo

private val RowShape = RoundedCornerShape(24.dp)

/**
 * Open tasks sit on an r24 glass surface; done tasks are plain (no surface),
 * struck through in text-tertiary. A one-line note preview shows under the title.
 */
@Composable
fun TodoItemRow(
    modifier: Modifier = Modifier,
    item: TodoItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val press = rememberGlassPressState()
    val titleColor by animateColorAsState(if (item.completed) Mo.TextTertiary else Mo.TextPrimary, tween(300))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (item.completed) Modifier else Modifier.liquidGlass({ RowShape }, GlassTint.Card, press = press))
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, top = 11.dp, end = 16.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassToggle(checked = item.completed, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (item.completed) FontWeight.Normal else FontWeight.SemiBold,
                    lineHeight = 22.sp,
                    letterSpacing = 0.sp,
                    shadow = GlassTextShadow,
                ),
                textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (item.note.isNotBlank() && !item.completed) {
                Text(
                    text = item.note,
                    color = Mo.TextPrimary.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = GlassTextShadow),
                )
            }
        }
    }
}
