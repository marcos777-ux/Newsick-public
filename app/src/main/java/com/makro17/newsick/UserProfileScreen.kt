package com.makro17.newsick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
    var user          by remember { mutableStateOf<UserResponse?>(null) }
    var posts         by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var friendStatus  by remember { mutableStateOf("loading") }
    var isLoading     by remember { mutableStateOf(true) }
    var requestSent   by remember { mutableStateOf(false) }

    // Captura local para permitir Smart Cast de Kotlin
    val currentUser = user

    // Cargar datos del usuario
    LaunchedEffect(userId) {
        isLoading = true
        user = viewModel.getUserById(userId)
        try {
            val postsResp = NewsickRetrofit.api.getUserPosts(userId)
            if (postsResp.isSuccessful) posts = postsResp.body() ?: emptyList()
        } catch (_: Exception) {}
        friendStatus = viewModel.getFriendStatus(userId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = currentUser?.username ?: "Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (currentUser == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Usuario no encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            // ── Cabecera ───────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                // Foto de perfil con validación de nulos
                Box(modifier = Modifier.size(72.dp)) {
                    if (!currentUser.profilePhoto.isNullOrBlank()) {
                        AsyncImage(
                            model = currentUser.profilePhoto,
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
                    Text(currentUser.username, style = MaterialTheme.typography.titleLarge)

                    // Bio con validación segura
                    if (!currentUser.bio.isNullOrBlank()) {
                        Text(
                            text = currentUser.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        "${posts.size} publicación(es)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Botón de amistad ───────────────────────────
            val isSelf = userId == viewModel.loggedUserId.value
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
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Publicaciones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // ── Grid de publicaciones ──────────────────────
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

@Composable
private fun FriendActionButton(status: String, onAddFriend: () -> Unit) {
    when (status) {
        "friends" -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ya sois amigos")
            }
        }
        "pending_sent" -> {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.HourglassEmpty, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Solicitud enviada")
            }
        }
        "pending_received" -> {
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Responder solicitud")
            }
        }
        "loading" -> {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        else -> { // "none"
            Button(onClick = onAddFriend, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir amigo")
            }
        }
    }
}

@Composable
private fun UserPostCard(post: PostResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = post.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = post.trackName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                    Text(
                        text = "${post.photoCount} foto(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}