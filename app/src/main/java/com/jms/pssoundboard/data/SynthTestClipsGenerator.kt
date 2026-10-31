package com.jms.pssoundboard.data

import android.content.Context
import com.jms.pssoundboard.audio.WavUtils
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Programmatically synthesizes 12 distinct mono 16-bit / 44.1 kHz WAV clips (+ a 1 kHz
 * loopback test tone) into `filesDir/builtin_clips/` so every pad makes real sound on first launch.
 */
object SynthTestClipsGenerator {

    const val SAMPLE_RATE = 44_100
    private const val TWO_PI = 2.0 * PI
    const val TEST_TONE_ID = "loopback_test_tone"

    data class BuiltinClip(val id: String, val title: String, val colorHex: String)

    val CLIPS = listOf(
        BuiltinClip("censor_beep", "Censor Beep 1kHz", "#EF4444"),
        BuiltinClip("airhorn", "Airhorn", "#F59E0B"),
        BuiltinClip("radar_ping", "Radar Ping", "#06B6D4"),
        BuiltinClip("pit_radio", "Pit Radio Beep", "#22C55E"),
        BuiltinClip("sad_trombone", "Sad Trombone", "#A855F7"),
        BuiltinClip("red_alert", "Red Alert Siren", "#DC2626"),
        BuiltinClip("bass_drop", "Bass Drop", "#6366F1"),
        BuiltinClip("laser_sweep", "Laser Sweep", "#EC4899"),
        BuiltinClip("victory_fanfare", "Victory Fanfare", "#EAB308"),
        BuiltinClip("wrong_buzzer", "Wrong Buzzer", "#F97316"),
        BuiltinClip("rimshot", "Ba-Dum-Tss", "#14B8A6"),
        BuiltinClip("power_up", "Power Up", "#3B82F6"),
    )

    fun dir(context: Context) = File(context.filesDir, "builtin_clips")

    fun fileFor(context: Context, id: String) = File(dir(context), "$id.wav")

    fun clip(id: String): BuiltinClip? = CLIPS.firstOrNull { it.id == id }

    /** Generates any missing clip. Safe to call on every launch (cheap when already present). */
    fun ensureGenerated(context: Context): Int {
        var generated = 0
        (CLIPS.map { it.id } + TEST_TONE_ID).forEach { id ->
            val f = fileFor(context, id)
            if (!f.exists() || f.length() <= WavUtils.HEADER_SIZE) {
                val raw = synth(id)
                val processed = WavUtils.applyFades(
                    if (id == TEST_TONE_ID) raw else WavUtils.normalize(raw),
                    SAMPLE_RATE,
                )
                WavUtils.writeMonoWav(f, processed, SAMPLE_RATE)
                generated++
            }
        }
        return generated
    }

    // ---------------------------------------------------------------------------------------
    // Synthesis
    // ---------------------------------------------------------------------------------------

    private fun synth(id: String): FloatArray = when (id) {
        "censor_beep" -> censorBeep()
        "airhorn" -> airhorn()
        "radar_ping" -> radarPing()
        "pit_radio" -> pitRadio()
        "sad_trombone" -> sadTrombone()
        "red_alert" -> redAlert()
        "bass_drop" -> bassDrop()
        "laser_sweep" -> laserSweep()
        "victory_fanfare" -> victoryFanfare()
        "wrong_buzzer" -> wrongBuzzer()
        "rimshot" -> rimshot()
        "power_up" -> powerUp()
        TEST_TONE_ID -> testTone()
        else -> error("Unknown builtin clip $id")
    }

    private fun buffer(seconds: Double) = FloatArray((seconds * SAMPLE_RATE).toInt())
    private fun t(i: Int) = i.toDouble() / SAMPLE_RATE

    /** Attack / release envelope for a note that lives in [start, start+dur). */
    private fun env(time: Double, start: Double, dur: Double, attack: Double = 0.005, release: Double = 0.03): Double {
        val local = time - start
        if (local < 0 || local > dur) return 0.0
        val a = if (local < attack) local / attack else 1.0
        val r = if (local > dur - release) (dur - local) / release else 1.0
        return (a * r).coerceIn(0.0, 1.0)
    }

    private fun saw(phase: Double) = 2.0 * (phase - floor(phase + 0.5))
    private fun square(phase: Double) = sign(sin(TWO_PI * phase))

