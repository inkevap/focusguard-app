package com.focusguard.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import com.focusguard.decision.SensitiveScreens

/** Apps que jamás se bloquean (ESPECIFICACION 2.1): la propia, teléfono, sistema, teclado y lanzador. */
object NeverBlockProvider {

    /** Teclados y sistema: sus ventanas aparecen sobre la app activa y no cambian "lo que se está usando". */
    fun overlayPackages(context: Context): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return imm.enabledInputMethodList.mapTo(mutableSetOf(SensitiveScreens.SYSTEM_UI)) { it.packageName }
    }

    fun build(context: Context): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf(context.packageName, SensitiveScreens.SYSTEM_UI, "com.android.phone", "com.android.emergency")

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(result) { it.activityInfo.packageName }

        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.let(result::add)

        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
            ?.defaultDialerPackage
            ?.let(result::add)

        return result
    }

    /**
     * Cámara y visor de fotos del teléfono: la detección de imágenes no los mira, porque ahí salen las fotos que uno se toma (DECISIONES).
     * Se quitan los navegadores, por si alguno se anuncia como visor de imágenes.
     */
    fun cameraAndGalleryPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val browsers = pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
            PackageManager.MATCH_ALL,
        ).mapTo(mutableSetOf()) { it.activityInfo.packageName }

        val result = mutableSetOf<String>()
        listOf(
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
        ).forEach { intent -> pm.queryIntentActivities(intent, 0).mapTo(result) { it.activityInfo.packageName } }
        // El visor de fotos por defecto (Google Fotos, la galería del fabricante); si no hay uno elegido sale el selector de Android y se ignora.
        pm.resolveActivity(Intent(Intent.ACTION_VIEW).setType("image/*"), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" }
            ?.let(result::add)
        return result - browsers
    }
}
