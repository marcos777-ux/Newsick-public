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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.makro17.newsick.ui.theme.CustomAlert
import com.makro17.newsick.ui.theme.CustomGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- COLORES PERSONALIZADOS PARA EL TEMA ---
val CustomGold = Color(0xFFFFD700)
val CustomAlert = Color(0xFFD32F2F)

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

// --- VIEWMODEL ---
class MainViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Estado del buscador
    var searchQuery by mutableStateOf("")

    // Datos simulados
    private val _items = mutableStateListOf(
        MusicItem(1, "Neon Nights", "CyberPunks", "Synthwave puro para programar.", "Electronic"),
        MusicItem(2, "Jazz Cafe", "Blue Note Trio", "Jazz suave para relajarse.", "Jazz"), // Quitamos acento para facilitar url
        MusicItem(3, "Heavy Code", "The Compilers", "Metal progresivo intenso.", "Metal"),
        MusicItem(4, "Lo-Fi Study", "Chill Hop", "Beats para estudiar.", "Lo-Fi")
    )
    val items: List<MusicItem> get() = _items

    // Lista filtrada por búsqueda (insensible a mayúsculas y lugar)
    val filteredItems: List<MusicItem>
        get() = if (searchQuery.isEmpty()) _items else _items.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
        }

    val favorites = derivedStateOf { _items.filter { it.isFavorite } }
    val comments = mutableStateListOf<Comment>()

    init {
        viewModelScope.launch {
            delay(1000)
            _isLoading.value = false
            comments.add(Comment("User1", "Increíble tema!"))
            comments.add(Comment("User2", "Necesito más bajo."))
        }
    }

    fun toggleFavorite(item: MusicItem) {
        val index = _items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            _items[index] = _items[index].copy(isFavorite = !_items[index].isFavorite)
        }
    }

    fun addComment(text: String) {
        comments.add(Comment("Me", text))
    }

    fun updateSearch(query: String) {
        searchQuery = query
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
            MaterialTheme(colorScheme = darkColorScheme()) { // Tema oscuro por defecto para estilo Neon
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
    // Determinar si es pantalla expandida (Tablet/Landscape)
    val isExpanded = windowSize == WindowWidthSizeClass.Expanded || windowSize == WindowWidthSizeClass.Medium

    Row(modifier = Modifier.fillMaxSize()) {
        // 1. MENÚ DE NAVEGACIÓN (Rail para Expanded, BottomBar dentro de Scaffold para Compact)
        if (isExpanded) {
            NewsickNavRail(navController)
        }

        Scaffold(
            bottomBar = {
                if (!isExpanded) {
                    NewsickBottomBar(navController)
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "ElementList", // Ruta inicial
                modifier = Modifier.padding(innerPadding)
            ) {
                // RUTA 1: LISTA DE ELEMENTOS (HOME)
                composable("ElementList") {
                    if (isExpanded) {
                        // MODO EXPANDIDO: VISTA DIVIDIDA
                        SplitScreen(viewModel)
                    } else {
                        // MODO COMPACTO: SOLO LISTA CON BUSCADOR
                        ElemListScreen(
                            items = viewModel.filteredItems,
                            searchQuery = viewModel.searchQuery,
                            onSearchChange = { viewModel.updateSearch(it) },
                            onItemClick = { item -> navController.navigate("detail/${item.title}") }, // Navegación por NOMBRE
                            onFavClick = { viewModel.toggleFavorite(it) }
                        )
                    }
                }

                // RUTA 2: DETALLES ELEMENTO (POR NOMBRE)
                composable("detail/{itemName}") { backStackEntry ->
                    val itemName = backStackEntry.arguments?.getString("itemName")
                    // Buscamos por nombre como pide el enunciado
                    val item = viewModel.items.find { it.title == itemName }

                    if (item != null) {
                        DetailItemScreen(item) { viewModel.toggleFavorite(item) }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Elemento no encontrado") }
                    }
                }

                // RUTA 3: FAVORITOS
                composable("FavList") {
                    FavListScreen(
                        favs = viewModel.favorites.value,
                        onRemove = { viewModel.toggleFavorite(it) }, // Dialog manejado dentro de la Screen
                        onItemClick = { navController.navigate("favDetail/${it.title}") } // Navegación por NOMBRE
                    )
                }

                // RUTA 4: DETALLE FAVORITOS (CON COMENTARIOS)
                composable("favDetail/{itemName}") { backStackEntry ->
                    val itemName = backStackEntry.arguments?.getString("itemName")
                    val item = viewModel.items.find { it.title == itemName }
                    if (item != null) {
                        DetailFavScreen(item, viewModel.comments) { comment -> viewModel.addComment(comment) }
                    }
                }

                // RUTA 5: PERFIL
                composable("Profile") { ProfileScreen() }

                // RUTA 6: ABOUT
                composable("About") { LapanTalla() }
            }
        }
    }
}

// --- MENÚS DE NAVEGACIÓN ---

@Composable
fun NewsickBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, "Inicio") },
            label = { Text("Inicio") },
            selected = currentRoute == "ElementList",
            onClick = { navController.navigate("ElementList") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, "Favoritos") },
            label = { Text("Favoritos") },
            selected = currentRoute == "FavList",
            onClick = { navController.navigate("FavList") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, "Perfil") },
            label = { Text("Perfil") },
            selected = currentRoute == "Profile",
            onClick = { navController.navigate("Profile") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Info, "Info") },
            label = { Text("About") },
            selected = currentRoute == "About",
            onClick = { navController.navigate("About") }
        )
    }
}

