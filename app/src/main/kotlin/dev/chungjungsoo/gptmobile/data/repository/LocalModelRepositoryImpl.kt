package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadPaths
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelReconciler
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.ReconcileAction
import dev.chungjungsoo.gptmobile.data.worker.LocalModelDownloadWorker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalModelRepositoryImpl(
    private val context: Context,
    private val localModelDao: LocalModelDao
) : LocalModelRepository {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun observeAll(): Flow<List<LocalModel>> = localModelDao.observeAll()

    override fun observeWorkInfos(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(LocalModelDownloadWorker.WORK_TAG)

    override suspend fun startDownload(entry: CatalogEntry) {
        withContext(Dispatchers.IO) {
            val commitHash = LocalModelDownloadPaths.commitHashFromUrl(entry.downloadUrl)
            val fileName = LocalModelDownloadPaths.fileNameFromUrl(entry.downloadUrl)
            val relativeDirectory = LocalModelDownloadPaths.relativeDirectory(entry.id, commitHash)
            val now = System.currentTimeMillis() / 1000
            val existing = localModelDao.getById(entry.id)
            localModelDao.upsert(
                LocalModel(
                    catalogEntryId = entry.id,
                    commitHash = commitHash,
                    fileName = fileName,
                    relativeDirectory = relativeDirectory,
                    totalBytes = entry.sizeInBytes,
                    status = LocalModelStatus.DOWNLOADING,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )

            val inputData = Data.Builder()
                .putString(LocalModelDownloadWorker.KEY_CATALOG_ENTRY_ID, entry.id)
                .putString(LocalModelDownloadWorker.KEY_DISPLAY_NAME, entry.displayName)
                .putString(LocalModelDownloadWorker.KEY_DOWNLOAD_URL, entry.downloadUrl)
                .putString(LocalModelDownloadWorker.KEY_COMMIT_HASH, commitHash)
                .putString(LocalModelDownloadWorker.KEY_FILE_NAME, fileName)
                .putLong(LocalModelDownloadWorker.KEY_TOTAL_BYTES, entry.sizeInBytes)
                .build()

            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(inputData)
                .addTag(LocalModelDownloadWorker.WORK_TAG)
                .addTag(LocalModelDownloadWorker.idTag(entry.id))
                .build()

            workManager.enqueueUniqueWork(
                LocalModelDownloadPaths.uniqueWorkName(entry.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun cancelDownload(catalogEntryId: String) {
        deleteModel(catalogEntryId)
    }

    override suspend fun deleteModel(catalogEntryId: String) {
        withContext(Dispatchers.IO) {
            workManager.cancelUniqueWork(LocalModelDownloadPaths.uniqueWorkName(catalogEntryId))
            val row = localModelDao.getById(catalogEntryId)
            if (row != null) {
                File(storageRoot(), row.relativeDirectory).deleteRecursively()
                localModelDao.deleteById(catalogEntryId)
                File(storageRoot(), LocalModelDownloadPaths.MODELS_DIR)
                    .resolve(catalogEntryId)
                    .takeIf { it.isDirectory && it.list().isNullOrEmpty() }
                    ?.delete()
            }
        }
    }

    override suspend fun totalStorageUsed(): Long = withContext(Dispatchers.IO) {
        localModelDao.getAll()
            .filter { it.status == LocalModelStatus.DOWNLOADED }
            .sumOf { diskBytes(it) }
    }

    override suspend fun reconcile() {
        withContext(Dispatchers.IO) {
            val actions = LocalModelReconciler.reconcile(
                rows = localModelDao.getAll().map { it.toRecord() },
                diskFiles = listDiskFiles(),
                activeDownloadIds = activeDownloadIds()
            )
            val now = System.currentTimeMillis() / 1000
            actions.forEach { action ->
                when (action) {
                    is ReconcileAction.DeleteRow -> localModelDao.deleteById(action.catalogEntryId)

                    is ReconcileAction.MarkFailed -> localModelDao.updateStatus(
                        catalogEntryId = action.catalogEntryId,
                        status = LocalModelStatus.FAILED,
                        updatedAt = now
                    )

                    is ReconcileAction.DeleteFile -> File(storageRoot(), action.relativePath).delete()
                }
            }
        }
    }

    private fun storageRoot(): File = context.getExternalFilesDir(null) ?: context.filesDir

    private fun listDiskFiles(): Set<String> {
        val root = storageRoot()
        val modelsDir = File(root, LocalModelDownloadPaths.MODELS_DIR)
        if (!modelsDir.exists()) return emptySet()
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
    }

    private fun activeDownloadIds(): Set<String> {
        val workInfos = runCatching {
            workManager.getWorkInfosByTag(LocalModelDownloadWorker.WORK_TAG).get()
        }.getOrDefault(emptyList())
        return workInfos
            .filter { !it.state.isFinished }
            .mapNotNull { info ->
                info.tags.firstNotNullOfOrNull(LocalModelDownloadWorker::catalogEntryIdFromTag)
            }
            .toSet()
    }

    private fun diskBytes(model: LocalModel): Long {
        val file = File(storageRoot(), LocalModelDownloadPaths.relativeFilePath(model.catalogEntryId, model.commitHash, model.fileName))
        return if (file.exists()) file.length() else model.totalBytes
    }
}
