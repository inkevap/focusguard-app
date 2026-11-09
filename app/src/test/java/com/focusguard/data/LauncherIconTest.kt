package com.focusguard.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cada icono de la lista tiene su alias en el manifiesto, y solo el de siempre empieza activo. */
class LauncherIconTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun aliasBlock(alias: String): String {
        val start = manifest.indexOf("android:name=\".$alias\"")
        assertTrue(start >= 0, "falta el alias $alias en el manifiesto")
        return manifest.substring(start, manifest.indexOf("</activity-alias>", start))
    }

    @Test
    fun cadaIconoTieneSuAliasQueAbreLaActividadPrincipal() {
        LauncherIcon.entries.forEach {
            val block = aliasBlock(it.alias)
            assertTrue("android:targetActivity=\".MainActivity\"" in block, it.alias)
            assertTrue("android.intent.category.LAUNCHER" in block, it.alias)
            assertTrue("android.app.shortcuts" in block, it.alias)
        }
    }

    @Test
    fun soloElIconoDeSiempreEmpiezaActivo() {
        val enabled = LauncherIcon.entries.filter { "android:enabled=\"true\"" in aliasBlock(it.alias) }
        assertEquals(listOf(LauncherIcon.DEFAULT), enabled)
    }

    @Test
    fun laActividadPrincipalNoEsLanzadoraPorSiSola() {
        // Si lo fuera, la app tendría dos iconos a la vez.
        val main = manifest.substring(manifest.indexOf("android:name=\".MainActivity\""), manifest.indexOf("</activity>", manifest.indexOf("android:name=\".MainActivity\"")))
        assertTrue("android.intent.category.LAUNCHER" !in main)
    }
}
