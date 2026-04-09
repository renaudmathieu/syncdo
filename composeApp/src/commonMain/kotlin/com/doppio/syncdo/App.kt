package com.doppio.syncdo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doppio.syncdo.di.AppModule
import com.doppio.syncdo.persistence.getStoragePath
import com.doppio.syncdo.persistence.readTextFile
import com.doppio.syncdo.persistence.writeTextFile
import com.doppio.syncdo.ui.screens.TaskDetailScreen
import com.doppio.syncdo.ui.screens.TaskListScreen
import com.doppio.syncdo.ui.theme.SyncDoTheme
import com.doppio.syncdo.viewmodel.TodoViewModel

private enum class ConnectionState { INPUT, CONNECTING, READY }

private const val SERVER_HOST_FILE = "server-host.txt"

@Composable
fun App() {
    var serverHost by remember { mutableStateOf("172.16.110.8") }
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
        Surface(
            color = MaterialTheme.colorScheme.surface,
        ) {
            when (connectionState) {
                ConnectionState.INPUT -> {
                    ConnectionScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                        modifier = Modifier
                            .fillMaxSize(),
                        viewModel = viewModel
                    )
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
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "SyncDO",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Enter your server IP address",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = serverHost,
                onValueChange = onServerHostChange,
                label = { Text("Server Host") },
                placeholder = { Text("172.16.110.8") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )


            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onConnect,
                enabled = serverHost.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Connect",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    modifier: Modifier = Modifier,
    viewModel: TodoViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var syncEnabled by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TaskListScreen(
        modifier = modifier
            .fillMaxSize(),
        todos = uiState.todos,
        syncStatus = uiState.syncStatus,
        syncEnabled = syncEnabled,
        onToggleSync = { enabled ->
            syncEnabled = enabled
            if (enabled) AppModule.startSync() else AppModule.stopSync()
        },
        onAddTodo = viewModel::addTodo,
        onToggleTodo = viewModel::toggleCompleted,
        onTodoClick = { id ->
            viewModel.selectTodo(id)
            showDetailSheet = true
        }
    )

    if (showDetailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)
            }
        ) {
            TaskDetailScreen(
                item = uiState.selectedTodo,
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
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
