package com.bimal.clipforge

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class AiVisionService {
    private val functions = FirebaseFunctions.getInstance()
    suspend fun analyze(frameBase64Jpegs: List<String>, transcript: String, style: String): Map<String, Any?> {
        require(frameBase64Jpegs.isNotEmpty() && frameBase64Jpegs.size <= 20)
        val payload = hashMapOf("frames" to frameBase64Jpegs, "transcript" to transcript, "style" to style)
        @Suppress("UNCHECKED_CAST")
        return functions.getHttpsCallable("analyzeVideoFrames").call(payload).await().data as Map<String, Any?>
    }
}