    private fun testTone(): FloatArray {
        val out = buffer(3.0)
        for (i in out.indices) out[i] = (0.5 * sin(TWO_PI * 1000.0 * t(i))).toFloat() // -6 dBFS
        return out
    }

    private fun censorBeep(): FloatArray {
        val out = buffer(0.6)
        for (i in out.indices) out[i] = (sin(TWO_PI * 1000.0 * t(i)) * env(t(i), 0.0, 0.6)).toFloat()
        return out
    }

    private fun airhorn(): FloatArray {
        val out = buffer(1.35)
        val freqs = doubleArrayOf(349.23, 440.0, 523.25, 698.46)
        val phases = DoubleArray(freqs.size)
        val blasts = listOf(0.0 to 0.22, 0.30 to 0.22, 0.60 to 0.75)
        for (i in out.indices) {
            val time = t(i)
            val e = blasts.sumOf { (s, d) -> env(time, s, d, 0.01, 0.05) }
            val bend = 1.0 - 0.03 * exp(-((time % 0.3) * 25))
            var v = 0.0
            for (k in freqs.indices) {
                phases[k] += freqs[k] * bend / SAMPLE_RATE
                v += saw(phases[k])
            }
            out[i] = (tanh(v * 0.6) * e).toFloat()
        }
        return out
    }

    private fun radarPing(): FloatArray {
        val out = buffer(1.6)
        val echoes = listOf(0.0 to 1.0, 0.45 to 0.4, 0.9 to 0.15)
        for (i in out.indices) {
            val time = t(i)
            var v = 0.0
            for ((start, amp) in echoes) {
                val local = time - start
                if (local >= 0) {
                    v += amp * exp(-local * 6.0) *
                        (sin(TWO_PI * 1450.0 * local) + 0.3 * sin(TWO_PI * 1453.0 * local)) *
                        (local / 0.002).coerceAtMost(1.0)
                }
            }
            out[i] = v.toFloat()
        }
        return out
    }

    private fun pitRadio(): FloatArray {
        val out = buffer(0.7)
        val rnd = Random(42)
        var lp = 0.0
        for (i in out.indices) {
            val time = t(i)
            val noise = rnd.nextDouble() * 2 - 1
            lp += 0.25 * (noise - lp)
            var v = 0.0
            v += lp * 0.5 * (env(time, 0.0, 0.08, 0.002, 0.01) + env(time, 0.5, 0.12, 0.002, 0.04))
            v += sin(TWO_PI * 880.0 * time) * env(time, 0.09, 0.15)
            v += sin(TWO_PI * 1320.0 * time) * env(time, 0.27, 0.15)
            out[i] = tanh(v * 1.6).toFloat()
        }
        return out
    }

    private fun sadTrombone(): FloatArray {
        val notes = listOf(293.66 to 0.45, 277.18 to 0.45, 261.63 to 0.45, 246.94 to 1.35)
        val total = notes.sumOf { it.second }
        val out = buffer(total)
        var phase = 0.0
        val harmonics = doubleArrayOf(1.0, 0.6, 0.4, 0.25, 0.15, 0.08)
        for (i in out.indices) {
            val time = t(i)
            var acc = 0.0
            var idx = 0
            while (idx < notes.lastIndex && time >= acc + notes[idx].second) { acc += notes[idx].second; idx++ }
            val (f, d) = notes[idx]
            val local = time - acc
            val isLast = idx == notes.lastIndex
            val vibrato = if (isLast) 1.0 + 0.015 * sin(TWO_PI * 5.0 * local) * (local / 0.3).coerceAtMost(1.0) else 1.0
            val slide = if (isLast) 1.0 - 0.04 * (local / d) else 1.0
            phase += f * vibrato * slide / SAMPLE_RATE
            var v = 0.0
            for (h in harmonics.indices) v += harmonics[h] * sin(TWO_PI * phase * (h + 1))
            val wah = 0.6 + 0.4 * sin(PI * (local / d).coerceIn(0.0, 1.0))
            out[i] = (v * wah * env(local, 0.0, d, 0.03, 0.08)).toFloat()
        }
        return out
    }

    private fun redAlert(): FloatArray {
        val out = buffer(2.1)
        var phase = 0.0
        for (i in out.indices) {
            val time = t(i)
            val cycle = time % 0.7
            val sweeping = cycle < 0.55
            val f = 450.0 + 500.0 * (cycle / 0.55).coerceAtMost(1.0)
            phase += f / SAMPLE_RATE
            val e = if (sweeping) env(cycle, 0.0, 0.55, 0.01, 0.04) else 0.0
            out[i] = (tanh(3.0 * sin(TWO_PI * phase)) * e).toFloat()
        }
        return out
    }

