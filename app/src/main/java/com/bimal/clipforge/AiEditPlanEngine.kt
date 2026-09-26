package com.bimal.clipforge

data class AiEditPlan(
    val summary: String = "",
    val hook: String = "",
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val clipOrder: List<Int> = emptyList(),
    val suggestedDurationsMs: List<Long> = emptyList(),
    val transition: String = "Cut",
    val musicEnergy: String = "medium"
)

object AiEditPlanEngine {

    fun parse(data: Map<*, *>): AiEditPlan {
        fun intList(key: String): List<Int> =
            (data[key] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toInt() }
                ?: emptyList()

        fun longList(key: String): List<Long> =
            (data[key] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toLong() }
                ?: emptyList()

        fun stringList(key: String): List<String> =
            (data[key] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                ?: emptyList()

        return AiEditPlan(
            summary = data["summary"]?.toString().orEmpty(),
            hook = data["hook"]?.toString().orEmpty(),
            caption = data["caption"]?.toString().orEmpty(),
            hashtags = stringList("hashtags"),
            clipOrder = intList("clipOrder"),
            suggestedDurationsMs = longList("suggestedDurationsMs"),
            transition = data["transition"]?.toString() ?: "Cut",
            musicEnergy = data["musicEnergy"]?.toString() ?: "medium"
        )
    }

    fun reorderAndTrim(
        clips: List<Clip>,
        plan: AiEditPlan
    ): List<Clip> {
        if (clips.isEmpty()) return emptyList()

        val ordered = if (plan.clipOrder.isEmpty()) {
            clips
        } else {
            plan.clipOrder.mapNotNull(clips::getOrNull).ifEmpty { clips }
        }

        return ordered.mapIndexed { index, clip ->
            val requested = plan.suggestedDurationsMs
                .getOrNull(index)
                ?: (clip.endMs - clip.startMs)

            val duration = requested
                .coerceAtLeast(300L)
                .coerceAtMost(clip.endMs - clip.startMs)

            clip.copy(endMs = clip.startMs + duration)
        }
    }
}
