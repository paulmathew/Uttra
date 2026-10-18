
package com.uttra.app.transcription

import android.content.Context
import com.uttra.app.BuildConfig
import java.io.File

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

        return when (language) {

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
                    val detection = languageDetector
                        .detect(audioFile)
                        .getOrElse { error ->
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
    }
}
