package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.plugins.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientTest {

    @Test
    fun `network logging avoids request body logging`() {
        assertEquals(LogLevel.HEADERS, NetworkClient.resolveNetworkLogLevel())
    }

    @Test
    fun `gemini credential header is sanitized case insensitively`() {
        assertTrue(NetworkClient.isSensitiveHeader("x-goog-api-key"))
        assertTrue(NetworkClient.isSensitiveHeader("X-Goog-Api-Key"))
    }
}
