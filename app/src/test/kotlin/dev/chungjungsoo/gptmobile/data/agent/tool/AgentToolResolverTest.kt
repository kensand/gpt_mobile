package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolResolverTest {
    @Test
    fun `zero bindings resolves no tools`() = runBlocking {
        val resolver = resolver()

        assertEquals(emptyList<ResolvedAgentTool>(), resolver.resolve("profile-1"))
    }

    @Test
    fun `resolve filters bindings to exact profile`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val resolver = resolver(dao)
        dao.bind(connection("search-1", ToolConnectionType.FIRECRAWL, secretRef = "secret-1"), binding("profile-2", "search-1", "web_search"))
        dao.bind(null, binding("profile-1", null, BuiltInAgentTool.READ_URL))

        val resolved = resolver.resolve("profile-1")

        assertEquals(listOf("read_url"), resolved.map { it.tool.definition.name })
        assertEquals(null, resolved.single().connectionUid)
    }

    @Test
    fun `search bindings map provider endpoints and wipe credential bytes`() = runBlocking {
        val cases = listOf(
            ToolConnectionType.FIRECRAWL to WebSearchProvider.FIRECRAWL to "https://api.firecrawl.dev/v2/search",
            ToolConnectionType.PERPLEXITY to WebSearchProvider.PERPLEXITY to "https://api.perplexity.ai/search",
            ToolConnectionType.EXA to WebSearchProvider.EXA to "https://api.exa.ai/search"
        )

        cases.forEachIndexed { index, (providerCase, defaultEndpoint) ->
            val (connectionType, expectedProvider) = providerCase
            val dao = ResolverFakeToolConnectionDao()
            val vault = ResolverFakeSecretVault(mapOf("secret-$index" to "token-$index".encodeToByteArray()))
            val resolver = resolver(dao, vault)
            dao.bind(connection("search-$index", connectionType, endpointUrl = null, secretRef = "secret-$index"), binding("profile-1", "search-$index", "web_search"))

            val resolved = resolver.resolve("profile-1").single()
            val config = resolved.tool.webSearchConfig()

            assertEquals("web_search", resolved.realToolName)
            assertEquals("web_search", resolved.modelToolName)
            assertEquals("search-$index", resolved.connectionUid)
            assertEquals("Search $index", resolved.connectionName)
            assertEquals(expectedProvider, config.provider)
            assertEquals("token-$index", config.bearerToken)
            assertEquals(defaultEndpoint, config.endpointUrl)
            assertTrue(vault.lastReadBytes!!.all { it == 0.toByte() })
        }
    }

    @Test
    fun `search binding ignores stored endpoint to protect bearer credentials`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val vault = ResolverFakeSecretVault(mapOf("secret-1" to "token".encodeToByteArray()))
        val resolver = resolver(dao, vault)
        dao.bind(
            connection("search-1", ToolConnectionType.EXA, endpointUrl = "https://search.example/custom", secretRef = "secret-1"),
            binding("profile-1", "search-1", "web_search")
        )

        val config = resolver.resolve("profile-1").single().tool.webSearchConfig()

        assertEquals("https://api.exa.ai/search", config.endpointUrl)
    }

    @Test
    fun `assigned search without credential returns bounded error without network`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val resolver = resolver(dao)
        dao.bind(connection("search-1", ToolConnectionType.FIRECRAWL, secretRef = null), binding("profile-1", "search-1", "web_search"))

        val resolved = resolver.resolve("profile-1").single()
        val result = resolved.tool.execute("call-1", buildJsonObject {})

        assertEquals("web_search", resolved.tool.definition.name)
        assertTrue(result.isError)
        val text = (result.content as ToolResultContent.Text).text
        assertTrue(text.contains("missing credential"))
        assertTrue(text.length <= 240)
        assertTrue(!text.contains("secret"))
        assertTrue(!text.contains("search-1"))
    }

    @Test
    fun `vault read failure aborts resolution instead of masking credential storage errors`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val vault = ResolverFakeSecretVault(readError = IllegalStateException("vault unavailable"))
        val resolver = resolver(dao, vault)
        dao.bind(connection("search-1", ToolConnectionType.FIRECRAWL, secretRef = "secret-1"), binding("profile-1", "search-1", "web_search"))

        val error = runCatching { resolver.resolve("profile-1") }.exceptionOrNull()

        assertEquals("vault unavailable", error?.message)
    }

    @Test
    fun `read url binding exposes default read_url tool snapshot`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val resolver = resolver(dao)
        dao.bind(null, binding("profile-1", null, BuiltInAgentTool.READ_URL))

        val resolved = resolver.resolve("profile-1").single()

        assertEquals(ReadUrlTool::class.java, resolved.tool.javaClass)
        assertEquals(null, resolved.connectionUid)
        assertEquals(null, resolved.connectionName)
        assertEquals("read_url", resolved.realToolName)
        assertEquals("read_url", resolved.modelToolName)
    }

    @Test
    fun `orphan unknown and mcp bindings are ignored in web resolver branch`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val resolver = resolver(dao)
        dao.bind(null, binding("profile-1", "missing", "web_search"))
        dao.bind(connection("mcp-1", ToolConnectionType.MCP, secretRef = "secret-mcp"), binding("profile-1", "mcp-1", "mcp_tool"))
        dao.bind(connection("search-1", ToolConnectionType.FIRECRAWL, secretRef = "secret-1"), binding("profile-1", "search-1", "unknown_tool"))

        assertEquals(emptyList<ResolvedAgentTool>(), resolver.resolve("profile-1"))
    }

    @Test
    fun `resolved tools are deterministic and duplicate model names keep first binding`() = runBlocking {
        val dao = ResolverFakeToolConnectionDao()
        val vault = ResolverFakeSecretVault(
            mapOf(
                "secret-a" to "token-a".encodeToByteArray(),
                "secret-b" to "token-b".encodeToByteArray()
            )
        )
        val resolver = resolver(dao, vault)
        dao.bind(connection("search-b", ToolConnectionType.EXA, secretRef = "secret-b"), binding("profile-1", "search-b", "web_search", bindingUid = "binding-b"))
        dao.bind(connection("search-a", ToolConnectionType.FIRECRAWL, secretRef = "secret-a"), binding("profile-1", "search-a", "web_search", bindingUid = "binding-a"))
        dao.bind(null, binding("profile-1", null, BuiltInAgentTool.READ_URL, bindingUid = "binding-read"))

        val resolved = resolver.resolve("profile-1")

        assertEquals(listOf("read_url", "web_search"), resolved.map { it.modelToolName })
        assertEquals("search-a", resolved.single { it.modelToolName == "web_search" }.connectionUid)
        assertEquals(WebSearchProvider.FIRECRAWL, resolved.single { it.modelToolName == "web_search" }.tool.webSearchConfig().provider)
    }

    private fun resolver(
        dao: ResolverFakeToolConnectionDao = ResolverFakeToolConnectionDao(),
        vault: ResolverFakeSecretVault = ResolverFakeSecretVault()
    ) = AgentToolResolver(ToolConnectionRepository(dao, vault), vault, NetworkClient(CIO))

    private fun connection(
        uid: String,
        type: String,
        endpointUrl: String? = "https://$uid.example/search",
        secretRef: String? = null
    ) = ToolConnection(
        connectionUid = uid,
        name = "Search ${uid.substringAfterLast("-")}",
        alias = uid,
        type = type,
        endpointUrl = endpointUrl,
        authType = ToolConnectionAuthType.BEARER,
        secretRef = secretRef,
        oauthClientId = null
    )

    private fun binding(
        profileUid: String,
        connectionUid: String?,
        toolName: String,
        bindingUid: String = "$profileUid-${connectionUid ?: "builtin"}-$toolName"
    ) = AgentToolBinding(
        bindingUid = bindingUid,
        profileUid = profileUid,
        connectionUid = connectionUid,
        toolName = toolName
    )

    private fun AgentTool.webSearchConfig(): WebSearchProviderConfig {
        assertEquals(WebSearchTool::class.java, javaClass)
        val field = WebSearchTool::class.java.getDeclaredField("config")
        field.isAccessible = true
        return field.get(this) as WebSearchProviderConfig
    }
}

