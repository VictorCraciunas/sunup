package dev.victorc.sunup.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.victorc.sunup.core.alarm.AlarmRingingService
import dev.victorc.sunup.core.alarm.RingingState
import dev.victorc.sunup.core.alarm.WakeUpScreenState
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_ID
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_PREFIX
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
        val id = savedInstanceState?.getString(KEY_ALARM_ID)
            ?: intent.getStringExtra(EXTRA_PREVIEW_ALARM)?.let { PREVIEW_ALARM_PREFIX + it }
            ?: PREVIEW_ALARM_ID.takeIf { intent.getBooleanExtra(EXTRA_PREVIEW, false) }
            ?: RingingState.current.value?.alarmId
        if (id == null) {
            finish()
            return
        }
        alarmId = id
        showOverLockScreen()

        setContent {
            // No escaping with Back: the alarm stops by answering, or with the emergency stop.
            BackHandler {}
            val ringing by RingingState.current.collectAsState()

            SunupTheme {
                WakeUpRoot(
                    alarmId = id,
                    ringing = ringing != null,
                    onStopAlarm = { AlarmRingingService.stop(this) },
                    onClose = { finish() },
                )
            }
        }
    }

    // The wake-up check rings whenever this screen is showing and was left alone. When it isn't
    // showing, the alarm engine decides at ring time: busy in another app means awake for now.
    override fun onStart() {
        super.onStart()
        WakeUpScreenState.isVisible = true
    }

    override fun onStop() {
        WakeUpScreenState.isVisible = false
        super.onStop()
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

    companion object {
        private const val KEY_ALARM_ID = "alarm_id"
        private const val EXTRA_PREVIEW = "dev.victorc.sunup.extra.PREVIEW"
        private const val EXTRA_PREVIEW_ALARM = "dev.victorc.sunup.extra.PREVIEW_ALARM"

        /** Plays every game once without an alarm, for trying them out. Saves nothing. */
        fun previewIntent(context: Context): Intent =
            Intent(context, AlarmRingingActivity::class.java).putExtra(EXTRA_PREVIEW, true)

        /** Plays [alarmId]'s morning (its deck and mode) without sound, as practice. Saves nothing. */
        fun previewIntent(context: Context, alarmId: String): Intent =
            Intent(context, AlarmRingingActivity::class.java).putExtra(EXTRA_PREVIEW_ALARM, alarmId)
    }
}
