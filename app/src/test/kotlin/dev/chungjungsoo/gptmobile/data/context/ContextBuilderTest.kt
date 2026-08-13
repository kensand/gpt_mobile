package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextBuilderTest {
    @Test
    fun `tool-only historical turn keeps its user message for follow-up context`() {
        val platform = PlatformV2(
            uid = "profile",
            name = "Provider",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://provider.example/v1",
            model = "model"
        )

        val turns = ContextBuilder().build(
            userMessages = listOf(
                MessageV2(content = "What is the latest album of NMIXX?", platformType = null),
                MessageV2(content = "Where's the result?", platformType = null)
            ),
            assistantMessages = listOf(
                listOf(MessageV2(content = "", thoughts = "I searched for it.", platformType = platform.uid)),
                listOf(MessageV2(content = "", platformType = platform.uid))
            ),
            platform = platform
        )

        assertEquals(
            listOf("What is the latest album of NMIXX?", "Where's the result?"),
            turns.map { it.userMessage.content }
        )
        assertNull(turns.first().assistantMessage)
    }
}
