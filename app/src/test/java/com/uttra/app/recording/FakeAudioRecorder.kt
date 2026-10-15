
package com.uttra.app.recording

import com.uttra.app.audio.AudioRecorder
import java.io.File

class FakeAudioRecorder : AudioRecorder {

    var isRecording = false
        private set

    var startCount = 0
        private set

    var stopCount = 0
        private set

    var cancelCount = 0
        private set

    override fun start(): Result<Unit> {
        startCount++

        if (failOnStart) {
            return Result.failure(
                IllegalStateException("Simulated start failure")
            )
        }
        if (isRecording) {
            return Result.failure(
                IllegalStateException("Already recording")
            )
        }

        isRecording = true
        return Result.success(Unit)
    }

    override fun stop(): Result<File> {
        stopCount++
        if (failOnStop) {
            return Result.failure(
                IllegalStateException("Simulated stop failure")
            )
        }
        if (!isRecording) {
            return Result.failure(
                IllegalStateException("Not recording")
            )
        }

        isRecording = false

        return Result.success(
            File("fake_recording.m4a")
        )
    }

    override fun cancel() {
        cancelCount++
        isRecording = false
    }
    var failOnStart = false
    var failOnStop = false
}
