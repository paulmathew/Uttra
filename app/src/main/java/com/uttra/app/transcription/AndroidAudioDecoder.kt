package com.uttra.app.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.uttra.app.core.logging.logd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import android.os.SystemClock

class AndroidAudioDecoder : AudioDecoder {

    override suspend fun decodeToPcm16kMono(
        audioFile: File
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        try {
            require(audioFile.isFile) {
                "Audio file does not exist: ${audioFile.absolutePath}"
            }

            require(audioFile.length() > 0L) {
                "Audio file is empty"
            }

            // 1. Decode M4A/AAC into validated PCM16.
            val decoded = decodeAacToPcm(audioFile)

            require(decoded.pcmBytes.isNotEmpty()) {
                "Decoder produced no audio"
            }

            // 2. Convert PCM16 bytes into FloatArray.
            val floatSamples = pcm16ToFloatArray(
                decoded.pcmBytes
            )

            // 3. Convert stereo to mono if necessary.
            val monoSamples = AudioChannelConverter.toMono(
                samples = floatSamples,
                channelCount = decoded.channelCount
            )

            // 4. Resample to Whisper's expected 16 kHz.
//            Result.success(
//                AudioResampler.resampleTo16k(
//                    input = monoSamples,
//                    sourceSampleRate = decoded.sampleRate
//                )
//            )
            val pcm16k = AudioResampler.resampleTo16k(
                input = monoSamples,
                sourceSampleRate = decoded.sampleRate
            )

            val peakAmplitude = pcm16k.maxOfOrNull {
                kotlin.math.abs(it)
            } ?: 0f

            logd {
                "Uttra PCM decoded: " +
                        "sourceRate=${decoded.sampleRate}, " +
                        "channels=${decoded.channelCount}, " +
                        "outputSamples=${pcm16k.size}, " +
                        "durationSeconds=${pcm16k.size / 16000.0}, " +
                        "peakAmplitude=$peakAmplitude"
            }

            Result.success(pcm16k)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private fun findAudioTrack(
        extractor: MediaExtractor
    ): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)

            val mimeType = format.getString(
                MediaFormat.KEY_MIME
            )

            if (mimeType?.startsWith("audio/") == true) {
                return index
            }
        }

        throw IllegalArgumentException(
            "No audio track found"
        )
    }


    private fun inspectAudioFile(audioFile: File) {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(audioFile.absolutePath) // open the m4a file

            val trackIndex = findAudioTrack(extractor) // locate the audio track
            extractor.selectTrack(trackIndex)// prepare the track for reading

            val format = extractor.getTrackFormat(trackIndex)

            val mimeType = format.getString(
                MediaFormat.KEY_MIME
            )

            val sampleRate = format.getInteger(
                MediaFormat.KEY_SAMPLE_RATE
            )

            val channelCount = format.getInteger(
                MediaFormat.KEY_CHANNEL_COUNT
            )

            logd {
                "Audio format: " +
                        "MIME=$mimeType, " +
                        "SampleRate=$sampleRate, " +
                        "Channels=$channelCount"
            }

        } finally {
            extractor.release()
        }
    }


    private fun createDecoder(
        format: MediaFormat
    ): MediaCodec {
        val mimeType = requireNotNull(
            format.getString(MediaFormat.KEY_MIME)
        ) {
            "Audio MIME type is missing"
        }

        return MediaCodec.createDecoderByType(mimeType)
            .apply {
                try {
                    configure(format, null, null, 0)
                    start()
                } catch (e: Exception) {
                    release()
                    throw e
                }
            }
    }


    private fun queueInputSample(
        extractor: MediaExtractor,
        decoder: MediaCodec
    ): Boolean {

        val inputBufferIndex = decoder.dequeueInputBuffer(
            10_000L
        )

        if (inputBufferIndex < 0) {
            return false
        }

        val inputBuffer = requireNotNull(
            decoder.getInputBuffer(inputBufferIndex)
        )

        inputBuffer.clear()

        val sampleSize = extractor.readSampleData(
            inputBuffer,
            0
        )

        if (sampleSize < 0) {
            decoder.queueInputBuffer(
                inputBufferIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )

            return true
        }

        val presentationTimeUs = extractor.sampleTime

        decoder.queueInputBuffer(
            inputBufferIndex,
            0,
            sampleSize,
            presentationTimeUs,
            0
        )

        extractor.advance()

        return false
    }


    private fun drainOutput(
        decoder: MediaCodec,
        output: ByteArrayOutputStream
    ): DrainResult {

        val bufferInfo = MediaCodec.BufferInfo()

        val outputIndex = decoder.dequeueOutputBuffer(
            bufferInfo,
            10_000L
        )

        return when {
            outputIndex >= 0 -> {
                try {
                    val outputBuffer = requireNotNull(
                        decoder.getOutputBuffer(outputIndex)
                    )

                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(
                        bufferInfo.offset + bufferInfo.size
                    )

                    val bufferFormat = decoder.getOutputFormat(
                        outputIndex
                    )

// Verify format before copying PCM.
                    validatePcmFormat(bufferFormat)

                    val bytes = ByteArray(bufferInfo.size)
                    outputBuffer.get(bytes)
                    output.write(bytes)

                    DrainResult.Buffer(
                        format = bufferFormat,
                        byteCount = bytes.size,
                        endOfStream = (
                                bufferInfo.flags and
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                ) != 0
                    )
                } finally {
                    decoder.releaseOutputBuffer(
                        outputIndex,
                        false
                    )
                }
            }

            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                DrainResult.FormatChanged(
                    decoder.outputFormat
                )
            }

            outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                DrainResult.TryAgain
            }

            else -> DrainResult.TryAgain
        }
    }


    private suspend fun decodeAacToPcm(
        audioFile: File
    ): DecodedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(audioFile.absolutePath)

            val trackIndex = findAudioTrack(extractor)
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            decoder = createDecoder(format)

            val output = ByteArrayOutputStream()
            var inputEnded = false
            var outputEnded = false

