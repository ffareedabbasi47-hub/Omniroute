package com.omni.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omni.assistant.OmniApp

/**
 * Voice-first screen: tap the mic, speak, the recognised text is sent, and the reply is read aloud when
 * it finishes (only for requests made while this screen is open).
 */
@Composable
fun VoiceScreen(vm: ChatViewModel, onBack: () -> Unit, onModels: () -> Unit) {
    val omni = LocalContext.current.applicationContext as OmniApp
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val settings by omni.settings.flow.collectAsStateWithLifecycle()
    val conn by omni.connection.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val mic = rememberMicAction(
        onReady = { vm.toggleMic(autoSend = true) },
        onDenied = { vm.notify("Microphone permission denied. Enable it in Android settings.") }
    )

    LaunchedEffect(Unit) { vm.setMode(ChatMode.CHAT) }
    LaunchedEffect(ui.notice) {
        val n = ui.notice
        if (n != null) {
            snackbar.showSnackbar(n)
            vm.dismissMessages()
        }
    }

    val lastUser = messages.lastOrNull { it.role == "user" }?.content
    val lastReply = messages.lastOrNull { it.role == "assistant" && !it.failed }
    val lastMessage = messages.lastOrNull()

    // Read a reply aloud only if it answers something sent while this screen was open.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(ui.streaming) {
        if (ui.streaming) armed = true
    }
    LaunchedEffect(lastReply?.id, ui.streaming) {
        val r = lastReply
        if (armed && !ui.streaming && r != null && lastMessage?.id == r.id) {
            armed = false
            vm.speak(r.content)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.stopSpeaking()
            if (vm.ui.value.listening) vm.toggleMic(autoSend = false)
        }
    }

    val busy = ui.listening || ui.streaming || ui.speaking
    AppBackground {
        Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close voice conversation")
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ModelChip(settings.model, conn, onModels)
                    }
                    Spacer(Modifier.size(48.dp))
                }

                Spacer(Modifier.height(16.dp))
                GlowOrb(size = 250.dp, active = busy)
                Spacer(Modifier.height(12.dp))

                val heading = when {
                    ui.listening -> "Listening…"
                    ui.streaming -> "Thinking…"
                    ui.speaking -> "Speaking…"
                    else -> "What's on your mind?"
                }
                Text(
                    heading,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val heard = if (ui.listening && draft.isNotBlank()) draft else lastUser
                    if (heard != null) {
                        Text(
                            heard,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (ui.streaming && ui.partial.isNotEmpty()) {
                        Text(ui.partial, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    } else if (lastReply != null && lastMessage?.id == lastReply.id) {
                        Text(
                            MarkdownParser.toSpeakable(lastReply.content),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    } else if (lastMessage != null && lastMessage.failed) {
                        Text(
                            lastMessage.error ?: "The reply failed.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { vm.retry(lastMessage.id) }, enabled = !ui.streaming) { Text("Retry") }
                    } else if (heard == null) {
                        Text(
                            "Tap the microphone and speak in English, हिन्दी or Hinglish.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundControl(Icons.Default.Keyboard, "Switch to typing", onClick = onBack)
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(if (ui.listening) androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error) else LocalOmni.current.accent)
                            .clickable(
                                enabled = !ui.streaming,
                                role = Role.Button,
                                onClickLabel = if (ui.listening) "Stop listening" else "Start listening",
                                onClick = mic
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = if (ui.listening) "Stop listening" else "Start listening",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    RoundControl(
                        Icons.Default.Stop, "Stop", enabled = ui.streaming || ui.speaking,
                        onClick = {
                            if (ui.streaming) vm.cancel()
                            vm.stopSpeaking()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
