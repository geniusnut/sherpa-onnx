package com.k2fsa.sherpa.onnx.vad

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.k2fsa.sherpa.onnx.R
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.WavAudioPlayer
import com.k2fsa.sherpa.onnx.WavAudioPlayerListener
import com.k2fsa.sherpa.onnx.WavAudioReader
import com.k2fsa.sherpa.onnx.getVadModelConfig
import java.util.Locale
import kotlin.concurrent.thread


private const val TAG = "sherpa-onnx"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val REQUEST_READ_AUDIO_PERMISSION = 201
private const val REQUEST_PICK_AUDIO_FILE = 1001

class MainActivity : AppCompatActivity(), WavAudioPlayerListener {

    private lateinit var recordButton: Button
    private lateinit var selectWavButton: Button
    private lateinit var playWavButton: Button
    private lateinit var stopWavButton: Button
    private lateinit var circle: View
    private lateinit var modeIndicator: TextView
    private lateinit var vadStatus: TextView
    private lateinit var playbackProgress: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView

    private lateinit var vad: Vad

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private val audioPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @Volatile
    private var isRecording: Boolean = false

    private var wavAudioReader: WavAudioReader? = null
    private var wavAudioPlayer: WavAudioPlayer? = null
    private var selectedWavFilePath: String? = null
    private var currentWavIndex: Int = 0
    private var wavSamples: FloatArray? = null
    private var playingWavThread: Thread? = null

