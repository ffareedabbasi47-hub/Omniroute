package com.omni.assistant.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import com.omni.assistant.data.FileExport
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omni.assistant.ConnState
import com.omni.assistant.OmniApp
import com.omni.assistant.data.AttachmentEntity
import com.omni.assistant.data.AttachmentRules
import com.omni.assistant.data.ConversationEntity
import com.omni.assistant.data.DraftAttachment
import com.omni.assistant.data.MessageEntity
import com.omni.assistant.data.FileExport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Returns a function that checks the microphone permission (asking if needed), then runs onReady. */
@Composable
fun rememberMicAction(onReady: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val ready by rememberUpdatedState(onReady)
    val denied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ready() else denied()
    }
    return remember {
        {
            val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (has) ready() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private val suggestions = listOf(
    "Explain how HTTPS works in simple words",
    "Mujhe Python mein ek simple to-do app banana sikhao",
    "मुझे छुट्टी के लिए एक छोटा ईमेल लिखना है"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAgent: () -> Unit,
    onVoice: () -> Unit,
    onModels: () -> Unit
) {
    val ctx = LocalContext.current
    val omni = ctx.applicationContext as OmniApp
    val messages by vm.messages.collectAsStateWithLifecycle()
    val attachments by vm.attachmentsByMessage.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val settings by omni.settings.flow.collectAsStateWithLifecycle()
    val conn by omni.connection.state.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<AttachmentEntity?>(null) }
    var viewingDraft by remember { mutableStateOf<DraftAttachment?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentRules.MAX_ATTACHMENTS_PER_MESSAGE)
    ) { uris -> vm.addAttachments(uris) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.addAttachments(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) vm.addAttachments(listOf(u))
        cameraUri = null
    }

    fun launchPhotos() {
        try {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        } catch (_: Exception) {
            try {
                filePicker.launch("image/*")
            } catch (_: Exception) {
                filePicker.launch("*/*")
            }
        }
    }

    fun launchFiles() {
        filePicker.launch("*/*")
    }

    fun launchCamera() {
        try {
            val uri = omni.attachments.newCameraUri()
            cameraUri = uri
            camera.launch(uri)
        } catch (e: Exception) {
            vm.notify("No camera app is available on this device.")
        }
    }

    fun openFile(path: String, mime: String, name: String) {
        val uri = omni.attachments.shareUri(path)
        if (uri == null) {
            vm.notify("That file is no longer available.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(Intent.createChooser(intent, name))
        } catch (e: Exception) {
            vm.notify("No app can open this file.")
        }
    }

    fun shareAttachments(list: List<AttachmentEntity>) {
        val uris = list.mapNotNull { omni.attachments.shareUri(it.path) }
        val mimes = list.map { it.mime }.distinct()
        val mime = if (mimes.size == 1) mimes[0] else "*/*"
        if (uris.isEmpty() || !shareUris(ctx, uris, mime, "Share")) vm.notify("Couldn't share that file.")
    }

    fun exportReply(text: String, format: String) {
        scope.launch {
            try {
                val (name, bytes, mime) = when (format) {
                    "pdf" -> Triple(
                        "reply.pdf",
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            FileExport.textToPdf("", FileExport.markdownToPlain(text))
                        },
                        "application/pdf"
                    )
                    "zip" -> {
                        val files = FileExport.extractCodeFiles(text)
                        if (files.isEmpty()) {
                            vm.notify("No code blocks found to zip.")
                            return@launch
                        }
                        Triple("project.zip", FileExport.createZipArchive(files, "omni-project"), "application/zip")
                    }
                    "html" -> {
                        val files = FileExport.extractCodeFiles(text)
                        val playable = FileExport.findPlayableHtml(files) ?: text
                        Triple("index.html", playable.toByteArray(Charsets.UTF_8), "text/html")
                    }
                    "txt" -> Triple("reply.txt", FileExport.markdownToPlain(text).toByteArray(), "text/plain")
                    else -> Triple("reply.md", text.toByteArray(), "text/markdown")
                }
                val uri = omni.attachments.uriFor(omni.attachments.writeExport(name, bytes))
                if (uri == null || !shareUris(ctx, listOf(uri), mime, name)) vm.notify("Couldn't create the file.")
            } catch (e: Exception) {
                vm.notify("Couldn't create the file: ${e.message}")
            }
        }
    }

    fun saveCodeFile(name: String, content: String) {
        scope.launch {
            try {
                val file = omni.attachments.writeExport(name, content.toByteArray(Charsets.UTF_8))
                val uri = omni.attachments.uriFor(file)
                val ext = name.substringAfterLast(".", "txt")
                val mime = FileExport.mimeFor(ext)
                if (uri == null || !shareUris(ctx, listOf(uri), mime, name)) {
                    vm.notify("File saved: $name")
                }
            } catch (e: Exception) {
                vm.notify("Couldn't save file.")
            }
        }
    }

    fun runHtmlPreview(html: String) {
        scope.launch {
            try {
                val file = omni.attachments.writeExport("preview.html", html.toByteArray(Charsets.UTF_8))
                val uri = omni.attachments.uriFor(file)
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "text/html")
                        clipData = ClipData.newRawUri("preview.html", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Run in Browser / Game Preview"))
                } else {
                    vm.notify("Couldn't open preview.")
                }
            } catch (e: Exception) {
                vm.notify("No browser app available to open preview.")
            }
        }
    }

    LaunchedEffect(Unit) { omni.connection.check() }
    LaunchedEffect(ui.notice) {
        val n = ui.notice
        if (n != null) {
            snackbar.showSnackbar(n)
            vm.dismissMessages()
        }
    }

    val micAction = rememberMicAction(
        onReady = { vm.toggleMic(autoSend = false) },
        onDenied = { vm.notify("Microphone permission denied. Enable it in Android settings to use voice input.") }
    )

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                ChatTopBar(
                    title = title,
                    model = settings.model,
                    conn = conn,
                    menuOpen = menuOpen,
                    onMenuOpen = { menuOpen = it },
                    onHistory = onHistory,
                    onNewChat = { vm.newChat() },
                    onModels = onModels,
                    onVoice = onVoice,
                    onAgent = onAgent,
                    onSettings = onSettings
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!settings.apiConfigured) {
                    Banner(
                        "Connect your OmniRoute server to start chatting.",
                        "Set up",
                        error = false,
                        onAction = onSettings
                    )
                } else if (conn is ConnState.Failed) {
                    Banner((conn as ConnState.Failed).message, "Retry", error = true, onAction = { omni.connection.check() })
                }
                ui.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty() && !ui.streaming) {
                        HomeContent(
                            conversations = conversations,
                            onTalk = onVoice,
                            onCamera = ::launchCamera,
                            onPhotos = ::launchPhotos,
                            onFiles = ::launchFiles,
                            onAgent = onAgent,
                            onOpenConversation = { vm.openConversation(it) },
                            onSuggestion = { vm.setDraft(it) }
                        )
                    } else {
                        val listState = rememberLazyListState()
                        // reverseLayout pins the newest message to the bottom, including while streaming.
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            if (ui.streaming) {
                                item(key = "streaming") {
                                    StreamingBubble(
                                        partial = ui.partial,
                                        mode = mode,
                                        status = ui.status,
                                        activeFile = ui.activeFile,
                                        completedFiles = ui.completedFiles,
                                        thinking = ui.thinking
                                    )
                                }
                            }
                            items(messages.reversed().distinctBy { it.id }, key = { it.id }) { m ->
                                MessageBubble(
                                    m = m,
                                    attachments = attachments[m.id].orEmpty(),
                                    speaking = ui.speaking,
                                    onSpeak = { vm.speak(m.content) },
                                    onStopSpeaking = { vm.stopSpeaking() },
                                    onRetry = { vm.retry(m.id) },
                                    retryEnabled = !ui.streaming,
                                    onShare = { shareAttachments(attachments[m.id].orEmpty()) },
                                    onExport = { fmt -> exportReply(m.content, fmt) },
                                    onSaveCode = ::saveCodeFile,
                                    onRunHtml = ::runHtmlPreview,
                                    onOpenAttachment = { a ->
                                        if (a.kind == "file") openFile(a.path, a.mime, a.name) else viewing = a
                                    }
                                )
                            }
                        }
                    }
                }

                Composer(
                    text = draft,
                    onText = vm::setDraft,
                    pending = pending,
                    importing = ui.importing,
                    streaming = ui.streaming,
                    listening = ui.listening,
                    mode = mode,
                    onMode = vm::setMode,
                    onSend = vm::sendDraft,
                    onStop = vm::cancel,
                    onMic = micAction,
                    onAttach = { showSheet = true },
                    onOpenPending = { d ->
                        if (d.kind == "file") openFile(d.path, d.mime, d.name) else viewingDraft = d
                    },
                    onRemovePending = vm::removePending
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 24.dp)) {
                SheetRow(Icons.Default.PhotoCamera, "Camera", "Take a photo") {
                    showSheet = false
                    launchCamera()
                }
                SheetRow(Icons.Default.Image, "Photos & videos", "Videos are sent as sampled frames") {
                    showSheet = false
                    launchPhotos()
                }
                SheetRow(Icons.Default.Description, "Files", "PDF, text and code files") {
                    showSheet = false
                    launchFiles()
                }
            }
        }
    }
    viewing?.let { AttachmentViewer(it, onClose = { viewing = null }) }
    viewingDraft?.let { DraftViewer(it, onClose = { viewingDraft = null }) }
}

