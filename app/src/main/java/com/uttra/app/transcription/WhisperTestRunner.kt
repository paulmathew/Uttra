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

}
