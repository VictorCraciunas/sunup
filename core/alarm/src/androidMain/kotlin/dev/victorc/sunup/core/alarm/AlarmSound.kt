package dev.victorc.sunup.core.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import co.touchlab.kermit.Logger

/**
 * Plays the phone's alarm sound on the alarm stream (so it rings in silent mode),
 * starting soft and getting louder. If that sound can't play (e.g. media isn't available
 * yet after a reboot), it falls back to plain beeps so the alarm is never silent.
 */
internal class AlarmSound(
    private val context: Context,
    /** 1 = the phone's alarm volume. Lower for soft rings. */
    private val maxVolume: Float = 1f,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val log = Logger.withTag("AlarmSound")

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var volume = START_VOLUME.coerceAtMost(maxVolume)

    private val ramp = object : Runnable {
        override fun run() {
            volume = (volume + VOLUME_STEP).coerceAtMost(maxVolume)
            player?.setVolume(volume, volume)
            if (volume < maxVolume) handler.postDelayed(this, STEP_MILLIS)
        }
    }

    private val beep = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
            handler.postDelayed(this, 1_500L)
        }
    }

    fun start() {
        val uri = alarmSoundUri()
        if (uri == null) {
            startBeeping()
            return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                setVolume(volume, volume)
                setOnErrorListener { _, what, extra ->
                    log.w { "Alarm sound failed ($what/$extra); beeping instead" }
                    startBeeping()
                    true
                }
                prepare()
                start()
            }
            handler.postDelayed(ramp, STEP_MILLIS)
        } catch (e: Exception) {
            log.w(e) { "Couldn't play the alarm sound; beeping instead" }
            startBeeping()
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        tone?.release()
        tone = null
    }

    private fun startBeeping() {
        if (tone != null) return
        player?.release()
        player = null
        tone = runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, (maxVolume * ToneGenerator.MAX_VOLUME).toInt())
        }.getOrNull()
        handler.removeCallbacks(ramp)
        handler.post(beep)
    }

    private fun alarmSoundUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private companion object {
        const val START_VOLUME = 0.15f
        const val VOLUME_STEP = 0.05f
        /** 0.15 → 1.0 in steps of 0.05 every 3 s: full volume after about 50 s. */
        const val STEP_MILLIS = 3_000L
    }
}
