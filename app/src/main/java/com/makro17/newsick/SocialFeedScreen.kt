// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.MediaPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════
// UTILIDAD: tiempo relativo
// ══════════════════════════════════════════════════════════

fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L        -> "ahora"
        diff < 3_600_000L     -> "${diff / 60_000}m"
        diff < 86_400_000L    -> "${diff / 3_600_000}h"
        diff < 604_800_000L   -> "${diff / 86_400_000}d"
        else                  -> "${diff / 604_800_000}sem"
    }
}

// ══════════════════════════════════════════════════════════
// PANTALLA SOCIAL — fotos recientes con swipe por canción
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    viewModel: MainViewModel,
    onSongClick: (String) -> Unit,
    onUploadClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    val feedGroups    by viewModel.feedPhotoGroups
    val searchQuery   by viewModel.userSearchQuery
    val searchResults by viewModel.searchResults
    val isSearching   by viewModel.isSearching
    val unreadCount   by viewModel.unreadCount
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    var isRefreshing  by remember { mutableStateOf(false) }
    val listState     = rememberLazyListState()

    // ── Auto-play: reproducir la canción del item visible ─
    var currentPreviewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { currentPreviewPlayer?.stop(); currentPreviewPlayer?.release(); currentPreviewPlayer = null }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { visibleIndex ->
                val group = feedGroups.getOrNull(visibleIndex) ?: return@collect
                val trackId = group.trackId
                // Sólo buscar preview si es una nueva canción
                scope.launch(Dispatchers.IO) {
                    try {
                        val numId = trackId.toLongOrNull() ?: return@launch
                        val res   = ItunesRetrofit.api.lookupTrack(numId)
                        val url   = res.results.firstOrNull()?.previewUrl ?: return@launch
                        currentPreviewPlayer?.stop(); currentPreviewPlayer?.release()
                        val mp = MediaPlayer()
                        mp.setDataSource(url)
                        mp.setOnPreparedListener { it.start() }
                        mp.setOnCompletionListener {}
                        mp.prepareAsync()
                        currentPreviewPlayer = mp
                    } catch (_: Exception) {}
                }
            }
    }

    // Estado para foto a pantalla completa
    var fullscreenGroup       by remember { mutableStateOf<FeedGroup?>(null) }
    var fullscreenInitialPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadFeedPhotos() }

    fullscreenGroup?.let { group ->
        PhotoFullscreenViewer(
            group       = group,
            initialPage = fullscreenInitialPage,
            onDismiss   = { fullscreenGroup = null },
            onSongClick = { onSongClick(group.trackId) }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Newsick", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
                BadgedBox(badge = {
                    if (unreadCount > 0) Badge { Text(if (unreadCount > 9) "9+" else unreadCount.toString()) }
                }) {
                    IconButton(onClick = onNotificationsClick) {
                        Icon(Icons.Default.Notifications, "Notificaciones")
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Barra de búsqueda ──────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    viewModel.userSearchQuery.value = it
                    viewModel.searchUsers(it)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar usuario...") },
                leadingIcon = { Icon(Icons.Default.PersonSearch, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.userSearchQuery.value = ""
                            viewModel.searchResults.value = emptyList()
                        }) { Icon(Icons.Default.Clear, "Borrar") }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            // ── Resultados de búsqueda ─────────────────────
            if (searchQuery.isNotBlank()) {
                when {
                    isSearching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    searchResults.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No se encontraron usuarios", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { user ->
                            UserSearchResultItem(user = user, onClick = { onUserClick(user.id) })
                        }
                    }
                }
                // ── Feed con pull-to-refresh ───────────────────
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.loadFeedPhotos()
                            viewModel.loadNotificationsData(context)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (feedGroups.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MusicNote, null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Aún no hay publicaciones.\n¡Sé el primero en subir una!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            state          = listState,
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(feedGroups, key = { it.trackId }) { group ->
                                SongPhotoCard(
                                    group       = group,
                                    onSongClick = { onSongClick(group.trackId) },
                                    onPhotoClick = { page ->
                                        fullscreenInitialPage = page
                                        fullscreenGroup = group
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// TARJETA DE CANCIÓN: header pulsable + pager de fotos
// ══════════════════════════════════════════════════════════

@Composable
fun SongPhotoCard(
    group: FeedGroup,
    onSongClick: () -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    val pagerState = rememberPagerState { group.photos.size }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Cabecera: canción (pulsable → detalle/álbum) ─────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSongClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = group.artworkUrl, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.trackName, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(group.artistName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Pager de fotos ────────────────────────────────
        HorizontalPager(
            state  = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val photo = group.photos[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onPhotoClick(page) }
            ) {
                AsyncImage(
                    model = NewsickRetrofit.absoluteUrl(photo.photoUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Overlay inferior: usuario + tiempo
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val profileUrl = NewsickRetrofit.absoluteUrl(photo.profilePhotoUrl)
                        if (profileUrl.isNotBlank()) {
                            AsyncImage(
                                model = profileUrl, contentDescription = null,
                                modifier = Modifier.size(18.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, null,
                                modifier = Modifier.size(18.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(photo.username, style = MaterialTheme.typography.labelMedium,
                            color = Color.White, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(timeAgo(photo.timestamp),
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // ── Indicador de páginas ──────────────────────────
        if (group.photos.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(group.photos.size.coerceAtMost(8)) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                    )
                }
                if (group.photos.size > 8) {
                    Spacer(Modifier.width(4.dp))
                    Text("+${group.photos.size - 8}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ══════════════════════════════════════════════════════════
// VISOR DE FOTO A PANTALLA COMPLETA (swipe horizontal)
// ══════════════════════════════════════════════════════════

@Composable
fun PhotoFullscreenViewer(
    group: FeedGroup,
    initialPage: Int,
    onDismiss: () -> Unit,
    onSongClick: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage) { group.photos.size }
    val currentPhoto = group.photos.getOrNull(pagerState.currentPage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val photo = group.photos[page]
                AsyncImage(
                    model = NewsickRetrofit.absoluteUrl(photo.photoUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // ── Barra superior: cerrar + canción ──────────
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
                Spacer(Modifier.width(4.dp))
                Row(
                    modifier = Modifier.weight(1f).clickable { onDismiss(); onSongClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = group.artworkUrl, contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(group.trackName, style = MaterialTheme.typography.labelLarge,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(group.artistName, style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Contador de página
                Text(
                    "${pagerState.currentPage + 1}/${group.photos.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // ── Barra inferior: usuario + tiempo ──────────
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

// ══════════════════════════════════════════════════════════
// RESULTADO DE BÚSQUEDA DE USUARIO
// ══════════════════════════════════════════════════════════

@Composable
fun UserSearchResultItem(user: UserResponse, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val photoUrl = NewsickRetrofit.absoluteUrl(user.profilePhoto)
            if (photoUrl.isNotBlank()) {
                AsyncImage(model = photoUrl, contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.AccountCircle, null,
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username.orEmpty().ifBlank { "Usuario" },
                    style = MaterialTheme.typography.titleSmall)
                if (!user.bio.isNullOrBlank()) {
                    Text(user.bio, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}