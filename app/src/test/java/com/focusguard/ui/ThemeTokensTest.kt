package com.focusguard.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

/**
 * La paleta tiene una sola fuente, `data/theme/tokens.json`, que también usan los correos y las páginas del servidor.
 * Esta prueba lee `Theme.kt` como texto y falla si un color de la app y el del JSON difieren, para que nadie cambie uno sin el otro.
 */
class ThemeTokensTest {
    private val theme = File("src/main/java/com/focusguard/ui/theme/Theme.kt").readText()
    private val tokens = JSONObject(File("../data/theme/tokens.json").readText())

    private fun block(name: String): String =
        Regex("private val $name: ColorScheme = \\w+ColorScheme\\((.*?)\\n\\)", RegexOption.DOT_MATCHES_ALL).find(theme)!!.groupValues[1]

    private fun colorsOf(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        Regex("(\\w+)\\s*=\\s*Color\\((0x[0-9A-Fa-f]{8})\\)").findAll(text).forEach { out[it.groupValues[1]] = "#" + it.groupValues[2].substring(4).uppercase() }
        Regex("(\\w+)\\s*=\\s*Color\\.White").findAll(text).forEach { out[it.groupValues[1]] = "#FFFFFF" }
        return out
    }

    private fun check(scheme: String, name: String) {
        val inApp = colorsOf(block(name))
        val inJson = tokens.getJSONObject(scheme)
        assertTrue(inApp.isNotEmpty(), "no se leyó la paleta $name de Theme.kt")
        assertEquals(inApp.keys, inJson.keys().asSequence().toSet(), "mismos colores en $name y en tokens.json")
        inApp.forEach { (key, hex) -> assertEquals(hex, inJson.getString(key), "$scheme.$key") }
    }

    @Test fun losColoresClarosDeLaAppSonLosDelJson() = check("light", "Light")

    @Test fun losColoresOscurosDeLaAppSonLosDelJson() = check("dark", "Dark")

    @Test fun elDegradadoDeLaMarcaCoincide() {
        for ((scheme, name) in listOf("light" to "LightBrand", "dark" to "DarkBrand")) {
            val line = Regex("private val $name = BrandColors\\((.*)\\)").find(theme)!!.groupValues[1]
            val values = Regex("Color\\((0x[0-9A-Fa-f]{8})\\)|(Color\\.White)").findAll(line)
                .map { if (it.groupValues[2].isNotEmpty()) "#FFFFFF" else "#" + it.groupValues[1].substring(4).uppercase() }.toList()
            val brand = tokens.getJSONObject("brand").getJSONObject(scheme)
            assertEquals(listOf(brand.getString("start"), brand.getString("end"), brand.getString("onBrand")), values, "degradado $scheme")
        }
    }
}
