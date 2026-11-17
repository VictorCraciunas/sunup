package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.components.SunupDayChip
import dev.victorc.sunup.core.designsystem.components.SunupFieldLabel
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupStepper
import dev.victorc.sunup.core.designsystem.motion.bob
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.domain.wakeup.WakeMode

// TODO(M4): move user-facing text to string resources once localization is set up.

/** How far along each screen is, for the progress bar. */
private const val STEPS = 5f

/** Mockup 1. */
@Composable
internal fun WelcomeScreen(onStart: () -> Unit) {
    val colors = SunupTheme.colors
    OnboardingFrame(
        progress = null,
        onBack = {},
        centered = true,
        bottom = { SunupButton(text = "Get started", onClick = onStart, modifier = Modifier.riseIn(3)) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.lg),
        ) {
            Bubble("Good morning!", Modifier.riseIn(0))
            Pip(mood = PipMood.AWAKE, size = SunupTheme.sizes.pipHero, modifier = Modifier.bob(), contentDescription = "Pip, a smiling sun")
            Text(
                "sunup",
                style = SunupTheme.typography.display,
                color = colors.ink,
                modifier = Modifier.riseIn(1).semantics { heading() },
            )
            Text(
                "Wake up for you. Learn a little something every single morning.",
                style = SunupTheme.typography.title,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.riseIn(2),
            )
        }
    }
}

/** Mockup 2, with the choices the app has today. */
@Composable
internal fun GoalScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = SunupTheme.colors
    OnboardingFrame(
        progress = 1 / STEPS,
        onBack = onBack,
        bottom = { SunupButton(text = "Continue", onClick = onContinue, enabled = state.goal != null) },
    ) {
        PipSays("What should we wake up with?")
        LearnGoal.entries.forEachIndexed { index, goal ->
            ChoiceCard(
                title = goal.title(),
                subtitle = goal.subtitle(),
                selected = state.goal == goal,
                onClick = { onAction(OnboardingAction.OnPickGoal(goal)) },
                modifier = Modifier.riseIn(index + 1),
                icon = {
                    when (goal) {
                        LearnGoal.GEOGRAPHY -> EmojiBadge("🌍", colors.daySky)
                        LearnGoal.OWN_CARDS -> EmojiBadge("🃏", colors.indigoContainer)
                        LearnGoal.MATH -> EmojiBadge("🧮", colors.correctContainer)
                    }
                },
            )
        }
        when (state.goal) {
            LearnGoal.GEOGRAPHY -> Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
                SunupFieldLabel("Start with")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
                ) {
                    GeoRegion.entries.forEach { region ->
                        RegionChip(region.title, on = region == state.region) { onAction(OnboardingAction.OnPickRegion(region)) }
                    }
                }
                Hint("You can add more maps later in Decks → Browse packs.")
            }
            LearnGoal.OWN_CARDS -> Hint("After setup the Decks tab opens. Make cards yourself, or let ChatGPT, Claude or any AI make a whole deck for you.")
            LearnGoal.MATH -> Hint("No deck needed. You can add your own cards any time in the Decks tab.")
            null -> Unit
        }
    }
}

