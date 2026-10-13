package com.omni.assistant.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omni.assistant.OmniApp
import com.omni.assistant.data.AttachmentEntity
import com.omni.assistant.data.AttachmentRules
import com.omni.assistant.data.DraftAttachment
import java.io.File

/** Saves an attachment directly to user's gallery / downloads folder via MediaStore. */
fun downloadAttachment(context: Context, attachment: AttachmentEntity): Boolean {
    val src = File(attachment.path)
    if (!src.exists() || (src.isFile && src.length() == 0L)) {
        Toast.makeText(context, "Attachment file is no longer available.", Toast.LENGTH_SHORT).show()
        return false
    }
    if (src.isDirectory) {
        Toast.makeText(context, "Frame directory cannot be downloaded directly.", Toast.LENGTH_SHORT).show()
        return false
    }
    return try {
        val isVideo = attachment.kind == "video" || attachment.mime.startsWith("video/")
        val isImage = attachment.kind == "image" || attachment.mime.startsWith("image/")
        val safeName = attachment.name.ifBlank { if (isVideo) "video.mp4" else "image.jpg" }
        val mime = when {
            isVideo -> "video/mp4"
            isImage -> if (attachment.mime.startsWith("image/")) attachment.mime else "image/jpeg"
            else -> attachment.mime.ifBlank { "*/*" }
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val folder = when {
                    isVideo -> Environment.DIRECTORY_MOVIES + "/OmniAssistant"
                    isImage -> Environment.DIRECTORY_PICTURES + "/OmniAssistant"
                    else -> Environment.DIRECTORY_DOWNLOADS + "/OmniAssistant"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = when {
            isVideo -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            isImage -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Files.getContentUri("external")
            }
        }

        val uri = resolver.insert(collection, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            val destFolder = if (isVideo) "Movies/OmniAssistant" else if (isImage) "Pictures/OmniAssistant" else "Downloads/OmniAssistant"
            Toast.makeText(context, "✅ Saved to $destFolder", Toast.LENGTH_LONG).show()
            true
        } else {
            saveToDownloadsFallback(context, src, safeName)
        }
    } catch (e: Exception) {
        saveToDownloadsFallback(context, src, attachment.name)
    }
}

private fun saveToDownloadsFallback(context: Context, src: File, name: String): Boolean {
    return try {
        val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (destDir != null && (destDir.exists() || destDir.mkdirs())) {
            val target = File(destDir, name)
            src.copyTo(target, overwrite = true)
            Toast.makeText(context, "✅ Saved to Downloads/$name", Toast.LENGTH_LONG).show()
            true
        } else {
            Toast.makeText(context, "Could not access Downloads folder.", Toast.LENGTH_SHORT).show()
            false
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        false
    }
}

/** Square tile for an attachment in composer preview. */
@Composable
private fun AttachmentTile(
    kind: String,
    name: String,
    sizeBytes: Long,
    thumbPath: String?,
    tile: Dp,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val bitmap = rememberBitmap(thumbPath, 320)
    Box(
        Modifier
            .size(tile)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClickLabel = "Open $name", onClick = onClick)
    ) {
        if (kind == "file" && thumbPath == null) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    AttachmentRules.formatBytes(sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = if (kind == "video") "Video $name" else "Image $name",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            if (kind == "video" || kind == "file") {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(role = Role.Button, onClickLabel = "Remove $name", onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove $name", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Row of pending attachments above the text field. */
@Composable
fun ComposerAttachmentRow(
    drafts: List<DraftAttachment>,
    importing: Boolean,
    onOpen: (DraftAttachment) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        items(drafts, key = { it.id }) { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AttachmentTile(
                    kind = d.kind, name = d.name, sizeBytes = d.sizeBytes, thumbPath = d.thumbPath, tile = 76.dp,
                    onClick = { onOpen(d) }, onRemove = { onRemove(d.id) }
                )
            }
        }
        if (importing) {
            item {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** Large inline image card with preview and direct download button. */
@Composable
private fun ImageAttachmentCard(
    attachment: AttachmentEntity,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bitmap = rememberBitmap(attachment.thumbPath ?: attachment.path, 1200)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .clickable(role = Role.Button, onClickLabel = "View full image", onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "🖼️ Photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = AttachmentRules.formatBytes(attachment.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onDownload)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download image",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/** Large inline video card with video preview, play button and direct download button. */
@Composable
private fun VideoAttachmentCard(
    attachment: AttachmentEntity,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bitmap = rememberBitmap(attachment.thumbPath ?: attachment.path, 800)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black)
                    .clickable(role = Role.Button, onClickLabel = "Play video", onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "🎥 Video (MP4)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${AttachmentRules.formatBytes(attachment.sizeBytes)} • Tap to play",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onDownload)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download video",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Download MP4",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/** Generic file card with download button. */
@Composable
private fun FileAttachmentCard(
    attachment: AttachmentEntity,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AttachmentRules.formatBytes(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDownload)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download file",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** Attachments shown inside a chat message with inline preview and direct download buttons. */
@Composable
fun MessageAttachments(items: List<AttachmentEntity>, onOpen: (AttachmentEntity) -> Unit) {
    val ctx = LocalContext.current
    val omni = ctx.applicationContext as OmniApp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (a in items) {
            val isVideo = a.kind == "video" || a.mime.startsWith("video/")
            val isImage = a.kind == "image" || a.mime.startsWith("image/")

            if (isImage) {
                ImageAttachmentCard(
                    attachment = a,
                    onOpen = { onOpen(a) },
                    onDownload = { downloadAttachment(ctx, a) }
                )
            } else if (isVideo) {
                VideoAttachmentCard(
                    attachment = a,
                    onPlay = {
                        val file = File(a.path)
                        if (file.isFile) {
                            val uri = omni.attachments.shareUri(a.path)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    onOpen(a)
                                }
                            } else {
                                onOpen(a)
                            }
                        } else {
                            onOpen(a)
                        }
                    },
                    onDownload = { downloadAttachment(ctx, a) }
                )
            } else {
                FileAttachmentCard(
                    attachment = a,
                    onOpen = { onOpen(a) },
                    onDownload = { downloadAttachment(ctx, a) }
                )
            }
        }
    }
}

/** Full-screen viewer for an image or sampled video frames with direct download button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentViewer(attachment: AttachmentEntity, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val omni = ctx.applicationContext as OmniApp
    val paths: List<String> =
        if (attachment.kind == "video") omni.attachments.framePaths(attachment.path) else listOf(attachment.path)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (paths.isEmpty()) {
                Text(
                    "This attachment is no longer available.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val pagerState = rememberPagerState(pageCount = { paths.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val bmp = rememberBitmap(paths[page], 1600)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = attachment.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(attachment.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (attachment.kind == "video") {
                        Text(
                            "Frame ${pagerState.currentPage + 1} of ${paths.size} • these frames are what the model receives",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Top action bar: Direct Download Button + Close Button
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { downloadAttachment(ctx, attachment) }) {
                    Icon(Icons.Default.Download, contentDescription = "Download attachment", tint = Color.White)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
                }
            }
        }
    }
}

/** Viewer for a not-yet-sent draft (image or video frames). Files open externally instead. */
@Composable
fun DraftViewer(draft: DraftAttachment, onClose: () -> Unit) {
    AttachmentViewer(
        attachment = AttachmentEntity(
            messageId = 0, kind = draft.kind, name = draft.name, mime = draft.mime,
            path = draft.path, thumbPath = draft.thumbPath, sizeBytes = draft.sizeBytes
        ),
        onClose = onClose
    )
}
