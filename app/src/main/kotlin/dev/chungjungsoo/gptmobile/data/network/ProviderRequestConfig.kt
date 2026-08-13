package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException

data class ProviderRequestConfig(
    val apiUrl: String,
    val token: String?
)

internal fun throwIfToolDefinitionsRejected(statusCode: Int, hasTools: Boolean) {
    if (hasTools && (statusCode == 400 || statusCode == 422)) {
        throw ToolDefinitionsRejectedException("HTTP $statusCode rejected tool definitions")
    }
}
