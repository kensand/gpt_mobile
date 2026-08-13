package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

data class ResolvedAgentTool(
    val tool: AgentTool,
    val connectionUid: String?,
    val connectionName: String?,
    val realToolName: String,
    val modelToolName: String
)

class AgentToolResolver @Inject constructor(
    private val toolConnectionRepository: ToolConnectionRepository,
    private val secretVault: SecretVault,
    private val networkClient: NetworkClient
) {
    suspend fun resolve(profileUid: String): List<ResolvedAgentTool> {
        val resolved = mutableListOf(
            CurrentDateTool().resolved(null, null, BuiltInAgentTool.CURRENT_DATE)
        )
        toolConnectionRepository.listBindingsWithConnections(profileUid)
            .sortedWith(compareBy<AgentToolBindingWithConnection> { it.binding.toolName }.thenBy { it.binding.connectionUid ?: "" }.thenBy { it.binding.bindingUid })
            .forEach { binding ->
                resolveBinding(binding)?.let { resolved += it }
            }
        return resolved.distinctBy { it.modelToolName }
            .sortedBy { it.modelToolName }
    }

    private suspend fun resolveBinding(binding: AgentToolBindingWithConnection): ResolvedAgentTool? = when (binding.binding.toolName) {
        WEB_SEARCH_TOOL -> resolveWebSearch(binding.connection)

        BuiltInAgentTool.READ_URL -> if (binding.binding.connectionUid == null) {
            ReadUrlTool().resolved(null, null, BuiltInAgentTool.READ_URL)
        } else {
            null
        }

        else -> null
    }

    private suspend fun resolveWebSearch(connection: ToolConnection?): ResolvedAgentTool? {
        val actualConnection = connection ?: return null
        val provider = SEARCH_PROVIDERS[actualConnection.type] ?: return null
        val endpointUrl = provider.defaultEndpointUrl
        val definition = WebSearchTool(
            config = WebSearchProviderConfig(provider.provider, "", endpointUrl),
            networkClient = networkClient
        ).definition
        val credential = actualConnection.secretRef?.let { secretRef ->
            secretVault.read(secretRef)
        }
        val tool = credential?.let { bytes ->
            try {
                val token = String(bytes, StandardCharsets.UTF_8)
                if (token.isBlank()) {
                    MissingCredentialTool(definition)
                } else {
                    WebSearchTool(
                        config = WebSearchProviderConfig(
                            provider = provider.provider,
                            bearerToken = token,
                            endpointUrl = endpointUrl
                        ),
                        networkClient = networkClient
                    )
                }
            } finally {
                bytes.fill(0)
            }
        } ?: MissingCredentialTool(definition)
        return tool.resolved(actualConnection.connectionUid, actualConnection.name, WEB_SEARCH_TOOL)
    }

    private fun AgentTool.resolved(
        connectionUid: String?,
        connectionName: String?,
        realToolName: String
    ) = ResolvedAgentTool(
        tool = this,
        connectionUid = connectionUid,
        connectionName = connectionName,
        realToolName = realToolName,
        modelToolName = definition.name
    )

    private companion object {
        const val WEB_SEARCH_TOOL = "web_search"
        val SEARCH_PROVIDERS = mapOf(
            ToolConnectionType.FIRECRAWL to SearchProvider(WebSearchProvider.FIRECRAWL, "https://api.firecrawl.dev/v2/search"),
            ToolConnectionType.PERPLEXITY to SearchProvider(WebSearchProvider.PERPLEXITY, "https://api.perplexity.ai/search"),
            ToolConnectionType.EXA to SearchProvider(WebSearchProvider.EXA, "https://api.exa.ai/search")
        )
    }
}

private data class SearchProvider(
    val provider: WebSearchProvider,
    val defaultEndpointUrl: String
)

private class MissingCredentialTool(
    override val definition: AgentToolDefinition
) : AgentTool {

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text("Tool web_search is unavailable: missing credential."),
        isError = true
    )
}
