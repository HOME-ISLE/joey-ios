import SwiftUI

@main
struct JOEYApp: App {
    @StateObject private var bluetooth = JoeyBluetoothManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(bluetooth)
        }
    }
}
