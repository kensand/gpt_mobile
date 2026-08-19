package dev.chungjungsoo.gptmobile.data.localruntime

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalEngineHolder(
    private val delegate: LocalRuntime
) : LocalRuntime {
    private val mutex = Mutex()
    private var loadedSpec: LocalEngineSpec? = null

    override suspend fun loadEngine(spec: LocalEngineSpec) = withGenerationLock {
        if (loadedSpec == spec) return@withGenerationLock
        if (loadedSpec != null) {
            delegate.closeConversation()
            delegate.unloadEngine()
        }
        delegate.loadEngine(spec)
        loadedSpec = spec
    }

    override suspend fun createConversation(config: LocalConversationConfig) = withGenerationLock {
        delegate.createConversation(config)
    }

    override fun sendMessage(text: String): Flow<LocalRuntimeEvent> = channelFlow {
        withGenerationLock {
            delegate.sendMessage(text).collect { send(it) }
        }
    }

    override fun cancelActive() {
        delegate.cancelActive()
    }

    override suspend fun closeConversation() = withGenerationLock {
        delegate.closeConversation()
    }

    override suspend fun unloadEngine() = withGenerationLock {
        delegate.closeConversation()
        delegate.unloadEngine()
        loadedSpec = null
    }

    override suspend fun <T> runExclusive(block: suspend LocalRuntime.() -> T): T = withGenerationLock {
        block(this)
    }

    override fun <T> runExclusiveFlow(block: suspend LocalRuntime.() -> Flow<T>): Flow<T> = channelFlow {
        withGenerationLock {
            block(this@LocalEngineHolder).collect { send(it) }
        }
    }

    private suspend fun <T> withGenerationLock(block: suspend () -> T): T {
        if (coroutineContext[GenerationLock] != null) {
            return block()
        }
        return mutex.withLock {
            withContext(GenerationLock()) { block() }
        }
    }
}

private class GenerationLock : AbstractCoroutineContextElement(GenerationLock) {
    companion object Key : CoroutineContext.Key<GenerationLock>
}
