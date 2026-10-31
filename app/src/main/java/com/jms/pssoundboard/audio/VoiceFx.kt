package com.jms.pssoundboard.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Live-tweakable voice changer settings. Immutable: swap the whole object to change settings. */
data class VoiceFxParams(
    val presetId: String = VoicePreset.NATURAL.id,
    /** Pitch shift in semitones (-12..+12). 0 bypasses the shifter (zero added latency). */
    val pitchSemitones: Float = 0f,
    /** Ring-modulator mix (0..1): the classic robot / Dalek effect. */
    val robot: Float = 0f,
    val robotHz: Float = 60f,
    /** Walkie-talkie / pit-radio band-pass (≈500 Hz–2.8 kHz). */
    val radio: Boolean = false,
    /** Saturation amount (0..1). */
    val drive: Float = 0f,
    /** Echo mix and feedback (0..1). */
    val echo: Float = 0f,
    val echoMs: Float = 220f,
    /** Input gain applied to the phone mic (0.25..4). */
    val micGain: Float = 1.5f,
    /** Output level into the loopback / DAC left channel (0.1..1). */
    val outputGain: Float = 0.8f,
    /** Noise-gate threshold in dBFS (-70..-30). Keeps room noise out of the party. */
    val gateDb: Float = -50f,
) {
    companion object {
        const val CUSTOM_ID = "custom"
    }
}

