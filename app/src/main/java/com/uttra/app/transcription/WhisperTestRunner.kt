package com.uttra.app.transcription

import android.content.Context
import android.os.SystemClock
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

internal object WhisperTestRunner {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    fun test(context: Context, audioFile: File) {
        val appContext = context.applicationContext

        scope.launch {

            try {
                val benchmarkDir = File(
                    appContext.filesDir,
                    "speech_benchmarks"
                )

                check(
                    benchmarkDir.isDirectory || benchmarkDir.mkdirs()
                ) {
                    "Unable to create benchmark directory"
                }

                val benchmarkFile = File(
                    benchmarkDir,
                    "mixed_01.m4a"
                )

                if (!benchmarkFile.exists()) {
                    try {
                        audioFile.copyTo(benchmarkFile)

                        logd {
                            "BENCHMARK SAVED: " +
                                    "${benchmarkFile.absolutePath}, " +
                                    "size=${benchmarkFile.length()}"
                        }
                    } catch (e: Exception) {
                        benchmarkFile.delete()
                        throw e
                    }
                } else {
                    logd {
                        "BENCHMARK ALREADY EXISTS: " +
                                benchmarkFile.absolutePath
                    }
                }

            } catch (e: Exception) {
                loge(e) {
                    "BENCHMARK SAVE FAILED"
                }
            }

            val startMs = SystemClock.elapsedRealtime()
            logd { "start MS = $startMs" }
            try {
                val result = WhisperNativeTranscriber(appContext)
                    .transcribe(
                        audioFile = audioFile,
                        language = TranscriptionLanguage.AUTO
                    )

                logd { "result MS = ${result.toString()}" }
                val durationMs =
                    SystemClock.elapsedRealtime() - startMs
                logd { "durationMs MS = $durationMs" }

                result
                    .onSuccess { text ->
                        logd {
                            "Uttra Whisper PASSED: " +
                                    "text='$text', " +
                                    "processingMs=$durationMs"
                        }
                    }
                    .onFailure { error ->
                        loge(error) {
                            "Uttra Whisper FAILED " +
                                    "after ${durationMs}ms"
                        }
                    }

            } catch (e: Exception) {
                loge(e) {
                    "Uttra Whisper test exception"
                }
            }
        }
    }


    fun replayBenchmark(
        context: Context,
        fileName: String,
        language: TranscriptionLanguage
    ) {
        val appContext = context.applicationContext

        val benchmarkFile = File(
            appContext.filesDir,
            "speech_benchmarks/$fileName"
        )

        if (!benchmarkFile.isFile || benchmarkFile.length() == 0L) {
            logd {
                "BENCHMARK REPLAY FAILED: Missing recording: " +
                        benchmarkFile.absolutePath
            }
            return
        }

        scope.launch {
            val startMs = SystemClock.elapsedRealtime()

            logd {
                "BENCHMARK REPLAY START: ${benchmarkFile.name}"
            }

            try {
                val result = WhisperNativeTranscriber(appContext)
                    .transcribe(
                        audioFile = benchmarkFile,
                        language = language
                    )

                val durationMs =
                    SystemClock.elapsedRealtime() - startMs

                result
                    .onSuccess { text ->
                        logd {
                            "BENCHMARK REPLAY PASSED: " +
                                    "text='$text', " +
                                    "processingMs=$durationMs"
                        }
                    }
                    .onFailure { error ->
                        loge(error) {
                            "BENCHMARK REPLAY FAILED " +
                                    "after ${durationMs}ms"
                        }
                    }

            } catch (e: Exception) {
                loge(e) {
                    "BENCHMARK REPLAY EXCEPTION"
                }
            }
        }
    }



