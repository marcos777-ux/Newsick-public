package com.makro17.newsick

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ══════════════════════════════════════════════════════════
// DETALLE DE CANCIÓN — fotos del usuario + sus amigos
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    trackId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // Fotos mezcladas desde la API (usuario + amigos)
    var mixedPhotos by remember { mutableStateOf<List<PhotoResponse>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    var song        by remember { mutableStateOf<SongPostEntity?>(null) }

    LaunchedEffect(trackId) {
        isLoading   = true
        song        = viewModel.getSongPost(trackId)
        mixedPhotos = viewModel.getMixedPhotos(trackId)
        isLoading   = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = song?.trackName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Cabecera de la canción ─────────────────────
            song?.let { s ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = s.artworkUrl, contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(s.trackName, style = MaterialTheme.typography.titleMedium)
                            Text(s.artistName, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            val contributors = mixedPhotos.map { it.username }.distinct()
                            Text(
                                "${mixedPhotos.size} foto(s) · ${contributors.size} persona(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Grid de fotos ──────────────────────────────
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                mixedPhotos.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aún no hay fotos para esta canción",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(mixedPhotos) { photo ->
                            PhotoCard(photo = photo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(photo: PhotoResponse) {
    Card(modifier = Modifier.aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.photoUri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            // Badge con avatar del usuario
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(photo.username, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
