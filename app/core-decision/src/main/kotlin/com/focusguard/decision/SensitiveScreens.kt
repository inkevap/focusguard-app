package com.focusguard.decision

import com.focusguard.model.StrictProtection

/** Lo que el servicio de accesibilidad lee de la ventana activa. */
data class ScreenInfo(
    val packageName: String,
    /** Todos los textos visibles (acotados por el servicio). */
    val texts: List<String>,
    /** Identificadores completos de vista (`paquete:id/nombre`) presentes en la ventana. */
    val viewIds: Set<String>,
    /** Clase de la actividad en primer plano; no depende del idioma. */
    val className: String? = null,
)

/**
 * Reconoce pantallas del sistema que el modo estricto protege (ESPECIFICACION 4 y TABLAS 4).
 * Los textos cambian con el idioma y el fabricante, así que se combinan el paquete, el nombre
 * de la propia app (que aparece en ajustes de accesibilidad, administradores, información de
 * la app y confirmación de desinstalar) y una tabla de títulos.
 */
object SensitiveScreens {

    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    private val factoryResetTitles = listOf(
        "factory data reset", "factory reset", "erase all data", "reset phone",
        "restablecer datos de fábrica", "restablecer valores de fábrica", "restablecimiento de fábrica",
        "borrar todos los datos", "restablecer teléfono", "restablecer el teléfono",
    )

    /**
     * La pregunta de confirmar la desinstalación. El botón «Desinstalar» no basta: está siempre en la ficha de la propia app, la abra el
     * usuario para lo que la abra (dar un permiso, ver la batería), y acusarle de intentar desinstalar sería injusto.
     */
    private val uninstallPhrases = listOf(
        "desinstalar esta", "desinstalar la aplicación", "quieres desinstalar", "uninstall this app", "want to uninstall", "uninstall the app",
    )

    /** ¿Es la confirmación de desinstalar la propia app? Distingue el intento de desinstalar del de entrar en otros ajustes. */
    fun isUninstallScreen(screen: ScreenInfo): Boolean =
        screen.packageName in settingsPackages && screen.texts.any { raw ->
            val text = raw.trim().lowercase()
            uninstallPhrases.any { text.contains(it) }
        }

    private val deviceAdminPhrases = listOf(
        "device admin", "administrador del dispositivo", "administradores del dispositivo", "administración del dispositivo", "administrador de dispositivo",
    )

    /**
     * La pantalla de ajustes de FocusGuard como administrador del dispositivo, o la lista de apps de administración donde aparece: por donde
     * se desactiva. Se reconoce por el nombre de la app junto a una frase de administración; la ficha normal de la app no la trae.
     */
    fun isDeviceAdminScreen(screen: ScreenInfo, appLabel: String): Boolean {
        if (screen.packageName !in settingsPackages) return false
        val texts = screen.texts.map { it.lowercase() }
        return texts.any { it.contains(appLabel.lowercase()) } && texts.any { text -> deviceAdminPhrases.any { text.contains(it) } }
    }

    private val accessibilityPhrases = listOf("accesibilidad", "accessibility")

    /**
     * La pantalla de accesibilidad donde aparece FocusGuard (su ficha, con el interruptor de «Usar», o la lista de servicios): por donde se apaga el servicio.
     * Sin servicio no se cierra ninguna pantalla, ni la del administrador, así que apagarlo es el primer paso para desinstalar la app. Se reconoce por el
     * nombre de la app junto a la palabra «accesibilidad»; la ficha normal de la app no la trae.
     */
    fun isAccessibilityScreen(screen: ScreenInfo, appLabel: String): Boolean {
        if (screen.packageName !in settingsPackages) return false
        val texts = screen.texts.map { it.lowercase() }
        return texts.any { it.contains(appLabel.lowercase()) } && texts.any { text -> accessibilityPhrases.any { text.contains(it) } }
    }

    private fun squash(text: String) = text.lowercase().filterNot(Char::isWhitespace)

    /**
     * Ahorro extremo de batería (A20): las cadenas de `TABLAS_Y_ALGORITMO.md` 4, sin las que otra contiene (se comparan por subcadena y sin
     * espacios). Incluye «Energiesparmodus» tal como manda la tabla, aunque en alemán nombre también el ahorro normal.
     */
    private val extremeBatterySaverTitles = listOf(
        "Ultra battery saver", "초절전", "סוללה חסכ'", "חיסכון גבוה במיוחד", "ウルトラ省電力", "Ultrabatería", "ahorro de energía ultra",
        "Ultra-Akku", "Akkuopti", "Energiesparmodus", "Ultra-Stromsparen", "Gestion d'alim. Ultra", "gestion d'alimentation Ultra", "Режим Ультра", "ultra power saving",
    ).map(::squash)

    /** Administrador de tareas en segundo plano y cambio de usuario (A17). */
    private val taskManagerTitles = listOf(
        "Running services", "Servicios en ejecución", "Procesos en ejecución", "Add user", "Add guest", "Añadir usuario", "Añadir invitado",
    ).map(::squash)

