package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focusguard.decision.ScheduleConflicts
import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.LimitScope
import com.focusguard.model.ProfileKind
import org.koin.androidx.compose.koinViewModel
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.AppCheckRow
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.components.RemovableRow
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.DayCircles
import com.focusguard.ui.components.DayRangeBar
import com.focusguard.ui.components.TimeButton
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.NumberField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.RadioGroup
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.parseMinutes
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileEditScreen(viewModel: ProfileEditViewModel, profileId: Long, onDone: () -> Unit, onPreview: (Long) -> Unit) {
    LaunchedEffect(profileId) { viewModel.load(profileId) }
    val profile = viewModel.profile
    var showAppPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var limitInvalid by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showBlockMe by remember { mutableStateOf(false) }
    val others by viewModel.others.collectAsState()
    val conflicts = remember(profile.intervals, profile.mode, others) { ScheduleConflicts.of(profile, others) }

    Scaffold(
        snackbarHost = { SnackbarHost(rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)) },
        topBar = {
            FgTopBar(
                if (profileId == 0L) "Nuevo perfil" else "Editar perfil",
                onBack = onDone,
                backDescription = "Volver sin guardar",
                actions = {
                    if (profileId != 0L) IconButton(onClick = { onPreview(profileId) }) { Icon(FgIcons.Info, contentDescription = "Vista previa") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profileId == 0L) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("Plantillas", { showTemplates = true }, Modifier.weight(1f))
                        SecondaryButton("Block Me", { showBlockMe = true }, Modifier.weight(1f))
                    }
                }
            }
            item {
                AppCard {
                    SectionTitle("Nombre")
                    Hint("Algo que te recuerde para qué sirve, por ejemplo «Estudio» o «Noche tranquila».")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = profile.emoji,
                            onValueChange = viewModel::setEmoji,
                            label = { Text("Emoji") },
                            singleLine = true,
                            modifier = Modifier.width(96.dp),
                        )
                        OutlinedTextField(
                            value = profile.title,
                            onValueChange = viewModel::setTitle,
                            label = { Text("Nombre") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { AppCard { SectionTitle("Qué hace"); ModePicker(profile.mode, viewModel::setMode) } }
            item {
                AppCard {
                    SectionTitle("Qué incluir", FgIcons.Apps)
                    val appLabels = rememberAppLabels(profile.apps)
                    profile.apps.sortedBy { appLabels[it]?.lowercase() }.forEach { pkg ->
                        RemovableAppRow(pkg, appLabels[pkg] ?: pkg, "Quitar ${appLabels[pkg] ?: pkg}", { viewModel.setApps(profile.apps - pkg) })
                    }
                    SecondaryButton(
                        text = if (profile.apps.isEmpty()) "Elegir apps" else "Cambiar las apps elegidas",
                        onClick = { showAppPicker = true },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    WebRulesEditor(profile.webs, viewModel::addWeb, viewModel::removeWeb)
                }
            }
            if (conflicts.isNotEmpty()) item { ConflictsCard(conflicts) }
            if (profile.mode == BlockMode.BLOCK) item { CategoriesCard(profile.categories, viewModel::setCategory) }
            item { SubAppsCard(viewModel.subAppRules, profile.subApps, viewModel::setSubApp) }
            item {
                AppCard {
                    SectionTitle("Cuándo", FgIcons.Timer)
                    Hint(if (profile.intervals.isEmpty()) "Siempre activo. Añade un horario si solo quieres bloquear a ciertas horas." else "Solo se activa en estos horarios.")
                    IntervalsEditor(profile.intervals, viewModel::addInterval, viewModel::removeInterval)
                }
            }
            item {
                ConditionsCard(
                    profile = profile,
                    hasLocationPermission = viewModel.hasLocationPermission,
                    onMode = viewModel::setConditionMode,
                    onAddWifi = viewModel::addWifi,
                    onRemoveWifi = viewModel::removeWifi,
                    readCurrentWifi = viewModel::currentWifi,
                    hasBluetoothPermission = viewModel.hasBluetoothPermission,
                    pairedBluetooth = viewModel::pairedBluetooth,
                    onAddBluetooth = viewModel::addBluetooth,
                    onRemoveBluetooth = viewModel::removeBluetooth,
                    onCharging = viewModel::setRequireCharging,
                    useCurrentLocation = viewModel::useCurrentLocation,
                    onRadius = viewModel::setGeofenceRadius,
                    onInverted = viewModel::setGeofenceInverted,
                    onClearGeofence = viewModel::clearGeofence,
                )
            }
            item { LimitCard(profile.limit, viewModel::setLimit) { limitInvalid = it } }
            if (profileId != 0L && profile.kind == ProfileKind.NORMAL) {
                item { ProfileLockCard(profileId, koinViewModel<ProfileLockViewModel>()) }
            }
            item {
                PrimaryButton("Guardar", { viewModel.save(onDone) }, Modifier.fillMaxWidth(), enabled = profile.title.isNotBlank() && !limitInvalid)
                if (profileId != 0L) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Eliminar este perfil", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (showTemplates) {
        TemplatePickerDialog(
            onPick = { viewModel.applyTemplate(it); showTemplates = false },
            onDismiss = { showTemplates = false },
        )
    }
    if (showBlockMe) {
        BlockMeDialog(
            onConfirm = { viewModel.applyTemplate(it); showBlockMe = false },
            onDismiss = { showBlockMe = false },
        )
    }
    if (viewModel.needsConsciousWait) {
        ConsciousEditDialog(onContinue = viewModel::acknowledgeConscious, onCancel = onDone)
    }
    if (showAppPicker) {
        AppPickerDialog(
            selected = profile.apps,
            onConfirm = { viewModel.setApps(it); showAppPicker = false },
            onDismiss = { showAppPicker = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar «${profile.title}»?") },
            text = { Text("Se borran sus apps, webs, horarios y límites. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onDone) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun ModePicker(mode: BlockMode, onChange: (BlockMode) -> Unit) {
    RadioGroup(
        options = listOf(
            Triple(BlockMode.BLOCK, "Bloquear lo que elija", "El resto del teléfono funciona normal."),
            Triple(BlockMode.ALLOW_ONLY, "Permitir solo lo que elija", "Todo lo demás se bloquea, salvo teléfono y emergencias."),
        ),
        selected = mode,
        onSelect = onChange,
    )
}

/** Lo usan los perfiles y las sesiones. */
@Composable
internal fun WebRulesEditor(webs: List<WebRule>, onAdd: (String, WebMatchType) -> Unit, onRemove: (WebRule) -> Unit) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WebMatchType.EXACT) }
    Text("Webs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Hint(if (type == WebMatchType.EXACT) "Dominio: bloquea la web y todas sus páginas, por ejemplo reddit.com." else "Contiene: bloquea cualquier dirección que incluya esa palabra.")
    ChipChoices(listOf(WebMatchType.EXACT to "Dominio", WebMatchType.CONTAINS to "Contiene"), type, { type = it })
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (type == WebMatchType.EXACT) "Por ejemplo reddit.com" else "Por ejemplo apuestas") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        SecondaryButton("Añadir", { onAdd(text, type); text = "" }, enabled = text.isNotBlank())
    }
    webs.forEach { rule ->
        RemovableRow(
            text = "${rule.value} · ${if (rule.matchType == WebMatchType.EXACT) "dominio" else "contiene"}",
            removeDescription = "Quitar ${rule.value}",
            onRemove = { onRemove(rule) },
        )
    }
}

/** Lo usan los perfiles y el modo estricto. */
@Composable
internal fun IntervalsEditor(
    intervals: List<ScheduleInterval>,
    onAdd: (days: Set<DayOfWeek>, startMinute: Int, endMinute: Int) -> Unit,
    onRemove: (ScheduleInterval) -> Unit,
) {
    var days by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    val startMinute = remember(start) { parseMinutes(start)?.takeIf { it < 24 * 60 } }
    val endMinute = remember(end) { parseMinutes(end) }

    intervals.sortedWith(compareBy({ it.day.value }, { it.startMinute })).forEach { interval ->
        RemovableRow(
            text = "${Days.word(interval.day).replaceFirstChar { it.uppercase() }} · ${formatMinutes(interval.startMinute)}–${formatMinutes(interval.endMinute)}",
            removeDescription = "Quitar horario del ${Days.word(interval.day)}",
            onRemove = { onRemove(interval) },
        )
    }

    Text("Añadir un horario", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Hint("Puedes añadir varios bloques el mismo día, por ejemplo de 9:00 a 12:00 y de 14:00 a 17:00: el perfil solo bloquea dentro de cada uno.")
    DayChips(days) { days = it }
    TimeRangeFields(start, end, { start = it }, { end = it }, startMinute != null, endMinute != null)
    Hint("Toca «Desde» y «Hasta» para elegir la hora. Si «hasta» es menor que «desde», el horario sigue al día siguiente (por ejemplo de 23:00 a 07:00).")
    SecondaryButton(
        text = "Añadir horario",
        onClick = { onAdd(days, startMinute!!, endMinute!!) },
        enabled = days.isNotEmpty() && startMinute != null && endMinute != null,
    )
}

/** Los días de la semana como círculos con atajos; sirve para elegir varios. */
@Composable
internal fun DayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) = DayCircles(days, onChange)

/**
 * «Desde» y «hasta» como botones que abren el selector de hora, y debajo la barra de 24 h con el rango marcado. Los textos siguen siendo
 * HH:mm para quien llama; [startValid] y [endValid] dicen si se entienden.
 */
@Composable
internal fun TimeRangeFields(start: String, end: String, onStart: (String) -> Unit, onEnd: (String) -> Unit, startValid: Boolean, endValid: Boolean) {
    val startMinute = parseMinutes(start)?.takeIf { it < 24 * 60 }.takeIf { startValid }
    val endMinute = parseMinutes(end).takeIf { endValid }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeButton("Desde", startMinute, fallbackMinute = 9 * 60, onPick = { onStart(formatMinutes(it)) }, modifier = Modifier.weight(1f))
        TimeButton("Hasta", endMinute, fallbackMinute = 17 * 60, onPick = { onEnd(formatMinutes(it)) }, modifier = Modifier.weight(1f))
    }
    DayRangeBar(startMinute, endMinute, Modifier.padding(top = 8.dp))
}

@Composable
private fun LimitCard(limit: UsageLimit?, onChange: (UsageLimit?) -> Unit, onInvalid: (Boolean) -> Unit) {
    // Sin límite no hay campo que validar.
    LaunchedEffect(limit == null) { if (limit == null) onInvalid(false) }
    AppCard {
        SwitchRow(
            title = "Límite de uso",
            subtitle = "Con límite, el perfil solo bloquea cuando lo agotas. Puedes repartirlo entre todas sus apps y webs o dárselo a cada una.",
            checked = limit != null,
            onCheckedChange = { on -> onChange(if (on) UsageLimit(LimitKind.TIME, 30) else null) },
        )
        if (limit != null) LimitFields(limit, onChange, onInvalid)
    }
}

@Composable
private fun LimitFields(limit: UsageLimit, onChange: (UsageLimit) -> Unit, onInvalid: (Boolean) -> Unit) {
    ChipChoices(
        options = listOf(LimitKind.TIME to "Tiempo", LimitKind.OPENS to "Aperturas", LimitKind.CONTINUOUS to "Seguidos"),
        selected = limit.kind,
        onSelect = { onChange(limit.copy(kind = it)) },
        modifier = Modifier.padding(top = 12.dp),
    )
    ChipChoices(
        options = listOf(LimitScope.SHARED to "Entre todas", LimitScope.PER_TARGET to "Cada app aparte"),
        selected = limit.scope,
        onSelect = { onChange(limit.copy(scope = it)) },
        modifier = Modifier.padding(top = 8.dp),
    )
    Hint(
        if (limit.scope == LimitScope.SHARED) "Un solo contador: lo que gastes en cualquiera cuenta para todas."
        else "Cada app o web tiene su propio contador: agotar una no bloquea las demás.",
    )
    var amount by remember(limit.kind) { mutableStateOf(limit.amount.toString()) }
    NumberField(
        label = if (limit.kind == LimitKind.OPENS) "Aperturas permitidas" else "Minutos",
        value = amount,
        onChange = { text ->
            amount = text
            val value = text.toIntOrNull()?.takeIf { it > 0 }
            // Si el texto no es válido se avisa para no guardar con el importe anterior.
            onInvalid(value == null)
            value?.let { onChange(limit.copy(amount = it)) }
        },
        modifier = Modifier.padding(top = 8.dp),
    )
    if (limit.kind != LimitKind.CONTINUOUS) {
        ChipChoices(
            options = listOf(LimitPeriod.HOURLY to "Por hora", LimitPeriod.DAILY to "Al día", LimitPeriod.WEEKLY to "A la semana"),
            selected = limit.period,
            onSelect = { onChange(limit.copy(period = it)) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun AppPickerDialog(selected: Set<String>, onConfirm: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Resolver los nombres de cientos de apps tarda: se hace fuera del hilo principal.
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { loadInstalledApps(context) }
    }
    var chosen by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) { apps?.filter { it.label.contains(query.trim(), ignoreCase = true) }.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    apps == null -> CircularProgressIndicator(Modifier.padding(top = 16.dp))
                    shown.isEmpty() -> Text("Ninguna app coincide.", modifier = Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        AppCheckRow(
                            packageName = app.packageName,
                            label = app.label,
                            checked = app.packageName in chosen,
                            onCheckedChange = { chosen = if (it) chosen + app.packageName else chosen - app.packageName },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text("Aceptar (${chosen.size})") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
