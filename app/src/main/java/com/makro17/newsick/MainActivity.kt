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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
    val db   = NewsickDatabase.getDatabase(application)
    private val repo = NewsickRepository(db)

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    var isLoggedIn         = mutableStateOf(false)
    var authError          = mutableStateOf<String?>(null)
    var isRegistering      = mutableStateOf(false)
    var needsUsername      = mutableStateOf(false)
    var loggedUsername     = mutableStateOf("")
    var loggedBio          = mutableStateOf("")
    var loggedEmail        = mutableStateOf("")
    var loggedProfilePhoto = mutableStateOf("")
    var loggedUserId       = mutableStateOf(0)

    var userSearchQuery    = mutableStateOf("")
    var searchResults      = mutableStateOf<List<UserResponse>>(emptyList())
    var isSearching        = mutableStateOf(false)

    var pendingRequests    = mutableStateOf<List<FriendRequestResponse>>(emptyList())
    var notifications      = mutableStateOf<List<NotificationResponse>>(emptyList())
    var unreadCount        = mutableStateOf(0)
    var friendCount        = mutableStateOf(0)
    var friendsList        = mutableStateOf<List<FriendshipResponse>>(emptyList())

    var apiFeed            = mutableStateOf<List<PostResponse>>(emptyList())
    var mixedPhotosCache   = mutableStateOf<Map<String, List<PhotoResponse>>>(emptyMap())

    // Dark mode: null = seguir al sistema, true = oscuro, false = claro
    var darkModeOverride   = mutableStateOf<Boolean?>(null)

    // Mapa
    var nearbyUsers        = mutableStateOf<List<NearbyUserResponse>>(emptyList())

    val feedSongs: StateFlow<List<SongPostEntity>> = repo.getActiveSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val shownNotifIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            val saved = prefs.getInt("user_id", 0)
            if (saved > 0) {
                loggedUserId.value       = saved
                loggedUsername.value     = prefs.getString("username", "") ?: ""
                loggedBio.value          = prefs.getString("bio", "") ?: ""
                loggedEmail.value        = prefs.getString("email", "") ?: ""
                loggedProfilePhoto.value = prefs.getString("profile_photo", "") ?: ""
                AuthManager.token        = prefs.getString("token", "") ?: ""
                AuthManager.userId       = saved
                isLoggedIn.value         = true
            }
            val savedTheme = prefs.getInt("dark_mode", -1)
            darkModeOverride.value = when (savedTheme) {
                0    -> false
                1    -> true
                else -> null
            }
            delay(1000)
            _isLoading.value = false
        }
    }

    // ── Dark mode ─────────────────────────────────────────

    fun setDarkMode(value: Boolean?) {
        darkModeOverride.value = value
        prefs.edit().putInt("dark_mode", when (value) { false -> 0; true -> 1; else -> -1 }).apply()
    }

    // ── AUTH ──────────────────────────────────────────────

    fun performLogin(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.login(LoginRequest(email, pass))
                if (r.isSuccessful) saveSession(r.body()!!)
                else authError.value = "Correo o contraseña incorrectos"
            } catch (e: Exception) { authError.value = "Error de conexión: ${e.message}" }
            _isLoading.value = false
        }
    }

    fun prepareRegister(email: String, pass: String) {
        pendingEmail = email; pendingPassword = pass; needsUsername.value = true
    }
    private var pendingEmail    = ""
    private var pendingPassword = ""

    fun performRegister(email: String, pass: String, username: String) {
        val usernameError = validateUsername(username)
        if (usernameError != null) { authError.value = usernameError; return }
        viewModelScope.launch {
            _isLoading.value = true; authError.value = null
            try {
                val r = api.register(RegisterRequest(email, pass, username))
                if (r.isSuccessful) saveSession(r.body()!!)
                else authError.value = when (r.code()) {
                    409  -> "Nombre de usuario o email ya en uso"
                    400  -> "Nombre de usuario inválido"
                    else -> "Error al registrarse"
                }
            } catch (e: Exception) { authError.value = "Error de conexión: ${e.message}" }
            _isLoading.value = false
        }
    }

    private fun saveSession(auth: AuthResponse) {
        loggedUserId.value       = auth.user.id
        loggedUsername.value     = auth.user.username
        loggedBio.value          = auth.user.bio ?: ""
        loggedEmail.value        = auth.user.email
        loggedProfilePhoto.value = auth.user.profilePhoto ?: ""
        AuthManager.token        = auth.token
        AuthManager.userId       = auth.user.id
        isLoggedIn.value         = true
        prefs.edit().apply {
            putInt("user_id", auth.user.id)
            putString("username", auth.user.username)
            putString("bio", auth.user.bio ?: "")
            putString("email", auth.user.email)
            putString("token", auth.token)
            putString("profile_photo", auth.user.profilePhoto ?: "")
            apply()
        }
    }

    fun logout() {
        isLoggedIn.value = false; isRegistering.value = false; needsUsername.value = false
        loggedUserId.value = 0; loggedUsername.value = ""; loggedBio.value = ""
        loggedEmail.value = ""; loggedProfilePhoto.value = ""
        AuthManager.token = ""; AuthManager.userId = 0
        prefs.edit().clear().apply()
        apiFeed.value = emptyList(); mixedPhotosCache.value = emptyMap()
        shownNotifIds.clear()
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
                    onResult(false, when (r.code()) {
                        409  -> "Nombre de usuario ya en uso"
                        400  -> "Nombre de usuario inválido"
                        else -> "Error al actualizar"
                    })
                }
            } catch (e: Exception) { e.printStackTrace(); onResult(false, "Error de conexión") }
        }
    }

    fun deleteAccount(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val r = api.deleteAccount(DeleteAccountRequest(password))
                if (r.isSuccessful) { logout(); onResult(true) } else onResult(false)
            } catch (e: Exception) { onResult(false) }
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
                    invalidateMixedCache(); loadFeed()
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
                invalidateMixedCache(trackId); loadFeed()
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
                    invalidateMixedCache(trackId); loadFeed(); invalidateMySongsCache()
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
                    latitude   = lat,  longitude  = lng,
                    trackId    = song?.trackId,   trackName  = song?.trackName,
                    artistName = song?.artistName, artworkUrl = song?.artworkUrl
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
        setContent {
            val systemDark   = isSystemInDarkTheme()
            val darkOverride by viewModel.darkModeOverride
            val isDark       = darkOverride ?: systemDark
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                val windowSize = calculateWindowSizeClass(this)
                if (viewModel.isLoggedIn.value) NewsickApp(windowSize.widthSizeClass, viewModel)
                else AuthScreen(viewModel)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// NAVEGACIÓN
// ══════════════════════════════════════════════════════════

@Composable
fun NewsickApp(windowSize: WindowWidthSizeClass, viewModel: MainViewModel) {
    val navController = rememberNavController()
    val currentRoute  = navController.currentBackStackEntryAsState().value?.destination?.route
    val context       = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadNotificationsData(context)
        viewModel.loadFriendCount()
        viewModel.loadFeed()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Social") },
                    selected = currentRoute == "social",
                    onClick = {
                        if (currentRoute == "social") { viewModel.loadNotificationsData(context); viewModel.loadFeed() }
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
        NavHost(navController, "social", Modifier.padding(pad)) {
            composable("social") {
                SocialFeedScreen(viewModel,
                    onSongClick          = { navController.navigate("detail/$it") },
                    onUploadClick        = { navController.navigate("upload") },
                    onNotificationsClick = { navController.navigate("notifications") },
                    onUserClick          = { navController.navigate("userProfile/$it") }
                )
            }
            composable("map") {
                MapScreen(viewModel, onUserClick = { navController.navigate("userProfile/$it") })
            }
            composable("profile") {
                ProfileScreen(viewModel,
                    onSettingsClick = { navController.navigate("settings") },
                    onSongClick     = { navController.navigate("detail/$it") },
                    onFriendsClick  = { navController.navigate("friends") }
                )
            }
            composable("settings") {
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onLogout = { viewModel.logout() })
            }
            composable("friends") {
                FriendsListScreen(viewModel,
                    onBack      = { navController.popBackStack() },
                    onUserClick = { navController.navigate("userProfile/$it") }
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
                    onSongClick = { navController.navigate("detail/$it") }
                )
            }
            composable("notifications") {
                NotificationsScreen(viewModel,
                    onBack      = { navController.popBackStack() },
                    onUserClick = { navController.navigate("userProfile/$it") }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// AUTH SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val isReg     = viewModel.isRegistering.value
    val needUser  = viewModel.needsUsername.value
    val isLoading = viewModel.isLoading.collectAsState().value
    val authError = viewModel.authError.value
    val usernameError = if (needUser && username.isNotBlank()) validateUsername(username) else null

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {

            Icon(if (needUser) Icons.Default.AccountCircle else Icons.Default.MusicNote,
                null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(when { needUser -> "Elige tu nombre de usuario"; isReg -> "Crea tu cuenta"; else -> "Bienvenido a Newsick" },
                style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            if (!needUser) {
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true)
            } else {
                Text("Solo letras, números, puntos y guiones bajos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = username, onValueChange = { if (it.length <= 30) username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre de usuario") }, singleLine = true,
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } }
                )
            }

            authError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    when {
                        needUser -> viewModel.performRegister(email, password, username)
                        isReg    -> {
                            if (email.contains("@") && password.length >= 6) viewModel.prepareRegister(email, password)
                            else viewModel.authError.value = "Correo válido y mínimo 6 caracteres"
                        }
                        else -> viewModel.performLogin(email, password)
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = !isLoading && (if (needUser) username.isNotBlank() && usernameError == null else true)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(when { needUser -> "Finalizar"; isReg -> "Siguiente"; else -> "Iniciar Sesión" })
            }
            Spacer(Modifier.height(16.dp))
            if (!needUser) {
                TextButton(onClick = { viewModel.isRegistering.value = !isReg; viewModel.authError.value = null }) {
                    Text(if (isReg) "¿Ya tienes cuenta? Entra aquí" else "¿No tienes cuenta? Regístrate")
                }
            } else {
                TextButton(onClick = { viewModel.needsUsername.value = false }) { Text("Volver") }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// SETTINGS
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val context    = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkMode   by viewModel.darkModeOverride

    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "?" }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Configuración") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Newsick", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))

            Text("Apariencia", style = MaterialTheme.typography.titleSmall,
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

            Spacer(Modifier.height(24.dp))

            Button(onClick = {
                val i = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("marcosqh17@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback Newsick")
                }
                try { context.startActivity(i) }
                catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No se encontró app de correo", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Email, null); Spacer(Modifier.width(8.dp)); Text("Enviar Feedback")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Cerrar Sesión")
            }

            Spacer(Modifier.weight(1f))
            Text("v$version", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
        }
    }
}
