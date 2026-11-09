package com.focusguard.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ChangeGuard
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.model.PartnerEventType
import com.focusguard.data.GuardResult
import com.focusguard.data.ProfileJson
import com.focusguard.data.ProfileRepository
import com.focusguard.data.SubAppCatalog
import com.focusguard.data.TrustedClock
import com.focusguard.data.UsageRepository
import com.focusguard.decision.Approvals
import com.focusguard.decision.CategoryExpansion
import com.focusguard.decision.CategoryTable
import com.focusguard.decision.Conditions
import com.focusguard.decision.ConsciousEdit
import com.focusguard.decision.EndRules
import com.focusguard.decision.ScheduleTemplate
import com.focusguard.decision.ProfileActivation
import com.focusguard.decision.Weakening
import com.focusguard.service.DeviceContextProvider
import com.focusguard.ui.plural
import com.focusguard.model.AppCategory
import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.DeviceContext
import com.focusguard.model.EndConditions
import com.focusguard.model.EndPolicy
import com.focusguard.model.GeoFence
import com.focusguard.model.PomodoroPlan
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfilesViewModel(
    private val repository: ProfileRepository,
    private val guard: ChangeGuard,
    private val clock: TrustedClock,
    private val deviceContext: DeviceContextProvider,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    private val all: StateFlow<List<Profile>> =
        repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profiles: StateFlow<List<Profile>> =
        all.map { list -> list.filter { it.kind == ProfileKind.NORMAL } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quick Block y Pomodoro en curso. */
    val sessions: StateFlow<List<Profile>> =
        all.map { list -> list.filter { it.kind != ProfileKind.NORMAL } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.purgeExpired(clock.now()) }
    }

    /** Con el modo estricto activo no se puede apagar, editar ni borrar (sí crear y encender). */
    fun isEditLocked(nowMs: Long) = guard.isLocked(Instant.ofEpochMilli(nowMs))

    /** Cuántos perfiles y sesiones están bloqueando ahora: encendidos y dentro de su horario. */
    fun activeCount(profiles: List<Profile>, sessions: List<Profile>, nowMs: Long): Int {
        val now = Instant.ofEpochMilli(nowMs)
        val local = clock.localAt(now)
        val all = profiles + sessions
        val context = if (Conditions.needsDevice(all)) deviceContext.current() else DeviceContext()
        return all.count { ProfileActivation.isActive(it, local, now, context) }
    }

    fun endSession(id: Long) {
        val session = sessions.value.firstOrNull { it.id == id }
        if (session?.canEndEarly == false) {
            _message.value = if (session.kind == ProfileKind.PANIC) ChangeGuard.PANIC else ChangeGuard.NO_EARLY_END
            return
        }
        if (session?.canEndNow(clock.now()) == false) {
            _message.value = "Aún no puedes terminarlo: la espera que elegiste no ha pasado."
            return
        }
        // Terminar un bloqueo antes de tiempo lo debilita: pide aprobación como apagar un perfil.
        if (blocked(ApprovalExecutor.sessionEnd(id), Approvals.ACTION_SESSION_END)) return
        viewModelScope.launch { if (!repository.delete(id)) _message.value = ChangeGuard.PANIC }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        if (!enabled && blocked(ApprovalExecutor.profileOff(id), Approvals.ACTION_PROFILE_OFF)) return
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    /**
     * Pide apagar todos los perfiles encendidos, cada uno por el camino de siempre: con el modo estricto no se hace nada,
     * con aprobaciones se crea una solicitud por perfil y sin ellas se apagan. Las sesiones en curso no se tocan.
     */
    fun disableAll() {
        val on = profiles.value.filter { it.enabled }
        if (on.isEmpty()) {
            _message.value = "No hay ningún perfil encendido."
            return
        }
        val allowed = mutableListOf<Long>()
        var requested = 0
        for (profile in on) {
            when (val result = guard.check(ApprovalExecutor.profileOff(profile.id), Approvals.ACTION_PROFILE_OFF)) {
                GuardResult.Allowed -> allowed += profile.id
                is GuardResult.Blocked -> if (result.requested) requested++ else { _message.value = result.message; return }
            }
        }
        val turnedOff = if (allowed.isEmpty()) null else plural(allowed.size, "perfil apagado", "perfiles apagados")
        val asked = if (requested == 0) null else "${plural(requested, "solicitud creada", "solicitudes creadas")}: ábrelas en Aprobaciones para completarlas"
        viewModelScope.launch {
            allowed.forEach { repository.setEnabled(it, false) }
            _message.value = listOfNotNull(turnedOff, asked).joinToString(". ") + "."
        }
    }

    /** true si el cambio no se hace ahora; deja el motivo en [message]. */
    private fun blocked(actionKey: String, actionId: Int): Boolean =
        when (val result = guard.check(actionKey, actionId)) {
            GuardResult.Allowed -> false
            is GuardResult.Blocked -> { _message.value = result.message; true }
        }
}

class ProfileEditViewModel(
    private val repository: ProfileRepository,
    private val usageRepository: UsageRepository,
    private val guard: ChangeGuard,
    private val deviceContext: DeviceContextProvider,
    catalog: SubAppCatalog,
    private val categoryTable: CategoryTable,
    /** Los paquetes instalados con icono; consulta al sistema, así que se llama fuera del hilo principal. */
    private val installedPackages: () -> Set<String>,
) : ViewModel() {

    val subAppRules = catalog.rules

    /** Los demás perfiles, para avisar de horarios que coinciden. */
    val others: StateFlow<List<Profile>> = repository.profiles
        .map { list -> list.filter { it.kind == ProfileKind.NORMAL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var consciousAcknowledged by mutableStateOf(false)

    /** Cambiar un horario que ya existe, con el modo estricto apagado, pide una pausa de unos segundos antes de editar y de guardar. */
    val needsConsciousWait: Boolean get() = original != null && !consciousAcknowledged && ConsciousEdit.required(original, guard.isLocked())

    fun acknowledgeConscious() { consciousAcknowledged = true }

    /** Solo para perfiles nuevos: en uno existente borraría su horario y su límite. */
    fun applyTemplate(template: ScheduleTemplate) {
        if (profile.id == 0L) profile = template.applyTo(profile)
    }

    private var original by mutableStateOf<Profile?>(null)

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() { message = null }

    var profile by mutableStateOf(Profile(id = 0, title = ""))
        private set

    private var loadedId: Long? = null

    /** Un perfil nuevo siempre se puede guardar; uno existente, solo si el modo estricto no lo bloquea. */
    private fun isEditLocked() = profile.id != 0L && (guard.isLocked() || guard.isProfileLocked(profile.id))

    /** Carga el perfil una sola vez; id 0 es un perfil nuevo. */
    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        consciousAcknowledged = false
        if (id == 0L) {
            profile = Profile(id = 0, title = "")
            return
        }
        viewModelScope.launch { repository.get(id)?.let { profile = it; original = it } }
    }

    fun setTitle(value: String) { profile = profile.copy(title = value) }

    fun setEmoji(value: String) { profile = profile.copy(emoji = value) }

    /** Las categorías solo valen en modo bloquear: al pasar a «permitir solo esto» se quitan para que no reaparezcan a escondidas. */
    fun setMode(mode: BlockMode) {
        profile = profile.copy(mode = mode, categories = if (mode == BlockMode.BLOCK) profile.categories else emptySet())
    }

    fun setApps(apps: Set<String>) { profile = profile.copy(apps = apps) }

    fun setLimit(limit: UsageLimit?) { profile = profile.copy(limit = limit) }

    fun addWeb(value: String, type: WebMatchType) {
        val clean = value.trim()
        if (clean.isEmpty() || profile.webs.any { it.value.equals(clean, ignoreCase = true) }) return
        profile = profile.copy(webs = profile.webs + WebRule(clean, type))
    }

    fun removeWeb(rule: WebRule) { profile = profile.copy(webs = profile.webs - rule) }

    /** Añade un intervalo por cada día elegido. */
    fun addInterval(days: Set<DayOfWeek>, startMinute: Int, endMinute: Int) {
        val added = days.sortedBy { it.value }.map { ScheduleInterval(it, startMinute, endMinute) }
        profile = profile.copy(intervals = (profile.intervals + added).distinct())
    }

    fun removeInterval(interval: ScheduleInterval) { profile = profile.copy(intervals = profile.intervals - interval) }

    /** Al marcar una categoría, sus apps instaladas y sus webs se añaden a las listas del perfil (se ven y se pueden quitar). */
    fun setCategory(id: String, blocked: Boolean) {
        profile = profile.copy(categories = if (blocked) profile.categories + id else profile.categories - id)
        if (!blocked) return
        viewModelScope.launch {
            val installed = withContext(Dispatchers.Default) { installedPackages() }
            // Si la desmarcó mientras se leían las apps, no se añade nada.
            if (id !in profile.categories) return@launch
            val added = CategoryExpansion.expand(setOf(id), categoryTable, installed, profile.apps, profile.webs)
            if (added.isEmpty) return@launch
            profile = profile.copy(apps = profile.apps + added.apps, webs = profile.webs + added.webs)
            val label = AppCategory.entries.firstOrNull { it.id == id }?.label ?: id
            message = "Se añadieron ${plural(added.apps.size, "app", "apps")} y ${plural(added.webs.size, "web", "webs")} de «$label» a las listas del perfil."
        }
    }

    fun setSubApp(id: String, blocked: Boolean) {
        profile = profile.copy(subApps = if (blocked) profile.subApps + id else profile.subApps - id)
    }

    fun setConditionMode(mode: ConditionMode) { profile = profile.copy(conditionMode = mode) }

    fun setRequireCharging(value: Boolean) { profile = profile.copy(requireCharging = value) }

    fun addWifi(name: String) {
        profile = profile.copy(wifiNames = withName(profile.wifiNames, name.trim().trim('"')))
    }

    fun removeWifi(name: String) { profile = profile.copy(wifiNames = profile.wifiNames - name) }

    fun addBluetooth(name: String) {
        profile = profile.copy(bluetoothNames = withName(profile.bluetoothNames, name.trim()))
    }

    /** [names] con [name] añadido, salvo que esté vacío o ya esté (sin importar mayúsculas ni comillas). */
    private fun withName(names: Set<String>, name: String): Set<String> {
        val key = Conditions.normalizeName(name)
        return if (key.isEmpty() || names.any { Conditions.normalizeName(it) == key }) names else names + name
    }

    fun removeBluetooth(name: String) { profile = profile.copy(bluetoothNames = profile.bluetoothNames - name) }

    fun pairedBluetooth(): List<String> = deviceContext.pairedBluetooth()

    val hasBluetoothPermission: Boolean get() = deviceContext.hasBluetoothPermission

    /** La red a la que está conectado el teléfono; null si no se puede leer. */
    fun currentWifi(): String? = deviceContext.currentWifi()

    val hasLocationPermission: Boolean get() = deviceContext.hasLocationPermission

    /** Fija el lugar en la posición actual; false si no hay posición conocida. */
    fun useCurrentLocation(): Boolean {
        val here = deviceContext.bestLocation() ?: return false
        profile = profile.copy(
            geofence = profile.geofence?.copy(latitude = here.latitude, longitude = here.longitude)
                ?: GeoFence(here.latitude, here.longitude),
        )
        return true
    }

    fun setGeofenceRadius(meters: Int) { profile.geofence?.let { profile = profile.copy(geofence = it.copy(radiusMeters = meters)) } }

    fun setGeofenceInverted(value: Boolean) { profile.geofence?.let { profile = profile.copy(geofence = it.copy(inverted = value)) } }

    fun clearGeofence() { profile = profile.copy(geofence = null) }

    /** Borra el perfil, o crea la solicitud de aprobación si hay una activada. */
    fun delete(onDone: () -> Unit) {
        if (profile.id == 0L) return
        when (val result = guard.check(ApprovalExecutor.profileDelete(profile.id), Approvals.ACTION_PROFILE_DELETE)) {
            is GuardResult.Blocked -> message = result.message
            GuardResult.Allowed -> viewModelScope.launch {
                if (repository.delete(profile.id)) {
                    usageRepository.delete(profile.id)
                    onDone()
                } else {
                    message = ChangeGuard.PANIC
                }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        if (profile.title.isBlank()) return
        if (isEditLocked()) {
            message = if (guard.isProfileLocked(profile.id)) ChangeGuard.PROFILE_LOCKED else ChangeGuard.LOCKED
            return
        }
        if (needsConsciousWait) {
            message = "Espera unos segundos antes de cambiar un horario."
            return
        }
        if (profile.id != 0L && guard.hasPending(ApprovalExecutor.profileEdit(profile.id))) {
            // Evita que una edición pendiente pise cambios hechos mientras tanto.
            message = "Este perfil tiene una solicitud de cambio pendiente. Complétala o cancélala en Aprobaciones antes de editarlo."
            return
        }
        val before = original
        val weakened = if (before != null) Weakening.of(before, profile) else emptyList()
        if (weakened.isNotEmpty()) {
            // Debilitar un perfil existente espera aprobación; se guarda lo editado para aplicarlo después.
            val result = guard.check(ApprovalExecutor.profileEdit(profile.id), weakened.first(), ProfileJson.toJson(profile).toString())
            if (result is GuardResult.Blocked) {
                message = result.message
                return
            }
        }
        viewModelScope.launch {
            repository.save(profile)
            onDone()
        }
    }
}

/** Formulario de Quick Block y Pomodoro. */
class SessionViewModel(
    private val repository: ProfileRepository,
    private val clock: TrustedClock,
    private val partnerEvents: PartnerEventOutbox,
    private val deviceContext: DeviceContextProvider,
) : ViewModel() {

    var mode by mutableStateOf(BlockMode.BLOCK)
        private set
    var apps by mutableStateOf(emptySet<String>())
        private set
    var webs by mutableStateOf(emptyList<WebRule>())
        private set

    fun chooseMode(value: BlockMode) { mode = value }

    fun chooseApps(value: Set<String>) { apps = value }

    fun addWeb(value: String, type: WebMatchType) {
        val clean = value.trim()
        if (clean.isNotEmpty() && webs.none { it.value.equals(clean, ignoreCase = true) }) {
            webs = webs + WebRule(clean, type)
        }
    }

    fun removeWeb(rule: WebRule) { webs = webs - rule }

    /** Cómo se podrá terminar el Bloquear ya. Se decide antes de empezar; una vez en marcha no se cambia. */
    var endPolicy by mutableStateOf(EndPolicy.ANYTIME)
        private set

    /** Las salidas elegidas (sin la espera, que cuenta desde que empieza: ver [endWaitMinutes]). */
    var endConditions by mutableStateOf(EndConditions())
        private set

    /** Minutos de espera tras los que se puede pedir terminarlo; null si no se eligió espera. */
    var endWaitMinutes by mutableStateOf<Int?>(null)
        private set

    /** Por qué no se pudo empezar, para enseñarlo en pantalla. */
    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() { message = null }

    fun chooseEndPolicy(value: EndPolicy) { endPolicy = value }

    fun setEndCharging(value: Boolean) { endConditions = endConditions.copy(charging = value) }

    fun addEndWifi(name: String) {
        endConditions = endConditions.copy(wifiNames = withName(endConditions.wifiNames, name.trim().trim('"')))
    }

    fun removeEndWifi(name: String) { endConditions = endConditions.copy(wifiNames = endConditions.wifiNames - name) }

    fun clearEndWifi() { endConditions = endConditions.copy(wifiNames = emptySet()) }

    fun addEndBluetooth(name: String) {
        endConditions = endConditions.copy(bluetoothNames = withName(endConditions.bluetoothNames, name.trim()))
    }

    fun removeEndBluetooth(name: String) { endConditions = endConditions.copy(bluetoothNames = endConditions.bluetoothNames - name) }

    fun clearEndBluetooth() { endConditions = endConditions.copy(bluetoothNames = emptySet()) }

    /** Fija el lugar de llegada en la posición actual; false si no hay posición conocida. */
    fun useCurrentLocationAsEnd(): Boolean {
        val here = deviceContext.bestLocation() ?: return false
        val radius = endConditions.place?.radiusMeters ?: GeoFence.DEFAULT_RADIUS_METERS
        endConditions = endConditions.copy(place = GeoFence(here.latitude, here.longitude, radius))
        return true
    }

    fun setEndRadius(meters: Int) { endConditions.place?.let { endConditions = endConditions.copy(place = it.copy(radiusMeters = meters)) } }

    fun clearEndPlace() { endConditions = endConditions.copy(place = null) }

    fun setEndWait(minutes: Int?) { endWaitMinutes = minutes }

    private fun withName(names: Set<String>, name: String): Set<String> {
        val key = Conditions.normalizeName(name)
        return if (key.isEmpty() || names.any { Conditions.normalizeName(it) == key }) names else names + name
    }

    fun pairedBluetooth(): List<String> = deviceContext.pairedBluetooth()

    val hasBluetoothPermission: Boolean get() = deviceContext.hasBluetoothPermission

    fun currentWifi(): String? = deviceContext.currentWifi()

    val hasLocationPermission: Boolean get() = deviceContext.hasLocationPermission

    fun startQuickBlock(minutes: Int, onDone: () -> Unit) {
        if (minutes <= 0) return
        val now = clock.now()
        val conditions = endConditions.copy(waitUntil = endWaitMinutes?.let { now.plusSeconds(it * 60L) })
        val profile = Profile(
            id = 0, title = "Bloquear ya", kind = ProfileKind.QUICK_BLOCK,
            mode = mode, apps = apps, webs = webs,
            activeUntil = now.plusSeconds(minutes * 60L),
            endPolicy = endPolicy,
            endConditions = if (endPolicy == EndPolicy.CONDITIONS) conditions else EndConditions(),
        )
        endProblem(profile, minutes)?.let { message = it; return }
        save(profile, onDone)
    }

    /** Lo que impide empezar con las salidas elegidas: una que no se podría cumplir, o que ya está cumplida y lo acabaría al instante. Null si todo va bien. */
    private fun endProblem(profile: Profile, minutes: Int): String? {
        if (profile.endPolicy != EndPolicy.CONDITIONS) return null
        val ends = profile.endConditions
        if (ends.isEmpty) return "Elige al menos una forma de terminarlo antes, o cambia a «Solo cuando acabe el tiempo»."
        ends.waitUntil?.let { if (endWaitMinutes!! >= minutes) return "La espera tiene que ser menor que el tiempo del bloqueo." }
        if ((ends.place != null || ends.wifiNames.isNotEmpty()) && !deviceContext.hasLocationPermission) {
            return "Lugar y Wi-Fi necesitan el permiso de Ubicación (Protección > Permisos): sin él no se cumplirían nunca."
        }
        if (ends.bluetoothNames.isNotEmpty() && !deviceContext.hasBluetoothPermission) {
            return "Bluetooth necesita su permiso (Protección > Permisos): sin él no se cumpliría nunca."
        }
        val met = EndRules.alreadyMet(profile, deviceContext.current())
        if (met.isNotEmpty()) return "Ya se cumple ${met.joinToString(" y ")}: el bloqueo terminaría al instante. Quítalo o empieza cuando no se cumpla."
        return null
    }

    /** Hasta cuándo dura el bloqueo de pánico (o Dormir, que es el mismo mecanismo) en curso; null si no hay. */
    val panicUntil: StateFlow<Instant?> = repository.profiles
        .map { profiles -> ProfileActivation.activePanic(profiles, clock.now())?.activeUntil }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startPanic(minutes: Int, onDone: () -> Unit) {
        if (minutes !in 1..MAX_LOCK_MINUTES) return
        save(Profile.panic(clock.now().plusSeconds(minutes * 60L))) {
            partnerEvents.record(PartnerEventType.PANIC)
            onDone()
        }
    }

    fun startPomodoro(focusMinutes: Int, breakMinutes: Int, sessions: Int, onDone: () -> Unit) {
        if (focusMinutes <= 0 || breakMinutes < 0 || sessions <= 0) return
        save(
            Profile(
                id = 0, title = "Enfoque", kind = ProfileKind.POMODORO,
                mode = mode, apps = apps, webs = webs,
                pomodoro = PomodoroPlan(clock.now(), focusMinutes, breakMinutes, sessions),
            ),
            onDone,
        )
    }

    private fun save(profile: Profile, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.save(profile)
            onDone()
        }
    }
}
