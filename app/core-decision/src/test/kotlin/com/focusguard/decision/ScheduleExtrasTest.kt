package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleExtrasTest {

    private fun profile(id: Long, vararg intervals: ScheduleInterval, mode: BlockMode = BlockMode.BLOCK) =
        Profile(id, "P$id", intervals = intervals.toList(), mode = mode)

    private val monday9to13 = ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 13 * 60)

    @Test
    fun hayTrecePlantillasConHorarioOLimite() {
        assertEquals(13, ScheduleTemplates.all.size)
        assertEquals(13, ScheduleTemplates.all.map { it.id }.toSet().size, "identificadores repetidos")
        ScheduleTemplates.all.forEach { assertTrue(it.intervals.isNotEmpty() || it.limit != null, "${it.id} no define nada") }
    }

    @Test
    fun laPlantillaCambiaNombreHorarioYLimiteSinTocarLasApps() {
        val base = Profile(1, "", apps = setOf("com.x"), mode = BlockMode.ALLOW_ONLY)
        val applied = ScheduleTemplates.all.first { it.id == "study" }.applyTo(base)
        assertEquals("Estudio", applied.title)
        assertEquals(5, applied.intervals.size)
        assertEquals(setOf("com.x"), applied.apps)
        assertEquals(BlockMode.ALLOW_ONLY, applied.mode)
    }

    @Test
    fun detectaElSolapeYDiceCuandoEmpieza() {
        val mine = profile(1, monday9to13)
        val other = profile(2, ScheduleInterval(DayOfWeek.MONDAY, 12 * 60, 15 * 60))
        val conflict = ScheduleConflicts.of(mine, listOf(other)).single()
        assertEquals(2L, conflict.otherId)
        assertEquals(DayOfWeek.MONDAY, conflict.day)
        assertEquals(12 * 60, conflict.startMinute)
        assertEquals(13 * 60, conflict.endMinute)
    }

    @Test
    fun horariosQueSoloSeTocanNoChocan() {
        val touching = profile(2, ScheduleInterval(DayOfWeek.MONDAY, 13 * 60, 15 * 60))
        assertTrue(ScheduleConflicts.of(profile(1, monday9to13), listOf(touching)).isEmpty())
    }

    @Test
    fun otroModoOSinHorarioNoChocan() {
        val mine = profile(1, monday9to13)
        assertTrue(ScheduleConflicts.of(mine, listOf(profile(2, monday9to13, mode = BlockMode.ALLOW_ONLY))).isEmpty())
        assertTrue(ScheduleConflicts.of(mine, listOf(profile(3))).isEmpty())
        assertTrue(ScheduleConflicts.of(mine, listOf(mine)).isEmpty())
    }

    @Test
    fun detectaElSolapeQueCruzaLaMedianocheYElFinDeSemana() {
        val sundayNight = profile(1, ScheduleInterval(DayOfWeek.SUNDAY, 22 * 60, 7 * 60))
        val mondayEarly = profile(2, ScheduleInterval(DayOfWeek.MONDAY, 5 * 60, 8 * 60))
        val conflict = ScheduleConflicts.of(sundayNight, listOf(mondayEarly)).single()
        assertEquals(DayOfWeek.MONDAY, conflict.day)
        assertEquals(5 * 60, conflict.startMinute)
        assertEquals(7 * 60, conflict.endMinute)
    }

    @Test
    fun laHoraDeFinEsLaDelRelojAunqueElSolapeCrucelaMedianoche() {
        val late = profile(1, ScheduleInterval(DayOfWeek.MONDAY, 23 * 60, 3 * 60))
        val other = profile(2, ScheduleInterval(DayOfWeek.MONDAY, 22 * 60, 1 * 60))
        val conflict = ScheduleConflicts.of(late, listOf(other)).single()
        assertEquals(23 * 60, conflict.startMinute)
        assertEquals(1 * 60, conflict.endMinute)
    }

    @Test
    fun unIntervaloConLaMismaHoraDeInicioYFinDura24Horas() {
        val full = ScheduleInterval(DayOfWeek.TUESDAY, 8 * 60, 8 * 60)
        assertEquals(24 * 60, full.lengthMinutes)
        assertEquals(24 * 60 + 8 * 60, full.startOfWeek)
        assertEquals(60, ScheduleInterval(DayOfWeek.MONDAY, 0, 60).lengthMinutes)
    }

    @Test
    fun laEdicionConscienteSoloPideEsperaConHorarioYSinModoEstricto() {
        val scheduled = profile(1, monday9to13)
        assertTrue(ConsciousEdit.required(scheduled, strictLocked = false))
        assertFalse(ConsciousEdit.required(scheduled, strictLocked = true))
        assertFalse(ConsciousEdit.required(profile(2), strictLocked = false))
        assertFalse(ConsciousEdit.required(null, strictLocked = false))
    }
}
