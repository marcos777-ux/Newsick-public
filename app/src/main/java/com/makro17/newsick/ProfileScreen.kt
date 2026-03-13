// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

// ══════════════════════════════════════════════════════════
// Sube foto de perfil al servidor
// ══════════════════════════════════════════════════════════

private suspend fun uploadProfilePhoto(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes  = stream.readBytes(); stream.close()
            if (bytes.isEmpty()) return@withContext null
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext  = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val body = bytes.toRequestBody(mime.toMediaType())
            val part = MultipartBody.Part.createFormData("photo", "profile.$ext", body)
            val r    = NewsickRetrofit.api.uploadPhoto(part)
            if (r.isSuccessful) r.body()?.url else null
        } catch (_: Exception) { null }
    }

// ══════════════════════════════════════════════════════════
// PANTALLA DE PERFIL PROPIO
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    onSongClick: (String) -> Unit,
    onFriendsClick: () -> Unit,
    onUploadClick: () -> Unit = {}
) {
    var showEditDialog  by remember { mutableStateOf(false) }
    val mySongs         by viewModel.mySongs.collectAsState()
    val friendCount     by viewModel.friendCount
    var selectedTab     by remember { mutableIntStateOf(0) }
    var recommendations by remember { mutableStateOf<List<RecommendationResponse>>(emptyList()) }
    var isRefreshing    by remember { mutableStateOf(false) }
    var selectedRec     by remember { mutableStateOf<RecommendationResponse?>(null) }
    val scope           = rememberCoroutineScope()

    val username     = viewModel.loggedUsername.value
    val bio          = viewModel.loggedBio.value
    val profilePhoto = viewModel.loggedProfilePhoto.value

    fun refresh() {
        scope.launch {
            isRefreshing = true
            viewModel.loadFriendCount()
            try {
                val r = NewsickRetrofit.api.getMyRecommendations()
                if (r.isSuccessful) recommendations = r.body() ?: emptyList()
            } catch (_: Exception) {}
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // ── BottomSheet de detalle de recomendación ────────────
    selectedRec?.let { rec ->
        RecommendationDetailSheet(
            rec       = rec,
            viewModel = viewModel,
            onDismiss = { selectedRec = null },
            onPublishClick = {
                viewModel.pendingUploadTrack.value = PendingUploadTrack(
                    trackId    = rec.trackId,
                    trackName  = rec.trackName,
                    artistName = rec.artistName,
                    artworkUrl = rec.artworkUrl,
                    previewUrl = rec.previewUrl
                )
                selectedRec = null
                onUploadClick()
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Perfil", style = MaterialTheme.typography.headlineMedium)
                Row {
                    // Botón amigos
                    IconButton(onClick = onFriendsClick) {
                        Icon(Icons.Default.Group, "Amigos")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Configuración")
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { refresh() },
            modifier     = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                // ── Cabecera ───────────────────────────────
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)) {
                        Box(modifier = Modifier.size(80.dp)) {
                            val photoUrl = NewsickRetrofit.absoluteUrl(profilePhoto)
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
                }

                item {
                    AssistChip(
                        onClick = onFriendsClick,
                        label = { Text("$friendCount amigo${if (friendCount != 1) "s" else ""}") },
                        leadingIcon = { Icon(Icons.Default.Group, null, modifier = Modifier.size(16.dp)) }
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }

                // ── Pestañas ───────────────────────────────
                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text("Publicaciones") },
                            icon = { Icon(Icons.Default.GridOn, null, Modifier.size(18.dp)) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Recomendaciones")
                                    if (recommendations.isNotEmpty()) {
                                        Badge { Text("${recommendations.size}") }
                                    }
                                }
                            },
                            icon = { Icon(Icons.Default.Recommend, null, Modifier.size(18.dp)) })
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Contenido de la pestaña ────────────────
                when (selectedTab) {
                    0 -> {
                        if (mySongs.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.MusicNote, null,
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Aún no has publicado nada",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            item {
                                Text("${mySongs.size} publicación(es)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                            }
                            items(mySongs.chunked(2)) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { song ->
                                        Box(Modifier.weight(1f)) {
                                            MySongCard(song, onClick = { onSongClick(song.trackId) })
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    1 -> {
                        if (recommendations.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Recommend, null,
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Tus amigos aún no te han recomendado nada",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            items(recommendations) { rec ->
                                RecommendationCard(
                                    rec = rec,
                                    onClick = { selectedRec = rec },
                                    onListened = {
                                        scope.launch {
                                            try {
                                                val r = NewsickRetrofit.api.markListened(rec.trackId)
                                                if (r.isSuccessful) {
                                                    recommendations = recommendations.filter { it.trackId != rec.trackId }
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
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
            onSave              = { u, b, p ->
                viewModel.updateProfile(u, b, p) { _, _ -> }
                showEditDialog = false
            },
            onDeleteAccount = { password -> viewModel.deleteAccount(password) {}; showEditDialog = false }
        )
    }
}

// ══════════════════════════════════════════════════════════
// TARJETA DE RECOMENDACIÓN — fotos de quienes la recomiendan
// Tick directo para marcar escuchada · Pulsar → abre detalle
// ══════════════════════════════════════════════════════════

@Composable
fun RecommendationCard(
    rec: RecommendationResponse,
    onClick: () -> Unit,
    onListened: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = rec.artworkUrl, contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop)
            Column(modifier = Modifier.weight(1f)) {
                Text(rec.trackName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rec.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                // Fotos de perfil de quienes recomiendan + contador
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        rec.recommendedBy.take(3).forEach { r ->
                            val url = NewsickRetrofit.absoluteUrl(r.profilePhoto)
                            Box(modifier = Modifier.size(20.dp)
                                .clip(CircleShape)) {
                                if (url.isNotBlank()) {
                                    AsyncImage(model = url, contentDescription = r.username,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.AccountCircle, null,
                                        modifier = Modifier.fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (rec.totalCount == 1) "1" else "${rec.totalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Botón tick — marcar como escuchada directamente
            IconButton(onClick = { onListened() }) {
                Icon(Icons.Default.Check, contentDescription = "Ya la escuché",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// BOTTOM SHEET DE DETALLE DE RECOMENDACIÓN
// Canción + preview de audio + publicar rápido + marcar escuchada
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationDetailSheet(
    rec: RecommendationResponse,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onPublishClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPlaying  by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release(); mediaPlayer = null }
    }

    fun togglePreview() {
        val previewUrl = rec.previewUrl ?: return
        if (isPlaying) {
            mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null; isPlaying = false
        } else {
            try {
                val mp = MediaPlayer()
                mp.setDataSource(previewUrl)
                mp.setOnCompletionListener { isPlaying = false }
                mp.prepareAsync()
                mp.setOnPreparedListener { mp.start(); isPlaying = true }
                mediaPlayer = mp
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(onDismissRequest = { mediaPlayer?.release(); mediaPlayer = null; onDismiss() },
        sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quienes recomiendan — fotos de perfil + texto
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    rec.recommendedBy.take(3).forEach { r ->
                        val url = NewsickRetrofit.absoluteUrl(r.profilePhoto)
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape)) {
                            if (url.isNotBlank()) {
                                AsyncImage(model = url, contentDescription = r.username,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.AccountCircle, null,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (rec.totalCount == 1) "${rec.recommendedBy.firstOrNull()?.username} te recomienda:"
                    else "${rec.totalCount} amigos te recomiendan:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))

            // Artwork grande
            AsyncImage(model = rec.artworkUrl, contentDescription = null,
                modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop)
            Spacer(Modifier.height(12.dp))

            // Nombre y artista
            Text(rec.trackName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(rec.artistName, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(20.dp))

            // Botón preview de audio
            if (!rec.previewUrl.isNullOrBlank()) {
                OutlinedButton(onClick = { togglePreview() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) "Detener preview" else "Escuchar 30 segundos")
                }
                Spacer(Modifier.height(8.dp))
            }

            // Botón publicar — lleva al flujo de selección de imágenes
            Button(
                onClick = { mediaPlayer?.release(); mediaPlayer = null; onPublishClick() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Publicar")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// TARJETA DE CANCIÓN PROPIA (grid 2 columnas)
// ══════════════════════════════════════════════════════════

@Composable
fun MySongCard(song: SongPostEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.aspectRatio(1f).clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = song.artworkUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(song.trackName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(song.artistName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// DIÁLOGO EDICIÓN DE PERFIL
// ══════════════════════════════════════════════════════════

@Composable
fun EditProfileDialog(
    initialUsername: String, initialBio: String,
    initialProfilePhoto: String, email: String,
    onDismiss: () -> Unit,
    onSave: (username: String, bio: String, profilePhoto: String) -> Unit,
    onDeleteAccount: (password: String) -> Unit
) {
    val context      = LocalContext.current
    var username     by remember { mutableStateOf(initialUsername) }
    var bio          by remember { mutableStateOf(initialBio) }
    var profilePhoto by remember { mutableStateOf(initialProfilePhoto) }
    var showDelete   by remember { mutableStateOf(false) }
    var deletePass   by remember { mutableStateOf("") }
    var isUploading  by remember { mutableStateOf(false) }
    var uploadError  by remember { mutableStateOf<String?>(null) }
    val scope        = rememberCoroutineScope()
    val usernameError = if (username.isNotBlank()) validateUsername(username) else null

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            scope.launch {
                isUploading = true; uploadError = null
                val serverUrl = uploadProfilePhoto(context, uri)
                if (serverUrl != null) {
                    profilePhoto = if (serverUrl.startsWith("http")) serverUrl
                                   else NewsickRetrofit.BASE_URL.trimEnd('/') + serverUrl
                } else { uploadError = "Error al subir la foto. Inténtalo de nuevo." }
                isUploading = false
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar cuenta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Esta acción es permanente.")
                    OutlinedTextField(value = deletePass, onValueChange = { deletePass = it },
                        label = { Text("Confirma tu contraseña") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Button(onClick = { onDeleteAccount(deletePass) }, enabled = deletePass.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Eliminar")
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false; deletePass = "" }) { Text("Cancelar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    Box(Modifier.size(80.dp).clickable(enabled = !isUploading) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        val displayUrl = NewsickRetrofit.absoluteUrl(profilePhoto)
                        if (isUploading) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
                        } else if (displayUrl.isNotBlank()) {
                            AsyncImage(model = displayUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        if (!isUploading) {
                            Surface(modifier = Modifier.align(Alignment.BottomEnd).size(24.dp),
                                shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.padding(4.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
                uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = email, onValueChange = {}, label = { Text("Correo electrónico") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = false)
                OutlinedTextField(
                    value = username, onValueChange = { if (it.length <= 30) username = it },
                    label = { Text("Nombre de usuario") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = usernameError != null, supportingText = usernameError?.let { { Text(it) } }
                )
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3)
                HorizontalDivider()
                TextButton(onClick = { showDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Eliminar cuenta permanentemente")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(username, bio, profilePhoto) },
                enabled = username.isNotBlank() && usernameError == null && !isUploading) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
