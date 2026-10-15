
package com.uttra.app.transcription

internal object AudioResampler {

    fun resampleTo16k(
        input: FloatArray,
        sourceSampleRate: Int
    ): FloatArray {
        require(sourceSampleRate > 0) {
            "Invalid source sample rate"
        }

        if (input.isEmpty()) {
            return floatArrayOf()
        }

        if (sourceSampleRate == 16_000) {
            return input
        }

        val targetSampleRate = 16_000

        val outputSize = (
                input.size.toDouble() *
                        targetSampleRate / sourceSampleRate
                ).toInt()

        val output = FloatArray(outputSize)

        for (index in output.indices) {
            val sourcePosition =
                index.toDouble() *
                        sourceSampleRate / targetSampleRate

            val leftIndex = sourcePosition.toInt()
                .coerceIn(0, input.lastIndex)

            val rightIndex = (leftIndex + 1)
                .coerceAtMost(input.lastIndex)

            val fraction =
                (sourcePosition - leftIndex).toFloat()

            output[index] =
                input[leftIndex] * (1f - fraction) +
                        input[rightIndex] * fraction
        }

        return output
    }
}
