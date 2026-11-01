package com.jms.pssoundboard.data

import android.content.Context
import androidx.core.content.edit
import com.jms.pssoundboard.BuildConfig
import org.json.JSONObject

/** The changes in this build, as written into assets/whats_new.json by the GitHub build. */
data class WhatsNew(val version: String, val items: List<String>)

/**
 * "What's new" after an update: shown once per version. Local builds have no list, so nothing shows.
 */
class WhatsNewRepository(private val context: Context) {
    companion object {
        private const val PREFS = "whats_new"
        private const val KEY_SEEN = "seen_version"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val notes: WhatsNew? by lazy {
        runCatching {
            val json = JSONObject(context.assets.open("whats_new.json").bufferedReader().use { it.readText() })
            val array = json.optJSONArray("items")
            val items = (0 until (array?.length() ?: 0)).mapNotNull { i -> array?.optString(i)?.takeIf { it.isNotBlank() } }
            WhatsNew(json.optString("version", BuildConfig.VERSION_NAME), items)
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    /** True the first time this version opens (after an update or a fresh install). */
    fun isUnseen(): Boolean = notes != null && prefs.getString(KEY_SEEN, null) != BuildConfig.VERSION_NAME

    fun markSeen() = prefs.edit { putString(KEY_SEEN, BuildConfig.VERSION_NAME) }
}