enum class VoicePreset(
    val id: String,
    val label: String,
    val emoji: String,
    val colorHex: String,
    val params: VoiceFxParams,
) {
    NATURAL("natural", "Natural", "🎤", "#334155", VoiceFxParams(presetId = "natural")),
    CHIPMUNK("chipmunk", "Chipmunk", "🐿️", "#F59E0B", VoiceFxParams(presetId = "chipmunk", pitchSemitones = 8f)),
    DEEP("deep", "Deep Voice", "🗿", "#3B82F6", VoiceFxParams(presetId = "deep", pitchSemitones = -5f)),
    DEMON(
        "demon", "Demon", "😈", "#EF4444",
        VoiceFxParams(presetId = "demon", pitchSemitones = -9f, drive = 0.35f, echo = 0.2f, echoMs = 90f),
    ),
    ROBOT("robot", "Robot", "🤖", "#14B8A6", VoiceFxParams(presetId = "robot", robot = 0.9f, robotHz = 55f, drive = 0.15f)),
    RADIO("radio", "Pit Radio", "📻", "#22C55E", VoiceFxParams(presetId = "radio", radio = true, drive = 0.45f)),
    ALIEN(
        "alien", "Alien", "👽", "#A855F7",
        VoiceFxParams(presetId = "alien", pitchSemitones = 5f, robot = 0.6f, robotHz = 420f, echo = 0.15f, echoMs = 140f),
    ),
    CAVE("cave", "Cave Echo", "🦇", "#6366F1", VoiceFxParams(presetId = "cave", echo = 0.55f, echoMs = 260f)),
    ARNOLD("arnold", "Arnold (Terminator)", "🦾", "#3B82F6", VoiceFxParams(presetId = "arnold", pitchSemitones = -4.5f, drive = 0.25f)),
    VADER("vader", "Darth Vader", "🦹", "#1E293B", VoiceFxParams(presetId = "vader", pitchSemitones = -7.5f, robot = 0.3f, robotHz = 70f, echo = 0.25f)),
    MORGAN("morgan", "Morgan Freeman", "🎙️", "#D97706", VoiceFxParams(presetId = "morgan", pitchSemitones = -3f, echo = 0.15f, echoMs = 180f)),
    BATMAN("batman", "Dark Knight", "🦇", "#0F172A", VoiceFxParams(presetId = "batman", pitchSemitones = -6f, drive = 0.3f, echo = 0.2f)),
    RAMSAY("ramsay", "Gordon Ramsay", "👨‍🍳", "#DC2626", VoiceFxParams(presetId = "ramsay", pitchSemitones = 1.5f, radio = true, drive = 0.4f)),
    YODA("yoda", "Master Yoda", "🟢", "#10B981", VoiceFxParams(presetId = "yoda", pitchSemitones = 4.5f, echo = 0.2f));

    companion object {
        fun byId(id: String): VoicePreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Real-time mono effects chain: gate -> pitch shift -> ring mod -> radio band-pass -> drive -> echo.
 * Not thread-safe: own one instance per audio thread. Allocation-free in [process].
 */
class VoiceFxProcessor(private val sampleRate: Int) {

    // ---- Pitch shifter: two crossfaded taps sweeping a short delay line ---------------------
    private val psSize = 8192
    private val psMask = psSize - 1
    private val psBuf = FloatArray(psSize)
    private var psWrite = 0
    private var psPhase = 0f
    private val psWindow = (sampleRate * 0.040f).coerceAtMost((psSize - 8).toFloat()) // 40 ms grains

    // ---- Ring modulator ---------------------------------------------------------------------
    private var carrierPhase = 0.0

    // ---- Radio band-pass --------------------------------------------------------------------
    private val highPass = Biquad.highPass(sampleRate, 500.0)
    private val lowPass = Biquad.lowPass(sampleRate, 2800.0)

    // ---- Echo -------------------------------------------------------------------------------
    private val echoSize = sampleRate * 3 / 5 // 600 ms max
    private val echoBuf = FloatArray(echoSize)
    private var echoWrite = 0

    // ---- Noise gate -------------------------------------------------------------------------
    private var envelope = 0f
    private var gateGain = 0f
    private var holdRemaining = 0
    private val holdSamples = sampleRate * 15 / 100 // 150 ms

    fun reset() {
        psBuf.fill(0f); echoBuf.fill(0f)
        psWrite = 0; psPhase = 0f; echoWrite = 0
        carrierPhase = 0.0; envelope = 0f; gateGain = 0f; holdRemaining = 0
        highPass.reset(); lowPass.reset()
    }

    /** Processes [n] samples of [buf] in place. */
    fun process(buf: FloatArray, n: Int, p: VoiceFxParams) {
        val pitchOn = abs(p.pitchSemitones) > 0.05f
        val ratio = 2f.pow(p.pitchSemitones / 12f)
        val phaseStep = (1f - ratio) / psWindow
        val gateThreshold = 10f.pow(p.gateDb / 20f)
        val carrierStep = 2.0 * PI * p.robotHz / sampleRate
        val robot = p.robot.coerceIn(0f, 1f)
        val driveK = 1f + p.drive.coerceIn(0f, 1f) * 12f
        val driveNorm = tanh(driveK)
        val echoMix = p.echo.coerceIn(0f, 1f)
        val echoFb = echoMix * 0.6f
        val echoDelay = (p.echoMs / 1000f * sampleRate).toInt().coerceIn(1, echoSize - 1)

        for (i in 0 until n) {
            var x = buf[i] * p.micGain

            // Noise gate with hold so word tails are not chopped.
            val level = abs(x)
            envelope = if (level > envelope) envelope + (level - envelope) * 0.2f else envelope * 0.9995f
            if (envelope > gateThreshold) holdRemaining = holdSamples else if (holdRemaining > 0) holdRemaining--
            val target = if (holdRemaining > 0) 1f else 0f
            gateGain += (target - gateGain) * (if (target > gateGain) 0.01f else 0.0015f)
            x *= gateGain

            // Pitch shift (always feed the delay line so toggling never replays stale audio).
            psBuf[psWrite] = x
            if (pitchOn) {
                psPhase += phaseStep
                if (psPhase >= 1f) psPhase -= 1f else if (psPhase < 0f) psPhase += 1f
                var p2 = psPhase + 0.5f
                if (p2 >= 1f) p2 -= 1f
                x = readDelay(psPhase * psWindow + 2f) * sin(PI.toFloat() * psPhase) +
                    readDelay(p2 * psWindow + 2f) * sin(PI.toFloat() * p2)
            }
            psWrite = (psWrite + 1) and psMask

            // Ring modulator (RMS-compensated).
            if (robot > 0f) {
                carrierPhase += carrierStep
                if (carrierPhase > 2.0 * PI) carrierPhase -= 2.0 * PI
                x *= (1f - robot) + robot * 1.41f * sin(carrierPhase).toFloat()
            }

            if (p.radio) x = lowPass.process(highPass.process(x)) * 1.8f

            if (driveK > 1f) x = tanh(driveK * x) / driveNorm

            // Feedback echo.
            var readIdx = echoWrite - echoDelay
            if (readIdx < 0) readIdx += echoSize
            val delayed = echoBuf[readIdx]
            echoBuf[echoWrite] = x + delayed * echoFb
            echoWrite = if (echoWrite + 1 == echoSize) 0 else echoWrite + 1
            if (echoMix > 0f) x += delayed * echoMix

            buf[i] = softClip(x * p.outputGain)
        }
    }

    private fun readDelay(delay: Float): Float {
        val pos = psWrite - delay
        val base = kotlin.math.floor(pos).toInt()
        val frac = pos - base
        val a = psBuf[base and psMask]
        val b = psBuf[(base + 1) and psMask]
        return a + (b - a) * frac
    }

    private fun softClip(x: Float): Float {
        val ax = abs(x)
        if (ax <= 0.8f) return x
        val shaped = 0.8f + 0.2f * tanh((ax - 0.8f) / 0.2f)
        return if (x < 0f) -shaped else shaped
    }
}

/** RBJ-cookbook biquad in transposed direct form II. */
internal class Biquad private constructor(
    private val b0: Float, private val b1: Float, private val b2: Float,
    private val a1: Float, private val a2: Float,
) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = 0f; z2 = 0f }

    companion object {
        private const val Q = 0.7071

        fun lowPass(sampleRate: Int, freq: Double): Biquad {
            val w0 = 2 * PI * freq / sampleRate
            val c = cos(w0); val alpha = sin(w0) / (2 * Q); val a0 = 1 + alpha
            return Biquad(
                ((1 - c) / 2 / a0).toFloat(), ((1 - c) / a0).toFloat(), ((1 - c) / 2 / a0).toFloat(),
                (-2 * c / a0).toFloat(), ((1 - alpha) / a0).toFloat(),
            )
        }

        fun highPass(sampleRate: Int, freq: Double): Biquad {
            val w0 = 2 * PI * freq / sampleRate
            val c = cos(w0); val alpha = sin(w0) / (2 * Q); val a0 = 1 + alpha
            return Biquad(
                ((1 + c) / 2 / a0).toFloat(), (-(1 + c) / a0).toFloat(), ((1 + c) / 2 / a0).toFloat(),
                (-2 * c / a0).toFloat(), ((1 - alpha) / a0).toFloat(),
            )
        }
    }
}
