package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex

class FakeLocalRuntime : LocalRuntime {
    val loadEngineCalls = mutableListOf<LocalEngineSpec>()
    val createConversationCalls = mutableListOf<LocalConversationConfig>()
    val sendMessageCalls = mutableListOf<String>()
    var cancelActiveCalls = 0
    var closeConversationCalls = 0
    var unloadEngineCalls = 0

    var scriptedEvents: List<List<LocalRuntimeEvent>> = emptyList()
    var emitDelayMillis: Long = 0L
    var pauseAfterFirst: CompletableDeferred<Unit>? = null
    val generationMutex = Mutex()

    private var scriptIndex = 0
    private var conversationOpen = false

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        loadEngineCalls += spec
    }

    override suspend fun createConversation(config: LocalConversationConfig) {
        createConversationCalls += config
        conversationOpen = true
    }

    override fun sendMessage(text: String): Flow<LocalRuntimeEvent> = flow {
        sendMessageCalls += text
        if (emitDelayMillis > 0L) {
            delay(emitDelayMillis)
        }
        val events = scriptedEvents.getOrElse(scriptIndex++) {
            listOf(LocalRuntimeEvent.Done)
        }
        events.forEachIndexed { index, event ->
            emit(event)
            if (index == 0) {
                pauseAfterFirst?.await()
            }
        }
    }

    override fun cancelActive() {
        cancelActiveCalls += 1
    }

    override suspend fun closeConversation() {
        closeConversationCalls += 1
        conversationOpen = false
    }

    override suspend fun unloadEngine() {
        unloadEngineCalls += 1
        conversationOpen = false
    }

    override fun hasOpenConversation(): Boolean = conversationOpen

    override fun <T> runExclusiveFlow(
        onContended: suspend () -> Unit,
        block: suspend LocalRuntime.() -> Flow<T>
    ): Flow<T> = channelFlow {
        var locked = generationMutex.tryLock()
        if (!locked) {
            onContended()
            generationMutex.lock()
            locked = true
        }
        try {
            block(this@FakeLocalRuntime).collect { send(it) }
        } finally {
            if (locked) generationMutex.unlock()
        }
    }
}
