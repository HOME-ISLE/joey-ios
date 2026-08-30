import SwiftUI

struct DevicesView: View {
    @EnvironmentObject private var bluetooth: JoeyBluetoothManager

    var body: some View {
        NavigationStack {
            List {
                Section("Connexion") {
                    Text(bluetooth.state.label)
                    Button("Rechercher") { bluetooth.startScan() }
                }

                Section("Appareils détectés") {
                    if bluetooth.discoveredDevices.isEmpty {
                        Text("Aucun Joey détecté")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(bluetooth.discoveredDevices, id: \.identifier) { device in
                            Button {
                                bluetooth.connect(device)
                            } label: {
                                Label(device.name ?? "Joey", systemImage: "sensor.fill")
                            }
                        }
                    }
                }
            }
            .navigationTitle("Appareils")
        }
    }
}
