
package com.uttra.app.transcription

import android.content.Context
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge
import java.io.File

internal object IndicMalayalamSmokeTest {

    suspend fun run(context: Context) {
        val modelDir = File(context.filesDir, "indic_ml")
        val model = File(modelDir, "model.int8.onnx")
        val tokens = File(modelDir, "tokens.txt")

        if (!model.isFile || !tokens.isFile) {
            logd { "INDIC ML FAILED: Model files missing" }
            return
        }

        val startMs = SystemClock.elapsedRealtime()

        try {
            logd { "INDIC ML: Loading Malayalam model" }

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

            try {
                val elapsedMs =
                    SystemClock.elapsedRealtime() - startMs

                logd {
                    "INDIC ML MODEL INIT PASSED: elapsedMs=$elapsedMs"
                }

                val benchmarkFile = File(
                    context.filesDir,
                    "speech_benchmarks/english_01.m4a"
                )

                check(benchmarkFile.isFile) {
                    "Malayalam benchmark recording not found"
                }

                logd { "INDIC ML REPLAY: Decoding saved audio" }

                val samples = AndroidAudioDecoder()
                    .decodeToPcm16kMono(benchmarkFile)
                    .getOrThrow()

                logd {
                    "INDIC ML REPLAY: PCM ready, samples=${samples.size}"
                }

                val stream = recognizer.createStream()

                try {
                    stream.acceptWaveform(
                        samples,
                        sampleRate = 16000
                    )

                    logd { "INDIC ML REPLAY: Starting inference" }

                    val inferenceStartMs = SystemClock.elapsedRealtime()

                    recognizer.decode(stream)

                    val inferenceMs =
                        SystemClock.elapsedRealtime() - inferenceStartMs

                    val result = recognizer.getResult(stream)

                    logd {
                        "INDIC ML REPLAY PASSED: " +
                                "text='${result.text}', " +
                                "inferenceMs=$inferenceMs, " +
                                "totalMs=${SystemClock.elapsedRealtime() - startMs}"
                    }

                } finally {
                    stream.release()
                }

            } finally {
                recognizer.release()
            }

        } catch (e: UnsatisfiedLinkError) {
            loge(e) { "INDIC ML NATIVE LIBRARY FAILED" }
        } catch (e: Exception) {
            loge(e) { "INDIC ML MODEL INIT FAILED" }
        }
    }
}
