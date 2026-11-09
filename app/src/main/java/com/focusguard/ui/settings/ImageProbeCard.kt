package com.focusguard.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusguard.decision.AdaptiveSettings
import com.focusguard.decision.CaptureRisk
import com.focusguard.decision.CaptureTiming
import com.focusguard.decision.ImageAlerts
import com.focusguard.decision.ImageSensitivity
import com.focusguard.decision.VigilanceScope
import com.focusguard.decision.VigilanceSettings
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.icons.FgIcons
import com.focusguard.vision.ImageProbeSnapshot
import kotlin.math.roundToInt

/**
 * Registro experimental de la detección de imágenes en pantalla (apagado de serie). Solo mide: captura la pantalla cada cierto tiempo, la puntúa
 * con un modelo en el teléfono y cuenta los resultados. No bloquea, no avisa y no guarda ninguna imagen.
 */
@Composable
internal fun ImageProbeCard(
    enabled: Boolean,
    stats: ImageProbeSnapshot,
    sensitivity: Int,
    notifyMinutes: Int,
    vigilance: VigilanceSettings,
    adaptive: AdaptiveSettings,
    onChange: (Boolean) -> Unit,
    onSensitivity: (Int) -> Unit,
    onNotifyMinutes: (Int) -> Unit,
    onVigilance: (VigilanceSettings) -> Unit,
    onAdaptive: (AdaptiveSettings) -> Unit,
    onReset: () -> Unit,
) {
    AppCard {
        SectionTitle("Detección de imágenes (experimental)", FgIcons.Shield)
        Hint("Con la detección encendida, al entrar en una app o web se captura la pantalla lo más seguido que se puede durante ${CaptureTiming().holdMs / 60_000} minutos, y después cada vez más espaciado (hasta ${CaptureTiming().maxMs / 1000} segundos) mientras todo salga seguro. Dos modelos en este teléfono (GantMan y NudeNet) la puntúan. Lo explícito se bloquea; lo sugerente te avisa con una notificación que ofrece bloquearlo. No se guarda ninguna imagen y nada sale del teléfono.")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Text("Necesita Android 11 o más.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        } else {
            SwitchRow("Detectar imágenes", "Apagada de serie. Apagarla pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto.", enabled, onChange, Modifier.padding(top = 8.dp))
            NotifyMinutes(notifyMinutes, onNotifyMinutes)
            VigilanceControls(vigilance, onVigilance)
            AdaptiveControls(adaptive, onAdaptive)
            SensitivitySlider(sensitivity, onSensitivity)
        }
        if (enabled) {
            if (stats.nudenetMissing) {
                Text("NudeNet no está en esta versión: puntúa solo GantMan, que es menos preciso.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (stats.modelMissing) {
                Text("Falta el modelo: no está incluido en esta versión.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Text(summary(stats), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            SecondaryButton("Poner los contadores a cero", onReset, Modifier.padding(top = 8.dp))
        }
    }
}

internal fun summary(stats: ImageProbeSnapshot): String = buildString {
    stats.rhythmRisk?.let { append("Ahora: una captura cada ${"%.1f".format(stats.rhythmMs / 1000.0)} s (${riskLabel(it)})\n") }
    if (stats.vigilanceLeftMs > 0) append("Vigilancia activa: quedan ${stats.vigilanceLeftMs / 60_000} min\n")
    if (stats.adaptiveLevel > 0) append("Modo dinámico: nivel ${stats.adaptiveLevel} (${stats.adaptiveIncidents} bloqueos en el periodo) · sensibilidad aplicada: ${ImageSensitivity.label(stats.effectiveSensitivity)}\n")
    append("Intentos: ${stats.attempts} · puntuadas: ${stats.scored}")
    append("\nSaltadas: ${stats.skipped} · sin cambios: ${stats.unchanged} · fallidas: ${stats.failed}")
    stats.lastError?.let { append(" (último error $it)") }
    if (stats.scored > 0) {
        append("\nCaptura: ${stats.averageCaptureMs} ms de media · modelo: ${stats.averageScoreMs} ms de media, ${stats.maxScoreMs} ms el máximo")
        append("\nExplícitas: ${stats.flagged} · sugerentes: ${stats.suggestive} · dudosas: ${stats.doubtful}")
        append("\nBloqueos: ${stats.blocked} · avisos enviados: ${stats.offered}")
        append("\nPuntuaciones por tramo (0 a 1): ${stats.buckets.joinToString(" ")}")
    }
}

private fun riskLabel(risk: CaptureRisk) = when (risk) {
    CaptureRisk.LOW -> "navegador, más espaciado"
    CaptureRisk.NORMAL -> "ritmo normal"
    CaptureRisk.HIGH -> "redes o vídeo, más seguido"
}

/**
 * De «Mínima» a «Máxima». Subirla es libre y se aplica al soltar; bajarla debilita la protección y pasa por el compañero, el modo estricto o las
 * aprobaciones (si no se concede, el control vuelve a su sitio y se explica por qué).
 */
@Composable
private fun SensitivitySlider(sensitivity: Int, onSensitivity: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: sensitivity.toFloat()
    Text("Sensibilidad: ${ImageSensitivity.label(shown.roundToInt())}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Slider(
        value = shown,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSensitivity(it.roundToInt()) }
            dragging = null
        },
        valueRange = ImageSensitivity.MIN.toFloat()..ImageSensitivity.MAX.toFloat(),
        steps = ImageSensitivity.MAX - ImageSensitivity.MIN - 1,
        modifier = Modifier.semantics { contentDescription = "Sensibilidad de la detección de imágenes" },
    )
    Hint("Más sensibilidad marca más, pero también avisa de más. Subirla es libre; bajarla pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto. Los valores son provisionales.")
}

/** Cada cuántos minutos como mucho se ofrece bloquear algo sugerente. Solo regula los avisos: lo explícito se bloquea siempre. */
@Composable
private fun NotifyMinutes(minutes: Int, onChange: (Int) -> Unit) {
    Text("Avisos de lo sugerente: como mucho uno cada…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    ChipChoices(options = ImageAlerts.OPTIONS.map { it to "$it min" }, selected = minutes, onSelect = onChange, modifier = Modifier.padding(top = 4.dp))
    Hint("Si ya salió un aviso en ese plazo, no sale otro. Lo explícito se bloquea siempre, sin avisar.")
}

/**
 * La vigilancia tras lo explícito: si aparece contenido explícito es probable que sea un momento de impulso, así que durante un tiempo se captura más seguido
 * lo que puede ser un riesgo. Vigilar más es libre; vigilar menos pasa por el compañero, el modo estricto o las aprobaciones.
 */
@Composable
private fun VigilanceControls(settings: VigilanceSettings, onChange: (VigilanceSettings) -> Unit) {
    Text("Vigilancia tras lo explícito", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Hint("Si se detecta contenido explícito, es probable que sea un momento de impulso: durante un tiempo se captura más seguido lo que puede ser un riesgo. Vigilar más es libre; vigilar menos pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto.")
    SwitchRow("Vigilar más tras lo explícito", "Se renueva cada vez que se detecta algo.", settings.enabled, { onChange(settings.copy(enabled = it)) }, Modifier.padding(top = 4.dp))
    if (settings.enabled) {
        Text("Cuánto dura", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChipChoices(options = VigilanceSettings.OPTIONS_MINUTES.map { it to "$it min" }, selected = settings.minutes, onSelect = { onChange(settings.copy(minutes = it)) })
        Text("Cuánto más seguido", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChipChoices(
            options = listOf(1 to "Suave (30 % más)", 2 to "Media (el doble)", 3 to "Alta (el triple)"),
            selected = settings.intensity,
            onSelect = { onChange(settings.copy(intensity = it)) },
        )
        Text("Qué se vigila más seguido", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChipChoices(
            options = listOf(VigilanceScope.RISKY to "Lo de riesgo: redes, vídeo y webs", VigilanceScope.ALL to "Todo"),
            selected = settings.scope,
            onSelect = { onChange(settings.copy(scope = it)) },
        )
        SwitchRow("También tras lo sugerente", "Por defecto solo tras lo explícito.", settings.afterSuggestive, { onChange(settings.copy(afterSuggestive = it)) }, Modifier.padding(top = 8.dp))
    }
}

/**
 * El modo dinámico: la detección se adapta a cómo se comporta el usuario. Cuantas más veces se bloquea algo explícito en el periodo, más sube el nivel de alerta
 * (se captura más seguido, el enfriamiento dura más y la sensibilidad sube); sin incidentes, baja solo. Adaptar más es libre; adaptar menos pasa por el compañero,
 * el modo estricto o las aprobaciones.
 */
@Composable
private fun AdaptiveControls(settings: AdaptiveSettings, onChange: (AdaptiveSettings) -> Unit) {
    Text("Modo dinámico", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Hint("Cuantas más veces se bloquea algo explícito en el periodo, más sube el nivel de alerta: 1 bloqueo, nivel 1; 3, nivel 2; 6, nivel 3. Cada nivel captura un 20 % más seguido, alarga el enfriamiento un 50 % y sube la sensibilidad un paso. Cuando pasa el tiempo sin incidentes, baja solo. Adaptar más es libre; adaptar menos pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto.")
    SwitchRow("Adaptarse a mi comportamiento", "Se ve en la tarjeta como «Modo dinámico: nivel N».", settings.enabled, { onChange(settings.copy(enabled = it)) }, Modifier.padding(top = 4.dp))
    if (settings.enabled) {
        Text("Cuánto tiempo cuenta un bloqueo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChipChoices(
            options = AdaptiveSettings.OPTIONS_HOURS.map { it to if (it < 24) "$it h" else "${it / 24} ${if (it == 24) "día" else "días"}" },
            selected = settings.windowHours,
            onSelect = { onChange(settings.copy(windowHours = it)) },
        )
        Text("Hasta qué nivel puede subir", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChipChoices(
            options = (1..AdaptiveSettings.MAX_LEVEL).map { it to "Nivel $it" },
            selected = settings.maxLevel,
            onSelect = { onChange(settings.copy(maxLevel = it)) },
        )
    }
}
