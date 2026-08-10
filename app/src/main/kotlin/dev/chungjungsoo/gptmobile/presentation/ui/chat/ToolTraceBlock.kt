package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import java.time.Instant

private const val TOOL_TRACE_TEXT_LIMIT = 1024

@Composable
fun ToolTraceBlock(
    events: List<ToolEvent>,
    modifier: Modifier = Modifier,
    contentIdentity: Any = events
) {
    if (events.isEmpty()) return

    var isExpanded by remember(contentIdentity) { mutableStateOf(false) }
    var query by remember(contentIdentity) { mutableStateOf("") }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "tool trace rotation"
    )
    val summary = toolTraceStatusSummary(events)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .semantics { contentDescription = "Tool trace block, $summary" }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .semantics {
                    role = Role.Button
                    contentDescription = if (isExpanded) "Collapse tool trace" else "Expand tool trace"
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotationAngle)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            key(contentIdentity) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search tool trace") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Search tool trace" }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    filterToolEvents(events, query).forEach { event ->
                        ToolTraceEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTraceEventCard(event: ToolEvent) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = "Tool call ${event.callId}, ${event.status.lowercase()}" }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${event.sequence + 1}. ${event.toolName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (event.modelToolName != event.toolName) {
                ToolTraceLine("Model tool", event.modelToolName)
            }
            ToolTraceLine("Status", event.status)
            ToolTraceLine("Call ID", event.callId)
            connectionLabel(event)?.let { ToolTraceLine("Connection", it) }
            timingLabel(event)?.let { ToolTraceLine("Timing", it) }
            event.error?.takeIf { it.isNotBlank() }?.let { ToolTraceLine("Error", boundedText(it)) }
            ToolTraceBlockText("Arguments", event.arguments)
            event.result?.takeIf { it.isNotBlank() }?.let { ToolTraceBlockText("Result", it) }
        }
    }
}

@Composable
private fun ToolTraceLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ToolTraceBlockText(label: String, value: String) {
    Text(
        text = "$label:",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        text = boundedText(value),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun filterToolEvents(events: List<ToolEvent>, query: String): List<ToolEvent> {
    val ordered = events.sortedBy { it.sequence }
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return ordered

    return ordered.filter { event ->
        listOfNotNull(
            event.connectionUidSnapshot,
            event.connectionNameSnapshot,
            event.toolName,
            event.modelToolName,
            event.callId,
            event.arguments,
            event.result,
            event.error
        ).any { normalizedQuery in it.lowercase() }
    }
}

internal fun toolTraceStatusSummary(events: List<ToolEvent>): String {
    val count = events.size
    val noun = if (count == 1) "tool call" else "tool calls"
    if (events.isEmpty()) return "0 $noun"

    val status = when {
        events.any { it.status == ToolEventStatus.FAILED || it.isError } -> "failed"
        events.any { it.status == ToolEventStatus.RUNNING || it.status == ToolEventStatus.PENDING } -> "running"
        events.any { it.status == ToolEventStatus.CANCELED } -> "canceled"
        else -> "completed"
    }
    return "$count $noun - $status"
}

internal fun formatToolDuration(event: ToolEvent): String? {
    val startedAt = event.startedAt ?: return null
    val completedAt = event.completedAt ?: return null
    return "${(completedAt - startedAt).coerceAtLeast(0)} s"
}

internal fun formatToolTraceMarkdown(events: List<ToolEvent>): String {
    if (events.isEmpty()) return ""

    return buildString {
        appendLine("## Tool calls (${events.size})")
        filterToolEvents(events, "").forEach { event ->
            appendLine()
            appendLine("### ${event.sequence + 1}. ${event.toolName}")
            appendLine("- Status: ${event.status}")
            appendLine("- Call ID: ${event.callId}")
            connectionLabel(event)?.let { appendLine("- Connection: $it") }
            appendLine("- Tool: ${event.toolName}")
            if (event.modelToolName != event.toolName) appendLine("- Model tool: ${event.modelToolName}")
            timingLabel(event)?.let { appendLine("- Timing: $it") }
            event.error?.takeIf { it.isNotBlank() }?.let { appendLine("- Error: ${boundedText(it)}") }
            appendIndentedBlock("Arguments", event.arguments)
            event.result?.takeIf { it.isNotBlank() }?.let { appendIndentedBlock("Result", it) }
        }
    }.trimEnd()
}

private fun StringBuilder.appendIndentedBlock(label: String, value: String) {
    appendLine("- $label:")
    boundedText(value).lineSequence().forEach { line ->
        appendLine("    $line")
    }
}

private fun timingLabel(event: ToolEvent): String? {
    val startedAt = event.startedAt
    val completedAt = event.completedAt
    return when {
        startedAt != null && completedAt != null -> "${Instant.ofEpochSecond(startedAt)} - ${Instant.ofEpochSecond(completedAt)} (${formatToolDuration(event)})"
        startedAt != null -> "started at ${Instant.ofEpochSecond(startedAt)}"
        else -> null
    }
}

private fun connectionLabel(event: ToolEvent): String? {
    val name = event.connectionNameSnapshot?.takeIf { it.isNotBlank() }
    val uid = event.connectionUidSnapshot?.takeIf { it.isNotBlank() }
    return when {
        name != null && uid != null -> "$name ($uid)"
        name != null -> name
        uid != null -> uid
        else -> null
    }
}

private fun boundedText(value: String): String {
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.length <= TOOL_TRACE_TEXT_LIMIT) return normalized
    return normalized.take(TOOL_TRACE_TEXT_LIMIT) + "..."
}
