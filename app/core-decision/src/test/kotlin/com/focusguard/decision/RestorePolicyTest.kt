package com.focusguard.decision

import com.focusguard.model.ExitMethod
import com.focusguard.model.LockStyle
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestorePolicyTest {
    private val fresh = RestoreState(
        approvalRequired = false,
        hasPin = true,
        newApps = NewAppsPolicy(false, 24),
        embeddedBrowsers = EmbeddedBrowserPolicy(false, true, emptySet()),
        lockStyle = LockStyle(),
        strict = StrictConfig(),
    )

    private fun snapshot(settings: BackupSettings = BackupSettings(), lockStyle: LockStyle? = null, strict: StrictConfig? = null) =
        BackupSnapshot(settings, lockStyle, strict)

    @Test fun enUnTelefonoNuevoSeAplicaTodo() {
        val strict = StrictConfig(enabled = true, protections = setOf(StrictProtection.SETTINGS_GUARD), exitMethods = listOf(ExitMethod.Pin))
        val style = LockStyle(message = "Tú puedes", emoji = "💪")
        val plan = RestorePolicy.plan(
            snapshot(
                BackupSettings(dayStartMinute = 240, adultBlock = true, adultWords = setOf("palabra"), newApps = NewAppsPolicy(true, 72), foldFloating = true),
                style, strict,
            ),
            fresh,
        )
        assertEquals(240, plan.dayStartMinute)
        assertEquals(true, plan.enableAdultBlock)
        assertEquals(setOf("palabra"), plan.addAdultWords)
        assertEquals(NewAppsPolicy(true, 72), plan.newApps)
        assertEquals(style, plan.lockStyle)
        assertEquals(strict, plan.strict)
        assertEquals(false, plan.strictNeedsPin)
    }

    @Test fun conAprobacionesActivasNoSeMueveElInicioDelDia() {
        val plan = RestorePolicy.plan(snapshot(BackupSettings(dayStartMinute = 240)), fresh.copy(approvalRequired = true))
        assertNull(plan.dayStartMinute)
    }

    @Test fun unaPoliticaMasFlojaNoSeAplica() {
        val strictNow = fresh.copy(newApps = NewAppsPolicy(true, 72), embeddedBrowsers = EmbeddedBrowserPolicy(true, true, emptySet()))
        val plan = RestorePolicy.plan(
            snapshot(BackupSettings(newApps = NewAppsPolicy(true, 24), embeddedBrowsers = EmbeddedBrowserPolicy(false, true, emptySet()))),
            strictNow,
        )
        assertNull(plan.newApps)
        assertNull(plan.embeddedBrowsers)
    }

    @Test fun unaPoliticaIgualOMasDuraSiSeAplica() {
        val now = fresh.copy(newApps = NewAppsPolicy(true, 24))
        val plan = RestorePolicy.plan(snapshot(BackupSettings(newApps = NewAppsPolicy(true, 72))), now)
        assertEquals(NewAppsPolicy(true, 72), plan.newApps)
    }

    @Test fun elModoEstrictoYaConfiguradoNoSeToca() {
        val mine = StrictConfig(enabled = true, protections = setOf(StrictProtection.RECENTS))
        val plan = RestorePolicy.plan(snapshot(strict = StrictConfig(enabled = true, protections = setOf(StrictProtection.SETTINGS_GUARD))), fresh.copy(strict = mine))
        assertNull(plan.strict)
    }

    @Test fun unModoEstrictoQuePidePinSinPinAquiNoSeTrae() {
        val needsPin = StrictConfig(enabled = true, exitMethods = listOf(ExitMethod.Pin))
        val plan = RestorePolicy.plan(snapshot(strict = needsPin), fresh.copy(hasPin = false))
        assertNull(plan.strict)
        assertEquals(true, plan.strictNeedsPin)
        assertEquals(needsPin, RestorePolicy.plan(snapshot(strict = needsPin), fresh.copy(hasPin = true)).strict)
    }

    @Test fun unModoEstrictoQuePideCompaneroSinCompaneroAquiNoSeTrae() {
        val needsPartner = StrictConfig(enabled = true, exitMethods = listOf(ExitMethod.Pin, ExitMethod.Partner))
        val alone = RestorePolicy.plan(snapshot(strict = needsPartner), fresh.copy(hasPin = true, partnerLinked = false))
        assertNull(alone.strict)
        assertEquals(true, alone.strictNeedsPartner)
        assertEquals(needsPartner, RestorePolicy.plan(snapshot(strict = needsPartner), fresh.copy(hasPin = true, partnerLinked = true)).strict)
    }

    @Test fun laPausaDelModoEstrictoNoViaja() {
        val withPause = StrictConfig(enabled = true, pausedUntil = Instant.parse("2026-01-01T00:00:00Z"))
        assertNull(RestorePolicy.plan(snapshot(strict = withPause), fresh).strict!!.pausedUntil)
    }

    @Test fun laPantallaDeBloqueoPersonalizadaNoSePisa() {
        val mine = LockStyle(message = "mío")
        val plan = RestorePolicy.plan(snapshot(lockStyle = LockStyle(message = "de la copia")), fresh.copy(lockStyle = mine))
        assertNull(plan.lockStyle)
    }

    @Test fun lasCopiasAntiguasSinAjustesNoCambianNada() {
        val plan = RestorePolicy.plan(snapshot(), fresh)
        assertNull(plan.dayStartMinute)
        assertEquals(false, plan.enableAdultBlock)
        assertNull(plan.newApps)
        assertNull(plan.strict)
        assertNull(plan.lockStyle)
    }
}
