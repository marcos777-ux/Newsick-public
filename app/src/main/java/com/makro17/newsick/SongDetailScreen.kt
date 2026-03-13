// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════
// DETALLE DE CANCIÓN — álbum de fotos con swipe fullscreen
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    trackId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var photos          by remember { mutableStateOf<List<PhotoResponse>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(true) }
    var song            by remember { mutableStateOf<SongPostEntity?>(null) }
    var photoToDelete   by remember { mutableStateOf<PhotoResponse?>(null) }
    var fullscreenIndex by remember { mutableStateOf<Int?>(null) }

    var previewUrl  by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying   by remember { mutableStateOf(false) }
    var playerReady by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf(false) }

    val myId = viewModel.loggedUserId.value

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null }
    }

    // Carga inicial — fotos ordenadas newest-first por el backend
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

    photoToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title   = { Text("Eliminar foto") },
            text    = { Text("¿Seguro que quieres eliminar esta foto?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePhoto(p.id, trackId) { success ->
                            if (success) {
                                photos = photos.filter { it.id != p.id }
                                if (photos.isEmpty()) {
                                    mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
                                    onBack()
                                }
                            }
                        }
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") } }
        )
    }

    // Visor fullscreen con swipe horizontal
    fullscreenIndex?.let { startPage ->
        PhotoFullscreenViewerDetail(
            photos      = photos,
            initialPage = startPage,
            onDismiss   = { fullscreenIndex = null }
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
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            // Cabecera: portada + info + play
            item {
                song?.let { s ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(80.dp)) {
                                AsyncImage(
                                    s.artworkUrl, null,
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                if (previewUrl != null && !playerError) {
                                    Surface(
                                        modifier       = Modifier.align(Alignment.Center).size(34.dp),
                                        shape          = CircleShape,
                                        color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        tonalElevation = 4.dp
                                    ) {
                                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                                            if (!playerReady) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
                                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.trackName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text(s.artistName, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${photos.size} foto(s) · ${photos.map { it.username }.distinct().size} persona(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Fotos en lista vertical, más recientes primero
            if (photos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, null,
                                Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Aún no hay fotos",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                    PhotoListItem(
                        photo    = photo,
                        isOwn    = photo.userId == myId,
                        onDelete = { photoToDelete = photo },
                        onClick  = { fullscreenIndex = index }
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

// ── Foto en lista: ancho completo, tap → fullscreen ───────

@Composable
private fun PhotoListItem(
    photo: PhotoResponse,
    isOwn: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model              = NewsickRetrofit.absoluteUrl(photo.photoUri),
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AccountCircle, null,
                modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(photo.username, style = MaterialTheme.typography.labelMedium,
                color = Color.White, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(timeAgo(photo.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f))
        }
        if (isOwn) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp),
                shape    = CircleShape,
                color    = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
            ) {
                IconButton(onClick = onDelete, Modifier.fillMaxSize()) {
                    Icon(Icons.Default.DeleteOutline, "Eliminar",
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

// ── Visor a pantalla completa con swipe horizontal ────────

@Composable
private fun PhotoFullscreenViewerDetail(
    photos: List<PhotoResponse>,
    initialPage: Int,
    onDismiss: () -> Unit
) {
    val pagerState   = rememberPagerState(initialPage) { photos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model              = NewsickRetrofit.absoluteUrl(photos[page].photoUri),
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit
                )
            }
            // Barra superior: cerrar + contador
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            // Barra inferior: usuario + tiempo
            currentPhoto?.let { photo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, null,
                        modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(photo.username, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White, modifier = Modifier.weight(1f))
                    Text(timeAgo(photo.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}