package com.jms.pssoundboard.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Filterable sources for online sound searches.
 */
enum class SoundSource(val id: String, val label: String) {
    ALL("all", "All Sources"),
    ONE_ZERO_ONE("101sb", "101Soundboards"),
    MY_INSTANTS("myinstants", "MyInstants")
}

/**
 * Result page containing a batch of sounds (10 at a time) and pagination metadata.
 */
data class OnlineSearchResult(
    val items: List<LibraryItem> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val page: Int = 1,
    /** True when the request itself failed (offline, timeout, HTTP error), as opposed to no matches. */
    val failed: Boolean = false,
)

/**
 * Service to search and retrieve internet memes, movie quotes, and gaming sound clips
 * from multiple public sound libraries (101Soundboards and MyInstants)
 * with relevance ranking, pagination, and direct MP3 resolution.
 */
object OnlineSoundService {

    private const val TAG = "OnlineSoundService"
    private const val MY_INSTANTS_API = "https://www.myinstants.com/api/v1/instants/?format=json"
    private const val ONE_ZERO_ONE_MCP_URL = "https://www.101soundboards.com/mcp/search"
    private const val USER_AGENT = "Spewboard/1.0 (Android; Hardware-Audio)"
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val cache = ConcurrentHashMap<String, OnlineSearchResult>()
    private val cursorCache = ConcurrentHashMap<String, String>()
    private val audioUrlCache = ConcurrentHashMap<String, String>()

    /** Curated quick-search tags for movie, gaming, and internet favorites. */
    val PRESET_QUICK_SEARCHES = listOf(
        "Trending" to "",
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
        "Halo" to "halo",
        "Pokemon" to "pokemon",
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
     * Searches for sounds across the chosen [source] (All, 101Soundboards, or MyInstants)
     * with query relevance ranking and pagination.
     */
    suspend fun searchSounds(
        query: String,
        page: Int = 1,
        source: SoundSource = SoundSource.ALL
    ): OnlineSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val cacheKey = "$cleanQuery:${source.id}:p$page"
        cache[cacheKey]?.let { return@withContext it }

        val result = when (source) {
            SoundSource.ONE_ZERO_ONE -> fetch101Page(cleanQuery, page)
            SoundSource.MY_INSTANTS -> fetchMyInstantsPage(cleanQuery, page)
            SoundSource.ALL -> {
                coroutineScope {
                    val d1 = async { fetch101Page(cleanQuery, page) }
                    val d2 = async { fetchMyInstantsPage(cleanQuery, page) }
                    val r101 = d1.await()
                    val rMyInstants = d2.await()

                    // Combine, score by relevance, and deduplicate
                    val combinedItems = r101.items + rMyInstants.items
                    val rankedItems = rankAndDeduplicate(combinedItems, cleanQuery)

                    OnlineSearchResult(
                        items = rankedItems,
                        totalCount = r101.totalCount + rMyInstants.totalCount,
                        hasMore = r101.hasMore || rMyInstants.hasMore,
                        page = page,
                        failed = r101.failed && rMyInstants.failed
                    )
                }
            }
        }

        // Fallback on page 1 if query returned 0 results: try sanitized alphanumeric query
        if (page == 1 && !result.failed && result.items.isEmpty() && cleanQuery.isNotBlank()) {
            val sanitized = cleanQuery
                .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
            if (sanitized.isNotBlank() && !sanitized.equals(cleanQuery, ignoreCase = true)) {
                val fallback = searchSounds(sanitized, 1, source)
                if (fallback.items.isNotEmpty()) {
                    if (!fallback.failed) cache[cacheKey] = fallback
                    return@withContext fallback
                }
            }
        }

        // Never cache a failure, or one offline search stays empty until the app restarts.
        if (!result.failed) cache[cacheKey] = result
        result
    }

