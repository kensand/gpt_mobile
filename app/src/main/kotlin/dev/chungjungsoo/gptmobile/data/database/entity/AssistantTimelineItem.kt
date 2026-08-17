package dev.chungjungsoo.gptmobile.data.database.entity

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantTimelineItemType {
    THINKING,
    TEXT,
    TOOL,
    LEGACY_ORDER
}

@Serializable
data class AssistantTimelineItem(
    val type: AssistantTimelineItemType,
    val content: String = "",
    val toolSequence: Int? = null
)

internal const val LEGACY_ORDER_NOTICE =
    "Original event order is unavailable for this response. Reasoning, text, and tool calls are preserved below without implying chronology."

internal fun rebuildAssistantTimelineForEdit(
    currentTimeline: List<AssistantTimelineItem>,
    updatedContent: String,
    updatedThoughts: String,
    hasToolTrace: Boolean = currentTimeline.any { it.type == AssistantTimelineItemType.TOOL }
): List<AssistantTimelineItem> {
    val toolMarkers = currentTimeline.filter { it.type == AssistantTimelineItemType.TOOL }
    val chronologyUnavailable = hasToolTrace ||
        toolMarkers.isNotEmpty() ||
        currentTimeline.any { it.type == AssistantTimelineItemType.LEGACY_ORDER }
    if (chronologyUnavailable) {
        return listOf(AssistantTimelineItem(AssistantTimelineItemType.LEGACY_ORDER)) + toolMarkers
    }

    return buildList {
        updatedThoughts.takeIf(String::isNotBlank)?.let {
            add(AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = it))
        }
        updatedContent.takeIf(String::isNotBlank)?.let {
            add(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = it))
        }
    }
}

internal fun List<AssistantTimelineItem>.appendChronologicalText(text: String): List<AssistantTimelineItem> {
    if (text.isEmpty()) return this
    val last = lastOrNull()
    return if (last?.type == AssistantTimelineItemType.TEXT && last.toolSequence == null) {
        dropLast(1) + last.copy(content = last.content + text)
    } else {
        this + AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = text)
    }
}

internal fun hasUnavailableAssistantOrder(
    timeline: List<AssistantTimelineItem>,
    content: String,
    thoughts: String,
    hasToolEvents: Boolean
): Boolean = timeline.any { it.type == AssistantTimelineItemType.LEGACY_ORDER } ||
    (timeline.isEmpty() && (hasToolEvents || (content.isNotBlank() && thoughts.isNotBlank())))
