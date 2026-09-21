package com.derycode.srs.admin

// ─────────────────────────────────────────────────────────────────────────────
// Editorial design system (v2.2.26 console redesign)
// Ported from the design/redesign mockups: cream paper, deep-green sidebar,
// gold accents, teal progress. Pure drawing code — no new dependencies.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Donut progress ring like the mockup's "Reporting progress" card. */
@Composable
internal fun Donut(percent: Int, label: String, modifier: Modifier = Modifier) {
    val track = Color(0xFFE4E1D6)
    val sweep = (percent.coerceIn(0, 100) / 100f) * 360f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(92.dp)) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                drawArc(
                    color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke)
                )
                if (sweep > 0f) drawArc(
                    color = Theme.TEAL, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - stroke, this.size.height - stroke)
                )
            }
            Text("$percent%", color = Theme.INK, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Theme.MUTED, fontSize = 10.sp)
    }
}

/** Slim rounded progress bar used in lists and breakdowns. */
@Composable
internal fun MiniProgress(percent: Int, modifier: Modifier = Modifier, tint: Color = Theme.TEAL) {
    Box(
        modifier.height(5.dp).background(Color(0xFFE9E6DA), RoundedCornerShape(3.dp))
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .background(tint, RoundedCornerShape(3.dp))
        )
    }
}

/** Pill / tag with soft background (teal, gold, rust, muted). */
@Composable
internal fun Pill(text: String, tone: String = "teal") {
    val (bg, fg) = when (tone) {
        "gold" -> Theme.GOLD_SOFT to Color(0xFF7A5B1E)
        "rust" -> Theme.RUST_SOFT to Color(0xFF7C3A28)
        "muted" -> Color(0xFFECEAE0) to Theme.MUTED
        else -> Theme.TEAL_SOFT to Theme.TEAL
    }
    Text(
        text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        style = TextStyle(letterSpacing = 0.3.sp),
        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/** Term-average sparkline drawn from real mark data (0–100 values). */
@Composable
internal fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val line = Theme.TEAL
    val dot = Theme.GOLD
    Canvas(modifier.fillMaxWidth().height(108.dp)) {
        if (values.size < 2) return@Canvas
        val minV = 40f                                   // fixed floor keeps term-to-term changes readable
        val maxV = maxOf(100f, values.max())
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1)
        val y = { v: Float -> h - ((v.coerceIn(minV, maxV) - minV) / (maxV - minV)) * (h - 14f) - 2f }
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * stepX, y(v))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawLine(Color(0xFFE4E1D6), Offset(0f, h - 1f), Offset(w, h - 1f), 1.5f)
        drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v -> drawCircle(dot, 3.5.dp.toPx(), Offset(i * stepX, y(v))) }
    }
}
