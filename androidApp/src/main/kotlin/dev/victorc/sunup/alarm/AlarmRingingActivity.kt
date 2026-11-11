package dev.victorc.sunup.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import dev.victorc.sunup.core.alarm.AlarmRingingService
import dev.victorc.sunup.core.alarm.RingingState
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.feature.wakeup.presentation.WakeUpRoot

/**
 * Shown while an alarm rings, over the lock screen: the morning flow from the silencing
 * card to "You're up!". It stays open after the alarm stops, for the bonus round.
 */
class AlarmRingingActivity : ComponentActivity() {

    private var alarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recreated (e.g. rotated) mid-flow: keep going. Opened from an old notification: nothing to show.
        val id = savedInstanceState?.getString(KEY_ALARM_ID) ?: RingingState.current.value?.alarmId
        if (id == null) {
            finish()
            return
        }
        alarmId = id
        showOverLockScreen()

        setContent {
            // No escaping with Back: the alarm stops by answering, or with the emergency stop.
            BackHandler {}

            SunupTheme {
                WakeUpRoot(
                    alarmId = id,
                    onStopAlarm = { AlarmRingingService.stop(this) },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        alarmId?.let { outState.putString(KEY_ALARM_ID, it) }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private companion object {
        const val KEY_ALARM_ID = "alarm_id"
    }
}
