package com.omni.assistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omni.assistant.OmniApp
import com.omni.assistant.data.ApiContribution
import com.omni.assistant.data.AttachException
import com.omni.assistant.data.AttachmentEntity
import com.omni.assistant.data.AttachmentRules
import com.omni.assistant.data.ConversationEntity
import com.omni.assistant.data.DraftAttachment
import com.omni.assistant.data.MessageEntity
import com.omni.assistant.net.ApiException
import com.omni.assistant.net.ApiPart
import com.omni.assistant.net.ChatTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUi(
    val streaming: Boolean = false,
    val partial: String = "",
    val error: String? = null,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val importing: Boolean = false,
    val notice: String? = null
)

enum class ChatMode { CHAT, IMAGE, VIDEO }

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val omni = app as OmniApp
    private val dao = omni.db.chatDao()
    private val store = omni.attachments

    private val _conversationId = MutableStateFlow<Long?>(null)
    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val attachmentsByMessage: StateFlow<Map<Long, List<AttachmentEntity>>> = _conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeAttachments(id) }
        .map { list -> list.groupBy { it.messageId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val conversations: StateFlow<List<ConversationEntity>> = dao.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val title: StateFlow<String> = combine(_conversationId, conversations) { id, list ->
        list.firstOrNull { it.id == id }?.title ?: "New chat"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "New chat")

    private val _ui = MutableStateFlow(ChatUi())
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _pending = MutableStateFlow<List<DraftAttachment>>(emptyList())
    val pending: StateFlow<List<DraftAttachment>> = _pending.asStateFlow()

    private val _mode = MutableStateFlow(ChatMode.CHAT)
    val mode: StateFlow<ChatMode> = _mode.asStateFlow()

    /** Failed generation messages -> the mode that produced them, so Retry repeats the right request. */
    private val failedModes = HashMap<Long, ChatMode>()

    private var job: Job? = null

    init {
        store.clearCameraCache()
    }

    fun setDraft(text: String) {
        _draft.value = text
    }

    fun setMode(m: ChatMode) {
        if (_ui.value.streaming) return
        if (m != ChatMode.CHAT && _pending.value.isNotEmpty()) {
            notify("Attachments are only used in Chat mode, so they were kept for later.")
        }
        _mode.value = m
    }

    fun dismissMessages() {
        _ui.update { it.copy(error = null, notice = null) }
    }

    fun notify(message: String) {
        _ui.update { it.copy(notice = message) }
    }

    // ---- Attachments ----------------------------------------------------------------------

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true) }
            for (uri in uris) {
                if (_pending.value.size >= AttachmentRules.MAX_ATTACHMENTS_PER_MESSAGE) {
                    notify("You can attach up to ${AttachmentRules.MAX_ATTACHMENTS_PER_MESSAGE} items per message.")
                    break
                }
                try {
                    val draft = store.import(uri)
                    _pending.update { it + draft }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AttachException) {
                    notify(e.message.orEmpty())
                } catch (e: Exception) {
                    notify("Couldn't attach that item.")
                }
            }
            _ui.update { it.copy(importing = false) }
        }
    }

    fun removePending(id: String) {
        val item = _pending.value.firstOrNull { it.id == id } ?: return
        _pending.update { list -> list.filter { it.id != id } }
        store.delete(item)
    }

    private fun discardPending() {
        val items = _pending.value
        _pending.value = emptyList()
        items.forEach { store.delete(it) }
    }

    // ---- Conversations --------------------------------------------------------------------

    fun newChat() {
        cancel()
        discardPending()
        _conversationId.value = null
        _draft.value = ""
        _ui.update { it.copy(error = null, notice = null) }
    }

    fun openConversation(id: Long) {
        cancel()
        _conversationId.value = id
        _ui.update { it.copy(error = null, notice = null) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_conversationId.value == id) {
                cancel()
                _conversationId.value = null
            }
            val files = dao.getAttachments(id)
            dao.deleteConversation(id)
            files.forEach { store.delete(it) }
        }
    }

    fun renameConversation(id: Long, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        viewModelScope.launch { dao.rename(id, t.take(80)) }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            cancel()
            _conversationId.value = null
            val files = dao.getAllAttachments()
            dao.deleteAll()
            files.forEach { store.delete(it) }
        }
    }

    // ---- Sending, cancelling, retrying -----------------------------------------------------

    fun sendDraft() {
        val text = _draft.value
        if (send(text)) _draft.value = ""
    }

    /** Returns true if the message was accepted. Sends the pending attachments along with it. */
    fun send(raw: String): Boolean {
        val text = raw.trim()
        val mode = _mode.value
        val attached = if (mode == ChatMode.CHAT) _pending.value else emptyList()
        if ((text.isEmpty() && attached.isEmpty()) || _ui.value.streaming || _ui.value.importing) return false
        val cfg = omni.settings.current
        if (cfg.baseUrl.isBlank() || (mode == ChatMode.CHAT && cfg.model.isBlank())) {
            _ui.update { it.copy(error = "Set the base URL and a model in Settings first.") }
            return false
        }
        if (mode == ChatMode.IMAGE && text.isEmpty()) return false
        omni.voice.stopSpeaking()
        if (mode == ChatMode.CHAT) _pending.value = emptyList()
        _ui.update { it.copy(streaming = true, partial = "", error = null, notice = null, speaking = false) }
        job = viewModelScope.launch {
            val now = System.currentTimeMillis()
            var cid = _conversationId.value
            if (cid == null) {
                val titleSource = if (text.isNotEmpty()) text else attached.first().name
                cid = dao.insertConversation(
                    ConversationEntity(title = titleFrom(titleSource), createdAt = now, updatedAt = now)
                )
                _conversationId.value = cid
            }
            val messageId = dao.insertMessage(
                MessageEntity(conversationId = cid, role = "user", content = text, createdAt = now)
            )
            if (attached.isNotEmpty()) {
                dao.insertAttachments(
                    attached.map {
                        AttachmentEntity(
                            messageId = messageId, kind = it.kind, name = it.name, mime = it.mime,
                            path = it.path, thumbPath = it.thumbPath, sizeBytes = it.sizeBytes
                        )
                    }
                )
            }
            dao.touch(cid, now)
            if (mode == ChatMode.CHAT) runGeneration(cid) else runMedia(cid, text, mode)
        }
        return true
    }

    fun retry(failedMessageId: Long) {
        val cid = _conversationId.value ?: return
        if (_ui.value.streaming) return
        _ui.update { it.copy(streaming = true, partial = "", error = null, notice = null) }
        job = viewModelScope.launch {
            dao.deleteMessage(failedMessageId)
            val m = failedModes.remove(failedMessageId) ?: ChatMode.CHAT
            if (m == ChatMode.CHAT) {
                runGeneration(cid)
            } else {
                val prompt = dao.getMessages(cid).lastOrNull { it.role == "user" }?.content.orEmpty()
                runMedia(cid, prompt, m)
            }
        }
    }

    /** Image / video generation: one request, results saved privately and attached to the reply. */
    private suspend fun runMedia(cid: Long, prompt: String, mode: ChatMode) {
        _ui.update { it.copy(streaming = true, partial = "", error = null) }
        val kindWord = if (mode == ChatMode.IMAGE) "image" else "video"
        val files = ArrayList<DraftAttachment>()
        try {
            val items = if (mode == ChatMode.IMAGE) omni.client.requestImages(prompt) else omni.client.requestVideo(prompt)
            for ((i, item) in items.withIndex()) {
                val tmp = store.newGeneratedFile("bin")
                val type = try {
                    omni.client.saveItem(item, tmp, if (mode == ChatMode.IMAGE) 40L * 1024 * 1024 else 300L * 1024 * 1024)
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
                if (type == null || type.kind != kindWord) {
                    tmp.delete()
                    throw ApiException(
                        ApiException.Kind.BAD_RESPONSE,
                        "The server returned data that isn't a recognised $kindWord file."
                    )
                }
                val dest = java.io.File(tmp.parentFile, tmp.nameWithoutExtension + "." + type.ext)
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    throw ApiException(ApiException.Kind.CLIENT, "Couldn't store the generated file.")
                }
                val name = if (items.size > 1) "$kindWord-${i + 1}.${type.ext}" else "$kindWord.${type.ext}"
                files.add(store.registerGenerated(dest, if (mode == ChatMode.IMAGE) "image" else "file", name, type.mime))
            }
            val now = System.currentTimeMillis()
            val mid = dao.insertMessage(
                MessageEntity(
                    conversationId = cid, role = "assistant", createdAt = now,
                    content = "Generated $kindWord for: \"${prompt.take(200)}\""
                )
            )
            dao.insertAttachments(
                files.map {
                    AttachmentEntity(
                        messageId = mid, kind = it.kind, name = it.name, mime = it.mime,
                        path = it.path, thumbPath = it.thumbPath, sizeBytes = it.sizeBytes
                    )
                }
            )
            dao.touch(cid, now)
            _ui.update { it.copy(streaming = false) }
            omni.connection.markOk()
        } catch (e: CancellationException) {
            files.forEach { store.delete(it) }
            withContext(NonCancellable) { _ui.update { it.copy(streaming = false, partial = "") } }
            throw e
        } catch (e: Exception) {
            files.forEach { store.delete(it) }
            val api = e as? ApiException
                ?: ApiException(ApiException.Kind.CLIENT, "Unexpected error (${e.javaClass.simpleName}).")
            val id = dao.insertMessage(
                MessageEntity(
                    conversationId = cid, role = "assistant", content = "",
                    createdAt = System.currentTimeMillis(), failed = true, error = api.message
                )
            )
            failedModes[id] = mode
            _ui.update { it.copy(streaming = false, partial = "") }
            if (api.kind == ApiException.Kind.NETWORK || api.kind == ApiException.Kind.AUTH) {
                omni.connection.markFailed(api.message.orEmpty())
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun buildHistory(cid: Long): Pair<List<ChatTurn>, Boolean> {
        val msgs = dao.getMessages(cid)
        val atts = dao.getAttachments(cid).groupBy { it.messageId }
        val withAttachments = msgs.filter { it.role == "user" && atts.containsKey(it.id) }.map { it.id }
        val recent = withAttachments.takeLast(AttachmentRules.RESEND_LAST_N_MESSAGES).toSet()
        val history = ArrayList<ChatTurn>()
        val sys = omni.settings.current.systemPrompt.trim()
        if (sys.isNotEmpty()) history.add(ChatTurn("system", sys))
        var anyParts = false
        for (m in msgs) {
            if (m.failed) continue
            val list = atts[m.id].orEmpty()
            if (list.isEmpty() || m.role != "user") {
                history.add(ChatTurn(m.role, m.content))
                continue
            }
            val text = StringBuilder(m.content)
            val parts = ArrayList<ApiPart>()
            for (a in list) {
                val c: ApiContribution = try {
                    store.toApi(a, includeBinary = m.id in recent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ApiContribution("[Attachment ${a.name} is no longer available]", emptyList())
                }
                if (c.text.isNotBlank()) {
                    if (text.isNotEmpty()) text.append("\n\n")
                    text.append(c.text)
                }
                parts.addAll(c.parts)
            }
            if (parts.isNotEmpty()) anyParts = true
            history.add(ChatTurn(m.role, text.toString(), parts))
        }
        return history to anyParts
    }

    private suspend fun runGeneration(cid: Long) {
        _ui.update { it.copy(streaming = true, partial = "", error = null) }
        val sb = StringBuilder()
        var hadParts = false
        try {
            val (history, anyParts) = buildHistory(cid)
            hadParts = anyParts
            omni.client.streamChat(history).collect { chunk ->
                sb.append(chunk)
                _ui.update { it.copy(partial = sb.toString()) }
            }
            val finalText = sb.toString()
            val now = System.currentTimeMillis()
            dao.insertMessage(MessageEntity(conversationId = cid, role = "assistant", content = finalText, createdAt = now))
            dao.touch(cid, now)
            _ui.update { it.copy(streaming = false, partial = "") }
            omni.connection.markOk()
            if (omni.settings.current.speakReplies) speak(finalText)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (sb.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    dao.insertMessage(
                        MessageEntity(conversationId = cid, role = "assistant", content = sb.toString(), createdAt = now)
                    )
                    dao.touch(cid, now)
                }
                _ui.update { it.copy(streaming = false, partial = "") }
            }
            throw e
        } catch (e: ApiException) {
            fail(cid, sb.toString(), e, hadParts)
        } catch (e: Exception) {
            fail(cid, sb.toString(), ApiException(ApiException.Kind.CLIENT, "Unexpected error (${e.javaClass.simpleName})."), hadParts)
        }
    }

    private suspend fun fail(cid: Long, partial: String, e: ApiException, hadAttachments: Boolean) {
        var msg = e.message ?: "Something went wrong."
        if (hadAttachments && e.kind == ApiException.Kind.CLIENT) {
            msg += " If this chat has images, videos or PDFs, the selected model or server may not support them. " +
                "Try a vision-capable model or remove the attachment."
        }
        dao.insertMessage(
            MessageEntity(
                conversationId = cid, role = "assistant", content = partial,
                createdAt = System.currentTimeMillis(), failed = true, error = msg
            )
        )
        _ui.update { it.copy(streaming = false, partial = "") }
        if (e.kind == ApiException.Kind.NETWORK || e.kind == ApiException.Kind.TIMEOUT || e.kind == ApiException.Kind.AUTH) {
            omni.connection.markFailed(e.message ?: msg)
        }
    }

    // ---- Voice ------------------------------------------------------------------------------

    /** The caller must already hold RECORD_AUDIO. With autoSend the recognised text is sent immediately. */
    fun toggleMic(autoSend: Boolean) {
        if (_ui.value.listening) {
            omni.voice.stopListening()
            _ui.update { it.copy(listening = false) }
            return
        }
        omni.voice.stopSpeaking()
        _ui.update { it.copy(listening = true, speaking = false, notice = null) }
        omni.voice.startListening(
            languageTag = omni.settings.current.language,
            heardPartial = { text -> _draft.value = text },
            heardFinal = { text ->
                _ui.update { it.copy(listening = false) }
                if (autoSend) {
                    if (send(text)) _draft.value = "" else _draft.value = text
                } else {
                    _draft.value = text
                }
            },
            failed = { message -> _ui.update { it.copy(listening = false, notice = message) } }
        )
    }

    fun speak(markdownText: String) {
        val plain = MarkdownParser.toSpeakable(markdownText)
        _ui.update { it.copy(speaking = true) }
        omni.voice.speak(
            text = plain,
            languageTag = omni.settings.current.language,
            finished = { _ui.update { it.copy(speaking = false) } },
            failed = { message -> _ui.update { it.copy(speaking = false, notice = message) } }
        )
    }

    fun stopSpeaking() {
        omni.voice.stopSpeaking()
        _ui.update { it.copy(speaking = false) }
    }

    override fun onCleared() {
        omni.voice.stopListening()
        omni.voice.stopSpeaking()
        super.onCleared()
    }

    private fun titleFrom(text: String): String {
        val one = text.replace('\n', ' ').trim()
        return if (one.length <= 40) one else one.take(40).trimEnd() + "…"
    }
}
