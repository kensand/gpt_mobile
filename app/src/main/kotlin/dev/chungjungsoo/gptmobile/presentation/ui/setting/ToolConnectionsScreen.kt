package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ToolConnectionsViewModel = hiltViewModel(),
    onLaunchOAuth: (String) -> Unit = {},
    onAddConnectionClick: () -> Unit,
    onEditConnectionClick: (String) -> Unit,
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deletingConnection by remember { mutableStateOf<ToolConnection?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.oauthLaunches.collect(onLaunchOAuth)
    }
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
                onAddClick = onAddConnectionClick
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
                    onEditClick = { onEditConnectionClick(connection.connectionUid) },
                    onOAuthClick = { viewModel.startOAuth(connection.connectionUid) },
                    onDeleteClick = { deletingConnection = connection }
                )
            }
        }
    }

    deletingConnection?.let { connection ->
        val deleteDescription = stringResource(R.string.delete_named_connection, connection.name)
        AlertDialog(
            title = { Text(stringResource(R.string.delete_tool_connection)) },
            text = { Text(stringResource(R.string.delete_tool_connection_confirmation, connection.name)) },
            onDismissRequest = { deletingConnection = null },
            confirmButton = {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
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
fun ToolConnectionEditorScreen(
    modifier: Modifier = Modifier,
    connectionUid: String? = null,
    viewModel: ToolConnectionsViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connection = connectionUid?.let { uid -> uiState.connections.firstOrNull { it.connectionUid == uid } }
    val isEditing = connectionUid != null
    val title = stringResource(if (isEditing) R.string.edit_tool_connection else R.string.add_tool_connection)
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )

    if (isEditing && connection == null) {
        Scaffold(
            modifier = modifier,
            topBar = {
                ToolConnectionEditorTopBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    isSaveEnabled = false,
                    onNavigationClick = onNavigationClick,
                    onSaveClick = {}
                )
            }
        ) { innerPadding ->
            Text(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(24.dp),
                text = stringResource(R.string.tool_connection_not_found)
            )
        }
        return
    }

    val initialProvider = ToolConnectionsViewModel.providers.firstOrNull { it.type == connection?.type }
        ?: ToolConnectionsViewModel.providers.first()
    var provider by remember(connection?.connectionUid) { mutableStateOf(initialProvider) }
    var name by remember(connection?.connectionUid) { mutableStateOf(connection?.name.orEmpty()) }
    var alias by remember(connection?.connectionUid) { mutableStateOf(connection?.alias.orEmpty()) }
    var endpoint by remember(connection?.connectionUid) { mutableStateOf(connection?.endpointUrl.orEmpty()) }
    var authType by remember(connection?.connectionUid) { mutableStateOf(connection?.authType ?: initialProvider.authType) }
    var credential by remember(connection?.connectionUid) { mutableStateOf("") }
    var oauthClientId by remember(connection?.connectionUid) { mutableStateOf(connection?.oauthClientId.orEmpty()) }
    var allowCleartext by remember(connection?.connectionUid) { mutableStateOf(connection?.allowCleartext == true) }
    var clearCredential by remember(connection?.connectionUid) { mutableStateOf(false) }
    val normalizedAlias = ToolConnectionsViewModel.normalizeAlias(alias)
    val isMcp = provider.type == ToolConnectionType.MCP
    val actualEndpoint = if (isMcp) endpoint else provider.endpointUrl
    val isEndpointValid = !isMcp || ToolConnectionsViewModel.isValidMcpEndpoint(actualEndpoint, allowCleartext)
    val isSaveEnabled = name.isNotBlank() && ToolConnectionsViewModel.isValidAlias(normalizedAlias) && isEndpointValid

    Scaffold(
        modifier = modifier,
        topBar = {
            ToolConnectionEditorTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                isSaveEnabled = isSaveEnabled,
                onNavigationClick = onNavigationClick,
                onSaveClick = {
                    viewModel.saveConnection(
                        connection,
                        provider,
                        name,
                        alias,
                        actualEndpoint,
                        authType,
                        credential,
                        oauthClientId,
                        allowCleartext,
                        clearCredential,
                        onSaveComplete
                    )
                }
            )
        }
    ) { innerPadding ->
        ToolConnectionForm(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 24.dp),
            connection = connection,
            provider = provider,
            name = name,
            alias = alias,
            endpoint = actualEndpoint,
            authType = authType,
            credential = credential,
            oauthClientId = oauthClientId,
            allowCleartext = allowCleartext,
            clearCredential = clearCredential,
            isMcp = isMcp,
            isEndpointValid = isEndpointValid,
            onProviderChange = { option ->
                provider = option
                if (name.isBlank()) name = option.label
                if (alias.isBlank()) alias = ToolConnectionsViewModel.normalizeAlias(option.label)
                endpoint = if (option.type == ToolConnectionType.MCP) {
                    connection?.endpointUrl?.takeIf { connection.type == ToolConnectionType.MCP }.orEmpty()
                } else {
                    option.endpointUrl
                }
                authType = if (option.type == ToolConnectionType.MCP) {
                    connection?.authType?.takeIf { connection.type == ToolConnectionType.MCP } ?: ToolConnectionAuthType.NONE
                } else {
                    option.authType
                }
            },
            onNameChange = { name = it },
            onAliasChange = { alias = it },
            onEndpointChange = { endpoint = it },
            onAuthTypeChange = { authType = it },
            onCredentialChange = { credential = it },
            onOAuthClientIdChange = { oauthClientId = it },
            onAllowCleartextChange = { allowCleartext = it },
            onClearCredentialChange = { clearCredential = it }
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
                text = stringResource(R.string.tool_connections),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            IconButton(
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
    onOAuthClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val editDescription = stringResource(R.string.edit_named_connection, connection.name)
    val deleteDescription = stringResource(R.string.delete_named_connection, connection.name)
    val connectDescription = stringResource(R.string.connect_with_oauth, connection.name)
    val credentialStatus = when {
        connection.authType == ToolConnectionAuthType.NONE -> stringResource(R.string.public_access)
        connection.authType == ToolConnectionAuthType.OAUTH && connection.secretRef == null -> stringResource(R.string.oauth_not_connected)
        connection.authType == ToolConnectionAuthType.OAUTH -> stringResource(R.string.oauth_connected)
        connection.secretRef == null -> stringResource(R.string.credential_not_set)
        else -> stringResource(R.string.credential_set)
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = editDescription },
        headlineContent = { Text(connection.name, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = "${providerLabel(connection.type)} • ${connection.alias} • ${connection.endpointUrl.orEmpty()} • $credentialStatus",
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row {
                if (connection.type == ToolConnectionType.MCP && connection.authType == ToolConnectionAuthType.OAUTH) {
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = connectDescription },
                        onClick = onOAuthClick
                    ) {
                        Text(stringResource(if (connection.secretRef == null) R.string.connect else R.string.reconnect))
                    }
                }
                TextButton(
                    modifier = Modifier.semantics { contentDescription = editDescription },
                    onClick = onEditClick
                ) {
                    Text(stringResource(R.string.edit))
                }
                IconButton(
                    modifier = Modifier.semantics { contentDescription = deleteDescription },
                    onClick = onDeleteClick
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    )
}

@Composable
private fun ToolConnectionForm(
    modifier: Modifier = Modifier,
    connection: ToolConnection?,
    provider: ToolConnectionProvider,
    name: String,
    alias: String,
    endpoint: String,
    authType: String,
    credential: String,
    oauthClientId: String,
    allowCleartext: Boolean,
    clearCredential: Boolean,
    isMcp: Boolean,
    isEndpointValid: Boolean,
    onProviderChange: (ToolConnectionProvider) -> Unit,
    onNameChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onAuthTypeChange: (String) -> Unit,
    onCredentialChange: (String) -> Unit,
    onOAuthClientIdChange: (String) -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit
) {
    val normalizedAlias = ToolConnectionsViewModel.normalizeAlias(alias)
    val isAliasInvalid = alias.isNotBlank() && !ToolConnectionsViewModel.isValidAlias(normalizedAlias)
    val aliasDescription = stringResource(R.string.stable_alias_description)
    val aliasError = stringResource(R.string.stable_alias_error)
    val streamableHttp = stringResource(R.string.streamable_http)
    val connectionName = stringResource(R.string.connection_name)
    val stableAlias = stringResource(R.string.stable_alias)
    val apiKey = stringResource(R.string.api_key)
    val bearerToken = stringResource(R.string.bearer_token)
    val clearCredentialDescription = stringResource(R.string.clear_saved_credential)
    val allowCleartextDescription = stringResource(R.string.allow_cleartext_mcp_endpoint)
    Column(modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.tool_connection_provider_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ToolConnectionsViewModel.providers.forEach { option ->
            val providerDescription = stringResource(R.string.provider_option, option.label)
            RadioItem(
                modifier = Modifier.semantics { contentDescription = providerDescription },
                title = option.label,
                description = option.endpointUrl.ifBlank { streamableHttp },
                value = option.type,
                selected = provider.type == option.type
            ) { onProviderChange(option) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.tool_connection_details_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = connectionName },
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = stableAlias
                    if (isAliasInvalid) error(aliasError)
                },
            value = alias,
            onValueChange = onAliasChange,
            label = { Text(stringResource(R.string.stable_alias)) },
            isError = isAliasInvalid,
            supportingText = { Text(if (isAliasInvalid) aliasError else aliasDescription) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = endpoint,
            onValueChange = onEndpointChange,
            enabled = isMcp,
            label = { Text(stringResource(R.string.api_url)) },
            isError = !isEndpointValid,
            supportingText = if (isMcp && !isEndpointValid) {
                { Text(stringResource(R.string.mcp_endpoint_error)) }
            } else {
                null
            },
            singleLine = true
        )
        if (isMcp) {
            Text(
                text = stringResource(R.string.authentication),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            listOf(
                ToolConnectionAuthType.NONE to stringResource(R.string.public_access),
                ToolConnectionAuthType.BEARER to bearerToken,
                ToolConnectionAuthType.OAUTH to stringResource(R.string.oauth_pkce)
            ).forEach { (value, label) ->
                val authDescription = stringResource(R.string.mcp_authentication, label)
                RadioItem(
                    modifier = Modifier.semantics { contentDescription = authDescription },
                    title = label,
                    description = null,
                    value = value,
                    selected = authType == value
                ) { onAuthTypeChange(value) }
            }
            if (endpoint.startsWith("http://", ignoreCase = true)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = allowCleartextDescription }
                        .padding(top = 8.dp)
                ) {
                    Checkbox(checked = allowCleartext, onCheckedChange = onAllowCleartextChange)
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = stringResource(R.string.cleartext_mcp_warning)
                    )
                }
            }
        }
        if (!isMcp || authType != ToolConnectionAuthType.NONE) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.tool_connection_credentials_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (!isMcp || authType == ToolConnectionAuthType.BEARER) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = if (isMcp) bearerToken else apiKey },
                value = credential,
                onValueChange = onCredentialChange,
                label = { Text(if (isMcp) bearerToken else apiKey) },
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
        }
        if (isMcp && authType == ToolConnectionAuthType.OAUTH) {
            val oauthClientIdDescription = stringResource(R.string.oauth_client_id)
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = oauthClientIdDescription },
                value = oauthClientId,
                onValueChange = onOAuthClientIdChange,
                label = { Text(stringResource(R.string.preregistered_client_id_optional)) },
                supportingText = { Text(stringResource(R.string.dynamic_client_registration_hint)) },
                singleLine = true
            )
        }
        if (connection != null && (!isMcp || authType != ToolConnectionAuthType.NONE)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = clearCredentialDescription }
                    .padding(top = 8.dp)
            ) {
                Checkbox(
                    checked = clearCredential,
                    onCheckedChange = onClearCredentialChange
                )
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = stringResource(R.string.clear_saved_credential)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolConnectionEditorTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    isSaveEnabled: Boolean,
    onNavigationClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val saveDescription = stringResource(R.string.save_tool_connection)

    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            TextButton(
                modifier = Modifier.semantics { contentDescription = saveDescription },
                enabled = isSaveEnabled,
                onClick = onSaveClick
            ) {
                Text(stringResource(R.string.save))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

private fun providerLabel(type: String): String = ToolConnectionsViewModel.providers.firstOrNull { it.type == type }?.label ?: type
