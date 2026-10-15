
package com.uttra.app.transcription

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AudioChannelConverterTest {

    @Test
    fun stereoToMono_averagesChannels() {
        val stereo = floatArrayOf(
            0.4f, 0.2f,
            -0.2f, 0.6f
        )

        val mono = AudioChannelConverter.toMono(
            stereo,
            2
        )

        assertArrayEquals(
            floatArrayOf(0.3f, 0.2f),
            mono,
            0.0001f
        )
    }

    @Test
    fun monoInput_preservesSamples() {
        val input = floatArrayOf(
            -0.5f, 0f, 0.5f
        )

        val output = AudioChannelConverter.toMono(
            input,
            1
        )

        assertArrayEquals(input, output, 0f)
    }
}
