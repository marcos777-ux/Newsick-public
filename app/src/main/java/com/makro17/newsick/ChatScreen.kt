// Copyright (c) 2026 Marcos Quintero Hernández. Todos los derechos reservados.
package com.makro17.newsick

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ══════════════════════════════════════════════════════════
// CONFIGURACIÓN IA (guardada en SharedPreferences)
// ══════════════════════════════════════════════════════════

data class AiConfig(
    val provider: String,   // openai | anthropic | google
    val apiKey: String,
    val model: String
)

fun loadAiConfig(context: Context): AiConfig? {
    val prefs = context.getSharedPreferences("newsick_ai", Context.MODE_PRIVATE)
    val provider = prefs.getString("provider", null) ?: return null
    val apiKey   = prefs.getString("api_key", null)  ?: return null
    val model    = prefs.getString("model", null)    ?: return null
    return AiConfig(provider, apiKey, model)
}

fun saveAiConfig(context: Context, config: AiConfig) {
    context.getSharedPreferences("newsick_ai", Context.MODE_PRIVATE).edit()
        .putString("provider", config.provider)
        .putString("api_key", config.apiKey)
        .putString("model", config.model)
        .apply()
}

suspend fun callAi(config: AiConfig, chatHistory: List<MessageResponse>, replyTo: MessageResponse): String =
    withContext(Dispatchers.IO) {
        try {
            val http = OkHttpClient()
            // Construir contexto del chat (últimos 10 mensajes)
            val context = chatHistory.takeLast(10).joinToString("\n") {
                val role = if (it.isAiReply) "IA" else it.senderUsername
                "$role: ${it.content}"
            }
            val prompt = "Historial del chat:\n$context\n\nResponde al siguiente mensaje de forma natural y breve:\n${replyTo.senderUsername}: ${replyTo.content}"

            when (config.provider) {
                "openai" -> {
                    val body = JSONObject().apply {
                        put("model", config.model)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", prompt))
                        })
                        put("max_tokens", 300)
                    }
                    val req = Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val resp = http.newCall(req).execute()
                    val json = JSONObject(resp.body!!.string())
                    json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                }
                "anthropic" -> {
                    val body = JSONObject().apply {
                        put("model", config.model)
                        put("max_tokens", 300)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", prompt))
                        })
                    }
                    val req = Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .addHeader("x-api-key", config.apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val resp = http.newCall(req).execute()
                    val json = JSONObject(resp.body!!.string())
                    json.getJSONArray("content").getJSONObject(0).getString("text").trim()
                }
                "google" -> {
                    val body = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().put("text", prompt))
                                })
                            })
                        })
                    }
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}")
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val resp = http.newCall(req).execute()
                    val json = JSONObject(resp.body!!.string())
                    json.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim()
                }
                else -> "Proveedor de IA no reconocido"
            }
        } catch (e: Exception) {
            "Error al contactar la IA: ${e.message}"
        }
    }

