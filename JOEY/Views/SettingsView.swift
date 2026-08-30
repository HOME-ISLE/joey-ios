import SwiftUI

struct SettingsView: View {
    var body: some View {
        NavigationStack {
            Form {
                Section("Bassin") {
                    LabeledContent("Type", value: "Spa")
                    LabeledContent("Volume", value: "1 750 L")
                    LabeledContent("Désinfection", value: "Brome")
                }
                Section("Application") {
                    LabeledContent("Version", value: "V1")
                    Text("Aucun identifiant Joey n'est stocké dans le code source.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Réglages")
        }
    }
}
