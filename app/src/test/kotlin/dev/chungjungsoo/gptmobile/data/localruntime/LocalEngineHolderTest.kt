package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEngineHolderTest {
    @Test
    fun `reuses engine for the same spec and reloads for a different spec`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake)
        val first = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)
        val second = LocalEngineSpec("/models/b.litertlm", LocalAccelerators.CPU, 2048)

        holder.loadEngine(first)
        holder.loadEngine(first)
        holder.loadEngine(second)

        assertEquals(listOf(first, second), fake.loadEngineCalls)
        assertEquals(1, fake.unloadEngineCalls)
    }

    @Test
    fun `mutex serializes two concurrent sends`() = runTest {
        val fake = FakeLocalRuntime().apply {
            emitDelayMillis = 50L
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(fake)
        val order = mutableListOf<String>()

        val first = launch {
            holder.sendMessage("a").collect { event ->
                if (event is LocalRuntimeEvent.TextDelta) order += "a:${event.text}"
                if (event is LocalRuntimeEvent.Done) order += "a:done"
            }
        }
        val second = async {
            holder.sendMessage("b").toList()
        }

        first.join()
        val secondEvents = second.await()
        advanceUntilIdle()

        assertEquals(listOf("a:one", "a:done"), order)
        assertTrue(secondEvents.first() is LocalRuntimeEvent.TextDelta)
        assertEquals("two", (secondEvents.first() as LocalRuntimeEvent.TextDelta).text)
        assertEquals(listOf("a", "b"), fake.sendMessageCalls)
    }

    @Test
    fun `reloads engine when vision flag changes`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake)
        val textOnly = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024, enableVision = false)
        val vision = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024, enableVision = true)

        holder.loadEngine(textOnly)
        holder.loadEngine(vision)

        assertEquals(listOf(textOnly, vision), fake.loadEngineCalls)
        assertEquals(1, fake.unloadEngineCalls)
    }

    @Test
    fun `reloads engine when accelerator changes from GPU to NPU`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake)
        val gpu = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)
        val npu = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.NPU, 1024)

        holder.loadEngine(gpu)
        holder.loadEngine(npu)

        assertEquals(listOf(gpu, npu), fake.loadEngineCalls)
        assertEquals(1, fake.unloadEngineCalls)
    }

    @Test
    fun `forwards image payloads to the delegate`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake)
        val image = byteArrayOf(1, 2, 3)

        holder.sendMessage("look", listOf(image)).toList()

        assertEquals(listOf("look"), fake.sendMessageCalls)
        assertEquals(1, fake.sendMessageImages.single().size)
        assertTrue(fake.sendMessageImages.single().single().contentEquals(image))
    }
}