    fun detectBenchmark(
        context: Context,
        fileName: String
    ) {
        val appContext = context.applicationContext

        val benchmarkFile = File(
            appContext.filesDir,
            "speech_benchmarks/$fileName"
        )

        if (!benchmarkFile.isFile || benchmarkFile.length() == 0L) {
            logd {
                "LANGUAGE POLICY FAILED: Missing benchmark $fileName"
            }
            return
        }

        scope.launch {
            try {

                if (fileName == "english_03.m4a" ||
                    fileName == "live_auto_rejected_04.m4a"
                ) {
                    val transcriber = UttraSpeechTranscriber(appContext)

                    for (language in listOf(
                        TranscriptionLanguage.ENGLISH,
                        TranscriptionLanguage.MALAYALAM
                    )) {
                        val start = android.os.SystemClock.elapsedRealtime()

                        val result = transcriber.transcribe(
                            audioFile = benchmarkFile,
                            language = language
                        )

                        val elapsedMs =
                            android.os.SystemClock.elapsedRealtime() - start

                        result.fold(
                            onSuccess = { text ->
                                logd {
                                    "CROSS ASR RESULT: " +
                                            "file=$fileName, " +
                                            "engine=$language, " +
                                            "elapsedMs=$elapsedMs, " +
                                            "text='$text'"
                                }
                            },
                            onFailure = { error ->
                                logd {
                                    "CROSS ASR FAILED: " +
                                            "file=$fileName, " +
                                            "engine=$language, " +
                                            "elapsedMs=$elapsedMs, " +
                                            "reason=${error.message}"
                                }
                            }
                        )
                    }

                    return@launch
                }


                if (fileName == "live_ml_failed_03.m4a") {
                    val result = UttraSpeechTranscriber(appContext)
                        .transcribe(
                            audioFile = benchmarkFile,
                            language = TranscriptionLanguage.MALAYALAM
                        )

                    result.fold(
                        onSuccess = { text ->
                            logd {
                                "DIRECT MALAYALAM RESULT: " +
                                        "file=$fileName, text='$text'"
                            }
                        },
                        onFailure = { error ->
                            loge(error) {
                                "DIRECT MALAYALAM FAILED: $fileName"
                            }
                        }
                    )

                    return@launch
                }

                val samples = AndroidAudioDecoder()
                    .decodeToPcm16kMono(benchmarkFile)
                    .getOrThrow()

                check(samples.isNotEmpty()) {
                    "Empty decoded recording"
                }

                val rms = kotlin.math.sqrt(
                    samples.fold(0.0) { sum, sample ->
                        val value = sample.toDouble()
                        sum + value * value
                    } / samples.size
                )

                val peak = samples.maxOf { kotlin.math.abs(it) }

                val activeSamples = samples.count {
                    kotlin.math.abs(it) > 0.02f
                }

                val activePercent =
                    activeSamples * 100.0 / samples.size

                logd {
                    "AUDIO ACTIVITY RESULT: " +
                            "file=$fileName, " +
                            "rms=$rms, " +
                            "peak=$peak, " +
                            "activePercent=$activePercent"
                }


                if (fileName == "live_auto_rejected_04.m4a") {

                    val windowSize = 16_000 // One second at 16 kHz

                    for (start in samples.indices step windowSize) {

                        val end = minOf(start + windowSize, samples.size)
                        val count = end - start

                        var sumSquares = 0.0
                        var activeCount = 0

                        for (i in start until end) {
                            val value = samples[i].toDouble()

                            sumSquares += value * value

                            if (kotlin.math.abs(value) > 0.02) {
                                activeCount++
                            }
                        }

                        val windowRms = kotlin.math.sqrt(
                            sumSquares / count
                        )

                        val activePercent = activeCount * 100.0 / count

                        logd {
                            "AUDIO WINDOW RESULT: " +
                                    "file=$fileName, " +
                                    "second=${start / windowSize}, " +
                                    "rms=$windowRms, " +
                                    "activePercent=$activePercent"
                        }
                    }


                    val baseModel = File(
                        appContext.filesDir,
                        "ggml-base.bin"
                    )

                    check(baseModel.isFile && baseModel.length() > 0L) {
                        "Whisper Base model is missing"
                    }

                    val handle = UttraNativeBridge.nativeCreateContext(
                        baseModel.absolutePath
                    )

                    check(handle != 0L) {
                        "Failed to initialize Whisper Base"
                    }

                    try {
                        val scores = UttraNativeBridge.nativeDetectLanguage(
                            contextHandle = handle,
                            audioSamples = samples
                        )

                        check(scores.size == 2) {
                            "Invalid Whisper Base detection result"
                        }

                        logd {
                            "BASE LANGUAGE RESULT: " +
                                    "file=$fileName, " +
                                    "en=${scores[0]}, " +
                                    "ml=${scores[1]}"
                        }
                    } finally {
                        UttraNativeBridge.nativeFreeContext(handle)
                    }


                }

                val detection = WhisperLanguageDetector(appContext)
                    .detect(benchmarkFile)
                    .getOrThrow()

                val selectedLanguage =
                    detection.language?.name ?: "UNCERTAIN"

                logd {
                    "LANGUAGE POLICY RESULT: " +
                            "file=$fileName, " +
                            "selected=$selectedLanguage, " +
                            "en=${detection.englishScore}, " +
                            "ml=${detection.malayalamScore}"
                }


                val autoResult = UttraSpeechTranscriber(appContext)
                    .transcribe(
                        audioFile = benchmarkFile,
                        language = TranscriptionLanguage.AUTO
                    )

                autoResult.fold(
                    onSuccess = { text ->
                        logd {
                            "AUTO TRANSCRIPTION RESULT: " +
                                    "file=$fileName, " +
                                    "text=$text"
                        }
                    },
                    onFailure = { error ->
                        logd {
                            "AUTO TRANSCRIPTION REJECTED: " +
                                    "file=$fileName, " +
                                    "reason=${error.message}"
                        }
                    }
                )


            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loge(e) {
                    "LANGUAGE POLICY FAILED: $fileName"
                }
            }
        }
    }


