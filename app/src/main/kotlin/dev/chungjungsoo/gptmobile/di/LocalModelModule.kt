package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModelModule {

    @Provides
    @Singleton
    fun provideLocalModelRepository(
        @ApplicationContext context: Context,
        localModelDao: LocalModelDao
    ): LocalModelRepository = LocalModelRepositoryImpl(
        context = context,
        localModelDao = localModelDao
    )
}
