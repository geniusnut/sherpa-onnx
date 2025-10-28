package com.k2fsa.sherpa.onnx.tts.engine

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.k2fsa.sherpa.onnx.tts.engine.ui.theme.SherpaOnnxTtsEngineTheme
import java.util.Locale

/**
 * Demo activity showing how to use TextToSpeech.speak() with our custom TtsService
 */
@OptIn(ExperimentalMaterial3Api::class)
class TtsDemoActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TtsEngine first to ensure it's ready for the service
        Log.i(TAG, "Initializing TtsEngine in TtsDemoActivity")
        TtsEngine.createTts(this)

        // Initialize TextToSpeech with our custom engine
        initializeTextToSpeech()

        setContent {
            SherpaOnnxTtsEngineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TtsDemoScreen()
                }
            }
        }
    }

    private fun initializeTextToSpeech() {
        // Initialize TextToSpeech with our custom engine explicitly
        val engineName = packageName
        Log.i(TAG, "Initializing TTS with custom engine: $engineName")

        textToSpeech = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.let { tts ->
                    Log.i(TAG, "TTS initialized with engine: ${tts.defaultEngine}")

                    // Set language
                    val langCode = TtsEngine.lang ?: "eng"
                    val locale = when (langCode) {
                        "eng" -> Locale.US
                        "deu" -> Locale.GERMAN
                        "fra" -> Locale.FRENCH
                        "spa" -> Locale("es")
                        "cmn" -> Locale.CHINESE
                        "zho" -> Locale.CHINESE
                        else -> Locale(langCode)
                    }

                    val result = tts.setLanguage(locale)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Language not supported: $langCode")
                        Toast.makeText(
                            this,
                            "Language not supported",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        ttsInitialized = true
                        Log.i(TAG, "TTS initialized successfully with engine: $engineName")
                        Toast.makeText(
                            this,
                            "TTS initialized successfully with custom engine",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Set utterance progress listener
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.i(TAG, "TTS started: $utteranceId")
                        }

                        override fun onDone(utteranceId: String?) {
                            Log.i(TAG, "TTS done: $utteranceId")
                        }

                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS error: $utteranceId")
                        }
                    })
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
                Toast.makeText(
                    this,
                    "TTS initialization failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, engineName) // Specify our custom engine package name
    }

    @Composable
    fun TtsDemoScreen() {
        var textToSpeak by remember { mutableStateOf("你好，使用新一代卡尔迪的语音合成引擎") }
        var isSpeaking by remember { mutableStateOf(false) }
        var speechRate by remember { mutableStateOf(1.0f) }
        var pitch by remember { mutableStateOf(1.0f) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TTS Demo - TextToSpeech.speak()") }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status indicator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ttsInitialized)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = if (ttsInitialized) "TTS Ready" else "TTS Not Initialized",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Text input
                OutlinedTextField(
                    value = textToSpeak,
                    onValueChange = { textToSpeak = it },
                    label = { Text("Text to speak") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 5,
                    enabled = ttsInitialized && !isSpeaking
                )

                // Speech rate control
                Text("Speech Rate: ${String.format("%.2f", speechRate)}")
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.5f..2.0f,
                    enabled = ttsInitialized && !isSpeaking
                )

                // Pitch control
                Text("Pitch: ${String.format("%.2f", pitch)}")
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.5f..2.0f,
                    enabled = ttsInitialized && !isSpeaking
                )

                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            speakText(textToSpeak, speechRate, pitch)
                            isSpeaking = true
                        },
                        modifier = Modifier.weight(1f),
                        enabled = ttsInitialized && !isSpeaking && textToSpeak.isNotBlank()
                    ) {
                        Text("Speak")
                    }

                    Button(
                        onClick = {
                            stopSpeaking()
                            isSpeaking = false
                        },
                        modifier = Modifier.weight(1f),
                        enabled = ttsInitialized && isSpeaking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop")
                    }
                }

                Button(
                    onClick = {
                        speakText(textToSpeak, speechRate, pitch)
                        isSpeaking = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ttsInitialized && !isSpeaking && textToSpeak.isNotBlank()
                ) {
                    Text("Speak (Queue Mode)")
                }

                // Info text
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Usage Information",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = """
                                This activity demonstrates using the standard Android TextToSpeech API with our custom TtsService.
                                
                                • Enter text in the field above
                                • Adjust speech rate and pitch
                                • Click 'Speak' to use QUEUE_FLUSH mode
                                • Click 'Stop' to interrupt speech
                                
                                The TextToSpeech class will automatically use our custom TtsService to synthesize speech.
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Update speaking state when TTS finishes
        DisposableEffect(Unit) {
            onDispose {
                isSpeaking = false
            }
        }
    }

    private fun speakText(text: String, rate: Float, pitch: Float) {
        textToSpeech?.let { tts ->
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)

            // Use TextToSpeech.speak() with QUEUE_FLUSH to replace any ongoing speech
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

            Log.i(TAG, "Speaking: $text (rate: $rate, pitch: $pitch)")
        }
    }

    private fun stopSpeaking() {
        textToSpeech?.stop()
        Log.i(TAG, "Stopped speaking")
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
