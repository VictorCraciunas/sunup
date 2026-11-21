import AppIntents
import Shared

/// "Wake up with Pip" on Apple's alarm screen: opens Sunup on that alarm's card.
/// The card isn't answered yet, so the alarm is also set to come back 3 minutes from now;
/// answering the card cancels that.
struct OpenMorningIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Wake up with Pip"
    static let openAppWhenRun = true

    @Parameter(title: "Alarm")
    var alarmId: String

    init() {}

    init(alarmId: String) {
        self.alarmId = alarmId
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        SunupStartup.run()
        IosMorning.shared.show(alarmId: alarmId)
        try? await IosMorning.shared.dismissed(ringId: alarmId)
        // iOS may suspend the app again (phone still locked): wait until AlarmKit has the new rings.
        await AlarmKitBridgeImpl.shared.settle()
        return .result()
    }
}
