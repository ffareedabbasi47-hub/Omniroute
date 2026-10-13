package com.omni.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import com.omni.assistant.net.ApiPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class AttachException(message: String) : Exception(message)

/** An attachment that has been imported into private storage but not yet sent. */
data class DraftAttachment(
    val id: String,
    /** "image", "video" or "file". */
    val kind: String,
    val name: String,
    val mime: String,
    val path: String,
    val thumbPath: String?,
    val sizeBytes: Long,
    val note: String? = null
)

/** What gets added to a request for one stored attachment. */
data class ApiContribution(val text: String, val parts: List<ApiPart>)

/**
 * Imports picked/captured media into the app's private storage and prepares it for the API.
 *  - Images are rotated per EXIF, downscaled (max 1600 px) and re-encoded as JPEG.
 *  - Videos are NOT uploaded as video: a handful of evenly spaced frames are sampled and sent as images.
 *  - Text/code files are inlined as text; PDFs are sent as a document part (only some backends accept that).
 */
class AttachmentStore(context: Context) {
    private val ctx = context.applicationContext
    private val root = File(ctx.filesDir, "attachments").apply { mkdirs() }
    private val authority = ctx.packageName + ".fileprovider"

    // ---- Import ---------------------------------------------------------------------------

    suspend fun import(uri: Uri): DraftAttachment = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "attachment"
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val kind = AttachmentRules.classify(name, mime) ?: throw AttachException(AttachmentRules.unsupportedMessage(name))
        val size = sourceSize(uri)
        when (kind) {
            "image" -> {
                if (size > AttachmentRules.MAX_IMAGE_SOURCE_BYTES) throw AttachException("\"$name\" is too large (max 50 MB).")
                try {
                    importImage(uri, name)
                } catch (e: Exception) {
                    importRawImage(uri, name, mime)
                }
            }
            "video" -> importVideo(uri, name, size)
            else -> {
                if (size > AttachmentRules.MAX_FILE_BYTES) {
                    throw AttachException("\"$name\" is too large (max ${AttachmentRules.formatBytes(AttachmentRules.MAX_FILE_BYTES)}).")
                }
                importFile(uri, name, mime, kind == "pdf")
            }
        }
    }

    var lastCameraFile: File? = null
        private set
    var lastCameraUri: Uri? = null
        private set

    fun newCameraUri(): Uri {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", dir)
        lastCameraFile = file
        val uri = FileProvider.getUriForFile(ctx, authority, file)
        lastCameraUri = uri
        return uri
    }

    fun clearCameraCache() {
        File(ctx.cacheDir, "camera").deleteRecursively()
    }

    private fun displayName(uri: Uri): String? {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx)
                }
            }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment
    }

    private fun sourceSize(uri: Uri): Long {
        return try {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun importImage(uri: Uri, name: String): DraftAttachment {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw AttachException("Couldn't open \"$name\".")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AttachException("\"$name\" isn't a readable image.")
        var sample = 1
        val limit = AttachmentRules.MAX_IMAGE_EDGE
        while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (t: Throwable) {
            null
        } ?: throw AttachException("\"$name\" isn't a readable image.")

        val upright = rotateByExif(uri, decoded)
        val flat = flattenOnWhite(upright)
        val scaled = scaleDown(flat, AttachmentRules.MAX_IMAGE_EDGE)

        val id = UUID.randomUUID().toString()
        val file = File(root, "$id.jpg")
        val thumb = File(root, "$id-thumb.jpg")
        try {
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val small = scaleDown(scaled, 320)
            FileOutputStream(thumb).use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (small !== scaled) small.recycle()
        } catch (e: IOException) {
            file.delete()
            thumb.delete()
            throw AttachException("Couldn't save \"$name\" (storage full?).")
        } finally {
            scaled.recycle()
        }
        return DraftAttachment(id, "image", name, "image/jpeg", file.path, thumb.path, file.length())
    }

    private fun importVideo(uri: Uri, name: String, sourceBytes: Long): DraftAttachment {
        val retriever = MediaMetadataRetriever()
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        try {
            try {
                retriever.setDataSource(ctx, uri)
            } catch (e: Exception) {
                throw AttachException("\"$name\" isn't a readable video.")
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) throw AttachException("\"$name\" isn't a readable video.")
            dir.mkdirs()
            val n = AttachmentRules.frameCount(durationMs)
            var saved = 0
            for (t in AttachmentRules.frameTimesUs(durationMs, n)) {
                val frame = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val scaled = scaleDown(frame, AttachmentRules.MAX_FRAME_EDGE)
                FileOutputStream(File(dir, "frame_%02d.jpg".format(saved))).use {
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)
                }
                saved++
            }
            if (saved == 0) throw AttachException("Couldn't read any frames from \"$name\".")
            val size = if (sourceBytes > 0) sourceBytes else dir.listFiles()?.sumOf { it.length() } ?: 0L
            return DraftAttachment(
                id = id, kind = "video", name = name, mime = "video/*", path = dir.path,
                thumbPath = File(dir, "frame_00.jpg").path, sizeBytes = size,
                note = "$saved frames will be sent (no audio)"
            )
        } catch (e: AttachException) {
            dir.deleteRecursively()
            throw e
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw AttachException("Couldn't process \"$name\".")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun importFile(uri: Uri, name: String, mime: String, isPdf: Boolean): DraftAttachment {
        val id = UUID.randomUUID().toString()
        val ext = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
        val file = File(root, if (ext.isEmpty()) id else "$id.$ext")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw AttachException("Couldn't open \"$name\".")
            input.use { src ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > AttachmentRules.MAX_FILE_BYTES) throw AttachException("\"$name\" is too large.")
                        out.write(buf, 0, n)
                    }
                }
            }
        } catch (e: AttachException) {
            file.delete()
            throw e
        } catch (e: IOException) {
            file.delete()
            throw AttachException("Couldn't read \"$name\".")
        }
        val isImage = AttachmentRules.isImage(name, mime)
        val storedMime = when {
            isPdf -> "application/pdf"
            isImage -> if (mime.startsWith("image/")) mime else "image/jpeg"
            else -> mime.ifBlank { "text/plain" }
        }
        val storedKind = if (isImage) "image" else "file"
        val note = if (isPdf) "PDF is sent as a document; not every model accepts this" else null
        return DraftAttachment(id, storedKind, name, storedMime, file.path, if (isImage) file.path else null, file.length(), note)
    }

    private fun importRawImage(uri: Uri, name: String, mime: String): DraftAttachment {
        val id = UUID.randomUUID().toString()
        val ext = name.substringAfterLast('.', "jpg").lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }
        val file = File(root, "$id.$ext")
        val thumbFile = File(root, "$id-thumb.jpg")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw AttachException("Couldn't open \"$name\".")
            input.use { src ->
                FileOutputStream(file).use { out ->
                    src.copyTo(out)
                }
            }
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    var s = 1
                    while (bounds.outWidth / s > 320 || bounds.outHeight / s > 320) s *= 2
                    val thumbBmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                        inSampleSize = s
                        inPreferredConfig = Bitmap.Config.RGB_565
                    })
                    if (thumbBmp != null) {
                        FileOutputStream(thumbFile).use { thumbBmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                        thumbBmp.recycle()
                    }
                }
            } catch (t: Throwable) {
            }
        } catch (e: AttachException) {
            file.delete()
            thumbFile.delete()
            throw e
        } catch (e: IOException) {
            file.delete()
            thumbFile.delete()
            throw AttachException("Couldn't read \"$name\".")
        }
        val storedMime = if (mime.startsWith("image/")) mime else "image/jpeg"
        val thumbPath = if (thumbFile.exists() && thumbFile.length() > 0) thumbFile.path else file.path
        return DraftAttachment(id, "image", name, storedMime, file.path, thumbPath, file.length(), null)
    }

    private fun rotateByExif(uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bmp
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        val rotated = try {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (t: Throwable) {
            bmp
        }
        if (rotated !== bmp) {
            bmp.recycle()
        }
        return rotated
    }

    private fun flattenOnWhite(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        bmp.recycle()
        return out
    }

    private fun scaleDown(bmp: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxEdge) return bmp
        val ratio = maxEdge.toFloat() / longest
        val w = maxOf(1, (bmp.width * ratio).toInt())
        val h = maxOf(1, (bmp.height * ratio).toInt())
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled !== bmp) {
            bmp.recycle()
        }
        return scaled
    }

    // ---- Storage management ---------------------------------------------------------------

    fun delete(draft: DraftAttachment) = deletePaths(draft.path, draft.thumbPath)

    fun delete(a: AttachmentEntity) = deletePaths(a.path, a.thumbPath)

    private fun deletePaths(path: String, thumb: String?) {
        try {
            val f = File(path)
            if (f.startsWith(root)) f.deleteRecursively()
            if (thumb != null) {
                val t = File(thumb)
                if (t.startsWith(root)) t.delete()
            }
        } catch (e: Exception) {
        }
    }

    /** A content:// URI another app can use to open a stored file (read-only grant). */
    fun shareUri(path: String): Uri? {
        val f = File(path)
        if (!f.startsWith(root) || !f.isFile) return null
        return FileProvider.getUriForFile(ctx, authority, f)
    }

    fun framePaths(videoDir: String): List<String> {
        val dir = File(videoDir)
        if (!dir.startsWith(root)) return emptyList()
        return dir.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedBy { it.name }?.map { it.path }.orEmpty()
    }

    // ---- Preparing for the API --------------------------------------------------------------

    /**
     * Text and parts to add to the request for this attachment. When [includeBinary] is false (older
     * messages) only a short note is sent so the model still knows something was attached.
     */
    suspend fun toApi(a: AttachmentEntity, includeBinary: Boolean): ApiContribution = withContext(Dispatchers.IO) {
        if (!includeBinary) {
            return@withContext ApiContribution("[Earlier attachment: ${a.name} (not re-sent)]", emptyList())
        }
        val isImage = a.kind == "image" || AttachmentRules.isImage(a.name, a.mime)
        if (isImage) {
            val imgMime = if (a.mime.startsWith("image/")) a.mime else "image/jpeg"
            val file = File(a.path)
            return@withContext if (file.exists() && file.length() > 0) {
                val sendFile = if (file.length() > 1_500_000L) compressForApi(file) else file
                ApiContribution("", listOf(ApiPart.Image(dataUrl(imgMime, sendFile))))
            } else {
                ApiContribution("[Image attachment ${a.name} is missing or empty]", emptyList())
            }
        }
        when (a.kind) {
            "video" -> {
                val frames = framePaths(a.path)
                ApiContribution(
                    "[Video \"${a.name}\": ${frames.size} frames sampled evenly from the video follow as images. Audio is not included.]",
                    frames.map { ApiPart.Image(dataUrl("image/jpeg", File(it))) }
                )
            }
            else -> {
                if (a.mime == "application/pdf") {
                    ApiContribution("", listOf(ApiPart.Document(a.name, dataUrl("application/pdf", File(a.path)))))
                } else {
                    val file = File(a.path)
                    val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
                    val isBinary = bytes.take(1024).any { it == 0.toByte() }
                    if (isBinary) {
                        ApiContribution("[Binary file: ${a.name} (${bytes.size} bytes)]", emptyList())
                    } else {
                        var text = String(bytes, Charsets.UTF_8)
                        var truncated = false
                        if (text.length > AttachmentRules.MAX_TEXT_CHARS) {
                            text = text.take(AttachmentRules.MAX_TEXT_CHARS)
                            truncated = true
                        }
                        val tail = if (truncated) "\n[...truncated to ${AttachmentRules.MAX_TEXT_CHARS} characters]" else ""
                        ApiContribution("[File: ${a.name}]\n```\n$text\n```$tail", emptyList())
                    }
                }
            }
        }
    }

    private fun compressForApi(source: File): File {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return source
            var sample = 1
            while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeFile(source.path, opts) ?: return source
            val outFile = File(ctx.cacheDir, "api_send_${UUID.randomUUID().toString().take(8)}.jpg")
            FileOutputStream(outFile).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, it)
            }
            bmp.recycle()
            if (outFile.exists() && outFile.length() > 0) outFile else source
        } catch (t: Throwable) {
            source
        }
    }

    private fun dataUrl(mime: String, file: File): String {
        val bytes = file.readBytes()
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ---- Generated and exported files ---------------------------------------------------------

    /** A fresh file in private storage for generated media; the caller writes it, then calls [registerGenerated]. */
    fun newGeneratedFile(ext: String): File {
        val dir = File(root, "gen").apply { mkdirs() }
        return File(dir, UUID.randomUUID().toString() + "." + ext)
    }

    /** Describes a generated file as an attachment (images double as their own thumbnail). */
    fun registerGenerated(file: File, kind: String, name: String, mime: String): DraftAttachment {
        var thumb: String? = if (kind == "image") file.path else null
        if (mime.startsWith("video/")) thumb = videoThumbnail(file)
        return DraftAttachment(
            id = UUID.randomUUID().toString(), kind = kind, name = name, mime = mime,
            path = file.path, thumbPath = thumb, sizeBytes = file.length()
        )
    }

    /** First frame of a generated video as a small JPEG next to it; null if the platform can't decode it. */
    private fun videoThumbnail(video: File): String? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(video.path)
            val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val small = scaleDown(frame, 320)
            val out = File(video.parentFile, video.nameWithoutExtension + "_thumb.jpg")
            FileOutputStream(out).use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            out.path
        } catch (e: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }
    }

    /** Writes [bytes] to a share-able file with a safe name (kept in cache, cleared on the next start). */
    suspend fun writeExport(fileName: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_').ifEmpty { "file" }.take(80)
        File(dir, safe).also { it.writeBytes(bytes) }
    }

    /** content:// URI for any file this store created (generated, exported or attached). */
    fun uriFor(file: File): Uri? {
        val ok = file.isFile && (file.startsWith(root) || file.startsWith(File(ctx.cacheDir, "camera")))
        return if (ok) FileProvider.getUriForFile(ctx, authority, file) else null
    }
}

