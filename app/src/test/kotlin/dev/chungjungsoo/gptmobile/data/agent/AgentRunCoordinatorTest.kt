package dev.chungjungsoo.gptmobile.data.agent

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunCoordinatorTest {
    @Test
    fun `cancellation joins the run before applying the terminal fallback`() = runTest {
        val events = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { events += "persist partial message" }
            }
        }

        cancelAndJoinAgentRun(job) { events += "terminal fallback" }

        assertEquals(listOf("persist partial message", "terminal fallback"), events)
    }

    @Test
    fun `terminal message is persisted only after the running transition wins`() = runTest {
        val rejectedEvents = mutableListOf<String>()
        val rejected = commitTerminalAgentRun(
            finishRun = {
                rejectedEvents += "finish"
                false
            },
            persistMessage = { rejectedEvents += "message" }
        )

        val acceptedEvents = mutableListOf<String>()
        val accepted = commitTerminalAgentRun(
            finishRun = {
                acceptedEvents += "finish"
                true
            },
            persistMessage = { acceptedEvents += "message" }
        )

        assertFalse(rejected)
        assertEquals(listOf("finish"), rejectedEvents)
        assertTrue(accepted)
        assertEquals(listOf("finish", "message"), acceptedEvents)
    }
}
