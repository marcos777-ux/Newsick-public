// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
package com.makro17.newsick

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID   = "newsick_notifications"
    private const val CHANNEL_NAME = "Newsick"

    // Extras para deep link
    const val EXTRA_NAV_TARGET       = "nav_target"   // "chat"
    const val EXTRA_CONVERSATION_ID  = "conversation_id"
    const val EXTRA_OTHER_USER_ID    = "other_user_id"
    const val EXTRA_OTHER_USERNAME   = "other_username"
    const val EXTRA_OTHER_PHOTO      = "other_photo"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notificaciones de Newsick" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    /** Notificación genérica (sin deep link). */
    fun show(context: Context, id: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        showWithIntent(context, id, title, message, intent)
    }

    /** Notificación de mensaje de chat con deep link a la conversación. */
    fun showChatMessage(
        context: Context,
        notifId: Int,
        senderUsername: String,
        preview: String,
        conversationId: Int,
        otherUserId: Int,
        otherPhoto: String = ""
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TARGET,      "chat")
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_OTHER_USER_ID,   otherUserId)
            putExtra(EXTRA_OTHER_USERNAME,  senderUsername)
            putExtra(EXTRA_OTHER_PHOTO,     otherPhoto)
        }
        showWithIntent(context, notifId, "Mensaje de $senderUsername", preview, intent)
    }

    /** Cancela la notificación del sistema para una conversación. */
    fun cancelChat(context: Context, conversationId: Int) {
        // Usamos el conversationId como notifId para las notifs de chat
        NotificationManagerCompat.from(context).cancel(conversationId + 10000)
    }

    private fun showWithIntent(context: Context, id: Int, title: String, message: String, intent: Intent) {
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {}
    }
}
