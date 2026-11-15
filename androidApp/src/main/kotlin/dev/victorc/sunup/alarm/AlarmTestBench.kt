package dev.victorc.sunup.alarm

import android.Manifest
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.alarm.Alarm
import dev.victorc.sunup.core.alarm.AlarmPermissions
import dev.victorc.sunup.core.alarm.AlarmRingingService
import dev.victorc.sunup.core.alarm.AlarmScheduler
import dev.victorc.sunup.core.alarm.AlarmStore
import dev.victorc.sunup.core.alarm.RingEvent
import dev.victorc.sunup.core.alarm.RingingState
import dev.victorc.sunup.core.data.GeoDeckSeeder
import dev.victorc.sunup.core.data.SampleDeckSeeder
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.geo.GeoAtlasSource
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.Result
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/**
 * A temporary screen for testing the alarm engine on real phones: check permissions,
 * set test alarms, and read the ring log to see how late each alarm rang.
 */
@Composable
fun AlarmTestBench(
    scheduler: AlarmScheduler,
    store: AlarmStore,
    permissions: AlarmPermissions,
    wakeAlarms: WakeAlarmRepository,
    seeder: SampleDeckSeeder,
    geoSeeder: GeoDeckSeeder,
    atlasSource: GeoAtlasSource,
    decks: DeckRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(WakeMode.NORMAL) }
    var region by remember { mutableStateOf(GeoRegion.EUROPE) }
    /** Deck the test alarms use; null means the first deck by name. */
    var deckId by remember { mutableStateOf<String?>(null) }
    val allDecks by remember { decks.observeDecks() }.collectAsState(initial = emptyList())
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var alarms by remember { mutableStateOf(emptyList<Alarm>()) }
    var events by remember { mutableStateOf(emptyList<RingEvent>()) }
    val ringing by RingingState.current.collectAsState()

    LaunchedEffect(refresh, ringing) {
        alarms = store.alarms().filter { it.enabled }.sortedBy { it.time }
        events = store.events().reversed()
    }

    // Coming back from a Settings screen or a permission prompt: check again.
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun open(intent: Intent?) {
        if (intent != null) runCatching { settings.launch(intent) }
    }

    fun schedule(alarm: Alarm) {
        scope.launch {
            // The morning flow reads the wake-up mode from the app's own alarm record.
            wakeAlarms.save(WakeAlarm(id = alarm.id, time = alarm.time, label = alarm.label, mode = mode, deckId = deckId))
            val scheduled = when (val result = scheduler.schedule(alarm)) {
                is Result.Success -> result.data?.let { "\"${alarm.label}\" rings at ${it.localTime()}" } ?: "Alarm is off"
                is Result.Error -> "Couldn't schedule: ${result.error}"
            }
            message = when {
                !permissions.notificationsAllowed() ->
                    "$scheduled. Notifications are off, so it will ring but nothing will show. Fix Notifications above."
                !permissions.fullScreenAllowed() ->
                    "$scheduled. Full-screen alarm is off, so it shows only as a notification."
                else -> scheduled
            }
            refresh++
        }
    }

    fun ringIn(minutes: Int) {
        // Ring at the start of a minute, between `minutes` and `minutes + 1` from now.
        val at = java.time.LocalTime.now().plusSeconds(minutes * 60L + 59)
        schedule(
            Alarm(
                id = "test-in-$minutes",
                time = LocalTime(at.hour, at.minute),
                label = "Test in $minutes min",
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SunupTheme.spacing.xxl)
            .background(SunupTheme.colors.surface, SunupTheme.shapes.card)
            .border(2.dp, SunupTheme.colors.border, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Text("ALARM TEST BENCH", style = SunupTheme.typography.label, color = SunupTheme.colors.inkMuted)

        // Permissions
        val notificationsOk = remember(refresh) { permissions.notificationsAllowed() }
        val exactOk = remember(refresh) { permissions.exactAlarmsAllowed() }
        val fullScreenOk = remember(refresh) { permissions.fullScreenAllowed() }
        val batteryOk = remember(refresh) { permissions.batteryUnrestricted() }

        CheckRow("Notifications", notificationsOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open(permissions.notificationSettings())
            }
        }
        CheckRow("Exact alarms", exactOk) { open(permissions.exactAlarmSettings()) }
        CheckRow("Full-screen alarm", fullScreenOk) { open(permissions.fullScreenSettings()) }
        CheckRow("Battery: unrestricted", batteryOk) { open(permissions.batterySettings()) }
        Text(
            "Xiaomi, Samsung and similar phones: also allow Autostart / \"never sleeping apps\" for Sunup in the phone's settings.",
            style = SunupTheme.typography.bodySmall,
            color = SunupTheme.colors.inkMuted,
        )

        // Morning flow
        Text("MORNING FLOW", style = SunupTheme.typography.label, color = SunupTheme.colors.inkMuted)
        SunupButton(
            text = "Load sample Spanish deck",
            style = SunupButtonStyle.Secondary,
            onClick = {
                scope.launch {
                    val deck = seeder.seedSpanish()
                    deckId = deck.id
                    message = "Loaded \"${deck.name}\": 12 cards, every game"
                }
            },
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Map", style = SunupTheme.typography.body, color = SunupTheme.colors.ink)
            GeoRegion.entries.forEach { option ->
                TextButton(onClick = { region = option }) {
                    Text(
                        text = (if (option == region) "● " else "") + option.title,
                        color = if (option == region) SunupTheme.colors.indigo else SunupTheme.colors.inkMuted,
                    )
                }
            }
        }
        SunupButton(
            text = "Load geography deck · ${region.title}",
            style = SunupButtonStyle.Secondary,
            onClick = {
                scope.launch {
                    message = when (val atlas = atlasSource.load()) {
                        is Result.Success -> {
                            val countries = atlas.data.countries(region)
                            val deck = geoSeeder.seed(region, countries)
                            deckId = deck.id
                            "Loaded \"${deck.name}\": ${countries.size} countries. Test alarms now use it."
                        }
                        is Result.Error ->
                            "No map file yet. Build it with tools/geo/build_countries.py, then rebuild the app."
                    }
                }
            },
        )
        if (allDecks.isNotEmpty()) {
            Text("Deck for test alarms", style = SunupTheme.typography.body, color = SunupTheme.colors.ink)
            val options = listOf<Pair<String?, String>>(null to "First deck") + allDecks.map { it.id to it.name }
            options.forEach { (id, name) ->
                TextButton(onClick = { deckId = id }) {
                    Text(
                        text = (if (id == deckId) "● " else "") + name,
                        color = if (id == deckId) SunupTheme.colors.indigo else SunupTheme.colors.inkMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Text(
            "Without a deck, the alarm uses Quick math.",
            style = SunupTheme.typography.bodySmall,
            color = SunupTheme.colors.inkMuted,
        )
        SunupButton(
            text = "Preview all games",
            style = SunupButtonStyle.Secondary,
            onClick = { context.startActivity(AlarmRingingActivity.previewIntent(context)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mode", style = SunupTheme.typography.body, color = SunupTheme.colors.ink, modifier = Modifier.weight(1f))
            WakeMode.entries.forEach { option ->
                TextButton(onClick = { mode = option }) {
                    Text(
                        text = (if (option == mode) "● " else "") + option.label(),
                        color = if (option == mode) SunupTheme.colors.indigo else SunupTheme.colors.inkMuted,
                    )
                }
            }
        }

        // Test alarms
        SunupButton(text = "Ring in 1 minute", onClick = { ringIn(1) })
        SunupButton(text = "Ring in 5 minutes", onClick = { ringIn(5) }, style = SunupButtonStyle.Secondary)
        SunupButton(
            text = "Ring at a time…",
            style = SunupButtonStyle.Secondary,
            onClick = {
                val now = java.time.LocalTime.now()
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        schedule(Alarm(id = "test-at", time = LocalTime(hour, minute), label = "Test at a time"))
                    },
                    now.hour,
                    now.minute,
                    true,
                ).show()
            },
        )
        if (ringing != null) {
            SunupButton(text = "Stop ringing", onClick = { AlarmRingingService.stop(context) })
        }
        message?.let { Text(it, style = SunupTheme.typography.body, color = SunupTheme.colors.indigo) }

        // Saved alarms
        if (alarms.isNotEmpty()) {
            Text("SET ALARMS", style = SunupTheme.typography.label, color = SunupTheme.colors.inkMuted)
            alarms.forEach { alarm ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${alarm.time}  ${alarm.label}",
                        style = SunupTheme.typography.body,
                        color = SunupTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scope.launch { scheduler.cancel(alarm.id); refresh++ } }) {
                        Text("Cancel")
                    }
                }
            }
        }

        // Ring log
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "RING LOG (NEWEST FIRST)",
                style = SunupTheme.typography.label,
                color = SunupTheme.colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Sunup ring log", events.joinToString("\n") { it.describe() }))
                message = "Log copied"
            }) { Text("Copy") }
            TextButton(onClick = { scope.launch { store.clearEvents(); refresh++ } }) { Text("Clear") }
        }
        if (events.isEmpty()) {
            Text("Nothing yet.", style = SunupTheme.typography.bodySmall, color = SunupTheme.colors.inkMuted)
        }
        events.take(40).forEach { event ->
            val late = event.type == RingEvent.Type.RANG && (event.delay ?: Duration.ZERO) > 1.minutes
            val failed = event.type == RingEvent.Type.SCHEDULE_FAILED || event.type == RingEvent.Type.TIMED_OUT
            Text(
                event.describe(),
                style = SunupTheme.typography.bodySmall,
                color = if (late || failed) SunupTheme.colors.wrong else SunupTheme.colors.ink,
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = (if (ok) "✓  " else "✗  ") + label,
            style = SunupTheme.typography.body,
            color = if (ok) SunupTheme.colors.onCorrectContainer else SunupTheme.colors.wrong,
            modifier = Modifier.weight(1f),
        )
        if (!ok) {
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

private fun WakeMode.label(): String = when (this) {
    WakeMode.GENTLE -> "Gentle"
    WakeMode.NORMAL -> "Normal"
    WakeMode.HEAVY_SLEEPER -> "Heavy"
}

private val logTime = DateTimeFormatter.ofPattern("EEE HH:mm:ss", Locale.US)
private val shortTime = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private fun Instant.toZoned() =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(ZoneId.systemDefault())

private fun Instant.localTime(): String = toZoned().format(shortTime)

private fun RingEvent.describe(): String = buildString {
    append(at.toZoned().format(logTime))
    append("  ")
    append(type.name)
    append("  ")
    append(alarmId)
    when (type) {
        RingEvent.Type.SCHEDULED, RingEvent.Type.SCHEDULE_FAILED ->
            expected?.let { append(" → ").append(it.toZoned().format(logTime)) }
        RingEvent.Type.RANG ->
            delay?.let { append(" · ").append(String.format(Locale.US, "%.1f s late", it.inWholeMilliseconds / 1000.0)) }
        else -> Unit
    }
    if (note.isNotBlank()) append(" · ").append(note)
}
