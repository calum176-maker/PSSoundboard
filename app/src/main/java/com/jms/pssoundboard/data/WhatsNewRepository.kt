package com.jms.pssoundboard.data

import android.content.Context
import androidx.core.content.edit
import com.jms.pssoundboard.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** The changes in one release. */
data class WhatsNewRelease(val version: String, val items: List<String>)

/** This build and the last few releases, newest first, as written into assets/whats_new.json by the GitHub build. */
data class WhatsNew(val version: String, val releases: List<WhatsNewRelease>)

/**
 * "What's new" after an update: shown once per version, covering every release since the one the
 * person last saw. Local builds have no list, so nothing shows.
 */
class WhatsNewRepository(private val context: Context) {
    companion object {
        private const val PREFS = "whats_new"
        private const val KEY_SEEN = "seen_version"
        private val NUMBER = Regex("\\d+")
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val notes: WhatsNew? by lazy {
        runCatching {
            val json = JSONObject(context.assets.open("whats_new.json").bufferedReader().use { it.readText() })
            val version = json.optString("version", BuildConfig.VERSION_NAME)
            val releases = json.optJSONArray("releases")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.let { WhatsNewRelease(it.optString("version"), it.optJSONArray("items").strings()) }
                }
            } ?: listOf(WhatsNewRelease(version, json.optJSONArray("items").strings())) // older single-release format
            WhatsNew(version, releases.filter { it.items.isNotEmpty() })
        }.getOrNull()?.takeIf { it.releases.isNotEmpty() }
    }

    /** True the first time this version opens (after an update or a fresh install). */
    fun isUnseen(): Boolean = notes != null && prefs.getString(KEY_SEEN, null) != BuildConfig.VERSION_NAME

    /** Releases newer than the one last seen; on a fresh install, just the newest. */
    fun unseenReleases(): List<WhatsNewRelease> {
        val all = notes?.releases ?: return emptyList()
        val seen = prefs.getString(KEY_SEEN, null)?.let(::versionNumbers) ?: return all.take(1)
        return all.filter { compareVersions(versionNumbers(it.version), seen) > 0 }.ifEmpty { all.take(1) }
    }

    fun markSeen() = prefs.edit { putString(KEY_SEEN, BuildConfig.VERSION_NAME) }

    private fun versionNumbers(version: String): List<Int> =
        NUMBER.findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