private class ResolverFakeSecretVault(
    private val values: Map<String, ByteArray> = emptyMap(),
    private val readError: Throwable? = null
) : SecretVault {
    var lastReadBytes: ByteArray? = null

    override suspend fun put(secretRef: String, secret: ByteArray) = Unit

    override suspend fun read(secretRef: String): ByteArray? {
        readError?.let { throw it }
        return values[secretRef]?.copyOf()?.also { lastReadBytes = it }
    }

    override suspend fun delete(secretRef: String) = Unit
}

private class ResolverFakeToolConnectionDao : ToolConnectionDao {
    private val connections = mutableMapOf<String, ToolConnection>()
    private val bindings = mutableMapOf<String, AgentToolBinding>()

    fun bind(connection: ToolConnection?, binding: AgentToolBinding) {
        connection?.let { connections[it.connectionUid] = it }
        bindings[binding.bindingUid] = binding
    }

    override suspend fun listConnections(): List<ToolConnection> = connections.values.sortedWith(
        compareBy<ToolConnection> { it.name }
            .thenBy { it.alias }
            .thenBy { it.connectionUid }
    )

    override suspend fun getConnection(connectionUid: String): ToolConnection? = connections[connectionUid]

    override suspend fun getConnectionsByUids(connectionUids: List<String>): List<ToolConnection> = connectionUids.mapNotNull(connections::get)

    override suspend fun upsertConnection(connection: ToolConnection) {
        connections[connection.connectionUid] = connection
    }

    override suspend fun deleteConnectionByUid(connectionUid: String) {
        connections.remove(connectionUid)
        bindings.values.removeAll { it.connectionUid == connectionUid }
    }

    override suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding> = bindings.values
        .filter { it.profileUid == profileUid }
        .sortedWith(compareBy<AgentToolBinding> { it.toolName }.thenBy { it.connectionUid ?: "" }.thenBy { it.bindingUid })

    override suspend fun insertBinding(binding: AgentToolBinding) {
        bindings[binding.bindingUid] = binding
    }

    override suspend fun deleteConnectionToolBindingsForTypes(
        profileUid: String,
        toolName: String,
        connectionTypes: List<String>
    ) {
        bindings.values.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.toolName == toolName &&
                binding.connectionUid?.let { connections[it]?.type in connectionTypes } == true
        }
    }

    override suspend fun deleteBuiltInToolBinding(profileUid: String, toolName: String) {
        bindings.values.removeAll { it.profileUid == profileUid && it.toolName == toolName && it.connectionUid == null }
    }

    override suspend fun deleteConnectionBindingsForType(profileUid: String, connectionType: String) {
        bindings.values.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.connectionUid?.let { connections[it]?.type == connectionType } == true
        }
    }

    override suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> = listBindingsByProfile(profileUid).map { binding ->
        AgentToolBindingWithConnection(binding, binding.connectionUid?.let(connections::get))
    }
}
