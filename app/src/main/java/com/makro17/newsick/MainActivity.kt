// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════
// USERNAME VALIDATION
// ══════════════════════════════════════════════════════════

val USERNAME_REGEX = Regex("^[a-zA-Z0-9._]{3,30}$")

fun validateUsername(username: String): String? = when {
    username.length < 3              -> "Mínimo 3 caracteres"
    username.length > 30             -> "Máximo 30 caracteres"
    !USERNAME_REGEX.matches(username) -> "Solo letras, números, puntos y guiones bajos"
    else -> null
}

// ══════════════════════════════════════════════════════════
// PENDING UPLOAD TRACK (desde recomendación → publicar)
// ══════════════════════════════════════════════════════════

data class PendingUploadTrack(
    val trackId: String, val trackName: String, val artistName: String,
    val artworkUrl: String, val previewUrl: String?
)

// ══════════════════════════════════════════════════════════
// VIEW MODEL
// ══════════════════════════════════════════════════════════

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _mySongsRefresh = MutableStateFlow(0L)

    val mySongs: StateFlow<List<SongPostEntity>> =
        snapshotFlow { loggedUserId.value }
            .flatMapLatest { uid ->
                if (uid == 0) flowOf(emptyList())
                else repo.getActiveSongsByUser(uid)
            }
            .combine(_mySongsRefresh) { songs, _ -> songs }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    val api = NewsickRetrofit.api
    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("newsick_prefs", Context.MODE_PRIVATE)
    }
    // Preferencias de UI en fichero separado — NUNCA se borran al cerrar sesión
    private val settingsPrefs by lazy {
        getApplication<Application>().getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
    }
    val db   = NewsickDatabase.getDatabase(application)
    private val repo = NewsickRepository(db)

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    var isLoggedIn         = mutableStateOf(false)
    var authError          = mutableStateOf<String?>(null)
    var isRegistering      = mutableStateOf(false)
    var needsUsername      = mutableStateOf(false)
    // Auth flow: "login" | "register_email" | "register_verify" | "register_username" | "reset_send" | "reset_verify" | "reset_new"
    var authStep           = mutableStateOf("login")
    var authPendingEmail   = mutableStateOf("")
    var authPendingCode    = mutableStateOf("")
    var authPrefillId      = mutableStateOf("") // username/email pre-rellenado al cambiar cuenta
    var loggedUsername     = mutableStateOf("")
    var loggedBio          = mutableStateOf("")
    var loggedEmail        = mutableStateOf("")
    var loggedProfilePhoto = mutableStateOf("")
    var loggedUserId       = mutableStateOf(0)

    // Sesión guardada temporalmente al pulsar "Añadir cuenta" para poder cancelar y volver
    private data class SavedSession(
        val userId: Int, val username: String, val bio: String,
        val email: String, val profilePhoto: String, val token: String
    )
    private var savedSession: SavedSession? = null
    val canCancelAddAccount get() = savedSession != null

    // Ruta a la que volver si el usuario cancela "añadir cuenta"
    var pendingReturnRoute = mutableStateOf<String?>(null)

    var userSearchQuery    = mutableStateOf("")
    var searchResults      = mutableStateOf<List<UserResponse>>(emptyList())
    var isSearching        = mutableStateOf(false)

    var pendingRequests    = mutableStateOf<List<FriendRequestResponse>>(emptyList())
    var notifications      = mutableStateOf<List<NotificationResponse>>(emptyList())
    var unreadCount        = mutableStateOf(0)
    var friendCount        = mutableStateOf(0)
    var friendsList        = mutableStateOf<List<FriendshipResponse>>(emptyList())

    // Feed agrupado por canción — nuevo diseño social
    var feedPhotoGroups    = mutableStateOf<List<FeedGroup>>(emptyList())
    // Feed viejo (compatibilidad)
    var apiFeed            = mutableStateOf<List<PostResponse>>(emptyList())
    var mixedPhotosCache   = mutableStateOf<Map<String, List<PhotoResponse>>>(emptyMap())

    // Canciones de amigos rankeadas
    var friendsSongs       = mutableStateOf<List<FriendSongEntry>>(emptyList())

    // darkModeOverride se guarda en newsick_settings (aislado de la sesión).
    // Si el valor aún no está ahí pero existe en newsick_prefs (versión anterior),
    // se migra automáticamente la primera vez.
    private val prefsEager = getApplication<Application>().run {
        val settings = getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
        if (!settings.contains("dark_mode")) {
            val legacy = getSharedPreferences("newsick_prefs", Context.MODE_PRIVATE)
            if (legacy.contains("dark_mode")) {
                settings.edit().putInt("dark_mode", legacy.getInt("dark_mode", -1)).apply()
            }
        }
        settings
    }
    var darkModeOverride = mutableStateOf<Boolean?>(
        when (prefsEager.getInt("dark_mode", -1)) { 0 -> false; 1 -> true; else -> null }
    )

    // Pista preseleccionada para publicar desde recomendación
    var pendingUploadTrack = mutableStateOf<PendingUploadTrack?>(null)

    // Mapa
    var nearbyUsers        = mutableStateOf<List<NearbyUserResponse>>(emptyList())

    val feedSongs: StateFlow<List<SongPostEntity>> = repo.getActiveSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val shownNotifIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            // Leer dark mode síncronamente ANTES de cualquier recomposición
            val settingsPrefsEager = getApplication<Application>()
                .getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
            val legacyPrefsEager = getApplication<Application>()
                .getSharedPreferences("newsick_prefs", Context.MODE_PRIVATE)
            if (!settingsPrefsEager.contains("dark_mode") && legacyPrefsEager.contains("dark_mode")) {
                settingsPrefsEager.edit().putInt("dark_mode", legacyPrefsEager.getInt("dark_mode", -1)).apply()
            }
            darkModeOverride.value = when (settingsPrefsEager.getInt("dark_mode", -1)) {
                0 -> false; 1 -> true; else -> null
            }

            val saved = prefs.getInt("user_id", 0)
            if (saved > 0) {
                val token    = prefs.getString("token", "") ?: ""
                val username = prefs.getString("username", "") ?: ""
                val bio      = prefs.getString("bio", "") ?: ""
                val email    = prefs.getString("email", "") ?: ""
                val photo    = prefs.getString("profile_photo", "") ?: ""

                // Configurar AuthManager para poder hacer la llamada de validación
                AuthManager.token  = token
                AuthManager.userId = saved

                // Validar que el usuario aún existe en el servidor
                val valid = try {
                    val r = api.getUserById(saved)
                    r.isSuccessful
                } catch (_: Exception) {
                    true // Si no hay red, asumir válido para no desloguear offline
                }

                if (valid) {
                    loggedUserId.value       = saved
                    loggedUsername.value     = username
                    loggedBio.value          = bio
                    loggedEmail.value        = email
                    loggedProfilePhoto.value = photo
                    isLoggedIn.value         = true
                    // Asegurar que la sesión activa está en la lista de sesiones guardadas
                    val ids = getSavedSessionIds().toMutableSet(); ids.add(saved)
                    if (!prefs.contains("session_${saved}_token")) {
                        prefs.edit().apply {
                            putString("session_${saved}_token",    token)
                            putString("session_${saved}_username", username)
                            putString("session_${saved}_email",    email)
                            putString("session_${saved}_bio",      bio)
                            putString("session_${saved}_photo",    photo)
                            putString("session_ids", ids.joinToString(","))
                            apply()
                        }
                    }
                } else {
                    // Usuario eliminado del servidor — limpiar sesión
                    AuthManager.token = ""; AuthManager.userId = 0
                    val savedThemeVal = settingsPrefsEager.getInt("dark_mode", -1)
                    prefs.edit().clear().apply()
                    // Restaurar dark mode tras limpiar
                    settingsPrefsEager.edit().putInt("dark_mode", savedThemeVal).apply()
                }
            }
            delay(1000)
            _isLoading.value = false
        }
    }

    // ── Dark mode ─────────────────────────────────────────

    fun setDarkMode(value: Boolean?) {
        darkModeOverride.value = value
        settingsPrefs.edit().putInt("dark_mode", when (value) { false -> 0; true -> 1; else -> -1 }).apply()
    }

    // ── AÑADIR CUENTA ─────────────────────────────────────

    /** Guarda la sesión actual y va al login sin borrar el dark mode ni los datos. */
    fun startAddAccount(returnRoute: String = "settings") {
        val currentDark = darkModeOverride.value
        savedSession = SavedSession(
            userId       = loggedUserId.value,
            username     = loggedUsername.value,
            bio          = loggedBio.value,
            email        = loggedEmail.value,
            profilePhoto = loggedProfilePhoto.value,
            token        = AuthManager.token
        )
        pendingReturnRoute.value = returnRoute
        logout()
        darkModeOverride.value = currentDark
    }

    /** Restaura la sesión anterior si el usuario cancela "Añadir cuenta". */
    fun cancelAddAccount() {
        val s = savedSession ?: return
        savedSession = null
        saveSession(AuthResponse(
            user  = UserResponse(id = s.userId, email = s.email, username = s.username,
                bio = s.bio, profilePhoto = s.profilePhoto),
            token = s.token
        ))
        authError.value = null
    }

    // ── AUTH ──────────────────────────────────────────────

    fun performLogin(identifier: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.login(LoginRequest(identifier, pass))
                if (r.isSuccessful) saveSession(r.body()!!)
                else authError.value = "Credenciales incorrectas"
            } catch (e: Exception) { authError.value = "Error de conexión: ${e.message}" }
            _isLoading.value = false
        }
    }

    suspend fun performLoginAsync(identifier: String, pass: String, onResult: (Boolean) -> Unit) {
        _isLoading.value = true; authError.value = null
        try {
            val r = api.login(LoginRequest(identifier, pass))
            if (r.isSuccessful) { saveSession(r.body()!!); onResult(true) }
            else { authError.value = "Credenciales incorrectas"; onResult(false) }
        } catch (e: Exception) { authError.value = "Error de conexión: ${e.message}"; onResult(false) }
        _isLoading.value = false
    }

    fun prepareRegister(email: String, pass: String) {
        pendingEmail = email; pendingPassword = pass; needsUsername.value = true
    }
    var pendingEmail    = ""
    var pendingPassword = ""

    fun performRegister(email: String, pass: String, username: String) {
        val err = validateUsername(username)
        if (err != null) { authError.value = err; return }
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.register(RegisterRequest(email, pass, username))
                if (r.isSuccessful) saveSession(r.body()!!)
                else authError.value = when (r.code()) {
                    409  -> "Nombre de usuario ya en uso"
                    400  -> "Nombre de usuario inválido"
                    else -> "Error al registrarse"
                }
            } catch (e: Exception) { authError.value = "Error de conexión: ${e.message}" }
            _isLoading.value = false
        }
    }

    private fun saveSession(auth: AuthResponse) {
        savedSession             = null
        loggedUserId.value       = auth.user.id
        loggedUsername.value     = auth.user.username
        loggedBio.value          = auth.user.bio ?: ""
        loggedEmail.value        = auth.user.email
        loggedProfilePhoto.value = auth.user.profilePhoto ?: ""
        AuthManager.token        = auth.token
        AuthManager.userId       = auth.user.id
        isLoggedIn.value         = true
        val uid = auth.user.id
        prefs.edit().apply {
            // Sesión activa
            putInt("user_id", uid)
            putString("username", auth.user.username)
            putString("bio", auth.user.bio ?: "")
            putString("email", auth.user.email)
            putString("token", auth.token)
            putString("profile_photo", auth.user.profilePhoto ?: "")
            // Sesión individual por userId (para cambio sin contraseña)
            putString("session_${uid}_token",   auth.token)
            putString("session_${uid}_username", auth.user.username)
            putString("session_${uid}_email",    auth.user.email)
            putString("session_${uid}_bio",      auth.user.bio ?: "")
            putString("session_${uid}_photo",    auth.user.profilePhoto ?: "")
            // Añadir a lista de sesiones abiertas
            val ids = getSavedSessionIds().toMutableSet(); ids.add(uid)
            putString("session_ids", ids.joinToString(","))
            apply()
        }
    }

    /** IDs de usuarios con sesión guardada en este dispositivo. */
    private fun getSavedSessionIds(): Set<Int> {
        val raw = prefs.getString("session_ids", "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    /** Devuelve la primera sesión guardada diferente al usuario actual, o null. */
    private fun findOtherSavedSession(excludeId: Int): AuthResponse? {
        for (uid in getSavedSessionIds()) {
            if (uid == excludeId) continue
            val token    = prefs.getString("session_${uid}_token", null) ?: continue
            val username = prefs.getString("session_${uid}_username", "") ?: ""
            val email    = prefs.getString("session_${uid}_email", "") ?: ""
            val bio      = prefs.getString("session_${uid}_bio", "") ?: ""
            val photo    = prefs.getString("session_${uid}_photo", "") ?: ""
            return AuthResponse(
                user  = UserResponse(id = uid, email = email, username = username,
                    bio = bio, profilePhoto = photo),
                token = token
            )
        }
        return null
    }

    /** True si existe un token guardado para este userId. */
    fun hasSavedSession(userId: Int) = prefs.getString("session_${userId}_token", null) != null

    /** Devuelve todas las sesiones guardadas en este dispositivo (incluyendo la activa). */
    fun getAllSavedSessions(): List<UserResponse> {
        return getSavedSessionIds().mapNotNull { uid ->
            val token    = prefs.getString("session_${uid}_token", null) ?: return@mapNotNull null
            val username = prefs.getString("session_${uid}_username", "") ?: ""
            val email    = prefs.getString("session_${uid}_email", "") ?: ""
            val bio      = prefs.getString("session_${uid}_bio", "") ?: ""
            val photo    = prefs.getString("session_${uid}_photo", "") ?: ""
            UserResponse(id = uid, email = email, username = username, bio = bio, profilePhoto = photo)
        }
    }

    fun logout(prefillId: String = "") {
        val currentId = loggedUserId.value

        // Eliminar sesión del usuario actual de la lista
        val ids = getSavedSessionIds().toMutableSet(); ids.remove(currentId)
        prefs.edit().apply {
            remove("session_ids")
            if (ids.isNotEmpty()) putString("session_ids", ids.joinToString(","))
            // Borrar token guardado del usuario actual (fuerza contraseña al volver)
            remove("session_${currentId}_token")
            remove("session_${currentId}_username")
            remove("session_${currentId}_email")
            remove("session_${currentId}_bio")
            remove("session_${currentId}_photo")
            // Borrar sesión activa (sin tocar dark_mode ni otros ajustes)
            remove("user_id"); remove("username"); remove("bio")
            remove("email"); remove("token"); remove("profile_photo")
            apply()
        }

        isLoggedIn.value = false; isRegistering.value = false; needsUsername.value = false
        authStep.value = "login"; authPendingEmail.value = ""; authPendingCode.value = ""
        if (prefillId.isNotBlank()) authPrefillId.value = prefillId
        loggedUserId.value = 0; loggedUsername.value = ""; loggedBio.value = ""
        loggedEmail.value = ""; loggedProfilePhoto.value = ""
        AuthManager.token = ""; AuthManager.userId = 0
        // NO tocar darkModeOverride: el usuario no cambió su preferencia de tema
        feedPhotoGroups.value = emptyList(); apiFeed.value = emptyList()
        mixedPhotosCache.value = emptyMap(); friendsSongs.value = emptyList()
        shownNotifIds.clear()
    }

    /** Cierra sesión intentando cambiar a otra cuenta abierta. Si no hay, va al login. */
    fun logoutSmart() {
        val other = findOtherSavedSession(loggedUserId.value)
        logout()              // elimina las claves del usuario actual de prefs/session_ids
        if (other != null) saveSession(other)   // restaura la otra sesión
    }

    /** Cambia a otra cuenta: sin contraseña si hay sesión guardada, con contraseña si no. */
    fun switchAccountIfPossible(
        target: UserResponse,
        onNeedPassword: () -> Unit
    ) {
        if (hasSavedSession(target.id)) {
            // Restaurar sesión guardada directamente
            val token    = prefs.getString("session_${target.id}_token", "") ?: ""
            val username = prefs.getString("session_${target.id}_username", target.username) ?: target.username
            val email    = prefs.getString("session_${target.id}_email", target.email) ?: target.email
            val bio      = prefs.getString("session_${target.id}_bio", "") ?: ""
            val photo    = prefs.getString("session_${target.id}_photo", "") ?: ""
            saveSession(AuthResponse(
                user  = UserResponse(id = target.id, email = email, username = username,
                    bio = bio, profilePhoto = photo),
                token = token
            ))
        } else {
            onNeedPassword()
        }
    }

    // ── NEW AUTH FLOWS ────────────────────────────────────

    fun sendVerificationCode(email: String, purpose: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.sendVerificationCode(SendCodeRequest(email, purpose))
                if (r.isSuccessful) {
                    // Si el servidor no tiene SMTP configurado devuelve devCode para desarrollo
                    val devCode = r.body()?.get("devCode")?.toString()
                    onResult(true, devCode)
                } else {
                    authError.value = "No se pudo enviar el código. Inténtalo de nuevo."
                    onResult(false, null)
                }
            } catch (e: Exception) { authError.value = "Error de conexión: ${e.message}"; onResult(false, null) }
            _isLoading.value = false
        }
    }

    fun verifyCode(email: String, code: String, purpose: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.verifyCode(VerifyCodeRequest(email, code, purpose))
                if (r.isSuccessful) onResult(true)
                else { authError.value = "Código incorrecto o caducado"; onResult(false) }
            } catch (e: Exception) { authError.value = "Error de conexión"; onResult(false) }
            _isLoading.value = false
        }
    }

    fun changePassword(email: String, code: String, newPassword: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.changePassword(ChangePasswordRequest(email, code, newPassword))
                onResult(r.isSuccessful)
            } catch (e: Exception) { authError.value = "Error de conexión"; onResult(false) }
            _isLoading.value = false
        }
    }

    suspend fun getAccountsByEmail(email: String): List<UserResponse> = try {
        val r = api.getUsersByEmail(email); if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
    } catch (_: Exception) { emptyList() }

    fun loginAs(auth: AuthResponse) = saveSession(auth)

    fun switchAccount(targetUserId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = api.switchAccount(SwitchAccountRequest(targetUserId))
                if (r.isSuccessful) { saveSession(r.body()!!); onResult(true) }
                else onResult(false)
            } catch (e: Exception) { onResult(false) }
        }
    }

    // ── PERFIL ────────────────────────────────────────────

    fun updateProfile(newUsername: String, newBio: String, newPhoto: String, onResult: (Boolean, String?) -> Unit) {
        val err = validateUsername(newUsername)
        if (err != null) { onResult(false, err); return }
        viewModelScope.launch {
            try {
                val r = api.updateProfile(UpdateProfileRequest(bio = newBio, username = newUsername, profilePhoto = newPhoto))
                if (r.isSuccessful) {
                    val u = r.body()!!
                    loggedUsername.value = u.username; loggedBio.value = u.bio ?: ""
                    loggedProfilePhoto.value = u.profilePhoto ?: ""
                    prefs.edit().apply {
                        putString("username", u.username); putString("bio", u.bio ?: "")
                        putString("profile_photo", u.profilePhoto ?: ""); apply()
                    }
                    onResult(true, null)
                } else {
                    onResult(false, when (r.code()) { 409 -> "Nombre de usuario ya en uso"; 400 -> "Nombre inválido"; else -> "Error al actualizar" })
                }
            } catch (e: Exception) { e.printStackTrace(); onResult(false, "Error de conexión") }
        }
    }

    fun deleteAccount(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try { val r = api.deleteAccount(DeleteAccountRequest(password)); if (r.isSuccessful) { logout(); onResult(true) } else onResult(false) }
            catch (e: Exception) { onResult(false) }
        }
    }

    // ── BÚSQUEDA ──────────────────────────────────────────

    fun searchUsers(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) { searchResults.value = emptyList(); isSearching.value = false; return }
        searchJob = viewModelScope.launch {
            delay(300); isSearching.value = true
            try {
                val r = api.searchUsers(SearchUsersRequest(query))
                searchResults.value = if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
            } catch (_: Exception) { searchResults.value = emptyList() }
            isSearching.value = false
        }
    }

    suspend fun getUserById(userId: Int): UserResponse? = try {
        val r = api.getUserById(userId); if (r.isSuccessful) r.body() else null
    } catch (_: Exception) { null }

    suspend fun getFriendStatus(targetId: Int): String = try {
        val r = api.getFriendStatus(targetId); if (r.isSuccessful) r.body()?.status ?: "none" else "none"
    } catch (_: Exception) { "none" }

    // ── AMIGOS ────────────────────────────────────────────

    fun loadFriendCount() {
        viewModelScope.launch {
            try { val r = api.getFriendCount(); if (r.isSuccessful) friendCount.value = r.body()?.count ?: 0 }
            catch (_: Exception) {}
        }
    }

    fun loadFriendsList() {
        viewModelScope.launch {
            try { val r = api.getFriends(); if (r.isSuccessful) friendsList.value = r.body() ?: emptyList() }
            catch (_: Exception) {}
        }
    }

    fun loadFriendsSongs() {
        viewModelScope.launch {
            try { val r = api.getFriendsSongs(); if (r.isSuccessful) friendsSongs.value = r.body() ?: emptyList() }
            catch (_: Exception) {}
        }
    }

    fun sendFriendRequest(targetUserId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = api.sendFriendRequest(FriendRequestDto(targetUserId))
                onResult(r.isSuccessful || r.code() == 409)
            } catch (_: Exception) { onResult(false) }
        }
    }

    fun respondToFriendRequest(requestId: Int, accept: Boolean) {
        viewModelScope.launch {
            try { api.respondToFriendRequest(RespondFriendRequest(requestId, accept)); loadNotificationsData(); loadFriendCount() }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun removeFriend(friendId: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val r = api.removeFriend(friendId)
                if (r.isSuccessful) {
                    friendsList.value = friendsList.value.filter { it.friendId != friendId }
                    friendCount.value = (friendCount.value - 1).coerceAtLeast(0)
                    invalidateMixedCache(); loadFeedPhotos()
                    onResult(true)
                } else onResult(false)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }

    // ── NOTIFICACIONES ────────────────────────────────────

    fun loadNotificationsData(context: Context? = null) {
        viewModelScope.launch {
            try {
                val rq = api.getPendingRequests()
                if (rq.isSuccessful) pendingRequests.value = rq.body() ?: emptyList()
                val rn = api.getNotifications()
                if (rn.isSuccessful) {
                    val list = rn.body() ?: emptyList()
                    notifications.value = list
                    unreadCount.value   = list.count { !it.isRead }
                    if (context != null) {
                        list.filter { !it.isRead && !shownNotifIds.contains(it.id) }
                            .forEach { notif ->
                                NotificationHelper.show(context, notif.id, notif.title, notif.message)
                                shownNotifIds.add(notif.id)
                            }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun markNotificationRead(id: Int) {
        viewModelScope.launch {
            try {
                api.markNotificationRead(id)
                notifications.value = notifications.value.map { if (it.id == id) it.copy(isRead = true) else it }
                unreadCount.value   = notifications.value.count { !it.isRead }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── FEED ──────────────────────────────────────────────

    /** Carga fotos individuales del feed y las agrupa por canción */
    fun loadFeedPhotos() {
        viewModelScope.launch {
            try {
                val r = api.getFeedPhotos()
                if (r.isSuccessful) {
                    val photos = r.body() ?: emptyList()
                    // Agrupar por trackId manteniendo orden de primera aparición (más reciente)
                    val seen   = linkedMapOf<String, MutableList<FeedPhotoItem>>()
                    photos.forEach { photo ->
                        seen.getOrPut(photo.trackId) { mutableListOf() }.add(photo)
                    }
                    feedPhotoGroups.value = seen.values.map { list ->
                        val first = list.first()
                        FeedGroup(
                            trackId    = first.trackId,
                            trackName  = first.trackName,
                            artistName = first.artistName,
                            artworkUrl = first.artworkUrl,
                            photos     = list
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /** Carga feed agrupado antiguo (para compatibilidad) */
    fun loadFeed() {
        viewModelScope.launch {
            try { val r = api.getFeed(); if (r.isSuccessful) apiFeed.value = r.body() ?: emptyList() }
            catch (_: Exception) {}
        }
    }

    suspend fun getMixedPhotos(trackId: String): List<PhotoResponse> {
        mixedPhotosCache.value[trackId]?.let { return it }
        return try {
            val r      = api.getMixedPhotos(trackId)
            val photos = if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
            mixedPhotosCache.value = mixedPhotosCache.value + (trackId to photos)
            photos
        } catch (_: Exception) { emptyList() }
    }

    fun invalidateMixedCache(trackId: String? = null) {
        mixedPhotosCache.value = if (trackId != null) mixedPhotosCache.value - trackId else emptyMap()
    }

    suspend fun getCommonSongs(targetUserId: Int): List<PostResponse> = try {
        val r = api.getCommonSongs(targetUserId); if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
    } catch (_: Exception) { emptyList() }

    // ── POSTS ─────────────────────────────────────────────

    suspend fun createPostAsync(
        trackId: String, trackName: String, artistName: String,
        artworkUrl: String, photoUris: List<String>
    ): Boolean {
        return try {
            val request = PostRequest(
                trackId = trackId, trackName = trackName, artistName = artistName,
                artworkUrl = artworkUrl, timestamp = System.currentTimeMillis(),
                photos = photoUris.map { uri ->
                    PhotoRequest(uri, loggedUserId.value, loggedUsername.value, System.currentTimeMillis())
                }
            )
            val r = api.createPost(request)
            if (r.isSuccessful) {
                repo.createPost(trackId, trackName, artistName, artworkUrl, photoUris,
                    loggedUserId.value, loggedUsername.value)
                invalidateMixedCache(trackId); loadFeedPhotos()
            }
            r.isSuccessful
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun createPost(trackId: String, trackName: String, artistName: String, artworkUrl: String, photoUris: List<String>) {
        viewModelScope.launch { createPostAsync(trackId, trackName, artistName, artworkUrl, photoUris) }
    }

    fun deletePhoto(photoId: Int, trackId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = api.deletePhoto(photoId)
                if (r.isSuccessful) {
                    db.postPhotoDao().deleteByTrackAndUser(trackId, loggedUserId.value)
                    db.songPostDao().deleteEmptySongs()
                    invalidateMixedCache(trackId); loadFeedPhotos(); invalidateMySongsCache()
                    onResult(true)
                } else onResult(false)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }

    private fun invalidateMySongsCache() {
        viewModelScope.launch { delay(300); _mySongsRefresh.value = System.currentTimeMillis() }
    }

    // ── MAPA ──────────────────────────────────────────────

    fun updateLocationOnMap(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                val song = mySongs.value.firstOrNull()
                api.updateLocation(UpdateLocationRequest(
                    latitude   = lat, longitude  = lng,
                    trackId    = song?.trackId,  trackName  = song?.trackName,
                    artistName = song?.artistName, artworkUrl = song?.artworkUrl
                ))
            } catch (_: Exception) {}
        }
    }

    fun updateLocationWithPlatform(lat: Double, lng: Double, platform: String) {
        viewModelScope.launch {
            try {
                val song = mySongs.value.firstOrNull()
                api.updateLocation(UpdateLocationRequest(
                    latitude   = lat, longitude  = lng,
                    trackId    = song?.trackId,  trackName  = song?.trackName,
                    artistName = song?.artistName, artworkUrl = song?.artworkUrl,
                    platform   = platform
                ))
            } catch (_: Exception) {}
        }
    }

    fun loadNearbyUsers(lat: Double, lng: Double, radius: Double = 500.0) {
        viewModelScope.launch {
            try {
                val r = api.getNearbyUsers(lat, lng, radius)
                if (r.isSuccessful) nearbyUsers.value = r.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun removeLocationFromMap() {
        viewModelScope.launch { try { api.deleteLocation() } catch (_: Exception) {} }
    }

    fun getPhotosForSong(trackId: String) = repo.getPhotosForSong(trackId)
    suspend fun getSongPost(trackId: String) = repo.getSongPost(trackId)
}

// ══════════════════════════════════════════════════════════
// MAIN ACTIVITY
// ══════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }
        NotificationHelper.createChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        enableEdgeToEdge()
        // Leer dark mode desde newsick_settings con migración desde newsick_prefs si hace falta.
        run {
            val settings = getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
            if (!settings.contains("dark_mode")) {
                val legacy = getSharedPreferences("newsick_prefs", Context.MODE_PRIVATE)
                if (legacy.contains("dark_mode")) {
                    settings.edit().putInt("dark_mode", legacy.getInt("dark_mode", -1)).apply()
                }
            }
            viewModel.darkModeOverride.value = when (settings.getInt("dark_mode", -1)) {
                0    -> false
                1    -> true
                else -> null
            }
        }
        setContent {
            val systemDark   = isSystemInDarkTheme()
            val darkOverride by viewModel.darkModeOverride
            // Leer síncronamente de SharedPreferences como fallback garantizado:
            // evita cualquier race condition si el ViewModel todavía no actualizó su estado
            val savedDarkMode = remember {
                when (getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
                    .getInt("dark_mode", -1)) { 0 -> false; 1 -> true; else -> null }
            }
            val isDark = darkOverride ?: savedDarkMode ?: systemDark
            var updateUrl         by remember { mutableStateOf<String?>(null) }
            var latestVersionName by remember { mutableStateOf<String?>(null) }
            var isIncompatible    by remember { mutableStateOf(false) }
            val context = LocalContext.current

            // Deep link desde notificación de chat
            var pendingChatNav by remember {
                val i = intent
                val target = i?.getStringExtra(NotificationHelper.EXTRA_NAV_TARGET)
                mutableStateOf(
                    if (target == "chat") Triple(
                        i.getIntExtra(NotificationHelper.EXTRA_CONVERSATION_ID, -1),
                        i.getIntExtra(NotificationHelper.EXTRA_OTHER_USER_ID, 0),
                        i.getStringExtra(NotificationHelper.EXTRA_OTHER_USERNAME) to
                                i.getStringExtra(NotificationHelper.EXTRA_OTHER_PHOTO)
                    ) else null
                )
            }

            LaunchedEffect(Unit) {
                try {
                    val res = NewsickRetrofit.api.getLatestVersion()
                    if (res.isSuccessful) {
                        val body = res.body() ?: return@LaunchedEffect
                        val myVersionCode = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                        } catch (_: Exception) { 0 }
                        // Versión incompatible: bloquear uso
                        if (myVersionCode < body.minVersionCode) {
                            isIncompatible    = true
                            latestVersionName = body.latestVersionName
                            updateUrl         = body.channelUrl
                            return@LaunchedEffect
                        }
                        // Versión desactualizada: mostrar aviso UNA SOLA VEZ por versión
                        if (myVersionCode < body.latestVersionCode) {
                            val shownKey     = "update_shown_${body.latestVersionCode}"
                            val alreadyShown = getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
                                .getBoolean(shownKey, false)
                            if (!alreadyShown) {
                                latestVersionName = body.latestVersionName
                                updateUrl         = body.channelUrl
                                getSharedPreferences("newsick_settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean(shownKey, true).apply()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Diálogo de versión incompatible (bloquea el uso, no se puede cerrar)
            if (isIncompatible) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Actualización obligatoria") },
                    text  = { Text("Esta versión de Newsick ya no es compatible. Descarga la versión ${latestVersionName ?: "más reciente"} desde el canal oficial para seguir usando la app.") },
                    confirmButton = {
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                        }) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ir al canal de actualizaciones")
                        }
                    },
                    dismissButton = {}
                )
            }

            // Diálogo de actualización disponible (informativo, se muestra una sola vez)
            if (!isIncompatible && updateUrl != null) {
                AlertDialog(
                    onDismissRequest = { updateUrl = null },
                    title = { Text("Nueva versión disponible") },
                    text  = { Text("Hay una nueva versión de Newsick disponible (${latestVersionName ?: ""}). Descárgala desde el canal oficial de WhatsApp.") },
                    confirmButton = {
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                            updateUrl = null
                        }) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ir al canal")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateUrl = null }) { Text("Ahora no") }
                    }
                )
            }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                val windowSize = calculateWindowSizeClass(this)
                if (viewModel.isLoggedIn.value) NewsickApp(
                    windowSize     = windowSize.widthSizeClass,
                    viewModel      = viewModel,
                    pendingChatNav = pendingChatNav,
                    onNavConsumed  = { pendingChatNav = null }
                )
                else AuthScreen(
                    viewModel = viewModel,
                    onBack    = if (viewModel.canCancelAddAccount) ({ viewModel.cancelAddAccount() }) else null
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// NAVEGACIÓN
// ══════════════════════════════════════════════════════════

@Composable
fun NewsickApp(
    windowSize: WindowWidthSizeClass,
    viewModel: MainViewModel,
    pendingChatNav: Triple<Int, Int, Pair<String?, String?>>? = null,
    onNavConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentRoute  = navController.currentBackStackEntryAsState().value?.destination?.route
    val context       = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadNotificationsData(context)
        viewModel.loadFriendCount()
        viewModel.loadFeedPhotos()
    }

    // Si el usuario volvió atrás tras "añadir cuenta", navegar al sitio de donde vino
    val returnRoute by viewModel.pendingReturnRoute
    LaunchedEffect(returnRoute) {
        val route = returnRoute ?: return@LaunchedEffect
        viewModel.pendingReturnRoute.value = null
        navController.navigate(route) { launchSingleTop = true }
    }

    // Navegar al chat si se abrió desde una notificación
    LaunchedEffect(pendingChatNav) {
        val nav = pendingChatNav ?: return@LaunchedEffect
        val (convId, otherUid, userInfo) = nav
        if (convId > 0) {
            val username = userInfo.first ?: ""
            val photo    = userInfo.second ?: ""
            navController.navigate(
                "chat/$convId/$otherUid?username=${android.net.Uri.encode(username)}&photo=${android.net.Uri.encode(photo)}"
            ) { launchSingleTop = true }
            onNavConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Social") },
                    selected = currentRoute == "social",
                    onClick = {
                        if (currentRoute == "social") { viewModel.loadNotificationsData(context); viewModel.loadFeedPhotos() }
                        else navController.navigate("social") { launchSingleTop = true; popUpTo("social") }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, null) }, label = { Text("Mapa") },
                    selected = currentRoute == "map",
                    onClick = { navController.navigate("map") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Perfil") },
                    selected = currentRoute == "profile",
                    onClick = { navController.navigate("profile") { launchSingleTop = true; popUpTo("social") } }
                )
            }
        }
    ) { pad ->
        val navigateToUser: (Int) -> Unit = { uid ->
            if (uid == viewModel.loggedUserId.value)
                navController.navigate("profile") { launchSingleTop = true }
            else
                navController.navigate("userProfile/$uid")
        }
        NavHost(navController, "social", Modifier.padding(pad)) {
            composable("social") {
                SocialFeedScreen(viewModel,
                    onSongClick          = { navController.navigate("detail/$it") },
                    onUploadClick        = { navController.navigate("upload") },
                    onNotificationsClick = { navController.navigate("notifications") },
                    onUserClick          = navigateToUser
                )
            }
            composable("map") {
                MapScreen(viewModel, onUserClick = navigateToUser)
            }
            composable("profile") {
                ProfileScreen(viewModel,
                    onSettingsClick = { navController.navigate("settings") },
                    onSongClick     = { navController.navigate("detail/$it") },
                    onFriendsClick  = { navController.navigate("friends") },
                    onUserClick     = navigateToUser,
                    onUploadClick   = { navController.navigate("upload") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel    = viewModel,
                    onBack       = { navController.popBackStack() },
                    onLogout     = { viewModel.logoutSmart() },
                    onAddAccount = { viewModel.startAddAccount() }
                )
            }
            composable("friends") {
                FriendsPagerScreen(
                    viewModel   = viewModel,
                    onBack      = { navController.popBackStack() },
                    onUserClick = navigateToUser,
                    onSongClick = { navController.navigate("detail/$it") },
                    onChatClick = { convId, username, photo, otherUid ->
                        navController.navigate("chat/$convId/$otherUid?username=${Uri.encode(username)}&photo=${Uri.encode(photo)}")
                    }
                )
            }
            composable("upload") {
                PostUploadScreen(viewModel,
                    onPostCreated = { navController.popBackStack() },
                    onBack        = { navController.popBackStack() }
                )
            }
            composable("detail/{trackId}") { back ->
                val tid = back.arguments?.getString("trackId") ?: return@composable
                SongDetailScreen(tid, viewModel) { navController.popBackStack() }
            }
            composable("userProfile/{userId}") { back ->
                val uid = back.arguments?.getString("userId")?.toIntOrNull() ?: return@composable
                UserProfileScreen(uid, viewModel,
                    onBack      = { navController.popBackStack() },
                    onSongClick = { navController.navigate("detail/$it") },
                    onChatClick = { convId, username, photo, otherUid ->
                        navController.navigate("chat/$convId/$otherUid?username=${Uri.encode(username)}&photo=${Uri.encode(photo)}")
                    }
                )
            }
            composable("notifications") {
                NotificationsScreen(
                    viewModel        = viewModel,
                    onBack           = { navController.popBackStack() },
                    onUserClick      = navigateToUser,
                    onRequestsClick  = { navController.navigate("friend_requests") },
                    onChatClick      = { convId ->
                        // Navegar al chat con el ID de conversación; username/photo se cargan desde API
                        navController.navigate("chat/$convId/0?username=&photo=") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("friend_requests") {
                FriendRequestsScreen(
                    viewModel   = viewModel,
                    onBack      = { navController.popBackStack() },
                    onUserClick = navigateToUser
                )
            }
            composable(
                "chat/{conversationId}/{otherUserId}?username={username}&photo={photo}",
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.IntType },
                    navArgument("otherUserId")    { type = NavType.IntType },
                    navArgument("username") { defaultValue = "" },
                    navArgument("photo")    { defaultValue = "" }
                )
            ) { back ->
                val convId      = back.arguments?.getInt("conversationId") ?: return@composable
                val otherUid    = back.arguments?.getInt("otherUserId") ?: 0
                val username    = back.arguments?.getString("username") ?: ""
                val photo       = back.arguments?.getString("photo") ?: ""
                ChatScreen(
                    conversationId    = convId,
                    otherUserId       = otherUid,
                    otherUsername     = username,
                    otherProfilePhoto = photo,
                    viewModel         = viewModel,
                    onBack            = { navController.popBackStack() },
                    onUserClick       = navigateToUser
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// AUTH SCREEN — multi-paso con verificación por email
// ══════════════════════════════════════════════════════════

@Composable
fun AuthScreen(viewModel: MainViewModel, onBack: (() -> Unit)? = null) {
    val step      = viewModel.authStep.value
    val isLoading = viewModel.isLoading.collectAsState().value
    val authError = viewModel.authError.value

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                // ── Inicio de sesión ──────────────────────────
                "login" -> AuthLoginStep(viewModel, isLoading, authError, onBack = onBack)

                // ── Registro: introducir email + contraseña ───
                "register_email" -> AuthRegisterEmailStep(viewModel, isLoading, authError)

                // ── Registro: verificar código ────────────────
                "register_verify" -> AuthVerifyStep(
                    viewModel   = viewModel,
                    isLoading   = isLoading,
                    authError   = authError,
                    title       = "Verifica tu correo",
                    subtitle    = "Introduce el código de 6 dígitos que hemos enviado a ${viewModel.authPendingEmail.value}",
                    purpose     = "register",
                    onVerified  = { viewModel.authStep.value = "register_username" },
                    onBack      = { viewModel.authStep.value = "register_email"; viewModel.authError.value = null }
                )

                // ── Registro: elegir nombre de usuario ────────
                "register_username" -> AuthUsernameStep(viewModel, isLoading, authError)

                // ── Reset: enviar código ──────────────────────
                "reset_send" -> AuthResetSendStep(viewModel, isLoading, authError)

                // ── Reset: verificar código ───────────────────
                "reset_verify" -> AuthVerifyStep(
                    viewModel   = viewModel,
                    isLoading   = isLoading,
                    authError   = authError,
                    title       = "Verifica tu identidad",
                    subtitle    = "Introduce el código enviado a ${viewModel.authPendingEmail.value}",
                    purpose     = "reset",
                    onVerified  = { viewModel.authStep.value = "reset_new" },
                    onBack      = { viewModel.authStep.value = "reset_send"; viewModel.authError.value = null }
                )

                // ── Reset: nueva contraseña ───────────────────
                "reset_new" -> AuthNewPasswordStep(viewModel, isLoading, authError)
            }
        }
    }
}

@Composable
private fun AuthHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
}

// ── Paso: Login ───────────────────────────────────────────

@Composable
private fun AuthLoginStep(
    viewModel: MainViewModel,
    isLoading: Boolean,
    authError: String?,
    onBack: (() -> Unit)? = null
) {
    var identifier by remember { mutableStateOf(viewModel.authPrefillId.value) }
    var password   by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    // Limpiar prefill tras leerlo
    LaunchedEffect(Unit) { viewModel.authPrefillId.value = "" }

    // Botón de volver (solo visible al añadir cuenta)
    if (onBack != null) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
            }
        }
    }

    AuthHeader(Icons.Default.MusicNote, "Bienvenido a Newsick")

    OutlinedTextField(
        value = identifier, onValueChange = { identifier = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email o nombre de usuario") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contraseña") },
        singleLine = true,
        visualTransformation = if (passVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { passVisible = !passVisible }) {
                Icon(if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        }
    )
    authError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { viewModel.performLogin(identifier.trim(), password) },
        modifier = Modifier.fillMaxWidth(),
        enabled  = !isLoading && identifier.isNotBlank() && password.isNotBlank()
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text("Iniciar Sesión")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = {
        viewModel.authStep.value = "register_email"
        viewModel.authError.value = null
    }) { Text("¿No tienes cuenta? Regístrate") }
    TextButton(onClick = {
        viewModel.authStep.value = "reset_send"
        viewModel.authError.value = null
    }) { Text("¿Olvidaste tu contraseña?") }
}

// ── Paso: Registro — email + contraseña ──────────────────

@Composable
private fun AuthRegisterEmailStep(viewModel: MainViewModel, isLoading: Boolean, authError: String?) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    AuthHeader(Icons.Default.PersonAdd, "Crea tu cuenta")

    OutlinedTextField(
        value = email, onValueChange = { email = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Correo electrónico") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contraseña (mín. 6 caracteres)") },
        singleLine = true,
        visualTransformation = if (passVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { passVisible = !passVisible }) {
                Icon(if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = confirm, onValueChange = { confirm = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Repetir contraseña") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = confirm.isNotBlank() && confirm != password,
        supportingText = if (confirm.isNotBlank() && confirm != password) ({ Text("Las contraseñas no coinciden") }) else null
    )
    authError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = {
            if (!email.contains("@")) { viewModel.authError.value = "Introduce un email válido"; return@Button }
            if (password.length < 6) { viewModel.authError.value = "La contraseña debe tener al menos 6 caracteres"; return@Button }
            if (password != confirm) { viewModel.authError.value = "Las contraseñas no coinciden"; return@Button }
            viewModel.authPendingEmail.value = email.trim()
            viewModel.prepareRegister(email.trim(), password)
            viewModel.sendVerificationCode(email.trim(), "register") { ok, devCode ->
                if (ok) {
                    if (devCode != null) viewModel.authPendingCode.value = devCode // rellena el código automáticamente (sin SMTP)
                    viewModel.authStep.value = "register_verify"
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled  = !isLoading && email.isNotBlank() && password.isNotBlank() && confirm.isNotBlank()
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text("Enviar código de verificación")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { viewModel.authStep.value = "login"; viewModel.authError.value = null }) {
        Text("¿Ya tienes cuenta? Entra aquí")
    }
}

// ── Paso: Verificar código (registro o reset) ─────────────

@Composable
private fun AuthVerifyStep(
    viewModel: MainViewModel, isLoading: Boolean, authError: String?,
    title: String, subtitle: String, purpose: String,
    onVerified: () -> Unit, onBack: () -> Unit
) {
    // Si authPendingCode tiene valor (devCode del servidor sin SMTP), lo usamos como valor inicial
    var code by remember { mutableStateOf(viewModel.authPendingCode.value) }
    val scope = rememberCoroutineScope()

    AuthHeader(Icons.Default.MarkEmailRead, title)

    Text(subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = code, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Código de 6 dígitos") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    authError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = {
            viewModel.authPendingCode.value = code
            viewModel.verifyCode(viewModel.authPendingEmail.value, code, purpose) { ok ->
                if (ok) onVerified()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled  = !isLoading && code.length == 6
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text("Verificar")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = {
        scope.launch {
            viewModel.sendVerificationCode(viewModel.authPendingEmail.value, purpose) { _, devCode ->
                if (devCode != null) code = devCode
            }
        }
    }) { Text("Reenviar código") }
    TextButton(onClick = onBack) { Text("Volver") }
}

// ── Paso: Registro — elegir usuario ──────────────────────

@Composable
private fun AuthUsernameStep(viewModel: MainViewModel, isLoading: Boolean, authError: String?) {
    var username by remember { mutableStateOf("") }
    val usernameError = if (username.isNotBlank()) validateUsername(username) else null

    AuthHeader(Icons.Default.AccountCircle, "Elige tu nombre de usuario")

    Text("Solo letras, números, puntos y guiones bajos",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = username, onValueChange = { if (it.length <= 30) username = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nombre de usuario") },
        singleLine = true,
        isError = usernameError != null,
        supportingText = usernameError?.let { { Text(it) } }
    )
    authError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { viewModel.performRegister(viewModel.authPendingEmail.value, viewModel.pendingPassword, username) },
        modifier = Modifier.fillMaxWidth(),
        enabled  = !isLoading && username.isNotBlank() && usernameError == null
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text("Crear cuenta")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { viewModel.authStep.value = "register_verify"; viewModel.authError.value = null }) {
        Text("Volver")
    }
}

// ── Paso: Reset — enviar email ────────────────────────────

@Composable
private fun AuthResetSendStep(viewModel: MainViewModel, isLoading: Boolean, authError: String?) {
    var email by remember { mutableStateOf("") }

    AuthHeader(Icons.Default.LockReset, "Restablecer contraseña")

    Text("Introduce tu correo electrónico y te enviaremos un código.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = email, onValueChange = { email = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Correo electrónico") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    authError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = {
            if (!email.contains("@")) { viewModel.authError.value = "Introduce un email válido"; return@Button }
            viewModel.authPendingEmail.value = email.trim()
            viewModel.sendVerificationCode(email.trim(), "reset") { ok, devCode ->
                if (ok) {
                    if (devCode != null) viewModel.authPendingCode.value = devCode
                    viewModel.authStep.value = "reset_verify"
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled  = !isLoading && email.isNotBlank()
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text("Enviar código")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { viewModel.authStep.value = "login"; viewModel.authError.value = null }) {
        Text("Volver al inicio de sesión")
    }
}

// ── Paso: Reset — nueva contraseña ───────────────────────

@Composable
private fun AuthNewPasswordStep(viewModel: MainViewModel, isLoading: Boolean, authError: String?) {
    var password by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    AuthHeader(Icons.Default.Lock, "Nueva contraseña")

    OutlinedTextField(
        value = password, onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nueva contraseña") },
        singleLine = true,
        visualTransformation = if (passVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { passVisible = !passVisible }) {
                Icon(if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            }
        }
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = confirm, onValueChange = { confirm = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Repetir contraseña") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = confirm.isNotBlank() && confirm != password,
        supportingText = if (confirm.isNotBlank() && confirm != password) ({ Text("Las contraseñas no coinciden") }) else null
    )
    authError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = {
            if (password.length < 6) { viewModel.authError.value = "Mínimo 6 caracteres"; return@Button }
            if (password != confirm)  { viewModel.authError.value = "Las contraseñas no coinciden"; return@Button }
            viewModel.changePassword(viewModel.authPendingEmail.value, viewModel.authPendingCode.value, password) { ok ->
                if (ok) { viewModel.authStep.value = "login"; viewModel.authError.value = null }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled  = !isLoading && password.isNotBlank() && confirm.isNotBlank()
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        else Text("Guardar nueva contraseña")
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { viewModel.authStep.value = "reset_verify"; viewModel.authError.value = null }) {
        Text("Volver")
    }
}

// ══════════════════════════════════════════════════════════
// SETTINGS
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onLogout: () -> Unit, onAddAccount: () -> Unit) {
    val context    = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkMode   by viewModel.darkModeOverride
    val scope      = rememberCoroutineScope()
    var chatPrivacy   by remember { mutableStateOf("everyone") }
    var showAiConfig  by remember { mutableStateOf(false) }
    var selectedTab   by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val r = NewsickRetrofit.api.getChatPrivacy(viewModel.loggedUserId.value)
            if (r.isSuccessful) chatPrivacy = r.body()?.chatPrivacy ?: "everyone"
        } catch (_: Exception) {}
    }

    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "?" }
    }
    val versionCode = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionCode } catch (_: Exception) { 0 }
    }
    var updateStatus by remember { mutableStateOf<String?>(null) } // null=comprobando, "ok", "available:X.X", "required"
    var channelUrl   by remember { mutableStateOf("https://whatsapp.com/channel/0029VbC0ILX4yltWHUDKu22h") }

    LaunchedEffect(Unit) {
        try {
            val r = NewsickRetrofit.api.getLatestVersion()
            if (r.isSuccessful) {
                val body = r.body()!!
                channelUrl = body.channelUrl
                updateStatus = when {
                    versionCode < body.minVersionCode    -> "required"
                    versionCode < body.latestVersionCode -> "available:${body.latestVersionName}"
                    else                                 -> "ok"
                }
            }
        } catch (_: Exception) { updateStatus = "?" }
    }

    if (showAiConfig) {
        AiConfigDialog(context = context, onDismiss = { showAiConfig = false })
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Configuración") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        text     = { Text("Apariencia") },
                        icon     = { Icon(Icons.Default.Palette, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        text     = { Text("Privacidad") },
                        icon     = { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick  = { selectedTab = 2 },
                        text     = { Text("IA") },
                        icon     = { Icon(Icons.Default.SmartToy, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick  = { selectedTab = 3 },
                        text     = { Text("Cuenta") },
                        icon     = { Icon(Icons.Default.ManageAccounts, null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            when (selectedTab) {

                // ── Pestaña 0: Apariencia ──────────────────
                0 -> {
                    Text("Apariencia",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Tema", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = darkMode == null, onClick = { viewModel.setDarkMode(null) })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Automático (sistema)")
                                    Text("Actualmente: ${if (systemDark) "oscuro" else "claro"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = darkMode == false, onClick = { viewModel.setDarkMode(false) })
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.LightMode, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Modo claro")
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = darkMode == true, onClick = { viewModel.setDarkMode(true) })
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.DarkMode, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Modo oscuro")
                            }
                        }
                    }
                }

                // ── Pestaña 1: Privacidad ──────────────────
                1 -> {
                    Text("Privacidad",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Mensajes privados", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            listOf(
                                "everyone" to "Todos pueden enviarte mensajes",
                                "friends"  to "Solo amigos",
                                "nobody"   to "Nadie puede enviarte mensajes"
                            ).forEachIndexed { i, (key, label) ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = chatPrivacy == key,
                                        onClick  = {
                                            chatPrivacy = key
                                            scope.launch {
                                                try { NewsickRetrofit.api.updateChatPrivacy(ChatPrivacyUpdateRequest(key)) }
                                                catch (_: Exception) {}
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                // ── Pestaña 2: IA ──────────────────────────
                2 -> {
                    Text("Inteligencia Artificial",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Asistente de chat", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SmartToy, null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                val aiConfig = loadAiConfig(context)
                                if (aiConfig != null) {
                                    Text(aiConfig.provider.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(aiConfig.model,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Sin configurar",
                                        style = MaterialTheme.typography.titleSmall)
                                    Text("Pulsa Editar para añadir tu API Key",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(onClick = { showAiConfig = true }) { Text("Editar") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "La IA se usa para responder mensajes automáticamente en los chats. Necesitas una API Key del proveedor que elijas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Pestaña 3: Cuenta ──────────────────────
                3 -> {
                    // Todas las sesiones guardadas en este dispositivo (cualquier correo)
                    var allSessions      by remember { mutableStateOf(viewModel.getAllSavedSessions()) }
                    var showPassSheet    by remember { mutableStateOf(false) }
                    var switchTarget     by remember { mutableStateOf<UserResponse?>(null) }
                    var switchPass       by remember { mutableStateOf("") }
                    var switchErr        by remember { mutableStateOf(false) }
                    var switchLoading    by remember { mutableStateOf(false) }

                    // Recargar al cambiar de cuenta activa
                    LaunchedEffect(viewModel.loggedUserId.value) {
                        allSessions = viewModel.getAllSavedSessions()
                    }

                    // ── Diálogo: contraseña para cambiar de cuenta ──
                    switchTarget?.let { target ->
                        AlertDialog(
                            onDismissRequest = { if (!switchLoading) { switchTarget = null; switchPass = ""; switchErr = false } },
                            title = { Text("Acceder como @${target.username}") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Introduce la contraseña de esta cuenta.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = switchPass,
                                        onValueChange = { switchPass = it; switchErr = false },
                                        label = { Text("Contraseña") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        isError = switchErr,
                                        supportingText = if (switchErr) ({ Text("Contraseña incorrecta") }) else null,
                                        enabled = !switchLoading
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        switchLoading = true; switchErr = false
                                        scope.launch {
                                            viewModel.performLoginAsync(target.username, switchPass) { ok ->
                                                switchLoading = false
                                                if (ok) { switchTarget = null; switchPass = "" }
                                                else switchErr = true
                                            }
                                        }
                                    },
                                    enabled = switchPass.isNotBlank() && !switchLoading
                                ) {
                                    if (switchLoading) CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    else Text("Entrar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { switchTarget = null; switchPass = ""; switchErr = false }, enabled = !switchLoading) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }

                    // ── Bottom sheet: cambiar contraseña ──────
                    if (showPassSheet) {
                        var passStep  by remember { mutableStateOf("send") } // send | verify | new
                        var passCode  by remember { mutableStateOf("") }
                        var newPass   by remember { mutableStateOf("") }
                        var confirmP  by remember { mutableStateOf("") }
                        var passErr   by remember { mutableStateOf<String?>(null) }
                        var passLoad  by remember { mutableStateOf(false) }

                        ModalBottomSheet(onDismissRequest = { showPassSheet = false }) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                                Text(
                                    when (passStep) { "verify" -> "Introduce el código"; "new" -> "Nueva contraseña"; else -> "Cambiar contraseña" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(16.dp))
                                when (passStep) {
                                    "send" -> {
                                        Text("Te enviaremos un código al correo ${viewModel.loggedEmail.value}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        passErr?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                                        Spacer(Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                passLoad = true; passErr = null
                                                viewModel.sendVerificationCode(viewModel.loggedEmail.value, "reset") { ok, devCode ->
                                                    passLoad = false
                                                    if (ok) {
                                                        if (devCode != null) passCode = devCode
                                                        passStep = "verify"
                                                    } else passErr = "Error al enviar el código"
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(), enabled = !passLoad
                                        ) {
                                            if (passLoad) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                            else Text("Enviar código")
                                        }
                                    }
                                    "verify" -> {
                                        OutlinedTextField(
                                            value = passCode,
                                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) passCode = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("Código de 6 dígitos") }, singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        passErr?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                passLoad = true; passErr = null
                                                viewModel.verifyCode(viewModel.loggedEmail.value, passCode, "reset") { ok ->
                                                    passLoad = false
                                                    if (ok) passStep = "new" else passErr = "Código incorrecto o caducado"
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(), enabled = !passLoad && passCode.length == 6
                                        ) {
                                            if (passLoad) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                            else Text("Verificar")
                                        }
                                    }
                                    "new" -> {
                                        OutlinedTextField(
                                            value = newPass, onValueChange = { newPass = it },
                                            modifier = Modifier.fillMaxWidth(), label = { Text("Nueva contraseña") },
                                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = confirmP, onValueChange = { confirmP = it },
                                            modifier = Modifier.fillMaxWidth(), label = { Text("Repetir contraseña") },
                                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                            isError = confirmP.isNotBlank() && confirmP != newPass,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                        )
                                        passErr?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                if (newPass.length < 6) { passErr = "Mínimo 6 caracteres"; return@Button }
                                                if (newPass != confirmP) { passErr = "Las contraseñas no coinciden"; return@Button }
                                                passLoad = true; passErr = null
                                                viewModel.changePassword(viewModel.loggedEmail.value, passCode, newPass) { ok ->
                                                    passLoad = false
                                                    if (ok) showPassSheet = false else passErr = "Error al cambiar la contraseña"
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(), enabled = !passLoad && newPass.isNotBlank() && confirmP.isNotBlank()
                                        ) {
                                            if (passLoad) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                            else Text("Guardar nueva contraseña")
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }

                    Text("Cuenta",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))

                    // ── Sesiones guardadas en este dispositivo ─
                    // Agrupadas por email para que quede claro cuáles comparten correo
                    val sessionsByEmail = allSessions.groupBy { it.email }
                    sessionsByEmail.entries.forEachIndexed { groupIdx, (email, accounts) ->
                        if (groupIdx > 0) Spacer(Modifier.height(16.dp))
                        Text(email, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                accounts.forEachIndexed { i, acc ->
                                    val isActive = acc.id == viewModel.loggedUserId.value
                                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .then(if (!isActive) Modifier.clickable {
                                                viewModel.switchAccountIfPossible(acc) {
                                                    switchTarget = acc; switchPass = ""; switchErr = false
                                                }
                                            } else Modifier)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (isActive) Icons.Default.AccountCircle else Icons.Default.SwitchAccount,
                                            null, modifier = Modifier.size(24.dp),
                                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(acc.username, style = MaterialTheme.typography.titleSmall)
                                            if (isActive) Text("Sesión activa",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                        if (isActive) Icon(Icons.Default.CheckCircle, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp))
                                        else Icon(Icons.Default.ChevronRight, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Añadir cuenta ──────────────────────────
                    OutlinedButton(
                        onClick  = onAddAccount,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Añadir cuenta")
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Seguridad ─────────────────────────────
                    Text("Seguridad", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showPassSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Cambiar contraseña")
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Soporte ───────────────────────────────
                    Text("Soporte", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    // Estado de actualización
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (statusIcon, statusText, statusColor) = when {
                                updateStatus == null    -> Triple(Icons.Default.HourglassEmpty,  "Comprobando actualizaciones…", MaterialTheme.colorScheme.onSurfaceVariant)
                                updateStatus == "ok"    -> Triple(Icons.Default.CheckCircle,     "Newsick está actualizado",     MaterialTheme.colorScheme.primary)
                                updateStatus == "required" -> Triple(Icons.Default.Error,        "Actualización obligatoria",    MaterialTheme.colorScheme.error)
                                updateStatus == "?"     -> Triple(Icons.Default.CloudOff,        "No se pudo comprobar",         MaterialTheme.colorScheme.onSurfaceVariant)
                                else -> Triple(Icons.Default.SystemUpdate, "Actualización disponible: ${updateStatus?.removePrefix("available:")}", MaterialTheme.colorScheme.tertiary)
                            }
                            Icon(statusIcon, null, modifier = Modifier.size(24.dp), tint = statusColor)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                                Text("v$version (build $versionCode)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (updateStatus != "ok" && updateStatus != null && updateStatus != "?") {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)))
                                }) { Text("Actualizar") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val i = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("marcosqh17@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "Feedback Newsick")
                            }
                            try { context.startActivity(i) }
                            catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "No se encontró app de correo", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Email, null); Spacer(Modifier.width(8.dp)); Text("Enviar Feedback") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onLogout, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Cerrar Sesión") }
                    Spacer(Modifier.height(32.dp))
                    Text("v$version", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}