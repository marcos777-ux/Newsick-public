// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════
// Estilo de mapa: sin etiquetas de calles ni establecimientos
// ══════════════════════════════════════════════════════════
private const val MAP_STYLE = """
[
  {"elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi",    "stylers": [{"visibility": "off"}]},
  {"featureType": "transit","stylers": [{"visibility": "off"}]}
]
"""

// ── Información de plataforma ─────────────────────────────

data class PlatformInfo(val emoji: String, val label: String, val color: Color, val hue: Float)

fun getPlatformInfo(platform: String?) = when (platform?.lowercase()) {
    "spotify"     -> PlatformInfo("🟢", "Spotify",     Color(0xFF1DB954), BitmapDescriptorFactory.HUE_GREEN)
    "youtube"     -> PlatformInfo("🔴", "YouTube",     Color(0xFFFF0000), BitmapDescriptorFactory.HUE_RED)
    "soundcloud"  -> PlatformInfo("🟠", "SoundCloud",  Color(0xFFFF5500), BitmapDescriptorFactory.HUE_ORANGE)
    "apple_music" -> PlatformInfo("🍎", "Apple Music", Color(0xFFFC3C44), BitmapDescriptorFactory.HUE_ROSE)
    "tidal"       -> PlatformInfo("🔵", "Tidal",       Color(0xFF1A1A2E), BitmapDescriptorFactory.HUE_AZURE)
    else          -> PlatformInfo("🎵", "Newsick",     Color(0xFF6650A4), BitmapDescriptorFactory.HUE_VIOLET)
}

