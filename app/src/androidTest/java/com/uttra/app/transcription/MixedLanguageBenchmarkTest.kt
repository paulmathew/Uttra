package com.uttra.app.transcription

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Milestone 7.8A: read-only comparison of existing speech engines on the same
 * recording. This test does not interact with the overlay or insert editor text.
 *
 * Fixture location (inside the installed debug app's private storage):
 *     files/speech_benchmarks/mixed_01.m4a
 */
class MixedLanguageBenchmarkTest {

    @Test
    fun compareExistingEnginesOnMixedRecording(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val recording = File(context.filesDir, "speech_benchmarks/mixed_01.m4a")

        assertTrue(
            "Missing nonempty fixture: ${recording.absolutePath}. See README.md for adb setup.",
            recording.isFile && recording.length() > 0L
        )

        Log.i(TAG, "MIXED BENCH START: name=${recording.name}, bytes=${recording.length()}")
        Log.i(TAG, "MIXED BENCH REFERENCE: This is going to be a mixed recording, ഞാൻ അങ്ങനെ നോക്കാം")

        evaluate("WHISPER_AUTO") {
            WhisperNativeTranscriber(context).transcribe(recording, TranscriptionLanguage.AUTO)
        }

        evaluate("WHISPER_ENGLISH") {
            WhisperNativeTranscriber(context).transcribe(recording, TranscriptionLanguage.ENGLISH)
        }

        evaluate("WHISPER_MALAYALAM") {
            WhisperNativeTranscriber(context).transcribe(recording, TranscriptionLanguage.MALAYALAM)
        }

        evaluate("INDIC_MALAYALAM") {
            IndicMalayalamTranscriber(context).transcribe(recording, TranscriptionLanguage.MALAYALAM)
        }

        Log.i(TAG, "MIXED BENCH COMPLETE: compare the four outputs against the reference")
    }

    private suspend fun evaluate(
        label: String,
        operation: suspend () -> Result<String>
    ) {
        val startMs = SystemClock.elapsedRealtime()
        try {
            operation().fold(
                onSuccess = { rawText ->
                    val elapsed = SystemClock.elapsedRealtime() - startMs
                    Log.i(TAG, "MIXED BENCH RESULT: engine=$label, elapsedMs=$elapsed, text='$rawText'")
                },
                onFailure = { error ->
                    val elapsed = SystemClock.elapsedRealtime() - startMs
                    Log.e(TAG, "MIXED BENCH FAILED: engine=$label, elapsedMs=$elapsed, reason=${error.message}", error)
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startMs
            Log.e(TAG, "MIXED BENCH ERROR: engine=$label, elapsedMs=$elapsed", error)
        }
    }

    private companion object {
        const val TAG = "UttraMixedBench"
    }
}
