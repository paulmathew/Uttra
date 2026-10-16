
package com.uttra.app.transcription

import android.content.Context
import com.uttra.app.core.logging.logd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperNativeTranscriber(
    private val context: Context,
    private val audioDecoder: AudioDecoder =
        AndroidAudioDecoder()
) : SpeechTranscriber {

    override suspend fun transcribe(
        audioFile: File,
        language: TranscriptionLanguage
    ): Result<String> = withContext(Dispatchers.IO) {
        try {

            logd { "WHISPER STEP 1: Installing model" }

            val modelFile = WhisperModelInstaller.install(
                context.applicationContext
            )

            logd {
                "WHISPER MODEL: ${modelFile.name}, " +
                        "size=${modelFile.length()} bytes"
            }

            logd { "WHISPER STEP 2: Creating native context" }

            val handle = UttraNativeBridge.nativeCreateContext(
                modelFile.absolutePath
            )

            check(handle != 0L) {
                "Failed to initialize Whisper Base"
            }

            try {
                logd { "WHISPER STEP 3: Decoding audio" }

                val samples = audioDecoder
                    .decodeToPcm16kMono(audioFile)
                    .getOrThrow()

                logd {
                    "WHISPER STEP 4: Starting inference, samples=${samples.size}"
                }

                val recognizedText = UttraNativeBridge.nativeTranscribe(
                    contextHandle = handle,
                    audioSamples = samples,
                    languageCode = language.code
                )

                logd {
                    "WHISPER STEP 5: Inference completed"
                }

                Result.success(recognizedText.trim())

            } finally {
                logd { "WHISPER STEP 6: Freeing native context" }

                UttraNativeBridge.nativeFreeContext(handle)
            }


        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
