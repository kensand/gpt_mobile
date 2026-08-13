package dev.chungjungsoo.gptmobile.presentation.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunForegroundServiceTest {
    @Test
    fun `completion notification posts only for background nonempty to empty transition`() {
        assertTrue(shouldNotifyAgentRunsCompleted(wasActive = true, isActive = false, isAppBackground = true))
    }

    @Test
    fun `completion notification skips initial empty and foreground completion`() {
        assertFalse(shouldNotifyAgentRunsCompleted(wasActive = false, isActive = false, isAppBackground = true))
        assertFalse(shouldNotifyAgentRunsCompleted(wasActive = true, isActive = false, isAppBackground = false))
        assertFalse(shouldNotifyAgentRunsCompleted(wasActive = true, isActive = true, isAppBackground = true))
    }
}
