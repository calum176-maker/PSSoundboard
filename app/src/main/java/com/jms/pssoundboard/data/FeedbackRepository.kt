package com.jms.pssoundboard.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.jms.pssoundboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Sends tester feedback to the team's webhook, which files it as a card on the team's private board.
 * Reports that cannot be sent (offline, server down) are kept and retried later.
 */
class FeedbackRepository(context: Context) {

    companion object {
        private const val TAG = "FeedbackRepository"
        private const val PREFS = "feedback"
        private const val QUEUE_KEY = "pending"
        private const val MAX_QUEUED = 20
        const val MAX_MESSAGE_LENGTH = 4000
    }

    data class Outcome(val sent: Boolean, val issueNumber: Int? = null)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when this build has a feedback endpoint; otherwise the app offers to share the report instead. */
    val isConfigured: Boolean get() = BuildConfig.FEEDBACK_URL.isNotBlank()

    fun buildReport(category: String, screen: String, message: String, details: Map<String, String>?): JSONObject =
        JSONObject().apply {
            put("category", category)
            put("screen", screen)
            put("message", message.trim().take(MAX_MESSAGE_LENGTH))
            put("sent_at", Instant.now().toString())
            if (details != null) put("details", JSONObject(details as Map<*, *>))
        }

    fun deviceDetails(extra: Map<String, String> = emptyMap()): Map<String, String> = linkedMapOf(
        "app_version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    ) + extra

    /** Plain-text version for sharing when there is no endpoint. */
    fun reportAsText(report: JSONObject): String = buildString {
        appendLine("Spewboard feedback (${report.optString("category")}, ${report.optString("screen")})")
        appendLine()
        appendLine(report.optString("message"))
        report.optJSONObject("details")?.let { d ->
            appendLine()
            d.keys().forEach { k -> appendLine("$k: ${d.optString(k)}") }
        }
    }

    /** Sends [report]; if that fails it is queued for [retryPending]. */
    suspend fun send(report: JSONObject): Outcome {
        val result = post(report)
        if (result == null) enqueue(report)
        return Outcome(sent = result != null, issueNumber = result?.takeIf { it > 0 })
    }

    /** Re-sends queued reports, oldest first; stops at the first failure. Returns how many went. */
    suspend fun retryPending(): Int {
        if (!isConfigured) return 0
        val queue = readQueue()
        var sent = 0
        for (i in 0 until queue.length()) {
            if (post(queue.getJSONObject(i)) == null) break
            sent++
        }
        if (sent > 0) {
            val remaining = JSONArray()
            for (i in sent until queue.length()) remaining.put(queue.get(i))
            writeQueue(remaining)
        }
        return sent
    }

    fun pendingCount(): Int = readQueue().length()

    /** POSTs the report. Returns the issue number (0 if the server did not say), or null on failure. */
    private suspend fun post(report: JSONObject): Int? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(BuildConfig.FEEDBACK_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Feedback-Key", BuildConfig.FEEDBACK_KEY)
            }
            connection.outputStream.use { it.write(report.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Feedback rejected with HTTP $code")
                return@withContext null
            }
            val body = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
            runCatching { JSONObject(body).optInt("issue", 0) }.getOrDefault(0)
        } catch (t: Throwable) {
            Log.w(TAG, "Feedback not sent: ${t.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun enqueue(report: JSONObject) {
        val queue = readQueue()
        queue.put(report)
        // Keep the newest MAX_QUEUED so a long offline spell cannot grow this without bound.
        val trimmed = JSONArray()
        for (i in maxOf(0, queue.length() - MAX_QUEUED) until queue.length()) trimmed.put(queue.get(i))
        writeQueue(trimmed)
    }

    private fun readQueue(): JSONArray =
        runCatching { JSONArray(prefs.getString(QUEUE_KEY, "[]")) }.getOrDefault(JSONArray())

    private fun writeQueue(queue: JSONArray) {
        prefs.edit { putString(QUEUE_KEY, queue.toString()) }
    }
}
