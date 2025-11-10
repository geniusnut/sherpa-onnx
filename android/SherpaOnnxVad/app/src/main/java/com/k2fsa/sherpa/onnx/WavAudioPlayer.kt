package com.k2fsa.sherpa.onnx

import android.media.MediaPlayer
import android.util.Log

private const val TAG = "WavAudioPlayer"

interface WavAudioPlayerListener {
    fun onPlaybackProgress(currentPosition: Int, duration: Int)
    fun onPlaybackFinished()
    fun onPlaybackError(error: String)
}

class WavAudioPlayer(private val filePath: String, private val listener: WavAudioPlayerListener? = null) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying: Boolean = false
    private var progressThread: Thread? = null

    fun prepare(): Boolean {
        return try {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(filePath)
            mediaPlayer?.prepare()
            Log.i(TAG, "MediaPlayer prepared for $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing MediaPlayer: ${e.message}")
            listener?.onPlaybackError(e.message ?: "Unknown error")
            false
        }
    }

    fun play(): Boolean {
        return try {
            if (mediaPlayer == null) {
                Log.e(TAG, "MediaPlayer not prepared")
                return false
            }

            mediaPlayer?.start()
            isPlaying = true
            Log.i(TAG, "Playback started")

            // Start progress tracking thread
            startProgressTracking()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback: ${e.message}")
            listener?.onPlaybackError(e.message ?: "Unknown error")
            false
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isPlaying = false
                Log.i(TAG, "Playback paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback: ${e.message}")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            isPlaying = false
            Log.i(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            Log.i(TAG, "Seek to $position ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking: ${e.message}")
        }
    }

    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            Log.i(TAG, "MediaPlayer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        }
    }

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressThread = Thread {
            try {
                while (isPlaying && mediaPlayer != null) {
                    try {
                        val current = mediaPlayer?.currentPosition ?: 0
                        val duration = mediaPlayer?.duration ?: 0
                        listener?.onPlaybackProgress(current, duration)

                        if (current >= duration - 100) { // Near the end
                            isPlaying = false
                            listener?.onPlaybackFinished()
                            break
                        }

                        Thread.sleep(100) // Update every 100ms
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            } finally {
                progressThread = null
            }
        }
        progressThread?.start()
    }

    private fun stopProgressTracking() {
        progressThread?.interrupt()
        progressThread = null
    }
}

