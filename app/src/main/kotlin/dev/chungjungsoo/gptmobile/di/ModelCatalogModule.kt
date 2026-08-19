package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.BuildConfig
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepositoryImpl
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelCatalogModule {

    @Provides
    @Singleton
    fun provideModelCatalogRepository(
        @ApplicationContext context: Context,
        networkClient: NetworkClient
    ): ModelCatalogRepository {
        val cacheFile = File(context.filesDir, ModelCatalogRepositoryImpl.CATALOG_FILE_NAME)
        return ModelCatalogRepositoryImpl(
            fetchRemoteJson = {
                val response = networkClient().get(ModelCatalogRepositoryImpl.HOSTED_CATALOG_URL) {
                    timeout {
                        requestTimeoutMillis = CATALOG_REQUEST_TIMEOUT_MS
                    }
                }
                check(response.status.isSuccess()) { "Model Catalog fetch failed: ${response.status}" }
                response.bodyAsText()
            },
            readCacheJson = {
                cacheFile.takeIf { it.exists() }?.readText()
            },
            writeCacheJson = { json ->
                cacheFile.writeText(json)
            },
            readBundledJson = {
                context.assets.open(ModelCatalogRepositoryImpl.CATALOG_FILE_NAME).bufferedReader().use { it.readText() }
            },
            appVersionName = BuildConfig.VERSION_NAME
        )
    }

    private const val CATALOG_REQUEST_TIMEOUT_MS = 15_000L
}
