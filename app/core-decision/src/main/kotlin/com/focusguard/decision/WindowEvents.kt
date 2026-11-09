package com.focusguard.decision

/** Qué eventos de ventana del servicio de accesibilidad hablan de una app que de verdad se está usando. */
object WindowEvents {

    /**
     * Un evento «ventana cambiada» de [eventPackage] que **no tiene ninguna ventana en pantalla** mientras la activa es de [activePackage] no es una app
     * abierta: es un elemento suelto de otra app que vive dentro de la activa (el buscador de Google en el lanzador, un widget). Comprobado en un Motorola
     * con Android 15: al volver al inicio llega uno de `com.google.android.googlequicksearchbox` (clase `FrameLayout`) con el lanzador como única ventana activa,
     * y con «Ayúdame» (todo bloqueado menos lo permitido) se enseñaba «Google no está en tu lista de permitidos».
     *
     * [windowPackages] se pide solo si hace falta (el paquete del evento no es el de la ventana activa), porque leer las ventanas cuesta.
     */
    fun isGhost(eventPackage: String, activePackage: String, windowPackages: () -> List<String>): Boolean {
        if (eventPackage == activePackage) return false
        return eventPackage !in windowPackages()
    }
}
