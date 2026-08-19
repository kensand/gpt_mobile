package dev.chungjungsoo.gptmobile.data.repository

import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeLocalModelRepository(
    initialModels: List<LocalModel> = emptyList(),
    private val downloadedPaths: Map<String, String> = emptyMap()
) : LocalModelRepository {
    private val models = MutableStateFlow(initialModels)

    override fun observeAll(): Flow<List<LocalModel>> = models.asStateFlow()

    override fun observeWorkInfos(): Flow<List<WorkInfo>> = MutableStateFlow(emptyList())

    override suspend fun getById(catalogEntryId: String): LocalModel? = models.value.firstOrNull { it.catalogEntryId == catalogEntryId }

    override suspend fun resolveDownloadedPath(catalogEntryId: String): String? = downloadedPaths[catalogEntryId]

    override suspend fun startDownload(entry: CatalogEntry) = Unit

    override suspend fun cancelDownload(catalogEntryId: String) = Unit

    override suspend fun deleteModel(catalogEntryId: String) = Unit

    override suspend fun totalStorageUsed(): Long = 0L

    override suspend fun reconcile() = Unit
}
