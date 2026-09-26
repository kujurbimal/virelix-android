package com.bimal.clipforge

data class FinalScene(
    val startMs: Long,
    val endMs: Long,
    val score: Float = 0f,
    val label: String = "scene"
)

data class FinalCaption(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class FinalBeat(val timeMs: Long, val strength: Float = 1f)

data class FinalEditPlan(
    val summary: String = "",
    val hook: String = "",
    val title: String = "",
    val description: String = "",
    val hashtags: List<String> = emptyList(),
    val scenes: List<FinalScene> = emptyList(),
    val captions: List<FinalCaption> = emptyList(),
    val beats: List<FinalBeat> = emptyList(),
    val transition: String = "Cut",
    val musicEnergy: String = "medium"
)

object FinalAiEditEngine {

    fun applyExactSceneRanges(
        scenes: List<FinalScene>,
        minimumMs: Long = 500L
    ): List<FinalScene> =
        scenes
            .filter { it.endMs > it.startMs }
            .map {
                it.copy(
                    startMs = it.startMs.coerceAtLeast(0L),
                    endMs = it.endMs.coerceAtLeast(it.startMs + minimumMs)
                )
            }
            .sortedBy { it.startMs }

    fun snapToNearestBeat(timeMs: Long, beats: List<FinalBeat>, toleranceMs: Long = 180L): Long {
        if (beats.isEmpty()) return timeMs
        val nearest = beats.minByOrNull { kotlin.math.abs(it.timeMs - timeMs) } ?: return timeMs
        return if (kotlin.math.abs(nearest.timeMs - timeMs) <= toleranceMs) nearest.timeMs else timeMs
    }

    fun beatSyncScenes(
        scenes: List<FinalScene>,
        beats: List<FinalBeat>
    ): List<FinalScene> =
        scenes.map { s ->
            val start = snapToNearestBeat(s.startMs, beats)
            val end = snapToNearestBeat(s.endMs, beats)
            if (end > start) s.copy(startMs = start, endMs = end) else s
        }

    fun safeCaptions(captions: List<FinalCaption>): List<FinalCaption> =
        captions
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
            .map {
                it.copy(
                    startMs = it.startMs.coerceAtLeast(0L),
                    endMs = it.endMs.coerceAtLeast(it.startMs + 250L),
                    text = it.text.trim().take(140)
                )
            }
            .sortedBy { it.startMs }
}
