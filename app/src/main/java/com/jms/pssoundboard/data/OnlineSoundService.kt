package com.jms.pssoundboard.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Filterable sources for online sound searches.
 */
enum class SoundSource(val id: String, val label: String) {
    TUNA("tuna", "Voicemod Tuna"),
    ALL("all", "All Sources"),
    MY_INSTANTS("myinstants", "MyInstants")
}

/**
 * Result page containing a batch of sounds and pagination metadata.
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
 * from public sound libraries with real trending charts, relevance ranking, and clean MP3 streams.
 */
object OnlineSoundService {

    private const val TAG = "OnlineSoundService"
    private const val MY_INSTANTS_API = "https://www.myinstants.com/api/v1/instants/?format=json"
    private const val USER_AGENT = "Spewboard/1.0 (Android; Hardware-Audio)"
    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val cache = ConcurrentHashMap<String, OnlineSearchResult>()

    /** Curated quick-search tags for movie, gaming, and internet favorites. */
    val PRESET_QUICK_SEARCHES = listOf(
        "Trending" to "",
        "Movie Quotes" to "movie",
        "Bruh" to "Bruh",
        "Vine Boom" to "Vine Boom",
        "GET OUT!" to "GET OUT!",
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
        "HUH?" to "HUH?",
    )

    /**
     * Checks if the query indicates a request for trending / popular sounds
     * rather than a specific text search.
     */
    fun isTrendingQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || q == "trending" || q == "trending memes" || q == "popular"
    }

    /**
     * Searches for sounds with relevance ranking and pagination.
     * When [query] is empty or "trending", returns community meme / trending charts.
     */
    suspend fun searchSounds(
        query: String,
        page: Int = 1,
        source: SoundSource = SoundSource.TUNA
    ): OnlineSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val cacheKey = "${source.id}:$cleanQuery:p$page"
        cache[cacheKey]?.let { return@withContext it }

        val result = when (source) {
            SoundSource.TUNA -> TunaSoundService.searchTunaSounds(cleanQuery, page = page, pageSize = 10)
            SoundSource.MY_INSTANTS -> {
                if (isTrendingQuery(cleanQuery)) fetchTrendingPage(page)
                else fetchMyInstantsPage(cleanQuery, page)
            }
            SoundSource.ALL -> {
                val tunaDeferred = async { TunaSoundService.searchTunaSounds(cleanQuery, page = page, pageSize = 8) }
                val myInstantsDeferred = async {
                    if (isTrendingQuery(cleanQuery)) fetchTrendingPage(page)
                    else fetchMyInstantsPage(cleanQuery, page)
                }
                val tunaRes = tunaDeferred.await()
                val myInstantsRes = myInstantsDeferred.await()

                val combined = mutableListOf<LibraryItem>()
                val maxLen = maxOf(tunaRes.items.size, myInstantsRes.items.size)
                for (i in 0 until maxLen) {
                    if (i < tunaRes.items.size) combined.add(tunaRes.items[i])
                    if (i < myInstantsRes.items.size) combined.add(myInstantsRes.items[i])
                }

                val hasMore = tunaRes.hasMore || myInstantsRes.hasMore
                val totalCount = tunaRes.totalCount + myInstantsRes.totalCount
                val failed = tunaRes.failed && myInstantsRes.failed

                OnlineSearchResult(
                    items = combined,
                    totalCount = totalCount,
                    hasMore = hasMore,
                    page = page,
                    failed = failed
                )
            }
        }

        // Fallback on page 1 if query returned 0 results: try sanitized alphanumeric query
        if (page == 1 && !result.failed && result.items.isEmpty() && cleanQuery.isNotBlank() && !isTrendingQuery(cleanQuery)) {
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

    /**
     * Fetches real live trending sounds from MyInstants trending chart.
     * Falls back to the JSON API if HTML parsing is unavailable.
     */
    private fun fetchTrendingPage(page: Int): OnlineSearchResult {
        val urlString = if (page == 1) {
            "https://www.myinstants.com/en/trending/us/"
        } else {
            "https://www.myinstants.com/en/trending/us/?page=$page"
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "Trending HTML returned HTTP $responseCode for p$page; falling back to JSON API")
                return fetchMyInstantsPage("", page)
            }

            val html = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            val items = ArrayList<LibraryItem>()
            val regex = Regex("""play\('(/media/sounds/[^']+)'[\s\S]*?href="(/en/instant/[^"]+)"[^>]*>([^<]+)<""")
            val matches = regex.findAll(html)

            var index = 0
            for (match in matches) {
                val soundPath = match.groupValues[1].trim()
                val instantPath = match.groupValues[2].trim()
                val rawTitle = match.groupValues[3].trim()
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")

                if (rawTitle.isBlank() || soundPath.isBlank()) continue

                val fullSoundUrl = if (soundPath.startsWith("http")) soundPath else "https://www.myinstants.com$soundPath"
                val slug = instantPath.removePrefix("/en/instant/").removeSuffix("/")
                val category = determineCategory(rawTitle, "", "")

                items.add(
                    LibraryItem(
                        id = "trending:$slug:$page:$index",
                        title = rawTitle,
                        category = if (category == "Internet Meme") "Trending" else category,
                        url = fullSoundUrl,
                        source = "MyInstants"
                    )
                )
                index++
            }

            if (items.isEmpty()) {
                Log.w(TAG, "Trending HTML yielded 0 items for p$page; falling back to JSON API")
                return fetchMyInstantsPage("", page)
            }

            val hasMore = html.contains("page=${page + 1}") || items.size >= 10
            return OnlineSearchResult(
                items = items,
                // The trending chart gives no total, so report none rather than a guess
                totalCount = 0,
                hasMore = hasMore,
                page = page
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to fetch trending HTML page $page: ${t.message}; falling back to JSON API")
            return fetchMyInstantsPage("", page)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Searches MyInstants via public JSON API.
     */
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

            val rawItems = ArrayList<LibraryItem>(resultsArray.length())
            for (i in 0 until resultsArray.length()) {
                val obj = resultsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val soundUrl = obj.optString("sound", "").trim()
                if (name.isBlank() || soundUrl.isBlank()) continue

                val slug = obj.optString("slug", name.hashCode().toString())
                val desc = obj.optString("description", "")
                val category = determineCategory(name, desc, query)

                rawItems.add(
                    LibraryItem(
                        id = "net:$slug:$page:$i",
                        title = name,
                        category = category,
                        url = soundUrl,
                        source = "MyInstants"
                    )
                )
            }

            val rankedItems = rankAndDeduplicate(rawItems, query)
            val hasMore = nextUrl.isNotBlank() && rankedItems.isNotEmpty()
            return OnlineSearchResult(
                items = rankedItems,
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
     * Resolves the direct audio MP3 stream URL. Since MyInstants audio streams are already
     * direct and unwatermarked, this safely returns the URL directly.
     */
    fun resolveAudioUrl(url: String): String = url

    private fun rankAndDeduplicate(items: List<LibraryItem>, query: String): List<LibraryItem> {
        val q = query.lowercase().trim()
        val seenTitles = HashSet<String>()
        val scored = ArrayList<Pair<LibraryItem, Int>>(items.size)

        for (item in items) {
            val t = item.title.lowercase().trim()
            val normalizedKey = t.replace(Regex("[^a-z0-9]"), "")
            if (normalizedKey.isNotBlank() && !seenTitles.add(normalizedKey)) {
                continue
            }

            var score = 0
            if (q.isNotBlank() && !isTrendingQuery(q)) {
                when {
                    t == q -> score += 100
                    t.startsWith(q) -> score += 60
                    t.contains(Regex("\\b${Regex.escape(q)}\\b")) -> score += 40
                    t.contains(q) -> score += 20
                }
            }
            scored.add(item to score)
        }

        return if (q.isNotBlank() && !isTrendingQuery(q)) {
            scored.sortedByDescending { it.second }.map { it.first }
        } else {
            items
        }
    }

    private fun determineCategory(name: String, desc: String, query: String): String {
        val text = "$name $desc $query".lowercase()
        return when {
            text.contains("movie") || text.contains("film") || text.contains("hollywood") ||
                text.contains("Vine Boom") || text.contains("Bruh") || text.contains("GET OUT!") ||
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

