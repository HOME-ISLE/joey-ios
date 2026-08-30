import Foundation

struct WaterReading: Identifiable, Codable {
    let id: UUID
    let date: Date
    let waterTemperature: Double?
    let airTemperature: Double?
    let pH: Double?
    let orp: Double?
    let conductivity: Double?

    init(
        id: UUID = UUID(),
        date: Date = .now,
        waterTemperature: Double? = nil,
        airTemperature: Double? = nil,
        pH: Double? = nil,
        orp: Double? = nil,
        conductivity: Double? = nil
    ) {
        self.id = id
        self.date = date
        self.waterTemperature = waterTemperature
        self.airTemperature = airTemperature
        self.pH = pH
        self.orp = orp
        self.conductivity = conductivity
    }
}

extension WaterReading {
    static let preview = WaterReading(
        waterTemperature: 35.1,
        airTemperature: 23.0,
        pH: 7.4,
        orp: 705,
        conductivity: 2.6
    )
}
