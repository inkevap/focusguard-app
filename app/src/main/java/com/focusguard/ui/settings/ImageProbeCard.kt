package com.focusguard.ui.settings

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.AdaptiveSettings
import com.focusguard.decision.CaptureRisk
import com.focusguard.decision.CaptureTiming
import com.focusguard.decision.ImageAlerts
import com.focusguard.decision.ImageSensitivity
import com.focusguard.decision.VigilanceScope
import com.focusguard.decision.VigilanceSettings
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.profiles.ErrorNote
import com.focusguard.ui.profiles.GroupStack
import com.focusguard.vision.ImageProbeSnapshot
import kotlin.math.roundToInt

/**
 * Detección de imágenes en pantalla. Captura la pantalla cada cierto tiempo, la puntúa con modelos en el teléfono y bloquea o avisa. No guarda
 * ninguna imagen. Vive en Protección; comparte el modelo de vista de Ajustes porque ahí están sus ajustes.
 */
@Composable
fun ImageProbeScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val enabled by viewModel.imageProbe.collectAsStateWithLifecycle()
    val stats by viewModel.imageProbeSnapshot.collectAsStateWithLifecycle()
    val sensitivity by viewModel.imageSensitivity.collectAsStateWithLifecycle()
    val notifyMinutes by viewModel.imageNotifyMinutes.collectAsStateWithLifecycle()
    val vigilance by viewModel.imageVigilance.collectAsStateWithLifecycle()
    val adaptive by viewModel.imageAdaptive.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    ImageProbeContent(
        enabled, stats, sensitivity, notifyMinutes, vigilance, adaptive,
        viewModel::setImageProbe, viewModel::setImageSensitivity, viewModel::setImageNotifyMinutes, viewModel::setImageVigilance, viewModel::setImageAdaptive,
        viewModel::resetImageProbe,
        onBack = onBack, snackbar = snackbar,
    )
}

private enum class ImagePage(val title: String) {
    ALERTS("Avisos de lo sugerente"),
    VIGILANCE("Vigilancia tras lo explícito"),
    ADAPTIVE("Modo dinámico"),
    COUNTERS("Contadores"),
}

@Composable
internal fun ImageProbeContent(
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
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
) {
    var page by rememberSaveable { mutableStateOf<ImagePage?>(null) }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Detección de imágenes", onBack = onBack, snackbar = snackbar) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    item { ErrorNote("Necesita Android 11 o más.") }
                    return@FgScreen
                }
                item {
                    FgGroup(
                        footer = "Experimental. Al entrar en una app o web se captura la pantalla lo más seguido que se puede durante ${CaptureTiming().holdMs / 60_000} minutos, " +
                            "y después cada vez más espaciado (hasta ${CaptureTiming().maxMs / 1000} segundos) mientras todo salga seguro. Dos modelos en este teléfono (GantMan y NudeNet) la puntúan. " +
                            "Lo explícito se bloquea; lo sugerente te avisa con una notificación que ofrece bloquearlo. No se guarda ninguna imagen y nada sale del teléfono.",
                    ) {
                        FgSwitchRow(
                            "Detectar imágenes", enabled, onChange,
                            subtitle = "Apagada de serie. Apagarla pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto.",
                        )
                    }
                }
                if (enabled) {
                    if (stats.nudenetMissing) item { ErrorNote("NudeNet no está en esta versión: puntúa solo GantMan, que es menos preciso.") }
                    if (stats.modelMissing) item { ErrorNote("Falta el modelo: no está incluido en esta versión.") }
                }
                item { SensitivityGroup(sensitivity, onSensitivity) }
                item {
                    FgGroup {
                        FgRow("Avisos de lo sugerente", value = "Uno cada $notifyMinutes min", onClick = { page = ImagePage.ALERTS })
                        FgRow("Vigilancia tras lo explícito", value = if (vigilance.enabled) "Sí" else "No", onClick = { page = ImagePage.VIGILANCE })
                        FgRow("Modo dinámico", value = if (adaptive.enabled) "Sí" else "No", onClick = { page = ImagePage.ADAPTIVE })
                        if (enabled) FgRow("Contadores", value = "${stats.attempts} intentos", onClick = { page = ImagePage.COUNTERS })
                    }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, onBack = { page = null }, snackbar = snackbar) {
                item {
                    when (current) {
                        ImagePage.ALERTS -> AlertsGroup(notifyMinutes, onNotifyMinutes)
                        ImagePage.VIGILANCE -> VigilanceGroups(vigilance, onVigilance)
                        ImagePage.ADAPTIVE -> AdaptiveGroups(adaptive, onAdaptive)
                        ImagePage.COUNTERS -> CountersGroups(stats, onReset)
                    }
                }
            }
        },
    )
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

@Composable
private fun CountersGroups(stats: ImageProbeSnapshot, onReset: () -> Unit) {
    GroupStack {
        FgGroup { FgCell { Text(summary(stats), style = MaterialTheme.typography.bodyMedium) } }
        FgGroup { FgActionRow("Poner los contadores a cero", onReset, danger = true) }
    }
}

/**
 * De «Mínima» a «Máxima». Subirla es libre y se aplica al soltar; bajarla debilita la protección y pasa por el compañero, el modo estricto o las
 * aprobaciones (si no se concede, el control vuelve a su sitio y se explica por qué).
 */
