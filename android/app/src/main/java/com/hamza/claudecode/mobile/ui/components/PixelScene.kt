package com.hamza.claudecode.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.claudecode.mobile.ui.theme.JetBrainsMono
import com.hamza.claudecode.mobile.ui.theme.Term

/**
 * The night-sky pixel-art scene from the Claude Code welcome screen:
 * dithered clouds, a pixel "C" moon, twinkling stars and the coral mascot.
 * Everything is drawn on a 70 x 30 cell grid so it scales to any width.
 */

private const val GRID_W = 70f
private const val GRID_H = 30f

// '#' = mascot body, 'o' = background (eyes)
private val MASCOT = listOf(
    ".#########.",
    ".#########.",
    "##o#####o##",
    "###########",
    ".#########.",
    ".#########.",
    ".#.#...#.#.",
    ".#.#...#.#."
)

private val MASCOT_BLINK = listOf(
    ".#########.",
    ".#########.",
    "###########",
    "###########",
    ".#########.",
    ".#########.",
    ".#.#...#.#.",
    ".#.#...#.#."
)

// W = white, L = light gray, D = dithered gray
private val MOON = listOf(
    "...WWWWWLLD..",
    ".WWWWWLL..DD.",
    "WWWLDD.......",
    "WWWD.........",
    "WWWD.........",
    "WWWD......LL.",
    ".WWLD.....L..",
    "..WWWLLWWLD..",
    "....LLWWLD..."
)

private val CLOUD_BIG = listOf(
    "........DDDDDD......",
    "..DDD...DDDDDDDD....",
    "..DDDDDDDDDDDDDDDD..",
    "DDDDDDDDDDDDDDDDDDDD",
    "DDDDDDDDDDDDDDDDDDDD"
)

private val CLOUD_SMALL = listOf(
    "....DDDD........",
    "..DDDDDDDD......",
    "DDDDDDDDDDDDDDDD",
    "DDDDDDDDDDDDDDDD"
)

private data class Star(val x: Float, val y: Float, val dim: Boolean, val phase: Float)

private val STARS = listOf(
    Star(4f, 1.5f, false, 0.0f),
    Star(29f, 2.5f, false, 0.3f),
    Star(23.5f, 7.5f, false, 0.6f),
    Star(1.2f, 10.5f, true, 0.2f),
    Star(46f, 15.5f, true, 0.8f),
    Star(34.5f, 18.5f, false, 0.4f),
    Star(19.5f, 21f, false, 0.7f),
    Star(62f, 4f, true, 0.5f),
    Star(66f, 19f, false, 0.1f)
)

@Composable
fun PixelScene(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val transition = rememberInfiniteTransition(label = "scene")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "blink"
    )
    val walk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "walk"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GRID_W / GRID_H)
    ) {
        val cell = size.width / GRID_W

        drawSprite(CLOUD_BIG, 2f, 4f, cell)
        drawSprite(CLOUD_SMALL, 27f, 12.5f, cell)
        drawSprite(MOON, 39f, 1f, cell * 1.1f)

        // Stars twinkle with individual phases.
        STARS.forEach { star ->
            val p = (twinkle + star.phase) % 1f
            val alpha = if (p < 0.5f) 0.55f + p * 0.9f else 1.45f - p * 0.9f
            val color = (if (star.dim) Term.TextDim else Term.Text).copy(alpha = alpha.coerceIn(0.3f, 1f))
            val layout = measurer.measure(
                text = "*",
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = (cell * 0.95f / density).sp, color = color)
            )
            drawText(layout, topLeft = Offset(star.x * cell, star.y * cell))
        }

        // Mascot (bobs one cell while "walking")
        val blinking = blink > 0.94f
        val bob = if (walk < 0.5f) 0f else -0.25f
        drawSprite(if (blinking) MASCOT_BLINK else MASCOT, 6f, 20f + bob, cell * 1.0f)

        // Ground dotted line under the mascot
        drawDotted(y = 28.2f * cell, width = size.width, cell = cell)
    }
}

private fun DrawScope.drawSprite(rows: List<String>, gx: Float, gy: Float, cell: Float) {
    val sub = cell / 3f
    rows.forEachIndexed { r, row ->
        row.forEachIndexed { c, ch ->
            val x = gx * (size.width / GRID_W) + c * cell
            val y = gy * (size.width / GRID_W) + r * cell
            when (ch) {
                '#' -> drawRect(Term.Coral, Offset(x, y), Size(cell + 0.5f, cell + 0.5f))
                'o' -> drawRect(Term.Background, Offset(x, y), Size(cell, cell))
                'W' -> drawRect(Term.MoonWhite, Offset(x, y), Size(cell + 0.5f, cell + 0.5f))
                'L' -> drawRect(Term.MoonGray, Offset(x, y), Size(cell + 0.5f, cell + 0.5f))
                'D' -> {
                    // 3x3 checkerboard dithering to mimic the terminal's shaded blocks.
                    for (i in 0 until 3) for (j in 0 until 3) {
                        if ((i + j) % 2 == 0) {
                            drawRect(Term.Cloud, Offset(x + i * sub, y + j * sub), Size(sub * 0.9f, sub * 0.9f))
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDotted(y: Float, width: Float, cell: Float) {
    drawLine(
        color = Term.TextFaint,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = cell * 0.18f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.18f, cell * 0.42f), 0f)
    )
}

/** The "‥‥‥‥" separator line used across the app. */
@Composable
fun DottedDivider(modifier: Modifier = Modifier, color: Color = Term.TextFaint) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx()), 0f)
        )
    }
}
