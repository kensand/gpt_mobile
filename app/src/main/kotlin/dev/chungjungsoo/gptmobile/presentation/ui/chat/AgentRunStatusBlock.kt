package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus

@Composable
fun AgentRunStatusBlock(run: AgentRun?, modifier: Modifier = Modifier) {
    if (run == null || run.status == AgentRunStatus.COMPLETED) return

    val status = when (run.status) {
        AgentRunStatus.QUEUED -> stringResource(R.string.agent_run_queued)
        AgentRunStatus.RUNNING -> stringResource(R.string.agent_run_running)
        AgentRunStatus.CANCELED -> stringResource(R.string.agent_run_canceled)
        AgentRunStatus.INTERRUPTED -> stringResource(R.string.agent_run_interrupted)
        else -> stringResource(R.string.agent_run_failed)
    }
    val duration = agentRunDurationSeconds(run)?.let { " · ${it}s" }.orEmpty()

    Card(
        modifier = modifier.semantics { contentDescription = status },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (run.status == AgentRunStatus.QUEUED || run.status == AgentRunStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = status + duration,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            run.terminalError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

internal fun agentRunDurationSeconds(run: AgentRun): Long? {
    val startedAt = run.startedAt ?: return null
    val completedAt = run.completedAt ?: return null
    return (completedAt - startedAt).coerceAtLeast(0)
}
