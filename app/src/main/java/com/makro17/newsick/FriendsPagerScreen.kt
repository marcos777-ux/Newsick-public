// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
package com.makro17.newsick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════
// PANTALLA AMIGOS CON PESTAÑAS: MENSAJES + CANCIONES
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsPagerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit,
    onSongClick: (String) -> Unit,
    onChatClick: (Int, String, String) -> Unit,  // conversationId, username, photo
    initialTab: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Mensajes" else "Canciones de amigos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = { Text("Mensajes") },
                    icon     = { Icon(Icons.Default.Chat, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = { Text("Canciones") },
                    icon     = { Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp)) }
                )
            }
            when (selectedTab) {
                0 -> MessagesTab(
                    viewModel   = viewModel,
                    onChatClick = onChatClick
                )
                1 -> SongsTab(
                    viewModel   = viewModel,
                    onUserClick = onUserClick,
                    onSongClick = onSongClick
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// PESTAÑA: MENSAJES
// ══════════════════════════════════════════════════════════

@Composable
private fun MessagesTab(
    viewModel: MainViewModel,
    onChatClick: (Int, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    var isLoading     by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val r = NewsickRetrofit.api.getChats()
            if (r.isSuccessful) conversations = r.body() ?: emptyList()
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (conversations.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ChatBubbleOutline, null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Sin chats todavía",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Visita el perfil de alguien para enviarle un mensaje",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(conversations, key = { it.id }) { conv ->
            ConversationItem(
                conv   = conv,
                onClick = {
                    onChatClick(conv.id, conv.otherUsername, conv.otherProfilePhoto)
                },
                onDelete = {
                    scope.launch {
                        try {
                            NewsickRetrofit.api.deleteChat(conv.id)
                            conversations = conversations.filter { it.id != conv.id }
                        } catch (_: Exception) {}
                    }
                }
            )
            HorizontalDivider(Modifier.padding(horizontal = 72.dp))
        }
    }
}

@Composable
private fun ConversationItem(
    conv: ConversationResponse,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    val photoUrl   = NewsickRetrofit.absoluteUrl(conv.otherProfilePhoto)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(52.dp)) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(model = photoUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.AccountCircle, null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary)
            }
            // Burbuja de no leídos
            if (conv.unreadCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd),
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text("${conv.unreadCount}")
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(conv.otherUsername, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (conv.unreadCount > 0)
                    androidx.compose.ui.text.font.FontWeight.Bold
                else androidx.compose.ui.text.font.FontWeight.Normal)
            val preview = when (conv.lastMessageType) {
                "image" -> "📷 Imagen"
                "video" -> "🎥 Vídeo"
                "audio", "voice" -> "🎵 Audio"
                else -> conv.lastMessage ?: "Sin mensajes"
            }
            Text(preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            conv.lastMessageAt?.let { at ->
                Text(at.take(10), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDelete = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar chat") },
            text  = { Text("¿Eliminar el chat con ${conv.otherUsername}?") },
            confirmButton = {
                Button(
                    onClick = { showDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════
// PESTAÑA: CANCIONES DE AMIGOS
// ══════════════════════════════════════════════════════════

@Composable
private fun SongsTab(
    viewModel: MainViewModel,
    onUserClick: (Int) -> Unit,
    onSongClick: (String) -> Unit
) {
    val friendsSongs by viewModel.friendsSongs
    var isLoading    by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadFriendsSongs()
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (friendsSongs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LibraryMusic, null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Tus amigos no han publicado canciones todavía",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(friendsSongs) { entry ->
            FriendSongCard(
                entry       = entry,
                onSongClick = { onSongClick(entry.trackId) },
                onUserClick = onUserClick
            )
        }
    }
}

@Composable
private fun FriendSongCard(
    entry: FriendSongEntry,
    onSongClick: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick  = onSongClick
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.artworkUrl, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.trackName, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(entry.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    entry.contributors.take(3).forEach { contrib ->
                        val photoUrl = NewsickRetrofit.absoluteUrl(contrib.profilePhoto)
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape)
                            .clickable { onUserClick(contrib.userId) }) {
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(model = photoUrl, contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.AccountCircle, null,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (entry.contributorCount > 3) {
                        Spacer(Modifier.width(12.dp))
                        Text("+${entry.contributorCount - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
        }
    }
}
