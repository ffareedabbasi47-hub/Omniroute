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

/** Tracks whether the configured server is reachable. Never reports success without a real response. */
class ConnectionMonitor(private val client: OmniRouteClient, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<ConnState>(ConnState.Unknown)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    fun check() {
        if (settings.current.baseUrl.isBlank()) {
            _state.value = ConnState.Unknown
            return
        }
        _state.value = ConnState.Checking
        scope.launch {
            _state.value = try {
                ConnState.Ok(client.listModels())
            } catch (e: ApiException) {
                if (e.kind == ApiException.Kind.NOT_FOUND) {
                    ConnState.Unverified("This server has no model list. Type the model name manually.")
                } else {
                    ConnState.Failed(e.message.orEmpty())
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
    val connection: ConnectionMonitor by lazy { ConnectionMonitor(client, settings) }
    val voice: VoiceController by lazy { VoiceController(this) }
    val attachments: AttachmentStore by lazy { AttachmentStore(this) }
}
