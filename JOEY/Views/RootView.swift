import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Spa", systemImage: "drop.fill") }

            HistoryView()
                .tabItem { Label("Analyses", systemImage: "chart.xyaxis.line") }

            DevicesView()
                .tabItem { Label("Appareils", systemImage: "sensor.fill") }

            SettingsView()
                .tabItem { Label("Réglages", systemImage: "gearshape.fill") }
        }
    }
}
