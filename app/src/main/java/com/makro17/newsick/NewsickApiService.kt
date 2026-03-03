package com.makro17.newsick

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════
// MODELOS DE DATOS PARA LA API
// ══════════════════════════════════════════════════════════

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val username: String)

@Serializable
data class UserResponse(val id: Int, val email: String, val username: String, val bio: String)

@Serializable
data class AuthResponse(val user: UserResponse, val token: String)

@Serializable
data class PostResponse(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val timestamp: Long,
    val photoCount: Int
)

@Serializable
data class PhotoResponse(
    val id: Int,
    val trackId: String,
    val photoUri: String,
    val userId: Int,
    val username: String,
    val timestamp: Long
)

@Serializable
data class PostRequest(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val timestamp: Long,
    val photos: List<PhotoRequest>
)

@Serializable
data class PhotoRequest(
    val photoUri: String,
    val userId: Int,
    val username: String,
    val timestamp: Long
)

// ══════════════════════════════════════════════════════════
// INTERFAZ DE LA API
// ══════════════════════════════════════════════════════════

interface NewsickApiService {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): retrofit2.Response<AuthResponse>

    @GET("api/posts")
    suspend fun getPosts(): retrofit2.Response<List<PostResponse>>

    @GET("api/posts/{trackId}/photos")
    suspend fun getPhotos(@Path("trackId") trackId: String): retrofit2.Response<List<PhotoResponse>>

    @POST("api/posts")
    suspend fun createPost(@Body request: PostRequest): retrofit2.Response<Unit>

    @GET("api/health")
    suspend fun healthCheck(): retrofit2.Response<String>
}

// ══════════════════════════════════════════════════════════
// CONFIGURACIÓN DE RETROFIT
// ══════════════════════════════════════════════════════════

object NewsickRetrofit {
    // ⚠️ CAMBIA ESTA IP si tu servidor tiene otra dirección
    private const val BASE_URL = "https://newsick.duckdns.org/"

    val api: NewsickApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsickApiService::class.java)
    }
}