package com.jms.pssoundboard.data

import android.content.Context
import android.util.Log
import com.jms.pssoundboard.data.model.SpewEntry
import com.jms.pssoundboard.data.model.VaultSortOrder
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent Master Directory & "Spew Vault".
 * Remembers every sound effect, meme, or movie quote ever triggered or fired into party chat.
 */
class SpewVaultRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "SpewVaultRepository"
        private const val VAULT_FILE = "spew_vault.json"
        private const val MAX_VAULT_ENTRIES = 500
    }

    private val vaultFile = File(context.filesDir, VAULT_FILE)
    private val _entries = MutableStateFlow<List<SpewEntry>>(emptyList())
    val entries: StateFlow<List<SpewEntry>> = _entries.asStateFlow()

    private var saveJob: Job? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val loaded = runCatching {
            if (vaultFile.exists()) {
                val jsonText = vaultFile.readText()
                val array = JSONArray(jsonText)
                val list = ArrayList<SpewEntry>(array.length())
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(
                        SpewEntry(
                            id = obj.optString("id", ""),
                            title = obj.optString("title", ""),
                            sourceUrl = obj.optString("sourceUrl", "").takeIf { it.isNotBlank() },
                            localFilePath = obj.optString("localFilePath", "").takeIf { it.isNotBlank() },
                            category = obj.optString("category", "Meme"),
                            spewCount = obj.optInt("spewCount", 1),
                            lastSpewedEpochMs = obj.optLong("lastSpewedEpochMs", System.currentTimeMillis()),
                            isPinned = obj.optBoolean("isPinned", false)
                        )
                    )
                }
                list.filter { it.title.isNotBlank() }
            } else null
        }.getOrNull()

        _entries.value = loaded ?: emptyList()
    }

    /**
     * Records a clip when triggered/spewed.
     * Increments the play count and updates the last-spewed timestamp.
     */
    fun recordSpew(
        title: String,
        sourceUrl: String? = null,
        localFilePath: String? = null,
        category: String = "Meme",
    ) {
        if (title.isBlank() || title == "Empty") return
        val id = (sourceUrl ?: localFilePath ?: title).hashCode().toString()

        _entries.update { current ->
            val existing = current.firstOrNull { it.id == id || (it.title == title && it.category == category) }
            val updated = if (existing != null) {
                val copy = existing.copy(
                    spewCount = existing.spewCount + 1,
                    lastSpewedEpochMs = System.currentTimeMillis(),
                    sourceUrl = sourceUrl ?: existing.sourceUrl,
                    localFilePath = localFilePath ?: existing.localFilePath
                )
                listOf(copy) + current.filterNot { it.id == existing.id }
            } else {
                val entry = SpewEntry(
                    id = id,
                    title = title,
                    sourceUrl = sourceUrl,
                    localFilePath = localFilePath,
                    category = category,
                    spewCount = 1,
                    lastSpewedEpochMs = System.currentTimeMillis()
                )
                listOf(entry) + current
            }
            updated.take(MAX_VAULT_ENTRIES)
        }
        schedulePersist()
    }

    fun togglePin(id: String) {
        _entries.update { current ->
            current.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it }
        }
        schedulePersist()
    }

    fun deleteEntries(ids: Set<String>) {
        _entries.update { current -> current.filterNot { it.id in ids } }
        schedulePersist()
    }

    /** Puts back a snapshot taken before a delete or clear (Undo). */
    fun restore(snapshot: List<SpewEntry>) {
        _entries.value = snapshot
        schedulePersist()
    }

    fun clearAll() {
        _entries.value = emptyList()
        schedulePersist()
    }

    private fun schedulePersist() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(300)
            persistNow()
        }
    }

    private fun persistNow() {
        runCatching {
            val array = JSONArray()
            _entries.value.forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("sourceUrl", entry.sourceUrl ?: "")
                    put("localFilePath", entry.localFilePath ?: "")
                    put("category", entry.category)
                    put("spewCount", entry.spewCount)
                    put("lastSpewedEpochMs", entry.lastSpewedEpochMs)
                    put("isPinned", entry.isPinned)
                }
                array.put(obj)
            }
            val tmp = File(vaultFile.parentFile, "$VAULT_FILE.tmp")
            tmp.writeText(array.toString(2))
            if (!tmp.renameTo(vaultFile)) {
                tmp.copyTo(vaultFile, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.e(TAG, "Failed to persist spew vault", it) }
    }
}
