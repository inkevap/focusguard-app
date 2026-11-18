package com.focusguard.decision

import com.focusguard.decision.BrowserPrep.Choice
import com.focusguard.decision.BrowserPrep.Result
import com.focusguard.decision.BrowserPrep.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserPrepTest {

    private fun t(text: String, clickable: Boolean = false) = UiText(text, clickable)

    private val page = listOf(
        t("Experiments"),
        t("Enable Custom Tabs screenshot capturing"),
        t("#cct-navigation-info-screenshot", true),
        t("Default", true),
        t("Incognito Screenshot"),
        t("Enables Incognito screenshots on Android."),
        t("#incognito-screenshot", true),
        t("Default", true),
        t("Long Screenshots No Memory Check"),
        t("#long-screenshots-no-memory-check", true),
        t("Enabled", true),
    )

    @Test
    fun `encuentra el desplegable de la opcion y no el de otra`() {
        assertEquals(7, BrowserPrep.findSelect(page))
        assertEquals(Choice.DEFAULT, BrowserPrep.choiceOf(page[7].text))
    }

    @Test
    fun `chrome parte el identificador en piezas`() {
        val chrome = listOf(
            t("Incognito Screenshot"), t("#incognito-screenshot", true), t("#", true), t("incognito-screenshot", true), t("Enabled", true),
            t("#temporary-unexpire-flags-m155", true), t("Default", true),
        )
        assertEquals(4, BrowserPrep.findSelect(chrome))
    }

    @Test
    fun `sin la opcion en pantalla no hay desplegable`() {
        assertNull(BrowserPrep.findSelect(page.filterNot { it.text == "#incognito-screenshot" }))
        // El identificador está, pero su desplegable no se ve antes de la siguiente opción.
        assertNull(BrowserPrep.findSelect(listOf(t("#incognito-screenshot"), t("#otra-opcion"), t("Enabled", true))))
    }

    @Test
    fun `reconoce las opciones en ingles y en espanol`() {
        assertEquals(Choice.ENABLED, BrowserPrep.choiceOf(" Enabled "))
        assertEquals(Choice.ENABLED, BrowserPrep.choiceOf("Habilitado"))
        assertEquals(Choice.DISABLED, BrowserPrep.choiceOf("Inhabilitado"))
        assertEquals(Choice.DEFAULT, BrowserPrep.choiceOf("Predeterminado"))
        assertNull(BrowserPrep.choiceOf("Enables Incognito screenshots"))
    }

    @Test
    fun `encuentra la opcion de la lista y el boton de reiniciar`() {
        val popup = listOf(t("Default", true), t("Enabled", true), t("Disabled", true))
        assertEquals(1, BrowserPrep.findOption(popup, Choice.ENABLED))
        val banner = listOf(t("Los cambios se aplicarán la próxima vez que reinicies Chrome."), t("Reiniciar", true))
        assertEquals(1, BrowserPrep.findRelaunch(banner))
        assertNull(BrowserPrep.findRelaunch(listOf(t("Reiniciar", clickable = false))))
    }

    @Test
    fun `guarda y lee los resultados`() {
        val results = mapOf("com.android.chrome" to Result.DONE, "com.brave.browser" to Result.ALREADY)
        assertEquals(results, BrowserPrep.decode(BrowserPrep.encode(results)))
        assertEquals(mapOf("a" to Result.FAILED), BrowserPrep.decode("a=FAILED;b=RARO;=DONE"))
        assertEquals(emptyMap(), BrowserPrep.decode(null))
    }
}
