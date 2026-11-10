package com.radley.homefit.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Seven rounded bars; [highlight] (today) is drawn in full colour, the rest softened. */
@Composable
fun WeekBars(values: List<Float>, labels: List<String>, highlight: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(10f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / values.size
            val barWidth = slot * 0.46f
            values.forEachIndexed { i, v ->
                val left = slot * i + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2))
                val h = size.height * (v / max)
                if (h > 0f) {
                    drawRoundRect(
                        if (i == highlight) color else color.copy(alpha = 0.55f),
                        Offset(left, size.height - h),
                        Size(barWidth, h.coerceAtLeast(barWidth)),
                        CornerRadius(barWidth / 2),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlight) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Weight over time: a line through the points with a soft fill and an optional goal line. */
@Composable
fun LineChart(points: List<Pair<Float, Float>>, goal: Float?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    val goalColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val hole = MaterialTheme.colorScheme.surfaceContainer
    if (points.isEmpty()) return
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val xs = points.map { it.first }
        val ys = points.map { it.second } + listOfNotNull(goal)
        val minX = xs.min()
        val maxX = xs.max().let { if (it == minX) minX + 1 else it }
        val pad = ((ys.max() - ys.min()) * 0.15f).coerceAtLeast(1f)
        val minY = ys.min() - pad
        val maxY = ys.max() + pad
        fun x(v: Float) = (v - minX) / (maxX - minX) * size.width
        fun y(v: Float) = size.height - (v - minY) / (maxY - minY) * size.height

        for (i in 0..3) {
            val gy = size.height * i / 3
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        goal?.let {
            drawLine(goalColor, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        val line = Path()
        points.forEachIndexed { i, (px, py) -> if (i == 0) line.moveTo(x(px), y(py)) else line.lineTo(x(px), y(py)) }
        if (points.size > 1) {
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.last().first), size.height)
                lineTo(x(points.first().first), size.height)
                close()
            }
            drawPath(fill, color.copy(alpha = 0.12f))
            drawPath(line, color, style = Stroke(width = 5f))
        }
        points.forEach { (px, py) ->
            drawCircle(color, radius = 7f, center = Offset(x(px), y(py)))
            drawCircle(hole, radius = 3.5f, center = Offset(x(px), y(py)))
        }
    }
}

/** The four WHO bands as a segmented bar with a marker at [bmi]. */
@Composable
fun BmiScale(bmi: Double, modifier: Modifier = Modifier) {
    val bands = listOf(
        15.0 to Color(0xFF38BDF8),
        18.5 to Color(0xFF22C55E),
        25.0 to Color(0xFFF59E0B),
        30.0 to Color(0xFFEF4444),
    )
    val lo = 15.0
    val hi = 40.0
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val barTop = size.height * 0.45f
        val barHeight = size.height * 0.4f
        fun x(v: Double) = ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * size.width
        bands.forEachIndexed { i, (start, c) ->
            val end = bands.getOrNull(i + 1)?.first ?: hi
            drawRect(c, Offset(x(start), barTop), Size(x(end) - x(start), barHeight))
        }
        val mx = x(bmi)
        val tri = Path().apply {
            moveTo(mx - 10f, 0f)
            lineTo(mx + 10f, 0f)
            lineTo(mx, barTop - 2f)
            close()
        }
        drawPath(tri, marker)
    }
}
