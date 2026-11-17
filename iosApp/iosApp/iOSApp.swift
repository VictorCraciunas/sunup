import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Koin, storage and AlarmKit, before the first screen (shared/src/iosMain/.../ios/IosApp.kt).
        IosAppKt.startSunup(alarmKit: AlarmKitBridgeImpl())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
