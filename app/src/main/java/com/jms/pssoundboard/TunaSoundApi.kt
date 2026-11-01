package com.jms.pssoundboard

import retrofit2.http.GET
import retrofit2.http.Query

interface TunaSoundApi {
    @GET("v1/sounds/search")
    suspend fun searchSounds(
        @Query("search") search: String? = null,
        @Query("tag") tag: String? = null,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 10
    ): TunaSearchResponse
}

