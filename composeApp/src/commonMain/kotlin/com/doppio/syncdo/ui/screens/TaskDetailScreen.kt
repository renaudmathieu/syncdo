package com.doppio.syncdo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.model.TodoItem
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TaskDetailScreen(
    modifier: Modifier = Modifier,
    item: TodoItem?,
    onUpdateTitle: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onDelete: () -> Unit
) {
    if (item == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            Text(
                "Select a task",
                color = MaterialTheme.colorScheme.outline,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text(
            text = "Task Detail",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Title card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                text = "Title",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
            )
            TextField(
                value = editingTitle,
                onValueChange = {
                    editingTitle = it
                    onUpdateTitle(it)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().onFocusChanged { isTitleFocused = it.isFocused },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
            )
        }

        // Note card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Text(
                "Note",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
            )
            TextField(
                value = editingNote,
                onValueChange = {
                    editingNote = it
                    onUpdateNote(it)
                },
                placeholder = { Text("Add a note...", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().onFocusChanged { isNoteFocused = it.isFocused },
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                minLines = 3
            )
        }

        // Last modified
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Last modified",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )
            val localDateTime = item.lastModified.toLocalDateTime(TimeZone.currentSystemDefault())
            Text(
                text = "${localDateTime.date} ${
                    localDateTime.hour.toString().padStart(2, '0')
                }:${localDateTime.minute.toString().padStart(2, '0')}:${
                    localDateTime.second.toString().padStart(2, '0')
                }",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Delete button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                .clickable(onClick = onDelete),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Delete Task",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
