package dev.victorc.sunup.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import kotlinx.coroutines.launch

/** What a [SunupSheet]'s content can do besides drawing itself. */
class SunupSheetScope internal constructor(
    columnScope: ColumnScope,
    private val hide: (then: () -> Unit) -> Unit,
) : ColumnScope by columnScope {
    /** Slides the panel away, then runs [then] (e.g. save, or tell the screen it's closed). */
    fun close(then: () -> Unit) = hide(then)
}

/**
 * Sunup's panel from the bottom of the screen (mockups G1, H1): white, rounded top, a small
 * handle, a title, then [content] in a column. A tap outside, swiping it down or Back calls
 * [onDismiss]. Content that saves or cancels should go through [SunupSheetScope.close], so the
 * panel slides away before the screen removes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SunupSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable SunupSheetScope.() -> Unit,
) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SunupTheme.shapes.sheet,
        containerColor = colors.surface,
        contentColor = colors.ink,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = spacing.sm)
                    .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                    .background(colors.border, SunupTheme.shapes.pill),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.screen, end = spacing.screen, top = spacing.md, bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(title, style = SunupTheme.typography.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            val sheetScope = SunupSheetScope(this) { then ->
                scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
            }
            sheetScope.content()
        }
    }
}

/**
 * A quiet full-width button with no box around it, e.g. Cancel or Keep it under a big button.
 * [color] null: indigo; pass `wrong` for one that starts deleting (Delete alarm).
 */
@Composable
fun SunupTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SunupTheme.sizes.minTouchTarget)
            .clip(SunupTheme.shapes.button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = SunupTheme.typography.button, color = color ?: SunupTheme.colors.indigo)
    }
}

private val HANDLE_WIDTH = 44.dp
private val HANDLE_HEIGHT = 5.dp
