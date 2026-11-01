package com.jms.pssoundboard.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jms.pssoundboard.audio.AudioDecoder
import com.jms.pssoundboard.audio.WavUtils
import com.jms.pssoundboard.data.model.Board
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PADS_PER_BOARD
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single source of truth for boards / pads. Persists as JSON in `filesDir/deck_state.json`
 * and manages the on-disk audio cache in `filesDir/sound_cache/`.
 */
class SoundRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "SoundRepository"
        private const val STATE_FILE = "deck_state.json"
        private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024

        /** SoundPool keeps decoded PCM in RAM; keep clips short and reliable. */
        const val MAX_CLIP_SECONDS = 10
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val stateFile = File(context.filesDir, STATE_FILE)
    val cacheDir: File = File(context.filesDir, "sound_cache").apply { mkdirs() }

    private val _state = MutableStateFlow(DeckState())
    val state: StateFlow<DeckState> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var saveJob: Job? = null

    val testTonePath: String
        get() = SynthTestClipsGenerator.fileFor(context, SynthTestClipsGenerator.TEST_TONE_ID).absolutePath

    fun builtinPath(clipId: String): String =
        SynthTestClipsGenerator.fileFor(context, clipId).absolutePath

    /** Folder holding the synthesised built-in clips (regenerated on every install, so not backed up). */
    val builtinDirPath: String
        get() = SynthTestClipsGenerator.fileFor(context, SynthTestClipsGenerator.TEST_TONE_ID).parentFile!!.absolutePath

    /** Current decks as JSON, for backups. */
    fun exportStateJson(): String = gson.toJson(_state.value)

    /** Parses a backed-up deck state; null if it is not usable. */
    fun parseStateJson(text: String): DeckState? =
        runCatching { gson.fromJson(text, DeckState::class.java)?.sanitized() }.getOrNull()

    // ---- Lifecycle --------------------------------------------------------------------------

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val generated = SynthTestClipsGenerator.ensureGenerated(context)
        if (generated > 0) Log.i(TAG, "Synthesized $generated built-in clips")

        val loaded = runCatching {
            if (stateFile.exists()) gson.fromJson(stateFile.readText(), DeckState::class.java)?.sanitized() else null
        }.onFailure { Log.w(TAG, "Corrupt deck state, recreating defaults", it) }.getOrNull()

        _state.value = loaded ?: DefaultDecks.create(context)
        _ready.value = true
        if (loaded == null) persistNow()
    }

    /** Defends against partially-written / hand-edited JSON (Gson may inject nulls). */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun DeckState.sanitized(): DeckState? {
        if (boards == null || boards.isEmpty()) return null
        val fixedBoards = boards.filterNotNull().map { board ->
            val pads = (board.pads ?: emptyList()).filterNotNull().associateBy { it.slot }
            board.copy(
                name = board.name ?: "Board",
                pads = List(PADS_PER_BOARD) { slot ->
                    val p = pads[slot] ?: Pad(slot = slot)
                    p.copy(
                        slot = slot,
                        title = p.title ?: "Empty",
                        colorHex = p.colorHex ?: "#334155",
                        mode = p.mode ?: com.jms.pssoundboard.data.model.PlaybackMode.ONE_SHOT,
                    )
                },
            )
        }
        return copy(
            boards = fixedBoards,
            activeBoardId = if (fixedBoards.any { it.id == activeBoardId }) activeBoardId else fixedBoards.first().id,
            gain = gain.coerceIn(0.1f, 1f),
            favorites = (favorites ?: emptyList()).filterNotNull(),
        )
    }

    // ---- Mutations --------------------------------------------------------------------------

    private fun mutate(transform: (DeckState) -> DeckState) {
        _state.update(transform)
        schedulePersist()
    }

    /** Puts back a snapshot taken before a destructive change (Undo). */
    fun restore(snapshot: DeckState) = mutate { snapshot }

    fun setActiveBoard(boardId: String) = mutate { it.copy(activeBoardId = boardId) }

    fun setGain(gain: Float) = mutate { it.copy(gain = gain) }

    fun updatePad(boardId: String, pad: Pad) = mutate { s ->
        s.copy(boards = s.boards.map { b ->
            if (b.id != boardId) b else b.copy(pads = b.pads.map { if (it.slot == pad.slot) pad else it })
        })
    }

    fun renameBoard(boardId: String, name: String) = mutate { s ->
        s.copy(boards = s.boards.map { if (it.id == boardId) it.copy(name = name) else it })
    }

    fun addBoard(name: String) = mutate { s ->
        val id = "board_" + System.currentTimeMillis()
        val newBoard = Board(
            id = id,
            name = name.ifBlank { "Deck ${s.boards.size + 1}" },
            pads = List(PADS_PER_BOARD) { Pad(slot = it, title = "Empty", colorHex = "#334155") }
        )
        s.copy(boards = s.boards + newBoard, activeBoardId = id)
    }

    fun deleteBoard(boardId: String) = mutate { s ->
        if (s.boards.size <= 1) s
        else {
            val remaining = s.boards.filterNot { it.id == boardId }
            val newActive = if (s.activeBoardId == boardId) remaining.first().id else s.activeBoardId
            s.copy(boards = remaining, activeBoardId = newActive)
        }
    }

    fun clearBoard(boardId: String) = mutate { s ->
        s.copy(boards = s.boards.map { b ->
            if (b.id != boardId) b
            else b.copy(pads = List(PADS_PER_BOARD) { Pad(slot = it, title = "Empty", colorHex = "#334155") })
        })
    }

    fun resetBoardToDefaults(boardId: String) = mutate { s ->
        val defaultDeck = DefaultDecks.create(context).board(boardId)
        if (defaultDeck != null) {
            s.copy(boards = s.boards.map { b -> if (b.id == boardId) defaultDeck else b })
        } else {
            s.copy(boards = s.boards.map { b ->
                if (b.id != boardId) b
                else b.copy(pads = List(PADS_PER_BOARD) { Pad(slot = it, title = "Empty", colorHex = "#334155") })
            })
        }
    }

    fun setFavorite(ref: PadRef, favorite: Boolean) = mutate { s ->
        val without = s.favorites.filterNot { it == ref }
        s.copy(favorites = if (favorite) (without + ref).takeLast(6) else without)
    }

    /** Binds an audio file to a pad (keeps mode, optionally overrides title / color). */
    fun assignSound(ref: PadRef, path: String, title: String?, colorHex: String? = null) {
        val current = _state.value.pad(ref) ?: Pad(slot = ref.slot)
        val defaultColors = listOf("#008080", "#06B6D4", "#3B82F6", "#6366F1", "#A855F7", "#EC4899", "#F97316", "#EAB308", "#22C55E")
        val fallbackColor = if (current.colorHex == "#334155") defaultColors[ref.slot % defaultColors.size] else current.colorHex
        updatePad(
            ref.boardId,
            current.copy(
                filePath = path,
                title = title?.takeIf { it.isNotBlank() } ?: current.title,
                colorHex = colorHex ?: fallbackColor,
            ),
        )
    }

    // ---- Persistence ------------------------------------------------------------------------

    private fun schedulePersist() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(250)
            persistNow()
        }
    }

    private fun persistNow() {
        runCatching {
            val tmp = File(stateFile.parentFile, "$STATE_FILE.tmp")
            tmp.writeText(gson.toJson(_state.value))
            if (!tmp.renameTo(stateFile)) {
                tmp.copyTo(stateFile, overwrite = true); tmp.delete()
            }
        }.onFailure { Log.e(TAG, "Failed to persist deck state", it) }
    }

    // ---- Import / download / record ---------------------------------------------------------

    fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported clip"
    }

    /** Copies a local document into `sound_cache/` and converts it to a mono WAV. */
    suspend fun importFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val name = sanitize(displayName(uri))
        val raw = File(cacheDir, "import_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            raw.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open $uri")
        toMonoWav(raw)
    }

    /** Downloads (or reuses the cached copy of) a remote clip into `sound_cache/`. */
    suspend fun downloadFromUrl(rawUrl: String): File = withContext(Dispatchers.IO) {
        val url = OnlineSoundService.resolveAudioUrl(rawUrl)
        val hash = url.hashCode().toUInt().toString(16)
        val baseName = sanitize(url.substringAfterLast('/').substringBefore('?').ifBlank { "clip" })
        val converted = File(cacheDir, "dl_${hash}_${baseName.substringBeforeLast('.')}_mono.wav")
        if (converted.exists() && converted.length() > WavUtils.HEADER_SIZE) return@withContext converted

        val raw = File(cacheDir, "dl_${hash}_$baseName")
        var connection: HttpURLConnection? = null
        try {
            var target = URL(url)
            var redirects = 0
            while (true) {
                connection = (target.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Spewboard/1.0 (Android)")
                }
                val code = connection.responseCode
                if (code in 300..399 && redirects < 5) {
                    val location = connection.getHeaderField("Location") ?: break
                    target = URL(target, location)
                    connection.disconnect()
                    redirects++
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code while downloading clip")
                break
            }
            val conn = connection!!
            if (conn.contentLengthLong > MAX_DOWNLOAD_BYTES) throw IOException("Clip is too large (>15 MB)")
            conn.inputStream.use { input ->
                raw.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_DOWNLOAD_BYTES) throw IOException("Clip is too large (>15 MB)")
                        out.write(buf, 0, n)
                    }
                }
            }
        } catch (t: Throwable) {
            raw.delete()
            throw t
        } finally {
            connection?.disconnect()
        }
        toMonoWav(raw, converted)
    }

    /**
     * Decodes [raw] to a mono, length-capped WAV (full signal on the hard-left loopback channel).
     * Falls back to the original file if the platform cannot decode it.
     */
    private fun toMonoWav(raw: File, target: File = File(cacheDir, raw.nameWithoutExtension + "_mono.wav")): File {
        return try {
            val decoded = AudioDecoder.decode(raw, MAX_CLIP_SECONDS)
            require(decoded.samples.isNotEmpty()) { "Decoded clip is empty" }
            WavUtils.writeMonoWav(target, WavUtils.applyFades(decoded.samples, decoded.sampleRate, 3), decoded.sampleRate)
            raw.delete()
            target
        } catch (t: Throwable) {
            Log.w(TAG, "Mono conversion failed, using original file", t)
            raw
        }
    }

    suspend fun decodeForEditing(uri: Uri): AudioDecoder.Decoded = withContext(Dispatchers.Default) {
        AudioDecoder.decode(context, uri, maxSeconds = 30)
    }

    /** Trims + optionally normalizes PCM and writes it into the cache as a WAV. */
    suspend fun saveProcessedClip(
        samples: FloatArray,
        sampleRate: Int,
        startSec: Float,
        endSec: Float,
        normalize: Boolean,
        fileStem: String,
        directory: File = cacheDir,
    ): File = withContext(Dispatchers.Default) {
        val end = minOf(endSec, startSec + MAX_CLIP_SECONDS)
        var clip = WavUtils.trim(samples, sampleRate, startSec, end)
        if (normalize) clip = WavUtils.normalize(clip)
        WavUtils.applyFades(clip, sampleRate, 5)
        val file = File(directory, "${sanitize(fileStem)}_${System.currentTimeMillis()}.wav")
        WavUtils.writeMonoWav(file, clip, sampleRate)
        file
    }

    /** Saves an audio clip to the device's public Downloads/Spewboard folder. */
    suspend fun saveSoundToDownloads(
        title: String,
        url: String? = null,
        localFilePath: String? = null,
        builtinId: String? = null,
    ): File = withContext(Dispatchers.IO) {
        val sourceFile: File = when {
            !localFilePath.isNullOrBlank() && File(localFilePath).exists() -> File(localFilePath)
            !url.isNullOrBlank() -> downloadFromUrl(url)
            !builtinId.isNullOrBlank() -> File(builtinPath(builtinId))
            else -> throw IllegalArgumentException("No audio source available to save")
        }

        val cleanTitle = sanitize(title).ifBlank { "sound_${System.currentTimeMillis()}" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$cleanTitle.wav")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Spewboard")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Could not create MediaStore entry in Downloads")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IOException("Could not write audio to MediaStore")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Spewboard/$cleanTitle.wav")
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "Spewboard").apply { mkdirs() }
            val destFile = File(appDir, "$cleanTitle.wav")
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("audio/wav"), null)
            destFile
        }
    }

    /**
     * Prepares an audio clip for sharing (via WhatsApp, Signal, etc.) by ensuring the audio file
     * is available locally in the cache directory with a user-friendly filename matching its title.
     */
    suspend fun prepareSoundFileForSharing(
        title: String,
        url: String? = null,
        localFilePath: String? = null,
        builtinId: String? = null,
    ): File = withContext(Dispatchers.IO) {
        val sourceFile: File = when {
            !localFilePath.isNullOrBlank() && File(localFilePath).exists() -> File(localFilePath)
            !url.isNullOrBlank() -> downloadFromUrl(url)
            !builtinId.isNullOrBlank() -> File(builtinPath(builtinId))
            else -> throw IllegalArgumentException("No audio source available to share")
        }

        val cleanTitle = sanitize(title).ifBlank { "sound_${System.currentTimeMillis()}" }
        val shareDir = File(context.cacheDir, "shared_audio").apply { mkdirs() }
        val ext = if (sourceFile.extension.isNotBlank()) sourceFile.extension else "wav"
        val shareFile = File(shareDir, "$cleanTitle.$ext")
        sourceFile.copyTo(shareFile, overwrite = true)
        shareFile
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "clip" }
}

