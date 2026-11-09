package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.ConditionMode
import com.focusguard.model.GeoFence
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebRule
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeakeningTest {

    private val base = Profile(1, "p", apps = setOf("a", "b"), webs = listOf(WebRule("x.com")))

    @Test
    fun anadirOEndurecerEsLibre() {
        val stronger = base.copy(apps = base.apps + "c", webs = base.webs + WebRule("y.com"))
        assertTrue(Weakening.of(base, stronger).isEmpty())
        assertTrue(Weakening.of(base, base.copy(title = "otro")).isEmpty())
    }

    @Test
    fun elPrimerLimiteOElPrimerHorarioDebilitanPorqueElPerfilDejaDeBloquearSiempre() {
        assertEquals(listOf(Weakening.ACTION_LIMIT), Weakening.of(base, base.copy(limit = UsageLimit(LimitKind.TIME, 1440, LimitPeriod.WEEKLY))))
        assertEquals(
            listOf(Weakening.ACTION_SCHEDULE),
            Weakening.of(base, base.copy(intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 3 * 60, 3 * 60 + 1)))),
        )
    }

    @Test
    fun quitarDeLaListaDeBloqueoDebilita() {
        assertEquals(listOf(Approvals.ACTION_REMOVE_BLOCKED_APP), Weakening.of(base, base.copy(apps = setOf("a"))))
        assertEquals(listOf(Approvals.ACTION_REMOVE_BLOCKED_WEB), Weakening.of(base, base.copy(webs = emptyList())))
    }

    @Test
    fun enModoPermitirDebilitaAnadirNoQuitar() {
        val allow = base.copy(mode = BlockMode.ALLOW_ONLY)
        assertTrue(Weakening.of(allow, allow.copy(apps = setOf("a"))).isEmpty())
        assertEquals(listOf(Approvals.ACTION_REMOVE_ALLOWED_APP), Weakening.of(allow, allow.copy(apps = allow.apps + "c")))
        assertEquals(listOf(Approvals.ACTION_REMOVE_ALLOWED_WEB), Weakening.of(allow, allow.copy(webs = allow.webs + WebRule("z.com"))))
    }

    @Test
    fun cambiarDeModoDebilita() {
        assertEquals(listOf(Weakening.ACTION_PROFILE_MODE), Weakening.of(base, base.copy(mode = BlockMode.ALLOW_ONLY)))
    }

    @Test
    fun limiteQueSeRelajaOSeQuitaDebilita() {
        val limited = base.copy(limit = UsageLimit(LimitKind.TIME, 30))
        assertTrue(Weakening.of(limited, limited.copy(limit = UsageLimit(LimitKind.TIME, 20))).isEmpty())
        assertEquals(listOf(Weakening.ACTION_LIMIT), Weakening.of(limited, limited.copy(limit = UsageLimit(LimitKind.TIME, 31))))
        assertEquals(listOf(Weakening.ACTION_LIMIT), Weakening.of(limited, limited.copy(limit = null)))
        assertEquals(
            listOf(Weakening.ACTION_LIMIT),
            Weakening.of(limited, limited.copy(limit = UsageLimit(LimitKind.TIME, 10, LimitPeriod.WEEKLY))),
        )
    }

    @Test
    fun anadirUnaCondicionDebilitaPorqueElPerfilSeActivaMenos() {
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(base, base.copy(wifiNames = setOf("Casa"))))
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(base, base.copy(requireCharging = true)))
        assertEquals(
            listOf(Weakening.ACTION_CONDITION),
            Weakening.of(base, base.copy(geofence = GeoFence(40.0, -3.0))),
        )
    }

    @Test
    fun quitarRedesOCambiarDeLugarDebilitaPeroAnadirRedesNo() {
        val withBluetooth = base.copy(bluetoothNames = setOf("Coche", "Auriculares"))
        // Quitar un dispositivo o añadir la condición (con «todas») estrecha el perfil: debilita; añadir otro dispositivo no.
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(withBluetooth, withBluetooth.copy(bluetoothNames = setOf("Coche"))))
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(base, base.copy(bluetoothNames = setOf("Coche"))))
        assertTrue(Weakening.of(withBluetooth, withBluetooth.copy(bluetoothNames = withBluetooth.bluetoothNames + "Altavoz")).isEmpty())
        val withWifi = base.copy(wifiNames = setOf("Casa", "Oficina"))
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(withWifi, withWifi.copy(wifiNames = setOf("Casa"))))
        assertTrue(Weakening.of(withWifi, withWifi.copy(wifiNames = withWifi.wifiNames + "Bar")).isEmpty())
        val here = base.copy(geofence = GeoFence(40.0, -3.0))
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(here, here.copy(geofence = GeoFence(41.0, -3.0))))
    }

    @Test
    fun quitarUnaCondicionOPasarATodasSegunElModo() {
        val charging = base.copy(requireCharging = true)
        assertTrue(Weakening.of(charging, base).isEmpty()) // sin exigir cargar, se activa más: endurece
        val any = base.copy(wifiNames = setOf("Casa"), requireCharging = true, conditionMode = ConditionMode.ANY)
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(any, any.copy(conditionMode = ConditionMode.ALL)))
        assertTrue(Weakening.of(any.copy(conditionMode = ConditionMode.ALL), any).isEmpty())
    }

    @Test
    fun conAlgunaQuitarUnaCondicionDebilitaYAnadirlaNo() {
        val any = base.copy(requireCharging = true, geofence = GeoFence(40.0, -3.0), conditionMode = ConditionMode.ANY)
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(any, any.copy(requireCharging = false)))
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(any, any.copy(geofence = null)))
        assertEquals(listOf(Weakening.ACTION_CONDITION), Weakening.of(any, any.copy(geofence = GeoFence(41.0, -3.0))))
        val onlyCharging = base.copy(requireCharging = true, conditionMode = ConditionMode.ANY)
        assertTrue(Weakening.of(onlyCharging, onlyCharging.copy(wifiNames = setOf("Casa"))).isEmpty())
    }

    @Test
    fun quitarUnaSeccionDentroDeAppsDebilitaPeroAnadirlaNo() {
        val withShorts = base.copy(subApps = setOf("YOUTUBE_SHORTS", "INSTAGRAM_REELS"))
        assertEquals(listOf(Weakening.ACTION_SUBAPP), Weakening.of(withShorts, withShorts.copy(subApps = setOf("YOUTUBE_SHORTS"))))
        assertTrue(Weakening.of(withShorts, withShorts.copy(subApps = withShorts.subApps + "SNAPCHAT_STORIES")).isEmpty())
    }

    @Test
    fun quitarOCambiarUnIntervaloDebilita() {
        val monday = ScheduleInterval(DayOfWeek.MONDAY, 8 * 60, 17 * 60)
        val scheduled = base.copy(intervals = listOf(monday))
        assertEquals(listOf(Weakening.ACTION_SCHEDULE), Weakening.of(scheduled, scheduled.copy(intervals = emptyList())))
        assertEquals(
            listOf(Weakening.ACTION_SCHEDULE),
            Weakening.of(scheduled, scheduled.copy(intervals = listOf(monday.copy(endMinute = 12 * 60)))),
        )
        assertTrue(Weakening.of(scheduled, scheduled.copy(intervals = scheduled.intervals + monday.copy(day = DayOfWeek.TUESDAY))).isEmpty())
    }
}
