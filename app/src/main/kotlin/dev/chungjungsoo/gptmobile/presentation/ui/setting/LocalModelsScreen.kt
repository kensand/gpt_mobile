package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.catalog.CatalogCapabilities
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    modifier: Modifier = Modifier,
    viewModel: LocalModelsViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<CatalogEntry?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingDownload?.let(viewModel::onDownloadClick)
        pendingDownload = null
    }
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onAuthActivityResult(result.data)
    }
    val licenseTabLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onLicenseTabClosed()
    }

    fun requestDownload(entry: CatalogEntry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = entry
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onDownloadClick(entry)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LocalModelsTopBar(
                scrollBehavior = scrollBehavior,
                onNavigationClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.items.isEmpty() -> {
                Text(
                    text = stringResource(R.string.local_models_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            else -> {
                Column(
                    Modifier
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = stringResource(
                            R.string.local_model_storage_used,
                            ModelCatalogParser.formatDownloadSize(uiState.totalStorageBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                    uiState.items.forEach { item ->
                        LocalModelItem(
                            item = item,
                            isCheckingAccess = uiState.checkingAccessEntryId == item.entry.id,
                            onDownload = { requestDownload(item.entry) },
                            onCancel = { viewModel.cancelDownload(item.entry) },
                            onDelete = { viewModel.onDeleteClick(item.entry) }
                        )
                    }
                }
            }
        }
    }

    when (val dialog = uiState.dialog) {
        is LocalModelsDialog.RamWarning -> {
            ConfirmDialog(
                title = stringResource(R.string.local_model_ram_warning_title),
                text = stringResource(R.string.local_model_ram_warning_message, dialog.entry.minRamGb),
                onConfirm = viewModel::confirmRamWarning,
                onDismiss = viewModel::dismissDialog
            )
        }

        is LocalModelsDialog.MeteredConfirm -> {
            ConfirmDialog(
                title = stringResource(R.string.local_model_metered_title),
                text = stringResource(
                    R.string.local_model_metered_message,
                    ModelCatalogParser.formatDownloadSize(dialog.entry.sizeInBytes)
                ),
                onConfirm = viewModel::confirmMeteredDownload,
                onDismiss = viewModel::dismissDialog
            )
        }

        is LocalModelsDialog.DeleteConfirm -> {
            ConfirmDialog(
                title = stringResource(R.string.local_model_delete),
                text = stringResource(R.string.local_model_delete_confirmation),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::dismissDialog
            )
        }

        is LocalModelsDialog.SignIn -> {
            HuggingFaceSignInSheet(
                sessionExpired = dialog.sessionExpired,
                onSignIn = {
                    val intent = viewModel.startHuggingFaceSignIn()
                    if (intent != null) {
                        authLauncher.launch(intent)
                    }
                },
                onDismiss = viewModel::dismissDialog
            )
        }

        is LocalModelsDialog.License -> {
            HuggingFaceLicenseSheet(
                onOpenAgreement = {
                    runCatching {
                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.intent.data = dialog.modelPageUrl.toUri()
                        licenseTabLauncher.launch(customTabsIntent.intent)
                    }
                },
                onRetry = viewModel::retryAfterLicense,
                onDismiss = viewModel::dismissDialog
            )
        }

        LocalModelsDialog.OAuthNotConfigured -> {
            MessageDialog(
                title = stringResource(R.string.local_model_oauth_not_configured_title),
                text = stringResource(R.string.local_model_oauth_not_configured_message),
                onDismiss = viewModel::dismissDialog
            )
        }

        LocalModelsDialog.ProbeError -> {
            MessageDialog(
                title = stringResource(R.string.local_model_probe_error_title),
                text = stringResource(R.string.local_model_probe_error_message),
                onDismiss = viewModel::dismissDialog
            )
        }

        LocalModelsDialog.SignInFailed -> {
            MessageDialog(
                title = stringResource(R.string.local_model_sign_in_title),
                text = stringResource(R.string.local_model_sign_in_failed),
                onDismiss = viewModel::dismissDialog
            )
        }

        LocalModelsDialog.Hidden -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalModelsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.local_models),
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
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalModelItem(
    item: LocalModelListItem,
    isCheckingAccess: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val entry = item.entry
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(
                R.string.local_model_requirements,
                ModelCatalogParser.formatDownloadSize(entry.sizeInBytes),
                entry.minRamGb
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        val capabilityLabels = capabilityLabels(entry.capabilities)
        if (capabilityLabels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                capabilityLabels.forEach { label ->
                    CapabilityBadge(text = label)
                }
            }
        }
        when {
            isCheckingAccess -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Text(
                    text = stringResource(R.string.local_model_checking_access),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item.status == LocalModelItemStatus.NOT_DOWNLOADED -> {
                if (entry.isGated) {
                    Text(
                        text = stringResource(R.string.local_model_gated_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.download))
                }
            }

            item.status == LocalModelItemStatus.DOWNLOADING -> {
                val percent = if (item.diskBytes > 0L) {
                    ((item.receivedBytes * 100) / item.diskBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                if (item.receivedBytes <= 0L) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
                Text(
                    text = downloadProgressText(item, percent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.local_model_cancel_download))
                }
            }

            item.status == LocalModelItemStatus.DOWNLOADED -> {
                Text(
                    text = stringResource(
                        R.string.local_model_on_disk,
                        ModelCatalogParser.formatDownloadSize(item.diskBytes)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            }

            item.status == LocalModelItemStatus.FAILED -> {
                Text(
                    text = item.errorMessage ?: stringResource(R.string.local_model_failed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.retry))
                    }
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun downloadProgressText(item: LocalModelListItem, percent: Int): String {
    val rate = if (item.bytesPerSecond > 0L) {
        stringResource(R.string.local_model_download_rate, ModelCatalogParser.formatDownloadSize(item.bytesPerSecond))
    } else {
        null
    }
    val eta = formatEta(item.remainingMs)
    return when {
        rate != null && eta != null -> stringResource(R.string.local_model_download_progress, percent, rate, eta)
        rate != null -> stringResource(R.string.local_model_download_progress_rate, percent, rate)
        else -> stringResource(R.string.local_model_download_percent, percent)
    }
}

@Composable
private fun formatEta(remainingMs: Long): String? {
    if (remainingMs <= 0L) return null
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.local_model_eta_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.local_model_eta_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.local_model_eta_seconds, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HuggingFaceSignInSheet(
    sessionExpired: Boolean,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.local_model_sign_in_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(
                    if (sessionExpired) {
                        R.string.local_model_session_expired
                    } else {
                        R.string.local_model_sign_in_message
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.local_model_sign_in_action))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HuggingFaceLicenseSheet(
    onOpenAgreement: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.local_model_license_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.local_model_license_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(
                onClick = onOpenAgreement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.local_model_open_license))
            }
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.local_model_license_retry))
            }
        }
    }
}

@Composable
private fun MessageDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        title = { Text(title) },
        text = { Text(text) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        title = { Text(title) },
        text = { Text(text) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CapabilityBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun capabilityLabels(capabilities: CatalogCapabilities): List<String> = buildList {
    if (capabilities.vision) add(stringResource(R.string.capability_vision))
    if (capabilities.tools) add(stringResource(R.string.capability_tools))
    if (capabilities.thinking) add(stringResource(R.string.capability_thinking))
}
