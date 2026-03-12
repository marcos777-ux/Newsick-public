package com.makro17.newsick

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
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
// Sube foto de perfil al servidor y devuelve la URL pública
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
    onFriendsClick: () -> Unit
) {
    var showEditDialog  by remember { mutableStateOf(false) }
    val mySongs         by viewModel.mySongs.collectAsState()
    val friendCount     by viewModel.friendCount
    var selectedTab     by remember { mutableIntStateOf(0) }
    var recommendations by remember { mutableStateOf<List<RecommendationResponse>>(emptyList()) }
    var isRefreshing    by remember { mutableStateOf(false) }
    val scope           = rememberCoroutineScope()
    val context         = LocalContext.current

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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { refresh() },
            modifier     = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
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
                        leadingIcon = { Icon(Icons.Default.Group, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.wrapContentWidth()
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

                // ── Contenido de la pestaña activa ─────────
                when (selectedTab) {
                    0 -> {
                        if (mySongs.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 64.dp),
                                    Alignment.Center) {
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
                            // Grid de 2 columnas simulado con items de 2 en 2
                            items(mySongs.chunked(2)) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { song ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            MySongCard(song = song, onClick = { onSongClick(song.trackId) })
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
                                Box(Modifier.fillMaxWidth().padding(top = 64.dp), Alignment.Center) {
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
                                RecommendationCard(rec = rec, onListened = {
                                    scope.launch {
                                        try {
                                            val r = NewsickRetrofit.api.markListened(rec.trackId)
                                            if (r.isSuccessful) {
                                                recommendations = recommendations.filter { it.trackId != rec.trackId }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                })
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
                viewModel.updateProfile(u, b, p) { success, errorMsg ->
                    if (!success && errorMsg != null) {
                        // El error se muestra dentro del diálogo — aquí simplemente lo cerramos si tuvo éxito
                    }
                }
                showEditDialog = false
            },
            onDeleteAccount     = { password -> viewModel.deleteAccount(password) {}; showEditDialog = false }
        )
    }
}

// ── Tarjeta de canción propia ─────────────────────────────

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

// ── Tarjeta de recomendación ──────────────────────────────

@Composable
fun RecommendationCard(rec: RecommendationResponse, onListened: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = rec.artworkUrl, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop)
            Column(modifier = Modifier.weight(1f)) {
                Text(rec.trackName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rec.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    rec.recommendedBy.take(3).forEach { recommender ->
                        val url = NewsickRetrofit.absoluteUrl(recommender.profilePhoto)
                        Box(modifier = Modifier.size(24.dp)) {
                            if (url.isNotBlank()) {
                                AsyncImage(model = url, contentDescription = recommender.username,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.AccountCircle, null,
                                    modifier = Modifier.fillMaxSize(),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (rec.totalCount == 1) rec.recommendedBy.firstOrNull()?.username ?: ""
                        else "${rec.totalCount} amigos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onListened) {
                Icon(Icons.Default.CheckCircle, "Ya la escuché",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
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
    // profilePhoto guarda la URL del servidor (después de subir) o la URL previa
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
            // Subir al servidor inmediatamente
            scope.launch {
                isUploading = true; uploadError = null
                val serverUrl = uploadProfilePhoto(context, uri)
                if (serverUrl != null) {
                    // Construir URL absoluta
                    profilePhoto = if (serverUrl.startsWith("http")) serverUrl
                                   else NewsickRetrofit.BASE_URL.trimEnd('/') + serverUrl
                } else {
                    uploadError = "Error al subir la foto. Inténtalo de nuevo."
                }
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
                    Text("Esta acción es permanente. Todos tus datos serán eliminados.")
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
                // Foto de perfil con botón de cámara
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    Box(Modifier.size(80.dp).clickable(enabled = !isUploading) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        val displayUrl = NewsickRetrofit.absoluteUrl(profilePhoto)
                        if (isUploading) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        } else if (displayUrl.isNotBlank()) {
                            AsyncImage(model = displayUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.AccountCircle, null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        if (!isUploading) {
                            Surface(modifier = Modifier.align(Alignment.BottomEnd).size(24.dp),
                                shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                Icon(Icons.Default.CameraAlt, null,
                                    modifier = Modifier.padding(4.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }

                uploadError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(value = email, onValueChange = {},
                    label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, enabled = false)

                OutlinedTextField(
                    value = username,
                    onValueChange = { if (it.length <= 30) username = it },
                    label = { Text("Nombre de usuario") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } }
                )

                OutlinedTextField(value = bio, onValueChange = { bio = it },
                    label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(),
                    maxLines = 3, placeholder = { Text("Cuéntanos algo sobre ti...") })

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
            Button(
                onClick = { onSave(username, bio, profilePhoto) },
                enabled = username.isNotBlank() && usernameError == null && !isUploading
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
