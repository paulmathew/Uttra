package com.uttra.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    override fun start(): Result<Unit> {
        if (recorder != null) {
            return Result.failure(
                Exception("recorder already started")
            )
        }
        return runCatching {
            val file = File.createTempFile(
                "uttra_",
                ".m4a",
                context.cacheDir
            )

            outputFile = file

            val mediaRecorder = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder = mediaRecorder

            with(mediaRecorder) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

        }.onFailure {
            releaseRecorder()
            outputFile?.delete()
            outputFile = null
        }
    }

    override fun stop(): Result<File> {

        val activeRecorder = recorder
            ?: return Result.failure(
                IllegalStateException("No active recording")
            )

        return runCatching {
            activeRecorder.stop()
            val file = requireNotNull(outputFile)
            releaseRecorder()
            outputFile = null
            file
        }.onFailure {
            releaseRecorder()
            outputFile?.delete()
            outputFile = null
        }
    }

    override fun cancel() {
        releaseRecorder()
        outputFile?.delete()
        outputFile = null
    }

    private fun releaseRecorder() {
        recorder?.run {
            try {
                reset()
                release()
            } catch (_: Exception) {
                // Continue cleanup.
            }
        }
        recorder = null
    }
}
