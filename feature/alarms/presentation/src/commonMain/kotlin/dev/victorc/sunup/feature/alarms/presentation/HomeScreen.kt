package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.wakeup.DayMark
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home, the Alarm tab's start screen (the original mockup).
 *
 * @param onPreview plays an alarm's morning as practice; null hides the button (no ringing screen on this platform yet)
 */
@Composable
fun HomeRoot(
    onOpenAlarms: () -> Unit,
    onNewAlarm: () -> Unit,
    onOpenDeck: (deckId: String) -> Unit,
    onOpenDecks: () -> Unit,
    onPreview: ((alarmId: String) -> Unit)? = null,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openAlarms by rememberUpdatedState(onOpenAlarms)
    val newAlarm by rememberUpdatedState(onNewAlarm)
    val openDeck by rememberUpdatedState(onOpenDeck)
    val openDecks by rememberUpdatedState(onOpenDecks)
    val preview by rememberUpdatedState(onPreview)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(HomeAction.OnLeave) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenAlarms -> openAlarms()
                HomeEvent.NewAlarm -> newAlarm()
                is HomeEvent.OpenDeck -> openDeck(event.deckId)
                HomeEvent.OpenDecks -> openDecks()
                is HomeEvent.Preview -> preview?.invoke(event.alarmId)
            }
        }
    }
    HomeScreen(state, viewModel::onAction, canPreview = onPreview != null, permissions = rememberMorningPermissions())
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun HomeScreen(state: HomeState, onAction: (HomeAction) -> Unit, canPreview: Boolean, permissions: MorningPermissions) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                "sunup",
                style = SunupTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            StatChip(
                text = "${state.streak}",
                description = if (state.streak == 1) "1 sunrise in a row" else "${state.streak} sunrises in a row",
                face = colors.sunSoft,
                ink = colors.onSunSoft,
            ) {
                Pip(PipMood.HAPPY, size = SunupTheme.spacing.xxl)
            }
            StatChip(
                text = "${state.cardsAnswered}",
                description = "${state.cardsAnswered} cards answered",
                face = colors.daySky,
                ink = colors.indigo,
            ) {
                Text("🃏", style = SunupTheme.typography.body)
            }
        }

        state.message?.let { Message(it, onDismiss = { onAction(HomeAction.OnDismissMessage) }) }
        if (!state.loading) {
            val alarm = state.alarm
            if (alarm != null) {
                AlarmCard(alarm, onAction, Modifier.riseIn(0))
            } else {
                NoAlarmCard(onAdd = { onAction(HomeAction.OnAddAlarm) }, modifier = Modifier.riseIn(0))
            }

            val deck = state.deck
            if (deck != null) {
                DeckCard(deck, onOpen = { onAction(HomeAction.OnOpenDeck) }, modifier = Modifier.riseIn(1))
            } else {
                NoDeckCard(onOpen = { onAction(HomeAction.OnOpenDecks) }, modifier = Modifier.riseIn(1))
            }

            WeekCard(state, Modifier.riseIn(2))

            if (alarm != null && canPreview) {
                SunupButton(
                    text = when (alarm.day) {
                        "Today" -> "Preview today's wake-up"
                        "Tomorrow" -> "Preview tomorrow's wake-up"
                        else -> "Preview this wake-up"
                    },
                    onClick = { onAction(HomeAction.OnPreview) },
                    modifier = Modifier.riseIn(3),
                )
            }

            if (permissions.supported) MorningExtrasCard(state, permissions, onAction, Modifier.riseIn(4))
        }
    }
}

/** Two switches for "You're up!": the weather and the next event today. Asks the phone only when turned on. */
@Composable
private fun MorningExtrasCard(state: HomeState, permissions: MorningPermissions, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Text("On \"You're up!\"", style = SunupTheme.typography.title, color = colors.ink)
        ExtraRow(
            icon = "☀️",
            title = "Today's weather",
            detail = "Uses your approximate location. Weather data from MET Norway.",
            on = state.showWeather && permissions.locationAllowed,
            onChange = { on ->
                if (!on) {
                    onAction(HomeAction.OnSetWeather(false))
                } else {
                    permissions.askLocation { allowed ->
                        onAction(if (allowed) HomeAction.OnSetWeather(true) else HomeAction.OnPermissionDenied(forWeather = true))
                    }
                }
            },
        )
        ExtraRow(
            icon = "📅",
            title = "Next event today",
            detail = "From your phone's calendar. It stays on your phone.",
            on = state.showCalendar && permissions.calendarAllowed,
            onChange = { on ->
                if (!on) {
                    onAction(HomeAction.OnSetCalendar(false))
                } else {
                    permissions.askCalendar { allowed ->
                        onAction(if (allowed) HomeAction.OnSetCalendar(true) else HomeAction.OnPermissionDenied(forWeather = false))
                    }
                }
            },
        )
    }
}

