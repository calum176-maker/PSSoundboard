package com.jms.pssoundboard.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder

/**
 * Decodes any platform-supported audio file (mp3 / ogg / wav / m4a / flac...) to mono float PCM.
 *
 * Down-mixing to mono matters for the loopback: SoundPool is hard-panned LEFT, so a stereo
 * clip would lose its right-channel content. A mono clip delivers the full mix to the Tip.
 */
object AudioDecoder {

    class Decoded(val samples: FloatArray, val sampleRate: Int) {
        val durationSec: Float get() = samples.size.toFloat() / sampleRate
    }

    private const val TIMEOUT_US = 10_000L

    fun decode(context: Context, uri: Uri, maxSeconds: Int = 30): Decoded {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        return decode(extractor, maxSeconds)
    }

    fun decode(file: File, maxSeconds: Int = 30): Decoded {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        return decode(extractor, maxSeconds)
    }

    private fun decode(extractor: MediaExtractor, maxSeconds: Int): Decoded {
        var codec: MediaCodec? = null
        try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found in file")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = FloatArrayBuilder(sampleRate * 4)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } else if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(ByteOrder.nativeOrder())
                        val ch = channels.coerceAtLeast(1)
                        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= ch) {
                                var sum = 0f
                                repeat(ch) { sum += fb.get() }
                                out.add(sum / ch)
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= ch) {
                                var sum = 0f
                                repeat(ch) { sum += sb.get() / 32768f }
                                out.add(sum / ch)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (out.size >= maxSeconds * sampleRate) outputDone = true
                }
            }
            val samples = out.toArray()
            val maxFrames = maxSeconds * sampleRate
            return Decoded(if (samples.size > maxFrames) samples.copyOf(maxFrames) else samples, sampleRate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
