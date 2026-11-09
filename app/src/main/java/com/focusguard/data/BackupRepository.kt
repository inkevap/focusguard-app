package com.focusguard.data

import android.content.Context
import android.net.Uri
import com.focusguard.decision.BackupSettings
import com.focusguard.decision.BackupSnapshot
import com.focusguard.decision.RestorePlan
import com.focusguard.decision.RestorePolicy
import com.focusguard.decision.RestoreState
import com.focusguard.model.ProfileKind
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * La configuración como una copia (N2, N3, H8): perfiles, ajustes, pantalla de bloqueo y modo estricto. Sirve para el archivo local
 * (sin los lugares) y para la copia cifrada del servidor (con ellos). No lleva el PIN, las aprobaciones pendientes, el uso acumulado, los permitidos
 * del filtro adulto ni el estado de la sesión.
 *
 * Restaurar nunca debilita una protección: los perfiles se añaden como nuevos; los interruptores solo se encienden; las políticas
 * (apps nuevas, navegadores integrados) solo si no son más flojas que las de ahora; el modo estricto y la pantalla de bloqueo solo si
 * en este teléfono aún no se tocaron.
 */
class BackupRepository(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val strict: StrictRepository,
    private val lockStyle: LockStyleRepository,
    private val guard: ChangeGuard,
) : SyncLocal {

    /** El archivo local solo lleva lo que se puede compartir sin contar nada íntimo: perfiles (sin Wi-Fi ni ubicación) y los ajustes sencillos. */
    suspend fun export(uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(snapshotJson(forServer = false).toString(2)) }
            ?: error("No se pudo abrir el archivo para escribir")
    }

    /**
     * La copia como JSON. [forServer]: la que sale cifrada al servidor es la de quien cambia de teléfono y lleva también los lugares, las
     * palabras propias del filtro, los mensajes de la pantalla de bloqueo y el modo estricto; el archivo, que se comparte, no.
     */
    suspend fun snapshotJson(forServer: Boolean): JSONObject = BackupJson.toJson(
        BackupSnapshot(
            settings = BackupSettings(
                dayStartMinute = settings.dayStartMinute.value,
                adultBlock = settings.adultBlockEnabled.value,
                adultWords = if (forServer) settings.adultWords.value else emptySet(),
                newApps = settings.newApps.value,
                embeddedBrowsers = settings.embeddedBrowsers.value,
                foldFloating = settings.foldFloating.value,
            ),
            lockStyle = lockStyle.style.value.takeIf { forServer },
            strict = strict.config.value.takeIf { forServer },
            profiles = profiles.profiles.first().filter(BackupJson::isPortable),
        ),
        includePlaces = forServer, exportedAtMs = System.currentTimeMillis(),
    )

    /** Qué se hizo al restaurar. */
    data class Report(
        val profiles: Int,
        /** El modo estricto de la copia pide un PIN que aquí no hay: no se trajo. */
        val strictNeedsPin: Boolean,
        /** El modo estricto de la copia pide la aprobación de un compañero que aquí no hay: no se trajo. */
        val strictNeedsPartner: Boolean = false,
    )

    /** Devuelve cuántos perfiles se añadieron. Lanza una excepción con un mensaje claro si el archivo no es válido. */
    suspend fun import(uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo abrir el archivo")
        // Un archivo puede venir de cualquiera (correo, chat, web): no trae el modo estricto ni la pantalla de bloqueo, que solo valen con la copia del servidor, que se abre con tu frase.
        return restore(text, fromServer = false).profiles
    }

    /**
     * Restaura una copia (archivo o servidor). Con el modo estricto bloqueando cambios no se restaura nada. Se lee y valida entera antes
     * de aplicar nada; lo que se aplica lo decide [RestorePolicy], y los perfiles van al final (si algo falla antes, un reintento no los duplica).
     */
    suspend fun restore(text: String, fromServer: Boolean): Report {
        if (guard.isLocked()) error("Con el modo estricto activo no se puede restaurar una copia.")
        val parsed = BackupJson.fromJson(text)
        val snapshot = if (fromServer) parsed else parsed.copy(lockStyle = null, strict = null)
        val plan = RestorePolicy.plan(snapshot, restoreState())
        applySettings(plan)
        plan.lockStyle?.let(lockStyle::save)
        plan.strict?.let(strict::saveConfig)
        snapshot.profiles.forEach { profiles.save(it.copy(id = 0, pausedUntil = null)) }
        return Report(snapshot.profiles.size, plan.strictNeedsPin, plan.strictNeedsPartner)
    }

    /** Lo que se aplica de una copia que se puede aplicar sin aflojar nada: los interruptores, las palabras y las políticas. */
    private fun applySettings(plan: RestorePlan) {
        plan.dayStartMinute?.let(settings::setDayStartMinute)
        if (plan.enableAdultBlock) settings.setAdultBlockEnabled(true)
        if (plan.enableFoldFloating) settings.setFoldFloating(true)
        plan.addAdultWords.forEach(settings::addAdultWord)
        plan.newApps?.let(settings::setNewApps)
        plan.embeddedBrowsers?.let(settings::setEmbeddedBrowsers)
    }

    /** Los ajustes que se sincronizan entre dispositivos (N4): los de la copia del servidor sin el inicio del día, que no se sincroniza. */
    override fun syncSettings() = BackupSettings(
        adultBlock = settings.adultBlockEnabled.value,
        adultWords = settings.adultWords.value,
        newApps = settings.newApps.value,
        embeddedBrowsers = settings.embeddedBrowsers.value,
        foldFloating = settings.foldFloating.value,
    )

    /** Aplica lo sincronizado con la misma regla que restaurar una copia: solo se enciende y se añade, nunca se afloja nada. */
    override fun applySync(incoming: BackupSettings) {
        applySettings(RestorePolicy.plan(BackupSnapshot(settings = incoming), restoreState()))
    }

    override val lastSyncMs get() = settings.lastSyncMs

    override fun setLastSyncMs(ms: Long?) = settings.setLastSyncMs(ms)

    private fun restoreState() =
        RestoreState(guard.approvalRequired, strict.hasPin, settings.newApps.value, settings.embeddedBrowsers.value, lockStyle.style.value, strict.config.value, guard.partnerLinked)
}
