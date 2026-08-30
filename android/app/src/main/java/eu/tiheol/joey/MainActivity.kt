package eu.tiheol.joey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.tiheol.joey.remote.BlueriiotRemoteClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JoeyApp() }
    }
}

@Composable
fun JoeyApp() {
    val scope = rememberCoroutineScope()
    val client = remember { BlueriiotRemoteClient() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Prêt pour la connexion distante") }
    var loading by remember { mutableStateOf(false) }
    var poolName by remember { mutableStateOf("Mon Spa") }
    var temperature by remember { mutableStateOf<Double?>(null) }
    var ph by remember { mutableStateOf<Double?>(null) }
    var orp by remember { mutableStateOf<Double?>(null) }
    var conductivity by remember { mutableStateOf<Double?>(null) }
    var timestamp by remember { mutableStateOf<String?>(null) }

    fun connectRemote() {
        if (email.isBlank() || password.isBlank()) {
            status = "Saisissez l’email et le mot de passe du compte Joey."
            return
        }
        loading = true
        status = "Connexion au service distant…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val session = client.login(email.trim(), password)
                    val pools = client.getPools(session)
                    require(pools.isNotEmpty()) { "Aucun bassin trouvé sur ce compte." }
                    val pool = pools.first()
                    val devices = client.getDevices(session, pool.id)
                    require(devices.isNotEmpty()) { "Aucun analyseur trouvé pour ${pool.name}." }
                    val device = devices.first()
                    Triple(pool, device, client.getLastMeasurements(session, pool.id, device.serial))
                }
                poolName = result.first.name
                temperature = result.third.temperature
                ph = result.third.ph
                orp = result.third.orp
                conductivity = result.third.conductivity
                timestamp = result.third.timestamp
                status = "Connecté à distance — mesures réelles reçues"
                password = ""
            } catch (e: Exception) {
                status = "Connexion impossible : ${e.message ?: "erreur inconnue"}"
            } finally {
                loading = false
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val unfolded = maxWidth >= 600.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(if (unfolded) 32.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text("JOEY", style = if (unfolded) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge)
                        Text(poolName, style = MaterialTheme.typography.headlineMedium)
                        Text("V4 DISTANT — build 2", style = MaterialTheme.typography.labelLarge)
                    }

                    item {
                        if (unfolded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                MeasureCard("Température", temperature?.let { "%.1f °C".format(it) } ?: "—", Modifier.weight(1f))
                                MeasureCard("pH", ph?.let { "%.2f".format(it) } ?: "—", Modifier.weight(1f))
                                MeasureCard("Désinfectant", orp?.let { "%.0f mV".format(it) } ?: "—", Modifier.weight(1f))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MeasureCard("Température", temperature?.let { "%.1f °C".format(it) } ?: "—")
                                MeasureCard("pH", ph?.let { "%.2f".format(it) } ?: "—")
                                MeasureCard("Désinfectant", orp?.let { "%.0f mV".format(it) } ?: "—")
                            }
                        }
                    }

                    if (conductivity != null || timestamp != null) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    conductivity?.let { Text("Conductivité : %.0f µS/cm".format(it)) }
                                    timestamp?.let { Text("Dernière mesure : $it", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Connexion distante", style = MaterialTheme.typography.titleLarge)
                                Text("Les identifiants restent saisis sur ce téléphone et ne sont pas ajoutés au dépôt GitHub.", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Email du compte Joey") }
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("Mot de passe") }
                                )
                                Button(onClick = ::connectRemote, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (loading) "Connexion…" else "Se connecter et charger les mesures")
                                }
                                Text(status, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
