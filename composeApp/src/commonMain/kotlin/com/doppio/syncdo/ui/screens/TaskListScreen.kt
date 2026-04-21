package com.doppio.syncdo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doppio.syncdo.sync.SyncStatus
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.ui.components.SyncStatusIndicator
import com.doppio.syncdo.ui.components.TodoItemRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TaskListScreen(
    modifier: Modifier = Modifier,
    todos: List<TodoItem>,
    syncStatus: SyncStatus,
    syncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onTodoClick: (String) -> Unit
) {
    var newTaskText by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = "SyncDO",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                subtitle = {
                    SyncStatusIndicator(status = syncStatus)
                },
                actions = {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                        tooltip = { PlainTooltip { Text("Turn Switch On") } },
                        state = rememberTooltipState(),
                    ) {
                        Switch(
                            checked = syncEnabled,
                            onCheckedChange = onToggleSync,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .windowInsetsPadding(BottomAppBarDefaults.windowInsets),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    placeholder = {
                        Text(
                            text = "Add a new task...",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newTaskText.isNotBlank()) {
                            onAddTodo(newTaskText)
                            newTaskText = ""
                        }
                    })
                )

                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = {
                        if (newTaskText.isNotBlank()) {
                            onAddTodo(newTaskText)
                            newTaskText = ""
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add task",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = innerPadding + PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(todos, key = { it.id }) { todo ->
                TodoItemRow(
                    item = todo,
                    onToggle = { onToggleTodo(todo.id) },
                    onClick = { onTodoClick(todo.id) }
                )
            }
        }
    }
}
