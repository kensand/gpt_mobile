package dev.chungjungsoo.gptmobile.presentation

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.backup.SanitizedChatBackup
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class GPTMobileApp : Application() {
    // TODO Delete when https://github.com/google/dagger/issues/3601 is resolved.
    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var agentRunDao: AgentRunDao

    @Inject
    lateinit var agentPersistenceDao: AgentPersistenceDao

    @Inject
    lateinit var settingRepository: SettingRepository

    @Volatile
    var secretMigrationErrors: List<SecretMigrationError> = emptyList()
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        SanitizedChatBackup.restoreIfPresent(this)
        super.onCreate()
        registerActivityLifecycleCallbacks(AppForegroundTracker)
        StartupRecoveryGate.start(applicationScope) {
            secretMigrationErrors = runStartupMaintenance(
                interruptPersistedWork = {
                    val interruptedAt = System.currentTimeMillis() / 1000
                    agentRunDao.interruptActiveRuns(interruptedAt)
                    agentPersistenceDao.cancelInterruptedToolEvents(interruptedAt)
                },
                migrateSecrets = settingRepository::migrateSecrets
            )
            if (secretMigrationErrors.isNotEmpty()) {
                runCatching {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@GPTMobileApp,
                            R.string.credential_migration_warning,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Unable to show credential migration warning.", error)
                }
            }
        }
    }

    private companion object {
        const val TAG = "GPTMobileApp"
    }
}

object StartupRecoveryGate {
    @Volatile
    private var job: Job? = null

    fun start(scope: CoroutineScope, block: suspend () -> Unit) {
        job = scope.launch { block() }
    }

    suspend fun await() {
        job?.join()
    }
}

internal suspend fun runStartupMaintenance(
    interruptPersistedWork: suspend () -> Unit,
    migrateSecrets: suspend () -> List<SecretMigrationError>
): List<SecretMigrationError> {
    interruptPersistedWork()
    return try {
        migrateSecrets()
    } catch (error: Exception) {
        listOf(SecretMigrationError("startup", error.message ?: "Credential migration failed."))
    }
}
