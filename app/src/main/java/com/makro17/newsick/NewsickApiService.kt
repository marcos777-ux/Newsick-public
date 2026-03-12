package com.makro17.newsick

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

object AuthManager {
    var token: String = ""
    var userId: Int   = 0
}

// ── Auth ──────────────────────────────────────────────────

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RegisterRequest(val email: String, val password: String, val username: String)
@Serializable data class UserResponse(
    val id: Int, val email: String = "", val username: String = "",
    val profilePhoto: String? = null, val bio: String? = null, val createdAt: String = ""
)
@Serializable data class AuthResponse(val user: UserResponse, val token: String)

// ── Posts ─────────────────────────────────────────────────

@Serializable data class PostResponse(
    val trackId: String, val trackName: String, val artistName: String,
    val artworkUrl: String, val timestamp: Long, val photoCount: Int
)
@Serializable data class PhotoResponse(
    val id: Int, val trackId: String, val photoUri: String,
    val userId: Int, val username: String, val timestamp: Long
)
@Serializable data class PostRequest(
    val trackId: String, val trackName: String, val artistName: String,
    val artworkUrl: String, val timestamp: Long, val photos: List<PhotoRequest>
)
@Serializable data class PhotoRequest(
    val photoUri: String, val userId: Int, val username: String, val timestamp: Long
)
@Serializable data class UploadResponse(val url: String)

// ── Usuarios ──────────────────────────────────────────────

@Serializable data class SearchUsersRequest(val query: String)
@Serializable data class UpdateProfileRequest(val bio: String, val username: String = "", val profilePhoto: String = "")
@Serializable data class DeleteAccountRequest(val password: String)

// ── Amigos ────────────────────────────────────────────────

@Serializable data class FriendRequestDto(val targetUserId: Int)
@Serializable data class RespondFriendRequest(val requestId: Int, val accept: Boolean)
@Serializable data class FriendRequestResponse(
    val id: Int, val senderId: Int, val senderUsername: String,
    val senderProfilePhoto: String = "", val status: String, val createdAt: String
)
@Serializable data class FriendshipResponse(
    val id: Int, val friendId: Int, val friendUsername: String,
    val friendProfilePhoto: String = "", val createdAt: String = ""
)
@Serializable data class NotificationResponse(
    val id: Int, val type: String, val title: String,
    val message: String, val isRead: Boolean, val createdAt: String
)
@Serializable data class FriendStatusResponse(val status: String)
@Serializable data class FriendCountResponse(val count: Int)

// ── Mapa ──────────────────────────────────────────────────

@Serializable data class UpdateLocationRequest(
    val latitude: Double, val longitude: Double,
    val trackId: String? = null, val trackName: String? = null,
    val artistName: String? = null, val artworkUrl: String? = null,
    val previewUrl: String? = null, val platform: String? = "newsick"
)
@Serializable data class NearbyUserResponse(
    val userId: Int, val username: String, val profilePhoto: String,
    val latitude: Double, val longitude: Double,
    val trackId: String? = null, val trackName: String? = null,
    val artistName: String? = null, val artworkUrl: String? = null,
    val previewUrl: String? = null, val platform: String? = null,
    val updatedAt: Long = 0L
)

// ── Versión ───────────────────────────────────────────────

@Serializable data class MinVersionResponse(val minVersionCode: Int)

// ── Recomendaciones ───────────────────────────────────────

@Serializable data class SendRecommendationRequest(
    val toUserId: Int,
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val previewUrl: String? = null
)

@Serializable data class RecommenderInfo(
    val userId: Int,
    val username: String,
    val profilePhoto: String
)

@Serializable data class RecommendationResponse(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val previewUrl: String? = null,
    val recommendedBy: List<RecommenderInfo>,
    val totalCount: Int
)

// ── Interfaz Retrofit ─────────────────────────────────────

interface NewsickApiService {

