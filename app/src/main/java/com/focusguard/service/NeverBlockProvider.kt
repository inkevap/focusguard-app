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
}
