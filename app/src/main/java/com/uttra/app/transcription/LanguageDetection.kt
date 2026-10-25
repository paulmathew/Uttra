
package com.uttra.app.transcription

import android.content.Context
import com.uttra.app.core.logging.logd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.os.SystemClock

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


            val modelSetupStartedMs = SystemClock.elapsedRealtime()

            val modelFile = WhisperModelInstaller.install(
                context.applicationContext
            )

            handle = UttraNativeBridge.nativeCreateContext(
                modelFile.absolutePath
            )

            check(handle != 0L) {
                "Failed to initialize Whisper language detector"
            }

            logd {
                "UTTRA LATENCY: stage=lid_model_setup " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - modelSetupStartedMs}"
            }



            val decodeStartedMs = SystemClock.elapsedRealtime()

            val samples = audioDecoder
                .decodeToPcm16kMono(audioFile)
                .getOrThrow()

            logd {
                "UTTRA LATENCY: stage=lid_audio_decode " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - decodeStartedMs}"
            }


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
                logd {
                    "UTTRA AUTO DETECT: decision=REJECTED reason=NEAR_SILENCE " +
                            "durationSec=${samples.size / 16000f} " +
                            "rms=$rms peak=$peak"
                }
                return@withContext Result.success(
                    LanguageDetection(
                        language = null,
                        englishScore = 0f,
                        malayalamScore = 0f
                    )
                )
            }


            val inferenceStartedMs = SystemClock.elapsedRealtime()

            val scores = UttraNativeBridge.nativeDetectLanguage(
                contextHandle = handle,
                audioSamples = samples
            )

            logd {
                "UTTRA LATENCY: stage=lid_inference " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - inferenceStartedMs}"
            }


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

            // Preserve the existing conservative AUTO rule for both languages.
            val passesOriginalRule =
                strongest >= 0.10f &&
                strongest >= weakest * 5f

            // Experimental 7.7B: allow strong English evidence with a 2x margin.
            // This is English-only: the Malayalam acceptance rule is unchanged.
            val passesRelaxedEnglishRule =
                englishScore > malayalamScore &&
                englishScore >= 0.35f &&
                englishScore >= malayalamScore * 2f

            val detectedLanguage = when {
                passesOriginalRule -> {
                    if (englishScore > malayalamScore) {
                        TranscriptionLanguage.ENGLISH
                    } else {
                        TranscriptionLanguage.MALAYALAM
                    }
                }

                passesRelaxedEnglishRule -> TranscriptionLanguage.ENGLISH
                else -> null
            }

            // Log only numeric scores and the decision; never speech content.
            val reason = when {
                passesOriginalRule -> "ACCEPTED"
                passesRelaxedEnglishRule -> "ACCEPTED_ENGLISH_RELAXED"
                strongest < 0.10f -> "LOW_SCORE"
                else -> "AMBIGUOUS_SCORES"
            }
            logd {
                "UTTRA AUTO DETECT: decision=${detectedLanguage ?: "REJECTED"} " +
                        "reason=$reason english=$englishScore " +
                        "malayalam=$malayalamScore rms=$rms peak=$peak " +
                        "durationSec=${samples.size / 16000f}"
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
