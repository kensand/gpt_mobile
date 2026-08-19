package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    private var scriptIndex = 0

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        loadEngineCalls += spec
    }

    override suspend fun createConversation(config: LocalConversationConfig) {
        createConversationCalls += config
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
    }

    override suspend fun unloadEngine() {
        unloadEngineCalls += 1
    }
}
