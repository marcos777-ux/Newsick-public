package com.makro17.newsick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
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

// ══════════════════════════════════════════════════════════
// PANTALLA DE PERFIL DE OTRO USUARIO
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSongClick: (String) -> Unit
) {
    var user            by remember { mutableStateOf<UserResponse?>(null) }
    var posts           by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var commonSongs     by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var friendStatus    by remember { mutableStateOf("loading") }
    var isLoading       by remember { mutableStateOf(true) }
    var requestSent     by remember { mutableStateOf(false) }
    var showCommonSongs by remember { mutableStateOf(false) }

    val isSelf = userId == viewModel.loggedUserId.value

    LaunchedEffect(userId) {
        isLoading = true
        user = viewModel.getUserById(userId)
        try {
            val r = NewsickRetrofit.api.getUserPosts(userId)
            if (r.isSuccessful) posts = r.body() ?: emptyList()
        } catch (_: Exception) {}

        if (!isSelf) {
            commonSongs  = viewModel.getCommonSongs(userId)
            friendStatus = viewModel.getFriendStatus(userId)
        }
        isLoading = false
    }

    val currentUser = user

    // Diálogo de canciones en común
    if (showCommonSongs && commonSongs.isNotEmpty()) {
        CommonSongsDialog(songs = commonSongs, onDismiss = { showCommonSongs = false }, onSongClick = { id ->
            showCommonSongs = false; onSongClick(id)
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentUser?.username ?: "Perfil") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (currentUser == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Usuario no encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize()) {

            // ── Cabecera ───────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                Box(modifier = Modifier.size(72.dp)) {
                    if (!currentUser.profilePhoto.isNullOrBlank()) {
                        AsyncImage(model = currentUser.profilePhoto, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.AccountCircle, null,
                            modifier = Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentUser.username, style = MaterialTheme.typography.titleLarge)
                    if (!currentUser.bio.isNullOrBlank()) {
                        Text(currentUser.bio, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${posts.size} publicación(es)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Chip de canciones en común (visible siempre si hay) ──
            if (!isSelf && commonSongs.isNotEmpty()) {
                AssistChip(
                    onClick = { showCommonSongs = true },
                    label = { Text("${commonSongs.size} canción(es) en común") },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ── Botón de amistad ───────────────────────────
            if (!isSelf) {
                val effectiveStatus = if (requestSent) "pending_sent" else friendStatus
                FriendActionButton(
                    status = effectiveStatus,
                    onAddFriend = {
                        viewModel.sendFriendRequest(userId) { success ->
                            if (success) requestSent = true
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text("Publicaciones (${posts.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // ── Grid de publicaciones ──────────────────────
            // Se ven las portadas (álbum), las fotos solo se ven en detalle si son amigos
            if (posts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Este usuario aún no ha publicado nada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(posts) { post ->
                        UserPostCard(post = post, onClick = { onSongClick(post.trackId) })
                    }
                }
            }
        }
    }
}

// ── Diálogo: lista de canciones en común ──────────────────

@Composable
private fun CommonSongsDialog(
    songs: List<PostResponse>,
    onDismiss: () -> Unit,
    onSongClick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canciones en común") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(songs) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(song.trackId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = song.artworkUrl, contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.trackName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(song.artistName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ── Botón dinámico de amistad ─────────────────────────────

@Composable
private fun FriendActionButton(status: String, onAddFriend: () -> Unit) {
    when (status) {
        "friends" -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                disabledContentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            )) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Ya sois amigos")
        }
        "pending_sent" -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.HourglassEmpty, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Solicitud enviada")
        }
        "loading" -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        else -> Button(onClick = onAddFriend, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Añadir amigo")
        }
    }
}

// ── Tarjeta de publicación (portada del álbum) ────────────

@Composable
private fun UserPostCard(post: PostResponse, onClick: () -> Unit) {
    Card(modifier = Modifier.aspectRatio(1f).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = post.artworkUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(post.trackName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text("${post.photoCount} foto(s)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
