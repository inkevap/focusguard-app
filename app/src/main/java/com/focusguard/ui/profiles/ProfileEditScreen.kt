package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.focusguard.ui.components.AppCheckRow
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.DayCircles
import com.focusguard.ui.components.DayRangeBar
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.FgTextAction
import com.focusguard.ui.components.NumberField
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.TimeButton
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.parseMinutes
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Las subpantallas de la ficha de un perfil. */
private enum class EditPage(val title: String) {
    NAME("Nombre y emoji"),
    MODE("Qué hace"),
    APPS("Apps"),
    WEBS("Webs"),
    CATEGORIES("Categorías"),
    SUBAPPS("Dentro de apps"),
    SCHEDULE("Horario"),
    CONDITIONS("Condiciones"),
    LIMIT("Límite de uso"),
    LOCK("Candado"),
}

private fun countValue(count: Int) = if (count == 0) "Ninguna" else count.toString()

@Composable
fun ProfileEditScreen(viewModel: ProfileEditViewModel, profileId: Long, onDone: () -> Unit, onPreview: (Long) -> Unit) {
    LaunchedEffect(profileId) { viewModel.load(profileId) }
    val profile = viewModel.profile
    var page by rememberSaveable { mutableStateOf<EditPage?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var limitInvalid by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showBlockMe by remember { mutableStateOf(false) }
    val others by viewModel.others.collectAsState()
    val conflicts = remember(profile.intervals, profile.mode, others) { ScheduleConflicts.of(profile, others) }
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    val lockViewModel = if (profileId != 0L) koinViewModel<ProfileLockViewModel>() else null
    val hasLock = lockViewModel != null && profile.kind == ProfileKind.NORMAL

    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen(
                title = profile.title.ifBlank { if (profileId == 0L) "Nuevo perfil" else "Perfil" },
                onBack = onDone,
                snackbar = snackbar,
                actions = {
                    FgTextAction("Guardar", { viewModel.save(onDone) }, enabled = profile.title.isNotBlank() && !limitInvalid)
                },
            ) {
                if (profileId == 0L) {
                    item {
                        FgGroup(header = "Empezar desde") {
                            FgRow("Plantillas", onClick = { showTemplates = true })
                            FgRow("Block Me", subtitle = "Días y horas en un paso", onClick = { showBlockMe = true })
                        }
                    }
                }
                item {
                    FgGroup {
                        FgRow("Nombre y emoji", value = "${profile.emoji} ${profile.title}".trim().ifBlank { "Sin nombre" }, onClick = { page = EditPage.NAME })
                        FgRow("Qué hace", value = if (profile.mode == BlockMode.BLOCK) "Bloquear" else "Permitir solo", onClick = { page = EditPage.MODE })
                    }
                }
                item {
                    FgGroup(header = if (profile.mode == BlockMode.BLOCK) "Qué bloquea" else "Qué permite") {
                        FgRow("Apps", value = countValue(profile.apps.size), onClick = { page = EditPage.APPS })
                        FgRow("Webs", value = countValue(profile.webs.size), onClick = { page = EditPage.WEBS })
                        if (profile.mode == BlockMode.BLOCK) FgRow("Categorías", value = countValue(profile.categories.size), onClick = { page = EditPage.CATEGORIES })
                        FgRow("Dentro de apps", value = countValue(profile.subApps.size), onClick = { page = EditPage.SUBAPPS })
                    }
                }
                item {
                    FgGroup(header = "Cuándo") {
                        FgRow("Horario", value = scheduleSummary(profile.intervals), onClick = { page = EditPage.SCHEDULE })
                        FgRow("Condiciones", value = conditionsSummary(profile) ?: "Ninguna", onClick = { page = EditPage.CONDITIONS })
                        FgRow("Límite de uso", value = limitSummary(profile.limit)?.removePrefix("Límite: ") ?: "No", onClick = { page = EditPage.LIMIT })
                    }
                }
                if (conflicts.isNotEmpty()) item { ConflictsCard(conflicts) }
                if (hasLock && lockViewModel != null) {
                    item {
                        FgGroup(header = "Más") {
                            FgRow("Candado", value = profileLockValue(profileId, lockViewModel), onClick = { page = EditPage.LOCK })
                        }
                    }
                }
                if (profileId != 0L) {
                    item {
                        FgGroup {
                            FgRow("Vista previa", onClick = { onPreview(profileId) })
                            FgActionRow("Eliminar perfil", { confirmDelete = true }, danger = true)
                        }
                    }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, onBack = { page = null }, snackbar = snackbar) {
                item {
                    when (current) {
                        EditPage.NAME -> NameGroup(profile.emoji, profile.title, viewModel::setEmoji, viewModel::setTitle)
                        EditPage.MODE -> GroupStack { ModePicker(profile.mode, viewModel::setMode) }
                        EditPage.APPS -> SelectedAppsGroups(profile.apps, viewModel::setApps, { showAppPicker = true })
                        EditPage.WEBS -> WebRulesEditor(profile.webs, viewModel::addWeb, viewModel::removeWeb)
                        EditPage.CATEGORIES -> CategoriesCard(profile.categories, viewModel::setCategory)
                        EditPage.SUBAPPS -> SubAppsCard(viewModel.subAppRules, profile.subApps, viewModel::setSubApp)
                        EditPage.SCHEDULE -> ScheduleGroups(profile.intervals, viewModel::addInterval, viewModel::removeInterval)
                        EditPage.CONDITIONS -> ConditionsCard(
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
                        EditPage.LIMIT -> LimitCard(profile.limit, viewModel::setLimit) { limitInvalid = it }
                        EditPage.LOCK -> if (lockViewModel != null) ProfileLockCard(profileId, lockViewModel)
                    }
                }
            }
        },
    )

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
private fun NameGroup(emoji: String, title: String, onEmoji: (String) -> Unit, onTitle: (String) -> Unit) {
    GroupStack {
        FgGroup(footer = "Algo que te recuerde para qué sirve, por ejemplo «Estudio» o «Noche tranquila».") {
            FgCell {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = onEmoji,
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitle,
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Lo usan los perfiles y las sesiones. Va dentro de un bloque ([GroupStack]): no pinta tarjeta propia. */
@Composable
internal fun ModePicker(mode: BlockMode, onChange: (BlockMode) -> Unit) {
    FgRadioGroup(
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
internal fun WebRulesEditor(webs: List<WebRule>, onAdd: (String, WebMatchType) -> Unit, onRemove: (WebRule) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WebMatchType.EXACT) }
    GroupStack(modifier) {
        FgGroup(
            header = "Añadir una web",
            footer = if (type == WebMatchType.EXACT) "Dominio: bloquea la web y todas sus páginas, por ejemplo reddit.com."
            else "Contiene: bloquea cualquier dirección que incluya esa palabra.",
        ) {
            FgCell { ChipChoices(listOf(WebMatchType.EXACT to "Dominio", WebMatchType.CONTAINS to "Contiene"), type, { type = it }) }
            FgCell {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
        if (webs.isNotEmpty()) {
            FgGroup(header = "Añadidas") {
                webs.forEach { rule ->
                    RemovableFgRow(
                        title = "${rule.value} · ${if (rule.matchType == WebMatchType.EXACT) "dominio" else "contiene"}",
                        removeDescription = "Quitar ${rule.value}",
                        onRemove = { onRemove(rule) },
                    )
                }
            }
        }
    }
}

/** Los horarios de la ficha: los que hay, y un formulario para añadir otro. Hace lo mismo que [IntervalsEditor], con listas agrupadas. */
@Composable
private fun ScheduleGroups(
    intervals: List<ScheduleInterval>,
    onAdd: (days: Set<DayOfWeek>, startMinute: Int, endMinute: Int) -> Unit,
    onRemove: (ScheduleInterval) -> Unit,
) {
    var days by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    val startMinute = remember(start) { parseMinutes(start)?.takeIf { it < 24 * 60 } }
    val endMinute = remember(end) { parseMinutes(end) }
    GroupStack {
        if (intervals.isEmpty()) {
            FgNote("Siempre activo. Añade un horario si solo quieres bloquear a ciertas horas.", Modifier.padding(horizontal = 16.dp))
        } else {
            FgGroup(header = "Horarios", footer = "Solo se activa en estos horarios.") {
                intervals.sortedWith(compareBy({ it.day.value }, { it.startMinute })).forEach { interval ->
                    RemovableFgRow(
                        title = "${Days.word(interval.day).replaceFirstChar { it.uppercase() }} · ${formatMinutes(interval.startMinute)}–${formatMinutes(interval.endMinute)}",
                        removeDescription = "Quitar horario del ${Days.word(interval.day)}",
                        onRemove = { onRemove(interval) },
                    )
                }
            }
        }
        FgGroup(
            header = "Añadir un horario",
            footer = "Puedes añadir varios bloques el mismo día, por ejemplo de 9:00 a 12:00 y de 14:00 a 17:00: el perfil solo bloquea dentro de cada uno. " +
                "Toca «Desde» y «Hasta» para elegir la hora. Si «hasta» es menor que «desde», el horario sigue al día siguiente (por ejemplo de 23:00 a 07:00).",
        ) {
            FgCell {
                DayChips(days) { days = it }
                TimeRangeFields(start, end, { start = it }, { end = it }, startMinute != null, endMinute != null)
            }
            FgActionRow(
                "Añadir horario",
                onClick = { onAdd(days, startMinute!!, endMinute!!) },
                enabled = days.isNotEmpty() && startMinute != null && endMinute != null,
            )
        }
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
        com.focusguard.ui.components.RemovableRow(
            text = "${Days.word(interval.day).replaceFirstChar { it.uppercase() }} · ${formatMinutes(interval.startMinute)}–${formatMinutes(interval.endMinute)}",
            removeDescription = "Quitar horario del ${Days.word(interval.day)}",
            onRemove = { onRemove(interval) },
        )
    }

    Text("Añadir un horario", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    com.focusguard.ui.components.Hint("Puedes añadir varios bloques el mismo día, por ejemplo de 9:00 a 12:00 y de 14:00 a 17:00: el perfil solo bloquea dentro de cada uno.")
    DayChips(days) { days = it }
    TimeRangeFields(start, end, { start = it }, { end = it }, startMinute != null, endMinute != null)
    com.focusguard.ui.components.Hint("Toca «Desde» y «Hasta» para elegir la hora. Si «hasta» es menor que «desde», el horario sigue al día siguiente (por ejemplo de 23:00 a 07:00).")
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
    GroupStack {
        FgGroup(footer = "Con límite, el perfil solo bloquea cuando lo agotas. Puedes repartirlo entre todas sus apps y webs o dárselo a cada una.") {
            FgSwitchRow(
                title = "Límite de uso",
                checked = limit != null,
                onCheckedChange = { on -> onChange(if (on) UsageLimit(LimitKind.TIME, 30) else null) },
            )
        }
        if (limit != null) LimitFields(limit, onChange, onInvalid)
    }
}

@Composable
private fun LimitFields(limit: UsageLimit, onChange: (UsageLimit) -> Unit, onInvalid: (Boolean) -> Unit) {
    // El texto que se escribe vive solo en esta subpantalla; al salir el borrador se queda con el último importe válido.
    DisposableEffect(Unit) { onDispose { onInvalid(false) } }
    GroupStack {
        FgRadioGroup(
            options = listOf(
                Triple(LimitKind.TIME, "Tiempo", null),
                Triple(LimitKind.OPENS, "Aperturas", null),
                Triple(LimitKind.CONTINUOUS, "Seguidos", null),
            ),
            selected = limit.kind,
            onSelect = { onChange(limit.copy(kind = it)) },
            header = "Qué se cuenta",
        )
        FgRadioGroup(
            options = listOf(
                Triple(LimitScope.SHARED, "Entre todas", null),
                Triple(LimitScope.PER_TARGET, "Cada app aparte", null),
            ),
            selected = limit.scope,
            onSelect = { onChange(limit.copy(scope = it)) },
            header = "Cómo se reparte",
            footer = if (limit.scope == LimitScope.SHARED) "Un solo contador: lo que gastes en cualquiera cuenta para todas."
            else "Cada app o web tiene su propio contador: agotar una no bloquea las demás.",
        )
        var amount by remember(limit.kind) { mutableStateOf(limit.amount.toString()) }
        FgGroup(header = "Cuánto") {
            FgCell {
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
                )
            }
        }
        if (limit.kind != LimitKind.CONTINUOUS) {
            FgRadioGroup(
                options = listOf(
                    Triple(LimitPeriod.HOURLY, "Por hora", null),
                    Triple(LimitPeriod.DAILY, "Al día", null),
                    Triple(LimitPeriod.WEEKLY, "A la semana", null),
                ),
                selected = limit.period,
                onSelect = { onChange(limit.copy(period = it)) },
                header = "Cada",
            )
        }
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
