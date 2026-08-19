package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.localmodel.SocModelFileResolver

object LocalAccelerators {
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val NPU = "npu"

    /**
     * NPU is offered only when the catalog lists it and this device has a matching
     * SOC-specific model file. Gallery hides NPU-only models whose `socToModelFiles`
     * omit `Build.SOC_MODEL`; we apply that same SOC gate to the Accelerator picker
     * so incapable devices never see NPU. CPU/GPU stay available from the default file.
     */
    fun isNpuEligible(
        supported: List<String>,
        socToModelFiles: Map<String, *>,
        deviceSocModel: String
    ): Boolean {
        val listsNpu = supported.any { it.equals(NPU, ignoreCase = true) }
        return listsNpu && SocModelFileResolver.hasMatchingVariant(socToModelFiles, deviceSocModel)
    }

    fun defaultFrom(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = ""
    ): String {
        val options = selectable(supported, socToModelFiles, deviceSocModel).toSet()
        return when {
            GPU in options -> GPU
            else -> supported.map { it.lowercase() }.firstOrNull { it in options } ?: CPU
        }
    }

    fun normalize(value: String?): String {
        val normalized = value?.lowercase()
        return if (normalized == CPU || normalized == GPU || normalized == NPU) {
            normalized
        } else {
            CPU
        }
    }

    fun selectable(
        supported: List<String>,
        socToModelFiles: Map<String, *> = emptyMap<String, Any>(),
        deviceSocModel: String = ""
    ): List<String> {
        val normalized = supported.map { it.lowercase() }.toSet()
        return listOf(CPU, GPU, NPU).filter { accelerator ->
            accelerator in normalized && (
                accelerator != NPU || isNpuEligible(supported, socToModelFiles, deviceSocModel)
                )
        }
    }

    fun shouldApplySampler(accelerator: String): Boolean = normalize(accelerator) != NPU
}
