// Small shared Compose building blocks: chips, section labels, a flow layout for
// wrapping chip rows, and the custom pill toggle. Matches the iOS components.

package com.example.smartwords.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import kotlin.math.max

@Composable
fun Chip(text: String, dim: Boolean = false) {
    val theme = LocalTheme.current
    Text(
        text = text,
        fontFamily = Fonts.sans,
        fontSize = 12.5.sp,
        color = theme.palette.fg.copy(alpha = if (dim) 0.7f else 1f),
        modifier = Modifier
            .border(1.dp, theme.palette.line2, RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val theme = LocalTheme.current
    Text(
        text = text,
        fontFamily = Fonts.mono,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = theme.palette.muted,
        modifier = modifier,
    )
}

/** A simple wrapping row layout (analog of the iOS FlowLayout). */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        val positions = ArrayList<Pair<Int, Int>>(placeables.size)
        placeables.forEach { p ->
            if (x > 0 && x + p.width > maxWidth) {
                x = 0
                y += rowHeight + vGap
                rowHeight = 0
            }
            positions.add(x to y)
            x += p.width + hGap
            widest = max(widest, x - hGap)
            rowHeight = max(rowHeight, p.height)
        }
        val totalHeight = y + rowHeight
        val width = if (constraints.hasBoundedWidth) maxWidth else widest

        layout(width, totalHeight) {
            placeables.forEachIndexed { i, p ->
                val (px, py) = positions[i]
                p.placeRelative(px, py)
            }
        }
    }
}

/** Custom pill toggle (matches the iOS ToggleSwitch). */
@Composable
fun ToggleSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalTheme.current
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) theme.accent else theme.palette.line2)
            .clickable { onChange(!on) },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