@Composable
private fun ExtraRow(icon: String, title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = SunupTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
        Text(icon, style = SunupTheme.typography.title)
        Column(Modifier.weight(1f)) {
            Text(title, style = SunupTheme.typography.body, color = colors.ink)
            Text(detail, style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.sun,
                checkedThumbColor = colors.surface,
                checkedBorderColor = colors.sun,
                uncheckedTrackColor = colors.track,
                uncheckedThumbColor = colors.surface,
                uncheckedBorderColor = colors.border,
            ),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun StatChip(text: String, description: String, face: Color, ink: Color, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .background(face, SunupTheme.shapes.pill)
            .padding(horizontal = SunupTheme.spacing.md, vertical = SunupTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
    ) {
        icon()
        Text(text, style = SunupTheme.typography.title, color = ink)
    }
}

/** The big indigo card: when the next alarm rings. Tap it for all alarms. */
@Composable
private fun AlarmCard(alarm: HomeAlarmUi, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    val on = alarm.enabled
    SunupRaised(
        face = if (on) colors.indigo else colors.inkMuted,
        edge = colors.indigoShadow,
        onClick = { onAction(HomeAction.OnOpenAlarms) },
        onClickLabel = "See all alarms",
        contentPadding = PaddingValues(horizontal = SunupTheme.spacing.xl, vertical = SunupTheme.spacing.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alarm.day.uppercase(),
                    style = SunupTheme.typography.label,
                    color = colors.onNightMuted,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = on,
                    onCheckedChange = { onAction(HomeAction.OnToggleAlarm(it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.sun,
                        checkedThumbColor = colors.surface,
                        checkedBorderColor = colors.sun,
                        uncheckedTrackColor = colors.indigoShadow,
                        uncheckedThumbColor = colors.surface,
                        uncheckedBorderColor = colors.indigoShadow,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Alarm ${alarm.time}" },
                )
            }
            Text(alarm.time, style = SunupTheme.typography.clock, color = colors.onIndigo)
            Text(alarm.details, style = SunupTheme.typography.body, color = colors.onNightMuted)
        }
    }
}

@Composable
private fun NoAlarmCard(onAdd: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        contentPadding = PaddingValues(SunupTheme.spacing.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
                Pip(PipMood.SLEEPY, size = SunupTheme.sizes.pipBadge)
                Column {
                    Text("No alarm set", style = SunupTheme.typography.title, color = colors.ink)
                    Text("Set one, and Pip wakes you with a quick card.", style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
                }
            }
            SunupButton(text = "Add alarm", onClick = onAdd)
        }
    }
}

@Composable
private fun DeckCard(deck: DeckProgressUi, onOpen: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val what = if (deck.isMap) "countries" else "cards"
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        onClick = onOpen,
        onClickLabel = "Open deck",
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(
                modifier = Modifier
                    .size(SunupTheme.sizes.pipBadge)
                    .background(colors.daySky, SunupTheme.shapes.button),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (deck.isMap) "🌍" else "🃏", style = SunupTheme.typography.title)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(deck.name, style = SunupTheme.typography.title, color = colors.ink)
                ProgressBar(fraction = if (deck.total == 0) 0f else deck.learned.toFloat() / deck.total)
                Text(
                    "${deck.learned} of ${deck.total} $what learned",
                    style = SunupTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun NoDeckCard(onOpen: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        contentPadding = PaddingValues(SunupTheme.spacing.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
            Text("No deck yet", style = SunupTheme.typography.title, color = colors.ink)
            Text(
                "Quick math wakes you for now. Make a deck, add a map, or import one with your AI.",
                style = SunupTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
            SunupButton(text = "Go to Decks", onClick = onOpen, style = SunupButtonStyle.Secondary)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val colors = SunupTheme.colors
    val still = reduceMotion()
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (still) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "learned",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SunupTheme.spacing.md)
            .clip(SunupTheme.shapes.pill)
            .background(colors.track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(SunupTheme.shapes.pill)
                .background(colors.indigoSoft),
        )
    }
}

@Composable
private fun WeekCard(state: HomeState, modifier: Modifier) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = SunupTheme.typography.title, color = colors.ink, modifier = Modifier.weight(1f))
            Text(
                if (state.sunrisesThisWeek == 1) "1 sunrise" else "${state.sunrisesThisWeek} sunrises",
                style = SunupTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.week.forEach { day ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "${day.name}: ${day.mark.spoken()}" },
                ) {
                    DayDot(day.mark, size = spacing.xxl + spacing.md)
                    Text(day.letter, style = SunupTheme.typography.label, color = colors.inkMuted)
                }
            }
        }
    }
}

private fun DayMark.spoken(): String = when (this) {
    DayMark.SUNRISE -> "woke up"
    DayMark.MISSED -> "missed"
    DayMark.NO_ALARM -> "no alarm"
    DayMark.TODAY -> "today"
    DayMark.LATER -> "still to come"
}

/** A sun for a sunrise, a quiet dash for a missed morning, a dashed ring for no alarm, a glowing ring for today. */
@Composable
private fun DayDot(mark: DayMark, size: Dp) {
    val colors = SunupTheme.colors
    val stroke = SunupTheme.sizes.border
    val glow = if (mark == DayMark.TODAY && !reduceMotion()) {
        val transition = rememberInfiniteTransition(label = "today")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        ).value
    } else {
        0f
    }
    Canvas(Modifier.size(size)) {
        val radius = this.size.minDimension / 2
        val line = stroke.toPx()
        when (mark) {
            DayMark.SUNRISE -> {
                drawCircle(colors.sun, radius)
                val w = this.size.width
                val tick = Path().apply {
                    moveTo(w * 0.3f, w * 0.52f)
                    lineTo(w * 0.45f, w * 0.66f)
                    lineTo(w * 0.72f, w * 0.36f)
                }
                drawPath(tick, colors.onSun, style = Stroke(line * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            DayMark.MISSED -> {
                drawCircle(colors.track, radius)
                drawLine(colors.inkMuted, Offset(radius * 0.65f, radius), Offset(radius * 1.35f, radius), line, StrokeCap.Round)
            }
            DayMark.TODAY -> {
                drawCircle(colors.sun.copy(alpha = 0.35f * glow), radius)
                drawCircle(colors.surface, radius - line * 1.5f)
                drawCircle(colors.sunOutline, radius - line * 0.75f, style = Stroke(line * 1.5f))
            }
            DayMark.NO_ALARM, DayMark.LATER -> drawCircle(
                colors.border,
                radius - line / 2,
                style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(line * 2, line * 2))),
            )
        }
    }
}
