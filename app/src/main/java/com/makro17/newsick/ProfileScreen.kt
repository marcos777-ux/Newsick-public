package com.makro17.newsick

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ══════════════════════════════════════════════════════════
// PANTALLA DE PERFIL PROPIO
// ══════════════════════════════════════════════════════════

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    onSongClick: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val mySongs by viewModel.mySongs.collectAsState()

    val username     = viewModel.loggedUsername.value
    val bio          = viewModel.loggedBio.value
    val profilePhoto = viewModel.loggedProfilePhoto.value

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Perfil", style = MaterialTheme.typography.headlineMedium)
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
                Box(modifier = Modifier.size(80.dp)) {
                    if (profilePhoto.isNotBlank()) {
                        AsyncImage(
                            model = profilePhoto,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle, null,
                            modifier = Modifier.fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(username, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (bio.isNotBlank()) bio else "Sin descripción",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, "Editar Perfil")
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text("Mis canciones (${mySongs.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // ── Grid de mis canciones (solo portada del álbum) ──
            if (mySongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Aún no has publicado nada", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        MySongCard(song = song, onClick = { onSongClick(song.trackId) })
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialUsername     = viewModel.loggedUsername.value,
            initialBio          = viewModel.loggedBio.value,
            initialProfilePhoto = viewModel.loggedProfilePhoto.value,
            email               = viewModel.loggedEmail.value,
            onDismiss           = { showEditDialog = false },
            onSave              = { newUsername, newBio, newPhoto ->
                viewModel.updateProfile(newUsername, newBio, newPhoto) { /* result ignored */ }
                showEditDialog = false
            },
            onDeleteAccount     = { password ->
                viewModel.deleteAccount(password) { /* logout handled in VM */ }
                showEditDialog = false
            }
        )
    }
}

// ── Tarjeta de canción: solo portada del álbum ────────────

@Composable
private fun MySongCard(song: SongPostEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Siempre muestra la portada del álbum (no la foto del usuario)
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Overlay con nombre
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(song.trackName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(
                        song.artistName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
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
    initialProfilePhoto: String,
    email: String,
    onDismiss: () -> Unit,
    onSave: (username: String, bio: String, profilePhoto: String) -> Unit,
    onDeleteAccount: (password: String) -> Unit
) {
    val context = LocalContext.current
    var username     by remember { mutableStateOf(initialUsername) }
    var bio          by remember { mutableStateOf(initialBio) }
    var profilePhoto by remember { mutableStateOf(initialProfilePhoto) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletePassword   by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            profilePhoto = it.toString()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar cuenta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Esta acción es permanente. Todos tus datos serán eliminados.")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Confirma tu contraseña") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteAccount(deletePassword) },
                    enabled = deletePassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; deletePassword = "" }) { Text("Cancelar") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Foto de perfil
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(80.dp).clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        if (profilePhoto.isNotBlank()) {
                            AsyncImage(
                                model = profilePhoto, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle, null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Icono de cámara superpuesto
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                Icons.Default.CameraAlt, null,
                                modifier = Modifier.padding(4.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Email (solo lectura)
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false
                )

                // Nombre de usuario
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Bio
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("Cuéntanos algo sobre ti...") }
                )

                HorizontalDivider()

                // Botón eliminar cuenta
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar cuenta permanentemente")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, bio, profilePhoto) },
                enabled = username.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
