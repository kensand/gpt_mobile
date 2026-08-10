package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
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
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
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

    private fun testViewModel(dao: FakeToolConnectionDao): PlatformSettingViewModel = PlatformSettingViewModel(
        settingRepository = FakeSettingRepository(),
        toolConnectionDao = dao,
        secretVault = FakeSecretVault(),
        savedStateHandle = SavedStateHandle(mapOf("platformUid" to "profile-1"))
    )

    private fun testConnection(connectionUid: String): ToolConnection = ToolConnection(
        connectionUid = connectionUid,
        name = connectionUid,
        alias = connectionUid.replace("-", "_"),
        type = ToolConnectionType.FIRECRAWL,
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

private class FakeToolConnectionDao(
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

private class FakeSecretVault : SecretVault {
    override suspend fun put(secretRef: String, secret: ByteArray) = Unit
    override suspend fun read(secretRef: String): ByteArray? = null
    override suspend fun delete(secretRef: String) = Unit
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
