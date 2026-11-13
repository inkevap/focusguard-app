package com.focusguard.ui.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.focusguard.model.EndPolicy
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WAIT_OPTIONS = listOf(15, 30, 60, 120).map { it to "$it min" }

/** El nombre corto de cada forma de terminar, para la fila de Bloquear ya. */
internal fun endPolicyLabel(policy: EndPolicy) = when (policy) {
    EndPolicy.ANYTIME -> "Cuando yo quiera"
    EndPolicy.TIME_ONLY -> "Solo al acabar"
    EndPolicy.CONDITIONS -> "Si se cumple algo"
}

/**
 * «Cómo se puede terminar» un Bloquear ya. Se decide antes de empezar: una vez en marcha no se cambia. Ayúdame y Dormir no pasan por aquí, nunca terminan antes.
 */
@Composable
internal fun EndPolicyCard(viewModel: SessionViewModel, modifier: Modifier = Modifier) {
    val policy = viewModel.endPolicy
    GroupStack(modifier) {
        FgRadioGroup(
            options = listOf(
                Triple(EndPolicy.ANYTIME, "Cuando yo quiera", "Con «Terminar» en Inicio (el modo estricto o las aprobaciones lo frenan)."),
                Triple(EndPolicy.TIME_ONLY, "Solo cuando acabe el tiempo", "No hay vuelta atrás, ni con una aprobación. Es el modo más firme."),
                Triple(EndPolicy.CONDITIONS, "Antes, si se cumple algo", "Con cualquiera de las condiciones que elijas, el bloqueo se levanta."),
            ),
            selected = policy,
            onSelect = viewModel::chooseEndPolicy,
            footer = "Lo decides ahora, antes de empezar. Después no se cambia.",
        )
        if (policy == EndPolicy.CONDITIONS) EndConditionsEditor(viewModel)
    }
}

@Composable
private fun EndConditionsEditor(viewModel: SessionViewModel) {
    val ends = viewModel.endConditions
    var placeOpen by remember { mutableStateOf(false) }
    var wifiOpen by remember { mutableStateOf(false) }
    var bluetoothOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val wait = viewModel.endWaitMinutes

    FgGroup(header = "Se levanta si", footer = "Lugar, Wi-Fi y Bluetooth necesitan sus permisos: sin ellos esas condiciones no se cumplen nunca.") {
        FgSwitchRow("Llego a un lugar", placeOpen || ends.place != null, {
            placeOpen = it
            if (!it) viewModel.clearEndPlace()
        }, subtitle = "Se levanta al entrar en el círculo que elijas.")
        if (placeOpen || ends.place != null) {
            ends.place?.let { fence ->
                FgCell {
                    Text("Lugar guardado. Si cambias de sitio, vuelve a pulsar «Usar mi ubicación actual».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipChoices(options = RADIUS_OPTIONS, selected = fence.radiusMeters, onSelect = viewModel::setEndRadius)
                }
            }
            FgActionRow("Usar mi ubicación actual", onClick = {
                notice = if (!viewModel.hasLocationPermission) "Falta el permiso de Ubicación (Protección > Permisos)."
                else if (!viewModel.useCurrentLocationAsEnd()) "Aún no hay posición. Abre un mapa un momento y vuelve a intentarlo."
                else null
            })
        }

        FgSwitchRow("Conecto el cargador", ends.charging, viewModel::setEndCharging, subtitle = "Se levanta al enchufar el teléfono.")

        FgSwitchRow("Me conecto a un Wi-Fi", wifiOpen || ends.wifiNames.isNotEmpty(), {
            wifiOpen = it
            if (!it) viewModel.clearEndWifi()
        }, subtitle = "Se levanta al conectarte a una de las redes que elijas.")
        if (wifiOpen || ends.wifiNames.isNotEmpty()) {
            ends.wifiNames.sorted().forEach { name -> RemovableFgRow(name, "Quitar la red $name") { viewModel.removeEndWifi(name) } }
            FgCell { AddNameRow("Nombre de la red", viewModel::addEndWifi) }
            FgActionRow("Usar la red actual", onClick = {
                val current = viewModel.currentWifi()
                if (current == null) notice = "No se pudo leer la red. Conéctate a una y comprueba el permiso de Ubicación."
                else { viewModel.addEndWifi(current); notice = null }
            })
        }

        FgSwitchRow("Conecto un dispositivo Bluetooth", bluetoothOpen || ends.bluetoothNames.isNotEmpty(), {
            bluetoothOpen = it
            if (!it) viewModel.clearEndBluetooth()
        }, subtitle = "Se levanta al conectar el coche, unos auriculares…")
        if (bluetoothOpen || ends.bluetoothNames.isNotEmpty()) {
            // Consultar los emparejados habla con el sistema: una vez y fuera del hilo principal.
            val paired by produceState(emptyList<String>(), viewModel.hasBluetoothPermission) { value = withContext(Dispatchers.IO) { viewModel.pairedBluetooth() } }
            ends.bluetoothNames.sorted().forEach { name -> RemovableFgRow(name, "Quitar el dispositivo $name") { viewModel.removeEndBluetooth(name) } }
            val toAdd = paired.filter { it !in ends.bluetoothNames }
            if (toAdd.isNotEmpty()) {
                FgCell {
                    Text("Emparejados: toca uno para añadirlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipChoices<String?>(options = toAdd.map { it to it }, selected = null, onSelect = { it?.let(viewModel::addEndBluetooth) })
                }
            }
            FgCell { AddNameRow("Nombre del dispositivo", viewModel::addEndBluetooth) }
        }

        FgSwitchRow(
            title = "Pasa un rato y pido aprobación",
            checked = wait != null,
            onCheckedChange = { viewModel.setEndWait(if (it) WAIT_OPTIONS[1].first else null) },
            subtitle = "Tras esa espera aparece «Terminar». Si tienes aprobaciones activadas, hará falta que te aprueben.",
        )
        if (wait != null) FgCell { ChipChoices(options = WAIT_OPTIONS, selected = wait, onSelect = viewModel::setEndWait) }
    }
    notice?.let { ErrorNote(it) }
}
