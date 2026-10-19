
package com.uttra.app.transcription

internal object TranscriptionTextFormatter {

    private val extraWhitespace = Regex("\\s+")
    private val spaceBeforePunctuation =
        Regex("\\s+([,.;:!?])")

    fun format(rawText: String): String {
        val normalized = rawText
            .trim()
            .replace(extraWhitespace, " ")
            .replace(spaceBeforePunctuation, "$1")

        if (normalized.isEmpty()) {
            return normalized
        }

        val capitalized = normalized.replaceFirstChar {
            it.titlecase()
        }

        val lastChar = capitalized.last()

        return if (lastChar in ".!?…।") {
            capitalized
        } else {
            "$capitalized."
        }
    }
}
