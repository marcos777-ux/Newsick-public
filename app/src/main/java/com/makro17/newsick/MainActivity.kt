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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.makro17.newsick.ui.theme.CustomAlert
import com.makro17.newsick.ui.theme.CustomGold
import com.makro17.newsick.ui.theme.NewsickTheme
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

// --- VIEWMODEL SIMULADO ---
class MainViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Datos simulados
    val items = mutableStateListOf(
        MusicItem(1, "Neon Nights", "CyberPunks", "Synthwave puro para programar.", "Electronic"),
        MusicItem(2, "Jazz Café", "Blue Note Trio", "Jazz suave para relajarse.", "Jazz"),
        MusicItem(3, "Heavy Code", "The Compilers", "Metal progresivo intenso.", "Metal"),
        MusicItem(4, "Lo-Fi Study", "Chill Hop", "Beats para estudiar.", "Lo-Fi")
    )

    val favorites = derivedStateOf { items.filter { it.isFavorite } }
    val comments = mutableStateListOf<Comment>()

    init {
        viewModelScope.launch {
            delay(1000)
            _isLoading.value = false
            // Comentarios dummy
            comments.add(Comment("User1", "Increíble tema!"))
            comments.add(Comment("User2", "Necesito más bajo."))
        }
    }

    fun toggleFavorite(item: MusicItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = items[index].copy(isFavorite = !items[index].isFavorite)
        }
    }

    fun addComment(text: String) {
        comments.add(Comment("Me", text))
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
            NewsickTheme {
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
    // Determinar si usamos layout expandido (Tablet/Horizontal)
    val isExpanded = windowSize == WindowWidthSizeClass.Expanded || windowSize == WindowWidthSizeClass.Medium

    Scaffold(
        bottomBar = {
            if (!isExpanded) { // BottomBar solo en móvil compacto
                BottomAppBar {
                    IconButton(onClick = { navController.navigate("list") }) {
                        Icon(Icons.Default.Home, "Inicio")
                    }
                    IconButton(onClick = { navController.navigate("favs") }) {
                        Icon(Icons.Default.Favorite, "Favoritos")
                    }
                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(Icons.Default.Person, "Perfil")
                    }
                    IconButton(onClick = { navController.navigate("about") }) {
                        Icon(Icons.Default.Info, "About")
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("list") {
                if (isExpanded) {
                    // LAYOUT ADAPTATIVO: LISTA + DETALLE
                    SplitScreen(viewModel)
                } else {
                    // LAYOUT COMPACTO: SOLO LISTA
                    ElemListScreen(
                        items = viewModel.items,
                        onItemClick = { item -> navController.navigate("detail/${item.id}") },
                        onFavClick = { viewModel.toggleFavorite(it) }
                    )
                }
            }
            composable("detail/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull()
                val item = viewModel.items.find { it.id == itemId }
                item?.let {
                    DetailItemScreen(it) { viewModel.toggleFavorite(it) }
                }
            }
            composable("favs") {
                FavListScreen(
                    favs = viewModel.favorites.value,
                    onRemove = { viewModel.toggleFavorite(it) },
                    onItemClick = { navController.navigate("favDetail/${it.id}") }
                )
            }
            composable("favDetail/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toIntOrNull()
                val item = viewModel.items.find { it.id == itemId }
                item?.let {
                    DetailFavScreen(it, viewModel.comments) { comment -> viewModel.addComment(comment) }
                }
            }
            composable("profile") { ProfileScreen() }
            composable("about") { LapanTalla() }
        }
    }
}

// --- PANTALLAS (SCREENS) ---

// 1. ElemListScreen
@Composable
fun ElemListScreen(
    items: List<MusicItem>,
    onItemClick: (MusicItem) -> Unit,
    onFavClick: (MusicItem) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "Novedades",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(items) { item ->
            MusicCard(
                item = item,
                onClick = { onItemClick(item) },
                onFavClick = { onFavClick(item) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// 2. DetailItemScreen
@Composable
fun DetailItemScreen(item: MusicItem, onFavClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.DarkGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
        }
        Spacer(modifier = Modifier.height(16.dp))
        GenreChip(genre = item.genre) // Componente personalizado 2
        Spacer(modifier = Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.headlineLarge)
        Text(item.artist, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(item.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onFavClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (item.isFavorite) CustomGold else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (item.isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos")
        }
    }
}

// 3. FavListScreen
@Composable
fun FavListScreen(favs: List<MusicItem>, onRemove: (MusicItem) -> Unit, onItemClick: (MusicItem) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Tu Colección", style = MaterialTheme.typography.headlineLarge, color = CustomGold)
        }
        items(favs) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onItemClick(item) }
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.artist, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { onRemove(item) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = CustomAlert)
                }
            }
        }
    }
}

// 4. DetailFavScreen (Comentarios + FAB)
@Composable
fun DetailFavScreen(item: MusicItem, comments: List<Comment>, onAddComment: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Default.Add, "Comentar")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Debate sobre: ${item.title}", style = MaterialTheme.typography.headlineMedium)
            Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn {
                items(comments) { comment ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(comment.author, style = MaterialTheme.typography.labelSmall, color = CustomGold)
                            Text(comment.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nuevo Comentario") },
            text = { TextField(value = newCommentText, onValueChange = { newCommentText = it }) },
            confirmButton = {
                Button(onClick = {
                    onAddComment(newCommentText)
                    newCommentText = ""
                    showDialog = false
                }) { Text("Publicar") }
            }
        )
    }
}

// 5. ProfileScreen
@Composable
fun ProfileScreen() {
    var isLoggedIn by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        if (isLoggedIn) {
            Text("Usuario: Makro17", style = MaterialTheme.typography.headlineMedium)
            Text("Nivel: Melómano Experto", color = CustomGold)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { isLoggedIn = false }, colors = ButtonDefaults.buttonColors(containerColor = CustomAlert)) {
                Text("Cerrar Sesión")
            }
        } else {
            Text("Bienvenido, Invitado", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { isLoggedIn = true }) {
                Text("Iniciar Sesión")
            }
        }
    }
}

