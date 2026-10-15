
package com.uttra.app.transcription

internal data class DecodedAudio(
    val pcmBytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int
)
