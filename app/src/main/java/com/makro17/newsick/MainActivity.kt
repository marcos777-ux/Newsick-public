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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════
// VIEW MODEL
// ══════════════════════════════════════════════════════════

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val api = NewsickRetrofit.api

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("newsick_prefs", Context.MODE_PRIVATE)
    }
    private val db   = NewsickDatabase.getDatabase(application)
    private val repo = NewsickRepository(db)

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    var isLoggedIn     = mutableStateOf(false)
    var authError      = mutableStateOf<String?>(null)
    var isRegistering  = mutableStateOf(false)
    var needsUsername  = mutableStateOf(false)
    var loggedUsername = mutableStateOf("")
    var loggedBio      = mutableStateOf("")
    var loggedEmail    = mutableStateOf("")
    var loggedProfilePhoto = mutableStateOf("")
    var loggedUserId   = mutableStateOf(0)

    var userSearchQuery  = mutableStateOf("")
    var searchResults    = mutableStateOf<List<UserResponse>>(emptyList())
    var isSearching      = mutableStateOf(false)

    // Friend requests & notifications
    var pendingRequests  = mutableStateOf<List<FriendRequestResponse>>(emptyList())
    var notifications    = mutableStateOf<List<NotificationResponse>>(emptyList())
    var unreadCount      = mutableStateOf(0)

    private var pendingEmail    = ""
    private var pendingPassword = ""

    val feedSongs: StateFlow<List<SongPostEntity>> = repo.getActiveSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mySongs: StateFlow<List<SongPostEntity>> =
        snapshotFlow { loggedUserId.value }
            .flatMapLatest { uid ->
                if (uid == 0) flowOf(emptyList()) else repo.getActiveSongsByUser(uid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val savedUserId = prefs.getInt("user_id", 0)
            if (savedUserId > 0) {
                loggedUserId.value   = savedUserId
                loggedUsername.value = prefs.getString("username", "") ?: ""
                loggedBio.value      = prefs.getString("bio", "") ?: ""
                loggedEmail.value    = prefs.getString("email", "") ?: ""
                loggedProfilePhoto.value = prefs.getString("profile_photo", "") ?: ""
                val token = prefs.getString("token", "") ?: ""
                AuthManager.token  = token
                AuthManager.userId = savedUserId
                isLoggedIn.value = true
            }
            delay(1000)
            _isLoading.value = false
        }
    }

    // ── AUTH ──────────────────────────────────────────────

    fun performLogin(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            try {
                val response = api.login(LoginRequest(email, pass))
                if (response.isSuccessful) {
                    val auth = response.body()!!
                    saveSession(auth)
                } else {
                    authError.value = "Correo o contraseña incorrectos"
                }
            } catch (e: Exception) {
                authError.value = "Error de conexión: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun prepareRegister(email: String, pass: String) {
        pendingEmail    = email
        pendingPassword = pass
        needsUsername.value = true
    }

    fun performRegister(email: String, pass: String, username: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            try {
                val response = api.register(RegisterRequest(email, pass, username))
                if (response.isSuccessful) {
                    val auth = response.body()!!
                    saveSession(auth)
                } else {
                    authError.value = "Este correo ya está registrado"
                }
            } catch (e: Exception) {
                authError.value = "Error de conexión: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    private fun saveSession(auth: AuthResponse) {
        loggedUserId.value       = auth.user.id
        loggedUsername.value     = auth.user.username
        loggedBio.value          = auth.user.bio ?: ""
        loggedEmail.value        = auth.user.email
        loggedProfilePhoto.value = auth.user.profilePhoto?: ""
        AuthManager.token        = auth.token
        AuthManager.userId       = auth.user.id
        isLoggedIn.value = true
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
        isLoggedIn.value = false
        isRegistering.value = false
        needsUsername.value = false
        loggedUserId.value = 0
        loggedUsername.value = ""
        loggedBio.value = ""
        loggedEmail.value = ""
        loggedProfilePhoto.value = ""
        AuthManager.token = ""
        AuthManager.userId = 0
        pendingEmail = ""
        pendingPassword = ""
        prefs.edit().clear().apply()
    }

    // ── PERFIL ────────────────────────────────────────────

    fun updateProfile(
        newUsername: String,
        newBio: String,
        newProfilePhoto: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = api.updateProfile(
                    UpdateProfileRequest(bio = newBio, username = newUsername, profilePhoto = newProfilePhoto)
                )
                if (response.isSuccessful) {
                    val user = response.body()!!
                    loggedUsername.value     = user.username
                    loggedBio.value          = user.bio ?: ""
                    loggedProfilePhoto.value = user.profilePhoto ?: ""
                    prefs.edit().apply {
                        putString("username", user.username)
                        putString("bio", user.bio ?: "")
                        putString("profile_photo", user.profilePhoto ?: "")
                        apply()
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun deleteAccount(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.deleteAccount(DeleteAccountRequest(password))
                if (response.isSuccessful) {
                    logout()
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // ── BÚSQUEDA DE USUARIOS ──────────────────────────────

    fun searchUsers(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                searchResults.value = emptyList()
                isSearching.value = false
                return@launch
            }
            isSearching.value = true
            try {
                val response = api.searchUsers(SearchUsersRequest(query))
                searchResults.value = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
            } catch (e: Exception) {
                searchResults.value = emptyList()
            }
            isSearching.value = false
        }
    }

    suspend fun getUserById(userId: Int): UserResponse? {
        return try {
            val response = api.getUserById(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) { null }
    }

    suspend fun getFriendStatus(targetId: Int): String {
        return try {
            val response = api.getFriendStatus(targetId)
            if (response.isSuccessful) response.body()?.status ?: "none" else "none"
        } catch (e: Exception) { "none" }
    }

    // ── SOLICITUDES Y NOTIFICACIONES ─────────────────────

    fun loadNotificationsData() {
        viewModelScope.launch {
            try {
                val reqResp = api.getPendingRequests()
                if (reqResp.isSuccessful) pendingRequests.value = reqResp.body() ?: emptyList()

                val notifResp = api.getNotifications()
                if (notifResp.isSuccessful) {
                    val list = notifResp.body() ?: emptyList()
                    notifications.value = list
                    unreadCount.value = list.count { !it.isRead }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendFriendRequest(targetUserId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.sendFriendRequest(FriendRequestDto(targetUserId))
                onResult(response.isSuccessful || response.code() == 409)
            } catch (e: Exception) { onResult(false) }
        }
    }

    fun respondToFriendRequest(requestId: Int, accept: Boolean) {
        viewModelScope.launch {
            try {
                api.respondToFriendRequest(RespondFriendRequest(requestId, accept))
                loadNotificationsData()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun markNotificationRead(id: Int) {
        viewModelScope.launch {
            try {
                api.markNotificationRead(id)
                notifications.value = notifications.value.map {
                    if (it.id == id) it.copy(isRead = true) else it
                }
                unreadCount.value = notifications.value.count { !it.isRead }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── POSTS ─────────────────────────────────────────────

    fun createPost(
        trackId: String, trackName: String, artistName: String,
        artworkUrl: String, photoUris: List<String>
    ) {
        viewModelScope.launch {
            try {
                val request = PostRequest(
                    trackId = trackId, trackName = trackName,
                    artistName = artistName, artworkUrl = artworkUrl,
                    timestamp = System.currentTimeMillis(),
                    photos = photoUris.map { uri ->
                        PhotoRequest(
                            photoUri = uri,
                            userId = loggedUserId.value,
                            username = loggedUsername.value,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                )
                val response = api.createPost(request)
                if (response.isSuccessful) {
                    repo.createPost(trackId, trackName, artistName, artworkUrl, photoUris, loggedUserId.value, loggedUsername.value)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
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
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val windowSize = calculateWindowSizeClass(this)
                if (viewModel.isLoggedIn.value) NewsickApp(windowSize.widthSizeClass, viewModel)
                else AuthScreen(viewModel)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// APP — navegación principal
// ══════════════════════════════════════════════════════════

@Composable
fun NewsickApp(windowSize: WindowWidthSizeClass, viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Cargar notificaciones al iniciar
    LaunchedEffect(Unit) { viewModel.loadNotificationsData() }

    val topLevelRoutes = listOf("social", "map", "profile")

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Social") },
                    selected = currentRoute == "social",
                    onClick = {
                        if (currentRoute == "social") viewModel.loadNotificationsData()
                        else navController.navigate("social") { launchSingleTop = true; popUpTo("social") }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, null) },
                    label = { Text("Mapa") },
                    selected = currentRoute == "map",
                    onClick = {
                        if (currentRoute != "map")
                            navController.navigate("map") { launchSingleTop = true }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Perfil") },   // renombrado de "Yo"
                    selected = currentRoute == "profile",
                    onClick = {
                        if (currentRoute == "profile") { /* ya estamos, no hacer nada */ }
                        else navController.navigate("profile") { launchSingleTop = true; popUpTo("social") }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "social", Modifier.padding(innerPadding)) {
            composable("social") {
                SocialFeedScreen(
                    viewModel = viewModel,
                    onSongClick = { navController.navigate("detail/$it") },
                    onUploadClick = { navController.navigate("upload") },
                    onNotificationsClick = { navController.navigate("notifications") },
                    onUserClick = { userId -> navController.navigate("userProfile/$userId") }
                )
            }
            composable("map") { MapScreen() }
            composable("profile") {
                ProfileScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate("settings") },
                    onSongClick = { navController.navigate("detail/$it") }
                )
            }
            composable("settings") { SettingsScreen(onLogout = { viewModel.logout() }) }
            composable("upload") {
                PostUploadScreen(
                    viewModel = viewModel,
                    onPostCreated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("detail/{trackId}") { backStack ->
                val trackId = backStack.arguments?.getString("trackId") ?: return@composable
                SongDetailScreen(trackId = trackId, viewModel = viewModel,
                    onBack = { navController.popBackStack() })
            }
            composable("userProfile/{userId}") { backStack ->
                val userId = backStack.arguments?.getString("userId")?.toIntOrNull() ?: return@composable
                UserProfileScreen(
                    userId = userId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSongClick = { navController.navigate("detail/$it") }
                )
            }
            composable("notifications") {
                NotificationsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onUserClick = { userId -> navController.navigate("userProfile/$userId") }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// PANTALLA DE AUTH
// ══════════════════════════════════════════════════════════

@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var chosenUsername by remember { mutableStateOf("") }

    val isRegistering = viewModel.isRegistering.value
    val needsUsername = viewModel.needsUsername.value
    val isLoading     = viewModel.isLoading.collectAsState().value
    val authError     = viewModel.authError.value

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (needsUsername) Icons.Default.AccountCircle else Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    needsUsername -> "Configura tu perfil"
                    isRegistering -> "Crea tu cuenta"
                    else          -> "Bienvenido a Newsick"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(32.dp))

            if (!needsUsername) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            } else {
                Text(
                    "Elige un nombre de usuario público para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = chosenUsername, onValueChange = { chosenUsername = it },
                    label = { Text("Nombre de usuario") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, placeholder = { Text("@ejemplo") }
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
                        needsUsername -> viewModel.performRegister(email, password, chosenUsername)
                        isRegistering -> {
                            if (email.contains("@") && password.length >= 6) viewModel.prepareRegister(email, password)
                            else viewModel.authError.value = "Correo válido y mínimo 6 caracteres"
                        }
                        else -> viewModel.performLogin(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && (if (needsUsername) chosenUsername.isNotBlank() else true)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(when { needsUsername -> "Finalizar y Entrar"; isRegistering -> "Siguiente"; else -> "Iniciar Sesión" })
            }

            Spacer(Modifier.height(16.dp))

            if (!needsUsername) {
                TextButton(onClick = {
                    viewModel.isRegistering.value = !isRegistering
                    viewModel.authError.value = null
                }) {
                    Text(if (isRegistering) "¿Ya tienes cuenta? Entra aquí" else "¿No tienes cuenta? Regístrate")
                }
            } else {
                TextButton(onClick = { viewModel.needsUsername.value = false }) { Text("Volver atrás") }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// PANTALLA DE AJUSTES
// ══════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        catch (_: Exception) { "?" }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Newsick", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Configuración y Feedback", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("marcosqh17@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Feedback Newsick")
                putExtra(Intent.EXTRA_TEXT, "Hola,\n\nFeedback:\n\n")
            }
            try { context.startActivity(intent) }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No se encontró app de correo", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Default.Email, null)
            Spacer(Modifier.width(8.dp))
            Text("Enviar Feedback")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.ExitToApp, null)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar Sesión")
        }

        Spacer(Modifier.height(24.dp))
        Text("v$versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
