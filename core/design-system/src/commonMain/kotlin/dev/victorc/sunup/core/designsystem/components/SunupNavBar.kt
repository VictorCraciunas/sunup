package dev.victorc.sunup.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/** The icons the bottom bar can show, drawn so no icon library is needed. */
enum class NavIcon { ALARM, DECKS }

data class NavBarItem(
    val label: String,
    val icon: NavIcon,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** The bottom bar from the mockups: icon over label, the current tab on a pale indigo pill. */
@Composable
fun SunupNavBar(items: List<NavBarItem>, modifier: Modifier = Modifier) {
    val colors = SunupTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(thickness = SunupTheme.sizes.border, color = colors.track)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SunupTheme.spacing.screen, vertical = SunupTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val tint = if (item.selected) colors.indigo else colors.inkMuted
                Column(
                    modifier = Modifier
                        .widthIn(min = SunupTheme.sizes.minTouchTarget * 1.5f)
                        .clip(SunupTheme.shapes.button)
                        .background(if (item.selected) colors.indigoContainer else Color.Transparent)
                        .clickable(role = Role.Tab, onClick = item.onClick)
                        .semantics { selected = item.selected }
                        .padding(vertical = SunupTheme.spacing.xs, horizontal = SunupTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NavIconImage(item.icon, tint)
                    Text(item.label, style = SunupTheme.typography.label, color = tint)
                }
            }
        }
    }
}

@Composable
private fun NavIconImage(icon: NavIcon, tint: Color) {
    val stroke = SunupTheme.sizes.border
    Canvas(Modifier.size(SunupTheme.spacing.xxl)) {
        val w = size.width
        val line = Stroke(width = stroke.toPx() * 1.2f, cap = StrokeCap.Round)
        when (icon) {
            NavIcon.ALARM -> {
                val center = Offset(w / 2, w * 0.55f)
                drawCircle(tint, radius = w * 0.3f, center = center, style = line)
                drawLine(tint, center, Offset(center.x, center.y - w * 0.14f), line.width, StrokeCap.Round)
                drawLine(tint, center, Offset(center.x + w * 0.1f, center.y + w * 0.08f), line.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.12f, w * 0.2f), Offset(w * 0.24f, w * 0.1f), line.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.88f, w * 0.2f), Offset(w * 0.76f, w * 0.1f), line.width, StrokeCap.Round)
            }
            NavIcon.DECKS -> {
                val corner = CornerRadius(w * 0.1f)
                drawRoundRect(tint, Offset(w * 0.14f, w * 0.3f), Size(w * 0.56f, w * 0.56f), corner, style = line)
                drawLine(tint, Offset(w * 0.32f, w * 0.14f), Offset(w * 0.76f, w * 0.14f), line.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.86f, w * 0.24f), Offset(w * 0.86f, w * 0.66f), line.width, StrokeCap.Round)
            }
        }
    }
}
