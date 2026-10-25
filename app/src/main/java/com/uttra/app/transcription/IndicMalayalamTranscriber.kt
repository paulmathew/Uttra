
package com.uttra.app.transcription

import android.content.Context
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.uttra.app.core.logging.logd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class IndicMalayalamTranscriber(
    private val context: Context,
    private val audioDecoder: AudioDecoder = AndroidAudioDecoder()
) : SpeechTranscriber {

    override suspend fun transcribe(
        audioFile: File,
        language: TranscriptionLanguage
    ): Result<String> = withContext(Dispatchers.IO) {

        try {
            require(language == TranscriptionLanguage.MALAYALAM) {
                "IndicMalayalamTranscriber currently supports MALAYALAM mode"
            }

            val startMs = SystemClock.elapsedRealtime()

            val modelDir = File(
                context.applicationContext.filesDir,
                "indic_ml"
            )

            val model = File(modelDir, "model.int8.onnx")
            val tokens = File(modelDir, "tokens.txt")

            require(model.isFile && tokens.isFile) {
                "IndicConformer model files are missing"
            }

            logd { "INDIC TRANSCRIBER: Decoding audio" }

            val decodeStartedMs = SystemClock.elapsedRealtime()

            val samples = audioDecoder
                .decodeToPcm16kMono(audioFile)
                .getOrThrow()

            logd {
                "UTTRA LATENCY: stage=indic_audio_decode " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - decodeStartedMs}"
            }

            logd { "INDIC TRANSCRIBER: Loading model" }
            val modelSetupStartedMs = SystemClock.elapsedRealtime()
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(
                        model = model.absolutePath
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )

            val recognizer = OfflineRecognizer(config = config)
            logd {
                "UTTRA LATENCY: stage=indic_model_setup " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - modelSetupStartedMs}"
            }
            try {
                val stream = recognizer.createStream()

                try {
                    stream.acceptWaveform(
                        samples,
                        sampleRate = 16000
                    )

                    logd { "INDIC TRANSCRIBER: Starting inference" }

                    val inferenceStartMs =
                        SystemClock.elapsedRealtime()

                    recognizer.decode(stream)

                    val inferenceMs =
                        SystemClock.elapsedRealtime() - inferenceStartMs

                    logd {
                        "UTTRA LATENCY: stage=indic_inference " +
                                "elapsedMs=$inferenceMs"
                    }

                    val text = recognizer
                        .getResult(stream)
                        .text
                        .trim()

                    logd {
                        "INDIC TRANSCRIBER PASSED: " +
                            "text='$text', " +
                            "inferenceMs=$inferenceMs, " +
                            "totalMs=${SystemClock.elapsedRealtime() - startMs}"
                    }

                    Result.success(text)

                } finally {
                    stream.release()
                }

            } finally {
                recognizer.release()
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsatisfiedLinkError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
