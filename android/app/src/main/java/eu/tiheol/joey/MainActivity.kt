package eu.tiheol.joey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JoeyApp() }
    }
}

@Composable
fun JoeyApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val unfolded = maxWidth >= 600.dp
                if (unfolded) JoeyWideDashboard() else JoeyCompactDashboard()
            }
        }
    }
}

@Composable
private fun JoeyCompactDashboard() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("JOEY", style = MaterialTheme.typography.headlineLarge)
        Text("Mon Spa", style = MaterialTheme.typography.titleLarge)
        StatusCard("Température", "35,1 °C")
        StatusCard("pH", "7,4")
        StatusCard("Désinfectant", "650 mV")
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Connecter le Joey") }
    }
}

@Composable
private fun JoeyWideDashboard() {
    Row(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("JOEY", style = MaterialTheme.typography.displaySmall)
            Text("Mon Spa", style = MaterialTheme.typography.headlineMedium)
            Text("Eau optimale", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { }) { Text("Connecter le Joey") }
        }
        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusCard("Température", "35,1 °C")
            StatusCard("pH", "7,4")
            StatusCard("Désinfectant", "650 mV")
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
