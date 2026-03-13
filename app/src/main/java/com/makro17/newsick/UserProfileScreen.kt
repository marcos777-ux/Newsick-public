// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════
// PANTALLA DE PERFIL DE OTRO USUARIO
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSongClick: (String) -> Unit,
    onChatClick: (Int, String, String, Int) -> Unit = { _, _, _, _ -> }
) {
    var user                by remember { mutableStateOf<UserResponse?>(null) }
    var posts               by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var commonSongs         by remember { mutableStateOf<List<PostResponse>>(emptyList()) }
    var friendStatus        by remember { mutableStateOf("loading") }
    var recommendations     by remember { mutableStateOf<List<RecommendationResponse>>(emptyList()) }
    var isLoading           by remember { mutableStateOf(true) }
    var requestSent         by remember { mutableStateOf(false) }
    var showCommonSongs     by remember { mutableStateOf(false) }
    var showRemoveConfirm   by remember { mutableStateOf(false) }
    var showRecommendDialog by remember { mutableStateOf(false) }
    var selectedTab         by remember { mutableIntStateOf(0) }
    var chatPrivacy         by remember { mutableStateOf("everyone") }
    var isChatLoading       by remember { mutableStateOf(false) }

    val isSelf      = userId == viewModel.loggedUserId.value
    val currentUser = user
    val scope       = rememberCoroutineScope()

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
            try {
                val r = NewsickRetrofit.api.getUserRecommendations(userId)
                if (r.isSuccessful) recommendations = r.body() ?: emptyList()
            } catch (_: Exception) {}
            try {
                val r = NewsickRetrofit.api.getChatPrivacy(userId)
                if (r.isSuccessful) chatPrivacy = r.body()?.chatPrivacy ?: "everyone"
            } catch (_: Exception) {}
        }
        isLoading = false
    }

    // ── Diálogo eliminar amigo ─────────────────────────────
    if (showRemoveConfirm && currentUser != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Eliminar amigo") },
            text = { Text("¿Seguro que quieres eliminar a ${currentUser.username} de tus amigos?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeFriend(userId) { success -> if (success) friendStatus = "none" }
                    showRemoveConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Eliminar")
                }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancelar") } }
        )
    }

    // ── Diálogo canciones en común ─────────────────────────
    if (showCommonSongs && commonSongs.isNotEmpty()) {
        CommonSongsDialog(songs = commonSongs, onDismiss = { showCommonSongs = false },
            onSongClick = { id -> showCommonSongs = false; onSongClick(id) })
    }

    // ── Diálogo recomendar canción (con búsqueda iTunes) ───
    if (showRecommendDialog && currentUser != null) {
        RecommendSongDialog(
            toUserId   = userId,
            toUsername = currentUser.username,
            onDismiss  = { showRecommendDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentUser?.username ?: "Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (currentUser == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Usuario no encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize()) {

            // ── Cabecera ───────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)) {
                Box(modifier = Modifier.size(72.dp)) {
                    // BUG FIX: URL absoluta para fotos de otros usuarios
                    val photoUrl = NewsickRetrofit.absoluteUrl(currentUser.profilePhoto)
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(model = photoUrl, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.AccountCircle, null,
                            modifier = Modifier.fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentUser.username, style = MaterialTheme.typography.titleLarge)
                    if (!currentUser.bio.isNullOrBlank()) {
                        Text(currentUser.bio, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${posts.size} publicación(es)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Canciones en común ─────────────────────────
            if (!isSelf && commonSongs.isNotEmpty()) {
                AssistChip(
                    onClick = { showCommonSongs = true },
                    label = { Text("${commonSongs.size} canción(es) en común") },
                    leadingIcon = { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ── Botones amistad + recomendar ───────────────
            if (!isSelf) {
                val effectiveStatus = if (requestSent) "pending_sent" else friendStatus
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        FriendActionButton(
                            status         = effectiveStatus,
                            onAddFriend    = {
                                viewModel.sendFriendRequest(userId) { success -> if (success) requestSent = true }
                            },
                            onRemoveFriend = { showRemoveConfirm = true }
                        )
                    }
                    if (effectiveStatus == "friends") {
                        OutlinedButton(onClick = { showRecommendDialog = true }) {
                            Icon(Icons.Default.Recommend, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("Recomendar")
                        }
                    }
                }
                // Botón enviar mensaje (respeta privacidad)
                if (chatPrivacy != "nobody") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isChatLoading = true
                            scope.launch {
                                try {
                                    val r = NewsickRetrofit.api.getOrCreateChat(userId)
                                    if (r.isSuccessful) {
                                        val convId = r.body()?.conversationId ?: -1
                                        if (convId > 0 && currentUser != null) {
                                            onChatClick(convId, currentUser!!.username, currentUser!!.profilePhoto ?: "", userId)
                                        }
                                    }
                                } catch (_: Exception) {}
                                isChatLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = !isChatLoading
                    ) {
                        if (isChatLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Chat, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enviar mensaje")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider()

            // ── Pestañas ───────────────────────────────────
            if (!isSelf) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Publicaciones") },
                        icon = { Icon(Icons.Default.GridOn, null, Modifier.size(18.dp)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Recomendaciones")
                                if (recommendations.isNotEmpty()) Badge { Text("${recommendations.size}") }
                            }
                        },
                        icon = { Icon(Icons.Default.Recommend, null, Modifier.size(18.dp)) })
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(12.dp))
                Text("Publicaciones (${posts.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            when (if (isSelf) 0 else selectedTab) {
                0 -> {
                    if (posts.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Text("Este usuario aún no ha publicado nada",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                1 -> {
                    if (recommendations.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Recommend, null, modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Ninguno de tus amigos le ha recomendado canciones",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)) {
                            items(recommendations) { rec ->
                                RecommendationCardReadOnly(rec = rec)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tarjeta de recomendación de solo lectura ──────────────

@Composable
private fun RecommendationCardReadOnly(rec: RecommendationResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = rec.artworkUrl, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop)
            Column(modifier = Modifier.weight(1f)) {
                Text(rec.trackName, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rec.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    rec.recommendedBy.take(3).forEach { r ->
                        val url = NewsickRetrofit.absoluteUrl(r.profilePhoto)
                        Box(modifier = Modifier.size(20.dp)) {
                            if (url.isNotBlank()) {
                                AsyncImage(model = url, contentDescription = r.username,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.AccountCircle, null,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (rec.totalCount == 1) rec.recommendedBy.firstOrNull()?.username ?: ""
                        else "${rec.totalCount} amigos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Diálogo: buscar canción (iTunes) y recomendar ─────────

@Composable
private fun RecommendSongDialog(
    toUserId: Int,
    toUsername: String,
    onDismiss: () -> Unit
) {
    var searchQuery   by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ItunesTrack>>(emptyList()) }
    var isSearching   by remember { mutableStateOf(false) }
    var sentTrackId   by remember { mutableStateOf<String?>(null) }
    var isSending     by remember { mutableStateOf(false) }
    var searchJob     by remember { mutableStateOf<Job?>(null) }
    val scope         = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recomendar a $toUsername") },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                // Barra de búsqueda iTunes
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        searchJob?.cancel()
                        if (q.length >= 2) {
                            isSearching = true
                            searchJob = scope.launch {
                                delay(400)
                                try {
                                    val res = withContext(Dispatchers.IO) { ItunesRetrofit.api.searchMusic(q) }
                                    searchResults = res.results.filter { it.trackId != 0L && it.trackName.isNotBlank() }
                                } catch (_: Exception) { searchResults = emptyList() }
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList(); isSearching = false
                        }
                    },
                    modifier    = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar canción o artista…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""; searchResults = emptyList(); isSearching = false
                            }) { Icon(Icons.Default.Clear, null) }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                when {
                    isSearching -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    searchQuery.length >= 2 && searchResults.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        Text("Sin resultados para \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                    searchQuery.length < 2 -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        Text("Escribe para buscar canciones",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(searchResults) { track ->
                            val alreadySent = sentTrackId == track.trackId.toString()
                            Card(modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (!isSending && !alreadySent) {
                                        scope.launch {
                                            isSending = true
                                            try {
                                                val r = NewsickRetrofit.api.sendRecommendation(
                                                    SendRecommendationRequest(
                                                        toUserId   = toUserId,
                                                        trackId    = track.trackId.toString(),
                                                        trackName  = track.trackName,
                                                        artistName = track.artistName,
                                                        artworkUrl = track.artworkUrl300,
                                                        previewUrl = track.previewUrl
                                                    )
                                                )
                                                if (r.isSuccessful || r.code() == 409) {
                                                    sentTrackId = track.trackId.toString()
                                                }
                                            } catch (_: Exception) {}
                                            isSending = false
                                        }
                                    }
                                }
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(model = track.artworkUrl100, contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.trackName, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artistName, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    if (alreadySent) {
                                        Icon(Icons.Default.CheckCircle, "Enviada",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp))
                                    } else {
                                        Icon(Icons.Default.Send, "Enviar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ── Botón dinámico de amistad ─────────────────────────────

@Composable
private fun FriendActionButton(status: String, onAddFriend: () -> Unit, onRemoveFriend: () -> Unit) {
    when (status) {
        "friends" -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                disabledContentColor   = MaterialTheme.colorScheme.onSecondaryContainer)) {
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

// ── Diálogo canciones en común ────────────────────────────

@Composable
private fun CommonSongsDialog(
    songs: List<PostResponse>, onDismiss: () -> Unit, onSongClick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canciones en común") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(songs) { song ->
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { onSongClick(song.trackId) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = song.artworkUrl, contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.trackName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(song.artistName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ── Tarjeta de publicación ────────────────────────────────

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