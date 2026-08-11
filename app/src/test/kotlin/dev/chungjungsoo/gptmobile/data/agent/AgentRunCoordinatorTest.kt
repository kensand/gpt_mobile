package dev.chungjungsoo.gptmobile.data.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunCoordinatorTest {
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
