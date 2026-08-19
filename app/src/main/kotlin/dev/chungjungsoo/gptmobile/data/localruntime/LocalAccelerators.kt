package dev.chungjungsoo.gptmobile.data.localruntime

object LocalAccelerators {
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val NPU = "npu"

    fun defaultFrom(supported: List<String>): String {
        val normalized = supported.map { it.lowercase() }
        return when {
            GPU in normalized -> GPU
            normalized.isNotEmpty() -> normalized.first()
            else -> CPU
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

    fun selectable(supported: List<String>): List<String> {
        val normalized = supported.map { it.lowercase() }.toSet()
        return listOf(CPU, GPU).filter { it in normalized }
    }
}
