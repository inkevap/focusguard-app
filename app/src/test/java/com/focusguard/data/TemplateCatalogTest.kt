package com.focusguard.data

import com.focusguard.model.AppCategory
import com.focusguard.model.BlockMode
import java.io.File
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateCatalogTest {

    @Test fun leeUnaPlantillaCompleta() {
        val templates = TemplateCatalog.parse(
            """
            {"version":1,"templates":[{"id":"noche","emoji":"🌙","title":"Noche","description":"d","mode":"allow_only",
              "apps":["a"],"webs":["x.com"],"limitMinutes":30,"schedule":{"days":"weekdays","from":"22:00","to":"07:00"}}]}
            """,
        )
        val t = templates.single()
        assertEquals(BlockMode.ALLOW_ONLY, t.mode)
        assertEquals(setOf("a"), t.apps)
        assertEquals(listOf("x.com"), t.webs.map { it.value })
        assertEquals(30, t.limit?.amount)
        assertEquals(5, t.intervals.size)
        assertEquals(22 * 60, t.intervals.first().startMinute)
        assertEquals(7 * 60, t.intervals.first().endMinute)
    }

    @Test fun losDiasPuedenSerUnaListaDeNumeros() {
        val t = TemplateCatalog.parse("""{"templates":[{"id":"a","title":"A","schedule":{"days":[1,7],"from":"09:00","to":"10:00"}}]}""").single()
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), t.intervals.map { it.day })
    }

    @Test fun unModoOUnosDiasInventadosSonUnError() {
        assertFailsWith<IllegalStateException> { TemplateCatalog.parse("""{"templates":[{"id":"a","title":"A","mode":"raro"}]}""") }
        assertFailsWith<IllegalStateException> { TemplateCatalog.parse("""{"templates":[{"id":"a","title":"A","schedule":{"days":"feriados","from":"09:00","to":"10:00"}}]}""") }
    }

    @Test fun unIdentificadorRepetidoEsUnError() {
        assertFailsWith<IllegalArgumentException> { TemplateCatalog.parse("""{"templates":[{"id":"a","title":"A"},{"id":"a","title":"B"}]}""") }
    }

    /** El archivo genérico que viaja en el APK es válido y solo usa categorías que existen. */
    @Test fun lasPlantillasGenericasDelRepositorioSonValidas() {
        val templates = TemplateCatalog.parse(File("../data/seed/plantillas_genericas.json").readText())
        assertTrue(templates.size >= 11)
        val known = AppCategory.entries.map { it.id }.toSet()
        templates.forEach {
            assertTrue(it.title.isNotBlank() && it.description.isNotBlank() && it.emoji.isNotBlank(), it.id)
            assertTrue(known.containsAll(it.categories), "${it.id}: categoría desconocida")
            assertTrue(it.categories.isNotEmpty() || it.apps.isNotEmpty() || it.webs.isNotEmpty(), "${it.id}: vacía")
            assertTrue(it.mode == BlockMode.BLOCK || it.categories.isEmpty(), "${it.id}: categorías en permitir solo")
        }
        listOf("redes", "video", "juegos", "apuestas", "citas", "noticias", "compras", "estudio", "descanso-nocturno")
            .forEach { id -> assertTrue(templates.any { it.id == id }, "falta $id") }
    }

    /** Las plantillas personales nunca van a git: si alguien quita la regla del `.gitignore`, esto falla. */
    @Test fun lasPlantillasPersonalesEstanIgnoradasPorGit() {
        val ignore = File("../.gitignore").readLines().map { it.trim() }
        assertTrue("app/personal/" in ignore)
    }
}
