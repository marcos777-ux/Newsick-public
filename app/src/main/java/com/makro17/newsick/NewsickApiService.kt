package com.makro17.newsick

import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ══════════════════════════════════════════════════════════
// AUTH MANAGER — token global para el interceptor
// ══════════════════════════════════════════════════════════

object AuthManager {
    var token: String = ""
    var userId: Int = 0
}

// ══════════════════════════════════════════════════════════
// MODELOS
// ══════════════════════════════════════════════════════════

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RegisterRequest(val email: String, val password: String, val username: String)

@Serializable data class UserResponse(
    val id: Int,
    val email: String,
    val username: String,
    val profilePhoto: String? = null,
    val bio: String? = null,
    val createdAt: String = ""
)

@Serializable data class AuthResponse(val user: UserResponse, val token: String)

@Serializable data class PostResponse(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val timestamp: Long,
    val photoCount: Int
)

@Serializable data class PhotoResponse(
    val id: Int,
    val trackId: String,
    val photoUri: String,
    val userId: Int,
    val username: String,
    val timestamp: Long
)

@Serializable data class PostRequest(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val timestamp: Long,
    val photos: List<PhotoRequest>
)

@Serializable data class PhotoRequest(
    val photoUri: String,
    val userId: Int,
    val username: String,
    val timestamp: Long
)

@Serializable data class SearchUsersRequest(val query: String)
@Serializable data class UpdateProfileRequest(val bio: String, val username: String = "", val profilePhoto: String = "")
@Serializable data class DeleteAccountRequest(val password: String)
@Serializable data class FriendRequestDto(val targetUserId: Int)
@Serializable data class RespondFriendRequest(val requestId: Int, val accept: Boolean)

@Serializable data class FriendRequestResponse(
    val id: Int,
    val senderId: Int,
    val senderUsername: String,
    val senderProfilePhoto: String = "",
    val status: String,
    val createdAt: String
)

@Serializable data class NotificationResponse(
    val id: Int,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String
)

@Serializable data class FriendStatusResponse(val status: String)

// ══════════════════════════════════════════════════════════
// INTERFAZ DE LA API
// ══════════════════════════════════════════════════════════

interface NewsickApiService {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): retrofit2.Response<AuthResponse>

    @GET("api/health")
    suspend fun healthCheck(): retrofit2.Response<String>

    // Posts
    @GET("api/posts")
    suspend fun getPosts(): retrofit2.Response<List<PostResponse>>

    @GET("api/posts/{trackId}/photos")
    suspend fun getPhotos(@Path("trackId") trackId: String): retrofit2.Response<List<PhotoResponse>>

    @POST("api/posts")
    suspend fun createPost(@Body request: PostRequest): retrofit2.Response<Unit>

    @GET("api/posts/user/{userId}")
    suspend fun getUserPosts(@Path("userId") userId: Int): retrofit2.Response<List<PostResponse>>

    // Usuarios
    @POST("api/users/search")
    suspend fun searchUsers(@Body request: SearchUsersRequest): retrofit2.Response<List<UserResponse>>

    @GET("api/users/{id}")
    suspend fun getUserById(@Path("id") userId: Int): retrofit2.Response<UserResponse>

    // Perfil
    @PUT("api/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): retrofit2.Response<UserResponse>

    @DELETE("api/account")
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): retrofit2.Response<Unit>

    // Amigos
    @GET("api/friends/status/{targetId}")
    suspend fun getFriendStatus(@Path("targetId") targetId: Int): retrofit2.Response<FriendStatusResponse>

    @POST("api/friend-requests/send")
    suspend fun sendFriendRequest(@Body request: FriendRequestDto): retrofit2.Response<Unit>

    @GET("api/friend-requests/pending")
    suspend fun getPendingRequests(): retrofit2.Response<List<FriendRequestResponse>>

    @POST("api/friend-requests/respond")
    suspend fun respondToFriendRequest(@Body request: RespondFriendRequest): retrofit2.Response<Unit>

    // Notificaciones
    @GET("api/notifications")
    suspend fun getNotifications(): retrofit2.Response<List<NotificationResponse>>

    @POST("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): retrofit2.Response<Unit>
}

// ══════════════════════════════════════════════════════════
// RETROFIT — con interceptor de auth
// ══════════════════════════════════════════════════════════

object NewsickRetrofit {
    private const val BASE_URL = "https://newsick.duckdns.org/"

    val api: NewsickApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${AuthManager.token}")
                    .addHeader("X-User-Id", AuthManager.userId.toString())
                    .build()
                chain.proceed(req)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsickApiService::class.java)
    }
}
