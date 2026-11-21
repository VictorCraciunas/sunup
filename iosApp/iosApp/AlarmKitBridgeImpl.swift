import AlarmKit
import CryptoKit
import Foundation
import Shared
import SwiftUI

/// What Sunup's alarms carry. Nothing yet, but AlarmKit needs a type.
struct SunupAlarmMetadata: AlarmMetadata {}

/// Sunup's alarms through Apple's AlarmKit (iOS 26+). Kotlin's alarm engine calls this
/// through the `AlarmKitBridge` interface (shared/src/iosMain/.../ios/AlarmKitBridge.kt);
/// `iOSApp` hands it over at start-up.
///
/// Apple shows its own alarm screen. Ours adds a "Wake up with Pip" button that opens the
/// card (`OpenMorningIntent`). Stop can't be hidden, so Kotlin also sets follow-ups every
/// 3 minutes that only answering the card cancels; each Stop adds another (`StopAlarmIntent`).
final class AlarmKitBridgeImpl: NSObject, AlarmKitBridge {

    /// One for the whole app: the stop intent waits on its work (`settle`).
    static let shared = AlarmKitBridgeImpl()

    private let manager = AlarmManager.shared

    /// Alarms on their way to AlarmKit. Kotlin calls from several threads, hence the lock.
    private let lock = NSLock()
    private var inFlight: [Task<Void, Never>] = []

    /// Waits until every alarm handed over so far has reached AlarmKit. For code that iOS may
    /// suspend as soon as it returns (the stop intent, in the background).
    func settle() async {
        let tasks = lock.withLock { () -> [Task<Void, Never>] in
            let waiting = inFlight
            inFlight.removeAll()
            return waiting
        }
        for task in tasks {
            await task.value
        }
    }

    func isAuthorized() -> Bool {
        manager.authorizationState == .authorized
    }

    func requestAuthorization(onResult: @escaping (KotlinBoolean) -> Void) {
        Task {
            let state = (try? await manager.requestAuthorization()) ?? .denied
            await MainActor.run { onResult(KotlinBoolean(value: state == .authorized)) }
        }
    }

    func scheduleOnce(alarmId: String, epochMillis: Int64, soft: Bool) {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000)
        schedule(alarmId: alarmId, schedule: .fixed(date), soft: soft)
    }

    func scheduleWeekly(alarmId: String, hour: Int32, minute: Int32, weekdays: [KotlinInt]) {
        let days = weekdays.compactMap { Self.weekday(iso: $0.intValue) }
        let time = Alarm.Schedule.Relative.Time(hour: Int(hour), minute: Int(minute))
        schedule(alarmId: alarmId, schedule: .relative(.init(time: time, repeats: .weekly(days))), soft: false)
    }

    func cancel(alarmId: String) {
        try? manager.cancel(id: Self.uuid(for: alarmId))
    }

    func scheduledAt(alarmId: String) -> Int64 {
        let id = Self.uuid(for: alarmId)
        guard let alarm = (try? manager.alarms)?.first(where: { $0.id == id }),
              case .fixed(let date)? = alarm.schedule
        else { return -1 }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    // MARK: - Private

    private func schedule(alarmId: String, schedule: Alarm.Schedule, soft: Bool) {
        let id = Self.uuid(for: alarmId)
        let title: LocalizedStringResource = soft ? "Still with me?" : "Good morning!"
        let alert = AlarmPresentation.Alert(
            title: title,
            stopButton: AlarmButton(text: "Stop", textColor: .white, systemImageName: "stop.fill"),
            secondaryButton: AlarmButton(text: "Wake up with Pip", textColor: .black, systemImageName: "sun.max.fill"),
            secondaryButtonBehavior: .custom
        )
        let attributes = AlarmAttributes<SunupAlarmMetadata>(
            presentation: AlarmPresentation(alert: alert),
            metadata: SunupAlarmMetadata(),
            // Pip's sun yellow (SunupTheme.colors.sun, #FFC23D).
            tintColor: Color(red: 1.0, green: 0.76, blue: 0.24)
        )
        let configuration = AlarmManager.AlarmConfiguration<SunupAlarmMetadata>.alarm(
            schedule: schedule,
            attributes: attributes,
            stopIntent: StopAlarmIntent(alarmId: alarmId),
            secondaryIntent: OpenMorningIntent(alarmId: alarmId),
            sound: .default
        )
        // Replace any earlier alarm with this id (Kotlin schedules again after every change).
        try? manager.cancel(id: id)
        let task = Task {
            do {
                _ = try await manager.schedule(id: id, configuration: configuration)
            } catch {
                print("Sunup: AlarmKit couldn't schedule \(alarmId): \(error)")
            }
        }
        lock.withLock {
            inFlight.append(task)
            // Old ones finished long ago; only the newest can still be on their way.
            if inFlight.count > 32 {
                inFlight.removeFirst(inFlight.count - 32)
            }
        }
    }

    /// 1 = Monday … 7 = Sunday (Kotlin's isoDayNumber).
    private static func weekday(iso: Int) -> Locale.Weekday? {
        switch iso {
        case 1: return .monday
        case 2: return .tuesday
        case 3: return .wednesday
        case 4: return .thursday
        case 5: return .friday
        case 6: return .saturday
        case 7: return .sunday
        default: return nil
        }
    }

    /// AlarmKit wants UUIDs. Sunup's alarm ids are UUIDs already; others ("…:stop", "test-alarm")
    /// get a stable one made from the text, so the same id always maps to the same alarm.
    static func uuid(for alarmId: String) -> UUID {
        if let uuid = UUID(uuidString: alarmId) { return uuid }
        var b = Array(SHA256.hash(data: Data(alarmId.utf8)).prefix(16))
        b[6] = (b[6] & 0x0F) | 0x50
        b[8] = (b[8] & 0x3F) | 0x80
        return UUID(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }
}
