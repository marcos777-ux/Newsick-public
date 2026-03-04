package com.makro17.newsick

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════
// DETALLE DE CANCIÓN
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    trackId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var photos        by remember { mutableStateOf<List<PhotoResponse>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }
    var song          by remember { mutableStateOf<SongPostEntity?>(null) }
    var photoToDelete by remember { mutableStateOf<PhotoResponse?>(null) }

    // Reproductor iTunes
    var previewUrl  by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying   by remember { mutableStateOf(false) }
    var playerReady by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf(false) }

    val myId = viewModel.loggedUserId.value

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null }
    }

    // Carga inicial
    LaunchedEffect(trackId) {
        isLoading = true
        song      = viewModel.getSongPost(trackId)
        photos    = viewModel.getMixedPhotos(trackId)

        trackId.toLongOrNull()?.let { numId ->
            try {
                val res = withContext(Dispatchers.IO) { ItunesRetrofit.api.lookupTrack(numId) }
                previewUrl = res.results.firstOrNull()?.previewUrl
            } catch (_: Exception) {}
        }
        isLoading = false
    }

    // Preparar MediaPlayer
    LaunchedEffect(previewUrl) {
        val url = previewUrl ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener { playerReady = true }
                    setOnCompletionListener { isPlaying = false }
                    setOnErrorListener { _, _, _ -> playerError = true; false }
                    prepareAsync()
                    mediaPlayer = this
                }
            } catch (_: Exception) { playerError = true }
        }
    }

    // Confirmación de borrado
    photoToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title   = { Text("Eliminar foto") },
            text    = { Text("¿Seguro que quieres eliminar esta foto? No se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePhoto(p.id) { ok ->
                            if (ok) {
                                photos = photos.filter { it.id != p.id }
                                // ✅ Si no quedan fotos, volver atrás automáticamente
                                if (photos.isEmpty()) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    onBack()  // ← Volver al perfil
                                }
                            }
                        }
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(song?.trackName ?: "") },
                navigationIcon = {
                    IconButton(onClick = {
                        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // ── Cabecera: portada + play/pause ─────────────
            song?.let { s ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

                        Box(Modifier.size(90.dp)) {
                            AsyncImage(s.artworkUrl, null,
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop)

                            if (previewUrl != null && !playerError) {
                                Surface(
                                    modifier = Modifier.align(Alignment.Center).size(38.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    tonalElevation = 4.dp
                                ) {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        if (!playerReady) {
                                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    val mp = mediaPlayer ?: return@IconButton
                                                    if (isPlaying) { mp.pause(); isPlaying = false }
                                                    else { mp.start(); isPlaying = true }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    if (isPlaying) Icons.Default.Pause
                                                    else Icons.Default.PlayArrow,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.trackName, style = MaterialTheme.typography.titleMedium)
                            Text(s.artistName, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            when {
                                playerError           -> Text("Preview no disponible",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                previewUrl == null && !isLoading -> Text("Sin preview de iTunes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                isPlaying             -> Text("▶ Reproduciendo (30 s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                playerReady           -> Text("Pulsa ▶ para escuchar 30 s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${photos.size} foto(s) · ${photos.map { it.username }.distinct().size} persona(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Grid de fotos ──────────────────────────────
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                photos.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null,
                            Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Aún no hay fotos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    contentPadding        = PaddingValues(vertical = 8.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoCard(
                            photo     = photo,
                            isOwn     = photo.userId == myId,
                            onDelete  = { photoToDelete = photo }
                        )
                    }
                }
            }
        }
    }
}

// ── Tarjeta de foto ───────────────────────────────────────

@Composable
private fun PhotoCard(
    photo: PhotoResponse,
    isOwn: Boolean,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.aspectRatio(1f)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                photo.photoUri, null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Badge con nombre
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, null,
                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(photo.username, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f))
                }
            }

            // Botón borrar — solo en fotos propias
            if (isOwn) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(30.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                ) {
                    IconButton(onClick = onDelete, Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.DeleteOutline, "Eliminar",
                            Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
