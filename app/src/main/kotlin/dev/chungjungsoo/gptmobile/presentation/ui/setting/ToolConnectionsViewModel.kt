package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ToolConnectionsViewModel @Inject constructor(
    toolConnectionDao: ToolConnectionDao,
    secretVault: SecretVault
) : ViewModel() {
    private val toolConnectionRepository = ToolConnectionRepository(toolConnectionDao, secretVault)

    private val _uiState = MutableStateFlow(ToolConnectionsUiState())
    val uiState: StateFlow<ToolConnectionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { toolConnectionRepository.listConnections().filter { it.type in WEB_SEARCH_TYPES } }
                .onSuccess { connections ->
                    _uiState.update { it.copy(connections = connections, errorMessage = null) }
                }
                .onFailure(::showError)
        }
    }

    fun saveConnection(
        existing: ToolConnection?,
        provider: WebToolProvider,
        name: String,
        alias: String,
        apiKey: String,
        clearCredential: Boolean
    ) {
        val normalizedAlias = normalizeAlias(alias)
        if (!isValidAlias(normalizedAlias)) {
            _uiState.update { it.copy(errorMessage = "Alias must match [a-z][a-z0-9_]{0,31}.") }
            return
        }
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name is required.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val connection = ToolConnection(
                connectionUid = existing?.connectionUid ?: UUID.randomUUID().toString(),
                name = name.trim(),
                alias = normalizedAlias,
                type = provider.type,
                endpointUrl = provider.endpointUrl,
                authType = provider.authType,
                secretRef = existing?.secretRef,
                oauthClientId = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            runCatching {
                val shouldClearCredential = shouldClearCredential(
                    existingType = existing?.type,
                    providerType = provider.type,
                    apiKey = apiKey,
                    clearCredential = clearCredential
                )
                toolConnectionRepository.upsertConnection(
                    connection = connection,
                    credential = credentialInput(apiKey, shouldClearCredential),
                    clearCredential = shouldClearCredential
                )
            }.onSuccess {
                refresh()
            }.onFailure(::showError)
        }
    }

    fun deleteConnection(connectionUid: String) {
        viewModelScope.launch {
            runCatching { toolConnectionRepository.deleteConnection(connectionUid) }
                .onSuccess { refresh() }
                .onFailure(::showError)
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(errorMessage = error.message ?: "Tool connection update failed.") }
    }

    data class ToolConnectionsUiState(
        val connections: List<ToolConnection> = emptyList(),
        val errorMessage: String? = null
    )

    companion object {
        val providers = listOf(
            WebToolProvider("Firecrawl", ToolConnectionType.FIRECRAWL, "https://api.firecrawl.dev/v2/search", ToolConnectionAuthType.BEARER),
            WebToolProvider("Perplexity", ToolConnectionType.PERPLEXITY, "https://api.perplexity.ai/search", ToolConnectionAuthType.BEARER),
            WebToolProvider("Exa", ToolConnectionType.EXA, "https://api.exa.ai/search", ToolConnectionAuthType.API_KEY)
        )

        private val WEB_SEARCH_TYPES = providers.map { it.type }.toSet()
        private val aliasRegex = Regex("[a-z][a-z0-9_]{0,31}")

        fun normalizeAlias(alias: String): String = alias.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")

        fun isValidAlias(alias: String): Boolean = aliasRegex.matches(alias)

        fun credentialInput(apiKey: String, clearCredential: Boolean): ByteArray? = apiKey.trim().takeIf { it.isNotEmpty() && !clearCredential }?.encodeToByteArray()

        fun shouldClearCredential(
            existingType: String?,
            providerType: String,
            apiKey: String,
            clearCredential: Boolean
        ): Boolean = clearCredential || (existingType != null && existingType != providerType && apiKey.isBlank())
    }
}

data class WebToolProvider(
    val label: String,
    val type: String,
    val endpointUrl: String,
    val authType: String
)
