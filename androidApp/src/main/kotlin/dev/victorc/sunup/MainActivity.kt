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
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val scheduler: AlarmScheduler by inject()
    private val store: AlarmStore by inject()
    private val permissions: AlarmPermissions by inject()

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
                extraContent = {
                    // TODO(M4): remove, or hide behind a debug menu, once real alarm screens exist.
                    AlarmTestBench(scheduler = scheduler, store = store, permissions = permissions)
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
