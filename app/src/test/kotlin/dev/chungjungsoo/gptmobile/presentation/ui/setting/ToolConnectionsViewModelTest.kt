package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolConnectionsViewModelTest {
    @Test
    fun `provider metadata records the credential transport`() {
        val providers = ToolConnectionsViewModel.providers.associateBy { it.type }

        assertEquals(ToolConnectionAuthType.BEARER, providers.getValue(ToolConnectionType.FIRECRAWL).authType)
        assertEquals(ToolConnectionAuthType.BEARER, providers.getValue(ToolConnectionType.PERPLEXITY).authType)
        assertEquals(ToolConnectionAuthType.API_KEY, providers.getValue(ToolConnectionType.EXA).authType)
    }

    @Test
    fun `normalizeAlias keeps aliases lowercase model safe and validates boundaries`() {
        assertEquals("fire_crawl_1", ToolConnectionsViewModel.normalizeAlias(" Fire-Crawl 1 "))

        assertTrue(ToolConnectionsViewModel.isValidAlias("exa_search"))
        assertTrue(ToolConnectionsViewModel.isValidAlias("a1234567890123456789012345678901"))
        assertFalse(ToolConnectionsViewModel.isValidAlias(""))
        assertFalse(ToolConnectionsViewModel.isValidAlias("1exa"))
        assertFalse(ToolConnectionsViewModel.isValidAlias("exa-search"))
        assertFalse(ToolConnectionsViewModel.isValidAlias("a12345678901234567890123456789012"))
    }

    @Test
    fun `normalizeAlias is independent of the device locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals("internal", ToolConnectionsViewModel.normalizeAlias("INTERNAL"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `credentialInput preserves blank edit credentials unless explicit clear is selected`() {
        assertNull(ToolConnectionsViewModel.credentialInput("   ", clearCredential = false))
        assertEquals("new-key", ToolConnectionsViewModel.credentialInput(" new-key ", clearCredential = false)?.decodeToString())
        assertNull(ToolConnectionsViewModel.credentialInput("new-key", clearCredential = true))
    }
}
