package com.uttra.app.audio

import java.io.File

interface AudioRecorder {

    fun start(): Result<Unit>
    fun stop(): Result<File>
    fun cancel()
}