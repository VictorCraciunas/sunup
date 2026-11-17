import AppIntents
import Shared

/// "Wake up with Pip" on Apple's alarm screen: opens Sunup on that alarm's card.
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
        IosMorning.shared.show(alarmId: alarmId)
        return .result()
    }
}
