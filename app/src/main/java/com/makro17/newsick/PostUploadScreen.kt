// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "PostUpload"

// ══════════════════════════════════════════════════════════
// FLUJO DE PUBLICACIÓN
// Paso 0: buscar canción en iTunes
// Paso 1: seleccionar fotos, subirlas al servidor, publicar
// ══════════════════════════════════════════════════════════

@Composable
fun PostUploadScreen(
    viewModel: MainViewModel,
    onPostCreated: () -> Unit,
    onBack: () -> Unit
) {
    var step          by remember { mutableStateOf(0) }
    var searchQuery   by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ItunesTrack>>(emptyList()) }
    var isSearching   by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<ItunesTrack?>(null) }
    val selectedUris   = remember { mutableStateListOf<Uri>() }

    var isUploading  by remember { mutableStateOf(false) }
    var statusMsg    by remember { mutableStateOf("") }
    var errorMsg     by remember { mutableStateOf<String?>(null) }

    // Si hay una pista pendiente (desde recomendación), saltar al paso 1
    LaunchedEffect(Unit) {
        viewModel.pendingUploadTrack.value?.let { pending ->
            selectedTrack = ItunesTrack(
                trackId    = pending.trackId.toLongOrNull() ?: 0L,
                trackName  = pending.trackName,
                artistName = pending.artistName,
                artworkUrl100 = pending.artworkUrl.replace("300x300", "100x100"),
                previewUrl = pending.previewUrl
            )
            step = 1
            viewModel.pendingUploadTrack.value = null
        }
    }

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        selectedUris.addAll(uris.filter { it !in selectedUris })
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (step == 0) onBack()
                    else { step = 0; errorMsg = null; statusMsg = "" }
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                Text(
                    if (step == 0) "Buscar canción" else "Añadir fotos",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (step) {

                // ── Paso 0: búsqueda ──────────────────────
                0 -> SongSearchStep(
                    query          = searchQuery,
                    onQueryChange  = { q -> searchQuery = q },
                    results        = searchResults,
                    isSearching    = isSearching,
                    onResultsReady = { searchResults = it; isSearching = false },
                    onSearching    = { isSearching = true },
                    onTrackSelected = { track ->
                        selectedTrack = track
                        selectedUris.clear()
                        errorMsg = null; statusMsg = ""
                        step = 1
                    }
                )

                // ── Paso 1: fotos ─────────────────────────
                1 -> PhotoSelectionStep(
                    track          = selectedTrack!!,
                    selectedUris   = selectedUris,
                    isUploading    = isUploading,
                    statusMsg      = statusMsg,
                    errorMsg       = errorMsg,
                    onAddPhotos    = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveUri    = { selectedUris.remove(it) },
                    onPublish      = {
                        scope.launch {
                            isUploading = true
                            errorMsg    = null
                            val t       = selectedTrack!!
                            val uploadedUrls = mutableListOf<String>()

                            selectedUris.forEachIndexed { idx, uri ->
                                statusMsg = "Subiendo foto ${idx + 1} de ${selectedUris.size}…"
                                Log.d(TAG, "Subiendo $uri")

                                val relUrl = uploadPhoto(context, uri)
                                if (relUrl != null) {
                                    val abs = if (relUrl.startsWith("http")) relUrl
                                    else NewsickRetrofit.BASE_URL.trimEnd('/') + relUrl
                                    uploadedUrls.add(abs)
                                    Log.d(TAG, "OK → $abs")
                                } else {
                                    errorMsg    = "❌ Error al subir foto ${idx + 1}. Comprueba tu conexión e inténtalo de nuevo."
                                    statusMsg   = ""
                                    isUploading = false
                                    return@launch
                                }
                            }

                            statusMsg = "Guardando publicación…"
                            Log.d(TAG, "Creando post con ${uploadedUrls.size} foto(s)")
                            val ok = viewModel.createPostAsync(
                                trackId    = t.trackId.toString(),
                                trackName  = t.trackName,
                                artistName = t.artistName,
                                artworkUrl = t.artworkUrl300,
                                photoUris  = uploadedUrls
                            )
                            isUploading = false
                            if (ok) {
                                onPostCreated()
                            } else {
                                errorMsg  = "❌ Las fotos se subieron pero no se pudo guardar la publicación. Inténtalo de nuevo."
                                statusMsg = ""
                            }
                        }
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Sube una sola foto al servidor — devuelve "/uploads/xxx.jpg" o null
// ══════════════════════════════════════════════════════════
private suspend fun uploadPhoto(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                Log.e(TAG, "No se pudo abrir el stream para $uri")
                return@withContext null
            }
            val bytes = stream.readBytes()
            stream.close()

            if (bytes.isEmpty()) {
                Log.e(TAG, "El archivo está vacío: $uri")
                return@withContext null
            }

            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext  = when (mime) {
                "image/png"  -> "png"
                "image/webp" -> "webp"
                else         -> "jpg"
            }

            val body = bytes.toRequestBody(mime.toMediaType())
            val part = MultipartBody.Part.createFormData("photo", "photo.$ext", body)

            val resp = NewsickRetrofit.api.uploadPhoto(part)
            if (resp.isSuccessful) {
                val url = resp.body()?.url
                Log.d(TAG, "Upload OK: $url")
                url
            } else {
                Log.e(TAG, "Upload HTTP ${resp.code()}: ${resp.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${e.message}", e)
            null
        }
    }

// ── Paso 0: búsqueda de canción ───────────────────────────

@Composable
private fun SongSearchStep(
    query: String, onQueryChange: (String) -> Unit,
    results: List<ItunesTrack>, isSearching: Boolean,
    onResultsReady: (List<ItunesTrack>) -> Unit,
    onSearching: () -> Unit,
    onTrackSelected: (ItunesTrack) -> Unit
) {
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { q ->
                onQueryChange(q)
                job?.cancel()
                if (q.length >= 2) {
                    onSearching()
                    job = scope.launch {
                        delay(400)
                        try {
                            val res = withContext(Dispatchers.IO) { ItunesRetrofit.api.searchMusic(q) }
                            onResultsReady(res.results.filter { it.trackId != 0L && it.trackName.isNotBlank() })
                        } catch (_: Exception) { onResultsReady(emptyList()) }
                    }
                } else onResultsReady(emptyList())
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Nombre de canción o artista…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        when {
            isSearching ->
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }

            query.length >= 2 && results.isEmpty() ->
                Text(
                    text = "Sin resultados para \"$query\"",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results) { t -> TrackRow(t) { onTrackSelected(t) } }
                }
        }
    }
}

@Composable
private fun TrackRow(track: ItunesTrack, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(track.artworkUrl100, null,
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.trackName, style = MaterialTheme.typography.titleSmall)
                Text(track.artistName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                track.collectionName?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// ── Paso 1: selección de fotos ────────────────────────────

@Composable
private fun PhotoSelectionStep(
    track: ItunesTrack,
    selectedUris: List<Uri>,
    isUploading: Boolean,
    statusMsg: String,
    errorMsg: String?,
    onAddPhotos: () -> Unit,
    onRemoveUri: (Uri) -> Unit,
    onPublish: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Canción elegida
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(track.artworkUrl300, null,
                        Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(track.trackName, style = MaterialTheme.typography.titleMedium)
                        Text(track.artistName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Botón añadir
        item {
            OutlinedButton(
                onClick = onAddPhotos,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                Icon(Icons.Default.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp))
                Text("Añadir fotos (máx. 10)")
            }
        }

        // Contador
        if (selectedUris.isNotEmpty()) {
            item {
                Text("${selectedUris.size} foto(s) seleccionada(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        // Error visible
        errorMsg?.let { msg ->
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Previsualización de fotos
        items(selectedUris) { uri ->
            Box {
                AsyncImage(uri, null,
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop)
                if (!isUploading) {
                    IconButton(
                        onClick = { onRemoveUri(uri) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Cancel, "Quitar",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Botón publicar / spinner
        item {
            if (isUploading) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(statusMsg, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Button(
                    onClick = onPublish,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedUris.isNotEmpty()
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}