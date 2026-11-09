package com.focusguard.data

import com.focusguard.model.AppCategory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryCatalogTest {

    @Test
    fun laTablaDeSemillaSeLeeYLasCategoriasSeConocen() {
        val csv = File("../data/seed/apps_webs_categories.csv").readText()
        val table = CategoryCatalog.parse(csv)
        assertEquals("shopping", table.ofPackage("com.alibaba.aliexpresshd"))
        assertEquals("shopping", table.ofUrl("https://es.aliexpress.com/item"))
        assertNull(table.ofPackage("com.accuweather.android"), "«none» no sirve para bloquear")
        val used = csv.lines().filter { it.isNotBlank() }.map { it.split(',')[2].trim() }.toSet() - setOf("none", "browsers", "tools")
        val labelled = AppCategory.entries.map { it.id }.toSet()
        assertTrue(labelled.containsAll(used), "categoría sin etiqueta: ${used - labelled}")
    }

    @Test
    fun ignoraLineasIncompletas() {
        val table = CategoryCatalog.parse("a.b,site.com,social\n\nmalformada\n,otra.com,video\n")
        assertEquals("social", table.ofPackage("a.b"))
        assertEquals("video", table.ofUrl("otra.com"))
    }
}
