package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.worker.LocalModelDownloadWorker
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LocalModelsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val localModelRepository: LocalModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalModelsUiState())
    val uiState: StateFlow<LocalModelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { localModelRepository.reconcile() }
            val catalogEntries = runCatching { modelCatalogRepository.getVisibleEntries() }
                .getOrDefault(emptyList())
            combine(
                localModelRepository.observeAll(),
                localModelRepository.observeWorkInfos()
            ) { localModels, workInfos ->
                val workById = workInfos.mapNotNull { info ->
                    val id = info.tags.firstNotNullOfOrNull(LocalModelDownloadWorker::catalogEntryIdFromTag)
                    id?.let { it to info }
                }.toMap()
                val modelsById = localModels.associateBy { it.catalogEntryId }
                val items = catalogEntries.map { entry ->
                    toListItem(entry, modelsById[entry.id], workById[entry.id])
                }
                val storage = localModels
                    .filter { it.status == LocalModelStatus.DOWNLOADED }
                    .sumOf { it.totalBytes }
                items to storage
            }.collect { (items, storage) ->
                _uiState.update {
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
        when {
            belowRamRequirement(entry) -> _uiState.update { it.copy(dialog = LocalModelsDialog.RamWarning(entry)) }
            isMeteredConnection() -> _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entry)) }
            else -> startDownload(entry)
        }
    }

    fun confirmRamWarning() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.RamWarning)?.entry ?: return
        dismissDialog()
        if (isMeteredConnection()) {
            _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entry)) }
        } else {
            startDownload(entry)
        }
    }

    fun confirmMeteredDownload() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.MeteredConfirm)?.entry ?: return
        dismissDialog()
        startDownload(entry)
    }

    fun onDeleteClick(entry: CatalogEntry) {
        _uiState.update { it.copy(dialog = LocalModelsDialog.DeleteConfirm(entry)) }
    }

    fun confirmDelete() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.DeleteConfirm)?.entry ?: return
        dismissDialog()
        viewModelScope.launch { localModelRepository.deleteModel(entry.id) }
    }

    fun cancelDownload(entry: CatalogEntry) {
        viewModelScope.launch { localModelRepository.cancelDownload(entry.id) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = LocalModelsDialog.Hidden) }
    }

    private fun startDownload(entry: CatalogEntry) {
        viewModelScope.launch { localModelRepository.startDownload(entry) }
    }

    private fun belowRamRequirement(entry: CatalogEntry): Boolean {
        if (entry.minRamGb <= 0) return false
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem < entry.minRamGb.toLong() * BYTES_PER_GB
    }

    private fun isMeteredConnection(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        return connectivityManager.isActiveNetworkMetered
    }

    private fun toListItem(
        entry: CatalogEntry,
        record: LocalModel?,
        workInfo: WorkInfo?
    ): LocalModelListItem {
        val receivedBytes = workInfo?.progress?.getLong(LocalModelDownloadWorker.KEY_RECEIVED_BYTES, 0L) ?: 0L
        val bytesPerSecond = workInfo?.progress?.getLong(LocalModelDownloadWorker.KEY_DOWNLOAD_RATE, 0L) ?: 0L
        val remainingMs = workInfo?.progress?.getLong(LocalModelDownloadWorker.KEY_REMAINING_MS, 0L) ?: 0L
        val errorMessage = workInfo?.outputData?.getString(LocalModelDownloadWorker.KEY_ERROR_MESSAGE)
        val workState = workInfo?.state
        val isWorkActive = workState == WorkInfo.State.RUNNING ||
            workState == WorkInfo.State.ENQUEUED ||
            workState == WorkInfo.State.BLOCKED
        return when {
            record?.status == LocalModelStatus.DOWNLOADED -> LocalModelListItem(
                entry = entry,
                status = LocalModelItemStatus.DOWNLOADED,
                diskBytes = record.totalBytes
            )

            record?.status == LocalModelStatus.DOWNLOADING || isWorkActive -> LocalModelListItem(
                entry = entry,
                status = LocalModelItemStatus.DOWNLOADING,
                receivedBytes = receivedBytes,
                bytesPerSecond = bytesPerSecond,
                remainingMs = remainingMs,
                diskBytes = entry.sizeInBytes
            )

            record?.status == LocalModelStatus.FAILED || workState == WorkInfo.State.FAILED -> LocalModelListItem(
                entry = entry,
                status = LocalModelItemStatus.FAILED,
                errorMessage = errorMessage
            )

            else -> LocalModelListItem(
                entry = entry,
                status = LocalModelItemStatus.NOT_DOWNLOADED
            )
        }
    }

    private companion object {
        const val BYTES_PER_GB = 1024L * 1024L * 1024L
    }
}

data class LocalModelsUiState(
    val items: List<LocalModelListItem> = emptyList(),
    val isLoading: Boolean = true,
    val totalStorageBytes: Long = 0L,
    val dialog: LocalModelsDialog = LocalModelsDialog.Hidden
)

data class LocalModelListItem(
    val entry: CatalogEntry,
    val status: LocalModelItemStatus,
    val receivedBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val remainingMs: Long = 0L,
    val diskBytes: Long = 0L,
    val errorMessage: String? = null
)

enum class LocalModelItemStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

sealed class LocalModelsDialog {
    data object Hidden : LocalModelsDialog()
    data class RamWarning(val entry: CatalogEntry) : LocalModelsDialog()
    data class MeteredConfirm(val entry: CatalogEntry) : LocalModelsDialog()
    data class DeleteConfirm(val entry: CatalogEntry) : LocalModelsDialog()
}
