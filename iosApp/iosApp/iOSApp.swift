import SwiftUI
import Shared

/// Starts the Kotlin side once: Koin, storage, AlarmKit and Sunup's own alarm sound
/// (shared/src/iosMain/.../ios/IosApp.kt). Safe to call again; later calls do nothing.
/// Call on the main thread.
enum SunupStartup {
    private static let sound = MorningSoundPlayer()

    static func run() {
        IosAppKt.startSunup(alarmKit: AlarmKitBridgeImpl.shared, sound: sound)
    }
}

@main
struct iOSApp: App {
    init() {
        // Before the first screen. The stop intent calls it too, in case iOS runs it first.
        SunupStartup.run()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
