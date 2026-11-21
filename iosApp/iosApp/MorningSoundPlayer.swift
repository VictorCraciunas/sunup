import AVFoundation
import MediaPlayer
import Shared
import UIKit

/// Sunup's own alarm sound, for after "Wake up with Pip": Apple's alarm goes quiet when that
/// button is tapped, so this keeps ringing until the card is answered. Kotlin starts and stops
/// it through `MorningSoundBridge` (shared/src/iosMain/.../ios/MorningSoundBridge.kt).
///
/// It plays with the silent switch on, starts soft and gets louder. Leaving Sunup pauses it
/// (the app has no background audio); coming back with the card unanswered rings again.
/// The follow-up alarms from AlarmKit cover the time Sunup isn't on screen.
///
/// While it rings it also keeps the phone's volume up (full on the phone's speaker), so a phone
/// left at volume zero, or the volume-down button, can't silence it. When the sound stops, the
/// volume goes back to where it was.
final class MorningSoundPlayer: NSObject, MorningSoundBridge {

    private static let startVolume: Float = 0.3
    /// Added every tick: from soft to full in about 20 seconds.
    private static let rampStep: Float = 0.0175
    /// How often the sound gets louder and the phone's volume is checked.
    private static let tickSeconds: TimeInterval = 0.5

    /// The phone's volume while Sunup rings, when the sound comes out of the phone's speaker.
    private static let speakerVolume: Float = 1.0
    /// On headphones or another speaker: loud enough to hear, not full blast in someone's ears.
    private static let otherOutputVolume: Float = 0.5

    private var engine: AVAudioEngine?
    private var tick: Timer?
    /// True from start() until stop(): the sound should play whenever Sunup is on screen.
    private var wanted = false

    /// Apple gives apps no way to set the phone's volume. The slider inside this view (kept off
    /// screen) is how alarm apps do it; while the view is there, iOS also doesn't show its volume bar.
    private var volumeView: MPVolumeView?
    /// The phone's volume before Sunup raised it, put back when the sound stops.
    private var volumeBefore: Float?

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appBecameActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWentToBackground),
            name: UIApplication.didEnterBackgroundNotification,
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
        releaseVolume()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: - Private

    @objc private func appBecameActive() {
        if wanted && engine?.isRunning != true {
            play()
        }
    }

    /// Sunup left the screen with the card unanswered: the sound pauses, so the phone gets its
    /// own volume back (not full volume in whatever is opened next). Coming back raises it again.
    @objc private func appWentToBackground() {
        if wanted {
            releaseVolume()
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

            // Its slider takes values a moment after the view is on screen: the first tick is soon enough.
            attachVolumeView()
            tick = Timer.scheduledTimer(
                timeInterval: Self.tickSeconds,
                target: self,
                selector: #selector(onTick),
                userInfo: nil,
                repeats: true
            )
        } catch {
            print("Sunup: couldn't play the morning sound: \(error)")
        }
    }

    /// Runs for as long as the sound plays: a little louder each time, and the phone's volume kept up.
    @objc private func onTick() {
        guard let mixer = engine?.mainMixerNode else {
            tick?.invalidate()
            tick = nil
            return
        }
        if mixer.outputVolume < 1 {
            mixer.outputVolume = min(1, mixer.outputVolume + Self.rampStep)
        }
        holdVolume()
    }

    private func halt() {
        tick?.invalidate()
        tick = nil
        engine?.stop()
        engine = nil
    }

    // MARK: - The phone's volume

    /// Turns the phone's volume up if it's below what the alarm needs: at the start, and again
    /// whenever the volume-down button is used.
    private func holdVolume() {
        let session = AVAudioSession.sharedInstance()
        let onPhoneSpeaker = session.currentRoute.outputs.contains { $0.portType == .builtInSpeaker }
        let target = onPhoneSpeaker ? Self.speakerVolume : Self.otherOutputVolume
        guard session.outputVolume < target - 0.01 else { return }

        guard let view = volumeView, view.window != nil else {
            // No window yet (the app was still opening): try again on the next tick.
            attachVolumeView()
            return
        }
        if volumeBefore == nil {
            volumeBefore = session.outputVolume
        }
        Self.slider(in: view)?.setValue(target, animated: false)
    }

    /// The sound stopped: the phone's volume goes back to what it was.
    private func releaseVolume() {
        if let before = volumeBefore, let view = volumeView, view.window != nil {
            Self.slider(in: view)?.setValue(before, animated: false)
        }
        volumeBefore = nil
        // The view stays a moment longer, so the change doesn't flash iOS's volume bar.
        perform(#selector(removeVolumeView), with: nil, afterDelay: 0.5)
    }

    @objc private func removeVolumeView() {
        if wanted { return }
        volumeView?.removeFromSuperview()
        volumeView = nil
    }

    private func attachVolumeView() {
        if let view = volumeView, view.window != nil { return }
        guard let window = Self.keyWindow() else { return }
        let view = volumeView ?? MPVolumeView(frame: CGRect(x: -1000, y: -1000, width: 100, height: 40))
        window.addSubview(view)
        volumeView = view
    }

    private static func keyWindow() -> UIWindow? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let windows = scenes.flatMap { $0.windows }
        return windows.first { $0.isKeyWindow } ?? windows.first
    }

    private static func slider(in view: UIView) -> UISlider? {
        if let slider = view as? UISlider { return slider }
        for subview in view.subviews {
            if let found = slider(in: subview) { return found }
        }
        return nil
    }

    // MARK: - The sound

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
