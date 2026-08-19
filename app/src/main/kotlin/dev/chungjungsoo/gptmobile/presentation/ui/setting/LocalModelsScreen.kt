package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

            uiState.entries.isEmpty() -> {
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
                    uiState.entries.forEach { entry ->
                        LocalModelItem(entry = entry)
                    }
                }
            }
        }
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
private fun LocalModelItem(entry: CatalogEntry) {
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
        Text(
            text = stringResource(R.string.local_model_not_downloaded),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
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
