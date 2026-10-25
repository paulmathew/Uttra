
package com.uttra.app.transcription

import android.content.Context
import com.uttra.app.BuildConfig
import java.io.File
import android.os.SystemClock
import com.uttra.app.core.logging.logd

internal class UttraSpeechTranscriber(
    context: Context
) : SpeechTranscriber {

    private val englishTranscriber =
        WhisperNativeTranscriber(context.applicationContext)

    private val malayalamTranscriber =
        IndicMalayalamTranscriber(context.applicationContext)

    private val languageDetector =
        WhisperLanguageDetector(context.applicationContext)

    override suspend fun transcribe(
        audioFile: File,
        language: TranscriptionLanguage
    ): Result<String> {

        // Experiment 7.9: measure the full transcribe() call.
        val startedAtMs = SystemClock.elapsedRealtime()

        try {
            val transcriptionResult = when (language) {

                TranscriptionLanguage.ENGLISH -> {
                    englishTranscriber.transcribe(
                        audioFile,
                        language
                    )
                }

                TranscriptionLanguage.MALAYALAM -> {
                    malayalamTranscriber.transcribe(
                        audioFile,
                        language
                    )
                }

                TranscriptionLanguage.AUTO -> {
                    if (!BuildConfig.DEBUG) {
                        Result.failure(
                            UnsupportedOperationException(
                                "Automatic language routing is still experimental"
                            )
                        )
                    } else {

                        val detectionStartedAtMs = SystemClock.elapsedRealtime()

                        val detectionResult = try {
                            languageDetector.detect(audioFile)
                        } finally {
                            logd {
                                "UTTRA LATENCY: stage=language_detection " +
                                        "elapsedMs=${SystemClock.elapsedRealtime() - detectionStartedAtMs}"
                            }
                        }

                        val detection = detectionResult.getOrElse { error ->
                            return Result.failure(error)
                        }

                        when (detection.language) {

                            TranscriptionLanguage.ENGLISH -> {
                                englishTranscriber.transcribe(
                                    audioFile,
                                    TranscriptionLanguage.ENGLISH
                                )
                            }

                            TranscriptionLanguage.MALAYALAM -> {
                                malayalamTranscriber.transcribe(
                                    audioFile,
                                    TranscriptionLanguage.MALAYALAM
                                )
                            }

                            TranscriptionLanguage.AUTO, null -> {
                                Result.failure(
                                    IllegalStateException(
                                        "No reliable English or Malayalam speech detected"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            return transcriptionResult.map { text ->
                TranscriptionTextFormatter.format(text)
            }

        } finally {
            logd {
                "UTTRA LATENCY: requested=$language " +
                        "totalMs=${SystemClock.elapsedRealtime() - startedAtMs}"
            }
        }
    }
}
