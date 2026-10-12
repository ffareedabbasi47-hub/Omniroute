package com.omni.assistant.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Every failure the UI can show comes through this type, with a user-readable message. */
class ApiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind {
        NOT_CONFIGURED, NETWORK, TIMEOUT, AUTH, NOT_FOUND, RATE_LIMIT,
        SERVER, BAD_RESPONSE, EMPTY, CLIENT
    }
}

internal fun mapIoException(e: IOException): ApiException {
    val raw = e.message.orEmpty()
    return when {
        e is SocketTimeoutException ->
            ApiException(ApiException.Kind.TIMEOUT, "The server took too long to respond.")
        e is UnknownHostException ->
            ApiException(ApiException.Kind.NETWORK, "Can't find that server. Check the URL and your internet connection.")
        e is ConnectException ->
            ApiException(ApiException.Kind.NETWORK, "Can't connect to the server. Is it running and reachable from this phone?")
        e is SSLException ->
            ApiException(ApiException.Kind.NETWORK, "Secure connection failed (TLS). Check the URL scheme (http/https) and certificate.")
        raw.contains("Canceled", ignoreCase = true) ->
            ApiException(ApiException.Kind.NETWORK, "Request cancelled.")
        else ->
            ApiException(ApiException.Kind.NETWORK, "Network problem: " + Redactor.redact(raw.take(160)))
    }
}

internal fun httpError(code: Int, body: String?): ApiException {
    val detail = ApiParsing.errorMessage(body.orEmpty())?.let { Redactor.redact(it) }
    val suffix = if (detail.isNullOrBlank()) "" else " ($detail)"
    return when (code) {
        401, 403 -> ApiException(ApiException.Kind.AUTH, "The server rejected the API key (HTTP $code).$suffix")
        404 -> ApiException(ApiException.Kind.NOT_FOUND, "Endpoint not found (HTTP 404). Check that the base URL ends with /v1.$suffix")
        408 -> ApiException(ApiException.Kind.TIMEOUT, "The server timed out (HTTP 408).$suffix")
        429 -> ApiException(ApiException.Kind.RATE_LIMIT, "Rate limit reached (HTTP 429). Wait a bit and retry.$suffix")
        in 500..599 -> ApiException(ApiException.Kind.SERVER, "The server had an error (HTTP $code).$suffix")
        else -> ApiException(ApiException.Kind.CLIENT, "The request was rejected (HTTP $code).$suffix")
    }
}

/** Suspends until the call responds; cancelling the coroutine cancels the HTTP call. */
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(mapIoException(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
