package com.focusguard.model

/**
 * Una sección dentro de una app (Shorts, Reels, Stories…). Se reconoce por identificadores de vista
 * (sin el paquete) o, si la app no los expone, por la actividad en pantalla y un texto visible.
 * Los identificadores cambian con las versiones de cada app, por eso la tabla es datos y no código.
 */
data class SubAppRule(
    val id: String,
    val label: String,
    val packages: Set<String>,
    val viewIds: Set<String> = emptySet(),
    val activity: String? = null,
    val texts: Set<String> = emptySet(),
)