// 6. AboutScreen (LapanTalla) - Tal cual estaba, solo aseguramos que use el tema


// --- COMPONENTES PERSONALIZADOS ---

/**
 * COMPONENTE 1: MusicCard
 * Tarjeta personalizada con borde neón y estilo específico.
 */
@Composable
fun MusicCard(item: MusicItem, onClick: () -> Unit, onFavClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        // Borde verde neón si es favorito, gris si no
        border = BorderStroke(1.dp, if (item.isFavorite) CustomGold else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(item.title.first().toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.artist, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onFavClick) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Fav",
                    tint = if (item.isFavorite) CustomGold else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * COMPONENTE 2: GenreChip
 * Chip personalizado para mostrar categorías.
 */
@Composable
fun GenreChip(genre: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Text(
            text = genre.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

// --- LOGICA PANTALLA DIVIDIDA (Expandido/Medio) ---
@Composable
fun SplitScreen(viewModel: MainViewModel) {
    var selectedItem by remember { mutableStateOf<MusicItem?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Panel Izquierdo (Lista) - 40% del ancho
        Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
            ElemListScreen(
                items = viewModel.items,
                onItemClick = { selectedItem = it },
                onFavClick = { viewModel.toggleFavorite(it) }
            )
        }

        // Separador visual vertical (Este es el que dibuja la línea gris)
        Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Gray))

        // Panel Derecho (Detalle) - 60% del ancho
        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
            if (selectedItem != null) {
                DetailItemScreen(item = selectedItem!!) {
                    viewModel.toggleFavorite(selectedItem!!)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Selecciona un elemento de la lista", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                }
            }
        }
    }
}
@Composable
fun LapanTalla(modifier: Modifier = Modifier) {
    val appName = "Newsick"
    val appTheme = "Neon Music Night"
    val appDescription = "Explora los mejores ritmos con un estilo visual único y adaptativo."
    val appVersion = "v2.0 Neon"
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

        Icon(
            imageVector = Icons.Filled.Email,
            contentDescription = "contacto",
            modifier = Modifier
                .size(40.dp)
                .clickable {
                    val email = "marcosqh17@gmail.com"
                    val subject = "Feedback sobre $appName"
                    val body = """
                        Hola,
                        
                        Tengo el siguiente feedback sobre la versión $appVersion:
                        
                        (Escribe aquí...)
                    """.trimIndent()

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }

                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast
                            .makeText(context, "No se encontró app de correo", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
            tint = MaterialTheme.colorScheme.tertiary // Usamos el color Gold personalizado
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appVersion,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}