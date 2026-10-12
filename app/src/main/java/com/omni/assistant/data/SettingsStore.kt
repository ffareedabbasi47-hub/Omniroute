package com.omni.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** BCP-47 tag such as "en-IN" or "hi-IN", or "auto" for the device default. */
    val language: String = "auto",
    val speakReplies: Boolean = false,
    /** "system", "light" or "dark". */
    val theme: String = "system",
    val onboarded: Boolean = false,
    val bridgeUrl: String = "http://127.0.0.1:8787",
    val bridgeToken: String = "",
    val lastWorkspace: String = "",
    /** Sent as the first "system" message of every chat. */
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    /** Model ids for image/video generation (may differ from the chat model). */
    val imageModel: String = "",
    val imageSize: String = "1024x1024",
    val videoModel: String = "",
    val videoSeconds: Int = 5
) {
    val apiConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are a helpful, honest assistant inside a mobile app. Reply in the language and script the " +
                "user writes in (English, Hindi or Hinglish). Be concise. Use Markdown. When the user asks you to " +
                "create a file, put its full content in one fenced code block whose info string is the language, " +
                "and mention the file name just before it. If you are unsure or don't know, say so."
    }
}

/**
 * Secrets (API key, bridge token) live in EncryptedSharedPreferences, encrypted with a key held
 * in the Android Keystore. Everything else is ordinary preferences. Note: this protects the
 * values at rest from other apps and from backups; it cannot hide them from someone who controls
 * the unlocked device (or a rooted one).
 */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val plain: SharedPreferences = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = createSecure()
    private val state = MutableStateFlow(load())

    val flow: StateFlow<Settings> = state.asStateFlow()
    val current: Settings get() = state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(state.value)
        save(next)
        state.value = next
    }

    private fun createSecure(): SharedPreferences {
        fun build(): SharedPreferences {
            val key = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                appContext,
                "secure_settings",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            build()
        } catch (e: Exception) {
            // Keystore key lost (e.g. after a restore): the old ciphertext is unreadable, so start fresh.
            appContext.deleteSharedPreferences("secure_settings")
            build()
        }
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            baseUrl = plain.getString("baseUrl", d.baseUrl).orEmpty(),
            apiKey = secure.getString("apiKey", "").orEmpty(),
            model = plain.getString("model", d.model).orEmpty(),
            language = plain.getString("language", d.language).orEmpty(),
            speakReplies = plain.getBoolean("speakReplies", d.speakReplies),
            theme = plain.getString("theme", d.theme).orEmpty(),
            onboarded = plain.getBoolean("onboarded", d.onboarded),
            bridgeUrl = plain.getString("bridgeUrl", d.bridgeUrl).orEmpty(),
            bridgeToken = secure.getString("bridgeToken", "").orEmpty(),
            lastWorkspace = plain.getString("lastWorkspace", d.lastWorkspace).orEmpty(),
            systemPrompt = plain.getString("systemPrompt", d.systemPrompt).orEmpty(),
            imageModel = plain.getString("imageModel", d.imageModel).orEmpty(),
            imageSize = plain.getString("imageSize", d.imageSize).orEmpty(),
            videoModel = plain.getString("videoModel", d.videoModel).orEmpty(),
            videoSeconds = plain.getInt("videoSeconds", d.videoSeconds)
        )
    }

    private fun save(s: Settings) {
        plain.edit()
            .putString("baseUrl", s.baseUrl)
            .putString("model", s.model)
            .putString("language", s.language)
            .putBoolean("speakReplies", s.speakReplies)
            .putString("theme", s.theme)
            .putBoolean("onboarded", s.onboarded)
            .putString("bridgeUrl", s.bridgeUrl)
            .putString("lastWorkspace", s.lastWorkspace)
            .putString("systemPrompt", s.systemPrompt)
            .putString("imageModel", s.imageModel)
            .putString("imageSize", s.imageSize)
            .putString("videoModel", s.videoModel)
            .putInt("videoSeconds", s.videoSeconds)
            .apply()
        secure.edit()
            .putString("apiKey", s.apiKey)
            .putString("bridgeToken", s.bridgeToken)
            .apply()
    }
}
