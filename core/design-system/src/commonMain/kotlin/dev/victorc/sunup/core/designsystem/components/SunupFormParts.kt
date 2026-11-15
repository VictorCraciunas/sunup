package dev.victorc.sunup.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/** Back arrow and screen title, the top of every inner screen. */
@Composable
fun SunupTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
    ) {
        SunupBackButton(onBack)
        Text(
            text = title,
            style = SunupTheme.typography.headline,
            color = SunupTheme.colors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

@Composable
fun SunupBackButton(onClick: () -> Unit, description: String = "Back") {
    val tint = SunupTheme.colors.inkMuted
    val stroke = SunupTheme.sizes.border
    Box(
        modifier = Modifier
            .size(SunupTheme.sizes.minTouchTarget)
            .clip(SunupTheme.shapes.button)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(SunupTheme.spacing.xl)) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.62f, w * 0.18f)
                lineTo(w * 0.3f, w * 0.5f)
                lineTo(w * 0.62f, w * 0.82f)
            }
            drawPath(path, tint, style = Stroke(width = stroke.toPx() * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A small uppercase label above a field or a group, e.g. "RIGHT ANSWER". */
@Composable
fun SunupFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SunupTheme.typography.label, color = SunupTheme.colors.inkMuted, modifier = modifier)
}

/**
 * A labelled text box in Sunup's look.
 *
 * @param accent outline colour, e.g. green for the right answer
 */
@Composable
fun SunupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    accent: Color? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = SunupTheme.typography.body,
) {
    val colors = SunupTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
        label?.let { SunupFieldLabel(it) }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            placeholder = placeholder?.let { { Text(it, color = colors.inkMuted) } },
            textStyle = textStyle.copy(color = colors.ink),
            shape = SunupTheme.shapes.button,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = accent ?: colors.indigoSoft,
                unfocusedBorderColor = accent ?: colors.border,
                cursorColor = colors.indigo,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
