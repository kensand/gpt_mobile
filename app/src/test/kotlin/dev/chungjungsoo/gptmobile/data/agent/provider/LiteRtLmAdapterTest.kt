package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineHolder
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryMessage
import dev.chungjungsoo.gptmobile.data.localruntime.LocalHistoryRole
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
        assertEquals(0, runtime.closeConversationCalls)
    }

    @Test
    fun `seeds conversation from prior history on first turn`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done))
        }
        val adapter = adapter(runtime)
        val historyTurns = listOf(
            completedTurn("first", "answer"),
            pendingTurn("second")
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
    fun `happy path follow-up sends only the new message`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(1, runtime.createConversationCalls.size)
        assertTrue(runtime.createConversationCalls.single().initialMessages.isEmpty())
        assertEquals(listOf("first", "second"), runtime.sendMessageCalls)
        assertEquals(0, runtime.closeConversationCalls)
    }

    @Test
    fun `edited early history closes and rebuilds the conversation`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("edited", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "edited"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            ),
            runtime.createConversationCalls[1].initialMessages
        )
        assertEquals(listOf("first", "second"), runtime.sendMessageCalls)
    }

    @Test
    fun `retry of the last turn rebuilds from remaining history`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("retry"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "answer"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(
            listOf(
                LocalHistoryMessage(LocalHistoryRole.USER, "first"),
                LocalHistoryMessage(LocalHistoryRole.MODEL, "answer")
            ),
            runtime.createConversationCalls[1].initialMessages
        )
        assertEquals(listOf("first", "second", "second"), runtime.sendMessageCalls)
    }

    @Test
    fun `revision switch rebuilds with the selected assistant content`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("answer"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("next"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()

        adapter.openSession(turns("first"), platform).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(
            listOf(completedTurn("first", "revision"), pendingTurn("second")),
            platform
        ).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals("revision", runtime.createConversationCalls[1].initialMessages[1].text)
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
            yield()
        }
        job.cancelAndJoin()
        pause.complete(Unit)

        assertEquals(1, runtime.cancelActiveCalls)
        assertTrue(events.any { it is ProviderEvent.TextDelta })
    }

    @Test
    fun `cancel mid-stream forces the next turn to rebuild`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("hel"), LocalRuntimeEvent.TextDelta("lo"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("rebuilt"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)
        val platform = localPlatform()
        val events = mutableListOf<ProviderEvent>()

        val job = launch {
            adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).collect {
                events += it
            }
        }
        while (events.none { it is ProviderEvent.TextDelta }) {
            yield()
        }
        job.cancelAndJoin()
        pause.complete(Unit)

        adapter.openSession(turns("hello"), platform).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(listOf("hello", "hello"), runtime.sendMessageCalls)
    }

    @Test
    fun `different profile rebuilds even when history matches`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val adapter = adapter(runtime)

        adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(turns("hello"), localPlatform(uid = "other")).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(1, runtime.closeConversationCalls)
        assertEquals(1, runtime.loadEngineCalls.distinct().size)
    }

    @Test
    fun `different model rebuilds and swaps the engine`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(runtime)
        val adapter = adapter(
            holder,
            FakeLocalModelRepository(
                downloadedPaths = mapOf(
                    "gemma3-1b-it" to "/models/gemma.litertlm",
                    "other-model" to "/models/other.litertlm"
                )
            )
        )

        adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).toList()
        adapter.openSession(turns("hello"), localPlatform(model = "other-model")).streamRound(emptyList(), emptyList()).toList()

        assertEquals(2, runtime.createConversationCalls.size)
        assertEquals(2, runtime.loadEngineCalls.size)
        assertEquals("/models/gemma.litertlm", runtime.loadEngineCalls[0].modelPath)
        assertEquals("/models/other.litertlm", runtime.loadEngineCalls[1].modelPath)
        assertEquals(1, runtime.unloadEngineCalls)
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

    @Test
    fun `contended mutex emits waiting notice and runs complete in order`() = runBlocking {
        val pause = CompletableDeferred<Unit>()
        val runtime = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(runtime)
        val adapter = adapter(holder)
        val platform = localPlatform()
        val firstEvents = mutableListOf<ProviderEvent>()
        val secondEvents = mutableListOf<ProviderEvent>()

        val first = launch {
            adapter.openSession(turns("a"), platform).streamRound(emptyList(), emptyList()).collect {
                firstEvents += it
            }
        }
        while (firstEvents.none { it is ProviderEvent.TextDelta }) {
            yield()
        }

        val second = launch {
            adapter.openSession(turns("b"), localPlatform(uid = "other")).streamRound(emptyList(), emptyList()).collect {
                secondEvents += it
            }
        }
        while (secondEvents.none { it is ProviderEvent.Notice }) {
            yield()
        }

        pause.complete(Unit)
        first.join()
        second.join()

        assertEquals(
            listOf(ProviderEvent.TextDelta("one"), ProviderEvent.Completed),
            firstEvents
        )
        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_WAITING_FOR_ENGINE),
                ProviderEvent.TextDelta("two"),
                ProviderEvent.Completed
            ),
            secondEvents
        )
        assertEquals(listOf("a", "b"), runtime.sendMessageCalls)
    }

    @Test
    fun `holding the fake mutex emits waiting notice before the run`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("ok"), LocalRuntimeEvent.Done))
        }
        runtime.generationMutex.lock()
        val adapter = adapter(runtime)
        val events = mutableListOf<ProviderEvent>()

        val job = launch {
            adapter.openSession(turns("hello"), localPlatform()).streamRound(emptyList(), emptyList()).collect {
                events += it
            }
        }
        while (events.none { it is ProviderEvent.Notice }) {
            yield()
        }

        runtime.generationMutex.unlock()
        job.join()

        assertEquals(
            listOf(
                ProviderEvent.Notice(LiteRtLmAdapter.DEFAULT_WAITING_FOR_ENGINE),
                ProviderEvent.TextDelta("ok"),
                ProviderEvent.Completed
            ),
            events
        )
    }

    private fun adapter(
        runtime: LocalRuntime,
        models: FakeLocalModelRepository = FakeLocalModelRepository(
            downloadedPaths = mapOf("gemma3-1b-it" to "/models/gemma.litertlm")
        )
    ) = LiteRtLmAdapter(
        localRuntime = runtime,
        localModelRepository = models,
        ignoredAttachmentsNotice = LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS,
        modelNotDownloadedError = LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED
    )

    private fun turns(text: String) = listOf(pendingTurn(text))

    private fun pendingTurn(text: String) = ConversationTurn(
        userMessage = MessageV2(content = text, platformType = null),
        assistantMessage = null,
        isCurrentTurn = true
    )

    private fun completedTurn(user: String, assistant: String) = ConversationTurn(
        userMessage = MessageV2(content = user, platformType = null),
        assistantMessage = MessageV2(content = assistant, platformType = "local"),
        isCurrentTurn = false
    )

    private fun localPlatform(
        uid: String = "local",
        model: String = "gemma3-1b-it"
    ) = PlatformV2(
        uid = uid,
        name = "Local",
        compatibleType = ClientType.LITERT_LM,
        apiUrl = "",
        model = model,
        temperature = 0.8f,
        topP = 0.9f,
        topK = 32,
        maxTokens = 2048,
        accelerator = "gpu",
        systemPrompt = "Be concise"
    )
}
