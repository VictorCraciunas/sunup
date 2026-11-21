import AlarmKit
import AppIntents
import Shared

/// Runs when Apple's Stop (or "slide to stop") is used on a Sunup alarm, usually in the
/// background. Stopping is allowed, but the card wasn't answered, so Kotlin sets the alarm to
/// come back 30 seconds from now, and every 30 seconds after
/// (`IosPlatformAlarmClock.dismissedWithoutCard`).
///
/// If this never runs, the follow-ups that were set ahead still ring.
struct StopAlarmIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop"
    static let isDiscoverable = false

    @Parameter(title: "Alarm")
    var alarmId: String

    init() {}

    init(alarmId: String) {
        self.alarmId = alarmId
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        // With a stop intent of our own, silencing the alarm is ours to do.
        try? AlarmManager.shared.stop(id: AlarmKitBridgeImpl.uuid(for: alarmId))

        SunupStartup.run()
        try? await IosMorning.shared.stopped(ringId: alarmId)
        // iOS may suspend the app as soon as this returns: wait until AlarmKit has the new rings.
        await AlarmKitBridgeImpl.shared.settle()
        return .result()
    }
}
