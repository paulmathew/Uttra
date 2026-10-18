
package com.uttra.app.transcription

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class LanguageDetection(
    val language: TranscriptionLanguage?,
    val englishScore: Float,
    val malayalamScore: Float
) {
    val isCertain: Boolean
        get() = language != null
}

internal class WhisperLanguageDetector(
    private val context: Context,
    private val audioDecoder: AudioDecoder = AndroidAudioDecoder()
) {

    suspend fun detect(
        audioFile: File
    ): Result<LanguageDetection> = withContext(Dispatchers.IO) {

        var handle = 0L

        try {
            require(audioFile.isFile && audioFile.length() > 0L) {
                "Audio recording is missing or empty"
            }

            val modelFile = WhisperModelInstaller.install(
                context.applicationContext
            )

            handle = UttraNativeBridge.nativeCreateContext(
                modelFile.absolutePath
            )

            check(handle != 0L) {
                "Failed to initialize Whisper language detector"
            }

            val samples = audioDecoder
                .decodeToPcm16kMono(audioFile)
                .getOrThrow()


            check(samples.isNotEmpty()) {
                "Decoded recording contains no samples"
            }

            val rms = kotlin.math.sqrt(
                samples.fold(0.0) { sum, sample ->
                    val value = sample.toDouble()
                    sum + value * value
                } / samples.size
            )

            val peak = samples.maxOf {
                kotlin.math.abs(it)
            }

// Experimental near-silence rejection.
// Thresholds require further testing with quiet speech.
            if (rms < 0.003 && peak < 0.015f) {
                return@withContext Result.success(
                    LanguageDetection(
                        language = null,
                        englishScore = 0f,
                        malayalamScore = 0f
                    )
                )
            }

            val scores = UttraNativeBridge.nativeDetectLanguage(
                contextHandle = handle,
                audioSamples = samples
            )

            check(scores.size == 2) {
                "Invalid language detection result"
            }

            val englishScore = scores[0]
            val malayalamScore = scores[1]

            check(
                englishScore.isFinite() &&
                malayalamScore.isFinite() &&
                englishScore >= 0f &&
                malayalamScore >= 0f
            ) {
                "Invalid language probabilities"
            }

            val strongest = maxOf(
                englishScore,
                malayalamScore
            )

            val weakest = minOf(
                englishScore,
                malayalamScore
            )

            // Experimental thresholds, subject to further testing.
            // Do not force a language when evidence is weak.
            val reliableEnough =
                strongest >= 0.10f &&
                strongest >= weakest * 5f

            val detectedLanguage = when {
                !reliableEnough -> null

                englishScore > malayalamScore ->
                    TranscriptionLanguage.ENGLISH

                else ->
                    TranscriptionLanguage.MALAYALAM
            }

            Result.success(
                LanguageDetection(
                    language = detectedLanguage,
                    englishScore = englishScore,
                    malayalamScore = malayalamScore
                )
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (handle != 0L) {
                UttraNativeBridge.nativeFreeContext(handle)
            }
        }
    }
}
