package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.LocalConversationConfig
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineSpec
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryMessage
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.localruntime.LocalSamplerConfig
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LiteRtLmAdapter(
    private val localRuntime: LocalRuntime,
    private val localModelRepository: LocalModelRepository,
    private val ignoredAttachmentsNotice: String,
    private val modelNotDownloadedError: String
) {
    suspend fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession {
        return object : AgentProviderSession {
            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = flow {
                val hasAttachments = turns.any { turn ->
                    turn.userMessage.attachments.isNotEmpty() ||
                        turn.assistantMessage?.attachments?.isNotEmpty() == true
                }
                if (hasAttachments) {
                    emit(ProviderEvent.Notice(ignoredAttachmentsNotice))
                }

                val modelPath = localModelRepository.resolveDownloadedPath(platform.model)
                if (modelPath == null) {
                    emit(ProviderEvent.Failed(modelNotDownloadedError))
                    return@flow
                }

                val latestUserText = turns.lastOrNull()?.userMessage?.effectiveContent().orEmpty()
                val history = turns.dropLast(1).flatMap { turn ->
                    buildList {
                        add(LocalHistoryMessage(LocalHistoryRole.USER, turn.userMessage.effectiveContent()))
                        turn.assistantMessage?.effectiveContent()?.takeIf { it.isNotBlank() }?.let { content ->
                            add(LocalHistoryMessage(LocalHistoryRole.MODEL, content))
                        }
                    }
                }

                try {
                    var failed = false
                    localRuntime.runExclusiveFlow {
                        loadEngine(
                            LocalEngineSpec(
                                modelPath = modelPath,
                                accelerator = LocalAccelerators.normalize(platform.accelerator),
                                maxTokens = platform.maxTokens ?: DEFAULT_MAX_TOKENS
                            )
                        )
                        // Interim rebuild-every-turn. ADR-0002's warm conversation cache is ticket #318.
                        createConversation(
                            LocalConversationConfig(
                                sampler = LocalSamplerConfig(
                                    topK = platform.topK ?: DEFAULT_TOP_K,
                                    topP = platform.topP ?: DEFAULT_TOP_P,
                                    temperature = platform.temperature ?: DEFAULT_TEMPERATURE
                                ),
                                systemPrompt = platform.systemPrompt,
                                initialMessages = history
                            )
                        )
                        sendMessage(latestUserText)
                    }.collect { event ->
                        when (event) {
                            is LocalRuntimeEvent.TextDelta -> emit(ProviderEvent.TextDelta(event.text))

                            is LocalRuntimeEvent.ThinkingDelta -> emit(ProviderEvent.ThinkingDelta(event.text))

                            is LocalRuntimeEvent.Error -> {
                                failed = true
                                emit(ProviderEvent.Failed(event.message))
                            }

                            LocalRuntimeEvent.Done -> Unit
                        }
                    }
                    if (!failed) emit(ProviderEvent.Completed)
                } catch (error: CancellationException) {
                    localRuntime.cancelActive()
                    throw error
                } catch (error: Exception) {
                    emit(ProviderEvent.Failed(error.message ?: "Local inference failed"))
                } finally {
                    localRuntime.closeConversation()
                }
            }
        }
    }

    companion object {
        const val DEFAULT_IGNORED_ATTACHMENTS = "The local platform ignored attachments"
        const val DEFAULT_MODEL_NOT_DOWNLOADED =
            "This Local Model is not downloaded. Download it from Settings → Local Models."
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_MAX_TOKENS = 1024
    }
}
