package com.uttra.app.recording

import com.uttra.app.audio.AudioRecorder
import java.io.File

class RecordingSessionController(private val audioRecorder: AudioRecorder) {

    var state = RecordingState.IDLE
        private set

    var activeSessionId: String? = null
        private set

    fun startRecording(sessionId: String): Result<Unit> {
        require(sessionId.isNotBlank()) {
            "Session ID must not be blank"
        }
        if (state != RecordingState.IDLE) {
            return Result.failure(
                IllegalStateException("Recording already active")
            )
        }

        return try {
            audioRecorder.start()
                .onSuccess {
                    state = RecordingState.RECORDING
                    activeSessionId = sessionId

                }
                .onFailure {
                    runCatching { audioRecorder.cancel() }
                }
        } catch (e: Exception) {
            runCatching { audioRecorder.cancel() }
            Result.failure(e)
        }
    }

    fun stopRecording(sessionId: String): Result<File> {
        if (state != RecordingState.RECORDING ||
            activeSessionId != sessionId
        ) {
            return Result.failure(
                IllegalStateException("No active recording")
            )
        }

        return try {
            audioRecorder.stop()
                .also {result ->
                    if (result.isFailure) {
                        runCatching { audioRecorder.cancel() }
                    }
                    state = RecordingState.IDLE
                    activeSessionId = null
                }
        } catch (e: Exception) {
            state = RecordingState.IDLE
            runCatching { audioRecorder.cancel() }
            activeSessionId = null
            Result.failure(e)
        }
    }

    fun cancelRecording() {
        try {
            audioRecorder.cancel()
        } finally {
            state = RecordingState.IDLE
            activeSessionId= null
        }
    }
    fun cancelRecording(sessionId: String): Result<Unit> {
        if (
            state != RecordingState.RECORDING ||
            activeSessionId != sessionId
        ) {
            return Result.failure(
                IllegalStateException("No matching active recording")
            )
        }

        return runCatching {
            cancelRecording()
        }
    }
}