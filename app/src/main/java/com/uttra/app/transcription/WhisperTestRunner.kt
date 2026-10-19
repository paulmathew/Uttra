package com.uttra.app.transcription

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
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


    // Milestone 7.8B: diagnostic-only comparison of the two manually cut audio segments.
    // Fixed app-private filenames; no overlay, microphone or editor access.
    fun compareMixedSegments(context: Context) {
        val appContext = context.applicationContext

        scope.launch {
            val benchmarkDir = File(appContext.filesDir, "speech_benchmarks")
            val englishFile = File(benchmarkDir, "english_segment.m4a")
            val malayalamFile = File(benchmarkDir, "malayalam_segment.m4a")
            val reportFile = File(benchmarkDir, "mixed_7_8b_results.txt")

            try {
                check(englishFile.isFile && englishFile.length() > 0L) {
                    "Missing English segment: ${englishFile.absolutePath}"
                }
                check(malayalamFile.isFile && malayalamFile.length() > 0L) {
                    "Missing Malayalam segment: ${malayalamFile.absolutePath}"
                }

                Log.i(MIXED_BENCH_TAG, "MIXED 7.8B START: englishBytes=${englishFile.length()}, malayalamBytes=${malayalamFile.length()}")

                // Run sequentially to avoid overlapping model inference/memory use.
                val english = runMixedSegment("WHISPER_ENGLISH") {
                    WhisperNativeTranscriber(appContext).transcribe(
                        englishFile, TranscriptionLanguage.ENGLISH
                    )
                }
                val malayalam = runMixedSegment("INDIC_MALAYALAM") {
                    IndicMalayalamTranscriber(appContext).transcribe(
                        malayalamFile, TranscriptionLanguage.MALAYALAM
                    )
                }

                // This is deliberately simple concatenation, not automatic
                // overlap correction or language-switch detection.
                val combined = if (english.text != null && malayalam.text != null) {
                    english.text + " " + malayalam.text
                } else {
                    "UNAVAILABLE — one or both segments failed"
                }

                val report = listOf(
                    "Uttra 7.8B manual segmentation benchmark",
                    "Reference: This is going to be a mixed recording, ഞാൻ അങ്ങനെ നോക്കാം",
                    "English segment: ${englishFile.name} (${englishFile.length()} bytes)",
                    "Malayalam segment: ${malayalamFile.name} (${malayalamFile.length()} bytes)",
                    english.summary,
                    malayalam.summary,
                    "COMBINED (no deduplication or editing): $combined"
                ).joinToString("\n", postfix = "\n")

                reportFile.writeText(report, Charsets.UTF_8)
                Log.i(MIXED_BENCH_TAG, "MIXED 7.8B COMBINED: $combined")
                Log.i(MIXED_BENCH_TAG, "MIXED 7.8B REPORT SAVED: ${reportFile.absolutePath}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(MIXED_BENCH_TAG, "MIXED 7.8B FAILED", error)
                // Preserve an error report too, if private storage is writable.
                try {
                    benchmarkDir.mkdirs()
                    reportFile.writeText(
                        "Uttra 7.8B benchmark failed: ${error.stackTraceToString()}\n",
                        Charsets.UTF_8
                    )
                } catch (_: Exception) {
                    // Original failure is already logged.
                }
            }
        }
    }

    // Milestone 7.8C: compare full-recording Whisper English with the
    // 7.8B manually cut English and Malayalam segments. No text insertion.
    fun compareWholeVsSegments(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val dir = File(appContext.filesDir, "speech_benchmarks")
            val full = File(dir, "mixed_01.m4a")
            val english = File(dir, "english_segment.m4a")
            val malayalam = File(dir, "malayalam_segment.m4a")
            val reportFile = File(dir, "mixed_7_8c_results.txt")

            try {
                for (recording in listOf(full, english, malayalam)) {
                    check(recording.isFile && recording.length() > 0L) {
                        "Missing or empty benchmark recording: ${recording.absolutePath}"
                    }
                }

                Log.i(MIXED_BENCH_TAG, "MIXED 7.8C START: fullBytes=${full.length()}, " +
                    "englishBytes=${english.length()}, malayalamBytes=${malayalam.length()}")

                // Sequential inference limits simultaneous native model memory use.
                val fullEnglish = runMixedSegment("WHISPER_ENGLISH_FULL", "7.8C") {
                    WhisperNativeTranscriber(appContext).transcribe(
                        full, TranscriptionLanguage.ENGLISH
                    )
                }
                val splitEnglish = runMixedSegment("WHISPER_ENGLISH_SEGMENT", "7.8C") {
                    WhisperNativeTranscriber(appContext).transcribe(
                        english, TranscriptionLanguage.ENGLISH
                    )
                }
                val splitMalayalam = runMixedSegment("INDIC_MALAYALAM_SEGMENT", "7.8C") {
                    IndicMalayalamTranscriber(appContext).transcribe(
                        malayalam, TranscriptionLanguage.MALAYALAM
                    )
                }

                // Do NOT concatenate whole-English output with Malayalam:
                // Whisper may hallucinate English words for the Malayalam tail.
                val report = listOf(
                    "Uttra 7.8C whole-versus-segmented benchmark",
                    "Reference: This is going to be a mixed recording, ഞാൻ അങ്ങനെ നോക്കാം",
                    "Full recording: ${full.name} (${full.length()} bytes)",
                    "English segment: ${english.name} (${english.length()} bytes)",
                    "Malayalam segment: ${malayalam.name} (${malayalam.length()} bytes)",
                    fullEnglish.summary,
                    splitEnglish.summary,
                    splitMalayalam.summary,
                    "NOTE: Manual comparison only; no automatic text merge or insertion."
                ).joinToString("\n", postfix = "\n")

                reportFile.writeText(report, Charsets.UTF_8)
                Log.i(MIXED_BENCH_TAG, "MIXED 7.8C REPORT SAVED: ${reportFile.absolutePath}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(MIXED_BENCH_TAG, "MIXED 7.8C ERROR", error)
                try {
                    dir.mkdirs()
                    reportFile.writeText(
                        "Uttra 7.8C benchmark failed: ${error.stackTraceToString()}\n",
                        Charsets.UTF_8
                    )
                } catch (_: Exception) {
                    // The error is already in Logcat.
                }
            }
        }
    }

    private data class MixedSegmentOutcome(
        val text: String?,
        val summary: String
    )

    private suspend fun runMixedSegment(
        engine: String,
        phase: String = "7.8B",
        operation: suspend () -> Result<String>
    ): MixedSegmentOutcome {
        val startedAt = SystemClock.elapsedRealtime()
        val result: Result<String> = try {
            operation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt

        return result.fold(
            onSuccess = { text ->
                val cleanedText = text.trim()
                val line = if (cleanedText.isEmpty()) {
                    "MIXED $phase RESULT: engine=$engine, elapsedMs=$elapsedMs, EMPTY OUTPUT"
                } else {
                    "MIXED $phase RESULT: engine=$engine, elapsedMs=$elapsedMs, text='$cleanedText'"
                }
                Log.i(MIXED_BENCH_TAG, line)
                MixedSegmentOutcome(cleanedText.ifEmpty { null }, line)
            },
            onFailure = { error ->
                val line = "MIXED $phase FAILED: engine=$engine, elapsedMs=$elapsedMs, reason=${error.message}"
                Log.e(MIXED_BENCH_TAG, line, error)
                MixedSegmentOutcome(null, line)
            }
        )
    }

    private const val MIXED_BENCH_TAG = "UttraMixedBench"

}
