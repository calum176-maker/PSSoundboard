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
import com.jms.pssoundboard.data.BackupRepository
import com.jms.pssoundboard.data.FeedbackRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class UndoableMessage(val text: String, val undo: () -> Unit)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PSSoundboardApp
    private val engine: SoundboardEngine = app.engine
    private val repository: SoundRepository = app.repository
    private val vaultRepository: SpewVaultRepository = app.vaultRepository
    private val recorder = ClipRecorder(application)
    private val feedback = FeedbackRepository(application)
    private val backups = BackupRepository(application, repository, vaultRepository)

    val deck: StateFlow<DeckState> = repository.state
    val ready: StateFlow<Boolean> = repository.ready
    val loopbackConnected: StateFlow<Boolean> = engine.loopbackConnected
    val outputDeviceName: StateFlow<String?> = engine.outputDeviceName
    val headsetMicDetected: StateFlow<Boolean> = engine.headsetMicDetected
    val gain: StateFlow<Float> = engine.gain
    val testTonePlaying: StateFlow<Boolean> = engine.testTonePlaying
    val playingPads: StateFlow<Set<String>> = engine.playingPads

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

    private val _onlineSearchFailed = MutableStateFlow(false)
    val onlineSearchFailed: StateFlow<Boolean> = _onlineSearchFailed.asStateFlow()

    /** The last "load more" failed; scrolling stops retrying until the user taps to try again. */
    private val _loadMoreFailed = MutableStateFlow(false)
    val loadMoreFailed: StateFlow<Boolean> = _loadMoreFailed.asStateFlow()

    private var currentOnlinePage = 1
    private var activeSearchQuery = ""
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Messages for destructive actions; the snackbar offers Undo and calls [UndoableMessage.undo]. */
    private val _undoMessages = MutableSharedFlow<UndoableMessage>(extraBufferCapacity = 4)
    val undoMessages: SharedFlow<UndoableMessage> = _undoMessages.asSharedFlow()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()
    private var previewPlayer: MediaPlayer? = null
    private var lastClipPreviewPath: String? = null

    init {
        // Load default trending sounds on launch
        searchOnline("")
        // Send any feedback that could not go out last time
        viewModelScope.launch {
            val sent = feedback.retryPending()
            if (sent > 0) _messages.tryEmit("Sent $sent saved feedback ${if (sent == 1) "report" else "reports"}")
        }
    }

    // ---- Backups (export / import everything) -----------------------------------------------

    fun exportDecks(uri: Uri) {
        viewModelScope.launch {
            _busyMessage.value = "Saving your decks…"
            runCatching { backups.export(uri) }
                .onSuccess { s ->
                    _messages.tryEmit("Saved ${count(s.decks, "deck")} and ${count(s.sounds, "sound")}")
                }
                .onFailure { _messages.tryEmit("Couldn't save the backup: ${it.message ?: it.javaClass.simpleName}") }
            _busyMessage.value = null
        }
    }

    /** Replaces decks and vault with a backup; the snackbar offers Undo, which puts the old ones back. */
    fun importDecks(uri: Uri) {
        viewModelScope.launch {
            _busyMessage.value = "Bringing your decks back…"
            runCatching { backups.read(uri) }
                .onSuccess { restored ->
                    val oldDeck = deck.value
                    val oldVault = spewEntries.value
                    repository.restore(restored.deck)
                    vaultRepository.restore(restored.vault)
                    val missing = if (restored.missing > 0) " (${count(restored.missing, "sound")} missing)" else ""
                    _undoMessages.tryEmit(
                        UndoableMessage("Imported ${count(restored.deck.boards.size, "deck")} and ${count(restored.sounds, "sound")}$missing") {
                            repository.restore(oldDeck)
                            vaultRepository.restore(oldVault)
                            viewModelScope.launch(Dispatchers.IO) { restored.dir.deleteRecursively() }
                        }
                    )
                }
                .onFailure { _messages.tryEmit("Couldn't import: ${it.message ?: it.javaClass.simpleName}") }
            _busyMessage.value = null
        }
    }

    private fun count(n: Int, noun: String) = "$n ${if (n == 1) noun else noun + "s"}"

    // ---- Tester feedback --------------------------------------------------------------------

    val feedbackConfigured: Boolean get() = feedback.isConfigured

    /**
     * Sends feedback to the team (it becomes a card on the team's board). Builds without an endpoint hand the
     * report to [onShare] instead, so testers can still pass it on by message or email.
     */
    fun sendFeedback(
        category: String,
        screen: String,
        message: String,
        includeDetails: Boolean,
        onShare: (String) -> Unit,
    ) {
        val details = if (includeDetails) {
            feedback.deviceDetails(
                mapOf("loopback_cable" to if (loopbackConnected.value) "plugged in" else "not plugged in")
            )
        } else {
            null
        }
        val report = feedback.buildReport(category, screen, message, details)
        if (!feedback.isConfigured) {
            onShare(feedback.reportAsText(report))
            return
        }
        viewModelScope.launch {
            val outcome = feedback.send(report)
            _messages.tryEmit(
                when {
                    outcome.sent && outcome.issueNumber != null -> "Thanks! Sent to the team as issue #${outcome.issueNumber}"
                    outcome.sent -> "Thanks! Feedback sent to the team"
                    else -> "Couldn't send right now. It's saved and will go next time you open the app."
                }
            )
        }
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

    fun deleteBoard(boardId: String) {
        val name = deck.value.board(boardId)?.name ?: "Deck"
        withDeckUndo("Deleted \"$name\"") { repository.deleteBoard(boardId) }
    }

    fun clearBoard(boardId: String) {
        val name = deck.value.board(boardId)?.name ?: "Deck"
        withDeckUndo("Cleared all pads on \"$name\"") { repository.clearBoard(boardId) }
    }

    fun resetBoardToDefaults(boardId: String) {
        val name = deck.value.board(boardId)?.name ?: "Deck"
        withDeckUndo("Reset \"$name\" to its default sounds") { repository.resetBoardToDefaults(boardId) }
    }

    // Undo restores the whole snapshot, so it also drops any other deck edit made while the
    // snackbar was showing. That window is a few seconds, which is an acceptable trade.
    private fun withDeckUndo(message: String, change: () -> Unit) {
        val snapshot = deck.value
        change()
        _undoMessages.tryEmit(UndoableMessage(message) { repository.restore(snapshot) })
    }

    private fun withVaultUndo(message: String, change: () -> Unit) {
        val snapshot = spewEntries.value
        change()
        _undoMessages.tryEmit(UndoableMessage(message) { vaultRepository.restore(snapshot) })
    }

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
                _messages.tryEmit("⚡ Spewed: \"${item.title}\" into chat!")
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

    fun deleteSpewEntries(ids: Set<String>) {
        if (ids.isEmpty()) return
        val label = if (ids.size == 1) {
            "Removed \"${spewEntries.value.firstOrNull { it.id in ids }?.title ?: "clip"}\""
        } else {
            "Removed ${ids.size} clips"
        }
        withVaultUndo(label) { vaultRepository.deleteEntries(ids) }
    }

    fun clearSpewVault() {
        val count = spewEntries.value.size
        if (count == 0) return
        withVaultUndo("Cleared $count ${if (count == 1) "clip" else "clips"} from the vault") {
            vaultRepository.clearAll()
        }
    }

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
                _messages.tryEmit("💾 Saved \"${item.title}.wav\" to Downloads/Spewboard")
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
            _messages.tryEmit("💾 Saved $successCount / ${items.size} clips to Downloads/Spewboard")
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
        // A page still loading for the previous query would otherwise land on these results.
        loadMoreJob?.cancel()
        _isLoadingMoreOnline.value = false
        activeSearchQuery = query
        currentOnlinePage = 1
        _hasMoreOnlineResults.value = false
        _totalOnlineCount.value = 0
        _onlineSearchFailed.value = false
        _loadMoreFailed.value = false
        searchJob = viewModelScope.launch {
            _isSearchingOnline.value = true
            val pageResult = OnlineSoundService.searchSounds(query, page = 1)
            _onlineSearchFailed.value = pageResult.failed
            _onlineSearchResults.value = pageResult.items
            _hasMoreOnlineResults.value = pageResult.hasMore
            _totalOnlineCount.value = pageResult.totalCount
            _isSearchingOnline.value = false
        }
    }

    fun retryOnlineSearch() = searchOnline(activeSearchQuery)

    /**
     * Loads the next 10 sounds as the user scrolls down, appending them to the results.
     */
    fun loadMoreOnline() {
        if (_isSearchingOnline.value || _isLoadingMoreOnline.value || !_hasMoreOnlineResults.value) return
        val nextPage = currentOnlinePage + 1
        loadMoreJob = viewModelScope.launch {
            _isLoadingMoreOnline.value = true
            _loadMoreFailed.value = false
            val pageResult = OnlineSoundService.searchSounds(activeSearchQuery, page = nextPage)
            if (pageResult.items.isNotEmpty()) {
                val existingIds = _onlineSearchResults.value.map { it.id }.toSet()
                val existingUrls = _onlineSearchResults.value.mapNotNull { it.url }.toSet()
                val newUnique = pageResult.items.filterNot { it.id in existingIds || (it.url != null && it.url in existingUrls) }
                _onlineSearchResults.value = _onlineSearchResults.value + newUnique
                currentOnlinePage = nextPage
                _hasMoreOnlineResults.value = pageResult.hasMore
                _totalOnlineCount.value = pageResult.totalCount
            } else if (pageResult.failed) {
                // Keep hasMore; the footer offers a retry button instead of retrying on every scroll.
                _loadMoreFailed.value = true
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

    /** Records from the mic. On failure, posts a message and returns an empty array. */
    suspend fun recordClip(stop: AtomicBoolean, onProgress: (Long, Float) -> Unit): FloatArray =
        recordSafely { recorder.record(stop, onProgress) }

    private suspend fun recordSafely(block: suspend () -> FloatArray): FloatArray =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _messages.tryEmit("Recording failed: ${e.message ?: e.javaClass.simpleName}")
            FloatArray(0)
        }

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
    ): FloatArray = recordSafely {
        val raw = recorder.record(stop, onProgress)
        withContext(Dispatchers.Default) {
            VoiceFxProcessor(ClipRecorder.SAMPLE_RATE).process(raw, raw.size, params)
        }
        raw
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
