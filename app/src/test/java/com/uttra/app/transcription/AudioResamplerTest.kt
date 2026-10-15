
package com.uttra.app.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioResamplerTest {

    @Test
    fun resample44100To16000_producesCorrectSize() {
        val input = FloatArray(44_100) { 0.5f }

        val output = AudioResampler.resampleTo16k(
            input = input,
            sourceSampleRate = 44_100
        )

        assertEquals(16_000, output.size)
        assertTrue(
            output.all { it == 0.5f }
        )
    }
    @Test
    fun already16k_preservesSamples() {
        val input = floatArrayOf(
            -0.5f, 0.0f, 0.5f, 1.0f
        )

        val output = AudioResampler.resampleTo16k(
            input,
            16_000
        )

        assertTrue(input.contentEquals(output))
    }
}
