package jp.ikegami.roastscale.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** One shared visual vocabulary for measurement, history and settings. */
object RoastNeon {
    val night = Color(0xFF060918)
    val surface = Color(0xFF10172B)
    val cyan = Color(0xFF48E9FF)
    val lime = Color(0xFFBBFF66)
    val violet = Color(0xFFB594FF)
    val pink = Color(0xFFFF67C9)
    val gold = Color(0xFFFFD36D)
    val muted = Color(0xFFADBBD2)
}

/** Gentle moving aurora, HUD grid and stars. Heavy celebration FX stay BLE-only. */
@Composable
fun NeonBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ambientAurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing)
        ),
        label = "auroraDrift"
    )
    Canvas(modifier = modifier.background(
        Brush.verticalGradient(
            listOf(Color(0xFF101634), RoastNeon.night, Color(0xFF090B20))
        )
    )) {
        val short = min(size.width, size.height)
        val orbOne = Offset(
            size.width * (0.78f + drift * 0.08f),
            size.height * (0.14f + drift * 0.10f)
        )
        val orbTwo = Offset(
            size.width * (0.10f + drift * 0.14f),
            size.height * (0.77f - drift * 0.13f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(RoastNeon.violet.copy(alpha = 0.22f), Color.Transparent),
                center = orbOne,
                radius = short * 0.92f
            ),
            radius = short * 0.92f, center = orbOne
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(RoastNeon.cyan.copy(alpha = 0.16f), Color.Transparent),
                center = orbTwo,
                radius = short * 0.81f
            ),
            radius = short * 0.81f, center = orbTwo
        )

        val gridStep = 46.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                RoastNeon.cyan.copy(alpha = 0.045f),
                Offset(x, 0f), Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            x += gridStep
        }
        var y = 0f
        while (y < size.height) {
            drawLine(
                RoastNeon.violet.copy(alpha = 0.05f),
                Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            y += gridStep
        }
        repeat(26) { i ->
            val px = (((i * 47) % 101) / 101f) * size.width
            val py = (((i * 71 + 23) % 103) / 103f) * size.height
            drawCircle(
                if (i % 3 == 0) RoastNeon.pink.copy(alpha = 0.4f)
                else RoastNeon.cyan.copy(alpha = 0.26f),
                radius = (if (i % 4 == 0) 1.7.dp else 0.8.dp).toPx(),
                center = Offset(px, py)
            )
        }
    }
}

/** Glass-like neon card with a bright highlight and contrast-safe text area. */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color = RoastNeon.cyan,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.95f),
                        RoastNeon.violet.copy(alpha = 0.64f),
                        RoastNeon.pink.copy(alpha = 0.25f)
                    )
                ),
                shape = shape
            )
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xED1C2745), Color(0xF40F172C))
                ),
                shape = shape
            )
            .padding(16.dp)
    ) {
        Box(
            Modifier.width(44.dp).height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent, RoastNeon.violet, Color.Transparent)
                    ),
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun NeonSectionHeading(
    index: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(top = 18.dp, bottom = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "● $index",
                color = RoastNeon.cyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "R / SCALE  //  SYSTEM",
                color = RoastNeon.violet,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            color = RoastNeon.muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun NeonDivider(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        RoastNeon.cyan.copy(alpha = 0.75f),
                        RoastNeon.violet.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
    )
}
