package com.focusguard.ui

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.PrimaryButton

/** Puerta de entrada de la app cuando el PIN está activado. */
@Composable
fun PinGateScreen(verify: (String) -> Boolean, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val tryPin = { if (verify(pin)) onUnlock() else { wrong = true; pin = "" } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(FgIcons.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Escribe tu PIN", style = MaterialTheme.typography.headlineMedium)
            PinField("PIN", pin, { pin = it; wrong = false }, error = if (wrong) "PIN incorrecto. Prueba otra vez." else null)
            PrimaryButton("Entrar", tryPin, Modifier.fillMaxWidth(), enabled = pin.length >= 4)
        }
    }
}
