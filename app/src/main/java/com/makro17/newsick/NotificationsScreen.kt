// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ══════════════════════════════════════════════════════════
// PANTALLA DE SOLICITUDES Y NOTIFICACIONES
// ══════════════════════════════════════════════════════════

@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    val pendingRequests by viewModel.pendingRequests
    val notifications   by viewModel.notifications
    var selectedTab     by remember { mutableStateOf(0) }

    // Refrescar al abrir la pantalla
    LaunchedEffect(Unit) { viewModel.loadNotificationsData() }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                }
                Text("Actividad", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                // Botón refrescar
                IconButton(onClick = { viewModel.loadNotificationsData() }) {
                    Icon(Icons.Default.Refresh, "Actualizar")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Pestañas
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        BadgedBox(
                            badge = {
                                if (pendingRequests.isNotEmpty()) {
                                    Badge { Text(pendingRequests.size.toString()) }
                                }
                            }
                        ) { Text("Solicitudes") }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val unread = notifications.count { !it.isRead }
                        BadgedBox(
                            badge = {
                                if (unread > 0) Badge { Text(unread.toString()) }
                            }
                        ) { Text("Notificaciones") }
                    }
                )
            }

            when (selectedTab) {
                0 -> FriendRequestsTab(
                    requests = pendingRequests,
                    onAccept = { req -> viewModel.respondToFriendRequest(req.id, true) },
                    onReject = { req -> viewModel.respondToFriendRequest(req.id, false) },
                    onUserClick = onUserClick
                )
                1 -> NotificationsTab(
                    notifications = notifications,
                    onMarkRead = { notif -> viewModel.markNotificationRead(notif.id) }
                )
            }
        }
    }
}

// ── Pestaña: solicitudes de amistad ───────────────────────

@Composable
private fun FriendRequestsTab(
    requests: List<FriendRequestResponse>,
    onAccept: (FriendRequestResponse) -> Unit,
    onReject: (FriendRequestResponse) -> Unit,
    onUserClick: (Int) -> Unit
) {
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PeopleAlt, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No tienes solicitudes pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(requests, key = { it.id }) { req ->
            FriendRequestItem(
                request = req,
                onAccept = { onAccept(req) },
                onReject = { onReject(req) },
                onUserClick = { onUserClick(req.senderId) }
            )
        }
    }
}

@Composable
private fun FriendRequestItem(
    request: FriendRequestResponse,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onUserClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            if (request.senderProfilePhoto.isNotBlank()) {
                AsyncImage(
                    model = request.senderProfilePhoto,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
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

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.senderUsername,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.let { mod ->
                        mod // Could make clickable here too
                    }
                )
                Text(
                    "Quiere ser tu amigo/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Botones
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Aceptar", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Rechazar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Pestaña: notificaciones generales ─────────────────────

@Composable
private fun NotificationsTab(
    notifications: List<NotificationResponse>,
    onMarkRead: (NotificationResponse) -> Unit
) {
    if (notifications.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Sin notificaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(notifications, key = { it.id }) { notif ->
            NotificationItem(notification = notif, onMarkRead = { onMarkRead(notif) })
        }
    }
}

@Composable
private fun NotificationItem(
    notification: NotificationResponse,
    onMarkRead: () -> Unit
) {
    val containerColor = if (!notification.isRead)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono según tipo
            val icon = when (notification.type) {
                "friend_request"  -> Icons.Default.PersonAdd
                "friend_accepted" -> Icons.Default.PeopleAlt
                "new_post"        -> Icons.Default.MusicNote
                else              -> Icons.Default.Notifications
            }
            val iconTint = when (notification.type) {
                "friend_request"  -> MaterialTheme.colorScheme.primary
                "friend_accepted" -> MaterialTheme.colorScheme.tertiary
                "new_post"        -> MaterialTheme.colorScheme.secondary
                else              -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = iconTint)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(notification.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!notification.isRead) {
                IconButton(onClick = onMarkRead, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.CheckCircleOutline, "Marcar como leída",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
