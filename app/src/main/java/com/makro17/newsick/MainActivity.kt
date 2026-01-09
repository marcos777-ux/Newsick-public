package com.makro17.newsick

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.makro17.newsick.ui.theme.CustomGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// --- 1. CAPA DE RED (NETWORKING) ---
// Define cómo son los datos que envías y recibes del servidor
// El username es opcional (?) porque en el Login no lo enviamos, solo en el Registro
data class AuthRequest(
    val email: String,
    val password: String,
    val username: String? = null
)
data class AuthResponse(val success: Boolean, val token: String?, val message: String?)

// Interfaz para comunicarse con TU servidor
interface ApiService {
    // Estas rutas deben existir en tu servidor backend
    @POST("/api/login.php")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("/api/register.php")
    suspend fun register(@Body request: AuthRequest): AuthResponse
}

// Objeto Singleton para crear la conexión
object RetrofitInstance {
    private const val BASE_URL = "https://jumiquihe68.s3.tnas2.link:477/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// --- 2. MODELOS DE DATOS DE LA APP ---
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

// --- 3. VIEWMODEL ---
class MainViewModel : androidx.lifecycle.ViewModel() {

    // Estado de carga inicial
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // --- ESTADO DE AUTENTICACIÓN ---
    var isLoggedIn = mutableStateOf(false) // ¿El usuario entró?
    var authError = mutableStateOf<String?>(null) // Errores de login
    var isRegistering = mutableStateOf(false) // ¿Está en pantalla de registro?
    // Estado para las barras de búsqueda (App Principal)
    var userSearchQuery = mutableStateOf("")
    var mapSearchQuery = mutableStateOf("")
    var needsUsername = mutableStateOf(false)

    // Datos simulados (Se cargarían tras el login)
    val items = mutableStateListOf(
        MusicItem(1, "Neon Nights", "CyberPunks", "Synthwave", "Electronic"),
        MusicItem(2, "Jazz Café", "Blue Note", "Jazz", "Jazz"),
        MusicItem(3, "Heavy Code", "Compilers", "Metal", "Metal"),
        MusicItem(4, "Lo-Fi Study", "Chill Hop", "Beats", "Lo-Fi"),
        MusicItem(5, "Summer Vibes", "Sun", "Pop", "Pop"),
        MusicItem(6, "Night Drive", "Kavinsky", "Synth", "Electronic")
    )

    val friendCollections = mutableStateListOf(
        FriendCollection(1, "Neon Nights", listOf("Ana", "Pedro", "Tú")),
        FriendCollection(2, "Despacito", listOf("Luis", "Tú"))
    )

    init {
        viewModelScope.launch {
            delay(1000)
            _isLoading.value = false
            // Aquí podrías comprobar si ya existe un token guardado en DataStore
            // para poner isLoggedIn.value = true automáticamente.
        }
    }

    // --- LÓGICA DE LOGIN / REGISTRO ---
    // LOGIN: Enviamos email y pass (username se queda como null)
    fun performLogin(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            try {
                // --- CÓDIGO REAL ACTIVADO ---
                // Nota: Asegúrate de usar Dispatchers.IO para red
                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.api.login(AuthRequest(email = email, password = pass))
                }

                if (response.success) {
                    isLoggedIn.value = true
                    // Opcional: Guardar response.token en DataStore/SharedPreferences aquí
                } else {
                    authError.value = response.message ?: "Error al iniciar sesión"
                }
            } catch (e: Exception) {
                // Útil para debugging: imprime el error en Logcat
                e.printStackTrace()
                authError.value = "Error de conexión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Haz lo mismo para performRegister...

    fun performRegister(email: String, pass: String, username: String) {
        viewModelScope.launch {
            // 1. Activar estado de carga (muestra el círculo giratorio)
            _isLoading.value = true
            authError.value = null

            try {
                // 2. Llamada real a la red (IO Thread)
                // Usamos withContext(Dispatchers.IO) para mover esto fuera del hilo principal
                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.api.register(AuthRequest(email, pass, username))
                }

                // 3. Verificar respuesta del servidor
                if (response.success) {
                    // ÉXITO: El servidor creó el usuario
                    println("Registro exitoso. Token: ${response.token}")

                    // Aquí podrías guardar el token en DataStore si quisieras persistencia
                    // Por ahora, solo dejamos pasar al usuario:
                    isLoggedIn.value = true
                } else {
                    // ERROR DEL SERVIDOR (ej: "Usuario ya existe")
                    authError.value = response.message ?: "Error desconocido al registrar"
                }

            } catch (e: Exception) {
                // 4. ERROR DE CONEXIÓN (ej: Servidor apagado, sin internet, timeout)
                e.printStackTrace() // Ver error exacto en Logcat
                authError.value = "Error de conexión: ${e.localizedMessage}"
            } finally {
                // 5. Desactivar estado de carga siempre (haya éxito o error)
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        isLoggedIn.value = false
        // Aquí borrarías el token guardado
    }

    fun addFriendCollection() {
        friendCollections.add(FriendCollection(3, "New Jam", listOf("Tú", "Nuevo Amigo")))
    }
}

// --- 4. ACTIVIDAD PRINCIPAL ---
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

                // DECISIÓN DE FLUJO: ¿Login o App Principal?
                if (viewModel.isLoggedIn.value) {
                    NewsickApp(windowSize.widthSizeClass, viewModel)
                } else {
                    AuthScreen(viewModel)
                }
            }
        }
    }
}

