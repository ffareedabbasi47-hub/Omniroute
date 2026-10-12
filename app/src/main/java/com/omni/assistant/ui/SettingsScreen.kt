package com.omni.assistant.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omni.assistant.ConnState
import com.omni.assistant.OmniApp
import com.omni.assistant.net.ApiException
import com.omni.assistant.net.UrlUtils
import kotlinx.coroutines.launch

private val languages = listOf(
    "auto" to "Device default",
    "en-IN" to "English (India)",
    "en-US" to "English (US)",
    "hi-IN" to "हिन्दी (Hindi)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(chatVm: ChatViewModel, onBack: () -> Unit, onChooseModel: () -> Unit) {
    val omni = LocalContext.current.applicationContext as OmniApp
    val settings by omni.settings.flow.collectAsStateWithLifecycle()
    val conn by omni.connection.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var bridgeUrl by remember { mutableStateOf(settings.bridgeUrl) }
    var bridgeToken by remember { mutableStateOf(settings.bridgeToken) }
    var bridgeStatus by remember { mutableStateOf<String?>(null) }
    var bridgeBusy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }
    var imageModel by remember { mutableStateOf(settings.imageModel) }
    var imageSize by remember { mutableStateOf(settings.imageSize) }
    var videoModel by remember { mutableStateOf(settings.videoModel) }
    var videoSeconds by remember { mutableStateOf(settings.videoSeconds.toString()) }
    var imageModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var genStatus by remember { mutableStateOf<String?>(null) }
    var genBusy by remember { mutableStateOf(false) }

    AppBackground {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("OmniRoute connection")
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; urlError = null },
                label = { Text("Base URL") },
                placeholder = { Text("http://127.0.0.1:20128/v1") },
                supportingText = {
                    Text(urlError ?: "Your OmniRoute address including /v1. OmniRoute's documented default is port 20128.")
                },
                isError = urlError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            if (UrlUtils.isInsecureRemote(baseUrl)) {
                Text(
                    "This URL uses plain http:// to another machine, so your key and chats travel unencrypted. Prefer https:// or a VPN.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key (optional if your server doesn't require one)") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide key" else "Show key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "The key is stored encrypted with a key held in the Android Keystore. That protects it from other apps, " +
                    "but anyone who controls this unlocked device can still use or extract it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    val err = UrlUtils.validate(baseUrl)
                    if (err != null) {
                        urlError = err
                    } else {
                        omni.settings.update {
                            it.copy(baseUrl = UrlUtils.normalizeBaseUrl(baseUrl), apiKey = apiKey.trim())
                        }
                        baseUrl = UrlUtils.normalizeBaseUrl(baseUrl)
                        omni.connection.check()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Save and test connection") }
            ConnectionResult(conn)

            HorizontalDivider()
            SectionTitle("Model")
            Text(
                if (settings.model.isBlank()) "No model selected yet." else "Selected: ${settings.model}",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(onClick = onChooseModel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Choose model")
            }

            HorizontalDivider()
            SectionTitle("Assistant instructions (system prompt)")
            Text(
                "Sent at the start of every chat. Change it to give the assistant a role, tone or language rules. " +
                    "Leave it empty to send no instructions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { omni.settings.update { it.copy(systemPrompt = systemPrompt.trim()) } },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        systemPrompt = com.omni.assistant.data.Settings.DEFAULT_SYSTEM_PROMPT
                        omni.settings.update { it.copy(systemPrompt = systemPrompt) }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Reset to default") }
            }

            HorizontalDivider()
            SectionTitle("Image & video generation")
            Text(
                "Uses your server's /images/generations and /videos/generations endpoints, so you need models that your " +
                    "OmniRoute provider offers for them. Video support is experimental: the server's video response " +
                    "format isn't documented, so it may not work with every provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = imageModel,
                onValueChange = { imageModel = it },
                label = { Text("Image model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    genBusy = true
                    genStatus = null
                    scope.launch {
                        try {
                            imageModels = omni.client.listImageModels()
                            genStatus = if (imageModels.isEmpty()) "The server listed no image models. Type a model name." else null
                        } catch (e: ApiException) {
                            genStatus = e.message
                        }
                        genBusy = false
                    }
                },
                enabled = !genBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (genBusy) "Looking…" else "Find image models on my server") }
            if (imageModels.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in imageModels.take(30)) {
                        FilterChip(selected = m == imageModel, onClick = { imageModel = m }, label = { Text(m) })
                    }
                }
            }
            genStatus?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = imageSize,
                onValueChange = { imageSize = it },
                label = { Text("Image size") },
                placeholder = { Text("1024x1024") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = videoModel,
                onValueChange = { videoModel = it },
                label = { Text("Video model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = videoSeconds,
                onValueChange = { v -> videoSeconds = v.filter { it.isDigit() }.take(3) },
                label = { Text("Video length (seconds)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    omni.settings.update {
                        it.copy(
                            imageModel = imageModel.trim(),
                            imageSize = imageSize.trim(),
                            videoModel = videoModel.trim(),
                            videoSeconds = videoSeconds.toIntOrNull()?.coerceIn(1, 120) ?: 5
                        )
                    }
                    genStatus = null
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Save generation settings") }

            HorizontalDivider()
            SectionTitle("Voice and language")
            Text(
                "Used for voice input and spoken replies. Hindi needs a Hindi speech voice installed on your phone. " +
                    "Whether the model answers well in Hindi or Hinglish depends on the model you chose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for ((tag, label) in languages) {
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = settings.language == tag,
                        role = Role.RadioButton,
                        onClick = { omni.settings.update { it.copy(language = tag) } }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.language == tag, onClick = null)
                    Text(label, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Read replies aloud automatically", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.speakReplies,
                    onCheckedChange = { v -> omni.settings.update { it.copy(speakReplies = v) } }
                )
            }

            HorizontalDivider()
            SectionTitle("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf("system" to "System", "light" to "Light", "dark" to "Dark")) {
                    FilterChip(
                        selected = settings.theme == value,
                        onClick = { omni.settings.update { it.copy(theme = value) } },
                        label = { Text(label) }
                    )
                }
            }

            HorizontalDivider()
            SectionTitle("Coding-agent bridge")
            Text(
                "Address and token of the agent bridge that runs next to Claude Code (see README). " +
                    "The app never runs commands itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = bridgeUrl,
                onValueChange = { bridgeUrl = it },
                label = { Text("Bridge URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bridgeToken,
                onValueChange = { bridgeToken = it },
                label = { Text("Bridge token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    omni.settings.update {
                        it.copy(bridgeUrl = UrlUtils.normalizeBaseUrl(bridgeUrl), bridgeToken = bridgeToken.trim())
                    }
                    bridgeBusy = true
                    bridgeStatus = null
                    scope.launch {
                        bridgeStatus = try {
                            omni.bridge.health()
                        } catch (e: ApiException) {
                            e.message
                        }
                        bridgeBusy = false
                    }
                },
                enabled = !bridgeBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (bridgeBusy) "Testing…" else "Save and test bridge") }
            bridgeStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()
            SectionTitle("Data")
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Delete all conversations") }
            Spacer(Modifier.height(24.dp))
        }
    }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all conversations?") },
            text = { Text("This removes every saved chat from this device. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    chatVm.deleteAllConversations()
                    confirmClear = false
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ConnectionResult(conn: ConnState) {
    val (text, isError) = when (conn) {
        is ConnState.Unknown -> "Not tested yet." to false
        is ConnState.Checking -> "Contacting the server…" to false
        is ConnState.Ok ->
            (if (conn.models.isEmpty()) "Server answered." else "Connected. ${conn.models.size} models available.") to false
        is ConnState.Unverified -> conn.message to false
        is ConnState.Failed -> conn.message to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(onBack: () -> Unit) {
    val omni = LocalContext.current.applicationContext as OmniApp
    val settings by omni.settings.flow.collectAsStateWithLifecycle()
    val conn by omni.connection.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(settings.model) }

    LaunchedEffect(Unit) { if (conn !is ConnState.Ok) omni.connection.check() }

    val models = (conn as? ConnState.Ok)?.models.orEmpty()
    val filtered = models.filter { it.contains(query, ignoreCase = true) }

    AppBackground {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Choose model") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (conn) {
                is ConnState.Checking -> Text("Loading models from the server…")
                is ConnState.Failed -> Text((conn as ConnState.Failed).message, color = MaterialTheme.colorScheme.error)
                is ConnState.Unverified -> Text((conn as ConnState.Unverified).message)
                is ConnState.Unknown -> Text("Save your base URL in Settings first, or type a model name below.")
                is ConnState.Ok -> if (models.isEmpty()) Text("The server returned no models. Type a model name below.")
            }
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("Model name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    omni.settings.update { it.copy(model = manual.trim()) }
                    onBack()
                },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Use this model") }
            if (models.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search ${models.size} models") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    androidx.compose.foundation.lazy.items(filtered, key = { it }) { id ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = id == settings.model,
                                role = Role.RadioButton,
                                onClick = {
                                    omni.settings.update { it.copy(model = id) }
                                    onBack()
                                }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = id == settings.model, onClick = null)
                            Text(id, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
    }
}
