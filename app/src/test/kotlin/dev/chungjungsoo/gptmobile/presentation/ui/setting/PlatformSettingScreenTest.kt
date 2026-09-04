package dev.chungjungsoo.gptmobile.presentation.ui.setting

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformSettingScreenTest {
    @Test
    fun apiKeySummaryNeverReturnsTokenCharacters() {
        assertEquals("Key set", apiKeySummary("secret", "Key set", "Key not set"))
        assertEquals("Key not set", apiKeySummary(null, "Key set", "Key not set"))
        assertEquals("Key not set", apiKeySummary("", "Key set", "Key not set"))
    }
}
