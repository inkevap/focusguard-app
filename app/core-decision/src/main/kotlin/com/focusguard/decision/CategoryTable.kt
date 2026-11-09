package com.focusguard.decision

/**
 * Qué categoría tiene cada app y cada dominio. [packages] va de paquete a categoría y [domains] de dominio a categoría;
 * una web coincide si su dirección es el dominio o un subdominio suyo.
 */
class CategoryTable(private val packages: Map<String, String>, private val domains: Map<String, String>) {

    fun ofPackage(packageName: String): String? = packages[packageName]

    /** Los paquetes y los dominios que la tabla tiene en [category]. */
    fun packagesOf(category: String): Set<String> = packages.filterValues { it == category }.keys

    fun domainsOf(category: String): Set<String> = domains.filterValues { it == category }.keys

    /** Prueba de la dirección completa al dominio principal («m.instagram.com», «instagram.com») sin llegar a la terminación sola. */
    fun ofUrl(url: String): String? =
        generateSequence(UrlNormalizer.host(url)) { it.substringAfter('.', "") }
            .takeWhile { '.' in it }
            .firstNotNullOfOrNull { domains[it] }

    companion object {
        val EMPTY = CategoryTable(emptyMap(), emptyMap())
    }
}
