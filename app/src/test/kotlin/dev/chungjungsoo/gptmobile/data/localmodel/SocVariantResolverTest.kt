package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class SocVariantResolverTest {

    @Test
    fun `matching device SOC selects the variant file url and size`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "SM8650")

        assertEquals("qwen-sm8650.litertlm", resolved.fileName)
        assertEquals(VARIANT_URL, resolved.downloadUrl)
        assertEquals("abc", resolved.commitHash)
        assertEquals(1_597_931_520L, resolved.sizeInBytes)
    }

    @Test
    fun `SOC match is case insensitive`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "sm8650")

        assertEquals("qwen-sm8650.litertlm", resolved.fileName)
        assertEquals(VARIANT_URL, resolved.downloadUrl)
    }

    @Test
    fun `unknown device SOC falls back to the default file`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "SM8750")

        assertEquals(DEFAULT_FILE, resolved.fileName)
        assertEquals(DEFAULT_URL, resolved.downloadUrl)
        assertEquals(DEFAULT_COMMIT, resolved.commitHash)
        assertEquals(DEFAULT_SIZE, resolved.sizeInBytes)
    }

    @Test
    fun `blank device SOC falls back to the default file`() {
        val resolved = SocVariantResolver.resolve(entryWithVariant(), deviceSocModel = "")

        assertEquals(DEFAULT_FILE, resolved.fileName)
        assertEquals(DEFAULT_URL, resolved.downloadUrl)
    }

    @Test
    fun `entry without variants always uses the default file`() {
        val resolved = SocVariantResolver.resolve(entryWithoutVariants(), deviceSocModel = "SM8650")

        assertEquals(DEFAULT_FILE, resolved.fileName)
        assertEquals(DEFAULT_URL, resolved.downloadUrl)
        assertEquals(DEFAULT_COMMIT, resolved.commitHash)
        assertEquals(DEFAULT_SIZE, resolved.sizeInBytes)
    }

    private fun entryWithVariant() = CatalogEntry(
        id = "qwen2.5-1.5b-instruct",
        downloadUrl = DEFAULT_URL,
        sizeInBytes = DEFAULT_SIZE,
        socToModelFiles = mapOf(
            "SM8650" to SocVariant(
                modelFile = "qwen-sm8650.litertlm",
                downloadUrl = VARIANT_URL,
                commitHash = "abc",
                sizeInBytes = 1_597_931_520L
            )
        )
    )

    private fun entryWithoutVariants() = CatalogEntry(
        id = "gemma3-1b-it",
        downloadUrl = DEFAULT_URL,
        sizeInBytes = DEFAULT_SIZE
    )

    private companion object {
        const val DEFAULT_FILE = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val DEFAULT_COMMIT = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614"
        const val DEFAULT_SIZE = 1_597_931_520L
        const val DEFAULT_URL =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true"
        const val VARIANT_URL =
            "https://huggingface.co/example/qwen/resolve/abc/qwen-sm8650.litertlm?download=true"
    }
}
