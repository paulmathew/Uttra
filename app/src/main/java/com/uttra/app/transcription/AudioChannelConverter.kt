
package com.uttra.app.transcription

internal object AudioChannelConverter {

    fun toMono(
        samples: FloatArray,
        channelCount: Int
    ): FloatArray {
        require(channelCount == 1 || channelCount == 2) {
            "Only mono and stereo audio are supported"
        }

        if (channelCount == 1) {
            return samples
        }

        require(samples.size % 2 == 0) {
            "Invalid interleaved stereo sample count"
        }

        return FloatArray(samples.size / 2) { index ->
            val left = samples[index * 2]
            val right = samples[index * 2 + 1]

            (left + right) / 2f
        }
    }
}