@Composable
private fun SensitivityGroup(sensitivity: Int, onSensitivity: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: sensitivity.toFloat()
    FgGroup(
        header = "Sensibilidad: ${ImageSensitivity.label(shown.roundToInt())}",
        footer = "Más sensibilidad marca más, pero también avisa de más. Subirla es libre; bajarla pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto. Los valores son provisionales.",
    ) {
        FgCell {
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
        }
    }
}

/** Cada cuántos minutos como mucho se ofrece bloquear algo sugerente. Solo regula los avisos: lo explícito se bloquea siempre. */
@Composable
private fun AlertsGroup(minutes: Int, onChange: (Int) -> Unit) {
    FgGroup(
        header = "Como mucho uno cada…",
        footer = "Si ya salió un aviso en ese plazo, no sale otro. Lo explícito se bloquea siempre, sin avisar.",
    ) {
        FgCell { ChipChoices(options = ImageAlerts.OPTIONS.map { it to "$it min" }, selected = minutes, onSelect = onChange) }
    }
}

/**
 * La vigilancia tras lo explícito: si aparece contenido explícito es probable que sea un momento de impulso, así que durante un tiempo se captura más seguido
 * lo que puede ser un riesgo. Vigilar más es libre; vigilar menos pasa por el compañero, el modo estricto o las aprobaciones.
 */
@Composable
private fun VigilanceGroups(settings: VigilanceSettings, onChange: (VigilanceSettings) -> Unit) {
    GroupStack {
        FgGroup(
            footer = "Si se detecta contenido explícito, es probable que sea un momento de impulso: durante un tiempo se captura más seguido lo que puede ser un riesgo. " +
                "Vigilar más es libre; vigilar menos pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto.",
        ) {
            FgSwitchRow("Vigilar más tras lo explícito", settings.enabled, { onChange(settings.copy(enabled = it)) }, subtitle = "Se renueva cada vez que se detecta algo.")
        }
        if (settings.enabled) {
            FgGroup(header = "Cuánto dura") {
                FgCell { ChipChoices(options = VigilanceSettings.OPTIONS_MINUTES.map { it to "$it min" }, selected = settings.minutes, onSelect = { onChange(settings.copy(minutes = it)) }) }
            }
            FgGroup(header = "Cuánto más seguido") {
                FgCell {
                    ChipChoices(
                        options = listOf(1 to "Suave (30 % más)", 2 to "Media (el doble)", 3 to "Alta (el triple)"),
                        selected = settings.intensity,
                        onSelect = { onChange(settings.copy(intensity = it)) },
                    )
                }
            }
            FgGroup(header = "Qué se vigila más seguido") {
                FgCell {
                    ChipChoices(
                        options = listOf(VigilanceScope.RISKY to "Lo de riesgo: redes, vídeo y webs", VigilanceScope.ALL to "Todo"),
                        selected = settings.scope,
                        onSelect = { onChange(settings.copy(scope = it)) },
                    )
                }
            }
            FgGroup {
                FgSwitchRow("También tras lo sugerente", settings.afterSuggestive, { onChange(settings.copy(afterSuggestive = it)) }, subtitle = "Por defecto solo tras lo explícito.")
            }
        }
    }
}

/**
 * El modo dinámico: la detección se adapta a cómo se comporta el usuario. Cuantas más veces se bloquea algo explícito en el periodo, más sube el nivel de alerta
 * (se captura más seguido, el enfriamiento dura más y la sensibilidad sube); sin incidentes, baja solo. Adaptar más es libre; adaptar menos pasa por el compañero,
 * el modo estricto o las aprobaciones.
 */
@Composable
private fun AdaptiveGroups(settings: AdaptiveSettings, onChange: (AdaptiveSettings) -> Unit) {
    GroupStack {
        FgGroup(
            footer = "Cuantas más veces se bloquea algo explícito en el periodo, más sube el nivel de alerta: 1 bloqueo, nivel 1; 3, nivel 2; 6, nivel 3. " +
                "Cada nivel captura un 20 % más seguido, alarga el enfriamiento un 50 % y sube la sensibilidad un paso. Cuando pasa el tiempo sin incidentes, baja solo. " +
                "Adaptar más es libre; adaptar menos pide aprobación de tu compañero si lo tienes, y no se puede con el modo estricto.",
        ) {
            FgSwitchRow("Adaptarse a mi comportamiento", settings.enabled, { onChange(settings.copy(enabled = it)) }, subtitle = "Se ve en los contadores como «Modo dinámico: nivel N».")
        }
        if (settings.enabled) {
            FgGroup(header = "Cuánto tiempo cuenta un bloqueo") {
                FgCell {
                    ChipChoices(
                        options = AdaptiveSettings.OPTIONS_HOURS.map { it to if (it < 24) "$it h" else "${it / 24} ${if (it == 24) "día" else "días"}" },
                        selected = settings.windowHours,
                        onSelect = { onChange(settings.copy(windowHours = it)) },
                    )
                }
            }
            FgGroup(header = "Hasta qué nivel puede subir") {
                FgCell {
                    ChipChoices(
                        options = (1..AdaptiveSettings.MAX_LEVEL).map { it to "Nivel $it" },
                        selected = settings.maxLevel,
                        onSelect = { onChange(settings.copy(maxLevel = it)) },
                    )
                }
            }
        }
    }
}
