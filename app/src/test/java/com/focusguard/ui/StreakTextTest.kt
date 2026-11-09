package com.focusguard.ui

import com.focusguard.decision.LossReason
import com.focusguard.ui.streak.StreakText
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTextTest {

    @Test
    fun cuentaLosDiasEnSingularYPlural() {
        assertEquals("1 día", StreakText.days(1))
        assertEquals("0 días", StreakText.days(0))
        assertEquals("12 días", StreakText.days(12))
    }

    @Test
    fun poneLaFechaYElMesEnEspanol() {
        assertEquals("27 de octubre", StreakText.date(LocalDate.of(2026, 10, 27)))
        assertEquals("octubre 2026", StreakText.monthTitle(YearMonth.of(2026, 10)))
    }

    @Test
    fun explicaCadaMotivoDePerdida() {
        assertEquals("no llegó a 1 h de bloqueo activo", StreakText.lossReason(LossReason.TOO_LITTLE))
        assertEquals("hubo una pausa de más de 15 minutos", StreakText.lossReason(LossReason.LONG_PAUSE))
        assertEquals("hubo más de 3 pausas", StreakText.lossReason(LossReason.MANY_PAUSES))
    }

    @Test
    fun diceCuantoFaltaParaElSiguienteHito() {
        assertEquals("Próximo hito: 7 días (te faltan 2).", StreakText.nextMilestone(5))
        assertEquals("Próximo hito: 3 días (te falta 1).", StreakText.nextMilestone(2))
        assertEquals("Has superado todos los hitos. ¡Enhorabuena!", StreakText.nextMilestone(120))
    }

    @Test
    fun cuentaElProgresoDeHoyYDeLaMeta() {
        assertEquals("Hoy llevas 20 min de 1 h de bloqueo activo.", StreakText.todayProgress(20 * 60_000L))
        assertEquals("12 de 30 días.", StreakText.goalProgress(12, 30))
        assertEquals("Meta cumplida: 30 de 30 días.", StreakText.goalProgress(30, 30))
    }
}
