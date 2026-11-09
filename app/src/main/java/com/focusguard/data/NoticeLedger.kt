package com.focusguard.data

import android.content.Context
import com.focusguard.decision.Notice

/** Qué avisos ya se dieron. Se guarda en preferencias para que reiniciar el servicio no los repita. */
class NoticeLedger(context: Context) {

    private val prefs = context.getSharedPreferences("notice_ledger", Context.MODE_PRIVATE)

    /** Por orden de aviso: al llegar al tope se olvidan los más antiguos, que ya no están en su ventana. */
    private val seen = ArrayDeque((prefs.getString(KEY, null) ?: "").split(SEPARATOR).filter { it.isNotEmpty() })

    /** De los avisos [due], los que aún no se dieron. */
    fun unseen(due: List<Notice>): List<Notice> = due.filter { it.key !in seen }

    /** Anota un aviso como dado, tras mostrarlo. Cada clave lleva su horario o periodo, así que no se repite sola. */
    fun markSeen(notice: Notice) {
        if (notice.key in seen) return
        seen.addLast(notice.key)
        while (seen.size > MAX_KEPT) seen.removeFirst()
        prefs.edit().putString(KEY, seen.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val KEY = "seen_ordered"
        const val SEPARATOR = "\n"
        const val MAX_KEPT = 200
    }
}
