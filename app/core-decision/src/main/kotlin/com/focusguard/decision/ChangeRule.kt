package com.focusguard.decision

enum class ChangeOutcome { ALLOWED, LOCKED, NEEDS_APPROVAL }

/** Qué pasa con un cambio que debilita una protección: el modo estricto manda sobre las aprobaciones. */
object ChangeRule {
    fun decide(locked: Boolean, approvalRequired: Boolean): ChangeOutcome = when {
        locked -> ChangeOutcome.LOCKED
        approvalRequired -> ChangeOutcome.NEEDS_APPROVAL
        else -> ChangeOutcome.ALLOWED
    }
}
