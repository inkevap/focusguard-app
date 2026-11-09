package com.focusguard.lock

import android.content.Context
import com.focusguard.R
import com.focusguard.model.BlockReason
import com.focusguard.model.LockMotivation
import com.focusguard.model.LockPalette
import com.focusguard.model.LockPalettes
import com.focusguard.model.LockStyle
import com.focusguard.ui.formatLocal
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Lo que muestra la pantalla de bloqueo, ya resuelto: colores, textos y la hora de desbloqueo si se conoce. */
data class LockScreenContent(
    val palette: LockPalette,
    val emoji: String,
    val body: String,
    val unlockLabel: String?,
    val motivation: String?,
    /** Otro título que «Bloqueado» (el bloqueo de arranque). */
    val title: String? = null,
) {
    companion object {
        /**
         * [displayName] es el nombre visible del objetivo (de la app o de la web), [now] la hora de confianza y
         * [motivationIndex] qué frase toca si el estilo las rota. [availableAt] es nulo si no se conoce la hora.
         */
        fun of(
            context: Context,
            reason: BlockReason,
            target: String,
            availableAt: Instant?,
            displayName: String,
            style: LockStyle,
            now: Instant,
            motivationIndex: Int = Random.nextInt(LockMotivation.phrases.size),
            zone: ZoneId = ZoneId.systemDefault(),
            /** Los perfiles que bloquean, ya con su nombre; vacío si se desconocen o el motivo no es de un perfil. */
            blockers: List<String> = emptyList(),
            /** Cuántos perfiles más bloquean y no se nombran. */
            moreBlockers: Int = 0,
            /** Con qué coincidió el bloqueo (las palabras del filtro adulto); se añade al texto de serie, no a un mensaje que el usuario escribió. */
            detail: String? = null,
        ) = LockScreenContent(
            palette = LockPalettes.byId(style.paletteId),
            emoji = style.emoji,
            body = style.messageFor(reason, target)
                ?: (bodyFor(context, reason, displayName, blockers, moreBlockers) + detailSuffix(context, reason, detail)),
            unlockLabel = availableAt?.let { unlockLabel(context, it, now, zone) }
                ?: if (reason == BlockReason.LIMIT_REACHED) context.getString(R.string.lock_unlock_rest) else null,
            motivation = style.guardNoteFor(reason, target) ?: if (style.rotating) LockMotivation.pick(motivationIndex) else null,
        )

        /** Con perfiles conocidos y un motivo de perfil, el texto dice cuáles; si no, el genérico de siempre. */
        private fun bodyFor(context: Context, reason: BlockReason, displayName: String, blockers: List<String>, more: Int): String {
            val named = LockScreenView.byRes(reason)
            if (blockers.isEmpty() || !reason.fromProfile || named == null) return context.getString(LockScreenView.reasonRes(reason), displayName)
            val list = blockers.joinToString(", ").let { if (more > 0) context.getString(R.string.lock_blockers_more, it, more) else it }
            return context.getString(named, displayName, list)
        }

        private fun detailSuffix(context: Context, reason: BlockReason, detail: String?): String =
            if (reason == BlockReason.ADULT_CONTENT && !detail.isNullOrBlank()) context.getString(R.string.lock_adult_detail, detail) else ""

        private fun unlockLabel(context: Context, at: Instant, now: Instant, zone: ZoneId): String {
            val sameDay = at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
            return context.getString(if (sameDay) R.string.lock_unlock_today else R.string.lock_unlock_later, formatLocal(at, withDay = !sameDay, zone = zone))
        }
    }
}
