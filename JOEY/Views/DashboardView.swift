import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var bluetooth: JoeyBluetoothManager

    private var reading: WaterReading { bluetooth.reading ?? .preview }
    private var status: WaterStatus { WaterStatus.evaluate(bluetooth.reading ?? .preview) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    statusCard
                    temperatureCard
                    chemistryGrid
                    connectionCard
                }
                .padding()
            }
            .navigationTitle("JOEY")
            .background(Color(.systemGroupedBackground))
        }
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(status.rawValue, systemImage: status == .optimal ? "checkmark.seal.fill" : "exclamationmark.triangle.fill")
                .font(.title2.bold())
            Text("Dernière mesure : \(bluetooth.reading?.date.formatted(date: .omitted, time: .shortened) ?? "démonstration")")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 22))
    }

    private var temperatureCard: some View {
        VStack(spacing: 6) {
            Image(systemName: "water.waves")
                .font(.system(size: 34))
            Text(reading.waterTemperature.map { String(format: "%.1f°", $0) } ?? "—")
                .font(.system(size: 54, weight: .bold, design: .rounded))
            Text("Température de l'eau")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
        .background(.background, in: RoundedRectangle(cornerRadius: 26))
    }

    private var chemistryGrid: some View {
        HStack(spacing: 12) {
            metric(title: "pH", value: reading.pH.map { String(format: "%.2f", $0) } ?? "—", icon: "flask.fill")
            metric(title: "Redox", value: reading.orp.map { "\(Int($0)) mV" } ?? "—", icon: "bolt.fill")
        }
    }

    private func metric(title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon).font(.title2)
            Text(value).font(.title2.bold())
            Text(title).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.background, in: RoundedRectangle(cornerRadius: 22))
    }

    private var connectionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(bluetooth.state.label, systemImage: "antenna.radiowaves.left.and.right")
                .font(.headline)
            Button("Rechercher mon Joey") { bluetooth.startScan() }
                .buttonStyle(.borderedProminent)
            Button("Lancer une mesure") { bluetooth.requestMeasurement() }
                .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.background, in: RoundedRectangle(cornerRadius: 22))
    }
}
