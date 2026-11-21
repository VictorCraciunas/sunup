import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Koin, storage, AlarmKit and Sunup's own alarm sound, before the first screen
        // (shared/src/iosMain/.../ios/IosApp.kt).
        IosAppKt.startSunup(alarmKit: AlarmKitBridgeImpl(), sound: MorningSoundPlayer())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
