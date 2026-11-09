package com.focusguard.ui

import com.focusguard.decision.Approvals
import com.focusguard.model.ApprovalMode
import com.focusguard.model.StrictState

/** Título corto de cada modo de aprobación, igual en Protección y en Aprobaciones. */
internal fun ApprovalMode.label() = when (this) {
    ApprovalMode.NONE -> "Sin aprobación"
    ApprovalMode.DELAY_24H -> "Esperar 24 horas"
    ApprovalMode.DELAY_72H -> "Esperar 72 horas"
    ApprovalMode.LONG_PHRASE -> "Escribir una frase larga"
    ApprovalMode.STEPS -> "Caminar ${Approvals.DEFAULT_STEPS} pasos"
    ApprovalMode.PARTNER -> "Que lo apruebe mi compañero"
}

internal fun ApprovalMode.hint() = when (this) {
    ApprovalMode.NONE -> "Apagar es inmediato."
    ApprovalMode.DELAY_24H -> "Pides apagarlo y se aplica pasado un día."
    ApprovalMode.DELAY_72H -> "Igual, pero tres días."
    ApprovalMode.LONG_PHRASE -> "Copiarla a mano; no se puede pegar."
    ApprovalMode.STEPS -> "Con el teléfono encima."
    ApprovalMode.PARTNER -> "Tu compañero recibe un correo y aprueba o rechaza. Caduca a las 48 horas."
}

internal fun StrictState.label() = when (this) {
    StrictState.INACTIVE -> "Desactivado"
    StrictState.WAITING -> "En espera, fuera de su horario"
    StrictState.ACTIVE -> "Activo ahora"
    StrictState.PAUSED -> "En pausa"
}
