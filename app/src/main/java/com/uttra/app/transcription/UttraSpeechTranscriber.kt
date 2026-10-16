
package com.uttra.app.transcription

import android.content.Context
import java.io.File

internal class UttraSpeechTranscriber(
    context: Context
) : SpeechTranscriber {

    private val englishTranscriber =
        WhisperNativeTranscriber(context.applicationContext)

    private val malayalamTranscriber =
        IndicMalayalamTranscriber(context.applicationContext)

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
                Result.failure(
                    UnsupportedOperationException(
                        "Automatic language detection is not implemented yet"
                    )
                )
            }
        }
    }
}
