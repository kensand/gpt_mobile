package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    toolConnectionDao: ToolConnectionDao,
    secretVault: SecretVault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val toolConnectionRepository = ToolConnectionRepository(toolConnectionDao, secretVault)

    private val platformUid: String = checkNotNull(savedStateHandle["platformUid"])

    private val _platformState = MutableStateFlow<PlatformV2?>(null)
    val platformState: StateFlow<PlatformV2?> = _platformState.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    private val _toolBindingState = MutableStateFlow(ToolBindingState())
    val toolBindingState: StateFlow<ToolBindingState> = _toolBindingState.asStateFlow()

    init {
        loadPlatform()
        loadToolBindings()
    }

    private fun loadPlatform() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatformV2s()
            val platform = platforms.firstOrNull { it.uid == platformUid }
            _platformState.update { platform }
        }
    }

    fun loadToolBindings() {
        viewModelScope.launch {
            runCatching {
                val connections = toolConnectionRepository.listConnections().filter { it.type in WEB_SEARCH_TYPES }
                val bindings = toolConnectionRepository.listBindingsByProfile(platformUid)
                ToolBindingState(
                    searchConnections = connections,
                    selectedSearchConnectionUid = bindings.firstOrNull { it.toolName == WEB_SEARCH_TOOL }?.connectionUid,
                    readUrlEnabled = bindings.any { it.toolName == BuiltInAgentTool.READ_URL },
                    errorMessage = null
                )
            }.onSuccess { state ->
                _toolBindingState.update { state }
            }.onFailure(::showToolError)
        }
    }

    fun toggleEnabled() {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(enabled = !platform.enabled))
        }
    }

    fun toggleReasoning() {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(reasoning = !platform.reasoning))
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
            _platformState.update { platform }
        }
    }

    fun openPlatformNameDialog() = _dialogState.update { it.copy(isPlatformNameDialogOpen = true) }
    fun closePlatformNameDialog() = _dialogState.update { it.copy(isPlatformNameDialogOpen = false) }

    fun openApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = true) }
    fun closeApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = false) }

    fun openApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = true) }
    fun closeApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = false) }

    fun openApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = true) }
    fun closeApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = false) }

    fun openTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = true) }
    fun closeTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = false) }

    fun openTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = true) }
    fun closeTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = false) }

    fun openSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }
    fun closeSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }

    fun openTimeoutDialog() = _dialogState.update { it.copy(isTimeoutDialogOpen = true) }
    fun closeTimeoutDialog() = _dialogState.update { it.copy(isTimeoutDialogOpen = false) }

    fun openGeminiSafetyDialog() = _dialogState.update { it.copy(isGeminiSafetyDialogOpen = true) }
    fun closeGeminiSafetyDialog() = _dialogState.update { it.copy(isGeminiSafetyDialogOpen = false) }

    fun updatePlatformName(name: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(name = name.trim()))
            closePlatformNameDialog()
        }
    }

    fun updateApiUrl(url: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(apiUrl = url.trim()))
            closeApiUrlDialog()
        }
    }

    fun updateApiToken(token: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(token = token.trim().takeIf { it.isNotEmpty() }))
            closeApiTokenDialog()
        }
    }

    fun updateApiModel(model: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(model = model.trim()))
            closeApiModelDialog()
        }
    }

    fun updateTemperature(temperature: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(temperature = temperature))
            closeTemperatureDialog()
        }
    }

    fun updateTopP(topP: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(topP = topP))
            closeTopPDialog()
        }
    }

    fun updateSystemPrompt(prompt: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(systemPrompt = prompt.trim()))
            closeSystemPromptDialog()
        }
    }

    fun updateTimeout(timeoutSeconds: Int) {
        _platformState.value?.let { platform ->
            val normalizedTimeout = timeoutSeconds.coerceAtLeast(0)
            updatePlatform(platform.copy(timeout = normalizedTimeout))
            closeTimeoutDialog()
        }
    }

    fun updateGeminiSafetySettings(
        harassmentSafetyThreshold: String,
        hateSpeechSafetyThreshold: String,
        sexuallyExplicitSafetyThreshold: String,
        dangerousContentSafetyThreshold: String
    ) {
        _platformState.value?.let { platform ->
            updatePlatform(
                platform.copy(
                    harassmentSafetyThreshold = GeminiSafetySettings.normalizeThreshold(harassmentSafetyThreshold),
                    hateSpeechSafetyThreshold = GeminiSafetySettings.normalizeThreshold(hateSpeechSafetyThreshold),
                    sexuallyExplicitSafetyThreshold = GeminiSafetySettings.normalizeThreshold(sexuallyExplicitSafetyThreshold),
                    dangerousContentSafetyThreshold = GeminiSafetySettings.normalizeThreshold(dangerousContentSafetyThreshold)
                )
            )
            closeGeminiSafetyDialog()
        }
    }

    fun openDeleteDialog() = _dialogState.update { it.copy(isDeleteDialogOpen = true) }
    fun closeDeleteDialog() = _dialogState.update { it.copy(isDeleteDialogOpen = false) }

    fun deletePlatform() {
        _platformState.value?.let { platform ->
            viewModelScope.launch {
                settingRepository.deletePlatformV2(platform)
                closeDeleteDialog()
                _isDeleted.update { true }
            }
        }
    }

    fun openSearchBackendDialog() = _toolBindingState.update { it.copy(isSearchBackendDialogOpen = true) }
    fun closeSearchBackendDialog() = _toolBindingState.update { it.copy(isSearchBackendDialogOpen = false) }
    fun clearToolError() = _toolBindingState.update { it.copy(errorMessage = null) }

    fun selectSearchBackend(connectionUid: String?) {
        viewModelScope.launch {
            runCatching {
                if (connectionUid == null) {
                    toolConnectionRepository.removeWebSearchBinding(platformUid)
                } else {
                    toolConnectionRepository.replaceWebSearchBinding(platformUid, connectionUid)
                }
            }
                .onSuccess {
                    _toolBindingState.update {
                        it.copy(selectedSearchConnectionUid = connectionUid, isSearchBackendDialogOpen = false, errorMessage = null)
                    }
                }
                .onFailure(::showToolError)
        }
    }

    fun toggleReadUrl(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { toolConnectionRepository.setReadUrlBinding(platformUid, enabled) }
                .onSuccess {
                    _toolBindingState.update { it.copy(readUrlEnabled = enabled, errorMessage = null) }
                }
                .onFailure(::showToolError)
        }
    }

    private fun showToolError(error: Throwable) {
        _toolBindingState.update { it.copy(errorMessage = error.message ?: "Tool binding update failed.") }
    }

    data class DialogState(
        val isPlatformNameDialogOpen: Boolean = false,
        val isApiUrlDialogOpen: Boolean = false,
        val isApiTokenDialogOpen: Boolean = false,
        val isApiModelDialogOpen: Boolean = false,
        val isTemperatureDialogOpen: Boolean = false,
        val isTopPDialogOpen: Boolean = false,
        val isSystemPromptDialogOpen: Boolean = false,
        val isTimeoutDialogOpen: Boolean = false,
        val isGeminiSafetyDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false
    )

    data class ToolBindingState(
        val searchConnections: List<ToolConnection> = emptyList(),
        val selectedSearchConnectionUid: String? = null,
        val readUrlEnabled: Boolean = false,
        val isSearchBackendDialogOpen: Boolean = false,
        val errorMessage: String? = null
    )

    companion object {
        private const val WEB_SEARCH_TOOL = "web_search"
        private val WEB_SEARCH_TYPES = setOf(ToolConnectionType.FIRECRAWL, ToolConnectionType.PERPLEXITY, ToolConnectionType.EXA)
    }
}