// ══════════════════════════════════════════════════════════
// PANTALLA DE CHAT
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Int,
    otherUserId: Int,
    otherUsername: String,
    otherProfilePhoto: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit = {}
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val listState  = rememberLazyListState()
    val myId       = viewModel.loggedUserId.value

    var messages       by remember { mutableStateOf<List<MessageResponse>>(emptyList()) }
    var text           by remember { mutableStateOf("") }
    var isSending      by remember { mutableStateOf(false) }
    var replyTo        by remember { mutableStateOf<MessageResponse?>(null) }
    var contextMsg     by remember { mutableStateOf<MessageResponse?>(null) }
    var showAiDialog   by remember { mutableStateOf(false) }
    var showAiConfig   by remember { mutableStateOf(false) }
    var isAiLoading    by remember { mutableStateOf(false) }
    var isRecording    by remember { mutableStateOf(false) }
    var recordFile     by remember { mutableStateOf<File?>(null) }
    var mediaRecorder  by remember { mutableStateOf<MediaRecorder?>(null) }

    // Cargar mensajes
    fun loadMessages() {
        scope.launch {
            try {
                val r = NewsickRetrofit.api.getChatMessages(conversationId)
                if (r.isSuccessful) {
                    messages = r.body() ?: emptyList()
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(conversationId) { loadMessages() }

    // Auto-refresh cada 5 segundos
    LaunchedEffect(conversationId) {
        while (true) { delay(5000); loadMessages() }
    }

    // Función enviar mensaje
    fun sendMessage(content: String, type: String = "text", replyToId: Int? = null, isAi: Boolean = false) {
        if (content.isBlank()) return
        scope.launch {
            isSending = true
            try {
                val r = NewsickRetrofit.api.sendChatMessage(
                    conversationId,
                    SendMessageRequest(content = content, messageType = type, replyToId = replyToId, isAiReply = isAi)
                )
                if (r.isSuccessful) {
                    text    = ""
                    replyTo = null
                    loadMessages()
                }
            } catch (_: Exception) {}
            isSending = false
        }
    }

    // Selector de imagen/video/audio
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val url  = uploadMediaFile(context, uri) ?: return@launch
            val mime = context.contentResolver.getType(uri) ?: ""
            val type = when {
                mime.startsWith("image") -> "image"
                mime.startsWith("video") -> "video"
                else -> "audio"
            }
            sendMessage(url, type, replyTo?.id)
        }
    }

    // Diálogo: responder con IA
    if (showAiDialog && contextMsg != null) {
        val aiConfig = loadAiConfig(context)
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("Responder con IA") },
            text  = {
                Column {
                    if (aiConfig == null) {
                        Text("No hay IA configurada. Configúrala primero en ajustes.")
                    } else {
                        Text("La IA responderá al mensaje de ${contextMsg!!.senderUsername}:")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "\"${contextMsg!!.content.take(80)}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Modelo: ${aiConfig.provider} / ${aiConfig.model}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        if (isAiLoading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                if (loadAiConfig(context) != null) {
                    Button(
                        onClick = {
                            val cfg = loadAiConfig(context) ?: return@Button
                            val msg = contextMsg ?: return@Button
                            isAiLoading = true
                            scope.launch {
                                val reply = callAi(cfg, messages, msg)
                                sendMessage(reply, "text", msg.id, isAi = true)
                                isAiLoading = false
                                showAiDialog = false
                                contextMsg   = null
                            }
                        },
                        enabled = !isAiLoading
                    ) { Text("Responder") }
                } else {
                    Button(onClick = { showAiDialog = false; showAiConfig = true }) {
                        Text("Configurar IA")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDialog = false; contextMsg = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo: configurar IA
    if (showAiConfig) {
        AiConfigDialog(context = context, onDismiss = { showAiConfig = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onUserClick(otherUserId) }
                    ) {
                        val photoUrl = NewsickRetrofit.absoluteUrl(otherProfilePhoto)
                        if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = photoUrl, contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(otherUsername, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    // Menú eliminar chat
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.DeleteForever, "Eliminar chat",
                            tint = MaterialTheme.colorScheme.error)
                    }
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Eliminar chat") },
                            text  = { Text("Se eliminará el chat con $otherUsername. ¿Continuar?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                NewsickRetrofit.api.deleteChat(conversationId)
                                            } catch (_: Exception) {}
                                            showDeleteConfirm = false
                                            onBack()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("Eliminar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Burbuja de respuesta
                replyTo?.let { reply ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Reply, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(reply.senderUsername,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(
                                    reply.content.take(60),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { replyTo = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                // Barra de texto
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Adjuntar archivo
                    IconButton(onClick = { mediaLauncher.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, "Adjuntar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Nota de voz
                    IconButton(
                        onClick = {
                            if (!isRecording) {
                                // Empezar grabación
                                val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                recordFile = file
                                @Suppress("DEPRECATION")
                                val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                                    MediaRecorder(context)
                                else
                                    MediaRecorder()
                                recorder.apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(file.absolutePath)
                                    prepare(); start()
                                }
                                mediaRecorder = recorder
                                isRecording   = true
                            } else {
                                // Detener y enviar
                                mediaRecorder?.apply { stop(); release() }
                                mediaRecorder = null
                                isRecording   = false
                                val file = recordFile ?: return@IconButton
                                scope.launch {
                                    val uri = Uri.fromFile(file)
                                    val url = uploadMediaFile(context, uri) ?: return@launch
                                    sendMessage(url, "voice", replyTo?.id)
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            "Voz",
                            tint = if (isRecording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje…") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(6.dp))
                    FloatingActionButton(
                        onClick = { sendMessage(text, "text", replyTo?.id) },
                        modifier = Modifier.size(44.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (isSending)
                            CircularProgressIndicator(Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else
                            Icon(Icons.AutoMirrored.Filled.Send, "Enviar",
                                tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state  = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == myId
                MessageBubble(
                    msg    = msg,
                    isMe   = isMe,
                    onLongPress = {
                        contextMsg   = msg
                        showAiDialog = true
                    },
                    onReply = { replyTo = msg },
                    onDelete = {
                        scope.launch {
                            try {
                                NewsickRetrofit.api.deleteChatMessage(conversationId, msg.id)
                                loadMessages()
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// BURBUJA DE MENSAJE
// ══════════════════════════════════════════════════════════

@Composable
private fun MessageBubble(
    msg: MessageResponse,
    isMe: Boolean,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val align = if (isMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        if (!isMe) {
            Text(msg.senderUsername,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        if (msg.isAiReply) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isMe) {
                    Icon(Icons.Default.SmartToy, null,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Text("IA", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Box {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showMenu = true })
                    },
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd   = if (isMe) 4.dp  else 16.dp
                ),
                color = bubbleColor
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Vista previa de respuesta
                    msg.replyToContent?.let { replyContent ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            shape    = RoundedCornerShape(8.dp),
                            color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        ) {
                            Column(Modifier.padding(6.dp)) {
                                Text(msg.replyToSenderUsername ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(replyContent.take(60),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // Contenido según tipo
                    when (msg.messageType) {
                        "image" -> AsyncImage(
                            model = NewsickRetrofit.absoluteUrl(msg.content),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        "video" -> VideoMessageItem(url = NewsickRetrofit.absoluteUrl(msg.content))
                        "audio", "voice" -> AudioMessageItem(
                            url    = NewsickRetrofit.absoluteUrl(msg.content),
                            isVoice = msg.messageType == "voice"
                        )
                        else -> Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        msg.createdAt.take(16).replace("T", " "),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Responder") },
                    leadingIcon = { Icon(Icons.Default.Reply, null) },
                    onClick = { showMenu = false; onReply() }
                )
                DropdownMenuItem(
                    text = { Text("Responder con IA") },
                    leadingIcon = { Icon(Icons.Default.SmartToy, null) },
                    onClick = { showMenu = false; onLongPress() }
                )
                if (isMe) {
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// REPRODUCTOR DE AUDIO / VÍDEO INLINE
// ══════════════════════════════════════════════════════════

@Composable
private fun AudioMessageItem(url: String, isVoice: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var player    by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        onDispose { player?.release(); player = null }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isVoice) Icons.Default.GraphicEq else Icons.Default.MusicNote,
            null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(if (isVoice) "Nota de voz" else "Audio",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(
            onClick = {
                if (isPlaying) {
                    player?.pause(); isPlaying = false
                } else {
                    if (player == null) {
                        player = MediaPlayer().apply {
                            setDataSource(url); prepare()
                            setOnCompletionListener { isPlaying = false }
                        }
                    }
                    player?.start(); isPlaying = true
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Reproducir",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun VideoMessageItem(url: String) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                // Abrir vídeo en app externa
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            },
        color = Color.Black
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Icon(Icons.Default.PlayCircle, "Vídeo",
                modifier = Modifier.size(56.dp), tint = Color.White)
        }
    }
}

// ══════════════════════════════════════════════════════════
// DIÁLOGO CONFIGURACIÓN IA
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(context: Context, onDismiss: () -> Unit) {
    val current  = loadAiConfig(context)
    var provider by remember { mutableStateOf(current?.provider ?: "openai") }
    var apiKey   by remember { mutableStateOf(current?.apiKey ?: "") }
    var model    by remember { mutableStateOf(current?.model ?: "") }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val modelSuggestions = when (provider) {
        "openai"    -> listOf("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo")
        "anthropic" -> listOf("claude-sonnet-4-6", "claude-haiku-4-5-20251001", "claude-opus-4-6")
        "google"    -> listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
        else        -> emptyList()
    }

    // Si el modelo actual no está en la lista del nuevo proveedor, resetear
    LaunchedEffect(provider) {
        if (model !in modelSuggestions) model = modelSuggestions.firstOrNull() ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Proveedor", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("openai" to "OpenAI", "anthropic" to "Anthropic", "google" to "Google").forEach { (key, label) ->
                        FilterChip(
                            selected = provider == key,
                            onClick  = { provider = key; model = "" },
                            label    = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                // Selector de modelo con dropdown
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Modelo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        modelSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = { model = suggestion; modelMenuExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (apiKey.isNotBlank() && model.isNotBlank()) {
                        saveAiConfig(context, AiConfig(provider, apiKey, model))
                        onDismiss()
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ══════════════════════════════════════════════════════════
// HELPER: subir archivo al servidor
// ══════════════════════════════════════════════════════════

private suspend fun uploadMediaFile(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes  = stream.readBytes(); stream.close()
            val mime   = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val ext    = when {
                mime.startsWith("image") -> "jpg"
                mime.startsWith("video") -> "mp4"
                else -> "m4a"
            }
            val body = bytes.toRequestBody(mime.toMediaType())
            val part = MultipartBody.Part.createFormData("photo", "media.$ext", body)
            val r    = NewsickRetrofit.api.uploadPhoto(part)
            if (r.isSuccessful) r.body()?.url else null
        } catch (_: Exception) { null }
    }
