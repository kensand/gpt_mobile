package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class LocalEngineSpec(
    val modelPath: String,
    val accelerator: String,
    val maxTokens: Int,
    val enableVision: Boolean = false
)

data class LocalSamplerConfig(
    val topK: Int,
    val topP: Float,
    val temperature: Float
)

enum class LocalHistoryRole {
    USER,
    MODEL
}

data class LocalHistoryMessage(
    val role: LocalHistoryRole,
    val text: String
)

data class LocalConversationConfig(
    val sampler: LocalSamplerConfig,
    val systemPrompt: String?,
    val initialMessages: List<LocalHistoryMessage>
)

sealed interface LocalRuntimeEvent {
    data class TextDelta(val text: String) : LocalRuntimeEvent
    data class ThinkingDelta(val text: String) : LocalRuntimeEvent
    data object Done : LocalRuntimeEvent
    data class Error(val message: String, val cause: Throwable? = null) : LocalRuntimeEvent
}

interface LocalRuntime {
    suspend fun loadEngine(spec: LocalEngineSpec)
    suspend fun createConversation(config: LocalConversationConfig)
    fun sendMessage(text: String): Flow<LocalRuntimeEvent>
    fun cancelActive()
    suspend fun closeConversation()
    suspend fun unloadEngine()

    suspend fun <T> runExclusive(block: suspend LocalRuntime.() -> T): T = block(this)

    fun <T> runExclusiveFlow(block: suspend LocalRuntime.() -> Flow<T>): Flow<T> = flow {
        block(this@LocalRuntime).collect { emit(it) }
    }
}
