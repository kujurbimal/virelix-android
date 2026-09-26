package com.bimal.clipforge

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object KeyframeExtractor {

    fun extractBase64(
        context: Context,
        uri: Uri,
        count: Int = 12
    ): List<String> {
        val result = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return emptyList()

            val frameCount = count.coerceIn(4, 20)

            for (index in 0 until frameCount) {
                val fraction = if (frameCount == 1) 0.0 else {
                    index.toDouble() / (frameCount - 1).toDouble()
                }
                val timeUs = (durationMs * 1000L * fraction).toLong()

                val bitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                try {
                    val bytes = ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output)
                        output.toByteArray()
                    }
                    result += Base64.encodeToString(bytes, Base64.NO_WRAP)
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            retriever.release()
        }

        return result
    }
}
