package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsDialog

@Composable
fun rememberLocalModelDownloader(
    onDownload: (CatalogEntry) -> Unit
): (CatalogEntry) -> Unit {
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<CatalogEntry?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingDownload?.let(onDownload)
        pendingDownload = null
    }
    return { entry ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = entry
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDownload(entry)
        }
    }
}

@Composable
fun LocalModelDownloadDialogHost(
    dialog: LocalModelsDialog,
    onConfirmRamWarning: () -> Unit,
    onConfirmMeteredDownload: () -> Unit,
    onConfirmDelete: () -> Unit = {},
    onDismissDialog: () -> Unit,
    onStartSignIn: () -> Intent?,
    onAuthActivityResult: (Intent?) -> Unit,
    onLicenseTabClosed: () -> Unit,
    onRetryAfterLicense: () -> Unit
) {
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onAuthActivityResult(result.data)
    }
    val licenseTabLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onLicenseTabClosed()
    }

    when (dialog) {
        is LocalModelsDialog.RamWarning -> {
            ConfirmDialog(
                title = stringResource(R.string.local_model_ram_warning_title),
                text = stringResource(R.string.local_model_ram_warning_message, dialog.entry.minRamGb),
                onConfirm = onConfirmRamWarning,
                onDismiss = onDismissDialog
            )
        }

        is LocalModelsDialog.MeteredConfirm -> {
            ConfirmDialog(
                title = stringResource(R.string.local_model_metered_title),
                text = stringResource(
                    R.string.local_model_metered_message,
                    ModelCatalogParser.formatDownloadSize(dialog.entry.sizeInBytes)
                ),
                onConfirm = onConfirmMeteredDownload,
                onDismiss = onDismissDialog
            )
        }

        is LocalModelsDialog.DeleteConfirm -> {
            ConfirmDialog(
                title = stringResource(R.string.local_model_delete),
                text = stringResource(R.string.local_model_delete_confirmation),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDialog
            )
        }

        is LocalModelsDialog.SignIn -> {
            HuggingFaceSignInSheet(
                isSessionExpired = dialog.isSessionExpired,
                onSignIn = {
                    val intent = onStartSignIn()
                    if (intent != null) {
                        authLauncher.launch(intent)
                    }
                },
                onDismiss = onDismissDialog
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
                onRetry = onRetryAfterLicense,
                onDismiss = onDismissDialog
            )
        }

        LocalModelsDialog.OAuthNotConfigured -> {
            MessageDialog(
                title = stringResource(R.string.local_model_oauth_not_configured_title),
                text = stringResource(R.string.local_model_oauth_not_configured_message),
                onDismiss = onDismissDialog
            )
        }

        LocalModelsDialog.ProbeError -> {
            MessageDialog(
                title = stringResource(R.string.local_model_probe_error_title),
                text = stringResource(R.string.local_model_probe_error_message),
                onDismiss = onDismissDialog
            )
        }

        LocalModelsDialog.SignInFailed -> {
            MessageDialog(
                title = stringResource(R.string.local_model_sign_in_title),
                text = stringResource(R.string.local_model_sign_in_failed),
                onDismiss = onDismissDialog
            )
        }

        LocalModelsDialog.Hidden -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HuggingFaceSignInSheet(
    isSessionExpired: Boolean,
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
                    if (isSessionExpired) {
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
