package com.makro17.newsick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ══════════════════════════════════════════════════════════
// PANTALLA DE PERFIL — mis publicaciones + editar perfil
// ══════════════════════════════════════════════════════════

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    onSongClick: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf(viewModel.loggedUsername.value) }
    var currentBio by remember { mutableStateOf(viewModel.loggedBio.value) }

    val mySongs by viewModel.mySongs.collectAsState()

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
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Configuración", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            // ── Cabecera del usuario ───────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountCircle,
                    null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentUsername, style = MaterialTheme.typography.titleLarge)
                    if (currentBio.isNotBlank()) {
                        Text(
                            currentBio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Sin descripción",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, "Editar Perfil")
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                "Mis canciones (${mySongs.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))

            // ── Grid de mis canciones ──────────────────────
            if (mySongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Aún no has publicado nada",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mySongs) { song ->
                        MySongCard(
                            song = song,
                            viewModel = viewModel,
                            onClick = { onSongClick(song.trackId) }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialUsername = currentUsername,
            initialBio = currentBio,
            onDismiss = { showEditDialog = false },
            onSave = { newUsername, newBio ->
                currentUsername = newUsername
                currentBio = newBio
                viewModel.loggedUsername.value = newUsername
                viewModel.loggedBio.value = newBio
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun MySongCard(
    song: SongPostEntity,
    viewModel: MainViewModel,
    onClick: () -> Unit
) {
    val photos by viewModel.getPhotosForSong(song.trackId).collectAsState(initial = emptyList())

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Foto de portada si existe, o la imagen del álbum
            if (photos.isNotEmpty()) {
                AsyncImage(
                    model = photos.first().photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Overlay con info
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(song.trackName, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${photos.size} foto(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// DIÁLOGO DE EDICIÓN DE PERFIL
// ══════════════════════════════════════════════════════════

@Composable
fun EditProfileDialog(
    initialUsername: String,
    initialBio: String,
    onDismiss: () -> Unit,
    onSave: (username: String, bio: String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var bio by remember { mutableStateOf(initialBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
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
