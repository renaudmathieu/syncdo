package com.doppio.syncdo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.doppio.syncdo.model.TodoItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TodoItemRow(
    modifier: Modifier = Modifier,
    item: TodoItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        leadingContent = {
            IconToggleButton(
                checked = item.completed,
                onCheckedChange = { onToggle() },
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            ) {
                if (item.completed) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Completed",
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Not completed",
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.completed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    )
}
