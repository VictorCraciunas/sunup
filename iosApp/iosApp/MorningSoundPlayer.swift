import AVFoundation
import Shared
import UIKit

/// Sunup's own alarm sound, for after "Wake up with Pip": Apple's alarm goes quiet when that
/// button is tapped, so this keeps ringing until the card is answered. Kotlin starts and stops
/// it through `MorningSoundBridge` (shared/src/iosMain/.../ios/MorningSoundBridge.kt).
///
/// It plays with the silent switch on, starts soft and gets louder. Leaving Sunup pauses it
/// (the app has no background audio); coming back with the card unanswered rings again.
/// The follow-up alarms from AlarmKit cover the time Sunup isn't on screen.
final class MorningSoundPlayer: NSObject, MorningSoundBridge {

    private static let startVolume: Float = 0.3
    /// Added every second: from soft to full in about 20 seconds.
    private static let rampStep: Float = 0.035

    private var engine: AVAudioEngine?
    private var ramp: Timer?
    /// True from start() until stop(): the sound should play whenever Sunup is on screen.
    private var wanted = false

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appBecameActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    func start() {
        wanted = true
        play()
    }

    func stop() {
        wanted = false
        halt()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: - Private

    @objc private func appBecameActive() {
        if wanted && engine?.isRunning != true {
            play()
        }
    }

    private func play() {
        halt()
        do {
            let session = AVAudioSession.sharedInstance()
            // .playback: heard even with the silent switch on, like an alarm.
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)

            let newEngine = AVAudioEngine()
            let player = AVAudioPlayerNode()
            let buffer = Self.chime()
            newEngine.attach(player)
            newEngine.connect(player, to: newEngine.mainMixerNode, format: buffer.format)
            newEngine.mainMixerNode.outputVolume = Self.startVolume
            try newEngine.start()
            player.scheduleBuffer(buffer, at: nil, options: .loops)
            player.play()
            engine = newEngine

            ramp = Timer.scheduledTimer(
                timeInterval: 1,
                target: self,
                selector: #selector(louder),
                userInfo: nil,
                repeats: true
            )
        } catch {
            print("Sunup: couldn't play the morning sound: \(error)")
        }
    }

    @objc private func louder() {
        guard let mixer = engine?.mainMixerNode else {
            ramp?.invalidate()
            ramp = nil
            return
        }
        mixer.outputVolume = min(1, mixer.outputVolume + Self.rampStep)
        if mixer.outputVolume >= 1 {
            ramp?.invalidate()
            ramp = nil
        }
    }

    private func halt() {
        ramp?.invalidate()
        ramp = nil
        engine?.stop()
        engine = nil
    }

    /// One loop of the sound: two soft bell notes, then a pause. Made here, so no sound file is needed.
    private static func chime() -> AVAudioPCMBuffer {
        let sampleRate: Double = 44_100
        let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
        let frames = AVAudioFrameCount(sampleRate * 1.2)
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames)!
        buffer.frameLength = frames
        let samples = buffer.floatChannelData![0]

        // When each note starts (seconds) and its pitch (Hz): A5, then D6.
        let starts: [Double] = [0.0, 0.22]
        let pitches: [Double] = [880.0, 1174.66]

        for frame in 0..<Int(frames) {
            let time: Double = Double(frame) / sampleRate
            var value: Double = 0
            for note in 0..<starts.count {
                let t: Double = time - starts[note]
                if t < 0 { continue }
                // A quick attack and a bell-like fade, with a quieter octave on top.
                let attack: Double = min(1.0, t / 0.005)
                let fade: Double = exp(-t * 6.0)
                let phase: Double = 2.0 * Double.pi * pitches[note] * t
                let tone: Double = sin(phase) + 0.3 * sin(2.0 * phase)
                value += attack * fade * tone
            }
            samples[frame] = Float(value * 0.45)
        }
        return buffer
    }
}
