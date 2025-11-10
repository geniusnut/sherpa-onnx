package com.k2fsa.sherpa.onnx

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "WavAudioReader"

data class WavHeader(
    val sampleRate: Int,
    val numChannels: Int,
    val bitsPerSample: Int,
    val numSamples: Int,
    val dataStartPos: Long,
)

class WavAudioReader(private val filePath: String) {
    private var wavHeader: WavHeader? = null
    private var audioData: FloatArray? = null

    fun readWavFile(): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return false
            }

            val header = parseWavHeader(filePath)
            if (header == null) {
                Log.e(TAG, "Failed to parse WAV header")
                return false
            }

            wavHeader = header
            audioData = readAudioSamples(header)

            Log.i(TAG, "Successfully read WAV file")
            Log.i(TAG, "Sample rate: ${header.sampleRate}")
            Log.i(TAG, "Channels: ${header.numChannels}")
            Log.i(TAG, "Bits per sample: ${header.bitsPerSample}")
            Log.i(TAG, "Number of samples: ${header.numSamples}")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file: ${e.message}")
            false
        }
    }

    private fun parseWavHeader(filePath: String): WavHeader? {
        return try {
            val file = RandomAccessFile(filePath, "r")
            val buffer = ByteArray(12)

            // Read RIFF header
            file.read(buffer, 0, 12)
            val riffHeader = String(buffer, 0, 4)
            if (riffHeader != "RIFF") {
                Log.e(TAG, "Invalid RIFF header")
                file.close()
                return null
            }

            // Find fmt chunk
            var fmtFound = false
            var sampleRate = 0
            var numChannels = 0
            var bitsPerSample = 0

            var pos = 12
            file.seek(pos.toLong())
            val buffer2 = ByteArray(8)

            while (pos < file.length()) {
                file.seek(pos.toLong())
                if (file.read(buffer2, 0, 8) != 8) break

                val chunkId = String(buffer2, 0, 4)
                val chunkSize = bytesToInt(buffer2, 4)

                when (chunkId) {
                    "fmt " -> {
                        fmtFound = true
                        val fmtData = ByteArray(16)
                        file.read(fmtData)

                        numChannels = bytesToShort(fmtData, 2).toInt()
                        sampleRate = bytesToInt(fmtData, 4)
                        bitsPerSample = bytesToShort(fmtData, 14).toInt()

                        Log.i(TAG, "fmt chunk: channels=$numChannels, sampleRate=$sampleRate, bitsPerSample=$bitsPerSample")
                    }
                    "data" -> {
                        if (fmtFound) {
                            val numSamples = chunkSize / (numChannels * bitsPerSample / 8)
                            val dataStartPos = pos + 8
                            file.close()
                            return WavHeader(sampleRate, numChannels, bitsPerSample, numSamples, dataStartPos.toLong())
                        }
                    }
                }

                pos += 8 + chunkSize
            }

            file.close()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WAV header: ${e.message}")
            null
        }
    }

    private fun readAudioSamples(header: WavHeader): FloatArray {
        return try {
            val file = RandomAccessFile(filePath, "r")
            file.seek(header.dataStartPos)

            val audioData = FloatArray(header.numSamples)
            val bytesPerSample = header.bitsPerSample / 8

            when {
                header.bitsPerSample == 16 -> {
                    val buffer = ByteArray(2)
                    for (i in audioData.indices) {
                        file.read(buffer)
                        val sample = bytesToShort(buffer, 0).toInt()
                        audioData[i] = sample / 32768.0f
                    }
                }
                header.bitsPerSample == 8 -> {
                    val buffer = ByteArray(1)
                    for (i in audioData.indices) {
                        file.read(buffer)
                        val sample = (buffer[0].toInt() and 0xFF) - 128
                        audioData[i] = sample / 128.0f
                    }
                }
                else -> {
                    Log.e(TAG, "Unsupported bits per sample: ${header.bitsPerSample}")
                    file.close()
                    return FloatArray(0)
                }
            }

            file.close()
            audioData
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio samples: ${e.message}")
            FloatArray(0)
        }
    }

    fun getAudioData(): FloatArray? = audioData
    fun getHeader(): WavHeader? = wavHeader

    private fun bytesToShort(buffer: ByteArray, offset: Int): Short {
        return ((buffer[offset + 1].toInt() and 0xFF) shl 8 or (buffer[offset].toInt() and 0xFF)).toShort()
    }

    private fun bytesToInt(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset + 3].toInt() and 0xFF) shl 24 or
                ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                (buffer[offset].toInt() and 0xFF))
    }
}

