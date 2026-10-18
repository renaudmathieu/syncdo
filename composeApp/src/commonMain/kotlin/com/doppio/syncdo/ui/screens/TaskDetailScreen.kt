package com.doppio.syncdo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.sync.SyncStatus
import com.doppio.syncdo.ui.glass.GlassBadge
import com.doppio.syncdo.ui.glass.GlassButton
import com.doppio.syncdo.ui.glass.GlassCard
import com.doppio.syncdo.ui.glass.GlassOverline
import com.doppio.syncdo.ui.glass.GlassTint
import com.doppio.syncdo.ui.glass.materialize
import com.doppio.syncdo.ui.theme.Mo
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Content cards land after the sheet has unfolded (sheet starts at 120 ms, cards at 380 ms). */
private const val ContentDelayMillis = 380

@Composable
fun TaskDetailScreen(
    modifier: Modifier = Modifier,
    item: TodoItem?,
    syncStatus: SyncStatus = SyncStatus.Synced,
    onUpdateTitle: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onDelete: () -> Unit
) {
    if (item == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            Text(
                "Select a task",
                color = Mo.TextTertiary,
                modifier = Modifier.align(Alignment.Center).padding(48.dp)
            )
        }
        return
    }

    var editingTitle by remember(item.id) { mutableStateOf(item.title) }
    var isTitleFocused by remember { mutableStateOf(false) }
    LaunchedEffect(item.title) {
        if (!isTitleFocused) editingTitle = item.title
    }

    var editingNote by remember(item.id) { mutableStateOf(item.note) }
    var isNoteFocused by remember { mutableStateOf(false) }
    LaunchedEffect(item.note) {
        if (!isNoteFocused) editingNote = item.note
    }

    val fieldStyle = MaterialTheme.typography.bodyLarge.copy(color = Mo.TextPrimary, letterSpacing = 0.sp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Task Detail",
                style = MaterialTheme.typography.headlineMedium,
                color = Mo.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (syncStatus == SyncStatus.Offline) {
                GlassBadge("Offline · edits queued", Modifier.materialize(baseDelayMillis = ContentDelayMillis))
            }
        }

        GlassCard("Title", Modifier.materialize(baseDelayMillis = ContentDelayMillis)) {
            DetailField(
                value = editingTitle,
                onValueChange = {
                    editingTitle = it
                    onUpdateTitle(it)
                },
                style = fieldStyle,
                singleLine = true,
                onFocus = { isTitleFocused = it },
            )
        }

        GlassCard("Note", Modifier.heightIn(min = 139.dp).materialize(index = 1, baseDelayMillis = ContentDelayMillis)) {
            DetailField(
                value = editingNote,
                onValueChange = {
                    editingNote = it
                    onUpdateNote(it)
                },
                style = fieldStyle.copy(fontSize = 14.sp, lineHeight = 24.sp),
                placeholder = "Add a note...",
                minLines = 3,
                onFocus = { isNoteFocused = it },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassOverline("Last modified")
            val local = item.lastModified.toLocalDateTime(TimeZone.currentSystemDefault())
            val time = listOf(local.hour, local.minute, local.second).joinToString(":") { it.toString().padStart(2, '0') }
            Text(
                text = "${local.date} $time",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                color = Mo.TextTertiary,
            )
        }

        GlassButton(
            text = "Delete Task",
            onClick = onDelete,
            tint = GlassTint.Danger,
            icon = Icons.Rounded.Delete,
            modifier = Modifier.fillMaxWidth().materialize(index = 2, baseDelayMillis = ContentDelayMillis),
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Transparent text field inside a detail card. */
@Composable
private fun DetailField(
    value: String,
    onValueChange: (String) -> Unit,
    style: TextStyle,
    onFocus: (Boolean) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
    placeholder: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(Mo.Accent),
        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = style.copy(color = Mo.TextTertiary))
                }
                inner()
            }
        },
    )
}
