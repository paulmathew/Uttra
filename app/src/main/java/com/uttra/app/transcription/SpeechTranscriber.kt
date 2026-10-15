package com.uttra.app.transcription

import java.io.File

interface SpeechTranscriber {
    suspend fun transcribe(audioFile: File,language: TranscriptionLanguage): Result<String>
}