
package com.uttra.app.transcription

object UttraNativeBridge {

    init {
        System.loadLibrary("uttra_native")
    }

//    external fun nativeHello(): String
    external fun nativeWhisperInfo(): String

    external fun nativeLoadModel(modelPath: String): Boolean

    external fun nativeCreateContext(
        modelPath: String
    ): Long

    external fun nativeFreeContext(
        contextHandle: Long
    )


    external fun nativeTranscribe(
        contextHandle: Long,
        audioSamples: FloatArray,
        languageCode: String?
    ): String

}