@Composable
fun NewsickNavRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationRail {
        NavigationRailItem(
            icon = { Icon(Icons.Default.Home, "Inicio") },
            label = { Text("Inicio") },
            selected = currentRoute == "ElementList",
            onClick = { navController.navigate("ElementList") }
        )
        Spacer(Modifier.height(8.dp))
        NavigationRailItem(
            icon = { Icon(Icons.Default.Favorite, "Favoritos") },
            label = { Text("Favoritos") },
            selected = currentRoute == "FavList",
            onClick = { navController.navigate("FavList") }
        )
        Spacer(Modifier.height(8.dp))
        NavigationRailItem(
            icon = { Icon(Icons.Default.Person, "Perfil") },
            label = { Text("Perfil") },
            selected = currentRoute == "Profile",
            onClick = { navController.navigate("Profile") }
        )
        Spacer(Modifier.height(8.dp))
        NavigationRailItem(
            icon = { Icon(Icons.Default.Info, "Info") },
            label = { Text("About") },
            selected = currentRoute == "About",
            onClick = { navController.navigate("About") }
        )
    }
}

// --- PANTALLAS (SCREENS) ---

// 1. ElemListScreen (CON BÚSQUEDA)
@Composable
fun ElemListScreen(
    items: List<MusicItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onItemClick: (MusicItem) -> Unit,
    onFavClick: (MusicItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ZONA DE BÚSQUEDA
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            label = { Text("Buscar...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Text(
                    "Catálogo Musical",
                    style = MaterialTheme.typography.headlineMedium,
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
        GenreChip(genre = item.genre)
        Spacer(modifier = Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.headlineLarge)
        Text(item.artist, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(item.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        // BOTÓN FAVORITO CON CAMBIO DE ESTADO
        Button(
            onClick = onFavClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (item.isFavorite) CustomGold else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (item.isFavorite) "Añadido a Favoritos" else "Añadir a Favoritos")
        }
    }
}

// 3. FavListScreen (CON DIALOGO DE BORRADO)
@Composable
fun FavListScreen(favs: List<MusicItem>, onRemove: (MusicItem) -> Unit, onItemClick: (MusicItem) -> Unit) {
    // Estado para controlar el diálogo
    var itemToDelete by remember { mutableStateOf<MusicItem?>(null) }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar Favorito") },
            text = { Text("¿Estás seguro de que deseas eliminar '${itemToDelete?.title}' de favoritos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { onRemove(it) }
                        itemToDelete = null
                    }
                ) { Text("Eliminar", color = CustomAlert) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Tu Colección", style = MaterialTheme.typography.headlineLarge, color = CustomGold)
        }
        if (favs.isEmpty()) {
            item { Text("No tienes favoritos aún.", modifier = Modifier.padding(top = 16.dp)) }
        }
        items(favs) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onItemClick(item) }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.artist, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { itemToDelete = item }) { // Dispara el Dialog
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = CustomAlert)
                }
            }
        }
    }
}

// 4. DetailFavScreen
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
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp))
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
            text = { TextField(value = newCommentText, onValueChange = { newCommentText = it }, label = { Text("Tu opinión") }) },
            confirmButton = {
                Button(onClick = {
                    if(newCommentText.isNotEmpty()) {
                        onAddComment(newCommentText)
                        newCommentText = ""
                        showDialog = false
                    }
                }) { Text("Publicar") }
            }
        )
    }
}

// 5. ProfileScreen (LOGIN/LOGOUT)
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
                Text("Cerrar Sesión") // Texto cambia
            }
        } else {
            Text("Bienvenido, Invitado", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { isLoggedIn = true }) {
                Text("Iniciar Sesión") // Texto cambia
            }
        }
    }
}

// 6. AboutScreen (LapanTalla)
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

// --- LOGICA PANTALLA DIVIDIDA (Expandido) ---
@Composable
fun SplitScreen(viewModel: MainViewModel) {
    // Estado local para saber qué item se muestra en el panel derecho
    var selectedItem by remember { mutableStateOf<MusicItem?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Panel Izquierdo (Lista) - 40% del ancho
        Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
            ElemListScreen(
                items = viewModel.filteredItems, // Usa la lista filtrada
                searchQuery = viewModel.searchQuery,
                onSearchChange = { viewModel.updateSearch(it) },
                onItemClick = { selectedItem = it }, // En expandido, no navega, solo selecciona
                onFavClick = { viewModel.toggleFavorite(it) }
            )
        }

        // Separador vertical
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

// --- COMPONENTES UI AUXILIARES ---
@Composable
fun MusicCard(item: MusicItem, onClick: () -> Unit, onFavClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        border = BorderStroke(1.dp, if (item.isFavorite) CustomGold else MaterialTheme.colorScheme.outlineVariant),
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
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, // Cambio de Icono
                    contentDescription = "Fav",
                    tint = if (item.isFavorite) CustomGold else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

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