package dev.chungjungsoo.gptmobile.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogRepositoryTest {

    @Test
    fun `successful fetch returns remote entries and writes cache`() = runBlocking {
        var cached: String? = null
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { remoteCatalog("remote-model", "0.1.0") },
            readCacheJson = { cached },
            writeCacheJson = { cached = it },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("remote-model"), entries.map { it.id })
        assertEquals(remoteCatalog("remote-model", "0.1.0"), cached)
    }

    @Test
    fun `failed fetch uses last cached catalog`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { remoteCatalog("cached-model", "0.1.0") },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("cached-model"), entries.map { it.id })
    }

    @Test
    fun `failed fetch with no cache uses bundled snapshot`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { null },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("bundled-model"), entries.map { it.id })
    }

    @Test
    fun `invalid remote json falls back to cache instead of writing it`() = runBlocking {
        var cached = remoteCatalog("cached-model", "0.1.0")
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { "{ not-json" },
            readCacheJson = { cached },
            writeCacheJson = { cached = it },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("cached-model"), entries.map { it.id })
        assertEquals(remoteCatalog("cached-model", "0.1.0"), cached)
    }

    @Test
    fun `invalid cache falls through to bundled snapshot`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { error("offline") },
            readCacheJson = { "{ not-json" },
            writeCacheJson = { error("cache should not be written") },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertEquals(listOf("bundled-model"), entries.map { it.id })
    }

    @Test
    fun `unknown remote schema is cached but hidden`() = runBlocking {
        var cached: String? = null
        val unknownSchema = """
            {
              "schemaVersion": 2,
              "models": [
                {
                  "id": "future-model",
                  "displayName": "Future",
                  "minAppVersion": "0.1.0"
                }
              ]
            }
        """.trimIndent()
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { unknownSchema },
            readCacheJson = { cached },
            writeCacheJson = { cached = it },
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        val entries = repository.getVisibleEntries()

        assertTrue(entries.isEmpty())
        assertEquals(unknownSchema, cached)
    }

    @Test
    fun `entries above the running app version are hidden`() = runBlocking {
        val repository = ModelCatalogRepositoryImpl(
            fetchRemoteJson = { remoteCatalog("future-model", "9.0.0") },
            readCacheJson = { null },
            writeCacheJson = {},
            readBundledJson = { remoteCatalog("bundled-model", "0.1.0") },
            appVersionName = "0.8.0"
        )

        assertTrue(repository.getVisibleEntries().isEmpty())
    }

    private fun remoteCatalog(id: String, minAppVersion: String): String = """
        {
          "schemaVersion": 1,
          "models": [
            {
              "id": "$id",
              "displayName": "$id",
              "minAppVersion": "$minAppVersion"
            }
          ]
        }
    """.trimIndent()
}
