package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.localruntime.ConversationFingerprint
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.LocalConversationConfig
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineSpec
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryMessage
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.localruntime.LocalSamplerConfig
import dev.chungjungsoo.gptmobile.data.localruntime.conversationFingerprint
import dev.chungjungsoo.gptmobile.data.localruntime.incomingHistoryExtendsConsumed
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class LiteRtLmAdapter(
    private val localRuntime: LocalRuntime,
    private val localModelRepository: LocalModelRepository,
    private val ignoredAttachmentsNotice: String,
    private val modelNotDownloadedError: String,
    private val waitingForEngineNotice: String = DEFAULT_WAITING_FOR_ENGINE,
    private val tooManyImagesNotice: String = DEFAULT_TOO_MANY_IMAGES,
    private val modelCatalogRepository: ModelCatalogRepository? = null,
    private val loadImageBytes: suspend (ChatAttachment) -> ByteArray? = { null }
) {
    private data class OpenConversation(
        val profileUid: String,
        val engineSpec: LocalEngineSpec,
        val sampler: LocalSamplerConfig,
        val systemPrompt: String?,
        val consumed: ConversationFingerprint
    )

    private var openConversation: OpenConversation? = null
    private var conversationDirty = false

    suspend fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession {
        return object : AgentProviderSession {
            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = channelFlow {
                val visionCapable = modelCatalogRepository
                    ?.getVisibleEntries()
                    ?.firstOrNull { entry -> entry.id == platform.model }
                    ?.capabilities
                    ?.vision == true
                val latestAttachments = turns.lastOrNull()?.userMessage?.attachments.orEmpty()
                attachmentNotices(visionCapable, turns, latestAttachments).forEach { notice ->
                    send(notice)
                }

                val modelPath = localModelRepository.resolveDownloadedPath(platform.model)
                if (modelPath == null) {
                    send(ProviderEvent.Failed(modelNotDownloadedError))
                    return@channelFlow
                }

                val latestUserText = turns.lastOrNull()?.userMessage?.effectiveContent().orEmpty()
                val latestImageIds = visionImageIds(latestAttachments, visionCapable)
                val latestImages = if (visionCapable) {
                    latestAttachments
                        .filter { attachment -> attachment.isImageAttachment() }
                        .take(MAX_IMAGES_PER_MESSAGE)
                        .mapNotNull { attachment -> loadImageBytes(attachment) }
                } else {
                    emptyList()
                }
                val history = historyMessages(
                    priorTurns = turns.dropLast(1),
                    visionCapable = visionCapable,
                    includeImageBytes = false
                )
                val spec = LocalEngineSpec(
                    modelPath = modelPath,
                    accelerator = LocalAccelerators.normalize(platform.accelerator),
                    maxTokens = platform.maxTokens ?: DEFAULT_MAX_TOKENS,
                    enableVision = visionCapable
                )
                val sampler = LocalSamplerConfig(
                    topK = platform.topK ?: DEFAULT_TOP_K,
                    topP = platform.topP ?: DEFAULT_TOP_P,
                    temperature = platform.temperature ?: DEFAULT_TEMPERATURE
                )
                val incomingPrior = conversationFingerprint(history)

                try {
                    var failed = false
                    val assistantReply = StringBuilder()
                    localRuntime.runExclusiveFlow(
                        onContended = { send(ProviderEvent.Notice(waitingForEngineNotice)) }
                    ) {
                        loadEngine(spec)
                        val snapshot = openConversation
                        val canReuse = !conversationDirty &&
                            hasOpenConversation() &&
                            snapshot != null &&
                            snapshot.profileUid == platform.uid &&
                            snapshot.engineSpec == spec &&
                            snapshot.sampler == sampler &&
                            snapshot.systemPrompt == platform.systemPrompt &&
                            incomingHistoryExtendsConsumed(snapshot.consumed, incomingPrior)
                        if (!canReuse) {
                            if (hasOpenConversation()) {
                                closeConversation()
                            }
                            val seedHistory = if (visionCapable) {
                                historyMessages(
                                    priorTurns = turns.dropLast(1),
                                    visionCapable = true,
                                    includeImageBytes = true
                                )
                            } else {
                                history
                            }
                            createConversation(
                                LocalConversationConfig(
                                    sampler = sampler,
                                    systemPrompt = platform.systemPrompt,
                                    initialMessages = seedHistory
                                )
                            )
                            openConversation = OpenConversation(
                                profileUid = platform.uid,
                                engineSpec = spec,
                                sampler = sampler,
                                systemPrompt = platform.systemPrompt,
                                consumed = incomingPrior
                            )
                        }
                        conversationDirty = true
                        sendMessage(latestUserText, latestImages)
                    }.collect { event ->
                        when (event) {
                            is LocalRuntimeEvent.TextDelta -> {
                                assistantReply.append(event.text)
                                send(ProviderEvent.TextDelta(event.text))
                            }

                            is LocalRuntimeEvent.ThinkingDelta -> send(ProviderEvent.ThinkingDelta(event.text))

                            is LocalRuntimeEvent.Error -> {
                                failed = true
                                conversationDirty = true
                                send(ProviderEvent.Failed(event.message))
                            }

                            LocalRuntimeEvent.Done -> Unit
                        }
                    }
                    if (!failed) {
                        send(ProviderEvent.Completed)
                        val snapshot = openConversation
                        if (snapshot != null) {
                            openConversation = snapshot.copy(
                                consumed = snapshot.consumed.extend(
                                    listOfNotNull(
                                        LocalHistoryMessage(
                                            role = LocalHistoryRole.USER,
                                            text = latestUserText,
                                            imageIds = latestImageIds
                                        ),
                                        assistantReply.toString().takeIf { it.isNotBlank() }?.let { content ->
                                            LocalHistoryMessage(LocalHistoryRole.MODEL, content)
                                        }
                                    )
                                )
                            )
                            conversationDirty = false
                        }
                    }
                } catch (error: CancellationException) {
                    localRuntime.cancelActive()
                    conversationDirty = true
                    throw error
                } catch (error: Exception) {
                    conversationDirty = true
                    send(ProviderEvent.Failed(error.message ?: "Local inference failed"))
                }
            }
        }
    }

    private fun attachmentNotices(
        visionCapable: Boolean,
        turns: List<ConversationTurn>,
        latestAttachments: List<ChatAttachment>
    ): List<ProviderEvent> = buildList {
        if (visionCapable) {
            val images = latestAttachments.filter { attachment -> attachment.isImageAttachment() }
            val nonImages = latestAttachments.filter { attachment -> !attachment.isImageAttachment() }
            if (images.size > MAX_IMAGES_PER_MESSAGE) {
                add(ProviderEvent.Notice(tooManyImagesNotice))
            }
            if (nonImages.isNotEmpty()) {
                add(ProviderEvent.Notice(ignoredAttachmentsNotice))
            }
            return@buildList
        }
        val hasAttachments = turns.any { turn ->
            turn.userMessage.attachments.isNotEmpty() ||
                turn.assistantMessage?.attachments?.isNotEmpty() == true
        }
        if (hasAttachments) {
            add(ProviderEvent.Notice(ignoredAttachmentsNotice))
        }
    }

    private suspend fun historyMessages(
        priorTurns: List<ConversationTurn>,
        visionCapable: Boolean,
        includeImageBytes: Boolean
    ): List<LocalHistoryMessage> = priorTurns.flatMap { turn ->
        val attachments = turn.userMessage.attachments
        val imageIds = visionImageIds(attachments, visionCapable)
        val images = if (includeImageBytes && visionCapable) {
            attachments
                .filter { attachment -> attachment.isImageAttachment() }
                .take(MAX_IMAGES_PER_MESSAGE)
                .mapNotNull { attachment -> loadImageBytes(attachment) }
        } else {
            emptyList()
        }
        buildList {
            add(
                LocalHistoryMessage(
                    role = LocalHistoryRole.USER,
                    text = turn.userMessage.effectiveContent(),
                    imageIds = imageIds,
                    images = images
                )
            )
            turn.assistantMessage?.effectiveContent()?.takeIf { it.isNotBlank() }?.let { content ->
                add(LocalHistoryMessage(LocalHistoryRole.MODEL, content))
            }
        }
    }

    private fun visionImageIds(
        attachments: List<ChatAttachment>,
        visionCapable: Boolean
    ): List<String> {
        if (!visionCapable) return emptyList()
        return attachments
            .filter { attachment -> attachment.isImageAttachment() }
            .take(MAX_IMAGES_PER_MESSAGE)
            .map { attachment -> attachment.identity() }
    }

    private fun ChatAttachment.isImageAttachment(): Boolean = mimeType.startsWith("image/")

    private fun ChatAttachment.identity(): String = "${preparedFilePath.ifBlank { localFilePath }}|$mimeType|$sizeBytes"

    companion object {
        const val DEFAULT_IGNORED_ATTACHMENTS = "The local platform ignored attachments"
        const val DEFAULT_MODEL_NOT_DOWNLOADED =
            "This Local Model is not downloaded. Download it from Settings → Local Models."
        const val DEFAULT_WAITING_FOR_ENGINE = "Waiting for the local engine"
        const val DEFAULT_TOO_MANY_IMAGES = "The local platform accepted only the first 10 images"
        const val MAX_IMAGES_PER_MESSAGE = 10
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_MAX_TOKENS = 1024
    }
}
