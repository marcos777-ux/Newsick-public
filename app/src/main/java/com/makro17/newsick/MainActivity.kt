package com.makro17.newsick

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel  // ← CAMBIADO: era ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// MODELOS DE DATOS (sin cambios)
// ─────────────────────────────────────────────────────────────
data class MusicItem(
    val id: Int,
    val title: String,
    val artist: String,
    val description: String,
    val genre: String,
    var isFavorite: Boolean = false
)

data class UserPost(val id: Int, val userName: String, val imageUrl: String, val comment: String)
data class FriendCollection(val id: Int, val songTitle: String, val friendNames: List<String>)

// ─────────────────────────────────────────────────────────────
// VIEWMODEL — ahora usa AndroidViewModel + Room
// ─────────────────────────────────────────────────────────────
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // BD y repositorio
    private val db = NewsickDatabase.getDatabase(application)
    private val repo = NewsickRepository(db)

    // Estado de carga inicial
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // --- ESTADO DE AUTENTICACIÓN ---
    var isLoggedIn = mutableStateOf(false)
    var authError = mutableStateOf<String?>(null)
    var isRegistering = mutableStateOf(false)
    var needsUsername = mutableStateOf(false)

    // Campos temporales para el flujo de registro de 2 pasos
    private var pendingEmail = ""
    private var pendingPassword = ""

    // Usuario activo (se rellena tras login o registro)
    var loggedUsername = mutableStateOf("")
    var loggedBio      = mutableStateOf("")

    // Búsquedas
    var userSearchQuery = mutableStateOf("")
    var mapSearchQuery = mutableStateOf("")

    // Canciones simuladas (no persisten en BD, son de exploración)
    val items = mutableStateListOf(
        MusicItem(1, "Neon Nights",  "CyberPunks", "Synthwave", "Electronic"),
        MusicItem(2, "Jazz Café",    "Blue Note",  "Jazz",      "Jazz"),
        MusicItem(3, "Heavy Code",   "Compilers",  "Metal",     "Metal"),
        MusicItem(4, "Lo-Fi Study",  "Chill Hop",  "Beats",     "Lo-Fi"),
        MusicItem(5, "Summer Vibes", "Sun",        "Pop",       "Pop"),
        MusicItem(6, "Night Drive",  "Kavinsky",   "Synth",     "Electronic")
    )

    // Colecciones desde Room — se actualiza sola cuando cambia la BD
    val friendCollections = repo.getCollections()
        .map { list ->
            list.map { entity ->
                FriendCollection(
                    id = entity.id,
                    songTitle = entity.songTitle,
                    friendNames = repo.parseNames(entity.friendNames)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            delay(1000)
            _isLoading.value = false
        }
    }

    // ── LOGIN ──────────────────────────────────────────────
    fun performLogin(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            val user = repo.login(email, pass)
            if (user != null) {
                loggedUsername.value = user.username
                loggedBio.value      = user.bio
                isLoggedIn.value = true
            } else {
                authError.value = "Correo o contraseña incorrectos"
            }
            _isLoading.value = false
        }
    }

    // ── REGISTRO — Paso 1: guardar email+pass temporalmente ──
    fun prepareRegister(email: String, pass: String) {
        pendingEmail = email
        pendingPassword = pass
        needsUsername.value = true
    }

    // ── REGISTRO — Paso 2: escribir en BD con username ───────
    fun performRegister(email: String, pass: String, username: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            // Usamos los valores pendientes si email/pass llegan vacíos (compatibilidad con AuthScreen)
            val finalEmail = email.ifBlank { pendingEmail }
            val finalPass  = pass.ifBlank  { pendingPassword }
            val user = repo.register(finalEmail, finalPass, username)
            if (user != null) {
                loggedUsername.value = user.username
                loggedBio.value      = user.bio
                isLoggedIn.value = true
            } else {
                authError.value = "Este correo ya está registrado"
            }
            _isLoading.value = false
        }
    }

    // ── LOGOUT ────────────────────────────────────────────
    fun logout() {
        isLoggedIn.value = false
        isRegistering.value = false
        needsUsername.value = false
        loggedUsername.value = ""
        loggedBio.value = ""
        pendingEmail = ""
        pendingPassword = ""
    }

    // ── COLECCIONES ───────────────────────────────────────
    fun addFriendCollection() {
        viewModelScope.launch {
            repo.addCollection("New Jam", listOf("Tú", "Nuevo Amigo"))
        }
    }

    fun removeCollection(id: Int) {
        viewModelScope.launch {
            repo.removeCollection(id)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ACTIVIDAD PRINCIPAL (sin cambios relevantes)
// ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                val windowSize = calculateWindowSizeClass(this)
                if (viewModel.isLoggedIn.value) {
                    NewsickApp(windowSize.widthSizeClass, viewModel)
                } else {
                    AuthScreen(viewModel)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PANTALLA DE AUTENTICACIÓN
// ─────────────────────────────────────────────────────────────
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var chosenUsername by remember { mutableStateOf("") }

    val isRegistering = viewModel.isRegistering.value
    val needsUsername = viewModel.needsUsername.value
    val isLoading = viewModel.isLoading.collectAsState().value
    val authError = viewModel.authError.value

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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when {
                    needsUsername -> "Configura tu perfil"
                    isRegistering -> "Crea tu cuenta"
                    else          -> "Bienvenido a Newsick"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (!needsUsername) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            } else {
                Text(
                    text = "Por favor, elige un nombre de usuario para continuar. Este nombre será público.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = chosenUsername,
                    onValueChange = { chosenUsername = it },
                    label = { Text("Nombre de usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("@ejemplo") }
                )
            }

            authError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    when {
                        // Paso 2 del registro: ya tenemos username → escribir en BD
                        needsUsername -> viewModel.performRegister(email, password, chosenUsername)

                        // Paso 1 del registro: validar y avanzar
                        isRegistering -> {
                            if (email.contains("@") && password.length >= 6) {
                                viewModel.prepareRegister(email, password)  // ← CAMBIADO
                            } else {
                                viewModel.authError.value =
                                    "Introduce un correo válido y 6 caracteres mínimo"
                            }
                        }

                        // Login normal
                        else -> viewModel.performLogin(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && (if (needsUsername) chosenUsername.isNotBlank() else true)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(
                        text = when {
                            needsUsername -> "Finalizar y Entrar"
                            isRegistering -> "Siguiente"
                            else          -> "Iniciar Sesión"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!needsUsername) {
                TextButton(onClick = {
                    viewModel.isRegistering.value = !isRegistering
                    viewModel.authError.value = null
                }) {
                    Text(if (isRegistering) "¿Ya tienes cuenta? Entra aquí" else "¿No tienes cuenta? Regístrate")
                }
            } else {
                TextButton(onClick = { viewModel.needsUsername.value = false }) {
                    Text("Volver atrás")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// NAVEGACIÓN PRINCIPAL
// ─────────────────────────────────────────────────────────────
@Composable
fun NewsickApp(windowSize: WindowWidthSizeClass, viewModel: MainViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Social") },
                    label = { Text("Social") },
                    selected = navController.currentDestination?.route == "search",
                    onClick = { navController.navigate("search") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = "Mapa") },
                    label = { Text("Mapa") },
                    selected = navController.currentDestination?.route == "map",
                    onClick = { navController.navigate("map") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
                    label = { Text("Yo") },
                    selected = navController.currentDestination?.route == "profile",
                    onClick = { navController.navigate("profile") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "search",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("search") {
                SocialSearchScreen(viewModel) { songId ->
                    navController.navigate("feed/$songId")
                }
            }
            composable("feed/{songId}") { SocialFeedScreen() }
            composable("map") { MapScreen(viewModel) }
            composable("profile") {
                UserProfileScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                Pantalla(onLogout = { viewModel.logout() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PANTALLAS (con ajuste en UserProfileScreen para usar StateFlow)
// ─────────────────────────────────────────────────────────────

@Composable
fun SocialSearchScreen(viewModel: MainViewModel, onCoverClick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = viewModel.userSearchQuery.value,
            onValueChange = { viewModel.userSearchQuery.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar usuario (...)") },
            leadingIcon = { Icon(Icons.Default.PersonSearch, null) },
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Explora Momentos Musicales", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.items) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onCoverClick(item.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(40.dp))
                        Text(item.title, style = MaterialTheme.typography.labelMedium)
                        Text(item.artist, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun SocialFeedScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Fotos de la comunidad", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(3) { index ->
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Post simulado #$index")
                }
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFFE3F2FD)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Place, null, modifier = Modifier.size(64.dp), tint = Color.Red)
            Text("Mapa de Eventos", color = Color.Gray)
        }
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.mapSearchQuery.value,
                onValueChange = { viewModel.mapSearchQuery.value = it },
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
                placeholder = { Text("Buscar ciudad...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun UserProfileScreen(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
    var showEditDialog by remember { mutableStateOf(false) }

    // Datos del usuario activo, editables localmente
    var currentUsername by remember { mutableStateOf(viewModel.loggedUsername.value) }
    var userBio         by remember { mutableStateOf(viewModel.loggedBio.value) }

    val collections by viewModel.friendCollections.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mi Perfil", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Configuración", modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentUsername, style = MaterialTheme.typography.titleLarge)
                    Text(userBio, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, "Editar Perfil")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Mis Canciones", style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(collections) { collection ->          // ← usa la lista del StateFlow
                    Card(modifier = Modifier.aspectRatio(1f)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(collection.songTitle, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialUsername = currentUsername,
            initialBio      = userBio,
            onDismiss = { showEditDialog = false },
            onSave = { newUsername, newBio ->
                currentUsername          = newUsername
                userBio                  = newBio
                viewModel.loggedUsername.value = newUsername
                viewModel.loggedBio.value      = newBio
                showEditDialog = false
            }
        )
    }
}

@Composable
fun EditProfileDialog(
    initialUsername: String,
    initialBio: String,
    onDismiss: () -> Unit,
    onSave: (username: String, bio: String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var bio      by remember { mutableStateOf(initialBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Perfil") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("Cuéntanos algo sobre ti...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, bio) },
                enabled = username.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─────────────────────────────────────────────────────────────
// PANTALLA SETTINGS
// ─────────────────────────────────────────────────────────────
@Composable
fun Pantalla(modifier: Modifier = Modifier, onLogout: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Newsick", style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Neon Music Night", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Configuración y Feedback de la aplicación.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("marcosqh17@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Feedback sobre Newsick")
                putExtra(Intent.EXTRA_TEXT, "Hola,\n\nTengo el siguiente feedback:\n\n")
            }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No se encontró una app de correo", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Filled.Email, contentDescription = "Enviar feedback")
            Spacer(Modifier.width(8.dp))
            Text("Enviar Feedback")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
            Spacer(Modifier.width(8.dp))
            Text("Cerrar Sesión")
        }

        Spacer(modifier = Modifier.height(24.dp))
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) { "?" }

        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}