    private fun fetch101Page(query: String, page: Int): OnlineSearchResult {
        val cleanQuery = query.trim()
        val effectiveTerm = when {
            cleanQuery.isBlank() -> "trending"
            cleanQuery.length < 3 -> "$cleanQuery sound"
            else -> cleanQuery
        }

        val limit = 10
        val cursorKey = "$cleanQuery:101:p$page"
        var cursor = cursorCache[cursorKey]
        if (cursor == null && page > 1) {
            val offset = (page - 1) * limit
            cursor = Base64.encodeToString("{\"offset\":$offset}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(ONE_ZERO_ONE_MCP_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            }

            val argsObj = JSONObject().apply {
                put("search_term", effectiveTerm)
                put("limit", limit)
                if (!cursor.isNullOrBlank()) {
                    put("cursor", cursor)
                }
            }
            val requestBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", page)
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", "sound-search-tool")
                    put("arguments", argsObj)
                })
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(requestBody.toString())
                it.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "101soundboards MCP returned HTTP $responseCode for '$effectiveTerm' (page $page)")
                return OnlineSearchResult(page = page, failed = true)
            }

            val jsonText = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            val root = JSONObject(jsonText)
            val resultObj = root.optJSONObject("result") ?: return OnlineSearchResult(page = page)

            // Check structuredContent or fallback to parsing content[0].text
            var resultsArray = resultObj.optJSONObject("structuredContent")?.optJSONArray("results")
            var nextCursor = resultObj.optJSONObject("structuredContent")?.optString("next_cursor", "")

            if (resultsArray == null) {
                val contentArray = resultObj.optJSONArray("content")
                if (contentArray != null && contentArray.length() > 0) {
                    val textObj = contentArray.optJSONObject(0)
                    val innerText = textObj?.optString("text", "") ?: ""
                    if (innerText.isNotBlank()) {
                        val innerJson = JSONObject(innerText)
                        resultsArray = innerJson.optJSONArray("results")
                        nextCursor = innerJson.optString("next_cursor", "")
                    }
                }
            }

            if (resultsArray == null) return OnlineSearchResult(page = page)

            if (!nextCursor.isNullOrBlank()) {
                cursorCache["$cleanQuery:101:p${page + 1}"] = nextCursor
            }

            val items = ArrayList<LibraryItem>(resultsArray.length())
            for (i in 0 until resultsArray.length()) {
                val obj = resultsArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim()
                val soundUrl = obj.optString("url", "").trim()
                if (title.isBlank() || soundUrl.isBlank()) continue

                val slug = soundUrl.substringAfterLast("/sounds/").substringBefore('/')
                val category = determineCategory(title, "", query)

                items.add(
                    LibraryItem(
                        id = "101sb:${slug.ifBlank { title.hashCode().toString() }}:$page:$i",
                        title = title,
                        category = category,
                        url = soundUrl,
                        source = "101Soundboards"
                    )
                )
            }

            val hasMore = !nextCursor.isNullOrBlank() && items.isNotEmpty()
            return OnlineSearchResult(
                items = items,
                totalCount = items.size * page + if (hasMore) 10 else 0,
                hasMore = hasMore,
                page = page
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to query 101soundboards for '$effectiveTerm' p$page: ${t.message}")
            return OnlineSearchResult(page = page, failed = true)
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchMyInstantsPage(query: String, page: Int): OnlineSearchResult {
        val encoded = if (query.isBlank()) "" else URLEncoder.encode(query, "UTF-8")
        val urlString = if (encoded.isBlank()) {
            if (page == 1) MY_INSTANTS_API else "$MY_INSTANTS_API&page=$page"
        } else {
            if (page == 1) "$MY_INSTANTS_API&name=$encoded" else "$MY_INSTANTS_API&name=$encoded&page=$page"
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
            // The API answers 404 "Invalid page" past the last page (the total can shrink between requests).
            if (responseCode == 404 && page > 1) return OnlineSearchResult(page = page)
            if (responseCode !in 200..299) {
                Log.w(TAG, "MyInstants returned HTTP $responseCode for '$query' (page $page)")
                return OnlineSearchResult(page = page, failed = true)
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
                        source = "MyInstants"
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
            Log.e(TAG, "Failed to query MyInstants sounds for '$query' p$page: ${t.message}")
            return OnlineSearchResult(page = page, failed = true)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Resolves the direct audio MP3 stream URL for sounds that link to a sound page
     * (such as 101Soundboards sound detail pages). Caches results in memory for instant reuse.
     */
    suspend fun resolveAudioUrl(url: String): String = withContext(Dispatchers.IO) {
        if (!url.contains("101soundboards.com/sounds/")) return@withContext url
        audioUrlCache[url]?.let { return@withContext it }

        var connection: HttpURLConnection? = null
        try {
            val target = URL(url)
            connection = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val html = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                val ogAudioRegex = Regex("""property=["']og:audio["']\s+content=["'](https?://[^"']+\.mp3)""", RegexOption.IGNORE_CASE)
                val contentUrlRegex = Regex(""""contentUrl"\s*:\s*"(https?://[^"]+\.mp3)""", RegexOption.IGNORE_CASE)
                val audioSrcRegex = Regex("""<audio[^>]+src=["'](https?://[^"']+\.mp3)""", RegexOption.IGNORE_CASE)

                val directMp3 = ogAudioRegex.find(html)?.groupValues?.get(1)
                    ?: contentUrlRegex.find(html)?.groupValues?.get(1)
                    ?: audioSrcRegex.find(html)?.groupValues?.get(1)

                if (directMp3 != null) {
                    audioUrlCache[url] = directMp3
                    return@withContext directMp3
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve 101soundboards direct mp3 for $url: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        url
    }

    private fun rankAndDeduplicate(items: List<LibraryItem>, query: String): List<LibraryItem> {
        val q = query.lowercase().trim()
        val seenTitles = HashSet<String>()
        val scored = ArrayList<Pair<LibraryItem, Int>>(items.size)

        for (item in items) {
            val t = item.title.lowercase().trim()
            val normalizedKey = t.replace(Regex("[^a-z0-9]"), "")
            if (normalizedKey.isNotBlank() && !seenTitles.add(normalizedKey)) {
                continue // Deduplicate identical clip titles across providers
            }

            var score = 0
            if (q.isNotBlank()) {
                when {
                    t == q -> score += 100
                    t.startsWith(q) -> score += 60
                    t.contains(Regex("\\b${Regex.escape(q)}\\b")) -> score += 40
                    t.contains(q) -> score += 20
                }
                // Slight boost to 101soundboards for clean dialogue/movie/gaming lines
                if (item.source.contains("101", ignoreCase = true)) {
                    score += 5
                }
            }
            scored.add(item to score)
        }

        return if (q.isNotBlank()) {
            scored.sortedByDescending { it.second }.map { it.first }
        } else {
            items
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
                text.contains("mario") || text.contains("halo") || text.contains("call of duty") -> "Gaming Sound"
            text.contains("tv") || text.contains("show") || text.contains("series") ||
                text.contains("seinfeld") || text.contains("curb") -> "TV & Film"
            else -> "Internet Meme"
        }
    }
}
