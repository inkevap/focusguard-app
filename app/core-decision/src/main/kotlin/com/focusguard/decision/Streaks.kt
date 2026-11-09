package com.focusguard.decision

import com.focusguard.model.LimitPeriod
import java.time.LocalDate
import java.time.LocalDateTime

/** Lo guardado de un día para las rachas: cuánto estuvo el bloqueo protegiendo y qué pausas hubo. */
data class DayRecord(
    val day: LocalDate,
    val protectedMs: Long = 0,
    val pauses: Int = 0,
    val longestPauseMinutes: Int = 0,
)

enum class LossReason { TOO_LITTLE, LONG_PAUSE, MANY_PAUSES }

sealed interface DayOutcome {
    data object Achieved : DayOutcome

    /** Un día perdido que el usuario restauró con aprobación: cuenta como logrado. */
    data object Forgiven : DayOutcome

    data class Lost(val reason: LossReason) : DayOutcome

    /** Hoy, sin llegar aún a la hora de bloqueo. */
    data object InProgress : DayOutcome

    /** Antes de empezar la racha, o un día que aún no ha llegado. */
    data object NotCounted : DayOutcome

    val counts: Boolean get() = this == Achieved || this == Forgiven
}

/** Lo que el usuario decide de su racha. [startDay] vacío: desde el primer día guardado. */
data class StreakConfig(
    val startDay: LocalDate? = null,
    /** Meta en días; 0 sin meta. */
    val goalDays: Int = 0,
    val forgiven: Set<LocalDate> = emptySet(),
)

/**
 * Lo que se sabe de la racha ahora. [best] y [lastLoss] recorren todos los días guardados y se calculan solo si alguien los lee:
 * los avisos y Inicio no los necesitan.
 */
class StreakState(
    val current: Int,
    val today: DayOutcome,
    val todayProtectedMs: Long,
    best: () -> Int,
    lastLoss: () -> LostDay?,
) {
    val best: Int by lazy(best)
    val lastLoss: LostDay? by lazy(lastLoss)
}

data class LostDay(val day: LocalDate, val reason: LossReason)

/** Un día perdido que aún se puede restaurar, y los días de racha que había justo antes. */
data class Restorable(val day: LocalDate, val runBefore: Int)

/**
 * Rachas (L1, L2; `ESPECIFICACION.md` 9). Un día se logra con al menos una hora de bloqueo activo y se pierde si hubo una pausa de más
 * de 15 minutos o más de tres pausas. Un día sin datos (la app no corría) cuenta como sin bloqueo. Todo se calcula de los datos de
 * cada día, sin guardar el resultado: así un cambio de reglas o una restauración no deja números viejos.
 */
object Streaks {
    const val MIN_PROTECTED_MS = 60 * 60_000L
    const val MAX_PAUSE_MINUTES = 15
    const val MAX_PAUSES = 3
    val MILESTONES = listOf(1, 3, 7, 14, 30, 50, 100)
    val GOAL_PRESETS = listOf(7, 14, 30, 50, 100)
    const val MAX_GOAL = 365

    /** Hasta cuándo atrás se puede restaurar un día perdido, qué racha mínima merece la pena y cada cuánto se permite. */
    const val RESTORE_WINDOW_DAYS = 2
    const val MIN_RESTORABLE_RUN = 3
    const val RESTORE_EVERY_DAYS = 14

    /** Hora a partir de la que se avisa de que la racha de hoy está en riesgo. */
    const val RISK_MINUTE = 20 * 60

    /** El día lógico de la hora local, con el inicio del día de los ajustes (E17): la misma regla que los límites diarios. */
    fun dayOf(localNow: LocalDateTime, dayStartMinute: Int): LocalDate = Periods.start(LimitPeriod.DAILY, localNow, dayStartMinute).toLocalDate()

    fun lossReason(record: DayRecord?): LossReason? = when {
        record == null -> LossReason.TOO_LITTLE
        record.longestPauseMinutes > MAX_PAUSE_MINUTES -> LossReason.LONG_PAUSE
        record.pauses > MAX_PAUSES -> LossReason.MANY_PAUSES
        record.protectedMs < MIN_PROTECTED_MS -> LossReason.TOO_LITTLE
        else -> null
    }

    /**
     * La configuración con [StreakConfig.startDay] ya puesto: el elegido por el usuario o, sin él, el del primer dato. Quien calcula
     * muchos días (el calendario) la resuelve una vez y la pasa a [outcome].
     */
    fun resolve(records: Map<LocalDate, DayRecord>, config: StreakConfig): StreakConfig =
        if (config.startDay != null) config else config.copy(startDay = records.keys.minOrNull())

    fun outcome(day: LocalDate, today: LocalDate, records: Map<LocalDate, DayRecord>, config: StreakConfig): DayOutcome {
        val first = resolve(records, config).startDay
        if (day.isAfter(today) || first == null || day.isBefore(first)) return DayOutcome.NotCounted
        if (day in config.forgiven) return DayOutcome.Forgiven
        return when (val reason = lossReason(records[day])) {
            null -> DayOutcome.Achieved
            LossReason.TOO_LITTLE -> if (day == today) DayOutcome.InProgress else DayOutcome.Lost(reason)
            else -> DayOutcome.Lost(reason)
        }
    }

    fun state(records: Map<LocalDate, DayRecord>, today: LocalDate, config: StreakConfig): StreakState {
        val resolved = resolve(records, config)
        val todayOutcome = outcome(today, today, records, resolved)
        return StreakState(
            current = currentRun(records, today, resolved, todayOutcome),
            today = todayOutcome,
            todayProtectedMs = records[today]?.protectedMs ?: 0,
            best = { best(records, today, config.copy(startDay = null)) },
            lastLoss = { lastLoss(records, today, resolved) },
        )
    }