    @POST("api/login")
    suspend fun login(@Body r: LoginRequest): retrofit2.Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body r: RegisterRequest): retrofit2.Response<AuthResponse>

    @GET("api/health")
    suspend fun healthCheck(): retrofit2.Response<String>

    @GET("api/version/min")
    suspend fun getMinVersion(): retrofit2.Response<MinVersionResponse>

    // Fotos
    @Multipart
    @POST("api/uploads/photo")
    suspend fun uploadPhoto(@Part photo: MultipartBody.Part): retrofit2.Response<UploadResponse>

    // Posts
    @GET("api/posts")      suspend fun getPosts(): retrofit2.Response<List<PostResponse>>
    @GET("api/posts/feed") suspend fun getFeed(): retrofit2.Response<List<PostResponse>>

    @GET("api/posts/user/{userId}")
    suspend fun getUserPosts(@Path("userId") userId: Int): retrofit2.Response<List<PostResponse>>

    @GET("api/posts/common/{targetUserId}")
    suspend fun getCommonSongs(@Path("targetUserId") targetUserId: Int): retrofit2.Response<List<PostResponse>>

    @GET("api/posts/{trackId}/photos")
    suspend fun getPhotos(@Path("trackId") trackId: String): retrofit2.Response<List<PhotoResponse>>

    @GET("api/posts/{trackId}/photos/mixed")
    suspend fun getMixedPhotos(@Path("trackId") trackId: String): retrofit2.Response<List<PhotoResponse>>

    @POST("api/posts")
    suspend fun createPost(@Body r: PostRequest): retrofit2.Response<Unit>

    @DELETE("api/posts/photos/{photoId}")
    suspend fun deletePhoto(@Path("photoId") photoId: Int): retrofit2.Response<Unit>

    // Usuarios
    @POST("api/users/search")
    suspend fun searchUsers(@Body r: SearchUsersRequest): retrofit2.Response<List<UserResponse>>

    @GET("api/users/{id}")
    suspend fun getUserById(@Path("id") userId: Int): retrofit2.Response<UserResponse>

    @PUT("api/profile")
    suspend fun updateProfile(@Body r: UpdateProfileRequest): retrofit2.Response<UserResponse>

    @DELETE("api/account")
    suspend fun deleteAccount(@Body r: DeleteAccountRequest): retrofit2.Response<Unit>

    // Amigos
    @GET("api/friends/status/{targetId}")
    suspend fun getFriendStatus(@Path("targetId") targetId: Int): retrofit2.Response<FriendStatusResponse>

    @GET("api/friends/count")
    suspend fun getFriendCount(): retrofit2.Response<FriendCountResponse>

    @GET("api/friends")
    suspend fun getFriends(): retrofit2.Response<List<FriendshipResponse>>

    @DELETE("api/friends/{friendId}")
    suspend fun removeFriend(@Path("friendId") friendId: Int): retrofit2.Response<Unit>

    @POST("api/friend-requests/send")
    suspend fun sendFriendRequest(@Body r: FriendRequestDto): retrofit2.Response<Unit>

    @GET("api/friend-requests/pending")
    suspend fun getPendingRequests(): retrofit2.Response<List<FriendRequestResponse>>

    @POST("api/friend-requests/respond")
    suspend fun respondToFriendRequest(@Body r: RespondFriendRequest): retrofit2.Response<Unit>

    // Notificaciones
    @GET("api/notifications")
    suspend fun getNotifications(): retrofit2.Response<List<NotificationResponse>>

    @POST("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): retrofit2.Response<Unit>

    // Mapa
    @PUT("api/location")
    suspend fun updateLocation(@Body r: UpdateLocationRequest): retrofit2.Response<Unit>

    @GET("api/map/users")
    suspend fun getNearbyUsers(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 500.0
    ): retrofit2.Response<List<NearbyUserResponse>>

    @DELETE("api/location")
    suspend fun deleteLocation(): retrofit2.Response<Unit>

    // Recomendaciones
    @POST("api/recommendations")
    suspend fun sendRecommendation(@Body r: SendRecommendationRequest): retrofit2.Response<Unit>

    @GET("api/recommendations/mine")
    suspend fun getMyRecommendations(): retrofit2.Response<List<RecommendationResponse>>

    @GET("api/recommendations/user/{userId}")
    suspend fun getUserRecommendations(@Path("userId") userId: Int): retrofit2.Response<List<RecommendationResponse>>

    @POST("api/recommendations/listened")
    suspend fun markListened(@Query("trackId") trackId: String): retrofit2.Response<Unit>
}

// ── Cliente Retrofit ──────────────────────────────────────

object NewsickRetrofit {
    const val BASE_URL = "https://newsick.duckdns.org/"

    val api: NewsickApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${AuthManager.token}")
                    .addHeader("X-User-Id", AuthManager.userId.toString())
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsickApiService::class.java)
    }

    // Helper para construir URLs absolutas de fotos de perfil
    fun absoluteUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return if (path.startsWith("http")) path else BASE_URL.trimEnd('/') + path
    }
}
