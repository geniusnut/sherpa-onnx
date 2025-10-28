@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import PreferenceHelper
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.tts.engine.ui.theme.SherpaOnnxTtsEngineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.TimeSource

const val TAG = "sherpa-onnx-tts-engine"

class MainActivity : ComponentActivity() {
    // TODO(fangjun): Save settings in ttsViewModel
    private val ttsViewModel: TtsViewModel by viewModels()

    private var mediaPlayer: MediaPlayer? = null

    // see
    // https://developer.android.com/reference/kotlin/android/media/AudioTrack
    private lateinit var track: AudioTrack

    private var stopped: Boolean = false

    private var samplesChannel = Channel<FloatArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Start to initialize TTS")
        TtsEngine.createTts(this)
        Log.i(TAG, "Finish initializing TTS")

        Log.i(TAG, "Start to initialize AudioTrack")
        initAudioTrack()
        Log.i(TAG, "Finish initializing AudioTrack")

        val preferenceHelper = PreferenceHelper(this)
        setContent {
            SherpaOnnxTtsEngineTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(topBar = {
                        TopAppBar(title = { Text("Next-gen Kaldi: TTS Engine") })
                    }) {
                        Box(modifier = Modifier.padding(it)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Column {
                                    Text("Speed " + String.format("%.1f", TtsEngine.speed))
                                    Slider(
                                        value = TtsEngine.speedState.value,
                                        onValueChange = {
                                            TtsEngine.speed = it
                                            preferenceHelper.setSpeed(it)
                                        },
                                        valueRange = 0.2F..3.0F,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                val testTextContent = getSampleText(TtsEngine.lang ?: "")

                                var testText by remember { mutableStateOf(testTextContent) }
                                var startEnabled by remember { mutableStateOf(true) }
                                var playEnabled by remember { mutableStateOf(false) }
                                var rtfText by remember {
                                    mutableStateOf("")
                                }
                                val scrollState = rememberScrollState(0)

                                val isZeroShot = TtsEngine.tts!!.isZeroshot()
                                val numSpeakers = TtsEngine.tts!!.numSpeakers()
                                var promptText by remember { mutableStateOf("你就需要我这种专业人士的帮助，就像手无缚鸡之力的人进入雪山狩猎，一定需要最老练的猎人指导。") }
                                var promptAudioPath by remember { mutableStateOf("/sdcard/Android/data/com.k2fsa.sherpa.onnx.tts.engine/files/prompt.wav") }
                                var promptSamples by remember { mutableStateOf<FloatArray?>(null) }
                                var promptSampleRate by remember { mutableStateOf(0) }

                                if (!isZeroShot && numSpeakers > 1) {
                                    OutlinedTextField(
                                        value = TtsEngine.speakerIdState.value.toString(),
                                        onValueChange = {
                                            if (it.isEmpty() || it.isBlank()) {
                                                TtsEngine.speakerId = 0
                                            } else {
                                                try {
                                                    TtsEngine.speakerId = it.toString().toInt()
                                                } catch (ex: NumberFormatException) {
                                                    Log.i(TAG, "Invalid input: $it")
                                                    TtsEngine.speakerId = 0
                                                }
                                            }
                                            preferenceHelper.setSid(TtsEngine.speakerId)
                                        },
                                        label = {
                                            Text("Speaker ID: (0-${numSpeakers - 1})")
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .wrapContentHeight(),
                                    )
                                }

                                if (isZeroShot) {
                                    OutlinedTextField(
                                        value = promptText,
                                        onValueChange = { promptText = it },
                                        label = { Text("Prompt text (for voice cloning)") },
                                        maxLines = 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .wrapContentHeight(),
                                        singleLine = false,
                                    )
                                    Row {
                                        OutlinedTextField(
                                            value = promptAudioPath,
                                            onValueChange = { promptAudioPath = it },
                                            label = { Text("Prompt audio path (wav)") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            modifier = Modifier.padding(start = 8.dp),
                                            onClick = {
                                                // TODO: Add file picker for prompt.wav if desired
                                                // For now, just try to load the wav file from path
                                                try {
                                                    val file = File(promptAudioPath)
                                                    if (file.exists()) {
                                                        val (samples, sr) = WavFileUtil.readWavFloat(file)
                                                        promptSamples = samples
                                                        promptSampleRate = sr
                                                        Toast.makeText(applicationContext, "Loaded prompt.wav", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(applicationContext, "File not found", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(applicationContext, "Failed to load wav: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Text("Load")
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = testText,
                                    onValueChange = { testText = it },
                                    label = { Text("Please input your text here") },
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .verticalScroll(scrollState)
                                        .wrapContentHeight(),
                                    singleLine = false,
                                )

                                Row {
                                    Button(
                                        enabled = startEnabled,
                                        modifier = Modifier.padding(5.dp),
                                        onClick = {
                                            Log.i(TAG, "Clicked, text: $testText")
                                            if (testText.isBlank() || testText.isEmpty()) {
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Please input some text to generate",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else if (isZeroShot && (promptSamples == null || promptSamples!!.isEmpty() || promptText.isBlank() || promptSampleRate == 0)) {
                                                Toast.makeText(
                                                    applicationContext,
                                                    "Please load prompt.wav and enter prompt text",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                startEnabled = false
                                                playEnabled = false
                                                stopped = false

                                                track.pause()
                                                track.flush()
                                                track.play()
                                                rtfText = ""
                                                Log.i(TAG, "Started with text $testText")

                                                CoroutineScope(Dispatchers.IO).launch {
                                                    for (samples in samplesChannel) {
                                                        track.write(
                                                            samples,
                                                            0,
                                                            samples.size,
                                                            AudioTrack.WRITE_BLOCKING
                                                        )
                                                        if (stopped) {
                                                            break
                                                        }
                                                    }

                                                    for (s in samplesChannel) {
                                                        // drain the channel
                                                    }
                                                }

                                                CoroutineScope(Dispatchers.Default).launch {
                                                    val timeSource = TimeSource.Monotonic
                                                    val startTime = timeSource.markNow()

                                                    val audio = if (isZeroShot) {
                                                        TtsEngine.tts!!.generateZeroShotWithCallback(
                                                            text = testText,
                                                            promptText = promptText,
                                                            promptSamples = promptSamples!!,
                                                            sampleRate = promptSampleRate,
                                                            speed = TtsEngine.speed,
                                                            numSteps = 4,
                                                            callback = ::callback,
                                                        )
                                                    } else {
                                                        TtsEngine.tts!!.generateWithCallback(
                                                            text = testText,
                                                            sid = TtsEngine.speakerId,
                                                            speed = TtsEngine.speed,
                                                            callback = ::callback,
                                                        )
                                                    }

                                                    val elapsed =
                                                        startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000;
                                                    val audioDuration =
                                                        audio.samples.size / TtsEngine.tts!!.sampleRate()
                                                            .toFloat()
                                                    val RTF = String.format(
                                                        "Number of threads: %d\nElapsed: %.3f s\nAudio duration: %.3f s\nRTF: %.3f/%.3f = %.3f",
                                                        TtsEngine.tts!!.config.model.numThreads,
                                                        elapsed,
                                                        audioDuration,
                                                        elapsed,
                                                        audioDuration,
                                                        elapsed / audioDuration
                                                    )

                                                    val filename =
                                                        application.filesDir.absolutePath + "/generated.wav"

                                                    val ok =
                                                        audio.samples.isNotEmpty() && audio.save(
                                                            filename
                                                        )

                                                    if (ok) {
                                                        withContext(Dispatchers.Main) {
                                                            startEnabled = true
                                                            playEnabled = true
                                                            rtfText = RTF
                                                        }
                                                    }
                                                }.start()
                                            }
                                        }) {
                                        Text("Start")
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        enabled = playEnabled,
                                        onClick = {
                                            stopped = true
                                            track.pause()
                                            track.flush()
                                            onClickPlay()
                                        }) {
                                        Text("Play")
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        onClick = {
                                            onClickStop()
                                            startEnabled = true
                                        }) {
                                        Text("Stop")
                                    }
                                }

                                // Button to navigate to TTS Demo Activity
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    onClick = {
                                        val intent = Intent(this@MainActivity, TtsDemoActivity::class.java)
                                        startActivity(intent)
                                    }
                                ) {
                                    Text("Open TTS Demo (TextToSpeech.speak)")
                                }

                                if (rtfText.isNotEmpty()) {
                                    Row {
                                        Text(rtfText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopMediaPlayer()
        super.onDestroy()
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun onClickPlay() {
        val filename = application.filesDir.absolutePath + "/generated.wav"
        stopMediaPlayer()
        mediaPlayer = MediaPlayer.create(
            applicationContext,
            Uri.fromFile(File(filename))
        )
        mediaPlayer?.start()
    }

    private fun onClickStop() {
        stopped = true
        track.pause()
        track.flush()

        stopMediaPlayer()
    }

    // this function is called from C++
    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                samplesChannel.send(samplesCopy)
            }
            return 1
        } else {
            track.stop()
            Log.i(TAG, " return 0")
            return 0
        }
    }

    private fun initAudioTrack() {
        val sampleRate = TtsEngine.tts!!.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }
}
