package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LocalModelsViewModel @Inject constructor(
    private val modelCatalogRepository: ModelCatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalModelsUiState())
    val uiState: StateFlow<LocalModelsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = runCatching { modelCatalogRepository.getVisibleEntries() }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(entries = entries, isLoading = false) }
        }
    }
}

data class LocalModelsUiState(
    val entries: List<CatalogEntry> = emptyList(),
    val isLoading: Boolean = true
)
