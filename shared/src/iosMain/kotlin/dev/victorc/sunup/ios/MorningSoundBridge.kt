package dev.victorc.sunup.ios

/**
 * Sunup's own alarm sound on iOS. Apple's alarm goes quiet as soon as "Wake up with Pip" is
 * tapped, so Sunup keeps ringing itself until the card is answered. Implemented in
 * `iosApp/iosApp/MorningSoundPlayer.swift` and handed to [startSunup] when the app starts.
 *
 * Not on the ring path: AlarmKit rings whether or not this works.
 */
interface MorningSoundBridge {
    /**
     * Starts the looping sound, or starts it again if it was cut off. Heard while Sunup is on
     * screen, in silent mode and with the phone's volume turned down (it turns the volume up).
     */
    fun start()

    /** Stops the sound and puts the phone's volume back where it was. */
    fun stop()
}
