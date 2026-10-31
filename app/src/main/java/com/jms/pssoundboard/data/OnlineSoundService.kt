package com.jms.pssoundboard.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Result page containing a batch of sounds (10 at a time) and pagination metadata.
 */
data class OnlineSearchResult(
    val items: List<LibraryItem> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val page: Int = 1,
)

/**
 * Service to search and retrieve internet memes, movie quotes, and gaming sound clips
 * from public sound libraries and APIs with 10-at-a-time pagination and infinite scroll.
 */
object OnlineSoundService {

    private const val TAG = "OnlineSoundService"
    private const val BASE_API = "https://www.myinstants.com/api/v1/instants/?format=json"
    private const val USER_AGENT = "PSPartySoundboard/1.0 (Android; Hardware-Audio)"

    private val cache = LinkedHashMap<String, OnlineSearchResult>()

    /** Curated quick-search tags for movie and internet favorites. */
    val PRESET_QUICK_SEARCHES = listOf(
        "Trending Memes" to "",
        "Movie Quotes" to "movie",
        "Star Wars" to "star wars",
        "Terminator" to "terminator",
        "Batman" to "batman",
        "Avengers" to "avengers",
        "Godfather" to "godfather",
        "Pulp Fiction" to "pulp fiction",
        "Matrix" to "matrix",
        "Lord of Rings" to "lord of the rings",
        "Wilhelm Scream" to "wilhelm",
        "GTA Wasted" to "wasted",
        "CS:GO" to "csgo",
        "Call of Duty" to "call of duty",
        "Metal Gear" to "metal gear",
        "Mario" to "mario",
        "Minecraft" to "minecraft",
        "Roblox / OOF" to "oof",
        "Bruh" to "bruh",
        "Emotional Damage" to "emotional damage",
        "Sheesh" to "sheesh",
        "GigaChad" to "gigachad",
        "Directed by" to "directed by",
        "Windows Error" to "windows error",
        "Sad Violin" to "sad violin",
        "Rick Roll" to "rick roll",
        "Airhorn" to "airhorn",
        "Curb Theme" to "curb",
        "Gordon Ramsay" to "gordon ramsay",
    )

    /**
     * Searches for sounds by name/query or fetches trending sounds if [query] is blank,
     * retrieving 10 sounds per page.
     */
    suspend fun searchSounds(query: String, page: Int = 1): OnlineSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val cacheKey = "$cleanQuery:p$page"
        cache[cacheKey]?.let { return@withContext it }

        var result = fetchPage(cleanQuery, page)

        // Smart fallback on page 1: if cleanQuery returned 0 results, sanitize punctuation or try key terms
        if (page == 1 && result.items.isEmpty() && cleanQuery.isNotBlank()) {
            val sanitized = cleanQuery
                .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
            if (sanitized.isNotBlank() && !sanitized.equals(cleanQuery, ignoreCase = true)) {
                val fallback = fetchPage(sanitized, 1)
                if (fallback.items.isNotEmpty()) {
                    result = fallback
                }
            }

            // If still empty and multi-word, query the most significant 2 words
            if (result.items.isEmpty()) {
                val words = cleanQuery.split(Regex("\\s+")).filter { it.length > 2 }
                if (words.size >= 2) {
                    val subQuery = words.take(2).joinToString(" ")
                    val subResult = fetchPage(subQuery, 1)
                    if (subResult.items.isNotEmpty()) {
                        result = subResult
                    }
                }
            }
        }

        cache[cacheKey] = result
        result
    }

    private fun fetchPage(query: String, page: Int): OnlineSearchResult {
        val encoded = if (query.isBlank()) "" else URLEncoder.encode(query, "UTF-8")
        val urlString = if (encoded.isBlank()) {
            if (page == 1) BASE_API else "$BASE_API&page=$page"
        } else {
            if (page == 1) "$BASE_API&name=$encoded" else "$BASE_API&name=$encoded&page=$page"
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "Search returned HTTP $responseCode for '$query' (page $page)")
                return OnlineSearchResult(page = page)
            }

            val jsonText = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            val root = JSONObject(jsonText)
            val totalCount = root.optInt("count", 0)
            val nextUrl = root.optString("next", "").trim()
            val resultsArray = root.optJSONArray("results") ?: return OnlineSearchResult(totalCount = totalCount, page = page)

            val items = ArrayList<LibraryItem>(resultsArray.length())
            for (i in 0 until resultsArray.length()) {
                val obj = resultsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val soundUrl = obj.optString("sound", "").trim()
                if (name.isBlank() || soundUrl.isBlank()) continue

                val slug = obj.optString("slug", name.hashCode().toString())
                val desc = obj.optString("description", "")
                val category = determineCategory(name, desc, query)

                items.add(
                    LibraryItem(
                        id = "net:$slug:$page:$i",
                        title = name,
                        category = category,
                        url = soundUrl,
                    )
                )
            }

            val hasMore = nextUrl.isNotBlank() && items.isNotEmpty()
            return OnlineSearchResult(
                items = items,
                totalCount = totalCount,
                hasMore = hasMore,
                page = page
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to query online sounds for '$query' p$page: ${t.message}")
            return OnlineSearchResult(page = page)
        } finally {
            connection?.disconnect()
        }
    }

    private fun determineCategory(name: String, desc: String, query: String): String {
        val text = "$name $desc $query".lowercase()
        return when {
            text.contains("movie") || text.contains("film") || text.contains("hollywood") ||
                text.contains("terminator") || text.contains("star wars") || text.contains("batman") ||
                text.contains("wilhelm") || text.contains("cinema") || text.contains("godfather") ||
                text.contains("pulp fiction") || text.contains("matrix") || text.contains("avengers") -> "Movie Quote"
            text.contains("game") || text.contains("gta") || text.contains("csgo") ||
                text.contains("roblox") || text.contains("minecraft") || text.contains("metal gear") ||
                text.contains("mario") || text.contains("call of duty") -> "Gaming Sound"
            text.contains("tv") || text.contains("show") || text.contains("series") ||
                text.contains("seinfeld") || text.contains("curb") -> "TV & Film"
            else -> "Internet Meme"
        }
    }
}
