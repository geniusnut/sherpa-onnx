package com.k2fsa.sherpa.onnx.tts.engine

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import java.io.File

/*
https://developer.android.com/reference/java/util/Locale#getISO3Language()
https://developer.android.com/reference/java/util/Locale#getISO3Country()

eng, USA,
eng, USA, POSIX
eng,
eng, GBR
afr,
afr, NAM
afr, ZAF
agq
agq, CMR
aka,
aka, GHA
amh,
amh, ETH
ara,
ara, 001
ara, ARE
ara, BHR,
deu
deu, AUT
deu, BEL
deu, CHE
deu, ITA
deu, ITA
deu, LIE
deu, LUX
spa,
spa, 419
spa, ARG,
spa, BRA
fra,
fra, BEL,
fra, FRA,

E  Failed to check TTS data, no activity found for Intent
{ act=android.speech.tts.engine.CHECK_TTS_DATA pkg=com.k2fsa.sherpa.chapter5 })

E Failed to get default language from engine com.k2fsa.sherpa.chapter5
Engine failed voice data integrity check (null return)com.k2fsa.sherpa.chapter5
Failed to get default language from engine com.k2fsa.sherpa.chapter5

*/

class TtsService : TextToSpeechService() {
    companion object {
        const val TAG = "TtsService"
    }

    private var promptText: String = ""
    private var promptSamples: FloatArray? = null
    private var promptSampleRate: Int = 0

    override fun onCreate() {
        Log.i(TAG, "onCreate tts service")
        super.onCreate()

        // see https://github.com/Miserlou/Android-SDK-Samples/blob/master/TtsEngine/src/com/example/android/ttsengine/RobotSpeakTtsService.java#L68
        onLoadLanguage(TtsEngine.lang, "", "")
        if (TtsEngine.lang2 != null) {
            onLoadLanguage(TtsEngine.lang2, "", "")
        }

        // Initialize prompt text and samples for zero-shot TTS
        initializePromptData()
    }

    private fun initializePromptData() {
        val startTime = System.currentTimeMillis()
        try {
            // Set default prompt text for Chinese zero-shot TTS
            promptText = "你就需要我这种专业人士的帮助，就像手无缚鸡之力的人进入雪山狩猎，一定需要最老练的猎人指导。"

            // Try to load prompt audio from external storage or assets
            val promptPath = File(getExternalFilesDir(null), "prompt.wav")

            if (promptPath.exists()) {
                Log.i(TAG, "Loading prompt audio from: ${promptPath.absolutePath}")
                val loadStartTime = System.currentTimeMillis()
                val (samples, sampleRate) = WavFileUtil.readWavFloat(promptPath)
                val loadDuration = System.currentTimeMillis() - loadStartTime
                promptSamples = samples
                promptSampleRate = sampleRate
                Log.i(TAG, "Prompt audio loaded successfully. Sample rate: $sampleRate, samples: ${samples.size}, load time: ${loadDuration}ms")
            } else {
                Log.w(TAG, "Prompt audio file not found at: ${promptPath.absolutePath}")
                Log.w(TAG, "Zero-shot TTS will not work without prompt audio. Please place prompt.wav in: ${promptPath.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize prompt data: ${e.message}", e)
        } finally {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.i(TAG, "initializePromptData completed in ${totalDuration}ms")
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy tts service")
        super.onDestroy()
    }

    // https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeechService#onislanguageavailable
    override fun onIsLanguageAvailable(_lang: String?, _country: String?, _variant: String?): Int {
        val lang = _lang ?: ""

        if (lang == TtsEngine.lang || lang == TtsEngine.lang2) {
            return TextToSpeech.LANG_AVAILABLE
        }

        return TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf(TtsEngine.lang!!, "", "")
    }

    // https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeechService#onLoadLanguage(kotlin.String,%20kotlin.String,%20kotlin.String)
    override fun onLoadLanguage(_lang: String?, _country: String?, _variant: String?): Int {
        Log.i(TAG, "onLoadLanguage: $_lang, $_country")
        val lang = _lang ?: ""

        return if (lang == TtsEngine.lang || lang == TtsEngine.lang2) {
            Log.i(TAG, "creating tts, lang :$lang")
            TtsEngine.createTts(application)
            TextToSpeech.LANG_AVAILABLE
        } else {
            Log.i(TAG, "lang $lang not supported, tts engine lang: ${TtsEngine.lang}, ${TtsEngine.lang2}")
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onStop() {}

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) {
            return
        }
        val language = request.language
        val country = request.country
        val variant = request.variant
        val text = request.charSequenceText.toString()

        val ret = onIsLanguageAvailable(language, country, variant)
        if (ret == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback.error()
            return
        }
        Log.i(TAG, "text: $text")
        val tts = TtsEngine.tts!!

        // Note that AudioFormat.ENCODING_PCM_FLOAT requires API level >= 24
        // callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_FLOAT, 1)

        callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1)

        if (text.isBlank() || text.isEmpty()) {
            callback.done()
            return
        }

        Log.i(TAG, "text: $text")
        if (tts.isZeroshot()) {
            val result = tts.generateZeroShotWithCallback(
                text = text,
                promptText = promptText,
                promptSamples = promptSamples!!,
                sampleRate = promptSampleRate,
                speed = TtsEngine.speed,
                numSteps = 4,
                callback = { floatSamples ->
                    // This callback is not actually used by the C++ library
                    // Just return 1 to continue
                    0
                },
            )
            Log.d(TAG, "onSynthesizeText: sampleRate: ${result.sampleRate}, samples size: ${result.samples.size}, " +
                    "audio length: ${result.samples.size / result.sampleRate.toFloat()} seconds")

            // Process the result manually since the callback wasn't invoked
            val samples = floatArrayToByteArray(result.samples)
            Log.d(TAG, "Converted byte samples size: ${samples.size}")
            val maxBufferSize: Int = callback.maxBufferSize
            var offset = 0
            while (offset < samples.size) {
                val bytesToWrite = Math.min(maxBufferSize, samples.size - offset)
                callback.audioAvailable(samples, offset, bytesToWrite)
                offset += bytesToWrite
            }
        } else {
            tts.generateWithCallback(
                text = text,
                sid = TtsEngine.speakerId,
                speed = TtsEngine.speed,
                callback = { floatSamples ->
                    // convert FloatArray to ByteArray
                    val samples = floatArrayToByteArray(floatSamples)
                    Log.d(TAG, "ttsCallback: samples size: ${samples.size}")
                    val maxBufferSize: Int = callback.maxBufferSize
                    var offset = 0
                    while (offset < samples.size) {
                        val bytesToWrite = Math.min(maxBufferSize, samples.size - offset)
                        callback.audioAvailable(samples, offset, bytesToWrite)
                        offset += bytesToWrite
                    }

                    // 1 means to continue
                    // 0 means to stop
                    1
                },
            )
        }

        callback.done()
    }

    private fun floatArrayToByteArray(audio: FloatArray): ByteArray {
        // byteArray is actually a ShortArray
        val byteArray = ByteArray(audio.size * 2)
        for (i in audio.indices) {
            val sample = (audio[i] * 32767).toInt()
            byteArray[2 * i] = sample.toByte()
            byteArray[2 * i + 1] = (sample shr 8).toByte()
        }
        return byteArray
    }
}