    fun compareDirectVsAuto(
        context: Context,
        fileName: String
    ) {
        val directLanguage = when (fileName) {
            "english_02.m4a",
            "english_03.m4a",
            "quiet_english_01.m4a",-> TranscriptionLanguage.ENGLISH

            "malayalam_01.m4a",
            "malayalam_03.m4a",
            "quiet_malayalam_01.m4a"-> TranscriptionLanguage.MALAYALAM

            else -> {
                logd { "BENCH 7.9: Unsupported fixture $fileName" }
                return
            }
        }

        val appContext = context.applicationContext
        val benchmarkFile = File(
            appContext.filesDir,
            "speech_benchmarks/$fileName"
        )

        if (!benchmarkFile.isFile || benchmarkFile.length() == 0L) {
            logd { "BENCH 7.9: Missing fixture $fileName" }
            return
        }

        scope.launch {
            try {
                for (attempt in 1..3) {
                    for (language in listOf(
                        directLanguage,
                        TranscriptionLanguage.AUTO
                    )) {
                        val route = if (
                            language == TranscriptionLanguage.AUTO
                        ) "AUTO" else "DIRECT"

                        logd {
                            "BENCH 7.9 START: file=$fileName " +
                                    "attempt=$attempt route=$route"
                        }

                        val startMs = SystemClock.elapsedRealtime()

                        val result = UttraSpeechTranscriber(appContext)
                            .transcribe(
                                audioFile = benchmarkFile,
                                language = language
                            )

                        val elapsedMs =
                            SystemClock.elapsedRealtime() - startMs

                        logd {
                            "BENCH 7.9 END: file=$fileName " +
                                    "attempt=$attempt route=$route " +
                                    "elapsedMs=$elapsedMs " +
                                    "success=${result.isSuccess} " +
                                    "error=${result.exceptionOrNull()?.message} " +
                                    "text='${result.getOrNull()}'"
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loge(e) {
                    "BENCH 7.9 FAILED: file=$fileName"
                }
            }
        }
    }


}
