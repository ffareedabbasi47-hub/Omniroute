package com.omni.assistant.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the Android share sheet for one or more content:// URIs (the user can pick "Save to Files/Drive"). */
fun shareUris(ctx: Context, uris: List<Uri>, mime: String, title: String): Boolean {
    if (uris.isEmpty()) return false
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    }
    intent.type = mime
    val clip = ClipData.newRawUri(title, uris[0])
    for (u in uris.drop(1)) clip.addItem(ClipData.Item(u))
    intent.clipData = clip
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        ctx.startActivity(Intent.createChooser(intent, title))
        true
    } catch (e: Exception) {
        false
    }
}
