package com.omni.assistant.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omni.assistant.OmniApp
import com.omni.assistant.data.AttachmentEntity
import com.omni.assistant.data.AttachmentRules
import com.omni.assistant.data.DraftAttachment

/** Square tile for an attachment: image/video thumbnail, or a file icon with its name. */
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

/** Attachments shown inside a chat message. */
@Composable
fun MessageAttachments(items: List<AttachmentEntity>, onOpen: (AttachmentEntity) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (a in items) {
            AttachmentTile(
                kind = a.kind, name = a.name, sizeBytes = a.sizeBytes, thumbPath = a.thumbPath, tile = 112.dp,
                onClick = { onOpen(a) }
            )
        }
    }
}

/** Full-screen viewer for an image, or the sampled frames of a video. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentViewer(attachment: AttachmentEntity, onClose: () -> Unit) {
    val omni = LocalContext.current.applicationContext as OmniApp
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
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
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