    /** Solo los días seguidos de ahora, sin la mejor racha ni el último día perdido: lo que necesita Inicio. */
    fun current(records: Map<LocalDate, DayRecord>, today: LocalDate, config: StreakConfig): Int {
        val resolved = resolve(records, config)
        return currentRun(records, today, resolved, outcome(today, today, records, resolved))
    }

    /** Hoy aún sin llegar a la hora no rompe la racha: se cuenta desde ayer. Si hoy está perdido o no cuenta, el recorrido da 0 solo. */
    private fun currentRun(records: Map<LocalDate, DayRecord>, today: LocalDate, resolved: StreakConfig, todayOutcome: DayOutcome): Int =
        runEndingAt(if (todayOutcome == DayOutcome.InProgress) today.minusDays(1) else today, records, today, resolved)

    /** Días seguidos logrados que acaban en [end] (0 si ese día no cuenta). */
    private fun runEndingAt(end: LocalDate, records: Map<LocalDate, DayRecord>, today: LocalDate, resolved: StreakConfig): Int {
        var count = 0
        var day = end
        while (outcome(day, today, records, resolved).counts) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    private fun best(records: Map<LocalDate, DayRecord>, today: LocalDate, config: StreakConfig): Int {
        val resolved = resolve(records, config)
        var best = 0
        var run = 0
        var day = resolved.startDay ?: return 0
        while (!day.isAfter(today)) {
            val outcome = outcome(day, today, records, resolved)
            if (outcome.counts) {
                run++
                best = maxOf(best, run)
            } else if (outcome != DayOutcome.InProgress) {
                run = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    private fun lastLoss(records: Map<LocalDate, DayRecord>, today: LocalDate, resolved: StreakConfig): LostDay? {
        val start = resolved.startDay ?: return null
        var day = today
        while (!day.isBefore(start)) {
            (outcome(day, today, records, resolved) as? DayOutcome.Lost)?.let { return LostDay(day, it.reason) }
            day = day.minusDays(1)
        }
        return null
    }

    /**
     * El día perdido reciente que se puede restaurar, o null. Solo uno de los últimos [RESTORE_WINDOW_DAYS] días (sin contar hoy), si antes
     * tenía una racha de al menos [MIN_RESTORABLE_RUN] días, y no más de una vez cada [RESTORE_EVERY_DAYS] días. La aprobación la pide quien llama.
     */
    fun restorable(records: Map<LocalDate, DayRecord>, today: LocalDate, config: StreakConfig): Restorable? {
        val resolved = resolve(records, config)
        if (resolved.startDay == null) return null
        if (config.forgiven.any { !it.isBefore(today.minusDays(RESTORE_EVERY_DAYS.toLong())) }) return null
        for (back in 1..RESTORE_WINDOW_DAYS) {
            val day = today.minusDays(back.toLong())
            if (outcome(day, today, records, resolved) !is DayOutcome.Lost) continue
            val before = runEndingAt(day.minusDays(1), records, today, resolved)
            return if (before >= MIN_RESTORABLE_RUN) Restorable(day, before) else null
        }
        return null
    }

    fun nextMilestone(current: Int): Int? = MILESTONES.firstOrNull { it > current }

    /**
     * Avisos de la racha (J3): que está en riesgo al caer la tarde y que se llegó a un hito. La hora es la del día lógico (E17): con el día
     * empezando a las 22:00, «las 20:00» del día son las 18:00 del reloj.
     */
    fun notices(state: StreakState, today: LocalDate, localNow: LocalDateTime, dayStartMinute: Int = 0): List<Notice> {
        val minute = Math.floorMod(localNow.hour * 60 + localNow.minute - dayStartMinute, 24 * 60)
        return listOfNotNull(
            if (state.current in MILESTONES && state.today.counts) {
                Notice("streakmilestone:${state.current}:$today", NoticeKind.STREAK_MILESTONE, count = state.current)
            } else null,
            if (state.today == DayOutcome.InProgress && state.current > 0 && minute >= RISK_MINUTE) {
                val missing = ((MIN_PROTECTED_MS - state.todayProtectedMs + 59_999) / 60_000).toInt()
                // Si ya no queda tiempo para llegar a la hora, avisar no sirve de nada.
                if (missing <= 24 * 60 - minute) Notice("streakrisk:$today", NoticeKind.STREAK_AT_RISK, minutes = missing, count = state.current) else null
            } else null,
        )
    }
}

/**
 * Suma el tiempo en que el bloqueo está protegiendo. Un hueco largo entre dos lecturas (el servicio estuvo parado) no se cuenta entero:
 * como mucho [maxGapMs] por lectura, para que un servicio muerto no regale horas.
 */
class ProtectedTimeAccumulator(private val maxGapMs: Long = UsageAccumulator.MAX_STEP_MS) {
    private var lastMs: Long? = null
    private val pending = mutableMapOf<LocalDate, Long>()

    fun tick(nowMs: Long, protecting: Boolean, day: LocalDate) {
        val previous = lastMs
        lastMs = nowMs
        if (!protecting || previous == null) return
        val delta = (nowMs - previous).coerceIn(0, maxGapMs)
        if (delta > 0) pending[day] = (pending[day] ?: 0) + delta
    }

    /** Lo acumulado desde la última vez, que se vacía. */
    fun drain(): Map<LocalDate, Long> = pending.toMap().also { pending.clear() }

    /** Devuelve lo que se vació y no se pudo guardar, para volver a intentarlo con el siguiente volcado. */
    fun putBack(unsaved: Map<LocalDate, Long>) = unsaved.forEach { (day, ms) -> pending[day] = (pending[day] ?: 0) + ms }
}
