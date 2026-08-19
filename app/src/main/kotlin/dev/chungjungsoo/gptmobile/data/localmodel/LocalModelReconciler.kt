package dev.chungjungsoo.gptmobile.data.localmodel

object LocalModelStatus {
    const val DOWNLOADING = "DOWNLOADING"
    const val DOWNLOADED = "DOWNLOADED"
    const val FAILED = "FAILED"
}

data class LocalModelRecord(
    val catalogEntryId: String,
    val commitHash: String,
    val fileName: String,
    val relativeDirectory: String,
    val status: String
)

sealed class ReconcileAction {
    data class DeleteRow(val catalogEntryId: String) : ReconcileAction()
    data class MarkFailed(val catalogEntryId: String) : ReconcileAction()
    data class DeleteFile(val relativePath: String) : ReconcileAction()
}

object LocalModelReconciler {
    fun reconcile(
        rows: List<LocalModelRecord>,
        diskFiles: Set<String>,
        activeDownloadIds: Set<String>
    ): List<ReconcileAction> {
        val actions = mutableListOf<ReconcileAction>()
        val handledPartials = mutableSetOf<String>()

        rows.forEach { row ->
            val finalPath = LocalModelDownloadPaths.relativeFilePath(row.catalogEntryId, row.commitHash, row.fileName)
            val partialPath = LocalModelDownloadPaths.relativePartialFilePath(row.catalogEntryId, row.commitHash, row.fileName)
            val isActive = row.catalogEntryId in activeDownloadIds
            when (row.status) {
                LocalModelStatus.DOWNLOADED -> {
                    if (finalPath !in diskFiles) {
                        actions += ReconcileAction.DeleteRow(row.catalogEntryId)
                    }
                }

                LocalModelStatus.DOWNLOADING -> {
                    if (!isActive) {
                        actions += ReconcileAction.MarkFailed(row.catalogEntryId)
                        if (partialPath in diskFiles) {
                            actions += ReconcileAction.DeleteFile(partialPath)
                            handledPartials += partialPath
                        }
                    }
                }

                LocalModelStatus.FAILED -> {
                    if (!isActive && partialPath in diskFiles) {
                        actions += ReconcileAction.DeleteFile(partialPath)
                        handledPartials += partialPath
                    }
                }
            }
        }

        diskFiles.forEach { path ->
            if (!LocalModelDownloadPaths.isPartialFile(path) || path in handledPartials) {
                return@forEach
            }
            val catalogEntryId = LocalModelDownloadPaths.catalogEntryIdFromRelativePath(path) ?: return@forEach
            if (catalogEntryId !in activeDownloadIds) {
                actions += ReconcileAction.DeleteFile(path)
            }
        }

        return actions
    }
}
