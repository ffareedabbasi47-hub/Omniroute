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
import com.omni.assistant.data.FileExport
import com.omni.assistant.data.MessageEntity
import com.omni.assistant.net.ApiException
import com.omni.assistant.net.ApiPart
import com.omni.assistant.net.ChatTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ChatUi(
    val streaming: Boolean = false,
    val partial: String = "",
    val status: String? = null,
    val error: String? = null,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val importing: Boolean = false,
    val notice: String? = null,
    val activeFile: FileExport.LiveFileInfo? = null,
    val completedFiles: List<FileExport.LiveFileInfo> = emptyList(),
    val thinking: String? = null
)

data class ConversationTask(
    val cid: Long,
    val streaming: Boolean = true,
    val partial: String = "",
    val status: String? = null,
    val activeFile: FileExport.LiveFileInfo? = null,
    val completedFiles: List<FileExport.LiveFileInfo> = emptyList(),
    val thinking: String? = null,
    val error: String? = null
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
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.observeMessages(id).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val attachmentsByMessage: StateFlow<Map<Long, List<AttachmentEntity>>> = _conversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.observeAttachments(id).catch { emit(emptyList()) }
        }
        .map { list -> list.groupBy { it.messageId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val conversations: StateFlow<List<ConversationEntity>> = dao.observeConversations()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val title: StateFlow<String> = combine(_conversationId, conversations) { id, list ->
        list.firstOrNull { it.id == id }?.title ?: "New chat"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "New chat")

    // Concurrent multi-chat background task registry
    private val runningJobs = ConcurrentHashMap<Long, Job>()
    private val activeTasks = MutableStateFlow<Map<Long, ConversationTask>>(emptyMap())

    private val _uiBase = MutableStateFlow(ChatUi())
    val ui: StateFlow<ChatUi> = combine(_uiBase, _conversationId, activeTasks) { base, cid, tasks ->
        val task = if (cid != null) tasks[cid] else null
        if (task != null && task.streaming) {
            base.copy(
                streaming = true,
                partial = task.partial,
                status = task.status,
                activeFile = task.activeFile,
                completedFiles = task.completedFiles,
                thinking = task.thinking,
                error = task.error ?: base.error
            )
        } else {
            base.copy(
                streaming = false,
                partial = "",
                status = null,
                activeFile = null,
                completedFiles = emptyList(),
                thinking = null
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUi())

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _pending = MutableStateFlow<List<DraftAttachment>>(emptyList())
    val pending: StateFlow<List<DraftAttachment>> = _pending.asStateFlow()

    private val _mode = MutableStateFlow(ChatMode.CHAT)
    val mode: StateFlow<ChatMode> = _mode.asStateFlow()

    /** Failed generation messages -> the mode that produced them, so Retry repeats the right request. */
    private val failedModes = HashMap<Long, ChatMode>()

    init {
        store.clearCameraCache()
    }

    fun setDraft(text: String) {
        _draft.value = text
    }

    fun setMode(m: ChatMode) {
        val active = _conversationId.value?.let { activeTasks.value[it]?.streaming } == true
        if (active) return
        if (m != ChatMode.CHAT && _pending.value.isNotEmpty()) {
            notify("Attachments are only used in Chat mode, so they were kept for later.")
        }
        _mode.value = m
    }

    fun dismissMessages() {
        _uiBase.update { it.copy(error = null, notice = null) }
    }

    fun notify(message: String) {
        _uiBase.update { it.copy(notice = message) }
    }

    // ---- Attachments ----------------------------------------------------------------------

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiBase.update { it.copy(importing = true) }
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
            _uiBase.update { it.copy(importing = false) }
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

    // ---- Conversations (Multi-chat background safe) ---------------------------------------

    fun newChat() {
        // Do NOT cancel background jobs - previous chat work continues running!
        discardPending()
        _conversationId.value = null
        _draft.value = ""
        _uiBase.update { it.copy(error = null, notice = null) }
    }

    fun openConversation(id: Long) {
        // Switching conversation keeps previous conversation streaming safely in background!
        _conversationId.value = id
        _uiBase.update { it.copy(error = null, notice = null) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            runningJobs.remove(id)?.cancel()
            activeTasks.update { it - id }
            if (_conversationId.value == id) {
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
            runningJobs.forEach { (_, job) -> job.cancel() }
            runningJobs.clear()
            activeTasks.value = emptyMap()
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

    /** Returns true if the message was accepted. Runs on Application scope so tasks survive navigation! */
    fun send(raw: String): Boolean {
        val text = raw.trim()
        val mode = _mode.value
        val attached = if (mode == ChatMode.CHAT) _pending.value else emptyList()
        if ((text.isEmpty() && attached.isEmpty()) || _uiBase.value.importing) return false
        val cfg = omni.settings.current
        if (cfg.baseUrl.isBlank() || (mode == ChatMode.CHAT && cfg.model.isBlank())) {
            _uiBase.update { it.copy(error = "Set the base URL and a model in Settings first.") }
            return false
        }
        if (mode == ChatMode.IMAGE && text.isEmpty()) return false
        omni.voice.stopSpeaking()
        if (mode == ChatMode.CHAT) _pending.value = emptyList()

        val now = System.currentTimeMillis()
        var cid = _conversationId.value

        // Application scope survives ViewModel / Activity lifecycle!
        omni.appScope.launch {
            if (cid == null) {
                val titleSource = if (text.isNotEmpty()) text else attached.first().name
                cid = dao.insertConversation(
                    ConversationEntity(title = titleFrom(titleSource), createdAt = now, updatedAt = now)
                )
                _conversationId.value = cid
            }
            val targetCid = cid ?: return@launch
            val messageId = dao.insertMessage(
                MessageEntity(conversationId = targetCid, role = "user", content = text, createdAt = now)
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
            dao.touch(targetCid, now)
            if (mode == ChatMode.CHAT) runGeneration(targetCid) else runMedia(targetCid, text, mode)
        }
        return true
    }

    fun retry(failedMessageId: Long) {
        val cid = _conversationId.value ?: return
        if (activeTasks.value[cid]?.streaming == true) return
        omni.appScope.launch {
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
        runningJobs[cid] = coroutineContext.job
        val kindWord = if (mode == ChatMode.IMAGE) "image" else "video"
        activeTasks.update {
            it + (cid to ConversationTask(
                cid = cid,
                streaming = true,
                status = "Generating $kindWord with AI..."
            ))
        }
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
            omni.connection.markOk()
        } catch (e: CancellationException) {
            files.forEach { store.delete(it) }
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
            if (api.kind == ApiException.Kind.NETWORK || api.kind == ApiException.Kind.AUTH) {
                omni.connection.markFailed(api.message.orEmpty())
            }
        } finally {
            runningJobs.remove(cid)
            activeTasks.update { it - cid }
        }
    }

    /** Explicitly cancels only the active chat being viewed. */
    fun cancel() {
        val cid = _conversationId.value ?: return
        runningJobs.remove(cid)?.cancel()
        activeTasks.update { it - cid }
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
        runningJobs[cid] = coroutineContext.job
        activeTasks.update {
            it + (cid to ConversationTask(
                cid = cid,
                streaming = true,
                status = "🧠 Thinking & planning project architecture..."
            ))
        }
        val sb = StringBuilder()
        var hadParts = false
        var lastEmitTime = 0L
        try {
            val (history, anyParts) = buildHistory(cid)
            hadParts = anyParts
            omni.client.streamChat(history).collect { chunk ->
                sb.append(chunk)
                val now = System.currentTimeMillis()
                // Throttled UI state updates to prevent CPU starvation
                if (now - lastEmitTime >= 100 || sb.length < 200) {
                    lastEmitTime = now
                    val currentText = sb.toString()
                    val progress = FileExport.parseStreamingProgress(currentText)
                    activeTasks.update { map ->
                        map + (cid to ConversationTask(
                            cid = cid,
                            streaming = true,
                            partial = progress.cleanText,
                            status = progress.stageSummary,
                            activeFile = progress.activeFile,
                            completedFiles = progress.completedFiles,
                            thinking = progress.thinking
                        ))
                    }
                }
            }
            val rawFinal = sb.toString()
            val (_, cleanFinal) = FileExport.splitThinking(rawFinal)
            val finalText = cleanFinal.ifBlank { rawFinal }
            val now = System.currentTimeMillis()
            dao.insertMessage(MessageEntity(conversationId = cid, role = "assistant", content = finalText, createdAt = now))
            dao.touch(cid, now)
            omni.connection.markOk()
            if (omni.settings.current.speakReplies) speak(finalText)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (sb.isNotBlank()) {
                    val rawCancelled = sb.toString()
                    val (_, cleanCancelled) = FileExport.splitThinking(rawCancelled)
                    val saved = cleanCancelled.ifBlank { rawCancelled }
                    val now = System.currentTimeMillis()
                    dao.insertMessage(
                        MessageEntity(conversationId = cid, role = "assistant", content = saved, createdAt = now)
                    )
                    dao.touch(cid, now)
                }
            }
            throw e
        } catch (e: ApiException) {
            fail(cid, sb.toString(), e, hadParts)
        } catch (e: Exception) {
            fail(cid, sb.toString(), ApiException(ApiException.Kind.CLIENT, "Unexpected error (${e.javaClass.simpleName})."), hadParts)
        } finally {
            runningJobs.remove(cid)
            activeTasks.update { it - cid }
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
        if (e.kind == ApiException.Kind.NETWORK || e.kind == ApiException.Kind.AUTH) {
            omni.connection.markFailed(msg)
        }
    }

    // ---- Voice ------------------------------------------------------------------------------

    /** The caller must already hold RECORD_AUDIO. With autoSend the recognised text is sent immediately. */
    fun toggleMic(autoSend: Boolean) {
        if (_uiBase.value.listening) {
            omni.voice.stopListening()
            _uiBase.update { it.copy(listening = false) }
            return
        }
        omni.voice.stopSpeaking()
        _uiBase.update { it.copy(listening = true, speaking = false, notice = null) }
        omni.voice.startListening(
            languageTag = omni.settings.current.language,
            heardPartial = { text -> _draft.value = text },
            heardFinal = { text ->
                _uiBase.update { it.copy(listening = false) }
                if (autoSend) {
                    if (send(text)) _draft.value = "" else _draft.value = text
                } else {
                    _draft.value = text
                }
            },
            failed = { message -> _uiBase.update { it.copy(listening = false, notice = message) } }
        )
    }

    fun speak(markdownText: String) {
        val plain = MarkdownParser.toSpeakable(markdownText)
        _uiBase.update { it.copy(speaking = true) }
        omni.voice.speak(
            text = plain,
            languageTag = omni.settings.current.language,
            finished = { _uiBase.update { it.copy(speaking = false) } },
            failed = { message -> _uiBase.update { it.copy(speaking = false, notice = message) } }
        )
    }

    fun stopSpeaking() {
        omni.voice.stopSpeaking()
        _uiBase.update { it.copy(speaking = false) }
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
