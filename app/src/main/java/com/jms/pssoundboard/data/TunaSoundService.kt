package com.jms.pssoundboard.data

import android.util.Log
import com.jms.pssoundboard.TunaSearchResponse
import com.jms.pssoundboard.TunaSoundApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Service to search and retrieve community meme sounds from Voicemod Tuna API.
 * Base URL: https://tuna-api.voicemod.net/
 */
object TunaSoundService {

    private const val TAG = "TunaSoundService"
    private const val BASE_URL = "https://tuna-api.voicemod.net/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Spewboard/1.0 (Android; Hardware-Audio)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val api: TunaSoundApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TunaSoundApi::class.java)
    }

    /**
     * Searches community meme sounds via the Voicemod Tuna API.
     * When [query] is empty or trending, searches tag=meme.
     */
    suspend fun searchTunaSounds(
        query: String,
        page: Int = 1,
        pageSize: Int = 10
    ): OnlineSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val isTrending = cleanQuery.isEmpty() ||
            cleanQuery.equals("trending", ignoreCase = true) ||
            cleanQuery.equals("trending memes", ignoreCase = true) ||
            cleanQuery.equals("popular", ignoreCase = true)

        try {
            val response: TunaSearchResponse = if (isTrending) {
                api.searchSounds(
                    search = null,
                    tag = "meme",
                    page = page,
                    size = pageSize
                )
            } else {
                api.searchSounds(
                    search = cleanQuery,
                    tag = null,
                    page = page,
                    size = pageSize
                )
            }

            val rawItems = response.items.orEmpty()
            val libraryItems = rawItems
                .filter { it.resolvedAudioUrl != null }
                .map { it.toLibraryItem() }

            val hasMore = page < response.lastPage && libraryItems.isNotEmpty()

            OnlineSearchResult(
                items = libraryItems,
                totalCount = response.total,
                hasMore = hasMore,
                page = page,
                failed = false
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Tuna search failed for '$query' p$page: ${t.message}", t)
            OnlineSearchResult(
                items = emptyList(),
                totalCount = 0,
                hasMore = false,
                page = page,
                failed = true
            )
        }
    }
}
