package com.jms.pssoundboard.data

import android.content.Context
import android.net.Uri
import com.jms.pssoundboard.BuildConfig
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.SpewEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One-file backups of everything a user has set up: decks and pads, the Spew Vault, and the
 * sound files they point at. Built-in clips are left out (every install regenerates them).
 *
 * Zip layout: manifest.json (format, app version, original paths of each sound),
 * deck_state.json, spew_vault.json, audio/<n>_<name>.
 */
class BackupRepository(
    private val context: Context,
    private val sounds: SoundRepository,
    private val vault: SpewVaultRepository,
) {
    companion object {
        private const val FORMAT = 1
        private const val MAX_ENTRY_BYTES = 25L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 400L * 1024 * 1024
        private val AUDIO_ENTRY = Regex("""^audio/\d{1,4}_[A-Za-z0-9._ -]{1,100}$""")
    }

    data class ExportSummary(val decks: Int, val sounds: Int)

    /** [dir] holds the restored sound copies; delete it if the import is undone. */
    data class Restored(val deck: DeckState, val vault: List<SpewEntry>, val sounds: Int, val missing: Int, val dir: File)

    /** Writes a backup to [uri] (from the system "save as" picker). */
    suspend fun export(uri: Uri): ExportSummary = withContext(Dispatchers.IO) {
        val deck = sounds.state.value
        val entries = vault.entries.value
        val builtinDir = sounds.builtinDirPath

        val paths = (deck.allFilePaths() + entries.mapNotNull { it.localFilePath })
            .filter { !it.startsWith(builtinDir) && File(it).isFile }
            .distinct()

        val manifestFiles = JSONObject()
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("Could not open the file to save to")
        ZipOutputStream(out.buffered()).use { zip ->
            paths.forEachIndexed { i, path ->
                val entryName = "audio/${i}_${safeName(File(path).name)}"
                manifestFiles.put(entryName, path)
                zip.putNextEntry(ZipEntry(entryName))
                File(path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putText("deck_state.json", sounds.exportStateJson())
            zip.putText("spew_vault.json", vault.toJson(entries))
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("app_version", BuildConfig.VERSION_NAME)
                put("created_at", Instant.now().toString())
                put("builtin_dir", builtinDir)
                put("files", manifestFiles)
            }
            zip.putText("manifest.json", manifest.toString(2))
        }
        ExportSummary(decks = deck.boards.size, sounds = paths.size)
    }

    /**
     * Reads a backup from [uri]: copies its sounds into the app and returns the decks and vault
     * with file paths pointing at the copies. Nothing is applied yet; the caller decides.
     */
    suspend fun read(uri: Uri): Restored = withContext(Dispatchers.IO) {
        val restoreDir = File(sounds.cacheDir, "restored_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            readInto(uri, restoreDir)
        } catch (e: Exception) {
            restoreDir.deleteRecursively()
            throw e
        }
    }

    private fun readInto(uri: Uri, restoreDir: File): Restored {
        val extracted = HashMap<String, File>()   // zip entry name -> restored file
        var deckJson: String? = null
        var vaultJson: String? = null
        var manifestJson: String? = null
        var total = 0L

        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the backup file")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                when {
                    entry.name == "deck_state.json" -> deckJson = zip.readTextLimited()
                    entry.name == "spew_vault.json" -> vaultJson = zip.readTextLimited()
                    entry.name == "manifest.json" -> manifestJson = zip.readTextLimited()
                    AUDIO_ENTRY.matches(entry.name) -> {
                        // The name is checked above (no slashes beyond "audio/"), so it cannot escape restoreDir.
                        val target = File(restoreDir, entry.name.removePrefix("audio/"))
                        val written = zip.copyToLimited(target)
                        total += written
                        if (total > MAX_TOTAL_BYTES) throw IOException("This backup is too large")
                        extracted[entry.name] = target
                    }
                    // Anything else is ignored.
                }
            }
        }

        val manifest = manifestJson?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: throw IOException("This isn't a PSSoundboard backup")
        if (manifest.optInt("format", 0) > FORMAT) throw IOException("This backup is from a newer version of the app")
        val deck = deckJson?.let { sounds.parseStateJson(it) } ?: throw IOException("The backup has no usable decks")
        val entries = vaultJson?.let { runCatching { vault.parse(it) }.getOrNull() } ?: emptyList()

        // Map each original path to where its copy now lives; built-in clips map to this install's folder.
        val pathMap = HashMap<String, String>()
        manifest.optJSONObject("files")?.let { files ->
            files.keys().forEach { entryName ->
                extracted[entryName]?.let { pathMap[files.getString(entryName)] = it.absolutePath }
            }
        }
        val oldBuiltin = manifest.optString("builtin_dir", "")
        val newBuiltin = sounds.builtinDirPath
        var missing = 0
        fun remap(path: String?): String? {
            if (path == null) return null
            pathMap[path]?.let { return it }
            if (oldBuiltin.isNotEmpty() && path.startsWith(oldBuiltin)) {
                val local = newBuiltin + path.removePrefix(oldBuiltin)
                if (File(local).isFile) return local
            }
            missing++
            return null
        }

        val restoredDeck = deck.copy(
            boards = deck.boards.map { board ->
                board.copy(pads = board.pads.map { pad -> pad.copy(filePath = remap(pad.filePath)) })
            }
        )
        val restoredVault = entries.map { it.copy(localFilePath = remap(it.localFilePath)) }
        return Restored(restoredDeck, restoredVault, sounds = extracted.size, missing = missing, dir = restoreDir)
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "sound.wav" }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun InputStream.readTextLimited(): String {
        val bytes = readNBytesLimited(MAX_ENTRY_BYTES)
        return String(bytes, Charsets.UTF_8)
    }

    private fun InputStream.readNBytesLimited(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw IOException("A file in this backup is too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun InputStream.copyToLimited(target: File): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        target.outputStream().use { out ->
            while (true) {
                val n = read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_ENTRY_BYTES) {
                    target.delete()
                    throw IOException("A sound in this backup is too large")
                }
                out.write(buffer, 0, n)
            }
        }
        return total
    }
}
