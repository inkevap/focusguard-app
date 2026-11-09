package com.focusguard.decision

/**
 * Navegadores dentro de otras apps (B8): una app que abre las webs en su propia vista (Facebook, Instagram, Line…)
 * se salta el bloqueo de webs del navegador. Con esto se bloquea la app mientras enseña una vista web, para las apps
 * elegidas o para todas.
 */
data class EmbeddedBrowserPolicy(
    val enabled: Boolean = false,
    /** En todas las apps, no solo en [apps]. */
    val allApps: Boolean = false,
    val apps: Set<String> = emptySet(),
) {
    fun applies(packageName: String) = enabled && (allApps || packageName in apps)

    /** Para guardarlo en una solicitud de aprobación: «activado,todas|paquete,paquete». */
    fun encode(): String = "$enabled,$allApps|${apps.sorted().joinToString(",")}"

    companion object {
        /** Null si el texto no sale de [encode]: una solicitud con otro contenido no se aplica. */
        fun decode(text: String): EmbeddedBrowserPolicy? {
            val (flags, list) = text.split('|', limit = 2).takeIf { it.size == 2 } ?: return null
            val (enabled, all) = flags.split(',').takeIf { it.size == 2 } ?: return null
            return EmbeddedBrowserPolicy(
                enabled = enabled.toBooleanStrictOrNull() ?: return null,
                allApps = all.toBooleanStrictOrNull() ?: return null,
                apps = list.split(',').filter { it.isNotEmpty() }.toSet(),
            )
        }
    }
}

object EmbeddedBrowsers {

    /** Apps muy usadas que abren las webs en su propia vista; se ofrecen como sugerencia si están instaladas. */
    val KNOWN = listOf(
        "com.facebook.katana", "com.facebook.orca", "com.instagram.android", "jp.naver.line.android",
        "com.twitter.android", "com.linkedin.android", "com.snapchat.android", "org.telegram.messenger",
        "com.zhiliaoapp.musically", "com.reddit.frontpage", "com.pinterest",
    )

    /** Clase de la vista web de Android. */
    const val WEB_VIEW_CLASS = "android.webkit.WebView"
}
