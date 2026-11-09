package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.focusguard.model.GeoFence
import com.focusguard.model.Profile
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.RemovableRow
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow

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

    AppCard(modifier) {
        SectionTitle("Cuándo más", FgIcons.Sliders)
        Hint("Además del horario, puedes activar el perfil solo con una red Wi-Fi, con un dispositivo Bluetooth conectado, mientras carga o en un lugar.")
        if (!hasLocationPermission) {
            Text(
                "Wi-Fi y lugar necesitan el permiso de Ubicación. Se pide en Protección > Permisos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (Conditions.count(profile) >= 2) {
            Text("Se activa cuando", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            ChipChoices(
                options = listOf(ConditionMode.ALL to "Se cumplen todas", ConditionMode.ANY to "Se cumple alguna"),
                selected = profile.conditionMode,
                onSelect = onMode,
            )
        }

        Text("Red Wi-Fi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        remember(profile.wifiNames) { profile.wifiNames.sorted() }.forEach { name -> RemovableRow(name, "Quitar la red $name") { onRemoveWifi(name) } }
        AddNameRow("Nombre de la red", onAddWifi)
        SecondaryButton(
            text = "Usar la red actual",
            onClick = {
                val current = readCurrentWifi()
                if (current == null) notice = "No se pudo leer la red. Conéctate a una y comprueba el permiso de Ubicación."
                else { onAddWifi(current); notice = null }
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        BluetoothSection(profile.bluetoothNames, hasBluetoothPermission, pairedBluetooth, onAddBluetooth, onRemoveBluetooth)

        SwitchRow(
            title = "Solo mientras carga",
            subtitle = "Útil para la noche: se activa al enchufar el teléfono.",
            checked = profile.requireCharging,
            onCheckedChange = onCharging,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text("Lugar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        if (fence == null) {
            Hint("Elige dónde estás ahora y el perfil se activará dentro (o fuera) de ese círculo.")
        } else {
            Hint("Lugar guardado. Si cambias de sitio, vuelve a pulsar «Usar mi ubicación actual».")
            Text("Radio", style = MaterialTheme.typography.bodyMedium)
            ChipChoices(options = RADIUS_OPTIONS, selected = fence.radiusMeters, onSelect = onRadius)
            SwitchRow(
                title = "Activar fuera del lugar",
                subtitle = "En vez de dentro del círculo, se activa al salir de él.",
                checked = fence.inverted,
                onCheckedChange = onInverted,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Usar mi ubicación actual",
                onClick = {
                    notice = if (!hasLocationPermission) "Falta el permiso de Ubicación (Protección > Permisos)."
                    else if (!useCurrentLocation()) "Aún no hay posición. Abre un mapa un momento y vuelve a intentarlo."
                    else null
                },
                modifier = Modifier.weight(1f),
            )
            if (fence != null) SecondaryButton("Quitar", onClearGeofence)
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** Un campo de nombre con su botón «Añadir»; el texto escrito se queda aquí y se vacía al añadir. */
@Composable
internal fun AddNameRow(label: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Text("Bluetooth", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    names.sorted().forEach { name -> RemovableRow(name, "Quitar el dispositivo $name") { onRemove(name) } }
    if (!hasPermission) {
        Text(
            "Bluetooth necesita su permiso. Se pide en Protección > Permisos; sin él, esta condición se da por cumplida.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (paired.isNotEmpty()) {
        Hint("Emparejados: toca uno para añadirlo.", Modifier.padding(top = 4.dp))
        ChipChoices<String?>(options = paired.map { it to it }, selected = null, onSelect = { it?.let(onAdd) })
    }
    AddNameRow("Nombre del dispositivo", onAdd)
}