/** Mockup 3, without "cards each morning" (the wake-up mode decides that). */
@Composable
internal fun TimeScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = SunupTheme.colors
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    OnboardingFrame(
        progress = 2 / STEPS,
        onBack = onBack,
        bottom = { SunupButton(text = "Continue", onClick = onContinue) },
    ) {
        PipSays("When should I wake you?")
        SunupRaised(
            face = colors.surface,
            edge = colors.border,
            outline = colors.border,
            contentPadding = PaddingValues(SunupTheme.spacing.sm),
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .riseIn(1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
                SunupStepper(
                    value = state.hour.toString().padStart(2, '0'),
                    upLabel = "Later hour",
                    downLabel = "Earlier hour",
                    onUp = { onAction(OnboardingAction.OnHourStep(1)) },
                    onDown = { onAction(OnboardingAction.OnHourStep(-1)) },
                    onTapValue = { pickingTime = true },
                )
                Text(":", style = SunupTheme.typography.clock, color = colors.sunOutline)
                SunupStepper(
                    value = state.minute.toString().padStart(2, '0'),
                    upLabel = "Five minutes later",
                    downLabel = "Five minutes earlier",
                    onUp = { onAction(OnboardingAction.OnMinuteStep(5)) },
                    onDown = { onAction(OnboardingAction.OnMinuteStep(-5)) },
                    onTapValue = { pickingTime = true },
                )
            }
        }
        Column(Modifier.riseIn(2), verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            SunupFieldLabel("Repeat on")
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                WEEK.forEach { day ->
                    SunupDayChip(day = day.letter(), name = day.fullName(), on = day in state.days) {
                        onAction(OnboardingAction.OnToggleDay(day))
                    }
                }
            }
            Hint(daysText(state.days))
        }
        Hint("You can add more alarms, and change this one, on the Alarms screen.")
    }

    if (pickingTime) {
        TimeDialog(
            hour = state.hour,
            minute = state.minute,
            onPick = { hour, minute ->
                pickingTime = false
                onAction(OnboardingAction.OnTimePicked(hour, minute))
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** Mockup 4, with promises the app keeps today. */
@Composable
internal fun PromiseScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = SunupTheme.colors
    OnboardingFrame(
        progress = 3 / STEPS,
        onBack = onBack,
        bottom = {
            if (state.promised) {
                Pill(
                    "✓  Promise made. See you at sunrise!",
                    face = colors.correctContainer,
                    ink = colors.onCorrectContainer,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .riseIn(0)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                SunupButton(text = "Let's go", onClick = onContinue, modifier = Modifier.riseIn(1))
            } else {
                NightButton(text = "I'm doing this for me", onClick = { onAction(OnboardingAction.OnPromise) })
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
        ) {
            Bubble("Mornings are for you, not for the snooze button.")
            Pip(
                mood = if (state.promised) PipMood.HAPPY else PipMood.AWAKE,
                size = SunupTheme.sizes.pipHero,
                modifier = Modifier
                    .bob()
                    .wobbleWhenOn(state.promised),
            )
            Text(
                "This is for you",
                style = SunupTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        TickLine("No ads while you wake up. Ever.", Modifier.riseIn(1))
        TickLine("Answer one card and the alarm stops.", Modifier.riseIn(2))
        TickLine("A gentle check makes sure you stay up.", Modifier.riseIn(3))
    }
}

/** Mockup C1. */
@Composable
internal fun PermissionsScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val phone = rememberPhonePermissions()
    val ready = phone.needed.count { it in phone.granted }

    OnboardingFrame(
        progress = 4 / STEPS,
        onBack = onBack,
        bottom = {
            SunupButton(
                text = when (state.testAlarm) {
                    TestAlarmState.IDLE -> "Try a test alarm"
                    TestAlarmState.SET -> "Test alarm rings in 10 s"
                    TestAlarmState.NEEDS_EXACT_ALARMS -> "Allow Exact alarms first"
                    TestAlarmState.FAILED -> "Couldn't set it. Try again"
                },
                onClick = { onAction(OnboardingAction.OnTestAlarm) },
                style = SunupButtonStyle.Secondary,
                enabled = state.testAlarm != TestAlarmState.SET,
            )
            SunupButton(text = "Continue", onClick = onContinue)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Pip(mood = PipMood.AWAKE, size = SunupTheme.sizes.pipHero / 1.8f)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    "Let's make sure I can wake you",
                    style = SunupTheme.typography.headline,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = when {
                        phone.needed.isEmpty() -> "Nothing to set up on this phone."
                        ready == phone.needed.size -> "All set. I won't miss a morning."
                        else -> "$ready of ${phone.needed.size} ready"
                    },
                    style = SunupTheme.typography.body,
                    color = colors.inkMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        phone.needed.forEachIndexed { index, permission ->
            PermissionRow(
                permission = permission,
                subtitle = permission.subtitle(state, phone.autostartHint),
                done = permission in phone.granted,
                onAllow = { phone.request(permission) },
                modifier = Modifier.riseIn(index + 1),
            )
        }

        Text(
            "Some phones put apps to sleep to save battery. These keep your alarm on time, even after a restart or in silent mode.",
            style = SunupTheme.typography.bodySmall,
            color = colors.indigo,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.indigoContainer, SunupTheme.shapes.card)
                .padding(spacing.md),
        )
        Hint("The test alarm rings quietly in 10 seconds. Answer one card to stop it.")
    }
}

/** Mockup C2. Finishing saves the alarm. */
@Composable
internal fun ModeScreen(state: OnboardingState, onAction: (OnboardingAction) -> Unit, onBack: () -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val time = "${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}"

    OnboardingFrame(
        progress = 5 / STEPS,
        onBack = onBack,
        bottom = {
            state.problem?.let { Problem(it) }
            SunupButton(
                text = if (state.finishing) "Setting your alarm…" else "Set my $time alarm",
                onClick = { onAction(OnboardingAction.OnFinish) },
                enabled = !state.finishing,
            )
        },
    ) {
        PipSays("How hard is it to get you up?")
        WakeMode.entries.forEachIndexed { index, mode ->
            ChoiceCard(
                title = mode.title(),
                subtitle = mode.hint(),
                selected = state.mode == mode,
                onClick = { onAction(OnboardingAction.OnPickMode(mode)) },
                badge = if (mode == WakeMode.NORMAL) "Recommended" else null,
                modifier = Modifier.riseIn(index + 1),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SunupFieldLabel("Your morning")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, SunupTheme.shapes.card)
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                state.mode.steps().forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        Pill("${index + 1}", face = colors.indigo, ink = colors.onIndigo)
                        Text(step, style = SunupTheme.typography.body, color = colors.ink)
                    }
                }
            }
            Hint("You can change this for each alarm.")
        }
    }
}

@Composable
private fun PermissionRow(permission: PhonePermission, subtitle: String, done: Boolean, onAllow: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            when (permission) {
                PhonePermission.NOTIFICATIONS -> EmojiBadge("🔔", colors.sunSoft)
                PhonePermission.EXACT_ALARMS -> EmojiBadge("⏰", colors.indigoContainer)
                PhonePermission.LOCK_SCREEN -> EmojiBadge("🔒", colors.daySky)
                PhonePermission.BATTERY -> EmojiBadge("🔋", colors.correctContainer)
                PhonePermission.AUTOSTART -> EmojiBadge("⚡", colors.wrongContainer)
                PhonePermission.ALARMS -> EmojiBadge("⏰", colors.sunSoft)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(permission.title(), style = SunupTheme.typography.title, color = colors.ink)
                Text(subtitle, style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
            }
            if (done) {
                Pill("✓ Done", face = colors.correctContainer, ink = colors.onCorrectContainer, modifier = Modifier.wobbleWhenOn(true))
            } else {
                SmallButton(
                    text = if (permission == PhonePermission.AUTOSTART) "Show me" else "Allow",
                    onClick = onAllow,
                )
            }
        }
    }
}

private fun PhonePermission.title(): String = when (this) {
    PhonePermission.NOTIFICATIONS -> "Notifications"
    PhonePermission.EXACT_ALARMS -> "Exact alarms"
    PhonePermission.LOCK_SCREEN -> "Show over lock screen"
    PhonePermission.BATTERY -> "Battery: no limits"
    PhonePermission.AUTOSTART -> "Autostart"
    PhonePermission.ALARMS -> "Alarms"
}

private fun PhonePermission.subtitle(state: OnboardingState, autostartHint: String): String = when (this) {
    PhonePermission.NOTIFICATIONS -> "So the alarm can appear on screen"
    PhonePermission.EXACT_ALARMS -> {
        val late = (state.hour * 60 + state.minute + 4).mod(24 * 60)
        fun hhmm(minutes: Int) = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
        "Rings at ${hhmm(state.hour * 60 + state.minute)}, not ${hhmm(late)}"
    }
    PhonePermission.LOCK_SCREEN -> "So your card opens the moment it rings"
    PhonePermission.BATTERY -> "Stops the phone from putting Sunup to sleep"
    PhonePermission.AUTOSTART -> autostartHint
    PhonePermission.ALARMS -> "So Sunup rings like the Clock app, even in silent mode"
}

@Composable
private fun RegionChip(title: String, on: Boolean, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (on) colors.indigo else colors.surface,
        edge = if (on) colors.indigoShadow else colors.border,
        outline = if (on) null else colors.border,
        shape = SunupTheme.shapes.pill,
        onClick = onClick,
        role = Role.RadioButton,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(horizontal = SunupTheme.spacing.lg),
        modifier = Modifier
            .heightIn(min = SunupTheme.sizes.minTouchTarget)
            .wobbleWhenOn(on)
            .semantics { selected = on },
    ) {
        Text(title, style = SunupTheme.typography.body, color = if (on) colors.onIndigo else colors.ink)
    }
}

/** The indigo "night" button from the promise mockup. */
@Composable
private fun NightButton(text: String, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.indigo,
        edge = colors.indigoShadow,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SunupTheme.sizes.buttonHeight),
    ) {
        Text(text.uppercase(), style = SunupTheme.typography.button, color = colors.onIndigo)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.indigo,
        edge = colors.indigoShadow,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(horizontal = SunupTheme.spacing.md),
        modifier = Modifier.heightIn(min = SunupTheme.sizes.minTouchTarget),
    ) {
        Text(text, style = SunupTheme.typography.label, color = colors.onIndigo)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = SunupTheme.typography.bodySmall, color = SunupTheme.colors.inkMuted)
}

@Composable
private fun Problem(text: String) {
    val colors = SunupTheme.colors
    Text(
        text,
        style = SunupTheme.typography.bodySmall,
        color = colors.onWrongContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.wrongContainer, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(picker.hour, picker.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = picker) },
    )
}
