// filepath: /home/yw07/zjz/sherpa-onnx/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TextSplitter.kt
package com.k2fsa.sherpa.onnx.tts.engine

import android.util.Log

/**
 * Utility class for splitting text into smaller pieces for streaming TTS
 */
object TextSplitter {
    private const val TAG = "TextSplitter"

    // Sentence delimiters for both English and Chinese
    private val sentenceDelimiters = setOf('。', '！', '？', '.', '!', '?', ';', '；')
    private val phraseDelimiters = setOf('，', ',', '、', '·')

    /**
     * Split text into sentences
     * @param text The input text to split
     * @return List of sentences
     */
    fun splitBySentence(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        var currentSentence = StringBuilder()

        for (char in text) {
            currentSentence.append(char)
            if (char in sentenceDelimiters) {
                val sentence = currentSentence.toString().trim()
                if (sentence.isNotEmpty()) {
                    sentences.add(sentence)
                }
                currentSentence = StringBuilder()
            }
        }

        // Add remaining text if any
        val remaining = currentSentence.toString().trim()
        if (remaining.isNotEmpty()) {
            sentences.add(remaining)
        }

        Log.d(TAG, "Split text into ${sentences.size} sentences")
        return sentences
    }

    /**
     * Split text into phrases/chunks
     * @param text The input text to split
     * @param maxLength Maximum length per chunk (default 100 characters)
     * @return List of phrases/chunks
     */
    fun splitByPhrase(text: String, maxLength: Int = 100): List<String> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (char in text) {
            currentChunk.append(char)

            // Check if we should split
            if (currentChunk.length >= maxLength || char in sentenceDelimiters) {
                val chunk = currentChunk.toString().trim()
                if (chunk.isNotEmpty()) {
                    chunks.add(chunk)
                }
                currentChunk = StringBuilder()
            } else if (char in phraseDelimiters && currentChunk.length > 20) {
                // Also split at phrase delimiters if we have enough content
                val chunk = currentChunk.toString().trim()
                if (chunk.isNotEmpty()) {
                    chunks.add(chunk)
                }
                currentChunk = StringBuilder()
            }
        }

        // Add remaining text
        val remaining = currentChunk.toString().trim()
        if (remaining.isNotEmpty()) {
            chunks.add(remaining)
        }

        Log.d(TAG, "Split text into ${chunks.size} phrases (maxLength: $maxLength)")
        return chunks
    }

    /**
     * Intelligently split text into streaming chunks
     * Prioritizes sentences, then phrases, then length-based splitting
     * @param text The input text
     * @param maxChunkLength Maximum length for each chunk
     * @return List of text chunks optimized for streaming
     */
    fun splitForStreaming(text: String, maxChunkLength: Int = 50): List<String> {
        if (text.isBlank()) return emptyList()

        // First, try to split by sentences
        val sentences = splitBySentence(text)

        val streamChunks = mutableListOf<String>()

        for (sentence in sentences) {
            if (sentence.length <= maxChunkLength) {
                streamChunks.add(sentence)
            } else {
                // If sentence is too long, split into phrases
                val phrases = splitByPhrase(sentence, maxChunkLength)
                streamChunks.addAll(phrases)
            }
        }

        Log.d(TAG, "Split text into ${streamChunks.size} streaming chunks")
        return streamChunks
    }
}
