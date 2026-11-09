package com.focusguard.ui.help

import com.focusguard.ui.Routes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpTopicsTest {

    /** Cada función que el usuario puede abrir tiene su ficha: si se añade una y se olvida la ayuda, esto falla. */
    private val required = listOf(
        "perfiles", "horarios", "limites", "categorias", "bloquear-ya", "enfoque", "dormir-ayudame", "adulto", "dns",
        "aprobaciones", "companero", "modo-estricto", "emergencia", "impulso", "racha", "estadisticas", "copia", "permisos",
        "desinstalar", "privacidad",
        "imagenes", "adulto-apps", "bloqueo-apps", "bloqueo-webs", "subapps", "condiciones", "plantillas", "atajos", "notificaciones", "insights", "recuperacion", "pantalla-bloqueo", "pin-icono", "varios-companeros", "parental", "ayudas", "sincronizacion", "ajustes", "asistente",
    )

    @Test fun cadaFuncionTieneSuFicha() {
        val ids = HelpTopics.all.map { it.id }
        assertTrue(ids.containsAll(required), "faltan: ${required - ids.toSet()}")
        assertEquals(ids.size, ids.toSet().size, "ids repetidos")
    }

    @Test fun todasLasFichasEstanCompletas() {
        HelpTopics.all.forEach {
            assertTrue(it.title.isNotBlank() && it.summary.isNotBlank() && it.example.isNotBlank(), it.id)
        }
    }

    @Test fun lasRutasDeLasFichasSonDePantallasQueExisten() {
        val known = setOf(
            Routes.QUICK_BLOCK, "session/pomodoro", Routes.PANIC, Routes.ADULT, Routes.DNS, "approvals", Routes.ACCOUNT, "strict",
            Routes.EMERGENCY, Routes.URGE, Routes.STREAK, Routes.WEEKLY, Routes.BACKUP, Routes.TROUBLESHOOTER, "permissions", Routes.SLEEP, Routes.NOTIFICATIONS, Routes.TEMPLATES, Routes.RECOVERY,
        )
        HelpTopics.all.mapNotNull { it.route }.forEach { assertTrue(it in known, it) }
    }

    @Test fun laFichaDeDormirYAyudameDiceLaDiferencia() {
        val topic = HelpTopics.byId("dormir-ayudame")!!
        val text = (listOf(topic.summary) + topic.notes).joinToString(" ")
        // La diferencia real: Dormir termina a una hora y deja la alarma; Ayúdame es para un impulso y no deja nada.
        assertTrue("hora de despertar" in text && "un impulso" in text)
        assertTrue("alarma" in text && ("no se puede quitar" in text.lowercase() || "no se pueden deshacer" in text.lowercase()))
    }

    @Test fun elBuscadorNoDistingueAcentosNiMayusculasYExigeTodasLasPalabras() {
        assertTrue(HelpTopics.search("COMPAÑERO").any { it.id == "companero" })
        assertTrue(HelpTopics.search("companero").any { it.id == "companero" })
        assertTrue(HelpTopics.search("estricto pin").any { it.id == "modo-estricto" })
        assertTrue(HelpTopics.search("zzzz nada").isEmpty())
        assertEquals(HelpTopics.all, HelpTopics.search("  "))
    }

    @Test fun seEncuentraPorPalabrasQueNoSonElTitulo() {
        assertTrue(HelpTopics.search("pomodoro").any { it.id == "enfoque" })
        assertTrue(HelpTopics.search("vpn").any { it.id == "dns" })
        assertTrue(HelpTopics.search("manga").any { it.id == "adulto" })
    }
}
