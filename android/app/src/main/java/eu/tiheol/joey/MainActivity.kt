package eu.tiheol.joey

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.homeisle.joey.ble.JoeyBleScanner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JoeyApp() }
    }
}

@Composable
fun JoeyApp() {
    val context = LocalContext.current
    val scanner = remember { JoeyBleScanner(context.applicationContext) }
    val state by scanner.state.collectAsState()
    val devices by scanner.devices.collectAsState()
    val gattValues by scanner.gattValues.collectAsState()
    var diagnostic by remember { mutableStateOf("V3 GATT — prêt") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val scanOk = result[Manifest.permission.BLUETOOTH_SCAN] == true
        val connectOk = result[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (scanOk && connectOk) {
            diagnostic = "Autorisations accordées — lancement du scan"
            scanner.startScan()
        } else {
            diagnostic = "Bluetooth refusé : activez Appareils à proximité dans Paramètres > Applications > JOEY > Autorisations"
        }
    }

    fun startBleScan() {
        diagnostic = "Bouton reçu — vérification Bluetooth…"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (!scanner.hasScanPermission() || !scanner.hasConnectPermission())
        ) {
            diagnostic = "Demande d’autorisation Bluetooth…"
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            diagnostic = "Autorisations OK — scan BLE lancé"
            scanner.startScan()
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val unfolded = maxWidth >= 600.dp
                JoeyDashboard(
                    unfolded = unfolded,
                    scanner = scanner,
                    state = state,
                    devices = devices,
                    gattValues = gattValues,
                    diagnostic = diagnostic,
                    onScan = ::startBleScan
                )
            }
        }
    }
}

@Composable
private fun JoeyDashboard(
    unfolded: Boolean,
    scanner: JoeyBleScanner,
    state: JoeyBleScanner.State,
    devices: List<JoeyBleScanner.Device>,
    gattValues: List<JoeyBleScanner.GattValue>,
    diagnostic: String,
    onScan: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(if (unfolded) 32.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("JOEY", style = if (unfolded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge)
            Text("Mon Spa", style = MaterialTheme.typography.headlineMedium)
            Text("V3 GATT", style = MaterialTheme.typography.labelLarge)
        }

        item {
            if (unfolded) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusCard("Température", "35,1 °C", Modifier.weight(1f))
                    StatusCard("pH", "7,4", Modifier.weight(1f))
                    StatusCard("Désinfectant", "650 mV", Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatusCard("Température", "35,1 °C")
                    StatusCard("pH", "7,4")
                    StatusCard("Désinfectant", "650 mV")
                }
            }
        }

        item {
            Text("Connexion GATT", style = MaterialTheme.typography.titleLarge)
            Text(stateLabel(state), style = MaterialTheme.typography.bodyLarge)
            Text(diagnostic, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text(if (state is JoeyBleScanner.State.Scanning) "Recherche en cours…" else "Rechercher le Joey")
            }
        }

        if (devices.isNotEmpty()) {
            item { Text("Appareils détectés", style = MaterialTheme.typography.titleMedium) }
            items(devices, key = { it.address }) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name ?: "Appareil BLE", style = MaterialTheme.typography.titleMedium)
                            Text("${device.address}  •  ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { scanner.connect(device) }) { Text("GATT") }
                    }
                }
            }
        }

        if (gattValues.isNotEmpty()) {
            item {
                Text("Lectures GATT", style = MaterialTheme.typography.titleLarge)
                Text("Lecture seule — aucune commande n’est écrite dans le Joey.", style = MaterialTheme.typography.bodySmall)
            }
            items(gattValues, key = { "${it.serviceUuid}-${it.characteristicUuid}" }) { value ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Service ${value.serviceUuid}", style = MaterialTheme.typography.labelMedium)
                        Text("Caractéristique ${value.characteristicUuid}", style = MaterialTheme.typography.labelMedium)
                        Text(if (value.hex.isBlank()) "(vide)" else value.hex, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun stateLabel(state: JoeyBleScanner.State): String = when (state) {
    JoeyBleScanner.State.Idle -> "Prêt à rechercher le Joey"
    JoeyBleScanner.State.Scanning -> "Recherche Bluetooth Low Energy…"
    is JoeyBleScanner.State.Connecting -> "Connexion GATT à ${state.name}…"
    is JoeyBleScanner.State.Connected -> "Connecté en GATT à ${state.name}"
    is JoeyBleScanner.State.Error -> "Erreur : ${state.message}"
}

@Composable
private fun StatusCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
