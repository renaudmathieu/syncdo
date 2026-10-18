package com.doppio.syncdo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doppio.syncdo.di.AppModule
import com.doppio.syncdo.persistence.getStoragePath
import com.doppio.syncdo.persistence.readTextFile
import com.doppio.syncdo.persistence.writeTextFile
import com.doppio.syncdo.ui.glass.GlassButton
import com.doppio.syncdo.ui.glass.GlassHostField
import com.doppio.syncdo.ui.glass.GlassRoot
import com.doppio.syncdo.ui.glass.GlassSheet
import com.doppio.syncdo.ui.glass.materialize
import com.doppio.syncdo.ui.screens.TaskDetailScreen
import com.doppio.syncdo.ui.screens.TaskListScreen
import com.doppio.syncdo.ui.theme.Mo
import com.doppio.syncdo.ui.theme.SyncDoTheme
import com.doppio.syncdo.viewmodel.TodoViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private enum class ConnectionState { INPUT, CONNECTING, READY }

private const val SERVER_HOST_FILE = "server-host.txt"

@Composable
fun App() {
    var serverHost by remember { mutableStateOf("") }
    var connectionState by remember { mutableStateOf(ConnectionState.INPUT) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load saved server host
    LaunchedEffect(Unit) {
        val saved = try {
            readTextFile("${getStoragePath()}/$SERVER_HOST_FILE")
        } catch (_: Exception) {
            null
        }
        if (!saved.isNullOrBlank()) {
            serverHost = saved.trim()
        }
    }

    SyncDoTheme {
        GlassRoot(Modifier.fillMaxSize()) {
            Crossfade(connectionState, animationSpec = tween(300)) { state ->
                when (state) {
                    ConnectionState.INPUT -> {
                        ConnectionScreen(
                            modifier = Modifier.fillMaxSize(),
                            serverHost = serverHost,
                            onServerHostChange = { serverHost = it },
                            errorMessage = errorMessage,
                            onConnect = {
                                errorMessage = null
                                connectionState = ConnectionState.CONNECTING
                            }
                        )
                    }

                    ConnectionState.CONNECTING -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlassSpinner()
                        }

                        LaunchedEffect(Unit) {
                            try {
                                AppModule.initialize(serverHost.trim())
                                // Persist for next launch
                                try {
                                    writeTextFile("${getStoragePath()}/$SERVER_HOST_FILE", serverHost.trim())
                                } catch (_: Exception) {
                                }
                                connectionState = ConnectionState.READY
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Connection failed"
                                connectionState = ConnectionState.INPUT
                            }
                        }
                    }

                    ConnectionState.READY -> {
                        val viewModel = remember { TodoViewModel(AppModule.repository) }
                        AppContent(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    modifier: Modifier = Modifier,
    serverHost: String,
    onServerHostChange: (String) -> Unit,
    errorMessage: String?,
    onConnect: () -> Unit
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "SyncDO",
            style = MaterialTheme.typography.displayMedium,
            color = Mo.Accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Enter your server IP address",
            style = MaterialTheme.typography.bodyMedium,
            color = Mo.TextSecondary,
        )

        GlassHostField(
            value = serverHost,
            onValueChange = onServerHostChange,
            label = "Server Host",
            placeholder = "localhost or 192.168.1.42",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { if (serverHost.isNotBlank()) onConnect() }),
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = Mo.ErrorFg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassButton(
            text = "Connect",
            onClick = onConnect,
            enabled = serverHost.isNotBlank(),
            modifier = Modifier.fillMaxWidth().materialize(baseDelayMillis = 120),
        )
    }
}

/** 48 dp ring, two accent quarters, one turn per second. */
@Composable
private fun GlassSpinner() {
    val turn by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
    )
    Canvas(Modifier.size(48.dp).graphicsLayer { rotationZ = turn }) {
        val stroke = 4.dp.toPx()
        drawArc(
            color = Mo.Accent,
            startAngle = -135f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
    }
}

@Composable
private fun AppContent(
    modifier: Modifier = Modifier,
    viewModel: TodoViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var syncEnabled by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }

    // The page is recorded so the detail sheet refracts the list beneath it
    val page = rememberLayerBackdrop()

    Box(modifier) {
        Box(Modifier.fillMaxSize().layerBackdrop(page)) {
            TaskListScreen(
                modifier = Modifier.fillMaxSize(),
                todos = uiState.todos,
                syncStatus = uiState.syncStatus,
                syncEnabled = syncEnabled,
                onToggleSync = { enabled ->
                    syncEnabled = enabled
                    if (enabled) AppModule.startSync() else scope.launch { AppModule.stopSync() }
                },
                onAddTodo = viewModel::addTodo,
                onToggleTodo = viewModel::toggleCompleted,
                onDeleteTodo = viewModel::removeTodo,
                onTodoClick = { id ->
                    viewModel.selectTodo(id)
                    showDetailSheet = true
                }
            )
        }

        GlassSheet(
            visible = showDetailSheet,
            onDismissRequest = { showDetailSheet = false },
            pageBackdrop = page,
        ) {
            TaskDetailScreen(
                item = uiState.selectedTodo,
                syncStatus = uiState.syncStatus,
                onUpdateTitle = { title ->
                    uiState.selectedTodoId?.let { viewModel.updateTitle(it, title) }
                },
                onUpdateNote = { note ->
                    uiState.selectedTodoId?.let { viewModel.updateNote(it, note) }
                },
                onDelete = {
                    uiState.selectedTodoId?.let { id ->
                        viewModel.removeTodo(id)
                        showDetailSheet = false
                    }
                },
            )
        }
    }
}
