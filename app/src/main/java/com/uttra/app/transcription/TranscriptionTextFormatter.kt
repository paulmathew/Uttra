package com.uttra.app.transcription

internal object TranscriptionTextFormatter {

    private val extraWhitespace = Regex("\\s+")
    private val spaceBeforePunctuation =
        Regex("\\s+([,.;:!?])")

    // The native Whisper decoder may return internal control markers instead of speech.
    // Do not allow those markers to reach text insertion.
    private val blankAudioMarkers = Regex(
        """\[\s*(?:BLANK_AUDIO|NO_SPEECH)\s*\]""",
        RegexOption.IGNORE_CASE
    )
    private val whisperControlMarkers = Regex("""<\|[^|>\r\n]{1,64}\|>""")

    fun removeRecognitionMarkers(rawText: String): String {
        return rawText
            .replace(blankAudioMarkers, " ")
            .replace(whisperControlMarkers, " ")
            .replace(extraWhitespace, " ")
            .trim()
    }

    fun format(rawText: String): String {
        val normalized = removeRecognitionMarkers(rawText)
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
