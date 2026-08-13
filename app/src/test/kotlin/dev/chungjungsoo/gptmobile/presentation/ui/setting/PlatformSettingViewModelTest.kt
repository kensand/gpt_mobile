package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSettingViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectSearchBackend none removes web search binding and closes dialog`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("search-1" to testConnection("search-1")),
            bindings = mutableListOf(testBinding("profile-1", "search-1"))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.openSearchBackendDialog()
        viewModel.selectSearchBackend(null)

        assertNull(viewModel.toolBindingState.value.selectedSearchConnectionUid)
        assertFalse(viewModel.toolBindingState.value.isSearchBackendDialogOpen)
        assertNull(viewModel.toolBindingState.value.errorMessage)
        assertEquals(emptyList<AgentToolBinding>(), dao.listBindingsByProfile("profile-1"))
    }

    @Test
    fun `loadToolBindings refreshes selected web search binding from repository`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf(
                "search-1" to testConnection("search-1"),
                "search-2" to testConnection("search-2")
            ),
            bindings = mutableListOf(testBinding("profile-1", "search-1"))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        dao.bindings.clear()
        dao.bindings += testBinding("profile-1", "search-2")
        viewModel.loadToolBindings()

        assertEquals("search-2", viewModel.toolBindingState.value.selectedSearchConnectionUid)
    }

    @Test
    fun `saving MCP tool selection binds only selected server tool`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.toggleMcpTool("mcp-1", "echo")
        viewModel.saveMcpTools()

        assertEquals(
            listOf("mcp-1:echo"),
            dao.listBindingsByProfile("profile-1").map { "${it.connectionUid}:${it.toolName}" }
        )
    }

    @Test
    fun `MCP tools named like builtins do not enable builtin bindings`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP)),
            bindings = mutableListOf(
                AgentToolBinding("mcp-web", "profile-1", "mcp-1", "web_search"),
                AgentToolBinding("mcp-read", "profile-1", "mcp-1", "read_url")
            )
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()

        assertNull(viewModel.toolBindingState.value.selectedSearchConnectionUid)
        assertFalse(viewModel.toolBindingState.value.readUrlEnabled)
        assertEquals(setOf("web_search", "read_url"), viewModel.toolBindingState.value.selectedMcpTools.map { it.toolName }.toSet())
    }

    @Test
    fun `closing MCP tools dialog cancels discovery loading state`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.openMcpToolsDialog()
        viewModel.closeMcpToolsDialog()

        assertFalse(viewModel.toolBindingState.value.isMcpToolsDialogOpen)
        assertFalse(viewModel.toolBindingState.value.isMcpToolsLoading)
    }

    private fun testViewModel(dao: FakeToolConnectionDao): PlatformSettingViewModel {
        val vault = FakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val resolver = AgentToolResolver(
            repository,
            vault,
            networkClient,
            manager,
            McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        )
        return PlatformSettingViewModel(
            settingRepository = FakeSettingRepository(),
            toolConnectionDao = dao,
            secretVault = vault,
            agentToolResolver = resolver,
            savedStateHandle = SavedStateHandle(mapOf("platformUid" to "profile-1"))
        )
    }

    private fun testConnection(
        connectionUid: String,
        type: String = ToolConnectionType.FIRECRAWL
    ): ToolConnection = ToolConnection(
        connectionUid = connectionUid,
        name = connectionUid,
        alias = connectionUid.replace("-", "_"),
        type = type,
        endpointUrl = "https://example.com",
        authType = ToolConnectionAuthType.BEARER,
        secretRef = null,
        oauthClientId = null
    )

    private fun testBinding(profileUid: String, connectionUid: String): AgentToolBinding = AgentToolBinding(
        bindingUid = "$profileUid:$connectionUid:web_search",
        profileUid = profileUid,
        connectionUid = connectionUid,
        toolName = "web_search"
    )
}

internal class FakeToolConnectionDao(
    val connections: MutableMap<String, ToolConnection> = mutableMapOf(),
    val bindings: MutableList<AgentToolBinding> = mutableListOf()
) : ToolConnectionDao {
    override suspend fun listConnections(): List<ToolConnection> = connections.values.toList()

    override suspend fun getConnection(connectionUid: String): ToolConnection? = connections[connectionUid]

    override suspend fun getConnectionsByUids(connectionUids: List<String>): List<ToolConnection> = connectionUids.mapNotNull(connections::get)

    override suspend fun upsertConnection(connection: ToolConnection) {
        connections[connection.connectionUid] = connection
    }

    override suspend fun deleteConnectionByUid(connectionUid: String) {
        connections.remove(connectionUid)
        bindings.removeAll { it.connectionUid == connectionUid }
    }

    override suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding> = bindings.filter { it.profileUid == profileUid }

    override suspend fun insertBinding(binding: AgentToolBinding) {
        bindings.removeAll { it.bindingUid == binding.bindingUid }
        bindings += binding
    }

    override suspend fun deleteConnectionToolBindingsForTypes(
        profileUid: String,
        toolName: String,
        connectionTypes: List<String>
    ) {
        bindings.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.toolName == toolName &&
                binding.connectionUid?.let { connections[it]?.type in connectionTypes } == true
        }
    }

    override suspend fun deleteBuiltInToolBinding(profileUid: String, toolName: String) {
        bindings.removeAll { it.profileUid == profileUid && it.toolName == toolName && it.connectionUid == null }
    }

    override suspend fun deleteConnectionBindingsForType(profileUid: String, connectionType: String) {
        bindings.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.connectionUid?.let { connections[it]?.type == connectionType } == true
        }
    }

    override suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> = listBindingsByProfile(profileUid).map { binding ->
        AgentToolBindingWithConnection(binding, binding.connectionUid?.let(connections::get))
    }
}

internal class FakeSecretVault : SecretVault {
    val values = mutableMapOf<String, ByteArray>()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)?.fill(0)
    }
}

private class FakeSettingRepository : SettingRepository {
    override suspend fun fetchPlatforms(): List<Platform> = emptyList()

    override suspend fun fetchPlatformV2s(): List<PlatformV2> = listOf(
        PlatformV2(
            uid = "profile-1",
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            enabled = true,
            apiUrl = "https://example.com",
            model = "gpt"
        )
    )

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()
    override suspend fun migrateToPlatformV2() = Unit
    override suspend fun migrateSecrets(): List<SecretMigrationError> = emptyList()
    override suspend fun updatePlatforms(platforms: List<Platform>) = Unit
    override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit
    override suspend fun addPlatformV2(platform: PlatformV2) = Unit
    override suspend fun updatePlatformV2(platform: PlatformV2) = Unit
    override suspend fun deletePlatformV2(platform: PlatformV2) = Unit
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null
}
