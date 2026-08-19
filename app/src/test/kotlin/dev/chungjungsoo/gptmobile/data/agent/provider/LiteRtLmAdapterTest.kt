package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmAdapterTest {
    @Test
    fun `streams text deltas in order and maps thinking channel`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(
                    LocalRuntimeEvent.ThinkingDelta("plan"),
                    LocalRuntimeEvent.TextDelta("hel"),
                    LocalRuntimeEvent.TextDelta("lo"),
                    LocalRuntimeEvent.Done
                )
            )
        }
        val adapter = adapter(runtime)

        val events = adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.ThinkingDelta("plan"),
                ProviderEvent.TextDelta("hel"),
                ProviderEvent.TextDelta("lo"),
                ProviderEvent.Completed
            ),
            events
        )
        assertEquals(listOf("hello"), runtime.sendMessageCalls)
        assertEquals(1, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
    }

    @Test
    fun `rebuilds conversation from prior history each turn`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime)
        val historyTurns = listOf(
            ConversationTurn(
                userMessage = MessageV2(content = "first", platformType = null),
                assistantMessage = MessageV2(content = "answer", platformType = "local"),
                isCurrentTurn = false
            ),
            ConversationTurn(
                userMessage = MessageV2(content = "second", platformType = null),
                assistantMessage = null,
                isCurrentTurn = true
            )
        )

        adapter.openSession(historyTurns, localPlatform()).streamRound(emptyList(), emptyList()).toList()

        val config = runtime.createConversationCalls.single()
        assertEquals("Be concise", config.systemPrompt)
        assertEquals(2, config.initialMessages.size)
        assertEquals(LocalHistoryRole.USER, config.initialMessages[0].role)
        assertEquals("first", config.initialMessages[0].text)
        assertEquals(LocalHistoryRole.MODEL, config.initialMessages[1].role)
        assertEquals("answer", config.initialMessages[1].text)
        assertEquals(listOf("second"), runtime.sendMessageCalls)
    }

    @Test
    fun `cancellation calls cancelActive`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("hel"), LocalRuntimeEvent.TextDelta("lo"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val events = mutableListOf<ProviderEvent>()

        val job = launch {
            adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).collect {
                events += it
            }
        }
        while (events.none { it is ProviderEvent.TextDelta }) {
            kotlinx.coroutines.yield()
        }
        job.cancelAndJoin()
        pause.complete(Unit)

        assertEquals(1, runtime.cancelActiveCalls)
        assertTrue(events.any { it is ProviderEvent.TextDelta })
    }

    @Test
    fun `attachment turns produce the notice event and continue with text`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime)
        val attachmentTurns = listOf(
            ConversationTurn(
                userMessage = MessageV2(
                    content = "describe this",
                    platformType = null,
                    attachments = listOf(
                        ChatAttachment(
                            localFilePath = "/tmp/photo.png",
                            preparedFilePath = "/tmp/photo.png",
                            displayName = "photo.png",
                            mimeType = "image/png",
                            sizeBytes = 12
                        )
                    )
                ),
                assistantMessage = null,
                isCurrentTurn = true
            )
        )

        val events = adapter.openSession(attachmentTurns, localPlatform()).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(
                ProviderEvent.Notice("The local platform ignored attachments"),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
        assertEquals(listOf("describe this"), runtime.sendMessageCalls)
    }

    @Test
    fun `missing model produces error event`() = runBlocking {
        val runtime = FakeLocalRuntime()
        val adapter = LiteRtLmAdapter(
            localRuntime = runtime,
            localModelRepository = FakeLocalModelRepository(),
            ignoredAttachmentsNotice = LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS,
            modelNotDownloadedError = LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED
        )

        val events = adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(ProviderEvent.Failed("This Local Model is not downloaded. Download it from Settings → Local Models.")),
            events
        )
        assertTrue(runtime.loadEngineCalls.isEmpty())
        assertTrue(runtime.sendMessageCalls.isEmpty())
    }

    private fun adapter(runtime: FakeLocalRuntime) = LiteRtLmAdapter(
        localRuntime = runtime,
        localModelRepository = FakeLocalModelRepository(downloadedPaths = mapOf("gemma3-1b-it" to "/models/gemma.litertlm")),
        ignoredAttachmentsNotice = LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS,
        modelNotDownloadedError = LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED
    )

    private fun turns(text: String) = listOf(
        ConversationTurn(
            userMessage = MessageV2(content = text, platformType = null),
            assistantMessage = null,
            isCurrentTurn = true
        )
    )

    private fun localPlatform() = PlatformV2(
        uid = "local",
        name = "Local",
        compatibleType = ClientType.LITERT_LM,
        apiUrl = "",
        model = "gemma3-1b-it",
        temperature = 0.8f,
        topP = 0.9f,
        topK = 32,
        maxTokens = 2048,
        accelerator = "gpu",
        systemPrompt = "Be concise"
    )
}
