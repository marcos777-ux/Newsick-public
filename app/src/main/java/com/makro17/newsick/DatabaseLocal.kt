package com.makro17.newsick

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ══════════════════════════════════════════════════════════
// 1. ENTIDADES
// ══════════════════════════════════════════════════════════

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "email")    val email: String,
    @ColumnInfo(name = "password") val password: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "bio")      val bio: String = "",
    @ColumnInfo(name = "token")    val token: String = ""
)

@Entity(tableName = "friend_collections")
data class FriendCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "song_title")   val songTitle: String,
    @ColumnInfo(name = "friend_names") val friendNames: String
)

@Entity(tableName = "song_posts")
data class SongPostEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")      val trackId: String,   // ← nombre explícito
    @ColumnInfo(name = "track_name")    val trackName: String,
    @ColumnInfo(name = "artist_name")   val artistName: String,
    @ColumnInfo(name = "artwork_url")   val artworkUrl: String,
    @ColumnInfo(name = "timestamp")     val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "post_photos")
data class PostPhotoEntity(
    @PrimaryKey(autoGenerate = true)   val id: Int = 0,
    @ColumnInfo(name = "track_id")     val trackId: String,
    @ColumnInfo(name = "photo_uri")    val photoUri: String,
    @ColumnInfo(name = "user_id")      val userId: Int,
    @ColumnInfo(name = "username")     val username: String,
    @ColumnInfo(name = "timestamp")    val timestamp: Long = System.currentTimeMillis()
)

// ══════════════════════════════════════════════════════════
// 2. DAOs
// ══════════════════════════════════════════════════════════

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email AND password = :password LIMIT 1")
    suspend fun login(email: String, password: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): UserEntity?

    // NUEVO: Búsqueda de usuarios por nombre
    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' LIMIT 10")
    suspend fun searchUsers(query: String): List<UserEntity>
}

@Dao
interface FriendCollectionDao {
    @Query("SELECT * FROM friend_collections ORDER BY id DESC")
    fun getAll(): Flow<List<FriendCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: FriendCollectionEntity): Long

    @Query("DELETE FROM friend_collections WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface SongPostDao {

    // ✅ Obtener todas las canciones activas (con al menos 1 foto)
    @Query("""
        SELECT DISTINCT sp.* FROM song_posts sp 
        INNER JOIN post_photos pp ON sp.track_id = pp.track_id 
        ORDER BY sp.timestamp DESC
    """)
    fun getActiveSongs(): Flow<List<SongPostEntity>>

    // ✅ Obtener canciones de un usuario específico
    @Query("""
        SELECT DISTINCT sp.* FROM song_posts sp 
        INNER JOIN post_photos pp ON sp.track_id = pp.track_id 
        WHERE pp.user_id = :userId 
        ORDER BY sp.timestamp DESC
    """)
    fun getActiveSongsByUser(userId: Int): Flow<List<SongPostEntity>>

    // ✅ Insertar canción (ignorar si ya existe)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(post: SongPostEntity): Long

    // ✅ Obtener canción por trackId
    @Query("SELECT * FROM song_posts WHERE track_id = :trackId LIMIT 1")
    suspend fun getByTrackId(trackId: String): SongPostEntity?

    // ✅ Eliminar canción por trackId
    @Query("DELETE FROM song_posts WHERE track_id = :trackId")
    suspend fun deleteByTrackId(trackId: String): Int

    // ✅ NUEVO: Borrar canciones sin fotos
    @Query("""
        DELETE FROM song_posts 
        WHERE track_id NOT IN (SELECT DISTINCT track_id FROM post_photos)
    """)
    suspend fun deleteEmptySongs(): Int
}

@Dao
interface PostPhotoDao {
    @Query("SELECT * FROM post_photos WHERE track_id = :trackId ORDER BY timestamp DESC")
    fun getPhotosForSong(trackId: String): Flow<List<PostPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PostPhotoEntity): Long

    @Query("DELETE FROM post_photos WHERE id = :id")
    suspend fun deleteById(id: Int)
}

// ══════════════════════════════════════════════════════════
// 3. BASE DE DATOS (versión 2)
// ══════════════════════════════════════════════════════════

@Database(
    entities = [
        UserEntity::class,
        FriendCollectionEntity::class,
        SongPostEntity::class,
        PostPhotoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NewsickDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun friendCollectionDao(): FriendCollectionDao
    abstract fun songPostDao(): SongPostDao
    abstract fun postPhotoDao(): PostPhotoDao

    companion object {
        @Volatile private var INSTANCE: NewsickDatabase? = null

        fun getDatabase(context: Context): NewsickDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    NewsickDatabase::class.java,
                    "newsick_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

// ══════════════════════════════════════════════════════════
// 4. REPOSITORIO
// ══════════════════════════════════════════════════════════

class NewsickRepository(private val db: NewsickDatabase) {

    suspend fun login(email: String, password: String) = db.userDao().login(email, password)

    suspend fun register(email: String, password: String, username: String): UserEntity? {
        if (db.userDao().findByEmail(email) != null) return null
        val id = db.userDao().insert(
            UserEntity(email = email, password = password, username = username)
        )
        return db.userDao().getById(id.toInt())
    }


    fun getCollections() = db.friendCollectionDao().getAll()

    suspend fun addCollection(songTitle: String, friends: List<String>) {
        db.friendCollectionDao().insert(
            FriendCollectionEntity(songTitle = songTitle, friendNames = friends.joinToString(","))
        )
    }

    suspend fun removeCollection(id: Int) = db.friendCollectionDao().deleteById(id)

    fun parseNames(raw: String) = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getActiveSongs() = db.songPostDao().getActiveSongs()

    fun getActiveSongsByUser(userId: Int) = db.songPostDao().getActiveSongsByUser(userId)

    fun getPhotosForSong(trackId: String) = db.postPhotoDao().getPhotosForSong(trackId)

    suspend fun getSongPost(trackId: String) = db.songPostDao().getByTrackId(trackId)

    suspend fun createPost(
        trackId: String,
        trackName: String,
        artistName: String,
        artworkUrl: String,
        photoUris: List<String>,
        userId: Int,
        username: String
    ) {
        db.songPostDao().insertOrIgnore(
            SongPostEntity(trackId = trackId, trackName = trackName, artistName = artistName, artworkUrl = artworkUrl)
        )
        photoUris.forEach { uri ->
            db.postPhotoDao().insert(
                PostPhotoEntity(trackId = trackId, photoUri = uri, userId = userId, username = username)
            )
        }
    }

    // NUEVO: Búsqueda de usuarios
    suspend fun searchUsers(query: String) = db.userDao().searchUsers(query)

    // ✅ Borrar canciones sin fotos
    suspend fun cleanupEmptySongs() {
        // Esta query borra song_posts que no tienen ninguna foto asociada
        db.songPostDao().deleteEmptySongs()
    }
}