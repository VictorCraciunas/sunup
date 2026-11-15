package dev.victorc.sunup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import dev.victorc.sunup.alarm.AlarmRingingActivity
import dev.victorc.sunup.alarm.AlarmTestBench
import dev.victorc.sunup.core.alarm.AlarmPermissions
import dev.victorc.sunup.core.alarm.AlarmScheduler
import dev.victorc.sunup.core.alarm.AlarmStore
import dev.victorc.sunup.core.alarm.RingingState
import dev.victorc.sunup.core.data.GeoDeckSeeder
import dev.victorc.sunup.core.data.SampleDeckSeeder
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.geo.GeoAtlasSource
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val scheduler: AlarmScheduler by inject()
    private val store: AlarmStore by inject()
    private val permissions: AlarmPermissions by inject()
    private val wakeAlarms: WakeAlarmRepository by inject()
    private val seeder: SampleDeckSeeder by inject()
    private val geoSeeder: GeoDeckSeeder by inject()
    private val atlasSource: GeoAtlasSource by inject()
    private val decks: DeckRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // Whenever the app is open while an alarm rings, show the ringing screen. This is also the
            // way back to it if notifications are off and the full-screen alarm couldn't appear.
            val ringing by RingingState.current.collectAsState()
            LaunchedEffect(ringing) {
                if (ringing != null) {
                    startActivity(Intent(this@MainActivity, AlarmRingingActivity::class.java))
                }
            }

            App(
                onPreviewMorning = { alarmId ->
                    startActivity(AlarmRingingActivity.previewIntent(this@MainActivity, alarmId))
                },
                testBench = {
                    // Debug tools for real-phone testing (ring log, quick test alarms). TODO: drop before release.
                    AlarmTestBench(
                        scheduler = scheduler,
                        store = store,
                        permissions = permissions,
                        wakeAlarms = wakeAlarms,
                        seeder = seeder,
                        geoSeeder = geoSeeder,
                        atlasSource = atlasSource,
                        decks = decks,
                    )
                },
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
