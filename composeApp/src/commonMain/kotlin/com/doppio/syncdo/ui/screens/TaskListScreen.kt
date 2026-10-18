package com.doppio.syncdo.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doppio.syncdo.model.TodoItem
import com.doppio.syncdo.sync.SyncStatus
import com.doppio.syncdo.ui.components.SyncStatusIndicator
import com.doppio.syncdo.ui.components.TodoItemRow
import com.doppio.syncdo.ui.glass.GlassFab
import com.doppio.syncdo.ui.glass.GlassMotion
import com.doppio.syncdo.ui.glass.GlassPillField
import com.doppio.syncdo.ui.glass.GlassSectionHeader
import com.doppio.syncdo.ui.glass.GlassSnackbar
import com.doppio.syncdo.ui.glass.GlassSwitch
import com.doppio.syncdo.ui.glass.fadeIn
import com.doppio.syncdo.ui.glass.materialize
import com.doppio.syncdo.ui.theme.Mo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UndoWindowMillis = 4_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    modifier: Modifier = Modifier,
    todos: List<TodoItem>,
    syncStatus: SyncStatus,
    syncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onTodoClick: (String) -> Unit
) {
    var newTaskText by remember { mutableStateOf("") }
    var doneExpanded by remember { mutableStateOf(true) }

    // Swipe-to-delete is held back for the undo window, then committed
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingDelete) {
        val id = pendingDelete ?: return@LaunchedEffect
        delay(UndoWindowMillis)
        onDeleteTodo(id)
        pendingDelete = null
    }
    val requestDelete: (String) -> Unit = { id ->
        pendingDelete?.let(onDeleteTodo)
        pendingDelete = id
    }

    val visible = todos.filter { it.id != pendingDelete }
    val open = visible.filter { !it.completed }
    val done = visible.filter { it.completed }

    // Entrance plays once per task: the first batch staggers, later additions materialize alone
    val seen = remember { mutableSetOf<String>() }
    var firstBatch by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { firstBatch = false }

    val submit = {
        if (newTaskText.isNotBlank()) {
            onAddTodo(newTaskText)
            newTaskText = ""
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Header(syncStatus, syncEnabled, onToggleSync)

            if (visible.isEmpty()) {
                EmptyState(Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header-open") {
                        GlassSectionHeader("To do", open.size, Modifier.animateItem().materialize(play = firstBatch))
                    }
                    itemsIndexed(open, seen, firstBatch) { index, todo, play ->
                        SwipeableTodo(
                            todo = todo,
                            onToggle = { onToggleTodo(todo.id) },
                            onDelete = { requestDelete(todo.id) },
                            onClick = { onTodoClick(todo.id) },
                            modifier = Modifier.materialize(index = index + 1, play = play),
                        )
                    }
                    item(key = "header-done") {
                        GlassSectionHeader(
                            title = "Done",
                            count = done.size,
                            trailing = if (doneExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            onClick = { doneExpanded = !doneExpanded },
                            modifier = Modifier.animateItem().padding(top = 8.dp).materialize(play = firstBatch),
                        )
                    }
                    if (doneExpanded) {
                        itemsIndexed(done, seen, firstBatch) { _, todo, play ->
                            SwipeableTodo(
                                todo = todo,
                                onToggle = { onToggleTodo(todo.id) },
                                onDelete = { requestDelete(todo.id) },
                                onClick = { onTodoClick(todo.id) },
                                modifier = Modifier.fadeIn(play = play),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val undoId = pendingDelete
            if (undoId != null) {
                key(undoId) {
                    GlassSnackbar(
                        message = "Task deleted",
                        actionLabel = "Undo",
                        onAction = { pendingDelete = null },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassPillField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    placeholder = "Add a new task...",
                    onDone = submit,
                    modifier = Modifier.weight(1f).materialize(baseDelayMillis = 300),
                )
                GlassFab(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add task",
                    onClick = submit,
                    modifier = Modifier.materialize(baseDelayMillis = 380),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(syncStatus: SyncStatus, syncEnabled: Boolean, onToggleSync: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 68.dp, end = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Switch shares the title row so it reads as part of the app bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SyncDO",
                style = MaterialTheme.typography.headlineMedium,
                color = Mo.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                tooltip = { PlainTooltip { Text(if (syncEnabled) "Disable sync" else "Enable sync") } },
                state = rememberTooltipState(),
            ) {
                GlassSwitch(
                    checked = syncEnabled,
                    onCheckedChange = onToggleSync,
                    modifier = Modifier.materialize(),
                )
            }
        }
        SyncStatusIndicator(status = syncStatus)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(start = 40.dp, end = 40.dp, bottom = 130.dp).materialize(baseDelayMillis = 120),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Rounded.Checklist, contentDescription = null, tint = Mo.Accent, modifier = Modifier.size(40.dp))
        Text(
            "No tasks yet",
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
            color = Mo.TextPrimary,
        )
        Text(
            "Add one below. Tasks stay on this device until you turn sync on.",
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp),
            color = Mo.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Keyed items that know whether their entrance already played. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    todos: List<TodoItem>,
    seen: MutableSet<String>,
    firstBatch: Boolean,
    content: @Composable LazyItemScope.(index: Int, todo: TodoItem, play: Boolean) -> Unit,
) {
    items(todos.size, key = { todos[it].id }) { index ->
        val todo = todos[index]
        val play = remember(todo.id) { todo.id !in seen }
        SideEffect { seen += todo.id }
        Box(
            Modifier.animateItem(
                fadeInSpec = null,
                placementSpec = tween(GlassMotion.ReleaseMillis, easing = GlassMotion.Spring),
                fadeOutSpec = tween(200),
            )
        ) {
            content(if (firstBatch) index else 0, todo, play)
        }
    }
}

/**
 * Swipe end to start deletes (error-dim), start to end completes (accent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTodo(
    todo: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggle()
                    scope.launch { state.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val deleting = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val completing = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        when {
                            deleting -> Mo.ErrorDim.copy(alpha = 0.7f)
                            completing -> Mo.Accent.copy(alpha = 0.55f)
                            else -> Mo.ShadowInk.copy(alpha = 0f)
                        }
                    )
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, if (deleting) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (deleting || completing) {
                    val color = if (deleting) Mo.ErrorOnDim else Mo.TextPrimary
                    Icon(
                        if (deleting) Icons.Rounded.Delete else Icons.Rounded.Check,
                        contentDescription = null, tint = color, modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (deleting) "Delete" else if (todo.completed) "Reopen" else "Done",
                        color = color, style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    ) {
        TodoItemRow(item = todo, onToggle = onToggle, onClick = onClick)
    }
}
