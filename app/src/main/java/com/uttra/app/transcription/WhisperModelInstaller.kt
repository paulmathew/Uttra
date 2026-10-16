
package com.uttra.app.transcription

import android.content.Context
import java.io.File

internal object WhisperModelInstaller {

    fun install(context: Context): File {
        val modelFile = File(
            context.filesDir,
            "ggml-tiny.bin"
        )

        if (!modelFile.exists() ||
            modelFile.length() == 0L
        ) {
            context.assets.open(
                "models/ggml-tiny.bin"
            ).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return modelFile
    }
}