/** Factory for the out-of-the-box decks, all wired to the synthesized built-in clips. */
object DefaultDecks {

    private val layouts: List<Triple<String, String, List<Pair<String, String>>>> = listOf(
        Triple(
            "fps", "FPS Callouts", listOf(
                "radar_ping" to "Enemy Spotted", "red_alert" to "Push! Push!", "laser_sweep" to "Nade Out",
                "pit_radio" to "Reloading", "censor_beep" to "Bleep", "power_up" to "Shield Up",
                "bass_drop" to "Ult Ready", "wrong_buzzer" to "Team Wiped", "victory_fanfare" to "ACE!",
                "airhorn" to "Airhorn", "rimshot" to "Ba-Dum-Tss", "sad_trombone" to "Bot Frag",
            )
        ),
        Triple(
            "memes", "Memes & Reactions", listOf(
                "airhorn" to "MLG Airhorn", "sad_trombone" to "Sad Trombone", "rimshot" to "Ba-Dum-Tss",
                "censor_beep" to "Censor Beep", "wrong_buzzer" to "Wrong!", "victory_fanfare" to "Victory",
                "bass_drop" to "Bass Drop", "power_up" to "Level Up", "laser_sweep" to "Pew Pew",
                "red_alert" to "Sus Alert", "radar_ping" to "Ping", "pit_radio" to "Radio Chirp",
            )
        ),
        Triple(
            "racing", "Sim Racing / Pit Radio", listOf(
                "pit_radio" to "Box Box", "radar_ping" to "Gap Ping", "red_alert" to "Safety Car",
                "censor_beep" to "Track Limits", "wrong_buzzer" to "Penalty", "victory_fanfare" to "Chequered Flag",
                "power_up" to "DRS Open", "bass_drop" to "Launch", "airhorn" to "Start Horn",
                "laser_sweep" to "Fastest Lap", "sad_trombone" to "DNF", "rimshot" to "Pit Stop",
            )
        ),
        Triple(
            "scifi", "Sci-Fi FX", listOf(
                "laser_sweep" to "Blaster", "red_alert" to "Red Alert", "radar_ping" to "Sonar",
                "power_up" to "Warp Charge", "bass_drop" to "Hyperdrive", "pit_radio" to "Comms",
                "censor_beep" to "Override", "wrong_buzzer" to "Access Denied", "victory_fanfare" to "Mission Complete",
                "airhorn" to "Klaxon", "rimshot" to "Airlock", "sad_trombone" to "Hull Breach",
            )
        ),
    )

    fun create(context: Context): DeckState {
        val boards = layouts.map { (id, name, pads) ->
            Board(
                id = id,
                name = name,
                pads = pads.mapIndexed { slot, (clipId, title) ->
                    val clip = SynthTestClipsGenerator.clip(clipId)
                    Pad(
                        slot = slot,
                        title = title,
                        colorHex = clip?.colorHex ?: "#008080",
                        filePath = SynthTestClipsGenerator.fileFor(context, clipId).absolutePath,
                    )
                },
            )
        } + Board(
            id = "custom",
            name = "Custom Clips",
            pads = List(PADS_PER_BOARD) { Pad(slot = it, title = "Empty", colorHex = "#334155") },
        )
        return DeckState(boards = boards, activeBoardId = boards.first().id)
    }
}
