package com.jms.pssoundboard.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Minimal mono 16-bit PCM WAV writer + DSP helpers (trim / normalize / fades). */
object WavUtils {

    const val HEADER_SIZE = 44

    /** -1 dBFS peak target, leaves headroom for the TRRS loopback divider. */
    const val NORMALIZE_TARGET_PEAK = 0.891f

    fun writeMonoWav(file: File, samples: FloatArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val dataSize = samples.size * 2
        val tmp = File(file.parentFile, file.name + ".tmp")
        BufferedOutputStream(FileOutputStream(tmp), 64 * 1024).use { out ->
            out.write(header(dataSize, sampleRate, channels = 1))
            val chunk = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                if (chunk.remaining() < 2) {
                    out.write(chunk.array(), 0, chunk.position()); chunk.clear()
                }
                val v = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
                chunk.putShort(v.toShort())
            }
            out.write(chunk.array(), 0, chunk.position())
        }
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true); tmp.delete()
        }
    }

    fun header(dataSize: Int, sampleRate: Int, channels: Int): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                       // PCM fmt chunk size
            putShort(1)                      // AudioFormat = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
    }

    fun peak(samples: FloatArray): Float {
        var p = 0f
        for (s in samples) p = max(p, abs(s))
        return p
    }

    fun normalize(samples: FloatArray, targetPeak: Float = NORMALIZE_TARGET_PEAK): FloatArray {
        val p = peak(samples)
        if (p < 1e-4f) return samples.copyOf()
        val g = targetPeak / p
        return FloatArray(samples.size) { samples[it] * g }
    }

    fun trim(samples: FloatArray, sampleRate: Int, startSec: Float, endSec: Float): FloatArray {
        val start = (startSec * sampleRate).toInt().coerceIn(0, samples.size)
        val end = (endSec * sampleRate).toInt().coerceIn(start, samples.size)
        return samples.copyOfRange(start, end)
    }

    /** Short linear fades to kill clicks at clip boundaries. */
    fun applyFades(samples: FloatArray, sampleRate: Int, fadeMs: Int = 5): FloatArray {
        val n = min(samples.size / 2, sampleRate * fadeMs / 1000)
        if (n <= 0) return samples
        for (i in 0 until n) {
            val g = i.toFloat() / n
            samples[i] *= g
            samples[samples.size - 1 - i] *= g
        }
        return samples
    }

    /** Down-sampled absolute peaks for waveform drawing. */
    fun waveformPeaks(samples: FloatArray, buckets: Int): FloatArray {
        if (samples.isEmpty() || buckets <= 0) return FloatArray(0)
        val per = max(1, samples.size / buckets)
        return FloatArray(buckets) { b ->
            var p = 0f
            val from = b * per
            val to = min(samples.size, from + per)
            for (i in from until to) p = max(p, abs(samples[i]))
            p
        }
    }
}

/** Growable float buffer (avoids boxing of MutableList<Float>). */
class FloatArrayBuilder(initialCapacity: Int = 44_100) {
    private var data = FloatArray(max(16, initialCapacity))
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
