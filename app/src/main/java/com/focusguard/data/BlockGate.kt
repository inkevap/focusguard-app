package com.focusguard.data

/**
 * Deja que otras partes de la app pregunten al servicio de accesibilidad si una app está bloqueada ahora. El servicio
 * lo rellena al conectarse y lo vacía al apagarse: sin servicio no hay bloqueo, así que se responde que no. Se pregunta por la app entera, sin dirección: un navegador bloqueado solo por una web cuenta como no bloqueado.
 */
class BlockGate {

    @Volatile
    private var check: ((String) -> Boolean)? = null

    fun install(check: (String) -> Boolean) { this.check = check }

    fun clear() { check = null }

    fun isBlocked(packageName: String): Boolean = check?.invoke(packageName) == true
}
