package com.omni.assistant.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omni.assistant.ConnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen gradient with a soft violet glow at the top, like the reference designs. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ex = LocalOmni.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ex.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ex.glow.copy(alpha = if (ex.dark) 0.28f else 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.5f, 0f)
                )
            },
        content = content
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null
) {
    val ex = LocalOmni.current
    val shape = RoundedCornerShape(50)
    val fill: Brush = if (enabled) ex.accent else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Dark, rounded secondary button (like "Log in" in the reference). */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val base = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    val withClick = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Column(modifier = withClick.padding(16.dp), content = content)
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun connLabel(conn: ConnState): Pair<String, Color> = when (conn) {
    is ConnState.Ok -> "Connected" to LocalOmni.current.lime
    is ConnState.Checking -> "Checking…" to MaterialTheme.colorScheme.onSurfaceVariant
    is ConnState.Failed -> "Connection problem" to MaterialTheme.colorScheme.error
    is ConnState.Unverified -> "Not verified" to MaterialTheme.colorScheme.onSurfaceVariant
    is ConnState.Unknown -> "Not connected" to MaterialTheme.colorScheme.onSurfaceVariant
}

/** Small pill showing the active model and connection state; tapping opens the model selector. */
@Composable
fun ModelChip(model: String, conn: ConnState, onClick: () -> Unit) {
    val (label, color) = connLabel(conn)
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClickLabel = "Choose model", onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color)
        Spacer(Modifier.width(6.dp))
        Text(
            text = (if (model.isBlank()) "Choose model" else model) + " • " + label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** Animated glowing orb with an icon, used on the voice and onboarding screens. */
@Composable
fun GlowOrb(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    active: Boolean = false,
    icon: ImageVector = Icons.Default.Mic
) {
    val ex = LocalOmni.current
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 800 else 2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (active) 3500 else 12000, easing = LinearEasing)),
        label = "spin"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ex.glow.copy(alpha = 0.50f + 0.25f * pulse), Color.Transparent),
                    center = c,
                    radius = r
                ),
                radius = r,
                center = c
            )
            for (i in 0..2) {
                drawCircle(
                    color = primary.copy(alpha = 0.40f - 0.11f * i),
                    radius = r * (0.64f + 0.09f * i + 0.04f * pulse),
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            rotate(degrees = spin, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, Color(0xFFE879F9), Color(0xFF8B5CF6), Color.Transparent),
                        center = c
                    ),
                    radius = r * 0.70f,
                    center = c,
                    style = Stroke(width = 5.dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4C1D95), Color(0xFF14082F)),
                    center = c,
                    radius = r * 0.62f
                ),
                radius = r * 0.62f,
                center = c
            )
        }
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.28f))
    }
}

@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val a0 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, delayMillis = 0), RepeatMode.Reverse), label = "d0")
    val a1 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, delayMillis = 160), RepeatMode.Reverse), label = "d1")
    val a2 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500, delayMillis = 320), RepeatMode.Reverse), label = "d2")
    val color = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (a in listOf(a0, a1, a2)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = a)))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

fun decodeSampled(path: String, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        BitmapFactory.decodeFile(path, bounds)
    } catch (_: Throwable) {
        return null
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    return try {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    } catch (_: Throwable) {
        null
    }
}

/** Decodes an image file off the main thread; null while loading or if it can't be read. */
@Composable
fun rememberBitmap(path: String?, maxEdge: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path, maxEdge) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    decodeSampled(path, maxEdge)?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }
    return state.value
}
