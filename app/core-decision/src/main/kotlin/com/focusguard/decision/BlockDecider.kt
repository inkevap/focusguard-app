package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockMode
import com.focusguard.model.BlockReason
import com.focusguard.model.DeviceContext
import com.focusguard.model.ForegroundState
import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.model.LimitScope
import com.focusguard.model.UsageKey
import com.focusguard.model.UsageLimit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Decide si lo que está en primer plano se bloquea. Es la parte de F1 del orden de
 * `ESPECIFICACION.md` sección 2: excepciones y perfiles activos (modo bloquear y permitir).
 * Los límites de uso y el contenido adulto se añaden en fases posteriores.
 */
class BlockDecider(
    /** Paquetes que nunca se bloquean: la propia app, teléfono, emergencias, sistema, teclado, lanzador. */
    private val neverBlock: Set<String>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Minuto del día en que empieza el día lógico (E17); afecta a los periodos de los límites. */
    private val dayStartMinute: Int = 0,
    /** Categorías de apps y webs (A12); sin tabla, las categorías de los perfiles no bloquean nada. */
    private val categories: CategoryTable = CategoryTable.EMPTY,
) {

    /** Uso acumulado por id de perfil; solo hace falta para perfiles con límite. */
    fun decide(
        state: ForegroundState,
        profiles: List<Profile>,
        now: Instant,
        usage: Map<Long, ProfileUsage> = emptyMap(),
        context: DeviceContext = DeviceContext(),
        pausedUntil: Instant? = null,
        /** Uso de cada app o web en los perfiles con límite por app ([LimitScope.PER_TARGET]). */
        targetUsage: Map<UsageKey, ProfileUsage> = emptyMap(),
        /** Hasta cuándo sigue bloqueada esta app por ser nueva (A11); null si no lo es. */
        newAppUntil: Instant? = null,
        /** ¿Enseña esta app ahora una vista web y el ajuste de navegadores integrados la alcanza (B8)? Se pregunta al final: mirar la pantalla es caro. */
        webViewApp: () -> Boolean = { false },
    ): BlockDecision {
        if (state.packageName in neverBlock || isPaused(profiles, now, pausedUntil)) return BlockDecision.Allow

        val localNow = now.atZone(zone).toLocalDateTime()
        // El primer perfil (por id) manda en el motivo; los demás que bloquean lo mismo se anotan para nombrarlos en la pantalla de bloqueo.
        var first: BlockDecision.Block? = null
        val others = mutableListOf<Long>()
        profiles
            .filter { ProfileActivation.isActive(it, localNow, now, context) }
            .sortedBy { it.id }
            .forEach { profile ->
                val decision = when (profile.mode) {
                    BlockMode.BLOCK -> decideBlockList(profile, state)
                    BlockMode.ALLOW_ONLY -> decideAllowList(profile, state)
                }
                if (decision is BlockDecision.Block && limitAllowsBlocking(profile, decision.target, usage, targetUsage, now, localNow)) {
                    if (first == null) {
                        // Si el motivo de fondo es el límite agotado, la pantalla de bloqueo lo dice.
                        first = profile.limit?.let { limitDecision(decision, it, localNow) } ?: decision
                    } else {
                        others += profile.id
                    }
                }
            }
        first?.let { return it.copy(otherProfileIds = others) }
        if (webViewApp()) return BlockDecision.Block(BlockReason.EMBEDDED_BROWSER, 0, state.packageName)
        return if (newAppUntil != null) BlockDecision.Block(BlockReason.NEW_APP, 0, state.packageName, newAppUntil) else BlockDecision.Allow
    }

    /** ¿Hay algún perfil bloqueando ahora, sin un desbloqueo de emergencia vigente? Es lo que cuenta como bloqueo activo para las rachas. */
    fun isProtecting(
        profiles: List<Profile>,
        now: Instant,
        usage: Map<Long, ProfileUsage> = emptyMap(),
        context: DeviceContext = DeviceContext(),
        pausedUntil: Instant? = null,
        targetUsage: Map<UsageKey, ProfileUsage> = emptyMap(),
    ): Boolean {
        if (isPaused(profiles, now, pausedUntil)) return false
        val localNow = now.atZone(zone).toLocalDateTime()
        return profiles.any { isBlocking(it, now, localNow, usage, targetUsage, context) }
    }

    /** Secciones dentro de apps que bloquean ahora los perfiles activos (con su límite agotado, si lo tienen), con el perfil que las pide. */
    fun activeSubApps(
        profiles: List<Profile>,
        now: Instant,
        usage: Map<Long, ProfileUsage> = emptyMap(),
        context: DeviceContext = DeviceContext(),
        pausedUntil: Instant? = null,
        targetUsage: Map<UsageKey, ProfileUsage> = emptyMap(),
    ): Map<String, Long> {
        if (isPaused(profiles, now, pausedUntil)) return emptyMap()
        val localNow = now.atZone(zone).toLocalDateTime()
        val active = mutableMapOf<String, Long>()
        for (profile in profiles.sortedBy { it.id }) {
            if (profile.subApps.isEmpty() || !isBlocking(profile, now, localNow, usage, targetUsage, context)) continue
            profile.subApps.forEach { active.putIfAbsent(it, profile.id) }
        }
        return active
    }

    /** Pausa vigente (desbloqueo de emergencia). Un bloqueo de pánico en curso la anula. */
    private fun isPaused(profiles: List<Profile>, now: Instant, pausedUntil: Instant?): Boolean =
        pausedUntil != null && now.isBefore(pausedUntil) &&
            !ProfileActivation.isPanicActive(profiles, now)

    /** Si el motivo de fondo es el límite agotado, la pantalla de bloqueo lo dice y cuándo se reinicia. */
    private fun limitDecision(decision: BlockDecision.Block, limit: UsageLimit, localNow: LocalDateTime): BlockDecision.Block {
        // El uso continuo se libera tras un descanso cuya hora depende de cuándo se deje la app: no se puede avisar.
        val resetAt = if (limit.kind == LimitKind.CONTINUOUS) null
        else Periods.nextReset(limit.period, localNow, dayStartMinute).atZone(zone).toInstant()
        return decision.copy(reason = BlockReason.LIMIT_REACHED, availableAt = resetAt)
    }

    /** Activo ahora y, si tiene límite, con el límite agotado (en un límite por app, agotado en alguna). */
    private fun isBlocking(
        profile: Profile,
        now: Instant,
        localNow: LocalDateTime,
        usage: Map<Long, ProfileUsage>,
        targetUsage: Map<UsageKey, ProfileUsage>,
        context: DeviceContext,
    ): Boolean {
        if (!ProfileActivation.isActive(profile, localNow, now, context)) return false
        val limit = profile.limit ?: return true
        return when (limit.scope) {
            LimitScope.SHARED -> exhausted(limit, usage[profile.id], now, localNow)
            LimitScope.PER_TARGET -> targetUsage.any { (key, value) -> key.profileId == profile.id && exhausted(limit, value, now, localNow) }
        }
    }

    /** Un perfil con límite solo bloquea cuando se agota: el compartido, o el de la app o web [target] si el límite es por app. */
    private fun limitAllowsBlocking(
        profile: Profile,
        target: String,
        usage: Map<Long, ProfileUsage>,
        targetUsage: Map<UsageKey, ProfileUsage>,
        now: Instant,
        localNow: LocalDateTime,
    ): Boolean {
        val limit = profile.limit ?: return true
        val used = if (limit.scope == LimitScope.PER_TARGET) targetUsage[UsageKey(profile.id, target)] else usage[profile.id]
        return exhausted(limit, used, now, localNow)
    }

    private fun exhausted(limit: UsageLimit, used: ProfileUsage?, now: Instant, localNow: LocalDateTime) =
        LimitEvaluator.isExhausted(limit, used, now.toEpochMilli(), localNow, dayStartMinute, zone)

    private fun decideBlockList(profile: Profile, state: ForegroundState): BlockDecision =
        ProfileTargets.listed(profile, state, categories)?.let { BlockDecision.Block(it.reason, profile.id, it.target) } ?: BlockDecision.Allow

    private fun decideAllowList(profile: Profile, state: ForegroundState): BlockDecision {
        if (state.packageName in profile.apps) return BlockDecision.Allow

        // Un navegador no permitido como app puede dejar pasar webs concretas: se decide por la URL.
        if (state.isBrowser && profile.webs.isNotEmpty()) {
            val url = state.url ?: return BlockDecision.Allow // aún sin dirección legible
            if (WebMatcher.firstMatch(profile.webs, url) != null) return BlockDecision.Allow
            return BlockDecision.Block(BlockReason.NOT_ALLOWED, profile.id, UrlNormalizer.host(url))
        }
        return BlockDecision.Block(BlockReason.NOT_ALLOWED, profile.id, state.packageName)
    }
}
