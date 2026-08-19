package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry

class FakeModelCatalogRepository(
    private val entries: List<CatalogEntry> = emptyList()
) : ModelCatalogRepository {
    override suspend fun getVisibleEntries(): List<CatalogEntry> = entries
}
