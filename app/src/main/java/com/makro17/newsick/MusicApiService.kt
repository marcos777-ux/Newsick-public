package com.makro17.newsick

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ══════════════════════════════════════════════════════════
// API DE BÚSQUEDA DE MÚSICA — iTunes Search (gratuita, sin clave)
// Docs: https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI
// ══════════════════════════════════════════════════════════

data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesTrack> = emptyList()
)

data class ItunesTrack(
    val trackId: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val artworkUrl100: String = "",   // imagen 100×100
    val collectionName: String? = null,
    val kind: String? = null,         // "song" para filtrar
    val previewUrl: String? = null
) {
    /** Convierte la miniatura 100×100 a 300×300 para mejor calidad. */
    val artworkUrl300: String
        get() = artworkUrl100.replace("100x100", "300x300")
}

interface ItunesApiService {
    @GET("search")
    suspend fun searchMusic(
        @Query("term")   term: String,
        @Query("media")  media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit")  limit: Int = 25
    ): ItunesSearchResponse
}

object ItunesRetrofit {
    val api: ItunesApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")  // ✅ Con slash al final
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItunesApiService::class.java)
    }
}
