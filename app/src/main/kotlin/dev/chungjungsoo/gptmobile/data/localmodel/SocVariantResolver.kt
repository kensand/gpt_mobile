package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant

data class ResolvedModelDownload(
    val fileName: String,
    val downloadUrl: String,
    val commitHash: String,
    val sizeInBytes: Long
)

object SocVariantResolver {
    fun resolve(entry: CatalogEntry, deviceSocModel: String): ResolvedModelDownload {
        val default = ResolvedModelDownload(
            fileName = LocalModelDownloadPaths.fileNameFromUrl(entry.downloadUrl),
            downloadUrl = entry.downloadUrl,
            commitHash = LocalModelDownloadPaths.commitHashFromUrl(entry.downloadUrl),
            sizeInBytes = entry.sizeInBytes
        )
        val variant = matchingVariant(entry.socToModelFiles, deviceSocModel) ?: return default
        val variantUrl = variant.downloadUrl.ifBlank { default.downloadUrl }
        return ResolvedModelDownload(
            fileName = variant.modelFile.ifBlank {
                LocalModelDownloadPaths.fileNameFromUrl(variantUrl).ifBlank { default.fileName }
            },
            downloadUrl = variantUrl,
            commitHash = variant.commitHash.ifBlank {
                LocalModelDownloadPaths.commitHashFromUrl(variantUrl).ifBlank { default.commitHash }
            },
            sizeInBytes = variant.sizeInBytes.takeIf { it > 0L } ?: default.sizeInBytes
        )
    }

    fun hasMatchingVariant(socToModelFiles: Map<String, *>, deviceSocModel: String): Boolean = matchingVariantKey(socToModelFiles, deviceSocModel) != null

    private fun matchingVariant(
        socToModelFiles: Map<String, SocVariant>,
        deviceSocModel: String
    ): SocVariant? {
        val key = matchingVariantKey(socToModelFiles, deviceSocModel) ?: return null
        return socToModelFiles[key]
    }

    private fun matchingVariantKey(socToModelFiles: Map<String, *>, deviceSocModel: String): String? {
        if (deviceSocModel.isBlank() || socToModelFiles.isEmpty()) return null
        return socToModelFiles.keys.firstOrNull { it.equals(deviceSocModel, ignoreCase = true) }
    }
}
