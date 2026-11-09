package com.focusguard.decision

import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ApprovalStatus
import com.focusguard.model.BlockDecision
import com.focusguard.model.DeviceContext
import com.focusguard.model.ExitContext
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ExitRequirement
import com.focusguard.model.ForegroundState
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.LimitScope
import com.focusguard.model.Profile
import com.focusguard.model.ProfileLock
import com.focusguard.model.ProfileUsage
import com.focusguard.model.StrictAction
import com.focusguard.model.UsageKey
import com.focusguard.model.UsageLimit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerTargetLimitTest {

    private val zone = ZoneOffset.UTC
    private val local = LocalDateTime.of(2026, 10, 28, 12, 0)
    private val now = local.toInstant(zone)
    private val nowMs = now.toEpochMilli()
    private val periodStart = Periods.startMs(LimitPeriod.DAILY, local, 0, zone)
    private val decider = BlockDecider(neverBlock = emptySet(), zone = zone)

    private fun state(pkg: String) = ForegroundState(pkg)
    private fun profile(scope: LimitScope, amount: Int = 30) = Profile(
        id = 1, title = "Redes", apps = setOf("a.insta", "b.tiktok"), limit = UsageLimit(LimitKind.TIME, amount, scope = scope),
    )
    private fun spent(minutes: Int) = ProfileUsage(periodStartMs = periodStart, timeMs = minutes * 60_000L)

    @Test
    fun conLimitePorAppAgotarUnaNoBloqueaLaOtra() {
        val profiles = listOf(profile(LimitScope.PER_TARGET))
        val targetUsage = mapOf(UsageKey(1, "a.insta") to spent(30), UsageKey(1, "b.tiktok") to spent(5))
        assertIs<BlockDecision.Block>(decider.decide(state("a.insta"), profiles, now, targetUsage = targetUsage))
        assertEquals(BlockDecision.Allow, decider.decide(state("b.tiktok"), profiles, now, targetUsage = targetUsage))
    }

    @Test
    fun conLimiteCompartidoElUsoDeUnaAppBloqueaTodas() {
        val profiles = listOf(profile(LimitScope.SHARED))
        val usage = mapOf(1L to spent(30))
        assertIs<BlockDecision.Block>(decider.decide(state("a.insta"), profiles, now, usage))
        assertIs<BlockDecision.Block>(decider.decide(state("b.tiktok"), profiles, now, usage))
    }

    @Test
    fun elUsoCompartidoNoCuentaEnUnLimitePorApp() {
        val profiles = listOf(profile(LimitScope.PER_TARGET))
        assertEquals(BlockDecision.Allow, decider.decide(state("a.insta"), profiles, now, usage = mapOf(1L to spent(99))))
    }

    @Test
    fun elPerfilProtegeSoloSiAlgunaAppAgotoSuLimite() {
        val profiles = listOf(profile(LimitScope.PER_TARGET))
        assertFalse(decider.isProtecting(profiles, now, targetUsage = mapOf(UsageKey(1, "a.insta") to spent(10))))
        assertTrue(decider.isProtecting(profiles, now, targetUsage = mapOf(UsageKey(1, "a.insta") to spent(30))))
    }

    @Test
    fun elContadorEsElMismoObjetivoQueUsaElDecisor() {
        val web = Profile(id = 2, title = "Webs", webs = listOf(com.focusguard.model.WebRule("youtube.com")))
        val browser = ForegroundState("com.browser", url = "https://www.youtube.com/watch?v=1", isBrowser = true)
        val blocked = decider.decide(browser, listOf(web), now) as BlockDecision.Block
        assertEquals(blocked.target, ProfileTargets.counterOf(web, browser))
        val allow = Profile(id = 3, title = "Solo", mode = com.focusguard.model.BlockMode.ALLOW_ONLY, apps = setOf("ok.app"))
        assertNull(ProfileTargets.counterOf(allow, state("ok.app")))
        val denied = decider.decide(state("otra"), listOf(allow), now) as BlockDecision.Block
        assertEquals(denied.target, ProfileTargets.counterOf(allow, state("otra")))
    }

    @Test
    fun estadoDelLimiteDiceCuantoQuedaYCuantoSeLleva() {
        val time = UsageLimit(LimitKind.TIME, 30)
        val status = LimitStatus.of(time, spent(18), nowMs, local, zone = zone)
        assertEquals(18, status.used)
        assertEquals(12, status.left)
        assertEquals("Llevas 18 de 30 min · quedan 12 min", status.describe())
        assertEquals("Límite agotado: 30 de 30 min", LimitStatus.of(time, spent(30), nowMs, local, zone = zone).describe())

        val opens = UsageLimit(LimitKind.OPENS, 3)
        val three = ProfileUsage(periodStartMs = periodStart, opens = 3)
        assertEquals("Llevas 3 de 3 aperturas · la próxima bloquea", LimitStatus.of(opens, three, nowMs, local, zone = zone).describe())
        assertEquals(1, LimitStatus.of(opens, three.copy(opens = 2), nowMs, local, zone = zone).left)
    }

    @Test
    fun usoDeAyerNoGastaElLimiteDeHoy() {
        val status = LimitStatus.of(UsageLimit(LimitKind.TIME, 30), spent(29).copy(periodStartMs = periodStart - 86_400_000L), nowMs, local, zone = zone)
        assertEquals(0, status.used)
        assertEquals(30, status.left)
    }

    @Test
    fun laEsperaDeLasAprobacionesRetrasaElEnvioAlCompanero() {
        val created = Instant.parse("2026-10-28T12:00:00Z")
        assertEquals(created.plusSeconds(24 * 3600), Approvals.partnerSendAfter(ApprovalMode.DELAY_24H, created))
        assertEquals(created.plusSeconds(72 * 3600), Approvals.partnerSendAfter(ApprovalMode.DELAY_72H, created))
        assertNull(Approvals.partnerSendAfter(ApprovalMode.LONG_PHRASE, created))
        assertNull(Approvals.partnerSendAfter(ApprovalMode.NONE, created))

        val request = ApprovalRequest(1, "admin_off", Approvals.ACTION_ADMIN_OFF, ApprovalMode.PARTNER, created, sendAfter = Approvals.partnerSendAfter(ApprovalMode.DELAY_24H, created))
        val holding = Approvals.evaluate(request, com.focusguard.model.ApprovalProgress(), created.plusSeconds(3600))
        assertEquals(ApprovalStatus.HoldingBeforePartner(created.plusSeconds(24 * 3600)), holding)
        assertEquals(ApprovalStatus.WaitingPartner(sent = false), Approvals.evaluate(request, com.focusguard.model.ApprovalProgress(), created.plusSeconds(25 * 3600)))
        assertEquals(ApprovalStatus.WaitingPartner(sent = false), Approvals.evaluate(request.copy(sendAfter = null), com.focusguard.model.ApprovalProgress(), created))
    }

    @Test
    fun elCandadoDeUnPerfilPideTodosSusMetodos() {
        val lock = ProfileLock(listOf(ExitMethod.Timer(30), ExitMethod.Charger))
        val asked = Instant.parse("2026-10-28T12:00:00Z")
        val request = ExitRequest(StrictAction.Disable, asked)
        val early = ProfileLocks.evaluateExit(lock, request, ExitContext(charging = false, pinCorrect = null), local, asked.plusSeconds(60))
        assertFalse(early.satisfied)
        assertTrue(early.pending.any { it is ExitRequirement.WaitUntil })
        assertTrue(ExitRequirement.NeedCharger in early.pending)
        val done = ProfileLocks.evaluateExit(lock, request, ExitContext(charging = true, pinCorrect = null), local, asked.plusSeconds(31 * 60))
        assertTrue(done.satisfied)
    }

    @Test
    fun elCandadoSoloAfectaAlPerfilDeLaAccion() {
        assertEquals(7L, ProfileLocks.targetOf("profile_off:7"))
        assertEquals(7L, ProfileLocks.targetOf("profile_edit:7"))
        assertEquals(7L, ProfileLocks.targetOf("session_end:7"))
        assertNull(ProfileLocks.targetOf("admin_off"))
        assertNull(ProfileLocks.targetOf("profile_unlock:7"))
        assertNull(ProfileLocks.invalidReason(listOf(ExitMethod.Pin)))
        assertEquals("Elige al menos un método para poder quitar el candado.", ProfileLocks.invalidReason(emptyList()))
        assertTrue(ProfileLocks.invalidReason(listOf(ExitMethod.FollowSchedule)) != null)
    }
}