// --- 5. PANTALLA DE AUTENTICACIÓN (NUEVA) ---
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    // Estados locales para los campos de texto
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var chosenUsername by remember { mutableStateOf("") }

    // Estados observados desde el ViewModel
    val isRegistering = viewModel.isRegistering.value
    val needsUsername = viewModel.needsUsername.value // Debes añadir este booleano en tu ViewModel
    val isLoading = viewModel.isLoading.collectAsState().value
    val authError = viewModel.authError.value

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono dinámico según el paso
            Icon(
                imageVector = if (needsUsername) Icons.Default.AccountCircle else Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Título dinámico
            Text(
                text = when {
                    needsUsername -> "Configura tu perfil"
                    isRegistering -> "Crea tu cuenta"
                    else -> "Bienvenido a Newsick"
                },
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!needsUsername) {
                // --- PASO 1: LOGIN O REGISTRO INICIAL ---
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
                // --- PASO 2: NOMBRE DE USUARIO OBLIGATORIO (Solo tras registro) ---
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

            // Mensaje de error
            authError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de acción principal
            Button(
                onClick = {
                    if (needsUsername) {
                        // Finalizar registro con el nombre de usuario
                        viewModel.performRegister(email, password, chosenUsername)
                    } else if (isRegistering) {
                        // Validar correo/pass y pasar al siguiente paso
                        if (email.contains("@") && password.length >= 6) {
                            viewModel.needsUsername.value = true
                        } else {
                            viewModel.authError.value = "Introduce un correo válido y 6 caracteres mínimo"
                        }
                    } else {
                        // Login normal
                        viewModel.performLogin(email, password)
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
                            else -> "Iniciar Sesión"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón para alternar entre Login y Registro (Solo visible en paso 1)
            if (!needsUsername) {
                TextButton(onClick = {
                    viewModel.isRegistering.value = !isRegistering
                    viewModel.authError.value = null
                }) {
                    Text(if (isRegistering) "¿Ya tienes cuenta? Entra aquí" else "¿No tienes cuenta? Regístrate")
                }
            } else {
                // Botón para volver atrás si se equivocó de correo
                TextButton(onClick = { viewModel.needsUsername.value = false }) {
                    Text("Volver atrás")
                }
            }
        }
    }
}

// --- 6. ESTRUCTURA DE NAVEGACIÓN (APP PRINCIPAL) ---
@Composable
fun NewsickApp(windowSize: WindowWidthSizeClass, viewModel: MainViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Descubrir") },
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
            composable("feed/{songId}") {
                SocialFeedScreen()
            }
            composable("map") {
                MapScreen(viewModel)
            }
            composable("profile") {
                UserProfileScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                Pantalla(onLogout = { viewModel.logout() }) // Pasamos la función de logout
            }
        }
    }
}

// --- PANTALLAS PRINCIPALES (Sin cambios mayores, solo integración) ---

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
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
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
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
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

    // Datos de ejemplo del perfil (esto vendría del ViewModel)
    var userBio by remember { mutableStateOf("Amante de la música electrónica y el café.") }
    var currentUsername by remember { mutableStateOf("Marcos_Music") }
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
            // CABECERA
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp))
                    // Botón para cambiar foto (Solo visible aquí o en el diálogo)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentUsername, style = MaterialTheme.typography.titleLarge)
                    Text(userBio, style = MaterialTheme.typography.bodyMedium)
                }

                // BOTÓN EDITAR (Solo para el dueño)
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, "Editar Perfil")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Mis Canciones", style = MaterialTheme.typography.titleMedium)

            // CANCIONES EN DOS COLUMNAS (Igual que SocialSearchScreen)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.friendCollections) { collection ->
                    Card(modifier = Modifier.aspectRatio(1f)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(collection.songTitle, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
    // DIÁLOGO DE EDICIÓN
    if (showEditDialog) {
        EditProfileDialog(
            onDismiss = { showEditDialog = false },
            onSave = { /* Lógica para guardar en servidor */ }
        )
    }
}

@Composable
fun EditProfileDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Perfil") },
        text = {
            Column {
                OutlinedButton(onClick = { /* Abrir Galería */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cambiar Foto de Perfil")
                }
                OutlinedTextField(value = "Marcos_Music", onValueChange = {}, label = { Text("Nombre de Usuario") })

                Text("Género", modifier = Modifier.padding(top = 8.dp))
                Row {
                    RadioButton(selected = true, onClick = {})
                    Text("Masculino", modifier = Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = false, onClick = {})
                    Text("Femenino", modifier = Modifier.align(Alignment.CenterVertically))
                }

                OutlinedButton(onClick = { /* Mostrar DatePicker */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fecha de Nacimiento (Privada)")
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Guardar") } }
    )
}

// --- PANTALLA SETTINGS (Con Botón de Logout) ---
@Composable
fun Pantalla(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    val appName = "Newsick"
    val appTheme = "Neon Music Night"
    val appDescription = "Configuración y Feedback de la aplicación."
    val appVersion = "2.0"

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appTheme,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = appDescription,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // BOTÓN ENVIAR FEEDBACK (EMAIL REAL)
        Button(onClick = {
            val email = "marcosqh17@gmail.com"
            val subject = "Feedback sobre $appName"
            val body = "Hola,\n\nTengo el siguiente feedback sobre la aplicación:\n\n"

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "No se encontró una app de correo",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }) {
            Icon(Icons.Filled.Email, contentDescription = "Enviar feedback")
            Spacer(Modifier.width(8.dp))
            Text("Enviar Feedback")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BOTÓN CERRAR SESIÓN
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

        Text(
            text = appVersion,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}