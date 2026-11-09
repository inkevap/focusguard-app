package com.focusguard.decision

import com.focusguard.model.BlockReason
import com.focusguard.model.GuardTargets
import com.focusguard.model.PartnerEvent
import com.focusguard.model.PartnerEventType
import com.focusguard.model.StrictProtection
import java.time.Duration

/**
 * Qué sucesos del teléfono se cuentan al compañero (I4) y cómo se guardan hasta enviarlos. Solo viaja qué pasó y cuándo, con el
 * paquete o dominio como etiqueta; el texto de pantalla no sale del teléfono.
 */
object PartnerEvents {
    const val MAX_QUEUED = 200
    const val MAX_BATCH = 50
    const val MAX_LABEL = 200

    private val NOT_URGENT = setOf(PartnerEventType.BLOCKED, PartnerEventType.DNS_COUNTS, PartnerEventType.URGE_REPORT)

    /** Lo mismo repetido (alguien que insiste en abrir lo mismo o en los mismos ajustes) cuenta una vez cada tanto. Pánico y emergencia no se agrupan. */
    private fun gap(type: PartnerEventType): Duration? = when (type) {
        PartnerEventType.BLOCKED -> Duration.ofMinutes(5)
        PartnerEventType.SETTINGS_ATTEMPT, PartnerEventType.UNINSTALL_ATTEMPT, PartnerEventType.PROTECTION_OFF -> Duration.ofMinutes(2)
        PartnerEventType.PANIC, PartnerEventType.EMERGENCY_UNLOCK, PartnerEventType.ICON_CHANGED, PartnerEventType.DNS_COUNTS, PartnerEventType.URGE_REPORT -> null
    }

    /** Los bloqueos del modo estricto sobre los ajustes son intentos de apagar la protección (y de desinstalar la app, si la pantalla lo ofrece); el resto de pantallas del sistema no se cuentan. */
    fun classify(reason: BlockReason, target: String): PartnerEventType? = when (reason) {
        BlockReason.STRICT_MODE -> when (target) {
            StrictProtection.SETTINGS_GUARD.screenName -> PartnerEventType.SETTINGS_ATTEMPT
            GuardTargets.UNINSTALL -> PartnerEventType.UNINSTALL_ATTEMPT
            else -> null
        }
        BlockReason.CLOCK_PROTECTED -> null
        // Entrar donde se desactiva el administrador es el paso previo a desinstalar la app.
        BlockReason.ADMIN_PROTECTED -> PartnerEventType.UNINSTALL_ATTEMPT
        else -> PartnerEventType.BLOCKED
    }

    /** Los sucesos graves se envían enseguida; los bloqueos y los resúmenes (DNS diario, impulsos semanales) esperan al siguiente envío periódico. */
    fun urgent(type: PartnerEventType) = type !in NOT_URGENT

    /** Añade el suceso a la cola: sin repetir uno reciente igual y, si se llena, tirando antes los bloqueos más viejos que los graves. */
    fun enqueue(queue: List<PartnerEvent>, event: PartnerEvent): List<PartnerEvent> {
        val clean = event.copy(label = event.label?.take(MAX_LABEL))
        val gap = gap(clean.type)
        if (gap != null && queue.any { it.type == clean.type && it.label == clean.label && Duration.between(it.at, clean.at).abs() < gap }) return queue
        val result = queue + clean
        if (result.size <= MAX_QUEUED) return result
        // Entra de uno en uno, así que sobra uno: el bloqueo más viejo, o el más viejo de todos si solo hay graves.
        val drop = result.indexOfFirst { it.type == PartnerEventType.BLOCKED }.takeIf { it >= 0 } ?: 0
        return result.filterIndexed { i, _ -> i != drop }
    }

    fun nextBatch(queue: List<PartnerEvent>): List<PartnerEvent> = queue.take(MAX_BATCH)
}
