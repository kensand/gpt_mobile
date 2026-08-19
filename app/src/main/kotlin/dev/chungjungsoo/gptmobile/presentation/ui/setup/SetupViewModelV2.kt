package dev.chungjungsoo.gptmobile.presentation.ui.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localruntime.localSamplingDefaults
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object Saving : SaveStatus()
    data object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

data class DownloadedLocalModelOption(
    val catalogEntryId: String,
    val displayName: String
)

@HiltViewModel
class SetupViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val localModelRepository: LocalModelRepository,
    private val modelCatalogRepository: ModelCatalogRepository
) : ViewModel() {

    private val _platforms = MutableStateFlow<List<PlatformV2>>(emptyList())
    val platforms: StateFlow<List<PlatformV2>> = _platforms.asStateFlow()

    // Wizard state for adding a new platform
    private val _wizardStep = MutableStateFlow(0)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    private val _selectedClientType = MutableStateFlow<ClientType?>(null)
    val selectedClientType: StateFlow<ClientType?> = _selectedClientType.asStateFlow()

    private val _platformName = MutableStateFlow("")
    val platformName: StateFlow<String> = _platformName.asStateFlow()

    private val _apiUrl = MutableStateFlow("")
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val catalogEntries = MutableStateFlow<List<CatalogEntry>>(emptyList())

    val downloadedLocalModels: StateFlow<List<DownloadedLocalModelOption>> = combine(
        localModelRepository.observeAll(),
        catalogEntries
    ) { models, catalog ->
        val names = catalog.associate { it.id to it.displayName }
        models.filter { it.status == LocalModelStatus.DOWNLOADED }.map { model ->
            DownloadedLocalModelOption(
                catalogEntryId = model.catalogEntryId,
                displayName = names[model.catalogEntryId]?.takeIf { it.isNotBlank() } ?: model.catalogEntryId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadPlatforms()
        viewModelScope.launch {
            catalogEntries.value = modelCatalogRepository.getVisibleEntries()
        }
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            val existingPlatforms = settingRepository.fetchPlatformV2s()
            _platforms.value = existingPlatforms
        }
    }

    fun selectClientType(clientType: ClientType) {
        _selectedClientType.value = clientType
        _platformName.value = getDefaultPlatformName(clientType)
        _apiUrl.value = getDefaultApiUrl(clientType)
        _apiKey.value = ""
        _model.value = ModelConstants.defaultModel(clientType)
        _wizardStep.value = 0
    }

    fun updatePlatformName(name: String) {
        _platformName.value = name
    }

    fun updateApiUrl(url: String) {
        _apiUrl.value = url
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateModel(modelName: String) {
        _model.value = modelName
    }

    fun nextWizardStep() {
        if (isLiteRtLm() && _wizardStep.value == WIZARD_STEP_BASICS) {
            _wizardStep.value = WIZARD_STEP_MODEL
        } else {
            _wizardStep.update { it + 1 }
        }
    }

    fun previousWizardStep() {
        if (isLiteRtLm() && _wizardStep.value == WIZARD_STEP_MODEL) {
            _wizardStep.value = WIZARD_STEP_BASICS
        } else {
            _wizardStep.update { maxOf(0, it - 1) }
        }
    }

    fun resetWizard() {
        _wizardStep.value = 0
        _selectedClientType.value = null
        _platformName.value = ""
        _apiUrl.value = ""
        _apiKey.value = ""
        _model.value = ""
    }

    fun savePlatform() {
        val clientType = _selectedClientType.value ?: return

        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saving
            try {
                val defaults = if (clientType == ClientType.LITERT_LM) {
                    catalogDefaultsFor(_model.value.trim())
                } else {
                    null
                }
                val platform = PlatformV2(
                    name = _platformName.value.trim(),
                    compatibleType = clientType,
                    enabled = true,
                    apiUrl = if (clientType == ClientType.LITERT_LM) "" else _apiUrl.value.trim(),
                    token = _apiKey.value.trim().takeIf { it.isNotEmpty() && clientType != ClientType.LITERT_LM },
                    model = _model.value.trim(),
                    temperature = defaults?.temperature ?: 1.0f,
                    topP = defaults?.topP ?: 1.0f,
                    topK = defaults?.topK,
                    maxTokens = defaults?.maxTokens,
                    accelerator = defaults?.accelerator,
                    systemPrompt = ModelConstants.DEFAULT_PROMPT,
                    stream = true,
                    reasoning = false,
                    timeout = 30
                )
                settingRepository.addPlatformV2(platform)
                loadPlatforms()
                _saveStatus.value = SaveStatus.Success
                resetWizard()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save platform", e)
                val errorMessage = when (e) {
                    is android.database.sqlite.SQLiteConstraintException -> "A platform with this name already exists."
                    is android.database.sqlite.SQLiteException -> "Database error: ${e.message}"
                    else -> e.message ?: "Unknown error occurred while saving platform."
                }
                _saveStatus.value = SaveStatus.Error(errorMessage)
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
            loadPlatforms()
        }
    }

    fun canProceedFromStep(step: Int): Boolean = when (step) {
        WIZARD_STEP_BASICS -> {
            val hasName = _platformName.value.isNotBlank()
            if (isLiteRtLm()) hasName else hasName && _apiUrl.value.isNotBlank()
        }

        WIZARD_STEP_API_KEY -> true

        WIZARD_STEP_MODEL -> _model.value.isNotBlank()

        else -> false
    }

    fun isSetupComplete(): Boolean = _platforms.value.isNotEmpty()

    fun isLiteRtLm(): Boolean = _selectedClientType.value == ClientType.LITERT_LM

    fun wizardTotalSteps(): Int = if (isLiteRtLm()) WIZARD_LOCAL_STEPS else WIZARD_TOTAL_STEPS

    fun wizardDisplayStep(): Int = if (isLiteRtLm() && _wizardStep.value == WIZARD_STEP_MODEL) {
        1
    } else {
        _wizardStep.value
    }

    private fun catalogDefaultsFor(modelId: String) = catalogEntries.value
        .firstOrNull { it.id == modelId }
        ?.let(::localSamplingDefaults)

    private fun getDefaultPlatformName(clientType: ClientType): String = ModelConstants.defaultPlatformName(clientType)

    private fun getDefaultApiUrl(clientType: ClientType): String = ModelConstants.defaultApiUrl(clientType)

    companion object {
        private const val TAG = "SetupViewModelV2"
        const val WIZARD_STEP_BASICS = 0
        const val WIZARD_STEP_API_KEY = 1
        const val WIZARD_STEP_MODEL = 2
        const val WIZARD_TOTAL_STEPS = 3
        const val WIZARD_LOCAL_STEPS = 2
    }
}
