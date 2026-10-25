
package com.uttra.app.transcription

import android.content.Context
import com.uttra.app.core.logging.logd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.os.SystemClock

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

            val modelSetupStartedMs = SystemClock.elapsedRealtime()
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
            logd {
                "UTTRA LATENCY: stage=whisper_model_setup " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - modelSetupStartedMs}"
            }

            try {
                logd { "WHISPER STEP 3: Decoding audio" }

                val decodeStartedMs = SystemClock.elapsedRealtime()

                val samples = audioDecoder
                    .decodeToPcm16kMono(audioFile)
                    .getOrThrow()

                logd {
                    "UTTRA LATENCY: stage=whisper_audio_decode " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - decodeStartedMs}"
                }

                val inferenceStartedMs = SystemClock.elapsedRealtime()

                val recognizedText = UttraNativeBridge.nativeTranscribe(
                    contextHandle = handle,
                    audioSamples = samples,
                    languageCode = language.code
                )

                logd {
                    "UTTRA LATENCY: stage=whisper_inference " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - inferenceStartedMs}"
                }

                val cleanedText = TranscriptionTextFormatter
                    .removeRecognitionMarkers(recognizedText)

                if (cleanedText.none { it.isLetterOrDigit() }) {
                    // No usable speech: surface the existing Record Again UI.
                    Result.failure(
                        IllegalStateException("No recognizable speech detected")
                    )
                } else {
                    Result.success(cleanedText)
                }

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
