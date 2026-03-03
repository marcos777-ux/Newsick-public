package com.makro17.newsick

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============================================================
// 1. ENTIDADES (Tablas de la BD)
// ============================================================

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "password") val password: String,   // En prod: guardar hash, no texto plano
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "bio") val bio: String = "Sin bio todavía.",
    @ColumnInfo(name = "token") val token: String = ""
)

@Entity(tableName = "friend_collections")
data class FriendCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "song_title") val songTitle: String,
    // Los nombres se guardan como "Ana,Pedro,Tú" y se parsean al leer
    @ColumnInfo(name = "friend_names") val friendNames: String
)

// ============================================================
// 2. DAOs (Acceso a datos)
// ============================================================

@Dao
interface UserDao {

    // Devuelve null si no existe → Login fallido
    @Query("SELECT * FROM users WHERE email = :email AND password = :password LIMIT 1")
    suspend fun login(email: String, password: String): UserEntity?

    // Devuelve null si el email ya existe
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity): Int

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): UserEntity?
}

@Dao
interface FriendCollectionDao {

    @Query("SELECT * FROM friend_collections ORDER BY id DESC")
    fun getAll(): Flow<List<FriendCollectionEntity>>   // Flow → se actualiza automáticamente en la UI

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: FriendCollectionEntity): Long

    @Delete
    suspend fun delete(collection: FriendCollectionEntity)

    @Query("DELETE FROM friend_collections WHERE id = :id")
    suspend fun deleteById(id: Int)
}

// ============================================================
// 3. BASE DE DATOS ROOM
// ============================================================

@Database(
    entities = [UserEntity::class, FriendCollectionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NewsickDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun friendCollectionDao(): FriendCollectionDao

    companion object {
        @Volatile
        private var INSTANCE: NewsickDatabase? = null

        fun getDatabase(context: Context): NewsickDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NewsickDatabase::class.java,
                    "newsick_database"
                )
                    // Si cambias el esquema sube la versión y añade una Migration.
                    // Para desarrollo rápido puedes usar .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ============================================================
// 4. REPOSITORIO (Capa intermedia ViewModel ↔ BD)
// ============================================================

class NewsickRepository(private val db: NewsickDatabase) {

    // --- AUTH ---

    /** Devuelve el UserEntity si las credenciales son correctas, null si no. */
    suspend fun login(email: String, password: String): UserEntity? {
        return db.userDao().login(email, password)
    }

    /**
     * Registra un nuevo usuario.
     * @return UserEntity recién creado, o null si el email ya existe.
     */
    suspend fun register(email: String, password: String, username: String): UserEntity? {
        val existing = db.userDao().findByEmail(email)
        if (existing != null) return null   // Email duplicado

        val newUser = UserEntity(
            email = email,
            password = password,    // ⚠️ En producción: hash con BCrypt / Argon2
            username = username
        )
        val insertedId = db.userDao().insert(newUser)
        return db.userDao().getById(insertedId.toInt())
    }

    // --- COLECCIONES ---

    /** Flow que emite la lista actualizada cada vez que cambia la BD */
    fun getCollections(): Flow<List<FriendCollectionEntity>> =
        db.friendCollectionDao().getAll()

    suspend fun addCollection(songTitle: String, friends: List<String>) {
        db.friendCollectionDao().insert(
            FriendCollectionEntity(
                songTitle = songTitle,
                friendNames = friends.joinToString(",")
            )
        )
    }

    suspend fun removeCollection(id: Int) {
        db.friendCollectionDao().deleteById(id)
    }

    // Helper: convierte el String "Ana,Pedro" en List<String>
    fun parseNames(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
