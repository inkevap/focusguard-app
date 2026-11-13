package com.focusguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusguard.ui.components.IconBadge
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.icons.FgIcons

/** Puerta de entrada de la app cuando el PIN está activado. */
@Composable
fun PinGateScreen(verify: (String) -> Boolean, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val tryPin = { if (verify(pin)) onUnlock() else { wrong = true; pin = "" } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(FgIcons.Lock, size = 64.dp)
            Text("Escribe tu PIN", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            PinField("PIN", pin, { pin = it; wrong = false }, error = if (wrong) "PIN incorrecto. Prueba otra vez." else null)
            PrimaryButton("Entrar", tryPin, Modifier.fillMaxWidth(), enabled = pin.length >= 4)
        }
    }
}
