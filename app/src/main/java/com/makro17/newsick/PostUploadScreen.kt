package com.makro17.newsick

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════
// FLUJO DE SUBIDA DE PUBLICACIÓN
// Paso 0 → buscar canción (iTunes)
// Paso 1 → seleccionar fotos
// ══════════════════════════════════════════════════════════

@Composable
fun PostUploadScreen(
    viewModel: MainViewModel,
    onPostCreated: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ItunesTrack>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<ItunesTrack?>(null) }
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    var isUploading by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Selector múltiple de imágenes (hasta 10)
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        uris.forEach { uri ->
            // Persiste el permiso de lectura para que la URI siga siendo válida
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        selectedPhotos.addAll(uris)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (step == 0) onBack() else step = 0 }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                }
                Text(
                    text = if (step == 0) "Buscar canción" else "Añadir fotos",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                0 -> SongSearchStep(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it; isSearching = it.length >= 2 },
                    results = searchResults,
                    isSearching = isSearching,
                    onResultsReady = { searchResults = it; isSearching = false },
                    onTrackSelected = { track -> selectedTrack = track; step = 1 }
                )
                1 -> PhotoSelectionStep(
                    track = selectedTrack!!,
                    selectedPhotos = selectedPhotos,
                    isUploading = isUploading,
                    onAddPhotos = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemovePhoto = { selectedPhotos.remove(it) },
                    onConfirm = {
                        isUploading = true
                        val t = selectedTrack!!
                        viewModel.createPost(
                            trackId    = t.trackId.toString(),
                            trackName  = t.trackName,
                            artistName = t.artistName,
                            artworkUrl = t.artworkUrl300,
                            photoUris  = selectedPhotos.map { it.toString() }
                        )
                        onPostCreated()
                    }
                )
            }
        }
    }
}

// ── Paso 0: búsqueda de canción ───────────────────────────

@Composable
private fun SongSearchStep(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ItunesTrack>,
    isSearching: Boolean,
    onResultsReady: (List<ItunesTrack>) -> Unit,
    onTrackSelected: (ItunesTrack) -> Unit
) {
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(query) {
        searchJob?.cancel() // Cancelar búsqueda anterior

        if (query.length >= 2) {
            searchJob = launch {
                delay(400) // Debounce
                try {
                    val res = withContext(Dispatchers.IO) {
                        ItunesRetrofit.api.searchMusic(query)
                    }
                    // Filtrar solo canciones válidas
                    val validResults = res.results.filter {
                        it.trackId != 0L && it.trackName.isNotBlank()
                    }
                    onResultsReady(validResults)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResultsReady(emptyList())
                }
            }
        } else {
            onResultsReady(emptyList())
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Nombre de canción o artista...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (isSearching && query.length >= 2) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (query.length >= 2 && results.isEmpty()) {
            Text(
                "No se encontraron resultados",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results) { track ->
                    TrackResultItem(track = track, onClick = { onTrackSelected(track) })
                }
            }
        }
    }
}

@Composable
private fun TrackResultItem(track: ItunesTrack, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUrl100,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.trackName, style = MaterialTheme.typography.titleSmall)
                Text(
                    track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                track.collectionName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// ── Paso 1: selección de fotos ────────────────────────────

@Composable
private fun PhotoSelectionStep(
    track: ItunesTrack,
    selectedPhotos: List<Uri>,
    isUploading: Boolean,
    onAddPhotos: () -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    onConfirm: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Canción seleccionada
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = track.artworkUrl300,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(track.trackName, style = MaterialTheme.typography.titleMedium)
                        Text(track.artistName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Botón añadir fotos
        item {
            OutlinedButton(onClick = onAddPhotos, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir fotos (máx. 10)")
            }
        }

        // Contador
        if (selectedPhotos.isNotEmpty()) {
            item {
                Text(
                    "${selectedPhotos.size} foto(s) seleccionada(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Previsualización de fotos
        items(selectedPhotos) { uri ->
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { onRemovePhoto(uri) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Cancel, "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Botón publicar
        item {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedPhotos.isNotEmpty() && !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
