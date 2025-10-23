package com.k2fsa.sherpa.onnx.tts.engine

import java.io.File
import java.nio.ByteBuffer

object WavFileUtil {
    fun readWavFloat(file: File): Pair<FloatArray, Int> {
        // Minimal implementation: assumes 16-bit PCM, mono or stereo, little-endian
        val input = file.inputStream().buffered()
        val header = ByteArray(44)
        input.read(header)
        val sampleRate = java.nio.ByteBuffer.wrap(header, 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val numChannels = java.nio.ByteBuffer.wrap(header, 22, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
        val bitsPerSample = java.nio.ByteBuffer.wrap(header, 34, 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
        require(bitsPerSample == 16) { "Only 16-bit PCM supported" }
        val dataSize = java.nio.ByteBuffer.wrap(header, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val numSamples = dataSize / 2 / numChannels
        val audio = ByteArray(dataSize)
        input.read(audio)
        input.close()
        val floatSamples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            var sum = 0f
            for (ch in 0 until numChannels) {
                val idx = (i * numChannels + ch) * 2
                val sample = ((audio[idx + 1].toInt() shl 8) or (audio[idx].toInt() and 0xFF)).toShort()
                sum += sample / 32768.0f
            }
            floatSamples[i] = sum / numChannels
        }
        return floatSamples to sampleRate
    }
}