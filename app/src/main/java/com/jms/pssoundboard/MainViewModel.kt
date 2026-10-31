package com.jms.pssoundboard

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jms.pssoundboard.audio.AudioDecoder
import com.jms.pssoundboard.audio.ClipRecorder
import com.jms.pssoundboard.audio.SoundboardEngine
import com.jms.pssoundboard.audio.VoiceFxParams
import com.jms.pssoundboard.audio.VoiceFxProcessor
import com.jms.pssoundboard.audio.VoicePreset
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.OnlineSoundService
import com.jms.pssoundboard.data.SoundRepository
import com.jms.pssoundboard.data.SpewVaultRepository
import com.jms.pssoundboard.data.SynthTestClipsGenerator
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.data.model.PlaybackMode
import com.jms.pssoundboard.data.model.SpewEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PSSoundboardApp
    private val engine: SoundboardEngine = app.engine
    private val repository: SoundRepository = app.repository
    private val vaultRepository: SpewVaultRepository = app.vaultRepository
    private val recorder = ClipRecorder(application)

    val deck: StateFlow<DeckState> = repository.state
    val ready: StateFlow<Boolean> = repository.ready
    val loopbackConnected: StateFlow<Boolean> = engine.loopbackConnected
    val outputDeviceName: StateFlow<String?> = engine.outputDeviceName
    val headsetMicDetected: StateFlow<Boolean> = engine.headsetMicDetected
    val gain: StateFlow<Float> = engine.gain
    val testTonePlaying: StateFlow<Boolean> = engine.testTonePlaying

    // Spew Vault: directory of every clip remembered when spewed
    val spewEntries: StateFlow<List<SpewEntry>> = vaultRepository.entries

    // Online search state for internet memes & movie quotes (10 at a time with infinite scroll)
    private val _onlineSearchResults = MutableStateFlow<List<LibraryItem>>(emptyList())
    val onlineSearchResults: StateFlow<List<LibraryItem>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _isLoadingMoreOnline = MutableStateFlow(false)
    val isLoadingMoreOnline: StateFlow<Boolean> = _isLoadingMoreOnline.asStateFlow()

    private val _hasMoreOnlineResults = MutableStateFlow(false)
    val hasMoreOnlineResults: StateFlow<Boolean> = _hasMoreOnlineResults.asStateFlow()

    private val _totalOnlineCount = MutableStateFlow(0)
    val totalOnlineCount: StateFlow<Int> = _totalOnlineCount.asStateFlow()

    private var currentOnlinePage = 1
    private var activeSearchQuery = ""
    private var searchJob: Job? = null

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()
    private var previewPlayer: MediaPlayer? = null
    private var lastClipPreviewPath: String? = null

    init {
        // Load default trending sounds on launch
        searchOnline("")
    }

    // ---- Live deck & Spew logging -----------------------------------------------------------

    fun selectBoard(boardId: String) = repository.setActiveBoard(boardId)

    fun pressPad(ref: PadRef, pad: Pad) {
        engine.pressPad(ref.key, pad)
        pad.filePath?.let { path ->
            val board = deck.value.board(ref.boardId)
            vaultRepository.recordSpew(
                title = pad.title,
                localFilePath = path,
                category = board?.name ?: "Deck Pad"
            )
        }
    }

    fun releasePad(ref: PadRef, pad: Pad) = engine.releasePad(ref.key, pad)

    fun stopPad(ref: PadRef) = engine.stopPad(ref.key)

    fun stopAll() {
        engine.stopAll()
        stopPreview()
    }

    fun setGain(value: Float) {
        engine.setGain(value)
        repository.setGain(engine.gain.value)
    }

    fun savePad(boardId: String, pad: Pad, favorite: Boolean) {
        repository.updatePad(boardId, pad)
        repository.setFavorite(PadRef(boardId, pad.slot), favorite)
        pad.filePath?.let { engine.load(it) }
    }

    fun renameBoard(boardId: String, name: String) {
        if (name.isNotBlank()) repository.renameBoard(boardId, name.trim())
    }

    fun addBoard(name: String) = repository.addBoard(name)

    fun deleteBoard(boardId: String) = repository.deleteBoard(boardId)

    fun clearBoard(boardId: String) = repository.clearBoard(boardId)

    fun resetBoardToDefaults(boardId: String) = repository.resetBoardToDefaults(boardId)

    fun builtinPath(clipId: String) = repository.builtinPath(clipId)

    /** Plays any file once through the engine (hard-panned left like the pads). */
    fun auditionFile(path: String) {
        engine.trigger(SoundboardEngine.PREVIEW_KEY, path, PlaybackMode.RESTART_ON_SPAM)
    }

    // ---- Direct Spew & Spew Vault Operations ------------------------------------------------

    /**
     * Spews an internet / movie clip or sound directly into party chat loopback immediately
     * and automatically logs it into the Master Spew Vault directory.
     */
    fun spewDirectly(item: LibraryItem) {
        viewModelScope.launch {
            _busyMessage.value = "Spewing \"${item.title}\"…"
            runCatching {
                val path = when {
                    item.builtinId != null -> repository.builtinPath(item.builtinId)
                    item.localUri != null -> repository.importFromUri(Uri.parse(item.localUri)).absolutePath
                    item.url != null -> repository.downloadFromUrl(item.url).absolutePath
                    else -> error("Nothing to spew")
                }
                engine.load(path)
                engine.trigger(SoundboardEngine.PREVIEW_KEY, path, PlaybackMode.RESTART_ON_SPAM)
                vaultRepository.recordSpew(
                    title = item.title,
                    sourceUrl = item.url,
                    localFilePath = path,
                    category = item.category
                )
                _messages.tryEmit("⚡ Spewed: \"${item.title}\" into Party!")
            }.onFailure {
                _messages.tryEmit("Spew failed: ${it.message ?: it.javaClass.simpleName}")
            }
            _busyMessage.value = null
        }
    }

    /** Re-fires a previously remembered clip from the Spew Vault directory. */
    fun spewFromVault(entry: SpewEntry) {
        viewModelScope.launch {
            runCatching {
                val path = entry.localFilePath ?: entry.sourceUrl?.let { repository.downloadFromUrl(it).absolutePath }
                if (path != null) {
                    engine.load(path)
                    engine.trigger(SoundboardEngine.PREVIEW_KEY, path, PlaybackMode.RESTART_ON_SPAM)
                    vaultRepository.recordSpew(
                        title = entry.title,
                        sourceUrl = entry.sourceUrl,
                        localFilePath = path,
                        category = entry.category
                    )
                    _messages.tryEmit("⚡ Spewed: \"${entry.title}\" (${entry.spewCount + 1}x)")
                }
            }.onFailure {
                _messages.tryEmit("Failed to spew: ${it.message}")
            }
        }
    }

    fun togglePinSpew(id: String) = vaultRepository.togglePin(id)

    fun deleteSpewEntry(id: String) = vaultRepository.deleteEntry(id)

    fun clearSpewVault() = vaultRepository.clearAll()

    // ---- Clipboard & Save to Storage --------------------------------------------------------

    fun copyToClipboard(label: String, text: String) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        _messages.tryEmit("📋 Copied to clipboard: $label")
    }

    fun saveToDownloads(item: LibraryItem) {
        viewModelScope.launch {
            _busyMessage.value = "Saving \"${item.title}\" to Downloads…"
            runCatching {
                repository.saveSoundToDownloads(
                    title = item.title,
                    url = item.url,
                    localFilePath = item.localUri,
                    builtinId = item.builtinId
                )
                _messages.tryEmit("💾 Saved \"${item.title}.wav\" to Downloads/PSSoundboard")
            }.onFailure {
                _messages.tryEmit("Failed to save: ${it.message ?: it.javaClass.simpleName}")
            }
            _busyMessage.value = null
        }
    }

    fun saveMultipleToDownloads(items: List<LibraryItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _busyMessage.value = "Saving ${items.size} clips to Downloads…"
            var successCount = 0
            items.forEach { item ->
                runCatching {
                    repository.saveSoundToDownloads(
                        title = item.title,
                        url = item.url,
                        localFilePath = item.localUri,
                        builtinId = item.builtinId
                    )
                    successCount++
                }
            }
            _busyMessage.value = null
            _messages.tryEmit("💾 Saved $successCount / ${items.size} clips to Downloads/PSSoundboard")
        }
    }

    fun saveSpewEntryToDownloads(entry: SpewEntry) {
        val item = LibraryItem(
            id = entry.id,
            title = entry.title,
            category = entry.category,
            url = entry.sourceUrl,
            localUri = entry.localFilePath
        )
        saveToDownloads(item)
    }

    fun saveMultipleSpewEntriesToDownloads(entries: List<SpewEntry>) {
        val items = entries.map { entry ->
            LibraryItem(
                id = entry.id,
                title = entry.title,
                category = entry.category,
                url = entry.sourceUrl,
                localUri = entry.localFilePath
            )
        }
        saveMultipleToDownloads(items)
    }

    // ---- Online Search (Internet & Movie Clips - 10 At A Time with Infinite Scroll) --------

    fun searchOnline(query: String) {
        searchJob?.cancel()
        activeSearchQuery = query
        currentOnlinePage = 1
        _hasMoreOnlineResults.value = false
        _totalOnlineCount.value = 0
        searchJob = viewModelScope.launch {
            _isSearchingOnline.value = true
            val pageResult = OnlineSoundService.searchSounds(query, page = 1)
            _onlineSearchResults.value = pageResult.items
            _hasMoreOnlineResults.value = pageResult.hasMore
            _totalOnlineCount.value = pageResult.totalCount
            _isSearchingOnline.value = false
        }
    }

    /**
     * Loads the next 10 sounds as the user scrolls down, appending them to the results.
     */
    fun loadMoreOnline() {
        if (_isSearchingOnline.value || _isLoadingMoreOnline.value || !_hasMoreOnlineResults.value) return
        val nextPage = currentOnlinePage + 1
        viewModelScope.launch {
            _isLoadingMoreOnline.value = true
            val pageResult = OnlineSoundService.searchSounds(activeSearchQuery, page = nextPage)
            if (pageResult.items.isNotEmpty()) {
                val existingIds = _onlineSearchResults.value.map { it.id }.toSet()
                val existingUrls = _onlineSearchResults.value.mapNotNull { it.url }.toSet()
                val newUnique = pageResult.items.filterNot { it.id in existingIds || (it.url != null && it.url in existingUrls) }
                _onlineSearchResults.value = _onlineSearchResults.value + newUnique
                currentOnlinePage = nextPage
                _hasMoreOnlineResults.value = pageResult.hasMore
                _totalOnlineCount.value = pageResult.totalCount
            } else {
                _hasMoreOnlineResults.value = false
            }
            _isLoadingMoreOnline.value = false
        }
    }

    // ---- Library / Importer -----------------------------------------------------------------

    fun displayName(uri: Uri): String = repository.displayName(uri)

    suspend fun importToCache(uri: Uri): File = repository.importFromUri(uri)

    fun equip(item: LibraryItem, target: PadRef, title: String) {
        viewModelScope.launch {
            _busyMessage.value = "Equipping \"$title\"…"
            runCatching {
                val path = when {
                    item.builtinId != null -> repository.builtinPath(item.builtinId)
                    item.localUri != null -> repository.importFromUri(Uri.parse(item.localUri)).absolutePath
                    item.url != null -> repository.downloadFromUrl(item.url).absolutePath
                    else -> error("Nothing to equip")
                }
                engine.load(path) // into SoundPool RAM immediately
                repository.assignSound(target, path, title)
                vaultRepository.recordSpew(
                    title = title,
                    sourceUrl = item.url,
                    localFilePath = path,
                    category = item.category
                )
                val boardName = deck.value.board(target.boardId)?.name ?: target.boardId
                _messages.tryEmit("Equipped \"$title\" → $boardName · Pad ${target.slot + 1}")
            }.onFailure {
                _messages.tryEmit("Equip failed: ${it.message ?: it.javaClass.simpleName}")
            }
            _busyMessage.value = null
        }
    }

    fun togglePreview(item: LibraryItem) {
        if (_previewingId.value == item.id) {
            stopPreview(); return
        }
        stopPreview()
        val source = when {
            item.builtinId != null -> repository.builtinPath(item.builtinId)
            item.url != null -> item.url
            item.localUri != null -> item.localUri
            else -> return
        }
        val player = MediaPlayer()
        previewPlayer = player
        _previewingId.value = item.id
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            if (item.localUri != null) player.setDataSource(getApplication(), Uri.parse(source))
            else player.setDataSource(source)
            val g = engine.gain.value
            player.setVolume(g, g)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { if (previewPlayer === it) stopPreview() }
            player.setOnErrorListener { mp, _, _ ->
                if (previewPlayer === mp) {
                    stopPreview(); _messages.tryEmit("Preview failed (check your connection)")
                }
                true
            }
            player.prepareAsync()
        }.onFailure {
            stopPreview(); _messages.tryEmit("Preview failed: ${it.message}")
        }
    }

    fun stopPreview() {
        previewPlayer?.let { p -> runCatching { p.stop() }; p.release() }
        previewPlayer = null
        _previewingId.value = null
    }

    // ---- Clip creator -----------------------------------------------------------------------

    suspend fun recordClip(stop: AtomicBoolean, onProgress: (Long, Float) -> Unit): FloatArray =
        recorder.record(stop, onProgress)

    suspend fun decodeForEditing(uri: Uri): AudioDecoder.Decoded = repository.decodeForEditing(uri)

    fun previewClip(samples: FloatArray, sampleRate: Int, startSec: Float, endSec: Float, normalize: Boolean) {
        viewModelScope.launch {
            runCatching {
                val file = repository.saveProcessedClip(
                    samples, sampleRate, startSec, endSec, normalize, "preview",
                    directory = getApplication<Application>().cacheDir,
                )
                lastClipPreviewPath?.let { old -> engine.unload(old); File(old).delete() }
                lastClipPreviewPath = file.absolutePath
                auditionFile(file.absolutePath)
            }.onFailure { _messages.tryEmit("Preview failed: ${it.message}") }
        }
    }

    fun saveClip(
        samples: FloatArray,
        sampleRate: Int,
        startSec: Float,
        endSec: Float,
        normalize: Boolean,
        title: String,
        target: PadRef,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            _busyMessage.value = "Saving clip…"
            runCatching {
                val file = repository.saveProcessedClip(
                    samples, sampleRate, startSec, endSec, normalize, "rec_${title.ifBlank { "clip" }}"
                )
                engine.load(file.absolutePath)
                repository.assignSound(target, file.absolutePath, title.ifBlank { "My Clip" })
                vaultRepository.recordSpew(
                    title = title.ifBlank { "My Clip" },
                    localFilePath = file.absolutePath,
                    category = "Recorded Mic"
                )
                _messages.tryEmit("Clip \"${title.ifBlank { "My Clip" }}\" assigned to Pad ${target.slot + 1}")
                onDone()
            }.onFailure { _messages.tryEmit("Save failed: ${it.message}") }
            _busyMessage.value = null
        }
    }

    // ---- Voice Changer FX -------------------------------------------------------------------

    private val _voiceFxParams = MutableStateFlow(VoiceFxParams())
    val voiceFxParams: StateFlow<VoiceFxParams> = _voiceFxParams.asStateFlow()

    private val _activePresetId = MutableStateFlow(VoicePreset.NATURAL.id)
    val activePresetId: StateFlow<String> = _activePresetId.asStateFlow()

    fun updateVoiceFxParams(params: VoiceFxParams, presetId: String) {
        _voiceFxParams.value = params
        _activePresetId.value = presetId
    }

    suspend fun recordAndProcessVoice(
        params: VoiceFxParams,
        stop: AtomicBoolean,
        onProgress: (Long, Float) -> Unit,
    ): FloatArray {
        val raw = recorder.record(stop, onProgress)
        val processor = VoiceFxProcessor(ClipRecorder.SAMPLE_RATE)
        processor.process(raw, raw.size, params)
        return raw
    }

    fun saveProcessedVoiceClip(samples: FloatArray, title: String) {
        viewModelScope.launch {
            _busyMessage.value = "Saving voice clip…"
            runCatching {
                val file = repository.saveProcessedClip(
                    samples, ClipRecorder.SAMPLE_RATE, 0f, samples.size.toFloat() / ClipRecorder.SAMPLE_RATE,
                    true, "voicefx_${title.ifBlank { "clip" }}"
                )
                vaultRepository.recordSpew(
                    title = title.ifBlank { "Voice FX Clip" },
                    localFilePath = file.absolutePath,
                    category = "Voice Changer"
                )
                _messages.tryEmit("Voice FX clip \"${title.ifBlank { "Voice FX Clip" }}\" saved to Vault")
            }.onFailure { _messages.tryEmit("Save failed: ${it.message}") }
            _busyMessage.value = null
        }
    }

    fun auditionSamples(samples: FloatArray) {
        viewModelScope.launch {
            runCatching {
                val file = repository.saveProcessedClip(
                    samples, ClipRecorder.SAMPLE_RATE, 0f, samples.size.toFloat() / ClipRecorder.SAMPLE_RATE,
                    true, "preview", directory = getApplication<Application>().cacheDir
                )
                lastClipPreviewPath?.let { old -> engine.unload(old); File(old).delete() }
                lastClipPreviewPath = file.absolutePath
                auditionFile(file.absolutePath)
            }.onFailure { _messages.tryEmit("Preview failed: ${it.message}") }
        }
    }

    fun spewProcessedSamples(samples: FloatArray, title: String) {
        viewModelScope.launch {
            runCatching {
                val file = repository.saveProcessedClip(
                    samples, ClipRecorder.SAMPLE_RATE, 0f, samples.size.toFloat() / ClipRecorder.SAMPLE_RATE,
                    true, "spew_${title.ifBlank { "voice" }}"
                )
                spewDirectly(
                    LibraryItem(
                        id = file.absolutePath,
                        title = title.ifBlank { "Voice FX Clip" },
                        category = "Voice Changer",
                        localUri = file.absolutePath
                    )
                )
            }.onFailure { _messages.tryEmit("Spew failed: ${it.message}") }
        }
    }

    // ---- Hardware guide ---------------------------------------------------------------------

    fun toggleTestTone() {
        engine.toggleTestTone(repository.testTonePath)
    }

    val builtinClips = SynthTestClipsGenerator.CLIPS

    override fun onCleared() {
        stopPreview()
        if (engine.testTonePlaying.value) engine.toggleTestTone(repository.testTonePath)
        super.onCleared()
    }
}
