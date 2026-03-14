// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ══════════════════════════════════════════════════════════
// PANTALLA DE ACTIVIDAD
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit,
    onRequestsClick: () -> Unit,
    onChatClick: (conversationId: Int) -> Unit = {}
) {
    val notifications by viewModel.notifications
    val pendingCount  = viewModel.pendingRequests.value.size

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
                IconButton(onClick = { viewModel.loadNotificationsData() }) {
                    Icon(Icons.Default.Refresh, "Actualizar")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Botón solicitudes con contador a la izquierda ──
            item {
                FilledTonalButton(
                    onClick = onRequestsClick,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    if (pendingCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                "$pendingCount",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Icon(Icons.Default.PeopleAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Solicitudes")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            // ── Notificaciones ──────────────────────────────
            if (notifications.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsNone, null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Sin notificaciones",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(notifications, key = { it.id }) { notif ->
                    NotificationItem(
                        notification = notif,
                        onMarkRead   = { viewModel.markNotificationRead(notif.id) },
                        onClick      = {
                            if (notif.type == "message" && notif.referenceId != null) {
                                viewModel.markNotificationRead(notif.id)
                                onChatClick(notif.referenceId)
                            } else {
                                viewModel.markNotificationRead(notif.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// PANTALLA DE SOLICITUDES DE AMISTAD
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    val pendingRequests by viewModel.pendingRequests

    LaunchedEffect(Unit) { viewModel.loadNotificationsData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solicitudes de amistad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        if (pendingRequests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PeopleAlt, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No tienes solicitudes pendientes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pendingRequests, key = { it.id }) { req ->
                    FriendRequestItem(
                        request     = req,
                        onAccept    = { viewModel.respondToFriendRequest(req.id, true) },
                        onReject    = { viewModel.respondToFriendRequest(req.id, false) },
                        onUserClick = { onUserClick(req.senderId) }
                    )
                }
            }
        }
    }
}

// ── Tarjeta de solicitud de amistad ───────────────────────

@Composable
fun FriendRequestItem(
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
            if (request.senderProfilePhoto.isNotBlank()) {
                AsyncImage(
                    model = request.senderProfilePhoto, contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onUserClick() },
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.AccountCircle, null,
                    modifier = Modifier.size(48.dp).clickable { onUserClick() },
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.senderUsername,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clickable { onUserClick() })
                Text("Quiere ser tu amigo/a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("Aceptar", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Rechazar", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ── Tarjeta de notificación ───────────────────────────────

@Composable
private fun NotificationItem(
    notification: NotificationResponse,
    onMarkRead: () -> Unit,
    onClick: () -> Unit = {}
) {
    val containerColor = if (!notification.isRead)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text(notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!notification.isRead) {
                IconButton(onClick = onMarkRead, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.CheckCircleOutline, "Marcar como leída",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
