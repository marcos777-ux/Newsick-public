package com.makro17.newsick

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ══════════════════════════════════════════════════════════
// API DE BÚSQUEDA DE MÚSICA — iTunes Search (gratuita)
// ══════════════════════════════════════════════════════════

data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesTrack> = emptyList()
)

data class ItunesTrack(
    val trackId: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val artworkUrl100: String = "",
    val collectionName: String? = null,
    val kind: String? = null,
    val previewUrl: String? = null   // URL de preview de 30 segundos
) {
    val artworkUrl300: String
        get() = artworkUrl100.replace("100x100", "300x300")
}

interface ItunesApiService {
    @GET("search")
    suspend fun searchMusic(
        @Query("term")   term: String,
        @Query("media")  media: String  = "music",
        @Query("entity") entity: String = "song",
        @Query("limit")  limit: Int     = 25
    ): ItunesSearchResponse

    /** Obtiene los datos completos de una canción por su trackId (incluye previewUrl) */
    @GET("lookup")
    suspend fun lookupTrack(
        @Query("id") trackId: Long
    ): ItunesSearchResponse
}

object ItunesRetrofit {
    val api: ItunesApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItunesApiService::class.java)
    }
}