@Composable
private fun ChatTopBar(
    title: String,
    model: String,
    conn: ConnState,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onModels: () -> Unit,
    onVoice: () -> Unit,
    onAgent: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onHistory) {
            Icon(Icons.Default.Menu, contentDescription = "Conversation history")
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            ModelChip(model, conn, onModels)
        }
        IconButton(onClick = onNewChat) {
            Icon(Icons.Default.Add, contentDescription = "New chat")
        }
        Box {
            IconButton(onClick = { onMenuOpen(true) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
                DropdownMenuItem(
                    text = { Text("Voice conversation") },
                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    onClick = {
                        onMenuOpen(false)
                        onVoice()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Coding agent") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                    onClick = {
                        onMenuOpen(false)
                        onAgent()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        onMenuOpen(false)
                        onSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun Banner(message: String, action: String, error: Boolean, onAction: () -> Unit) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = container,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), color = content, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeContent(
    conversations: List<ConversationEntity>,
    onTalk: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onAgent: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onSuggestion: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Hello 👋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "What can I help you with today?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.weight(1f).fillMaxHeight(), onClick = onTalk) {
                GlowOrb(Modifier.align(Alignment.CenterHorizontally), size = 104.dp)
                Spacer(Modifier.height(8.dp))
                Text("Talk with voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Speak, then hear the answer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(Icons.Default.PhotoCamera, "Camera", "Ask about a photo", Modifier.weight(1f), onCamera)
                ActionCard(Icons.Default.Image, "Photos & video", "Pick from gallery", Modifier.weight(1f), onPhotos)
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(Icons.Default.Description, "Files", "PDF, text, code", Modifier.weight(1f).fillMaxHeight(), onFiles)
            ActionCard(Icons.Default.Code, "Coding agent", "Claude Code bridge", Modifier.weight(1f).fillMaxHeight(), onAgent)
        }
        if (conversations.isNotEmpty()) {
            SectionLabel("Recent chats", Modifier.padding(top = 8.dp))
            for (c in conversations.take(3)) {
                LinkRow(c.title) { onOpenConversation(c.id) }
            }
        }
        SectionLabel("Try asking", Modifier.padding(top = 8.dp))
        for (s in suggestions) {
            LinkRow(s) { onSuggestion(s) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AssistantAvatar(isGenerating: Boolean = false) {
    val ex = LocalOmni.current
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .then(
                if (isGenerating) {
                    Modifier.border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        shape = CircleShape
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ex.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .then(if (isGenerating) Modifier.graphicsLayer(rotationZ = rotation) else Modifier)
            )
        }
    }
}

@Composable
private fun LiveStageHeader(status: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "stage")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha),
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Live AI Generation in progress...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
            Spacer(Modifier.width(6.dp))
            TypingDots()
        }
    }
}

@Composable
private fun LiveClaudeStageCard(
    status: String,
    activeFile: FileExport.LiveFileInfo?,
    completedFiles: List<FileExport.LiveFileInfo>,
    thinking: String?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    var showThinking by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha),
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha))
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (activeFile != null) "Editing: ${activeFile.name} (Line ${activeFile.lineCount})" else "Autonomous Agent running in background...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
                Spacer(Modifier.width(6.dp))
                TypingDots()
            }

            if (activeFile != null || completedFiles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    completedFiles.forEach { file ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Text(
                                "✓ ${file.name} (${file.lineCount}L)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (activeFile != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = shimmerAlpha),
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                "⚡ ${activeFile.name} (${activeFile.lineCount} lines...)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (!thinking.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThinking = !showThinking }
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🧠 Thought process ${if (showThinking) "(tap to hide)" else "(tap to view)"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (showThinking) "▲" else "▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showThinking) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = thinking,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(
    partial: String,
    mode: ChatMode,
    status: String?,
    activeFile: FileExport.LiveFileInfo?,
    completedFiles: List<FileExport.LiveFileInfo>,
    thinking: String?
) {
    val ex = LocalOmni.current
    Row(Modifier.fillMaxWidth()) {
        AssistantAvatar(isGenerating = true)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(ex.assistantBubble)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                if (status != null) {
                    LiveClaudeStageCard(status, activeFile, completedFiles, thinking)
                }
                if (partial.isNotEmpty()) {
                    val lines = remember(partial) { partial.lines() }
                    val displayPartial = remember(partial) {
                        if (lines.size > 60) {
                            "… [Buffered ${lines.size - 45} lines above] …\n\n" + lines.takeLast(45).joinToString("\n")
                        } else {
                            partial
                        }
                    }
                    MarkdownView(displayPartial)
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TypingDots()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (activeFile != null) "Writing ${activeFile.name} (${activeFile.lineCount} lines)..." else "Writing code & files (${lines.size} lines generated)...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (mode == ChatMode.CHAT) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            TypingDots()
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Thinking & preparing files...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column {
                        TypingDots()
                        Text(
                            if (mode == ChatMode.IMAGE) "Generating image…" else "Generating video… this can take several minutes.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    m: MessageEntity,
    attachments: List<AttachmentEntity>,
    speaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
    onShare: () -> Unit,
    onExport: (String) -> Unit,
    onSaveCode: (String, String) -> Unit,
    onRunHtml: (String) -> Unit,
    onOpenAttachment: (AttachmentEntity) -> Unit
) {
    val ex = LocalOmni.current
    val clipboard = LocalClipboardManager.current
    var exportMenu by remember { mutableStateOf(false) }
    val codeFiles = remember(m.content) {
        try { FileExport.extractCodeFiles(m.content) } catch (_: Exception) { emptyList() }
    }
    val playableHtml = remember(codeFiles) {
        try { FileExport.findPlayableHtml(codeFiles) } catch (_: Exception) { null }
    }

    if (m.role == "user") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (attachments.isNotEmpty()) MessageAttachments(attachments, onOpenAttachment)
            if (m.content.isNotBlank()) {
                Box(
                    Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                        .background(ex.userBubble)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    SelectionContainer {
                        Text(m.content, style = MaterialTheme.typography.bodyLarge, color = ex.onUserBubble)
                    }
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth()) {
        AssistantAvatar()
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            if (attachments.isNotEmpty()) {
                MessageAttachments(attachments, onOpenAttachment)
                Spacer(Modifier.height(6.dp))
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                    .background(ex.assistantBubble)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (codeFiles.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Project Files (${codeFiles.size}):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (file in codeFiles) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { onSaveCode(file.name, file.content) }
                                    ) {
                                        Text(
                                            "📄 ${file.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (m.content.isNotEmpty()) {
                    MarkdownView(
                        text = m.content,
                        onSaveCode = onSaveCode,
                        onRunHtml = onRunHtml
                    )
                }

                if (playableHtml != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onRunHtml(playableHtml) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "▶ Play Game / Run in Browser",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (codeFiles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { onExport("zip") }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "📦 Download Project ZIP (${codeFiles.size} files)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (m.failed) {
                    Text(
                        m.error ?: "The reply failed.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = if (m.content.isEmpty()) 0.dp else 8.dp)
                    )
                    TextButton(onClick = onRetry, enabled = retryEnabled) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
            if (!m.failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(m.content)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy reply", modifier = Modifier.size(20.dp))
                    }
                    if (attachments.isNotEmpty()) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Save or share generated file", modifier = Modifier.size(20.dp))
                        }
                    } else if (m.content.isNotBlank()) {
                        if (codeFiles.isNotEmpty()) {
                            IconButton(onClick = { onExport("zip") }) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download project as ZIP",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { exportMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Export reply options", modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                                if (codeFiles.isNotEmpty()) {
                                    DropdownMenuItem(text = { Text("📦 Project ZIP (.zip)") }, onClick = { exportMenu = false; onExport("zip") })
                                }
                                if (playableHtml != null) {
                                    DropdownMenuItem(text = { Text("🌐 Web Page (.html)") }, onClick = { exportMenu = false; onExport("html") })
                                }
                                DropdownMenuItem(text = { Text("PDF document (.pdf)") }, onClick = { exportMenu = false; onExport("pdf") })
                                DropdownMenuItem(text = { Text("Markdown (.md)") }, onClick = { exportMenu = false; onExport("md") })
                                DropdownMenuItem(text = { Text("Plain text (.txt)") }, onClick = { exportMenu = false; onExport("txt") })
                            }
                        }
                    }
                    IconButton(onClick = if (speaking) onStopSpeaking else onSpeak) {
                        Icon(
                            if (speaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = if (speaking) "Stop speaking" else "Read reply aloud",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    pending: List<DraftAttachment>,
    importing: Boolean,
    streaming: Boolean,
    listening: Boolean,
    mode: ChatMode,
    onMode: (ChatMode) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onAttach: () -> Unit,
    onOpenPending: (DraftAttachment) -> Unit,
    onRemovePending: (String) -> Unit
) {
    val ex = LocalOmni.current
    val chat = mode == ChatMode.CHAT
    val canSend = (text.isNotBlank() || (chat && pending.isNotEmpty())) && !importing
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier.padding(bottom = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((m, label) in listOf(ChatMode.CHAT to "Chat", ChatMode.IMAGE to "Create image", ChatMode.VIDEO to "Create video")) {
                FilterChip(selected = mode == m, onClick = { onMode(m) }, enabled = !streaming, label = { Text(label) })
            }
        }
        if (chat && (pending.isNotEmpty() || importing)) {
            ComposerAttachmentRow(pending, importing, onOpenPending, onRemovePending)
        }
        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onAttach, enabled = !streaming && chat, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Attach photos, videos or files")
            }
            TextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when {
                            listening -> "Listening…"
                            mode == ChatMode.IMAGE -> "Describe the image to create…"
                            mode == ChatMode.VIDEO -> "Describe the video to create…"
                            else -> "Ask me something…"
                        }
                    )
                },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                )
            )
            IconButton(onClick = onMic, enabled = !streaming, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = if (listening) "Stop listening" else "Voice input",
                    tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (streaming || canSend) ex.accent
                        else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    .clickable(
                        enabled = streaming || canSend,
                        role = Role.Button,
                        onClickLabel = if (streaming) "Stop generating" else if (chat) "Send message" else "Generate",
                        onClick = if (streaming) onStop else onSend
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (streaming) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (streaming) "Stop generating" else "Send message",
                    tint = if (streaming || canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
