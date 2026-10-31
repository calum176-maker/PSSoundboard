package com.jms.pssoundboard.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PlaybackMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Zero-latency soundboard engine built for the TRRS hardware loopback plug.
 *
 * Routing contract:
 *  - SoundPool uses USAGE_MEDIA / CONTENT_TYPE_MUSIC so Android routes it out of the USB-C DAC,
 *    while the PS App keeps USAGE_VOICE_COMMUNICATION on the TRRS mic (Sleeve) input.
 *  - Every stream is HARD-PANNED LEFT (left = gain, right = 0f): Tip -> 10k -> Sleeve gets the
 *    full signal; Ring 1 (right) is unconnected and stays silent.
 *  - Audio focus is requested as AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK and released when idle, so the
 *    PS App never loses its permanent call focus. Playback proceeds even if focus is denied.
 *
 * All public methods must be called from the main thread.
 */
class SoundboardEngine(context: Context) {

    companion object {
        private const val TAG = "SoundboardEngine"
        const val MAX_STREAMS = 6
        private const val FOCUS_RELEASE_DELAY_MS = 6_000L
        const val TEST_TONE_KEY = "__loopback_test_tone__"
        const val PREVIEW_KEY = "__preview__"
        const val MIN_GAIN = 0.10f
        const val MAX_GAIN = 1.00f

        /** Output device types that indicate the loopback plug / USB-C DAC is attached. */
        private val LOOPBACK_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE,
        )
        private val LOOPBACK_MIC_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )
    }

    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(attributes)
        .build()

    private data class PendingPlay(val padKey: String, val mode: PlaybackMode)

    private val sampleIds = HashMap<String, Int>()            // file path -> SoundPool sample id
    private val loadedSamples = HashSet<Int>()
    private val failedSamples = HashSet<Int>()
    private val pendingPlays = HashMap<Int, MutableList<PendingPlay>>()
    private val padStreams = HashMap<String, MutableList<Int>>()
    private val heldPads = HashSet<String>()
    private val sampleDurationMs = HashMap<Int, Long>()
    private val playingEnds = HashMap<String, Runnable>()

    // ---- Observable state -------------------------------------------------------------------

    private val _loopbackConnected = MutableStateFlow(false)
    val loopbackConnected: StateFlow<Boolean> = _loopbackConnected.asStateFlow()

    private val _outputDeviceName = MutableStateFlow<String?>(null)
    val outputDeviceName: StateFlow<String?> = _outputDeviceName.asStateFlow()

    private val _headsetMicDetected = MutableStateFlow(false)
    val headsetMicDetected: StateFlow<Boolean> = _headsetMicDetected.asStateFlow()

    private val _gain = MutableStateFlow(0.8f)
    val gain: StateFlow<Float> = _gain.asStateFlow()

    private val _testTonePlaying = MutableStateFlow(false)
    val testTonePlaying: StateFlow<Boolean> = _testTonePlaying.asStateFlow()

    private val _loadedCount = MutableStateFlow(0)
    val loadedCount: StateFlow<Int> = _loadedCount.asStateFlow()

    /**
     * Pad keys that are currently sounding. SoundPool has no completion callback, so one-shots
     * are cleared after the clip's length; looping (Hold) pads stay until released or stopped.
     */
    private val _playingPads = MutableStateFlow<Set<String>>(emptySet())
    val playingPads: StateFlow<Set<String>> = _playingPads.asStateFlow()

    // ---- Audio focus ------------------------------------------------------------------------

    private var hasFocus = false

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                // We never pause for focus changes: the soundboard must keep firing into the party.
                if (change == AudioManager.AUDIOFOCUS_LOSS) hasFocus = false
            }, mainHandler)
            .build()

    private val releaseFocusRunnable = Runnable { abandonFocus() }

    private fun requestTransientFocus() {
        mainHandler.removeCallbacks(releaseFocusRunnable)
        if (!hasFocus) {
            hasFocus = audioManager.requestAudioFocus(focusRequest) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun scheduleFocusRelease() {
        mainHandler.removeCallbacks(releaseFocusRunnable)
        if (heldPads.isEmpty() && !_testTonePlaying.value) {
            mainHandler.postDelayed(releaseFocusRunnable, FOCUS_RELEASE_DELAY_MS)
        }
    }

    private fun abandonFocus() {
        if (hasFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    // ---- Device monitoring ------------------------------------------------------------------

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshDevices()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshDevices()
    }

    private fun refreshDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val loopback = outputs.firstOrNull { it.type in LOOPBACK_OUTPUT_TYPES }
        _loopbackConnected.value = loopback != null
        _outputDeviceName.value = loopback?.productName?.toString()?.takeIf { it.isNotBlank() }
        _headsetMicDetected.value = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .any { it.type in LOOPBACK_MIC_TYPES }
    }

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status -> onSampleLoaded(sampleId, status) }
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        refreshDevices()
    }

    // ---- Sample cache (RAM preload) ---------------------------------------------------------

    /** Loads a file into SoundPool RAM. Idempotent; returns the sample id or null on failure. */
    fun load(path: String): Int? {
        sampleIds[path]?.let { return it }
        if (!File(path).exists()) {
            Log.w(TAG, "Missing audio file: $path")
            return null
        }
        val id = soundPool.load(path, 1)
        if (id == 0) return null
        sampleIds[path] = id
        readDurationMs(path)?.let { sampleDurationMs[id] = it }
        return id
    }

    private fun readDurationMs(path: String): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun markPlaying(padKey: String, sampleId: Int, looping: Boolean) {
        playingEnds.remove(padKey)?.let { mainHandler.removeCallbacks(it) }
        _playingPads.value = _playingPads.value + padKey
        if (!looping) {
            val end = Runnable {
                playingEnds.remove(padKey)
                _playingPads.value = _playingPads.value - padKey
            }
            playingEnds[padKey] = end
            mainHandler.postDelayed(end, sampleDurationMs[sampleId] ?: 1_000L)
        }
    }

    private fun markStopped(padKey: String) {
        playingEnds.remove(padKey)?.let { mainHandler.removeCallbacks(it) }
        if (padKey in _playingPads.value) _playingPads.value = _playingPads.value - padKey
    }

    fun unload(path: String) {
        val id = sampleIds.remove(path) ?: return
        soundPool.unload(id)
        sampleDurationMs.remove(id)
        loadedSamples.remove(id)
        failedSamples.remove(id)
        pendingPlays.remove(id)
        _loadedCount.value = loadedSamples.size
    }

    /** Keeps exactly [paths] (+ [extra]) resident in RAM. */
    fun syncResident(paths: Set<String>, extra: Set<String> = emptySet()) {
        val wanted = paths + extra
        (sampleIds.keys - wanted).toList().forEach { unload(it) }
        wanted.forEach { load(it) }
    }

    fun isReady(path: String): Boolean = sampleIds[path]?.let { it in loadedSamples } == true

    private fun onSampleLoaded(sampleId: Int, status: Int) {
        if (status != 0) {
            failedSamples += sampleId
            pendingPlays.remove(sampleId)
            Log.w(TAG, "SoundPool failed to load sample $sampleId (status=$status)")
            return
        }
        loadedSamples += sampleId
        _loadedCount.value = loadedSamples.size
        pendingPlays.remove(sampleId)?.forEach { pending ->
            // Hold pads released before the sample finished loading should stay silent.
            if (pending.mode != PlaybackMode.HOLD_TO_PLAY || pending.padKey in heldPads) {
                startStream(pending.padKey, sampleId, pending.mode)
            }
        }
    }

    // ---- Playback ---------------------------------------------------------------------------

    fun setGain(value: Float) {
        val g = value.coerceIn(MIN_GAIN, MAX_GAIN)
        _gain.value = g
        // Live-update every active stream, keeping the hard-left pan.
        padStreams.values.flatten().forEach { soundPool.setVolume(it, g, 0f) }
    }

    /** Finger down on a pad (or a tap). */
    fun pressPad(padKey: String, pad: Pad) {
        val path = pad.filePath ?: return
        trigger(padKey, path, pad.mode)
    }

    /** Finger up on a pad: only HOLD_TO_PLAY pads react. */
    fun releasePad(padKey: String, pad: Pad) {
        if (pad.mode == PlaybackMode.HOLD_TO_PLAY) {
            heldPads -= padKey
            stopPad(padKey)
            scheduleFocusRelease()
        }
    }

    fun trigger(padKey: String, path: String, mode: PlaybackMode) {
        val sampleId = load(path) ?: return
        if (mode == PlaybackMode.HOLD_TO_PLAY) heldPads += padKey
        if (sampleId in failedSamples) return
        if (sampleId !in loadedSamples) {
            pendingPlays.getOrPut(sampleId) { mutableListOf() } += PendingPlay(padKey, mode)
            return
        }
        startStream(padKey, sampleId, mode)
    }

    private fun startStream(padKey: String, sampleId: Int, mode: PlaybackMode) {
        if (mode == PlaybackMode.RESTART_ON_SPAM || mode == PlaybackMode.HOLD_TO_PLAY) {
            stopPad(padKey)
        }
        requestTransientFocus()
        val loop = if (mode == PlaybackMode.HOLD_TO_PLAY) -1 else 0
        val g = _gain.value
        // HARD-PAN LEFT: leftVolume = gain, rightVolume = 0f (Ring 1 is isolated).
        val streamId = soundPool.play(sampleId, g, 0f, 1, loop, 1f)
        if (streamId != 0) {
            val list = padStreams.getOrPut(padKey) { mutableListOf() }
            list += streamId
            // SoundPool evicts the oldest stream beyond MAX_STREAMS; keep bookkeeping bounded.
            while (list.size > MAX_STREAMS) list.removeAt(0)
            markPlaying(padKey, sampleId, looping = loop != 0)
        }
        scheduleFocusRelease()
    }

    fun stopPad(padKey: String) {
        padStreams.remove(padKey)?.forEach { soundPool.stop(it) }
        markStopped(padKey)
    }

    /** Panic button. */
    fun stopAll() {
        padStreams.values.flatten().forEach { soundPool.stop(it) }
        padStreams.clear()
        playingEnds.values.forEach { mainHandler.removeCallbacks(it) }
        playingEnds.clear()
        _playingPads.value = emptySet()
        pendingPlays.clear()
        heldPads.clear()
        _testTonePlaying.value = false
        mainHandler.removeCallbacks(releaseFocusRunnable)
        abandonFocus()
    }

    /** Toggles a looping 1 kHz loopback calibration tone. Returns the new playing state. */
    fun toggleTestTone(path: String): Boolean {
        return if (_testTonePlaying.value) {
            _testTonePlaying.value = false
            heldPads -= TEST_TONE_KEY
            stopPad(TEST_TONE_KEY)
            scheduleFocusRelease()
            false
        } else {
            _testTonePlaying.value = true
            trigger(TEST_TONE_KEY, path, PlaybackMode.HOLD_TO_PLAY)
            true
        }
    }

    fun release() {
        stopAll()
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        soundPool.release()
    }
}
