// filepath: /home/yw07/zjz/sherpa-onnx/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/StreamingRtfMetrics.kt
package com.k2fsa.sherpa.onnx.tts.engine

import java.util.Locale

/**
 * Data class to hold streaming RTF (Real-Time Factor) metrics
 */
data class StreamingRtfMetrics(
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkText: String,
    val audioDuration: Float,
    val processingTime: Long,
    val rtf: Float,
    val totalAudioDuration: Float = 0f,
    val totalProcessingTime: Long = 0L,
    val overallRtf: Float = 0f,
    val isComplete: Boolean = false
) {
    override fun toString(): String {
        return if (isComplete) {
            "═══════════════════════════════════\n" +
            "RTF Streaming Results (Complete)\n" +
            "═══════════════════════════════════\n" +
            "Total Audio Duration: ${String.format(Locale.US, "%.2f", totalAudioDuration)}s\n" +
            "Total Processing Time: ${totalProcessingTime}ms\n" +
            "Overall RTF: ${String.format(Locale.US, "%.2f", overallRtf)}\n" +
            "(RTF < 1.0 = faster than real-time)\n" +
            "═══════════════════════════════════"
        } else {
            "Chunk ${chunkIndex + 1}/${totalChunks}\n" +
            "Text: \"$chunkText\"\n" +
            "Audio Duration: ${String.format(Locale.US, "%.2f", audioDuration)}s\n" +
            "Processing Time: ${processingTime}ms\n" +
            "RTF: ${String.format(Locale.US, "%.2f", rtf)}"
        }
    }
}

/**
 * Global callback for streaming RTF metrics
 */
object StreamingRtfCallback {
    var onMetricsUpdate: ((StreamingRtfMetrics) -> Unit)? = null
}
