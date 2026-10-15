package com.uttra.app.recording

import com.uttra.app.audio.AudioRecorder
import java.io.File

class RecordingSessionController(private val audioRecorder: AudioRecorder) {

    var state = RecordingState.IDLE
        private set

    fun startRecording(): Result<Unit> {
        if (state != RecordingState.IDLE) {
            return Result.failure(
                IllegalStateException("Recording already active")
            )
        }

        return try {
            audioRecorder.start()
                .onSuccess {
                    state = RecordingState.RECORDING
                }
                .onFailure {
                    runCatching { audioRecorder.cancel() }
                }
        } catch (e: Exception) {
            runCatching { audioRecorder.cancel() }
            Result.failure(e)
        }
    }

    fun stopRecording(): Result<File> {
        if (state != RecordingState.RECORDING) {
            return Result.failure(
                IllegalStateException("No active recording")
            )
        }

        return try {
            audioRecorder.stop()
                .also {
                    state = RecordingState.IDLE
                }
        } catch (e: Exception) {
            state = RecordingState.IDLE
            runCatching { audioRecorder.cancel() }
            Result.failure(e)
        }
    }
    fun cancelRecording() {
        try {
            audioRecorder.cancel()
        } finally {
            state = RecordingState.IDLE
        }
    }
}