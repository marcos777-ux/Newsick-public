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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
data class AuthRequest(val username: String, val password: String)
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
    fun performLogin(user: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            authError.value = null
            try {
                // --- CÓDIGO REAL ACTIVADO ---
                // Nota: Asegúrate de usar Dispatchers.IO para red
                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.api.login(AuthRequest(user, pass))
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

    fun performRegister(user: String, pass: String) {
        viewModelScope.launch {
            // 1. Activar estado de carga (muestra el círculo giratorio)
            _isLoading.value = true
            authError.value = null

            try {
                // 2. Llamada real a la red (IO Thread)
                // Usamos withContext(Dispatchers.IO) para mover esto fuera del hilo principal
                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.api.register(AuthRequest(user, pass))
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
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isRegistering = viewModel.isRegistering.value
    val isLoading = viewModel.isLoading.collectAsState().value

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
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isRegistering) "Crea tu cuenta" else "Bienvenido a Newsick",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Campos de texto
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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

            // Mensaje de error
            viewModel.authError.value?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botón Principal
            Button(
                onClick = {
                    if (isRegistering) {
                        viewModel.performRegister(username, password)
                    } else {
                        viewModel.performLogin(username, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(if (isRegistering) "Registrarse" else "Iniciar Sesión")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch entre Login y Registro
            TextButton(onClick = { viewModel.isRegistering.value = !isRegistering }) {
                Text(if (isRegistering) "¿Ya tienes cuenta? Entra aquí" else "¿No tienes cuenta? Regístrate")
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
            placeholder = { Text("Buscar usuario (@makro...)") },
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
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Usuario", style = MaterialTheme.typography.titleLarge)
                    Text("Melómano", color = CustomGold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.addFriendCollection() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir canción")
            }
            Spacer(Modifier.height(24.dp))
            Text("Mis Canciones", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(viewModel.friendCollections) { collection ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Text(collection.songTitle, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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