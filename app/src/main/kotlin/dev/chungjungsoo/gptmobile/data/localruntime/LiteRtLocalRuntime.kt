package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class LiteRtLocalRuntime(
    private val context: Context
) : LocalRuntime {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        withContext(Dispatchers.IO) {
            val engineConfig = EngineConfig(
                modelPath = spec.modelPath,
                backend = backendFor(spec.accelerator),
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = spec.maxTokens
            )
            val nextEngine = Engine(engineConfig)
            nextEngine.initialize()
            engine = nextEngine
        }
    }

    override suspend fun createConversation(config: LocalConversationConfig) {
        withContext(Dispatchers.IO) {
            val currentEngine = engine ?: error("LiteRT-LM engine is not loaded")
            conversation?.close()
            conversation = currentEngine.createConversation(
                ConversationConfig(
                    systemInstruction = config.systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                    initialMessages = config.initialMessages.map { message ->
                        when (message.role) {
                            LocalHistoryRole.USER -> Message.user(message.text)
                            LocalHistoryRole.MODEL -> Message.model(Contents.of(message.text))
                        }
                    },
                    samplerConfig = SamplerConfig(
                        topK = config.sampler.topK,
                        topP = config.sampler.topP.toDouble(),
                        temperature = config.sampler.temperature.toDouble()
                    )
                )
            )
        }
    }

    override fun sendMessage(text: String): Flow<LocalRuntimeEvent> = callbackFlow {
        val activeConversation = conversation
        if (activeConversation == null) {
            trySend(LocalRuntimeEvent.Error("LiteRT-LM conversation is not ready"))
            close()
            return@callbackFlow
        }

        activeConversation.sendMessageAsync(
            Contents.of(text),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    message.channels[THOUGHT_CHANNEL]?.takeIf { it.isNotEmpty() }?.let { thought ->
                        trySend(LocalRuntimeEvent.ThinkingDelta(thought))
                    }
                    val visibleText = message.visibleText()
                    if (visibleText.isNotEmpty()) {
                        trySend(LocalRuntimeEvent.TextDelta(visibleText))
                    }
                }

                override fun onDone() {
                    trySend(LocalRuntimeEvent.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException || throwable is kotlinx.coroutines.CancellationException) {
                        trySend(LocalRuntimeEvent.Done)
                    } else {
                        trySend(
                            LocalRuntimeEvent.Error(
                                message = throwable.message ?: "Local inference failed",
                                cause = throwable
                            )
                        )
                    }
                    close()
                }
            }
        )

        awaitClose {
            runCatching { activeConversation.cancelProcess() }
        }
    }

    override fun cancelActive() {
        runCatching { conversation?.cancelProcess() }
    }

    override suspend fun closeConversation() {
        withContext(Dispatchers.IO) {
            runCatching { conversation?.close() }
            conversation = null
        }
    }

    override suspend fun unloadEngine() {
        withContext(Dispatchers.IO) {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { engine?.close() }
            engine = null
        }
    }

    private fun backendFor(accelerator: String): Backend = when (LocalAccelerators.normalize(accelerator)) {
        LocalAccelerators.GPU -> Backend.GPU()
        LocalAccelerators.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
    }

    private fun Message.visibleText(): String {
        val fromContents = contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
        return fromContents.ifEmpty { toString() }
    }

    private companion object {
        const val THOUGHT_CHANNEL = "thought"
    }
}
