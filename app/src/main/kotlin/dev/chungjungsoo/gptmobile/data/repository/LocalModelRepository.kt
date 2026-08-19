package dev.chungjungsoo.gptmobile.data.repository

import androidx.work.WorkInfo
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import kotlinx.coroutines.flow.Flow

interface LocalModelRepository {
    fun observeAll(): Flow<List<LocalModel>>
    fun observeWorkInfos(): Flow<List<WorkInfo>>
    suspend fun startDownload(entry: CatalogEntry)
    suspend fun cancelDownload(catalogEntryId: String)
    suspend fun deleteModel(catalogEntryId: String)
    suspend fun totalStorageUsed(): Long
    suspend fun reconcile()
}
