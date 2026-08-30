import Foundation
import CoreBluetooth

@MainActor
final class JoeyBluetoothManager: NSObject, ObservableObject {
    enum ConnectionState: Equatable {
        case bluetoothOff
        case scanning
        case disconnected
        case connecting(String)
        case connected(String)
        case error(String)

        var label: String {
            switch self {
            case .bluetoothOff: return "Bluetooth désactivé"
            case .scanning: return "Recherche du Joey…"
            case .disconnected: return "Non connecté"
            case .connecting(let name): return "Connexion à \(name)…"
            case .connected(let name): return "Connecté à \(name)"
            case .error(let message): return message
            }
        }
    }

    @Published private(set) var state: ConnectionState = .disconnected
    @Published private(set) var reading: WaterReading?
    @Published private(set) var discoveredDevices: [CBPeripheral] = []

    private var central: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?

    // À remplacer par les UUID GATT confirmés sur le Joey réel.
    private let knownServiceUUIDs: [CBUUID] = []

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        guard central.state == .poweredOn else {
            state = .bluetoothOff
            return
        }
        discoveredDevices.removeAll()
        state = .scanning
        central.scanForPeripherals(withServices: knownServiceUUIDs.isEmpty ? nil : knownServiceUUIDs,
                                   options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func stopScan() {
        central.stopScan()
        if connectedPeripheral == nil { state = .disconnected }
    }

    func connect(_ peripheral: CBPeripheral) {
        central.stopScan()
        state = .connecting(peripheral.name ?? "Joey")
        connectedPeripheral = peripheral
        peripheral.delegate = self
        central.connect(peripheral)
    }

    func disconnect() {
        guard let connectedPeripheral else { return }
        central.cancelPeripheralConnection(connectedPeripheral)
    }

    func requestMeasurement() {
        // V1 : le déclenchement sera câblé quand les caractéristiques GATT
        // lecture/écriture du Joey auront été confirmées sur l'appareil réel.
        if reading == nil {
            reading = .preview
        }
    }
}

extension JoeyBluetoothManager: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            switch central.state {
            case .poweredOn:
                if connectedPeripheral == nil { state = .disconnected }
            case .poweredOff, .unauthorized, .unsupported:
                state = .bluetoothOff
            default:
                state = .disconnected
            }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager,
                                    didDiscover peripheral: CBPeripheral,
                                    advertisementData: [String : Any],
                                    rssi RSSI: NSNumber) {
        Task { @MainActor in
            let name = (peripheral.name ?? "").lowercased()
            guard name.contains("joey") || name.contains("blue") else { return }
            guard !discoveredDevices.contains(where: { $0.identifier == peripheral.identifier }) else { return }
            discoveredDevices.append(peripheral)
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        Task { @MainActor in
            state = .connected(peripheral.name ?? "Joey")
            peripheral.discoverServices(knownServiceUUIDs.isEmpty ? nil : knownServiceUUIDs)
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager,
                                    didFailToConnect peripheral: CBPeripheral,
                                    error: Error?) {
        Task { @MainActor in
            state = .error("Connexion impossible")
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager,
                                    didDisconnectPeripheral peripheral: CBPeripheral,
                                    error: Error?) {
        Task { @MainActor in
            connectedPeripheral = nil
            state = .disconnected
        }
    }
}

extension JoeyBluetoothManager: CBPeripheralDelegate {
    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil else { return }
        peripheral.services?.forEach { peripheral.discoverCharacteristics(nil, for: $0) }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral,
                                didDiscoverCharacteristicsFor service: CBService,
                                error: Error?) {
        // Point d'instrumentation pour identifier les caractéristiques du Joey réel.
        // Les UUID seront ensuite figés dans une version ultérieure.
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral,
                                didUpdateValueFor characteristic: CBCharacteristic,
                                error: Error?) {
        // Le décodage binaire sera ajouté après capture d'une mesure réelle.
    }
}
