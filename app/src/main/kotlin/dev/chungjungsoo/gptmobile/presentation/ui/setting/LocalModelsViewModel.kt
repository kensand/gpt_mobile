package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadActions
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LocalModelsViewModel @Inject constructor(
    private val modelCatalogRepository: ModelCatalogRepository,
    private val localModelRepository: LocalModelRepository,
    gatedDownloadCoordinator: GatedDownloadCoordinator,
    huggingFaceTokenStore: HuggingFaceTokenStore,
    downloadGuards: LocalDownloadGuards,
    huggingFaceAuthClient: HuggingFaceAuthClient
) : ViewModel() {

    private val downloadActions = LocalModelDownloadActions(
        localModelRepository = localModelRepository,
        gatedDownloadCoordinator = gatedDownloadCoordinator,
        huggingFaceTokenStore = huggingFaceTokenStore,
        downloadGuards = downloadGuards,
        huggingFaceAuthClient = huggingFaceAuthClient,
        scope = viewModelScope
    )

    private val listState = MutableStateFlow(LocalModelsListState())
    private val deleteDialog = MutableStateFlow<LocalModelsDialog>(LocalModelsDialog.Hidden)

    val uiState: StateFlow<LocalModelsUiState> = combine(
        listState,
        downloadActions.uiState,
        deleteDialog
    ) { list, download, delete ->
        LocalModelsUiState(
            items = list.items,
            isLoading = list.isLoading,
            totalStorageBytes = list.totalStorageBytes,
            checkingAccessEntryId = download.checkingAccessEntryId,
            dialog = if (delete !is LocalModelsDialog.Hidden) delete else download.dialog
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalModelsUiState())

    init {
        viewModelScope.launch {
            runCatching { localModelRepository.reconcile() }
            val catalogEntries = runCatching { modelCatalogRepository.getVisibleEntries() }
                .getOrDefault(emptyList())
            combine(
                localModelRepository.observeAll(),
                localModelRepository.observeWorkInfos()
            ) { localModels, workInfos ->
                val items = catalogLocalModelItems(catalogEntries, localModels, workInfos)
                val storage = localModels
                    .filter { it.status == LocalModelStatus.DOWNLOADED }
                    .sumOf { it.totalBytes }
                items to storage
            }.collect { (items, storage) ->
                listState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        totalStorageBytes = storage
                    )
                }
            }
        }
    }

    fun onDownloadClick(entry: CatalogEntry) {
        downloadActions.requestDownload(entry, currentStatus(entry.id))
    }

    fun confirmRamWarning() {
        downloadActions.confirmRamWarning()
    }

    fun confirmMeteredDownload() {
        downloadActions.confirmMeteredDownload()
    }

    fun onDeleteClick(entry: CatalogEntry) {
        deleteDialog.value = LocalModelsDialog.DeleteConfirm(entry)
    }

    fun confirmDelete() {
        val entry = (deleteDialog.value as? LocalModelsDialog.DeleteConfirm)?.entry ?: return
        deleteDialog.value = LocalModelsDialog.Hidden
        viewModelScope.launch { localModelRepository.deleteModel(entry.id) }
    }

    fun cancelDownload(entry: CatalogEntry) {
        viewModelScope.launch { localModelRepository.cancelDownload(entry.id) }
    }

    fun dismissDialog() {
        if (deleteDialog.value !is LocalModelsDialog.Hidden) {
            deleteDialog.value = LocalModelsDialog.Hidden
        } else {
            downloadActions.dismissDialog()
        }
    }

    fun startHuggingFaceSignIn(): Intent? = downloadActions.startHuggingFaceSignIn()

    fun onAuthActivityResult(data: Intent?) {
        downloadActions.onAuthActivityResult(data)
    }

    fun onLicenseTabClosed() {
        downloadActions.onLicenseTabClosed()
    }

    fun retryAfterLicense() {
        downloadActions.retryAfterLicense()
    }

    override fun onCleared() {
        downloadActions.release()
        super.onCleared()
    }

    private fun currentStatus(catalogEntryId: String): LocalModelItemStatus? = listState.value.items.firstOrNull { it.entry.id == catalogEntryId }?.status
}

private data class LocalModelsListState(
    val items: List<LocalModelListItem> = emptyList(),
    val isLoading: Boolean = true,
    val totalStorageBytes: Long = 0L
)
