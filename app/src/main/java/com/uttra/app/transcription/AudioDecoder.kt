package com.uttra.app.transcription

import java.io.File

interface AudioDecoder {
    suspend fun decodeToPcm16kMono(
        audioFile: File
    ): Result<FloatArray>
}