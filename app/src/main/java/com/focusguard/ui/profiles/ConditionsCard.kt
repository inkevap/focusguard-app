package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.focusguard.decision.Conditions
import com.focusguard.model.ConditionMode
import com.focusguard.model.Profile
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.SecondaryButton

internal val RADIUS_OPTIONS = listOf(50, 100, 200, 500).map { it to "$it m" }

/** Condiciones extra además del horario: Wi-Fi, Bluetooth, cargador y lugar. Todo se evalúa en el teléfono. */
@Composable
internal fun ConditionsCard(
    profile: Profile,
    hasLocationPermission: Boolean,
    onMode: (ConditionMode) -> Unit,
    onAddWifi: (String) -> Unit,
    onRemoveWifi: (String) -> Unit,
    readCurrentWifi: () -> String?,
    hasBluetoothPermission: Boolean,
    pairedBluetooth: () -> List<String>,
    onAddBluetooth: (String) -> Unit,
    onRemoveBluetooth: (String) -> Unit,
    onCharging: (Boolean) -> Unit,
    useCurrentLocation: () -> Boolean,
    onRadius: (Int) -> Unit,
    onInverted: (Boolean) -> Unit,
    onClearGeofence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var notice by remember { mutableStateOf<String?>(null) }
    val fence = profile.geofence

    GroupStack(modifier) {
        FgNote(
            "Además del horario, puedes activar el perfil solo con una red Wi-Fi, con un dispositivo Bluetooth conectado, mientras carga o en un lugar.",
            Modifier.padding(horizontal = 16.dp),
        )
        if (!hasLocationPermission) ErrorNote("Wi-Fi y lugar necesitan el permiso de Ubicación. Se pide en Protección > Permisos.")
        if (Conditions.count(profile) >= 2) {
            FgRadioGroup(
                options = listOf(
                    Triple(ConditionMode.ALL, "Se cumplen todas", null),
                    Triple(ConditionMode.ANY, "Se cumple alguna", null),
                ),
                selected = profile.conditionMode,
                onSelect = onMode,
                header = "Se activa cuando",
            )
        }

        FgGroup(header = "Red Wi-Fi") {
            remember(profile.wifiNames) { profile.wifiNames.sorted() }.forEach { name -> RemovableFgRow(name, "Quitar la red $name") { onRemoveWifi(name) } }
            FgCell { AddNameRow("Nombre de la red", onAddWifi) }
            FgActionRow(
                "Usar la red actual",
                onClick = {
                    val current = readCurrentWifi()
                    if (current == null) notice = "No se pudo leer la red. Conéctate a una y comprueba el permiso de Ubicación."
                    else { onAddWifi(current); notice = null }
                },
            )
        }

        BluetoothSection(profile.bluetoothNames, hasBluetoothPermission, pairedBluetooth, onAddBluetooth, onRemoveBluetooth)

        FgGroup(footer = "Útil para la noche: se activa al enchufar el teléfono.") {
            FgSwitchRow("Solo mientras carga", profile.requireCharging, onCharging)
        }

        FgGroup(
            header = "Lugar",
            footer = if (fence == null) "Elige dónde estás ahora y el perfil se activará dentro (o fuera) de ese círculo."
            else "Lugar guardado. Si cambias de sitio, vuelve a pulsar «Usar mi ubicación actual».",
        ) {
            if (fence != null) {
                FgCell {
                    Text("Radio", style = MaterialTheme.typography.bodyLarge)
                    ChipChoices(options = RADIUS_OPTIONS, selected = fence.radiusMeters, onSelect = onRadius)
                }
                FgSwitchRow("Activar fuera del lugar", fence.inverted, onInverted, subtitle = "En vez de dentro del círculo, se activa al salir de él.")
            }
            FgActionRow(
                "Usar mi ubicación actual",
                onClick = {
                    notice = if (!hasLocationPermission) "Falta el permiso de Ubicación (Protección > Permisos)."
                    else if (!useCurrentLocation()) "Aún no hay posición. Abre un mapa un momento y vuelve a intentarlo."
                    else null
                },
            )
            if (fence != null) FgActionRow("Quitar el lugar", onClearGeofence, danger = true)
        }
        notice?.let { ErrorNote(it) }
    }
}

/** Un campo de nombre con su botón «Añadir»; el texto escrito se queda aquí y se vacía al añadir. */
@Composable
internal fun AddNameRow(label: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
        SecondaryButton("Añadir", { onAdd(text); text = "" }, enabled = text.isNotBlank())
    }
}

/** Dispositivos Bluetooth (auriculares, el coche…): el perfil se activa mientras alguno esté conectado. */
@Composable
private fun BluetoothSection(
    names: Set<String>,
    hasPermission: Boolean,
    pairedBluetooth: () -> List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    // Consultar los emparejados habla con el sistema: una vez y fuera del hilo principal, no cada vez que cambia la lista.
    val allPaired by produceState(emptyList<String>(), hasPermission) { value = withContext(Dispatchers.IO) { pairedBluetooth() } }
    val paired = allPaired.filter { it !in names }
    FgGroup(header = "Bluetooth", footer = if (paired.isNotEmpty()) "Emparejados: toca uno para añadirlo." else null) {
        names.sorted().forEach { name -> RemovableFgRow(name, "Quitar el dispositivo $name") { onRemove(name) } }
        if (paired.isNotEmpty()) FgCell { ChipChoices<String?>(options = paired.map { it to it }, selected = null, onSelect = { it?.let(onAdd) }) }
        FgCell { AddNameRow("Nombre del dispositivo", onAdd) }
    }
    if (!hasPermission) ErrorNote("Bluetooth necesita su permiso. Se pide en Protección > Permisos; sin él, esta condición se da por cumplida.")
}
