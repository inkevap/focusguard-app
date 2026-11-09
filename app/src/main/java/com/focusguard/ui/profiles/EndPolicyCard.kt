package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.model.EndPolicy
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.RadioRow
import com.focusguard.ui.components.RemovableRow
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WAIT_OPTIONS = listOf(15, 30, 60, 120).map { it to "$it min" }

/**
 * «Cómo se puede terminar» un Bloquear ya. Se decide antes de empezar: una vez en marcha no se cambia. Ayúdame y Dormir no pasan por aquí, nunca terminan antes.
 */
@Composable
internal fun EndPolicyCard(viewModel: SessionViewModel, modifier: Modifier = Modifier) {
    val policy = viewModel.endPolicy
    AppCard(modifier) {
        SectionTitle("Cómo se puede terminar")
        Hint("Lo decides ahora, antes de empezar. Después no se cambia.")
        RadioRow("Cuando yo quiera", "Con «Terminar» en Inicio (el modo estricto o las aprobaciones lo frenan).", policy == EndPolicy.ANYTIME) { viewModel.chooseEndPolicy(EndPolicy.ANYTIME) }
        RadioRow("Solo cuando acabe el tiempo", "No hay vuelta atrás, ni con una aprobación. Es el modo más firme.", policy == EndPolicy.TIME_ONLY) { viewModel.chooseEndPolicy(EndPolicy.TIME_ONLY) }
        RadioRow("Antes, si se cumple algo", "Con cualquiera de las condiciones que elijas, el bloqueo se levanta.", policy == EndPolicy.CONDITIONS) { viewModel.chooseEndPolicy(EndPolicy.CONDITIONS) }
        if (policy == EndPolicy.CONDITIONS) EndConditionsEditor(viewModel, Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

@Composable
private fun EndConditionsEditor(viewModel: SessionViewModel, modifier: Modifier = Modifier) {
    val ends = viewModel.endConditions
    var placeOpen by remember { mutableStateOf(false) }
    var wifiOpen by remember { mutableStateOf(false) }
    var bluetoothOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        SwitchRow("Llego a un lugar", "Se levanta al entrar en el círculo que elijas.", placeOpen || ends.place != null, {
            placeOpen = it
            if (!it) viewModel.clearEndPlace()
        })
        if (placeOpen || ends.place != null) {
            ends.place?.let { fence ->
                Hint("Lugar guardado. Si cambias de sitio, vuelve a pulsar «Usar mi ubicación actual».")
                ChipChoices(options = RADIUS_OPTIONS, selected = fence.radiusMeters, onSelect = viewModel::setEndRadius)
            }
            SecondaryButton(
                text = "Usar mi ubicación actual",
                onClick = {
                    notice = if (!viewModel.hasLocationPermission) "Falta el permiso de Ubicación (Protección > Permisos)."
                    else if (!viewModel.useCurrentLocationAsEnd()) "Aún no hay posición. Abre un mapa un momento y vuelve a intentarlo."
                    else null
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SwitchRow("Conecto el cargador", "Se levanta al enchufar el teléfono.", ends.charging, viewModel::setEndCharging)

        SwitchRow("Me conecto a un Wi-Fi", "Se levanta al conectarte a una de las redes que elijas.", wifiOpen || ends.wifiNames.isNotEmpty(), {
            wifiOpen = it
            if (!it) viewModel.clearEndWifi()
        })
        if (wifiOpen || ends.wifiNames.isNotEmpty()) {
            ends.wifiNames.sorted().forEach { name -> RemovableRow(name, "Quitar la red $name") { viewModel.removeEndWifi(name) } }
            AddNameRow("Nombre de la red", viewModel::addEndWifi)
            SecondaryButton(
                text = "Usar la red actual",
                onClick = {
                    val current = viewModel.currentWifi()
                    if (current == null) notice = "No se pudo leer la red. Conéctate a una y comprueba el permiso de Ubicación."
                    else { viewModel.addEndWifi(current); notice = null }
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SwitchRow("Conecto un dispositivo Bluetooth", "Se levanta al conectar el coche, unos auriculares…", bluetoothOpen || ends.bluetoothNames.isNotEmpty(), {
            bluetoothOpen = it
            if (!it) viewModel.clearEndBluetooth()
        })
        if (bluetoothOpen || ends.bluetoothNames.isNotEmpty()) {
            // Consultar los emparejados habla con el sistema: una vez y fuera del hilo principal.
            val paired by produceState(emptyList<String>(), viewModel.hasBluetoothPermission) { value = withContext(Dispatchers.IO) { viewModel.pairedBluetooth() } }
            ends.bluetoothNames.sorted().forEach { name -> RemovableRow(name, "Quitar el dispositivo $name") { viewModel.removeEndBluetooth(name) } }
            val toAdd = paired.filter { it !in ends.bluetoothNames }
            if (toAdd.isNotEmpty()) {
                Hint("Emparejados: toca uno para añadirlo.", Modifier.padding(top = 4.dp))
                ChipChoices<String?>(options = toAdd.map { it to it }, selected = null, onSelect = { it?.let(viewModel::addEndBluetooth) })
            }
            AddNameRow("Nombre del dispositivo", viewModel::addEndBluetooth)
        }

        val wait = viewModel.endWaitMinutes
        SwitchRow(
            title = "Pasa un rato y pido aprobación",
            subtitle = "Tras esa espera aparece «Terminar». Si tienes aprobaciones activadas, hará falta que te aprueben.",
            checked = wait != null,
            onCheckedChange = { viewModel.setEndWait(if (it) WAIT_OPTIONS[1].first else null) },
        )
        if (wait != null) ChipChoices(options = WAIT_OPTIONS, selected = wait, onSelect = viewModel::setEndWait, modifier = Modifier.padding(top = 4.dp))

        Hint("Lugar, Wi-Fi y Bluetooth necesitan sus permisos: sin ellos esas condiciones no se cumplen nunca.", Modifier.padding(top = 8.dp))
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    }
}
