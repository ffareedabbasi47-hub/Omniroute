package com.omni.assistant

import android.app.Application
import androidx.room.Room
import com.omni.assistant.data.AppDatabase
import com.omni.assistant.data.AttachmentStore
import com.omni.assistant.data.SettingsStore
import com.omni.assistant.net.ApiException
import com.omni.assistant.net.BridgeClient
import com.omni.assistant.net.OmniRouteClient
import com.omni.assistant.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnState {
    data object Unknown : ConnState
    data object Checking : ConnState
    data class Ok(val models: List<String>) : ConnState
    /** Server reachable but it has no /models endpoint, so we cannot confirm the setup. */
    data class Unverified(val message: String) : ConnState
    data class Failed(val message: String) : ConnState
}

/** Tracks whether the configured server is reachable. Automatically starts Termux bridge if local server isn't running. */
class ConnectionMonitor(
    private val context: android.content.Context,
    private val client: OmniRouteClient,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ConnState>(ConnState.Unknown)
    val state: StateFlow<ConnState> = _state.asStateFlow()
    private var autoStartAttempted = false

    fun check(force: Boolean = false) {
        if (settings.current.baseUrl.isBlank()) {
            _state.value = ConnState.Unknown
            return
        }
        if (force) autoStartAttempted = false
        _state.value = ConnState.Checking
        scope.launch {
            try {
                val models = client.listModels()
                _state.value = ConnState.Ok(models)
                autoStartAttempted = false
            } catch (e: Exception) {
                if (e is ApiException && e.kind == ApiException.Kind.NOT_FOUND) {
                    _state.value = ConnState.Unverified("This server has no model list. Type the model name manually.")
                } else {
                    val base = settings.current.baseUrl
                    val isLocal = base.contains("127.0.0.1") || base.contains("localhost")
                    if (isLocal && !autoStartAttempted) {
                        autoStartAttempted = true
                        com.omni.assistant.net.TermuxBridgeManager.startBridge(context)
                        for (retry in 1..4) {
                            kotlinx.coroutines.delay(1200)
                            try {
                                val retryModels = client.listModels()
                                _state.value = ConnState.Ok(retryModels)
                                return@launch
                            } catch (_: Exception) {
                            }
                        }
                    }
                    _state.value = ConnState.Failed(e.message.orEmpty())
                }
            }
        }
    }

    fun markOk() {
        val s = _state.value
        if (s !is ConnState.Ok) _state.value = ConnState.Ok(emptyList())
    }

    fun markFailed(message: String) {
        _state.value = ConnState.Failed(message)
    }
}

class OmniApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val db: AppDatabase by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "omni.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build() }
    val client: OmniRouteClient by lazy { OmniRouteClient(settings) }
    val bridge: BridgeClient by lazy { BridgeClient(settings) }
    val connection: ConnectionMonitor by lazy { ConnectionMonitor(this, client, settings) }
    val voice: VoiceController by lazy { VoiceController(this) }
    val attachments: AttachmentStore by lazy { AttachmentStore(this) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        try {
            val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 100 * 1024 * 1024) // 100 MB cursor window to prevent big code crashes
        } catch (_: Throwable) {
        }
        voice // pre-warm TTS for instant vocal replies
    }
}
