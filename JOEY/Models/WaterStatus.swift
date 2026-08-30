import Foundation

enum WaterStatus: String {
    case optimal = "Eau optimale"
    case watch = "À surveiller"
    case action = "Action nécessaire"
    case unknown = "En attente de mesure"

    static func evaluate(_ reading: WaterReading?) -> WaterStatus {
        guard let reading else { return .unknown }
        guard let pH = reading.pH, let orp = reading.orp else { return .unknown }

        let pHOK = (7.0...7.6).contains(pH)
        let orpOK = (650...800).contains(orp)

        if pHOK && orpOK { return .optimal }
        if (6.8...7.8).contains(pH) && (600...850).contains(orp) { return .watch }
        return .action
    }
}
