package dev.chungjungsoo.gptmobile.data.localruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAcceleratorsTest {

    @Test
    fun `NPU is eligible when the model lists it and the device has a SOC variant`() {
        assertTrue(
            LocalAccelerators.isNpuEligible(
                supported = listOf("cpu", "gpu", "npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `NPU eligibility matches SOC keys case insensitively`() {
        assertTrue(
            LocalAccelerators.isNpuEligible(
                supported = listOf("npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "sm8650"
            )
        )
    }

    @Test
    fun `NPU is not eligible when the catalog does not list it`() {
        assertFalse(
            LocalAccelerators.isNpuEligible(
                supported = listOf("cpu", "gpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `NPU is not eligible when the device has no matching SOC variant`() {
        assertFalse(
            LocalAccelerators.isNpuEligible(
                supported = listOf("cpu", "npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8750"
            )
        )
    }

    @Test
    fun `NPU is not eligible when the entry has no SOC variants`() {
        assertFalse(
            LocalAccelerators.isNpuEligible(
                supported = listOf("npu", "cpu"),
                socToModelFiles = emptyMap<String, Any>(),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `selectable includes NPU only when the device qualifies`() {
        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU, LocalAccelerators.NPU),
            LocalAccelerators.selectable(
                supported = listOf("npu", "cpu", "gpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU),
            LocalAccelerators.selectable(
                supported = listOf("npu", "cpu", "gpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "tensor"
            )
        )
    }

    @Test
    fun `selectable keeps CPU and GPU when NPU is listed but the device does not qualify`() {
        assertEquals(
            listOf(LocalAccelerators.GPU),
            LocalAccelerators.selectable(
                supported = listOf("gpu", "npu"),
                socToModelFiles = emptyMap<String, Any>(),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `default accelerator prefers GPU and never picks ineligible NPU`() {
        assertEquals(
            LocalAccelerators.GPU,
            LocalAccelerators.defaultFrom(
                supported = listOf("cpu", "gpu", "npu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
        assertEquals(
            LocalAccelerators.NPU,
            LocalAccelerators.defaultFrom(
                supported = listOf("npu", "cpu"),
                socToModelFiles = mapOf("SM8650" to VARIANT),
                deviceSocModel = "SM8650"
            )
        )
        assertEquals(
            LocalAccelerators.CPU,
            LocalAccelerators.defaultFrom(
                supported = listOf("npu", "cpu"),
                socToModelFiles = emptyMap<String, Any>(),
                deviceSocModel = "SM8650"
            )
        )
    }

    @Test
    fun `sampler config is skipped on NPU`() {
        assertFalse(LocalAccelerators.shouldApplySampler(LocalAccelerators.NPU))
        assertTrue(LocalAccelerators.shouldApplySampler(LocalAccelerators.CPU))
        assertTrue(LocalAccelerators.shouldApplySampler(LocalAccelerators.GPU))
    }

    private companion object {
        val VARIANT = Any()
    }
}