    private fun bassDrop(): FloatArray {
        val out = buffer(2.2)
        val rnd = Random(7)
        var phase = 0.0
        for (i in out.indices) {
            val time = t(i)
            val f = 38.0 + (160.0 - 38.0) * exp(-time * 2.2)
            phase += f / SAMPLE_RATE
            val click = if (time < 0.01) (rnd.nextDouble() * 2 - 1) * (1 - time / 0.01) else 0.0
            val body = sin(TWO_PI * phase) * exp(-time * 0.9)
            out[i] = (tanh(2.5 * body) + 0.4 * click).toFloat() * env(time, 0.0, 2.2, 0.002, 0.2).toFloat()
        }
        return out
    }

    private fun laserSweep(): FloatArray {
        val out = buffer(0.45)
        var phase = 0.0
        for (i in out.indices) {
            val time = t(i)
            val f = 3200.0 * (180.0 / 3200.0).pow(time / 0.45)
            phase += f / SAMPLE_RATE
            out[i] = (0.6 * square(phase) * exp(-time * 4.0) * env(time, 0.0, 0.45, 0.002, 0.03)).toFloat()
        }
        return out
    }

    private fun victoryFanfare(): FloatArray {
        val notes = listOf(523.25 to 0.15, 659.25 to 0.15, 783.99 to 0.15, 1046.5 to 0.75)
        val out = buffer(notes.sumOf { it.second })
        var phase = 0.0
        for (i in out.indices) {
            val time = t(i)
            var acc = 0.0
            var idx = 0
            while (idx < notes.lastIndex && time >= acc + notes[idx].second) { acc += notes[idx].second; idx++ }
            val (f, d) = notes[idx]
            val local = time - acc
            phase += f / SAMPLE_RATE
            val brass = tanh(2.5 * sin(TWO_PI * phase)) * 0.7 + 0.3 * sin(TWO_PI * phase * 2)
            out[i] = (brass * env(local, 0.0, d, 0.01, 0.04)).toFloat()
        }
        return out
    }

    private fun wrongBuzzer(): FloatArray {
        val out = buffer(0.85)
        var p1 = 0.0
        var p2 = 0.0
        for (i in out.indices) {
            val time = t(i)
            p1 += 140.0 / SAMPLE_RATE
            p2 += 147.0 / SAMPLE_RATE
            val e = env(time, 0.0, 0.38, 0.005, 0.03) + env(time, 0.45, 0.4, 0.005, 0.06)
            out[i] = (tanh(1.5 * (square(p1) + saw(p2))) * e).toFloat()
        }
        return out
    }

    private fun rimshot(): FloatArray {
        val out = buffer(1.5)
        val rnd = Random(1234)
        var prevNoise = 0.0
        var p1 = 0.0
        var p2 = 0.0
        for (i in out.indices) {
            val time = t(i)
            var v = 0.0
            if (time < 0.25) {
                p1 += (120.0 + 60.0 * exp(-time * 30)) / SAMPLE_RATE
                v += sin(TWO_PI * p1) * exp(-time * 18)
            }
            val l2 = time - 0.2
            if (l2 >= 0 && l2 < 0.3) {
                p2 += (90.0 + 50.0 * exp(-l2 * 30)) / SAMPLE_RATE
                v += sin(TWO_PI * p2) * exp(-l2 * 14)
            }
            val l3 = time - 0.45
            val noise = rnd.nextDouble() * 2 - 1
            val hp = noise - prevNoise // crude high-pass for cymbal sizzle
            prevNoise = noise
            if (l3 >= 0) v += 0.5 * hp * exp(-l3 * 3.2)
            out[i] = v.toFloat()
        }
        return out
    }

    private fun powerUp(): FloatArray {
        val out = buffer(0.9)
        var phase = 0.0
        for (i in out.indices) {
            val time = t(i)
            val step = floor(time / 0.05)
            val f = 330.0 * 2.0.pow(step * 2.0 / 12.0)
            phase += f / SAMPLE_RATE
            val tone = tanh(2.0 * sin(TWO_PI * phase)) * (0.8 + 0.2 * sin(TWO_PI * 30.0 * time))
            out[i] = (tone * env(time, 0.0, 0.9, 0.005, 0.12)).toFloat()
        }
        return out
    }
}
