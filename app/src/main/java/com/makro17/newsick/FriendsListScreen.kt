package com.makro17.newsick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ══════════════════════════════════════════════════════════
// SECCIÓN AMIGOS — lista de amigos + canciones rankeadas
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsListScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit,
    onSongClick: (String) -> Unit
) {
    val friends  by viewModel.friendsList
    val songs    by viewModel.friendsSongs
    var tab      by remember { mutableIntStateOf(0) }
    var friendToRemove by remember { mutableStateOf<FriendshipResponse?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFriendsList()
        viewModel.loadFriendsSongs()
    }

    // Diálogo eliminar amigo
    friendToRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Eliminar amigo") },
            text = { Text("¿Seguro que quieres eliminar a ${friend.friendUsername} de tus amigos?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.removeFriend(friend.friendId); friendToRemove = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { friendToRemove = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amigos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadFriendsList()
                        viewModel.loadFriendsSongs()
                    }) { Icon(Icons.Default.Refresh, "Actualizar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Pestañas ───────────────────────────────────
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Amigos (${friends.size})") },
                    icon = { Icon(Icons.Default.People, null, Modifier.size(18.dp)) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Canciones") },
                    icon = { Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp)) })
            }

            when (tab) {

                // ── Tab 0: Lista de amigos ─────────────────
                0 -> {
                    if (friends.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PeopleAlt, null, modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Aún no tienes amigos agregados",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text("Busca usuarios en la pestaña Social",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(friends, key = { it.friendId }) { friend ->
                                FriendItem(
                                    friend   = friend,
                                    onClick  = { onUserClick(friend.friendId) },
                                    onRemove = { friendToRemove = friend }
                                )
                            }
                        }
                    }
                }

                // ── Tab 1: Canciones rankeadas ─────────────
                1 -> {
                    if (songs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Tus amigos aún no han publicado canciones",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("Canciones más compartidas por tus amigos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp))
                            }
                            items(songs.size) { index ->
                                FriendSongItem(
                                    index  = index + 1,
                                    entry  = songs[index],
                                    onClick = { onSongClick(songs[index].trackId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Tarjeta de amigo ──────────────────────────────────────

@Composable
private fun FriendItem(friend: FriendshipResponse, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable { onClick() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val photoUrl = NewsickRetrofit.absoluteUrl(friend.friendProfilePhoto)
            if (photoUrl.isNotBlank()) {
                AsyncImage(model = photoUrl, contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.AccountCircle, null,
                    modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(friend.friendUsername, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.PersonRemove, "Eliminar amigo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Tarjeta de canción rankeada ───────────────────────────

@Composable
private fun FriendSongItem(index: Int, entry: FriendSongEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            // Número de ranking
            Text(
                "$index",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (index) {
                    1    -> MaterialTheme.colorScheme.primary
                    2    -> MaterialTheme.colorScheme.secondary
                    3    -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(24.dp)
            )

            // Artwork
            AsyncImage(
                model = entry.artworkUrl, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Info de canción
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.trackName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))

                // Mini-avatares de contribuyentes + contador
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    entry.contributors.take(3).forEach { contributor ->
                        val url = NewsickRetrofit.absoluteUrl(contributor.profilePhoto)
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape)) {
                            if (url.isNotBlank()) {
                                AsyncImage(model = url, contentDescription = contributor.username,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.AccountCircle, null,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${entry.contributorCount} amigo${if (entry.contributorCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
