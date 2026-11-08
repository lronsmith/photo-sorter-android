package io.github.lronsmith.photosorter.ui.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lronsmith.photosorter.ui.theme.PsColors

/**
 * Soft vertical glows on the screen edges: red "TRASH" on the left, green "KEEP" on the right.
 * Each rests at alpha 0.25 and brightens linearly to 1.0 as [progress] reaches the threshold
 * toward its side. Purely visual; it does not take touches.
 */
@Composable
fun EdgeGlows(progress: SwipeProgress, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Glow(
            color = PsColors.Trash,
            label = "TRASH",
            alpha = edgeGlowAlpha(-progress.x),
            fromLeft = true,
        )
        Glow(
            color = PsColors.Keep,
            label = "KEEP",
            alpha = edgeGlowAlpha(progress.x),
            fromLeft = false,
        )
    }
}

@Composable
private fun BoxScope.Glow(color: Color, label: String, alpha: Float, fromLeft: Boolean) {
    val clear = color.copy(alpha = 0f)
    val colors = if (fromLeft) listOf(color, clear) else listOf(clear, color)
    Box(
        Modifier
            .align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd)
            .width(46.dp)
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha }
            .background(Brush.horizontalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            maxLines = 1,
            // Reads bottom-to-top on the left edge and top-to-bottom on the right, as in the mockup.
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .rotate(if (fromLeft) -90f else 90f),
        )
    }
}
