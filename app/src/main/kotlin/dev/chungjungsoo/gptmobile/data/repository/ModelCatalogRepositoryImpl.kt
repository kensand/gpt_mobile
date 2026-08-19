package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalog
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelCatalogRepositoryImpl(
    private val fetchRemoteJson: suspend () -> String,
    private val readCacheJson: () -> String?,
    private val writeCacheJson: (String) -> Unit,
    private val readBundledJson: () -> String,
    private val appVersionName: String
) : ModelCatalogRepository {
    override suspend fun getVisibleEntries(): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val remoteCatalog = fetchParsableCatalog(
            source = { fetchRemoteJson() },
            onParsed = writeCacheJson
        )
        if (remoteCatalog != null) {
            return@withContext ModelCatalogParser.visibleEntries(remoteCatalog, appVersionName)
        }

        val cachedCatalog = fetchParsableCatalog(source = { readCacheJson() })
        if (cachedCatalog != null) {
            return@withContext ModelCatalogParser.visibleEntries(cachedCatalog, appVersionName)
        }

        val bundledCatalog = fetchParsableCatalog(source = { readBundledJson() })
        if (bundledCatalog != null) {
            return@withContext ModelCatalogParser.visibleEntries(bundledCatalog, appVersionName)
        }

        emptyList()
    }

    private suspend fun fetchParsableCatalog(
        source: suspend () -> String?,
        onParsed: (String) -> Unit = {}
    ): ModelCatalog? = runCatching {
        val rawJson = source() ?: return null
        val catalog = ModelCatalogParser.parse(rawJson)
        onParsed(rawJson)
        catalog
    }.getOrNull()

    companion object {
        const val HOSTED_CATALOG_URL = "https://raw.githubusercontent.com/Taewan-P/gpt_mobile/main/model_catalog.json"
        const val CATALOG_FILE_NAME = "model_catalog.json"
    }
}
