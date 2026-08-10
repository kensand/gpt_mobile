package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolConnectionsViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingConnection by remember { mutableStateOf<ToolConnection?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deletingConnection by remember { mutableStateOf<ToolConnection?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ToolConnectionsTopBar(
                scrollBehavior = scrollBehavior,
                onNavigationClick = onNavigationClick,
                onAddClick = {
                    editingConnection = null
                    showForm = true
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            uiState.connections.forEach { connection ->
                ToolConnectionItem(
                    connection = connection,
                    onEditClick = {
                        editingConnection = connection
                        showForm = true
                    },
                    onDeleteClick = { deletingConnection = connection }
                )
            }
        }
    }

    if (showForm) {
        ToolConnectionDialog(
            connection = editingConnection,
            onDismissRequest = { showForm = false },
            onSave = { provider, name, alias, apiKey, clearCredential ->
                viewModel.saveConnection(editingConnection, provider, name, alias, apiKey, clearCredential)
                showForm = false
            }
        )
    }

    deletingConnection?.let { connection ->
        AlertDialog(
            title = { Text(stringResource(R.string.delete_tool_connection)) },
            text = { Text(stringResource(R.string.delete_tool_connection_confirmation, connection.name)) },
            onDismissRequest = { deletingConnection = null },
            confirmButton = {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = "Delete ${connection.name}" },
                    onClick = {
                        viewModel.deleteConnection(connection.connectionUid)
                        deletingConnection = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingConnection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConnectionsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit,
    onAddClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.web_tools),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier
                    .padding(4.dp)
                    .semantics { contentDescription = "Navigate back from Web tools" },
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            IconButton(
                modifier = Modifier.semantics { contentDescription = "Add web tool connection" },
                onClick = onAddClick
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add_tool_connection))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun ToolConnectionItem(
    connection: ToolConnection,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Edit ${connection.name}" },
        headlineContent = { Text(connection.name, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = "${providerLabel(connection.type)} • ${connection.alias} • ${connection.endpointUrl.orEmpty()} • ${
                    if (connection.secretRef == null) {
                        stringResource(R.string.credential_not_set)
                    } else {
                        stringResource(R.string.credential_set)
                    }
                }",
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = "Edit ${connection.name}" },
                    onClick = onEditClick
                ) {
                    Text(stringResource(R.string.edit))
                }
                IconButton(
                    modifier = Modifier.semantics { contentDescription = "Delete ${connection.name}" },
                    onClick = onDeleteClick
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    )
}

@Composable
private fun ToolConnectionDialog(
    connection: ToolConnection?,
    onDismissRequest: () -> Unit,
    onSave: (WebToolProvider, String, String, String, Boolean) -> Unit
) {
    val initialProvider = ToolConnectionsViewModel.providers.firstOrNull { it.type == connection?.type }
        ?: ToolConnectionsViewModel.providers.first()
    var provider by remember { mutableStateOf(initialProvider) }
    var name by remember { mutableStateOf(connection?.name.orEmpty()) }
    var alias by remember { mutableStateOf(connection?.alias.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var clearCredential by remember { mutableStateOf(false) }
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    val normalizedAlias = ToolConnectionsViewModel.normalizeAlias(alias)
    val isAliasInvalid = alias.isNotBlank() && !ToolConnectionsViewModel.isValidAlias(normalizedAlias)
    val aliasDescription = stringResource(R.string.stable_alias_description)
    val aliasError = stringResource(R.string.stable_alias_error)

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(if (connection == null) stringResource(R.string.add_tool_connection) else stringResource(R.string.edit_tool_connection)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ToolConnectionsViewModel.providers.forEach { option ->
                    RadioItem(
                        modifier = Modifier.semantics { contentDescription = "Provider ${option.label}" },
                        title = option.label,
                        description = option.endpointUrl,
                        value = option.type,
                        selected = provider.type == option.type
                    ) {
                        provider = option
                        if (name.isBlank()) name = option.label
                        if (alias.isBlank()) alias = ToolConnectionsViewModel.normalizeAlias(option.label)
                    }
                }
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Connection name" },
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Stable alias"
                            if (isAliasInvalid) error(aliasError)
                        },
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.stable_alias)) },
                    isError = isAliasInvalid,
                    supportingText = { Text(if (isAliasInvalid) aliasError else aliasDescription) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = provider.endpointUrl,
                    onValueChange = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.api_url)) },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "API key" },
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    supportingText = {
                        Text(
                            if (connection?.secretRef == null) {
                                stringResource(R.string.credential_not_set)
                            } else {
                                stringResource(R.string.blank_key_preserves_credential)
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (connection != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Clear API key" }
                            .padding(top = 8.dp)
                    ) {
                        Checkbox(
                            checked = clearCredential,
                            onCheckedChange = { clearCredential = it }
                        )
                        Text(
                            modifier = Modifier.padding(top = 12.dp),
                            text = stringResource(R.string.clear_api_key)
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && ToolConnectionsViewModel.isValidAlias(normalizedAlias),
                onClick = { onSave(provider, name, alias, apiKey, clearCredential) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun providerLabel(type: String): String = ToolConnectionsViewModel.providers.firstOrNull { it.type == type }?.label ?: type
