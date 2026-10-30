
package com.uttra.app.transcription

import android.content.Context
import com.uttra.app.BuildConfig
import java.io.File
import android.os.SystemClock
import com.uttra.app.core.logging.logd

internal class UttraSpeechTranscriber(
    context: Context
) : SpeechTranscriber {

    private val appContext = context.applicationContext
    private val englishTranscriber =
        WhisperNativeTranscriber(context.applicationContext)

    private val malayalamTranscriber =
        IndicMalayalamTranscriber(context.applicationContext)

//    private val languageDetector =
//        WhisperLanguageDetector(context.applicationContext)

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
                        val requestDecoder = ReusingAudioDecoder()
                        val detectionStartedAtMs = SystemClock.elapsedRealtime()

                        val detectionResult = try {
                            WhisperLanguageDetector(
                                context = appContext,
                                audioDecoder = requestDecoder
                            ).detect(audioFile)
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
                                WhisperNativeTranscriber(
                                    context = appContext,
                                    audioDecoder = requestDecoder
                                ).transcribe(
                                    audioFile,
                                    TranscriptionLanguage.ENGLISH
                                )
                            }

                            TranscriptionLanguage.MALAYALAM -> {
                                IndicMalayalamTranscriber(
                                    context = appContext,
                                    audioDecoder = requestDecoder
                                ).transcribe(
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


    private class ReusingAudioDecoder(
        private val delegate: AudioDecoder = AndroidAudioDecoder()
    ) : AudioDecoder {

        private var cachedFile: File? = null
        private var cachedSamples: FloatArray? = null

        override suspend fun decodeToPcm16kMono(
            audioFile: File
        ): Result<FloatArray> {

            if (cachedFile == audioFile) {
                cachedSamples?.let { samples ->
                    logd {
                        "UTTRA AUTO PCM: reused samples=${samples.size}"
                    }
                    return Result.success(samples)
                }
            }

            return delegate.decodeToPcm16kMono(audioFile)
                .onSuccess { samples ->
                    cachedFile = audioFile
                    cachedSamples = samples
                }
        }
    }

}