//            var receivedPcm = false
            var pcmFormat: MediaFormat? = null

            val timeoutMs = 30_000L
            val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime() < deadlineMs) {
                    "Audio decoding timed out after 30 seconds"
                }
                if (!inputEnded) {
                    inputEnded = queueInputSample(
                        extractor,
                        decoder
                    )
                }

                when (val result = drainOutput(decoder, output)) {

                    is DrainResult.FormatChanged -> {
                        validatePcmFormat(result.format)

                        val previousFormat = pcmFormat

                        if (previousFormat != null &&
                            !isSamePcmFormat(previousFormat, result.format)
                        ) {
                            error("PCM format changed during decoding")
                        }

                        pcmFormat = result.format
                    }

                    is DrainResult.Buffer -> {
                        validatePcmFormat(result.format)

                        val previousFormat = pcmFormat

                        if (previousFormat != null &&
                            !isSamePcmFormat(previousFormat, result.format)
                        ) {
                            error("PCM format changed during decoding")
                        }

                        pcmFormat = result.format
                        outputEnded = result.endOfStream
                    }

                    DrainResult.TryAgain -> {
                        // No decoded output yet.
                        // Continue feeding and draining.
                    }
                }
            }


            val finalFormat = requireNotNull(pcmFormat)

            return DecodedAudio(
                pcmBytes = output.toByteArray(),
                sampleRate = finalFormat.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE
                ),
                channelCount = finalFormat.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT
                )
            )

        } finally {
            try {
                decoder?.release()
            } finally {
                extractor.release()
            }
        }
    }


    private fun pcm16ToFloatArray(
        pcmBytes: ByteArray
    ): FloatArray {

        require(pcmBytes.size % 2 == 0) {
            "Invalid PCM16 data: odd byte count"
        }

        val samples = FloatArray(pcmBytes.size / 2)

        for (i in samples.indices) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()

            val sample = (high shl 8) or low

            samples[i] = sample.toShort() / 32768.0f
        }

        return samples
    }


    private fun validatePcmFormat(
        format: MediaFormat
    ) {
        val sampleRate = format.getInteger(
            MediaFormat.KEY_SAMPLE_RATE
        )

        val channelCount = format.getInteger(
            MediaFormat.KEY_CHANNEL_COUNT
        )

        val pcmEncoding = if (
            format.containsKey(MediaFormat.KEY_PCM_ENCODING)
        ) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

        require(sampleRate > 0) {
            "Invalid PCM sample rate: $sampleRate"
        }

        require(channelCount == 1 || channelCount == 2) {
            "Unsupported channel count: $channelCount"
        }

        require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            "Unsupported PCM encoding: $pcmEncoding"
        }

        logd {
            "Validated PCM output: " +
                    "sampleRate=$sampleRate, " +
                    "channels=$channelCount, " +
                    "encoding=$pcmEncoding"
        }
    }

    private fun isSamePcmFormat(
        first: MediaFormat,
        second: MediaFormat
    ): Boolean {
        fun encoding(format: MediaFormat): Int =
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

        return first.getInteger(MediaFormat.KEY_SAMPLE_RATE) ==
                second.getInteger(MediaFormat.KEY_SAMPLE_RATE) &&
                first.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ==
                second.getInteger(MediaFormat.KEY_CHANNEL_COUNT) &&
                encoding(first) == encoding(second)
    }


    private sealed interface DrainResult {
        data object TryAgain : DrainResult

        data class FormatChanged(
            val format: MediaFormat
        ) : DrainResult

        data class Buffer(
            val format: MediaFormat,
            val byteCount: Int,
            val endOfStream: Boolean
        ) : DrainResult
    }


}