package com.omni.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omni.assistant.ConnState
import com.omni.assistant.OmniApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    chatVm: ChatViewModel,
    onBack: () -> Unit,
    onChooseModel: () -> Unit,
) {
    val ctx = LocalContext.current
    val omni = ctx.applicationContext as OmniApp
    val settings by omni.settings.flow.collectAsStateWithLifecycle()
    val conn by omni.connection.state.collectAsStateWithLifecycle()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var bridgeUrl by remember(settings.bridgeUrl) { mutableStateOf(settings.bridgeUrl) }
    var bridgeToken by remember(settings.bridgeToken) { mutableStateOf(settings.bridgeToken) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connection Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Antigravity Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { omni.connection.check() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Check connection")
                            }
                        }
                        val statusText = when (conn) {
                            is ConnState.Ok -> "Connected to Antigravity"
                            is ConnState.Checking -> "Connecting to local bridge..."
                            is ConnState.Unverified -> (conn as ConnState.Unverified).message
                            is ConnState.Failed -> "Bridge not running: ${(conn as ConnState.Failed).message}"
                            is ConnState.Unknown -> "Tap check to test connection"
                        }
                        val statusColor = when (conn) {
                            is ConnState.Ok -> MaterialTheme.colorScheme.primary
                            is ConnState.Checking -> MaterialTheme.colorScheme.secondary
                            is ConnState.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(statusText, color = statusColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // AI Backend Settings
                SectionLabel("AI Server (Antigravity Bridge)")
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Bridge URL (Chat)") },
                    placeholder = { Text("http://127.0.0.1:8787") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Name") },
                    placeholder = { Text("antigravity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Coding Agent Settings
                SectionLabel("Coding Agent Bridge (Termux)")
                OutlinedTextField(
                    value = bridgeUrl,
                    onValueChange = { bridgeUrl = it },
                    label = { Text("Agent Bridge URL") },
                    placeholder = { Text("http://127.0.0.1:8787") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = bridgeToken,
                    onValueChange = { bridgeToken = it },
                    label = { Text("Bridge Token") },
                    placeholder = { Text("antigravity-local") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        omni.settings.update { s ->
                            s.copy(
                                baseUrl = baseUrl.trim(),
                                model = model.trim(),
                                bridgeUrl = bridgeUrl.trim(),
                                bridgeToken = bridgeToken.trim()
                            )
                        }
                        omni.connection.check()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("Save Configuration", modifier = Modifier.padding(start = 8.dp))
                }

                // Preferences
                SectionLabel("Preferences")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Speak Replies", style = MaterialTheme.typography.bodyLarge)
                        Text("Read AI answers aloud using voice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.speakReplies,
                        onCheckedChange = { chk -> omni.settings.update { it.copy(speakReplies = chk) } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme: ", style = MaterialTheme.typography.bodyMedium)
                    listOf("system", "dark", "light").forEach { mode ->
                        FilterChip(
                            selected = settings.theme == mode,
                            onClick = { omni.settings.update { it.copy(theme = mode) } },
                            label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                // Reset to default
                OutlinedButton(
                    onClick = {
                        baseUrl = "http://127.0.0.1:8787"
                        model = "antigravity"
                        bridgeUrl = "http://127.0.0.1:8787"
                        bridgeToken = "antigravity-local"
                        omni.settings.update { s ->
                            s.copy(
                                baseUrl = "http://127.0.0.1:8787",
                                model = "antigravity",
                                bridgeUrl = "http://127.0.0.1:8787",
                                bridgeToken = "antigravity-local"
                            )
                        }
                        omni.connection.check()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset to Antigravity Local Defaults")
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
