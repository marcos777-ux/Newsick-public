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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FriendCollection(val id: Int, val songTitle: String, val friendNames: List<String>)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = NewsickRetrofit.api
    private var authToken: String? = null
    // NUEVO: Estado de búsqueda de usuarios
    var searchResults = mutableStateOf<List<UserEntity>>(emptyList())
    var isSearching = mutableStateOf(false)
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
    var loggedUserId   = mutableStateOf(0)
    var userSearchQuery = mutableStateOf("")

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
            val savedUsername = prefs.getString("username", "")
            val savedBio = prefs.getString("bio", "")
            val savedToken = prefs.getString("token", "")

            if (savedUserId > 0) {
                loggedUserId.value = savedUserId
                loggedUsername.value = savedUsername ?: ""
                loggedBio.value = savedBio ?: ""
                isLoggedIn.value = true
            }
            delay(1000)
            _isLoading.value = false
        }
    }
    fun performLogin(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            try {
                val response = api.login(LoginRequest(email, pass))
                if (response.isSuccessful) {
                    val auth = response.body()!!
                    loggedUserId.value = auth.user.id
                    loggedUsername.value = auth.user.username
                    loggedBio.value = auth.user.bio
                    isLoggedIn.value = true

                    prefs.edit().apply {
                        putInt("user_id", auth.user.id)
                        putString("username", auth.user.username)
                        putString("bio", auth.user.bio)
                        putString("token", auth.token)
                        apply()
                    }
                } else {
                    authError.value = "Correo o contraseña incorrectos"
                }
            } catch (e: Exception) {
                authError.value = "Error de conexión: ${e.message}"
                e.printStackTrace()
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
                    loggedUserId.value = auth.user.id
                    loggedUsername.value = auth.user.username
                    loggedBio.value = auth.user.bio
                    isLoggedIn.value = true

                    prefs.edit().apply {
                        putInt("user_id", auth.user.id)
                        putString("username", auth.user.username)
                        putString("bio", auth.user.bio)
                        putString("token", auth.token)
                        apply()
                    }
                } else {
                    authError.value = "Este correo ya está registrado"
                }
            } catch (e: Exception) {
                authError.value = "Error de conexión: ${e.message}"
                e.printStackTrace()
            }
            _isLoading.value = false
        }
    }

    fun fetchPostsFromApi() {
        viewModelScope.launch {
            try {
                val response = api.getPosts()
                if (response.isSuccessful) {
                    val posts = response.body()!!
                    // Guardar en base de datos local para caché
                    posts.forEach { post ->
                        repo.createPost(
                            trackId = post.trackId,
                            trackName = post.trackName,
                            artistName = post.artistName,
                            artworkUrl = post.artworkUrl,
                            photoUris = emptyList(),
                            userId = 0,
                            username = ""
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        isLoggedIn.value = false
        isRegistering.value = false
        needsUsername.value = false
        loggedUserId.value = 0
        loggedUsername.value = ""
        loggedBio.value = ""
        pendingEmail = ""
        pendingPassword = ""

        prefs.edit().clear().apply()
    }

    fun createPost(
        trackId: String, trackName: String, artistName: String,
        artworkUrl: String, photoUris: List<String>
    ) {
        viewModelScope.launch {
            try {
                val request = PostRequest(
                    trackId = trackId,
                    trackName = trackName,
                    artistName = artistName,
                    artworkUrl = artworkUrl,
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
                    // También guardar localmente
                    repo.createPost(trackId, trackName, artistName, artworkUrl, photoUris, loggedUserId.value, loggedUsername.value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getPhotosForSong(trackId: String) = repo.getPhotosForSong(trackId)

    suspend fun getSongPost(trackId: String) = repo.getSongPost(trackId)

    // NUEVO: Función de búsqueda
    fun searchUsers(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                searchResults.value = emptyList()
                isSearching.value = false
                return@launch
            }
            isSearching.value = true
            val results = repo.searchUsers(query)
            searchResults.value = results
            isSearching.value = false
        }
    }
}

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

@Composable
fun NewsickApp(windowSize: WindowWidthSizeClass, viewModel: MainViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Social") },
                    selected = navController.currentDestination?.route == "social",
                    onClick = { navController.navigate("social") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, null) }, label = { Text("Mapa") },
                    selected = navController.currentDestination?.route == "map",
                    onClick = { navController.navigate("map") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Yo") },
                    selected = navController.currentDestination?.route == "profile",
                    onClick = { navController.navigate("profile") { launchSingleTop = true } }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "social", Modifier.padding(innerPadding)) {
            composable("social") {
                SocialFeedScreen(
                    viewModel = viewModel,
                    onSongClick = { navController.navigate("detail/$it") },
                    onUploadClick = { navController.navigate("upload") }
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
        }
    }
}

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
                    label = { Text("Correo electronico") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contrasena") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            } else {
                Text(
                    "Elige un nombre de usuario publico para continuar.",
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
                            else viewModel.authError.value = "Correo valido y minimo 6 caracteres"
                        }
                        else -> viewModel.performLogin(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && (if (needsUsername) chosenUsername.isNotBlank() else true)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(when { needsUsername -> "Finalizar y Entrar"; isRegistering -> "Siguiente"; else -> "Iniciar Sesion" })
            }

            Spacer(Modifier.height(16.dp))

            if (!needsUsername) {
                TextButton(onClick = {
                    viewModel.isRegistering.value = !isRegistering
                    viewModel.authError.value = null
                }) {
                    Text(if (isRegistering) "Ya tienes cuenta? Entra aqui" else "No tienes cuenta? Registrate")
                }
            } else {
                TextButton(onClick = { viewModel.needsUsername.value = false }) { Text("Volver atras") }
            }
        }
    }
}

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
        Text("Configuracion y Feedback", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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
                Toast.makeText(context, "No se encontro app de correo", Toast.LENGTH_SHORT).show()
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
            Text("Cerrar Sesion")
        }

        Spacer(Modifier.height(24.dp))
        Text("v$versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}