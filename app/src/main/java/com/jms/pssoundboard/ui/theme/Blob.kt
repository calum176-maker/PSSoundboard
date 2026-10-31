package com.jms.pssoundboard.ui.theme

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Organic blob outline, the same idea as CSS `border-radius: a b c d / e f g h`:
 * horizontal then vertical corner radii as fractions of the size, in the order
 * top-left, top-right, bottom-right, bottom-left.
 */
class BlobShape(private val horizontal: FloatArray, private val vertical: FloatArray) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun corner(i: Int) = CornerRadius(horizontal[i] * size.width, vertical[i] * size.height)
        return Outline.Rounded(
            RoundRect(
                left = 0f, top = 0f, right = size.width, bottom = size.height,
                topLeftCornerRadius = corner(0),
                topRightCornerRadius = corner(1),
                bottomRightCornerRadius = corner(2),
                bottomLeftCornerRadius = corner(3),
            )
        )
    }
}

/** The shapes a user can pick for a pad in the pad editor. */
enum class PadShape(val label: String, val shape: Shape) {
    PEBBLE("Pebble", BlobShape(floatArrayOf(.58f, .42f, .55f, .45f), floatArrayOf(.52f, .60f, .40f, .48f))),
    BEAN("Bean", BlobShape(floatArrayOf(.42f, .58f, .38f, .62f), floatArrayOf(.60f, .44f, .56f, .40f))),
    DROP("Drop", BlobShape(floatArrayOf(.50f, .50f, .46f, .54f), floatArrayOf(.38f, .40f, .60f, .62f))),
    CLOUD("Cloud", BlobShape(floatArrayOf(.62f, .38f, .60f, .40f), floatArrayOf(.45f, .55f, .45f, .55f))),
    ROUND("Round", BlobShape(floatArrayOf(.5f, .5f, .5f, .5f), floatArrayOf(.5f, .5f, .5f, .5f))),
    SQUISH("Squish", BlobShape(floatArrayOf(.36f, .44f, .40f, .48f), floatArrayOf(.46f, .38f, .52f, .42f)));

    companion object {
        /** The pad's chosen shape, or a varied default based on its slot. */
        fun of(name: String?, slot: Int): PadShape =
            entries.firstOrNull { it.name == name } ?: entries[slot % entries.size]
    }
}

/** Soft blob used for chips and small buttons. */
val ChipBlob = BlobShape(floatArrayOf(.55f, .45f, .50f, .50f), floatArrayOf(.50f, .55f, .45f, .50f))

private object Grain {
    /** A small tile of grey noise, drawn repeated over blobs with an overlay blend. */
    val brush: ShaderBrush by lazy {
        val size = 96
        val random = Random(7)
        val pixels = IntArray(size * size) {
            val v = random.nextInt(256)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * Fills the element with [colour] in [shape] and gives it a gummy-sweet finish:
 * a gloss highlight, a squish shadow along the bottom edge and a fine grain.
 */
fun Modifier.gummy(colour: Color, shape: Shape): Modifier =
    clip(shape)
        .background(colour)
        .drawWithContent {
            val reach = max(size.width, size.height)
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.16f),
                    radius = reach * 0.62f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(size.width * 0.72f, size.height * 1.18f),
                    radius = reach * 0.7f,
                )
            )
            drawRect(Brush.verticalGradient(0.86f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f)))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.07f to Color.Transparent))
            drawRect(Grain.brush, alpha = 0.45f, blendMode = BlendMode.Overlay)
            drawContent()
        }

/** True when the user has turned animations off (Settings › Accessibility › Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * While [active], the blob throbs and sends out rings in its own colour, so the pad that is
 * spewing is obvious at a glance. With reduced motion it shows one steady ring instead.
 * Apply before [gummy] so the rings draw outside the clipped blob.
 */
fun Modifier.spewPulse(active: Boolean, colour: Color, shape: Shape): Modifier = composed {
    if (!active) return@composed this
    val reducedMotion = rememberReducedMotion()
    val phase = if (reducedMotion) {
        0.35f
    } else {
        val transition = rememberInfiniteTransition(label = "spew_pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "spew_phase",
        ).value
    }
    val throb = if (reducedMotion) 1f else 1f + 0.045f * sin(phase * Math.PI.toFloat()).coerceAtLeast(0f)
    this
        .graphicsLayer {
            scaleX = throb
            scaleY = throb
        }
        .drawBehind {
            val ring = 16.dp.toPx() * phase
            val outline = shape.createOutline(size, layoutDirection, this)
            val grow = 1f + (2 * ring) / max(size.width, size.height)
            scale(grow) {
                drawOutline(outline, colour.copy(alpha = 0.35f * (1f - phase) + 0.05f), style = Stroke(width = 6.dp.toPx()))
            }
        }
}
