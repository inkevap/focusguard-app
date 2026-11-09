package com.focusguard.ui.settings

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.focusguard.appLabel
import com.focusguard.data.TrustedClock
import com.focusguard.lock.LockScreenContent
import com.focusguard.lock.LockScreenView
import com.focusguard.model.BlockReason
import com.focusguard.model.LockPalette
import com.focusguard.model.LockPalettes
import com.focusguard.model.LockStyle
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.profiles.AppPickerDialog
import org.koin.compose.koinInject

/** Personalizar la pantalla de bloqueo (M1, M2, M4) con vista previa. Se guarda al pulsar «Guardar». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockStyleCard(saved: LockStyle, onSave: (LockStyle) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var picking by remember { mutableStateOf(false) }
    var pickedApps by remember { mutableStateOf<Set<String>?>(null) }
    val context = LocalContext.current
    val labels = remember(draft.appMessages.keys) { draft.appMessages.keys.associateWith { context.appLabel(it) } }

    AppCard {
        SectionTitle("Pantalla de bloqueo", FgIcons.Lock)
        Hint("Así ves el bloqueo cuando algo se cierra. Un mensaje tuyo suele ayudar más que el de siempre.")

        LockPreview(draft)

        MessageField("Mensaje propio (opcional)", draft.message, { draft = draft.copy(message = it) }, Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = draft.emoji,
            onValueChange = { draft = draft.copy(emoji = it.take(LockStyle.MAX_EMOJI)) },
            label = { Text("Emoji (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Text("Color", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            LockPalettes.all.forEach { palette ->
                PaletteSwatch(palette, palette.id == draft.paletteId) { draft = draft.copy(paletteId = palette.id) }
            }
        }

        SwitchRow(
            title = "Frases motivadoras",
            subtitle = "Una frase distinta cada vez que se bloquea algo.",
            checked = draft.rotating,
            onCheckedChange = { draft = draft.copy(rotating = it) },
            modifier = Modifier.padding(top = 8.dp),
        )

        MessageField("Mensaje al intentar apagar o desinstalar la app (opcional)", draft.guardMessage, { draft = draft.copy(guardMessage = it) }, Modifier.padding(top = 16.dp))
        Hint("Se suma al motivo cuando el modo estricto cierra los ajustes de FocusGuard.")

        Text("Mensaje para una app", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        Hint("Por ejemplo, un recordatorio distinto cuando intentas abrir una app en concreto.")
        draft.appMessages.forEach { (pkg, message) ->
            val label = labels[pkg] ?: pkg
            RemovableAppRow(pkg, "$label: $message", "Quitar el mensaje de $label", { draft = draft.copy(appMessages = draft.appMessages - pkg) })
        }
        SecondaryButton("Añadir mensaje para una app", { picking = true }, Modifier.padding(top = 8.dp))

        PrimaryButton("Guardar", { onSave(draft) }, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = draft != saved)
    }

    if (picking) {
        AppPickerDialog(selected = emptySet(), onConfirm = { picking = false; pickedApps = it.takeIf { apps -> apps.isNotEmpty() } }, onDismiss = { picking = false })
    }
    pickedApps?.let { apps ->
        AppMessageDialog(
            title = "Mensaje para ${if (apps.size == 1) labels[apps.first()] ?: context.appLabel(apps.first()) else "${apps.size} apps"}",
            onAdd = { text -> draft = draft.copy(appMessages = draft.appMessages + apps.associateWith { text }); pickedApps = null },
            onDismiss = { pickedApps = null },
        )
    }
}

/** Misma vista que ve quien abre una app bloqueada, a tamaño reducido. */
@Composable
private fun LockPreview(style: LockStyle) {
    val context = LocalContext.current
    val clock: TrustedClock = koinInject()
    val content = remember(style) {
        LockScreenContent.of(
            context = context, reason = BlockReason.PROFILE_APP, target = "", availableAt = null,
            displayName = "Instagram", style = style, now = clock.now(), motivationIndex = 0,
        )
    }
    key(content) {
        AndroidView(
            factory = { LockScreenView(it, content) {} },
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .semantics { contentDescription = "Vista previa de la pantalla de bloqueo" },
        )
    }
}

@Composable
private fun PaletteSwatch(palette: LockPalette, chosen: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(palette.background))
            .border(BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Color ${palette.label}${if (chosen) ", elegido" else ""}" },
    ) {
        if (chosen) Icon(FgIcons.Check, contentDescription = null, tint = Color(palette.text))
    }
}

/** Pide el texto del mensaje para las apps elegidas. */
@Composable
private fun AppMessageDialog(title: String, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { MessageField("Mensaje", text, { text = it }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text.trim()) }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MessageField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(LockStyle.MAX_MESSAGE)) },
        label = { Text(label) },
        supportingText = { Text("${value.length}/${LockStyle.MAX_MESSAGE}") },
        minLines = 2,
        modifier = modifier.fillMaxWidth(),
    )
}