// ══════════════════════════════════════════════════════════
// PANTALLA PRINCIPAL DEL MAPA
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MainViewModel,
    onUserClick: (Int) -> Unit
) {
    val context      = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var mapLoaded          by remember { mutableStateOf(false) }
    var currentLatLng      by remember { mutableStateOf<LatLng?>(null) }
    var selectedUser       by remember { mutableStateOf<NearbyUserResponse?>(null) }
    var showBottomSheet    by remember { mutableStateOf(false) }
    var showPlatformDialog by remember { mutableStateOf(false) }
    // "auto" = cualquier plataforma, "no" = ocultar ubicación, o nombre de plataforma específica
    var selectedPlatform   by remember { mutableStateOf("auto") }
    // Visibilidad derivada de la selección
    val locationVisible    = selectedPlatform != "no"
    // ── Canción detectada del sistema ──────────────────────
    var nowPlayingDetected by remember { mutableStateOf<NowPlayingInfo?>(null) }
    var hasNotifAccess     by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    val nearbyUsers = viewModel.nearbyUsers.value

    // Filtrar la canción mostrada según la plataforma seleccionada
    val displayTrack: Pair<String, String>? = when {
        selectedPlatform == "no"           -> null
        nowPlayingDetected == null         -> null
        selectedPlatform == "auto"         -> Pair(nowPlayingDetected!!.title, nowPlayingDetected!!.artist)
        else -> {
            val pkg = nowPlayingDetected!!.packageName
            val matches = when (selectedPlatform) {
                "spotify"     -> "spotify"    in pkg
                "youtube"     -> "youtube"    in pkg
                "soundcloud"  -> "soundcloud" in pkg
                "apple_music" -> "apple"      in pkg
                "tidal"       -> "tidal"      in pkg
                else          -> true
            }
            if (matches) Pair(nowPlayingDetected!!.title, nowPlayingDetected!!.artist) else null
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.4168, -3.7038), 15f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Lanzador para abrir los ajustes de acceso a notificaciones
    val notifSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasNotifAccess = isNotificationListenerEnabled(context) }

    // ── Detectar canción en reproducción: al entrar y cada 5 s ─
    LaunchedEffect(hasNotifAccess) {
        while (true) {
            if (hasNotifAccess) {
                nowPlayingDetected = getNowPlayingInfo(context)
            } else {
                nowPlayingDetected = null
            }
            delay(5_000)
        }
    }

    // ── Obtener posición GPS ───────────────────────────────
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val fused = LocationServices.getFusedLocationProviderClient(context)
        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    currentLatLng = latLng
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    viewModel.loadNearbyUsers(it.latitude, it.longitude)
                }
            }
        } catch (_: SecurityException) {}
    }

    // ── Publicar/retirar ubicación al cambiar selección ───
    LaunchedEffect(selectedPlatform, currentLatLng) {
        val ll = currentLatLng ?: return@LaunchedEffect
        if (locationVisible) {
            val platform = if (selectedPlatform == "auto") "newsick" else selectedPlatform
            viewModel.updateLocationWithPlatform(ll.latitude, ll.longitude, platform)
            viewModel.loadNearbyUsers(ll.latitude, ll.longitude)
        } else {
            viewModel.removeLocationFromMap()
        }
    }

    // ── Refrescar usuarios cercanos cada 30 s ─────────────
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        while (true) {
            delay(30_000)
            currentLatLng?.let { viewModel.loadNearbyUsers(it.latitude, it.longitude) }
        }
    }

    // ── Limpiar ubicación al salir ─────────────────────────
    DisposableEffect(Unit) {
        onDispose { viewModel.removeLocationFromMap() }
    }

    // ── Sin permiso ───────────────────────────────────────
    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.LocationOff, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text("Activa la ubicación para ver a otros usuarios cerca",
                    style = MaterialTheme.typography.bodyMedium)
                Button({ permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Permitir ubicación")
                }
            }
        }
        return
    }

    // ── Mapa ──────────────────────────────────────────────
    Box(Modifier.fillMaxSize()) {

        GoogleMap(
            modifier            = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapStyleOptions     = MapStyleOptions(MAP_STYLE),
                mapType             = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true,
                zoomControlsEnabled     = true,
                compassEnabled          = true
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            // Marcador por cada usuario cercano
            nearbyUsers.forEach { user ->
                val pos      = LatLng(user.latitude, user.longitude)
                val platInfo = getPlatformInfo(user.platform)

                MarkerInfoWindowContent(
                    state   = MarkerState(position = pos),
                    title   = user.username,
                    snippet = user.trackName ?: "Sin canción",
                    icon    = BitmapDescriptorFactory.defaultMarker(platInfo.hue),
                    onClick = {
                        selectedUser    = user
                        showBottomSheet = true
                        false
                    }
                ) {
                    // Info window personalizada al pulsar el pin
                    Surface(
                        shape          = RoundedCornerShape(10.dp),
                        color          = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier       = Modifier.padding(4.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(platInfo.emoji, fontSize = 20.sp)
                            Column {
                                Text(user.username,
                                    fontWeight = FontWeight.Bold,
                                    style      = MaterialTheme.typography.labelMedium)
                                if (user.trackName != null) {
                                    Text(
                                        "♫ ${user.trackName}",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 160.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Spinner mientras carga el mapa
        if (!mapLoaded) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }

        // ── Overlays superiores ────────────────────────────
        Column(
            modifier            = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chip "Escuchando ahora" — pulsarlo abre el diálogo de ajustes
            Surface(
                shape           = RoundedCornerShape(20.dp),
                color           = MaterialTheme.colorScheme.surface,
                tonalElevation  = 6.dp,
                shadowElevation = 4.dp,
                modifier        = Modifier.clickable { showPlatformDialog = true }
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        selectedPlatform == "no" -> {
                            Icon(Icons.Default.LocationOff, null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Ubicación oculta · Toca para cambiar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        displayTrack != null -> {
                            val pkg = nowPlayingDetected?.packageName ?: ""
                            val platEmoji = when {
                                "spotify"    in pkg -> "🟢"
                                "youtube"    in pkg -> "🔴"
                                "soundcloud" in pkg -> "🟠"
                                "apple"      in pkg -> "🍎"
                                "tidal"      in pkg -> "🔵"
                                else                -> "🎵"
                            }
                            Text(platEmoji, fontSize = 24.sp)
                            Column {
                                Text(
                                    "Escuchando ahora",
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    buildString {
                                        append(displayTrack.first)
                                        if (displayTrack.second.isNotBlank()) append(" · ${displayTrack.second}")
                                    },
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.widthIn(max = 200.dp)
                                )
                            }
                            Icon(Icons.Default.MusicNote, null,
                                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        else -> {
                            Icon(Icons.Default.MusicOff, null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column {
                                Text(
                                    if (hasNotifAccess) "Sin canción en reproducción"
                                    else "Detectar música en reproducción",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Toca para ajustar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Contador de usuarios cercanos
            if (mapLoaded && nearbyUsers.isNotEmpty()) {
                Surface(
                    shape          = RoundedCornerShape(20.dp),
                    color          = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        "${nearbyUsers.size} persona${if (nearbyUsers.size != 1) "s" else ""} escuchando cerca",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

    }

    // ══════════════════════════════════════════════════════
    // BOTTOM SHEET: detalle del usuario seleccionado
    // ══════════════════════════════════════════════════════
    if (showBottomSheet && selectedUser != null) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
            UserMusicCard(
                user           = selectedUser!!,
                onVisitProfile = {
                    showBottomSheet = false
                    onUserClick(selectedUser!!.userId)
                },
                onAddFriend = {
                    viewModel.sendFriendRequest(selectedUser!!.userId) {}
                    showBottomSheet = false
                }
            )
        }
    }

    // ══════════════════════════════════════════════════════
    // DIÁLOGO: ajustes de escucha y visibilidad
    // ══════════════════════════════════════════════════════
    if (showPlatformDialog) {
        AlertDialog(
            onDismissRequest = { showPlatformDialog = false },
            title = { Text("¿Dónde escuchas?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

                    // ── Activar detección si no hay acceso ────
                    if (!hasNotifAccess) {
                        Surface(
                            shape  = RoundedCornerShape(8.dp),
                            color  = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPlatformDialog = false
                                    notifSettingsLauncher.launch(
                                        android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("Activar detección de música",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // ── Opciones de plataforma ─────────────
                    val options = listOf(
                        Triple("auto",        "🎵", "Automático"),
                        Triple("spotify",     "🟢", "Spotify"),
                        Triple("youtube",     "🔴", "YouTube"),
                        Triple("soundcloud",  "🟠", "SoundCloud"),
                        Triple("apple_music", "🍎", "Apple Music"),
                        Triple("tidal",       "🔵", "Tidal"),
                        Triple("no",          "🚫", "No compartir")
                    )
                    options.forEach { (key, emoji, label) ->
                        val selected = selectedPlatform == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedPlatform = key
                                    val ll = currentLatLng
                                    if (key == "no") {
                                        viewModel.removeLocationFromMap()
                                    } else if (ll != null) {
                                        val platform = if (key == "auto") "newsick" else key
                                        viewModel.updateLocationWithPlatform(ll.latitude, ll.longitude, platform)
                                    }
                                    showPlatformDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(emoji, fontSize = 22.sp)
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                if (key == "auto") {
                                    Text("Muestra cualquier canción en reproducción",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (key == "no") {
                                    Text("Tu ubicación no será visible para nadie",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (selected) {
                                Icon(Icons.Default.Check, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ══════════════════════════════════════════════════════════
// TARJETA DE USUARIO — bottom sheet
// ══════════════════════════════════════════════════════════

@Composable
fun UserMusicCard(
    user: NearbyUserResponse,
    onVisitProfile: () -> Unit,
    onAddFriend: () -> Unit
) {
    val platInfo  = getPlatformInfo(user.platform)
    var isPlaying by remember { mutableStateOf(false) }
    var player    by remember { mutableStateOf<MediaPlayer?>(null) }

    // Liberar el reproductor al cerrar el sheet
    DisposableEffect(user.userId) {
        onDispose {
            player?.stop()
            player?.release()
            player = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Cabecera: avatar + nombre + plataforma ────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(52.dp)) {
                if (user.profilePhoto.isNotBlank()) {
                    AsyncImage(
                        model              = user.profilePhoto,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.AccountCircle, null,
                        Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(user.username,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                // Badge de plataforma
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = platInfo.color.copy(alpha = 0.15f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(platInfo.emoji, fontSize = 12.sp)
                        Text(platInfo.label,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = platInfo.color,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Canción actual ────────────────────────────────
        if (user.trackName != null) {
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Portada del álbum
                    if (user.artworkUrl != null) {
                        AsyncImage(
                            model              = user.artworkUrl,
                            contentDescription = null,
                            modifier           = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Título y artista
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.trackName,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (user.artistName != null) {
                            Text(
                                user.artistName,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Etiqueta "preview 30s"
                        if (user.previewUrl != null) {
                            Text("Preview 30s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        }
                    }

                    // ── Botón de reproducción ─────────────
                    if (user.previewUrl != null) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    player?.pause()
                                    isPlaying = false
                                } else {
                                    if (player == null) {
                                        player = MediaPlayer().apply {
                                            setDataSource(user.previewUrl)
                                            setOnPreparedListener { start() }
                                            setOnCompletionListener { isPlaying = false }
                                            prepareAsync()
                                        }
                                    } else {
                                        player?.start()
                                    }
                                    isPlaying = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector        = if (isPlaying) Icons.Default.Pause
                                else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Escuchar preview",
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.MusicOff, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No está escuchando nada ahora mismo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Botones de acción ─────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick  = onVisitProfile,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ver perfil")
            }
            Button(
                onClick  = onAddFriend,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir")
            }
        }
    }
}