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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.makro17.newsick.ui.theme.CustomGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- MODELOS DE DATOS ---
data class MusicItem(
    val id: Int,
    val title: String,
    val artist: String,
    val description: String,
    val genre: String,
    var isFavorite: Boolean = false
)

data class Comment(val author: String, val text: String)

data class UserPost(val id: Int, val userName: String, val imageUrl: String, val comment: String)
data class FriendCollection(val id: Int, val songTitle: String, val friendNames: List<String>)

// --- VIEWMODEL ---
class MainViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Estado para las barras de búsqueda
    var userSearchQuery = mutableStateOf("")
    var mapSearchQuery = mutableStateOf("")

    // Datos simulados
    val items = mutableStateListOf(
        MusicItem(1, "Neon Nights", "CyberPunks", "Synthwave", "Electronic"),
        MusicItem(2, "Jazz Café", "Blue Note", "Jazz", "Jazz"),
        MusicItem(3, "Heavy Code", "Compilers", "Metal", "Metal"),
        MusicItem(4, "Lo-Fi Study", "Chill Hop", "Beats", "Lo-Fi"),
        MusicItem(5, "Summer Vibes", "Sun", "Pop", "Pop"),
        MusicItem(6, "Night Drive", "Kavinsky", "Synth", "Electronic")
    )

    // Simulacion de posts de otros usuarios para una canción
    val socialPosts = listOf(
        UserPost(1, "Ana_99", "", "Vibing en la playa 🌴"),
        UserPost(2, "CarlosDev", "", "Coding session intense 💻"),
        UserPost(3, "LuisaArt", "", "Inspiración nocturna 🌙")
    )

    // Colecciones de amigos en el perfil
    val friendCollections = mutableStateListOf(
        FriendCollection(1, "Neon Nights", listOf("Ana", "Pedro", "Tú")),
        FriendCollection(2, "Despacito", listOf("Luis", "Tú"))
    )

    init {
        viewModelScope.launch {
            delay(1000)
            _isLoading.value = false
        }
    }

    // CORREGIDO: Añadida función necesaria para cambiar favoritos
    fun toggleFavorite(item: MusicItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = items[index].copy(isFavorite = !items[index].isFavorite)
        }
    }

    fun addFriendCollection() {
        friendCollections.add(FriendCollection(3, "New Jam", listOf("Tú", "Nuevo Amigo")))
    }
}

// --- ACTIVIDAD PRINCIPAL ---
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }
        enableEdgeToEdge()

        setContent {
            // Usamos MaterialTheme por defecto si NewsickTheme no está definido externamente
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                // Cálculo del tamaño de ventana para diseño adaptativo
                val windowSize = calculateWindowSizeClass(this)
                NewsickApp(windowSize.widthSizeClass, viewModel)
            }
        }
    }
}

// --- ESTRUCTURA DE NAVEGACIÓN Y ADAPTABILIDAD ---
@Composable
fun NewsickApp(windowSize: WindowWidthSizeClass, viewModel: MainViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                // 1. IZQUIERDA: Buscador / Social
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Descubrir") },
                    label = { Text("Social") },
                    selected = navController.currentDestination?.route == "search",
                    onClick = { navController.navigate("search") }
                )
                // 2. CENTRO: Mapa
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = "Mapa") },
                    label = { Text("Mapa") },
                    selected = navController.currentDestination?.route == "map",
                    onClick = { navController.navigate("map") }
                )
                // 3. DERECHA: Perfil
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
            // RUTA 1: BUSCADOR Y PORTADAS
            composable("search") {
                SocialSearchScreen(viewModel) { songId ->
                    navController.navigate("feed/$songId")
                }
            }
            // DETALLE DE FOTOS (Al pulsar una portada)
            composable("feed/{songId}") {
                SocialFeedScreen()
            }

            // RUTA 2: MAPA
            composable("map") {
                MapScreen(viewModel)
            }

            // RUTA 3: PERFIL
            composable("profile") {
                UserProfileScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate("settings") }
                )
            }

            // CONFIGURACION (FEEDBACK)
            composable("settings") {
                Pantalla() // Reusamos la pantalla de Feedback/About
            }
        }
    }
}

// --- PANTALLAS (SCREENS) ---

// 1. PANTALLA IZQUIERDA: Buscador y Portadas
@Composable
fun SocialSearchScreen(viewModel: MainViewModel, onCoverClick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Barra de búsqueda (Usuarios)
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

        // Grid de Portadas
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.items) { item ->
                // Portada simulada
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
            // Espacio extra para scroll infinito simulado
            items(10) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.2f))
                )
            }
        }
    }
}

// Sub-pantalla: Feed de fotos (al pulsar portada)
@Composable
fun SocialFeedScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Fotos de la comunidad", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(5) { index ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    // Header del post
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Usuario_$index", style = MaterialTheme.typography.labelLarge)
                    }
                    // Foto simulada
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                    // Pie del post
                    Text(
                        text = "Escuchando este temazo 🎶",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// 2. PANTALLA CENTRAL: Mapa
@Composable
fun MapScreen(viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo simulando mapa
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE3F2FD)), // Azul mapa claro
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Place, null, modifier = Modifier.size(64.dp), tint = Color.Red)
            Text("Mapa de Eventos", color = Color.Gray)
        }

        // Barra de búsqueda flotante arriba
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.mapSearchQuery.value,
                onValueChange = { viewModel.mapSearchQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
                placeholder = { Text("Buscar ciudad o evento...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

// 3. PANTALLA DERECHA: Perfil + Configuración
@Composable
fun UserProfileScreen(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mi Perfil", style = MaterialTheme.typography.headlineMedium)
                // Botón Configuración (Feedback)
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Configuración", modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)) {
            // Cabecera de usuario
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Makro17", style = MaterialTheme.typography.titleLarge)
                    Text("Melómano Experto", color = CustomGold)
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.addFriendCollection() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir canción")
            }

            Spacer(Modifier.height(24.dp))
            Text("Canciones", style = MaterialTheme.typography.titleMedium)

            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(viewModel.friendCollections) { collection ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Miniaturas de fotos de amigos (simuladas)
                            Row(modifier = Modifier.weight(1f)) {
                                collection.friendNames.take(3).forEach { _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color.Gray)
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(2f)) {
                                Text(collection.songTitle, fontWeight = FontWeight.Bold)
                                Text("Con: ${collection.friendNames.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PANTALLA DE FEEDBACK (SETTINGS) ---
@Composable
fun Pantalla(modifier: Modifier = Modifier) {
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

        Button(onClick = {
            val email = "marcosqh17@gmail.com"
            val subject = "Feedback sobre $appName"
            val body = "Hola, tengo el siguiente feedback..."

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No se encontró app de correo", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Filled.Email, contentDescription = "contacto")
            Spacer(Modifier.width(8.dp))
            Text("Enviar Feedback")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appVersion,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}