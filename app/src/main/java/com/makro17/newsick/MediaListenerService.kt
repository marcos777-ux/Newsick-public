// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
// Newsick es software propietario. Queda prohibida su copia, modificación,
// distribución o ingeniería inversa sin autorización expresa del autor.

package com.makro17.newsick

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.provider.Settings

// ══════════════════════════════════════════════════════════
// SERVICIO DE ESCUCHA DE NOTIFICACIONES
// Necesario para que MediaSessionManager pueda leer sesiones
// de otras apps (Spotify, YouTube Music, etc.)
// El usuario debe activarlo en Ajustes > Acceso a notificaciones
// ══════════════════════════════════════════════════════════

class MediaListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Solo necesitamos que el servicio esté activo;
        // la detección real se hace vía MediaSessionManager
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}

// ══════════════════════════════════════════════════════════
// UTILIDADES PARA DETECTAR CANCIÓN EN REPRODUCCIÓN
// ══════════════════════════════════════════════════════════

data class NowPlayingInfo(
    val title: String,
    val artist: String,
    val packageName: String = ""
)

/** Devuelve la canción que se está reproduciendo en cualquier app, o null */
fun getNowPlayingInfo(context: Context): NowPlayingInfo? {
    return try {
        if (!isNotificationListenerEnabled(context)) return null
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? MediaSessionManager ?: return null
        val cn = ComponentName(context, MediaListenerService::class.java)
        val sessions = msm.getActiveSessions(cn)
        for (session in sessions) {
            val meta = session.metadata ?: continue
            val title  = meta.getString(MediaMetadata.METADATA_KEY_TITLE)  ?: continue
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                      ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                      ?: ""
            if (title.isNotBlank()) {
                return NowPlayingInfo(title, artist, session.packageName ?: "")
            }
        }
        null
    } catch (_: Exception) { null }
}

/** Comprueba si el usuario ha concedido acceso a notificaciones */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.contains(context.packageName)
}