    @Volatile
    private var isPlayingWav: Boolean = false

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                val permissionToRecordAccepted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (!permissionToRecordAccepted) {
                    Log.e(TAG, "Audio record is disallowed")
                    finish()
                } else {
                    Log.i(TAG, "Audio record is permitted")
                }
            }
            REQUEST_READ_AUDIO_PERMISSION -> {
                val permissionGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (permissionGranted) {
                    pickAudioFile()
                } else {
                    Log.e(TAG, "Read audio permission denied")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        Log.i(TAG, "Start to initialize model")
        initVadModel()
        Log.i(TAG, "Finished initializing model")

        circle = findViewById(R.id.powerCircle)
        modeIndicator = findViewById(R.id.mode_indicator)
        vadStatus = findViewById(R.id.vad_status)
        playbackProgress = findViewById(R.id.playback_progress)
        currentTimeText = findViewById(R.id.current_time)
        totalTimeText = findViewById(R.id.total_time)

        recordButton = findViewById(R.id.record_button)
        recordButton.setOnClickListener { onRecordClick() }

        selectWavButton = findViewById(R.id.select_wav_button)
        selectWavButton.setOnClickListener { onSelectWavClick() }

        playWavButton = findViewById(R.id.play_wav_button)
        playWavButton.setOnClickListener { onPlayWavClick() }

        stopWavButton = findViewById(R.id.stop_wav_button)
        stopWavButton.setOnClickListener { onStopWavClick() }

        playbackProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isPlayingWav) {
                    wavAudioPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun onRecordClick() {
        if (!isRecording) {
            val ret = initMicrophone()
            if (!ret) {
                Log.e(TAG, "Failed to initialize microphone")
                return
            }
            Log.i(TAG, "state: ${audioRecord?.state}")
            audioRecord!!.startRecording()
            recordButton.setText(R.string.stop)
            isRecording = true

            modeIndicator.text = "Recording from Microphone"
            vad.reset()
            recordingThread = thread(true) {
                processMicrophoneSamples()
            }
            Log.i(TAG, "Started recording")
            onVad(false)

        } else {
            isRecording = false

            audioRecord!!.stop()
            audioRecord!!.release()
            audioRecord = null

            recordButton.setText(R.string.start)
            onVad(false)
            vadStatus.text = "Stopped"
            Log.i(TAG, "Stopped recording")
        }
    }

    private fun onSelectWavClick() {
        if (ActivityCompat.checkSelfPermission(this, audioPermissions[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, audioPermissions, REQUEST_READ_AUDIO_PERMISSION)
            return
        }
        pickAudioFile()
    }

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/*"
        startActivityForResult(intent, REQUEST_PICK_AUDIO_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_AUDIO_FILE && resultCode == RESULT_OK && data != null) {
            val uri: Uri? = data.data
            if (uri != null) {
                selectedWavFilePath = getRealPathFromURI(uri)
                if (selectedWavFilePath != null) {
                    Log.i(TAG, "Selected file: $selectedWavFilePath")
                    loadWavFile(selectedWavFilePath!!)
                }
            }
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val columnIndex = it.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                if (columnIndex != -1) {
                    it.moveToFirst()
                    it.getString(columnIndex)
                } else {
                    uri.path
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path: ${e.message}")
            uri.path
        }
    }

    private fun loadWavFile(filePath: String) {
        try {
            wavAudioReader = WavAudioReader(filePath)
            if (wavAudioReader!!.readWavFile()) {
                val header = wavAudioReader!!.getHeader()
                wavSamples = wavAudioReader!!.getAudioData()

                Log.i(TAG, "WAV file loaded: $filePath")
                Log.i(TAG, "Sample rate: ${header?.sampleRate}, Channels: ${header?.numChannels}, Samples: ${header?.numSamples}")

                playWavButton.isEnabled = true
                vadStatus.text = "WAV loaded, ready to play"

                if (wavAudioPlayer != null) {
                    wavAudioPlayer!!.release()
                }
                wavAudioPlayer = WavAudioPlayer(filePath, this)
                if (wavAudioPlayer!!.prepare()) {
                    val duration = wavAudioPlayer!!.getDuration()
                    playbackProgress.max = duration
                    totalTimeText.text = formatTime(duration)
                    playbackProgress.visibility = View.VISIBLE
                }
            } else {
                Log.e(TAG, "Failed to load WAV file")
                vadStatus.text = "Failed to load WAV file"
                playWavButton.isEnabled = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading WAV file: ${e.message}")
            vadStatus.text = "Error: ${e.message}"
            playWavButton.isEnabled = false
        }
    }

    private fun onPlayWavClick() {
        if (wavSamples == null) {
            Log.e(TAG, "No WAV samples loaded")
            return
        }

        if (isPlayingWav) {
            wavAudioPlayer?.pause()
            isPlayingWav = false
            playWavButton.text = "Play WAV"
            stopWavButton.isEnabled = true
            return
        }

        // Start playback
        if (currentWavIndex == 0) {
            if (wavAudioPlayer?.play() == false) {
                Log.e(TAG, "Failed to play WAV")
                return
            }
        } else {
            wavAudioPlayer?.play()
        }

        isPlayingWav = true
        playWavButton.text = "Pause WAV"
        stopWavButton.isEnabled = true
        modeIndicator.text = "Playing WAV File"
        vad.reset()
        currentWavIndex = 0

        playingWavThread = thread(true) {
            processWavSamples()
        }
        Log.i(TAG, "Started WAV playback with VAD analysis")
    }

    private fun onStopWavClick() {
        isPlayingWav = false
        wavAudioPlayer?.stop()
        wavAudioPlayer?.release()
        playWavButton.text = "Play WAV"
        playWavButton.isEnabled = true
        stopWavButton.isEnabled = false
        onVad(false)
        vadStatus.text = "Stopped"
        currentWavIndex = 0
        playbackProgress.progress = 0
        currentTimeText.text = "0:00"
        Log.i(TAG, "Stopped WAV playback")
    }

    private fun onVad(isSpeech: Boolean) {
        if (isSpeech) {
            circle.background = resources.getDrawable(R.drawable.red_circle)
            vadStatus.text = "SPEECH DETECTED"
        } else {
            circle.background = resources.getDrawable(R.drawable.black_circle)
            if (vadStatus.text != "Stopped" && vadStatus.text != "No activity") {
                vadStatus.text = "No speech"
            }
        }
    }

    private fun initVadModel() {
        val type = 1
        Log.i(TAG, "Select VAD model type $type")
        val config = getVadModelConfig(type)

        vad = Vad(
            assetManager = application.assets,
            config = config!!,
        )
    }

    private fun initMicrophone(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
            return false
        }

        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        Log.i(
            TAG, "buffer size in milliseconds: ${numBytes * 1000.0f / sampleRateInHz}"
        )

        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2
        )
        return true
    }

    private fun processMicrophoneSamples() {
        Log.i(TAG, "processing microphone samples")

        val bufferSize = 512
        val buffer = ShortArray(bufferSize)

        while (isRecording) {
            val ret = audioRecord?.read(buffer, 0, buffer.size)
            if (ret != null && ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }

                vad.acceptWaveform(samples)

                val isSpeechDetected = vad.isSpeechDetected()
                vad.clear()

                runOnUiThread {
                    onVad(isSpeechDetected)
                }
            }
        }
    }

    private fun processWavSamples() {
        Log.i(TAG, "processing WAV samples")

        val bufferSize = 512
        val wavSamples = wavSamples ?: return

        val sampleDurationMs = (bufferSize * 1000.0f) / sampleRateInHz

        while (isPlayingWav && currentWavIndex < wavSamples.size) {
            val endIndex = (currentWavIndex + bufferSize).coerceAtMost(wavSamples.size)
            val buffer = wavSamples.sliceArray(currentWavIndex until endIndex)

            if (buffer.isNotEmpty()) {
                vad.acceptWaveform(buffer)

                val isSpeechDetected = vad.isSpeechDetected()
                vad.clear()

                runOnUiThread {
                    onVad(isSpeechDetected)
                }
            }

            currentWavIndex = endIndex
            Thread.sleep(sampleDurationMs.toLong())
        }

        if (currentWavIndex >= wavSamples.size) {
            isPlayingWav = false
            runOnUiThread {
                onPlaybackFinished()
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    // WavAudioPlayerListener implementation
    override fun onPlaybackProgress(currentPosition: Int, duration: Int) {
        runOnUiThread {
            playbackProgress.progress = currentPosition
            currentTimeText.text = formatTime(currentPosition)
        }
    }

    override fun onPlaybackFinished() {
        Log.i(TAG, "WAV playback finished")
        isPlayingWav = false
        playWavButton.text = "Play WAV"
        playWavButton.isEnabled = true
        stopWavButton.isEnabled = false
        onVad(false)
        vadStatus.text = "Playback finished"
        currentWavIndex = 0
        playbackProgress.progress = 0
        currentTimeText.text = "0:00"
    }

    override fun onPlaybackError(error: String) {
        Log.e(TAG, "Playback error: $error")
        runOnUiThread {
            vadStatus.text = "Error: $error"
            isPlayingWav = false
            playWavButton.text = "Play WAV"
        }
    }
}

