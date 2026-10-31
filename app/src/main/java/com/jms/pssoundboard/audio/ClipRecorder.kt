package com.jms.pssoundboard.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Quick mono recorder for the Clip Creator. Prefers the phone's BUILT-IN mic so recording still
 * works while the TRRS loopback plug occupies the headset mic line.
 */
class ClipRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44_100
        const val MAX_SECONDS = 10
    }

    @SuppressLint("MissingPermission")
    suspend fun record(
        stopRequested: AtomicBoolean,
        onProgress: (elapsedMs: Long, level: Float) -> Unit,
    ): FloatArray = withContext(Dispatchers.IO) {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission not granted" }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, SAMPLE_RATE / 5 * 2),
        )
        val out = FloatArrayBuilder(SAMPLE_RATE * MAX_SECONDS)
        val chunk = ShortArray(SAMPLE_RATE / 20) // 50 ms
        val maxFrames = SAMPLE_RATE * MAX_SECONDS
        try {
            // Inside the try so the native recorder is released on every failure path.
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "Could not open the microphone. Another app may be using it."
            }
            val audioManager = context.getSystemService(AudioManager::class.java)
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?.let { recorder.setPreferredDevice(it) }

            recorder.startRecording()
            while (currentCoroutineContext().isActive && !stopRequested.get() && out.size < maxFrames) {
                val read = recorder.read(chunk, 0, chunk.size)
                // Negative values are errors (dead object, invalid operation) that never recover.
                check(read >= 0) { "The microphone stopped responding (error $read)" }
                if (read == 0) continue
                var peak = 0f
                for (i in 0 until read) {
                    val v = chunk[i] / 32768f
                    peak = max(peak, abs(v))
                    out.add(v)
                }
                onProgress(out.size * 1000L / SAMPLE_RATE, peak)
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        out.toArray()
    }
}
