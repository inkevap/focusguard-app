package com.focusguard.data

/** Qué dejó una consulta de solicitudes: si alguna quedó respondida y qué se aplicó solo o no se pudo aplicar. */
data class ApprovalsReport(val resolved: Boolean, val applied: List<String> = emptyList(), val failed: List<String> = emptyList()) {
    /** Hay algo que contarle al usuario. */
    val worthTelling: Boolean get() = resolved || applied.isNotEmpty() || failed.isNotEmpty()
}
