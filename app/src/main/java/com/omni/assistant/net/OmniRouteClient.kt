package com.omni.assistant.net

import com.omni.assistant.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for an OpenAI-compatible endpoint such as OmniRoute
 * (POST {base}/chat/completions, GET {base}/models, "Authorization: Bearer <key>").
 * The base URL, key and model all come from user settings; nothing is hardcoded.
 */
class OmniRouteClient(private val settings: SettingsStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // max silence between streamed chunks
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun builder(path: String): Request.Builder {
        val s = settings.current
        val urlError = UrlUtils.validate(s.baseUrl)
        if (urlError != null) throw ApiException(ApiException.Kind.NOT_CONFIGURED, urlError)
        val base = UrlUtils.normalizeBaseUrl(s.baseUrl)
        val b = try {
            Request.Builder().url(base + path)
        } catch (e: IllegalArgumentException) {
            throw ApiException(ApiException.Kind.NOT_CONFIGURED, "Base URL is not valid.")
        }
        if (s.apiKey.isNotBlank()) b.header("Authorization", "Bearer " + s.apiKey.trim())
        return b
    }

    /** GET /models. Throws ApiException (kind NOT_FOUND if the server has no model list). */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = builder("/models").get().build()
        val call = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build().newCall(request)
        call.awaitResponse().use { resp ->
            val body = try {
                resp.body?.string().orEmpty()
            } catch (e: IOException) {
                throw mapIoException(e)
            }
            if (!resp.isSuccessful) throw httpError(resp.code, body)
            try {
                ApiParsing.parseModels(body)
            } catch (e: JSONException) {
                throw ApiException(ApiException.Kind.BAD_RESPONSE, "The server's model list isn't valid JSON.")
            }
        }
    }

    /**
     * POST /chat/completions with stream=true. Emits text deltas. If the server answers with plain
     * JSON instead of an event stream, the whole reply is emitted once. Completes normally only if
     * at least some text arrived; otherwise fails with ApiException(EMPTY).
     */
    fun streamChat(turns: List<ChatTurn>): Flow<String> = callbackFlow<String> {
        val s = settings.current
        if (s.model.isBlank()) {
            throw ApiException(ApiException.Kind.NOT_CONFIGURED, "No model selected. Choose one in Settings.")
        }
        val payload = ApiParsing.buildChatRequest(s.model, turns, stream = true)
        val request = builder("/chat/completions")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val call: Call = http.newCall(request)
        val scope = this
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                scope.close(mapIoException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { readStream(it, scope) }
                    scope.close()
                } catch (e: ApiException) {
                    scope.close(e)
                } catch (e: IOException) {
                    val mapped = mapIoException(e)
                    val interrupted = ApiException(
                        ApiException.Kind.NETWORK,
                        "Connection interrupted while receiving the reply. (" + mapped.message + ")"
                    )
                    scope.close(interrupted)
                }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun readStream(resp: Response, out: ProducerScope<String>) {
        if (!resp.isSuccessful) {
            throw httpError(resp.code, resp.body?.string())
        }
        val body = resp.body ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "The server sent an empty response body.")
        val contentType = resp.header("Content-Type").orEmpty()
        var emitted = 0
        if (contentType.contains("text/event-stream", ignoreCase = true)) {
            val source = body.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val event = ApiParsing.parseSseLine(line)
                if (event is SseEvent.Delta) {
                    out.trySend(event.text)
                    emitted += event.text.length
                } else if (event is SseEvent.Done) {
                    break
                } else if (event is SseEvent.Error) {
                    throw ApiException(ApiException.Kind.SERVER, Redactor.redact(event.message))
                }
            }
        } else {
            val text = body.string()
            val content = try {
                ApiParsing.parseCompletion(text)
            } catch (e: JSONException) {
                throw ApiException(ApiException.Kind.BAD_RESPONSE, "The server's reply isn't valid JSON.")
            } catch (e: IllegalStateException) {
                throw ApiException(ApiException.Kind.SERVER, Redactor.redact(e.message.orEmpty()))
            }
            if (content != null) {
                out.trySend(content)
                emitted += content.length
            }
        }
        if (emitted == 0) {
            throw ApiException(ApiException.Kind.EMPTY, "The model returned an empty reply. Try again or pick another model.")
        }
    }

    // ---- Image / video generation -------------------------------------------------------------

    /** Image-capable model ids from GET /models/image (OmniRoute). Empty if the server has no such list. */
    suspend fun listImageModels(): List<String> = withContext(Dispatchers.IO) {
        val request = builder("/models/image").get().build()
        val call = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build().newCall(request)
        call.awaitResponse().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, body)
            try {
                ApiParsing.parseModels(body)
            } catch (e: JSONException) {
                throw ApiException(ApiException.Kind.BAD_RESPONSE, "The server's image model list isn't valid JSON.")
            }
        }
    }

    /** POST /images/generations. Returns the generated items (inline base64 or URLs). */
    suspend fun requestImages(prompt: String): List<MediaItem> {
        val s = settings.current
        if (s.imageModel.isBlank()) {
            throw ApiException(ApiException.Kind.NOT_CONFIGURED, "No image model set. Choose one in Settings > Image & video generation.")
        }
        return postMedia("/images/generations", MediaApi.buildImageRequest(s.imageModel, prompt, s.imageSize), 240)
    }

    /** POST /videos/generations. The response format is not documented upstream; see [MediaApi]. */
    suspend fun requestVideo(prompt: String): List<MediaItem> {
        val s = settings.current
        if (s.videoModel.isBlank()) {
            throw ApiException(ApiException.Kind.NOT_CONFIGURED, "No video model set. Choose one in Settings > Image & video generation.")
        }
        return postMedia("/videos/generations", MediaApi.buildVideoRequest(s.videoModel, prompt, s.videoSeconds), 900)
    }

    private suspend fun postMedia(path: String, payload: String, timeoutSec: Long): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val request = builder(path)
                .header("Accept", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val client = http.newBuilder()
                .readTimeout(timeoutSec, TimeUnit.SECONDS)
                .callTimeout(timeoutSec, TimeUnit.SECONDS)
                .build()
            client.newCall(request).awaitResponse().use { resp ->
                val body = try {
                    resp.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw mapIoException(e)
                }
                if (!resp.isSuccessful) throw httpError(resp.code, body)
                val items = MediaApi.safeParse(body)
                if (items.isEmpty()) {
                    throw ApiException(
                        ApiException.Kind.EMPTY,
                        "The server answered but returned no image/video data I recognise. Check the model supports this."
                    )
                }
                items
            }
        }

    /**
     * Writes one item to [dest] (decoding base64 or downloading the URL, capped at [maxBytes]) and returns
     * the first bytes' detected type, or null if unrecognised. The API key is only sent to the same host.
     */
    suspend fun saveItem(item: MediaItem, dest: File, maxBytes: Long): MediaType? = withContext(Dispatchers.IO) {
        if (item.b64 != null) {
            val bytes = try {
                android.util.Base64.decode(item.b64, android.util.Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw ApiException(ApiException.Kind.BAD_RESPONSE, "The server sent invalid base64 data.")
            }
            if (bytes.size > maxBytes) throw ApiException(ApiException.Kind.BAD_RESPONSE, "The generated file is too large.")
            dest.writeBytes(bytes)
            return@withContext MediaApi.sniff(bytes)
        }
        val url = item.url ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "Nothing to download.")
        val b = try {
            Request.Builder().url(url)
        } catch (e: IllegalArgumentException) {
            throw ApiException(ApiException.Kind.BAD_RESPONSE, "The server returned a bad download link.")
        }
        val s = settings.current
        val sameHost = try {
            java.net.URI(url).host == java.net.URI(UrlUtils.normalizeBaseUrl(s.baseUrl)).host
        } catch (e: Exception) {
            false
        }
        if (sameHost && s.apiKey.isNotBlank()) b.header("Authorization", "Bearer " + s.apiKey.trim())
        val client = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(600, TimeUnit.SECONDS).build()
        client.newCall(b.build()).awaitResponse().use { resp ->
            if (!resp.isSuccessful) throw httpError(resp.code, null)
            val body = resp.body ?: throw ApiException(ApiException.Kind.BAD_RESPONSE, "Empty download.")
            try {
                var total = 0L
                dest.outputStream().use { out ->
                    val src = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw ApiException(ApiException.Kind.BAD_RESPONSE, "The generated file is too large.")
                        out.write(buf, 0, n)
                    }
                }
            } catch (e: IOException) {
                throw mapIoException(e)
            }
            val head = ByteArray(16)
            val n = dest.inputStream().use { it.read(head) }
            MediaApi.sniff(if (n > 0) head.copyOf(n) else ByteArray(0))
        }
    }
}
