package dev.victorc.sunup.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Rows a wheel shows: the picked one in the middle, two above and two below. */
private const val VISIBLE_ROWS = 5

/** A wheel holds its numbers this many times over, so it turns round and round (23 → 00) without an end. */
private const val LOOPS = 1000

/**
 * Mockup G1: a panel from the bottom with an hour wheel and a minute wheel, for picking any
 * time (the arrows next to the time move minutes by 5). [onDone] gets the picked time;
 * Cancel, a tap outside, or swiping the panel down calls [onDismiss] and changes nothing.
 */
@Composable
fun SunupTimeSheet(
    hour: Int,
    minute: Int,
    onDone: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SunupTheme.colors
    var pickedHour by rememberSaveable { mutableIntStateOf(hour) }
    var pickedMinute by rememberSaveable { mutableIntStateOf(minute) }

    SunupSheet(title = "Set the time", onDismiss = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xl),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Wheel(count = 24, value = pickedHour, unit = "hours", onChange = { pickedHour = it })
            Text(":", style = SunupTheme.typography.display, color = colors.sunOutline)
            Wheel(count = 60, value = pickedMinute, unit = "minutes", onChange = { pickedMinute = it })
        }
        SunupButton(text = "Done", onClick = { close { onDone(pickedHour, pickedMinute) } })
        SunupTextButton(text = "Cancel", onClick = { close(onDismiss) })
    }
}

/**
 * One wheel: swipe it, or tap a number to move it to the middle. The number in the yellow band
 * is the one picked; [onChange] is called every time a new one lands there.
 */
@Composable
private fun Wheel(count: Int, value: Int, unit: String, onChange: (Int) -> Unit) {
    val colors = SunupTheme.colors
    val row = SunupTheme.sizes.wheelRow
    val rowPx = with(LocalDensity.current) { row.roundToPx() }
    val haptics = LocalHapticFeedback.current
    val still = reduceMotion()
    val scope = rememberCoroutineScope()
    val change by rememberUpdatedState(onChange)

    // Starts in the middle of the long list, a whole number of loops in, so index % count is the value.
    val middle = count * (LOOPS / 2)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middle + value - VISIBLE_ROWS / 2)
    val centred by remember(state, rowPx) {
        derivedStateOf {
            val past = if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            state.firstVisibleItemIndex + VISIBLE_ROWS / 2 + past
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centred.mod(count) }
            .distinctUntilChanged()
            .drop(1)
            .collect { picked ->
                change(picked)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
    }

    Box(contentAlignment = Alignment.Center) {
        // The band behind the middle row.
        Box(
            Modifier
                .width(SunupTheme.sizes.wheelWidth + SunupTheme.spacing.xl)
                .height(row)
                .background(colors.sunSoft, SunupTheme.shapes.button)
                .border(SunupTheme.sizes.border, colors.sun, SunupTheme.shapes.button),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(SunupTheme.sizes.wheelWidth)
                .height(row * VISIBLE_ROWS),
        ) {
            items(count * LOOPS) { index ->
                val number = index.mod(count)
                val text = number.toString().padStart(2, '0')
                val distance = abs(index - centred)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(row)
                        .clickable(onClickLabel = "Pick $text") {
                            scope.launch {
                                val target = index - VISIBLE_ROWS / 2
                                if (still) state.scrollToItem(target) else state.animateScrollToItem(target)
                            }
                        }
                        .semantics {
                            contentDescription = "$text $unit"
                            selected = distance == 0
                        },
                ) {
                    Text(
                        text,
                        style = when (distance) {
                            0 -> SunupTheme.typography.display
                            1 -> SunupTheme.typography.headline
                            else -> SunupTheme.typography.title
                        },
                        color = if (distance == 0) colors.ink else colors.inkMuted,
                    )
                }
            }
        }
    }
}
