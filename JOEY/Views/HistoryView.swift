import SwiftUI

struct HistoryView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "Historique à venir",
                systemImage: "chart.xyaxis.line",
                description: Text("Les mesures réelles du Joey seront enregistrées ici.")
            )
            .navigationTitle("Analyses")
        }
    }
}
