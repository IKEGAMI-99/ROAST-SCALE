package jp.ikegami.roastscale.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val NeonLime = Color(0xFFB8FF6A)
private val NeonCyan = Color(0xFF4DE8FF)
private val NeonPurple = Color(0xFFC6A8FF)
private val NeonPink = Color(0xFFFF63C6)
private val NeonGold = Color(0xFFFFD36B)
private val NeonColors = listOf(NeonLime, NeonCyan, NeonPurple, NeonPink, NeonGold)

/**
 * Actual weight is the sole source of truth. The rolling digits are visual only:
 * never emit a fabricated value to the ViewModel, database or metrics.
 * Reveal right to left: tenths -> ones -> tens -> hundreds (when needed).
 */
@Composable
fun SlotWeightDisplay(weightKg: Double?, modifier: Modifier = Modifier) {
    val validWeight = weightKg?.takeIf { it.isFinite() && it > 0.0 && it < 1000.0 }
    val digitString = remember(validWeight) {
        if (validWeight == null) null else {
            val tenths = (validWeight * 10).roundToInt()
            String.format(Locale.US, "%d%d", tenths / 10, tenths % 10)
        }
    }
    val digitCount = digitString?.length ?: 3
    var revealed by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "slotRoll")
    val rollingStep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 19f,
        animationSpec = infiniteRepeatable(tween(1550, easing = LinearEasing)),
        label = "slotFrames"
    )

    // Restart the reveal only when real readings first arrive, not on every BLE packet.
    LaunchedEffect(digitCount, validWeight != null) {
        revealed = 0
        if (validWeight != null) {
            repeat(digitCount) {
                delay(310L)
                revealed = it + 1
            }
        }
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        repeat(digitCount) { index ->
            if (index == digitCount - 1) {
                Text(".", color = Color.White, fontSize = 38.sp,
                    fontWeight = FontWeight.Black)
            }
            val rightIndex = digitCount - 1 - index
            val locked = digitString != null && revealed > rightIndex
            val digit = if (locked) digitString[index] else ('0' + (rollingStep.toInt() + index * 4) % 10)
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    (slideInVertically(tween(100)) { it } + fadeIn(tween(100))) togetherWith
                        (slideOutVertically(tween(100)) { -it } + fadeOut(tween(100)))
                },
                label = "digitReel$index",
                modifier = Modifier.width(47.dp).height(67.dp),
                contentAlignment = Alignment.Center
            ) { shown ->
                Box(
                    modifier = Modifier.width(44.dp).height(63.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (locked) NeonLime.copy(alpha = 0.26f)
                                    else NeonPurple.copy(alpha = 0.24f),
                                    Color(0xFF101C2B),
                                    if (locked) NeonCyan.copy(alpha = 0.15f)
                                    else NeonPurple.copy(alpha = 0.12f)
                                )
                            ), RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = if (locked) 2.dp else 1.dp,
                            color = if (locked) NeonLime else NeonPurple.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(shown.toString(), fontSize = 43.sp, lineHeight = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = if (locked) Color.White else NeonCyan)
                }
            }
        }
        Text(" kg", color = NeonLime, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Full-screen, pointer-transparent celebration layer, only composed while BLE
 * packets are actively arriving. Frame work stops as soon as reception ends.
 */
@Composable
fun MeasurementCelebrationOverlay(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "measurementFX")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "particlePhase"
    )
    Canvas(modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.40f)
        val scale = size.minDimension

        // Expanding neon shockwaves radiate outward from the weight readout.
        repeat(4) { index ->
            val p = (phase + index * 0.25f) % 1f
            drawCircle(
                color = NeonColors[index].copy(alpha = (1f - p) * 0.38f),
                radius = scale * (0.19f + p * 0.72f),
                center = center,
                style = Stroke(width = (3.8f - p * 2.5f).dp.toPx())
            )
        }

        // Rotating radial light streaks.
        repeat(22) { index ->
            val angle = (index / 22.0 + phase * 0.5) * 2.0 * PI
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val inner = scale * 0.21f
            val outer = inner + scale * (0.09f + (index % 4) * 0.025f)
            drawLine(
                color = NeonColors[index % NeonColors.size].copy(alpha = 0.36f),
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = (1.4f + (index % 3) * 0.6f).dp.toPx()
            )
        }

        // Deterministic shower of sparkles across the entire screen.
        repeat(100) { index ->
            val speed = 0.55f + (index % 6) * 0.10f
            val progress = (index * 0.618034f + phase * speed) % 1f
            val sway = sin((phase * 2.0 * PI + index).toFloat()) * 0.045f
            val normalizedX = (index * 0.7548776f + sway).let { it - kotlin.math.floor(it) }
            val x = normalizedX * size.width
            val y = progress * size.height
            val twinkle = 0.4f + 0.6f * kotlin.math.abs(
                sin((phase * 4.0 * PI + index).toFloat())
            )
            drawCircle(
                color = NeonColors[index % NeonColors.size].copy(alpha = twinkle * 0.82f),
                radius = (1.3f + index % 4 * 0.85f).dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
