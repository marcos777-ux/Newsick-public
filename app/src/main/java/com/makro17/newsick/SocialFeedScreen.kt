package com.makro17.newsick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
// PANTALLA SOCIAL
// ══════════════════════════════════════════════════════════

@Composable
fun SocialFeedScreen(
    viewModel: MainViewModel,
    onSongClick: (String) -> Unit,
    onUploadClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    val feedSongs    by viewModel.feedSongs.collectAsState()
    val searchQuery  by viewModel.userSearchQuery
    val searchResults by viewModel.searchResults
    val isSearching  by viewModel.isSearching
    val unreadCount  by viewModel.unreadCount

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Newsick", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge { Text(if (unreadCount > 9) "9+" else unreadCount.toString()) }
                        }
                    }
                ) {
                    IconButton(onClick = onNotificationsClick) {
                        Icon(Icons.Default.Notifications, "Notificaciones y solicitudes")
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onUploadClick,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Publicar") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Barra de búsqueda de usuarios
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    viewModel.userSearchQuery.value = it
                    viewModel.searchUsers(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar usuario...") },
                leadingIcon = { Icon(Icons.Default.PersonSearch, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.userSearchQuery.value = ""
                            viewModel.searchResults.value = emptyList()
                        }) {
                            Icon(Icons.Default.Clear, "Borrar")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            // Resultados de búsqueda
            if (searchQuery.isNotBlank()) {
                when {
                    isSearching -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    searchResults.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No se encontraron usuarios",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { user ->
                                UserSearchResultItem(
                                    user = user,
                                    onClick = { onUserClick(user.id) }
                                )
                            }
                        }
                    }
                }
            } else if (feedSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Aún no hay publicaciones.\n¡Sé el primero en subir una!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "Últimas publicaciones",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feedSongs) { song ->
                        SongFeedCard(
                            song = song,
                            viewModel = viewModel,
                            onClick = { onSongClick(song.trackId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchResultItem(user: UserResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (user.profilePhoto.isNotBlank()) {
                AsyncImage(
                    model = user.profilePhoto,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle, null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username, style = MaterialTheme.typography.titleSmall)
                if (user.bio.isNotBlank()) {
                    Text(
                        user.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SongFeedCard(
    song: SongPostEntity,
    viewModel: MainViewModel,
    onClick: () -> Unit
) {
    val photos by viewModel.getPhotosForSong(song.trackId).collectAsState(initial = emptyList())
    val contributorCount = photos.map { it.username }.distinct().size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.trackName, style = MaterialTheme.typography.titleSmall)
                Text(
                    song.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("${photos.size} foto(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.Group, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text("$contributorCount persona(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Preview de la última foto si existe
        if (photos.isNotEmpty()) {
            AsyncImage(
                model = photos.first().photoUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
