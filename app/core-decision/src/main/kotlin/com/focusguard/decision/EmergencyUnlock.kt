package com.focusguard.decision

import com.focusguard.model.BlockReason
import java.time.Duration
import java.time.Instant

/** Cupo del desbloqueo de emergencia: [perWeek] usos en cualquier ventana de 7 días, cada uno de [minutes] minutos. */
data class EmergencyConfig(val perWeek: Int = 3, val minutes: Int = 5) {
    init {
        require(perWeek > 0 && minutes > 0) { "cupo no válido" }
    }
}

sealed interface EmergencyAvailability {
    data class Available(val left: Int) : EmergencyAvailability

    sealed interface Unavailable : EmergencyAvailability

    data object ByStrictMode : Unavailable

    data object ByPanic : Unavailable

    /** El cupo está gastado; [again] es cuándo se libera un uso. */
    data class QuotaSpent(val again: Instant) : Unavailable
}

/** Los pasos del desbloqueo de emergencia con fricción (H12). */
enum class EmergencyStep { INTRO, WHY, GAIN, FEEL, PLEDGE, WAIT, DONE }

object EmergencyUnlock {

    const val WAIT_SECONDS = 60
    const val MIN_ANSWER_CHARS = 15
    const val COMMITMENT = "Me comprometo a volver a mis objetivos cuando acabe este tiempo."

    /** Solo se ofrece cuando el bloqueo viene de un perfil; las pantallas protegidas no se desbloquean. */
    val ELIGIBLE_REASONS = BlockReason.entries.filterTo(mutableSetOf()) { it.fromProfile }

    private val WINDOW = Duration.ofDays(7)

    fun availability(
        grants: List<Instant>,
        now: Instant,
        config: EmergencyConfig,
        strictActive: Boolean,
        panicActive: Boolean,
    ): EmergencyAvailability {
        if (strictActive) return EmergencyAvailability.ByStrictMode
        if (panicActive) return EmergencyAvailability.ByPanic
        // Un uso con fecha futura (el reloj retrocedió) también cuenta: retrasar el reloj no libera cupo.
        val recent = grants.filter { it.isAfter(now - WINDOW) }.sorted()
        val left = config.perWeek - recent.size
        if (left > 0) return EmergencyAvailability.Available(left)
        // Se libera un uso cuando el más antiguo de los que cuentan sale de la ventana.
        return EmergencyAvailability.QuotaSpent(recent[recent.size - config.perWeek] + WINDOW)
    }

    /** Hasta cuándo dura el desbloqueo concedido en [grantedAt]. */
    fun activeUntil(grantedAt: Instant, config: EmergencyConfig): Instant = grantedAt.plusSeconds(config.minutes * 60L)

    /** ¿Se puede pasar al siguiente paso con lo escrito en este? La espera la mide quien llama. */
    fun canAdvance(step: EmergencyStep, text: String): Boolean = when (step) {
        EmergencyStep.WHY, EmergencyStep.GAIN, EmergencyStep.FEEL -> text.trim().length >= MIN_ANSWER_CHARS
        EmergencyStep.PLEDGE -> Approvals.matches(text, COMMITMENT)
        else -> true
    }

    fun next(step: EmergencyStep): EmergencyStep = EmergencyStep.entries[minOf(step.ordinal + 1, EmergencyStep.DONE.ordinal)]
}