    /** La tienda de Google: sus diálogos de compra (A15). Las demás tiendas se añaden probando en teléfonos reales. */
    private const val PLAY_STORE = "com.android.vending"
    private val purchaseClassHints = listOf("billing", "acquire", "purchase")

    fun isBillingPackage(packageName: String) = packageName == PLAY_STORE

    private fun isPurchaseDialog(screen: ScreenInfo) =
        isBillingPackage(screen.packageName) && purchaseClassHints.any { screen.className?.contains(it, ignoreCase = true) == true }

    /** La protección de ajustes del sistema cuyo título sale en la pantalla (una sola pasada por los textos, sin repetir la normalización). */
    private fun classifyTitles(texts: List<String>): StrictProtection? {
        val squashed = texts.map(::squash)
        return when {
            squashed.any { text -> extremeBatterySaverTitles.any { text.contains(it) } } -> StrictProtection.BATTERY_SAVER
            squashed.any { text -> taskManagerTitles.any { text.contains(it) } } -> StrictProtection.TASK_MANAGER
            else -> null
        }
    }

    private val platformSettingsPackages = setOf("com.android.settings", "com.samsung.android.settings")

    private val dateTimeTitles = listOf(
        "date & time", "date and time", "set time automatically", "use network-provided time", "time zone",
        "fecha y hora", "establecer hora automáticamente", "hora proporcionada por la red", "zona horaria",
    )

    private val recentsViewIdSuffixes = listOf(":id/recents_container", ":id/overview_panel", ":id/taskView")

    const val SYSTEM_UI = "com.android.systemui"
    private const val MIUI_PLUGIN = "miui.systemui.plugin"
    private val shadeViewIdSuffixes = listOf(":id/notification_panel", ":id/qs_detail_header", ":id/tiles_edit")

    /** ¿Puede ser el menú de apagado o el panel de notificaciones? Evita leer la pantalla del resto de apps. */
    fun isSystemShell(packageName: String) = packageName == SYSTEM_UI || packageName == MIUI_PLUGIN

    /** Por la actividad basta, sin recorrer la pantalla; las vistas cubren los fabricantes que usan otra clase. */
    fun isPowerMenuClass(className: String?) = className?.contains("globalactions", ignoreCase = true) == true

    private fun isPowerMenu(screen: ScreenInfo): Boolean =
        isSystemShell(screen.packageName) && (
            isPowerMenuClass(screen.className) ||
                screen.viewIds.any { it.contains("globalactions", ignoreCase = true) || it == "$MIUI_PLUGIN:id/touch_container" }
            )

    /** Identificadores completos del panel y del menú de Xiaomi, para buscarlos sin recorrer toda la pantalla. */
    val shellViewIds: List<String> = shadeViewIdSuffixes.map { SYSTEM_UI + it } + "$MIUI_PLUGIN:id/touch_container"

    fun isSettingsPackage(packageName: String) = packageName in settingsPackages

    /**
     * Pantallas de fecha, hora y zona horaria. Cambiar el reloj a mano acorta esperas y reinicia
     * límites, así que se cierran mientras haya algo que proteger.
     */
    fun isPlatformSettings(packageName: String) = packageName in platformSettingsPackages

    /**
     * Por nombre de actividad, o por al menos dos títulos distintos: la página «Sistema» solo
     * lista una fila «Fecha y hora» y no debe contar.
     */
    fun isDateTimeSettings(screen: ScreenInfo): Boolean {
        if (screen.packageName !in platformSettingsPackages) return false
        if (screen.className?.contains("DateTime", ignoreCase = true) == true) return true
        val texts = screen.texts.map { it.lowercase() }
        return dateTimeTitles.count { title -> texts.any { it.contains(title) } } >= 2
    }

    /** La protección que corresponde a esta pantalla, o null si no es sensible. */
    fun classify(screen: ScreenInfo, appLabel: String): StrictProtection? {
        if (screen.viewIds.any { id -> recentsViewIdSuffixes.any { id.endsWith(it) } }) {
            return StrictProtection.RECENTS
        }
        if (isPowerMenu(screen)) return StrictProtection.POWER_MENU
        if (screen.packageName == SYSTEM_UI && screen.viewIds.any { id -> shadeViewIdSuffixes.any { id.endsWith(it) } }) {
            return StrictProtection.NOTIFICATION_SHADE
        }
        if (isPurchaseDialog(screen)) return StrictProtection.PURCHASE_DIALOGS
        if (screen.packageName !in settingsPackages) return null
        classifyTitles(screen.texts)?.let { return it }

        val texts = screen.texts.map { it.lowercase() }
        if (texts.any { it.contains(appLabel.lowercase()) }) return StrictProtection.SETTINGS_GUARD
        if (screen.packageName in platformSettingsPackages && containsAny(texts, factoryResetTitles)) {
            return StrictProtection.SETTINGS_GUARD
        }
        return null
    }

    private fun containsAny(lowercaseTexts: List<String>, titles: List<String>) =
        lowercaseTexts.any { text -> titles.any { text.contains(it) } }
}
