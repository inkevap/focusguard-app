package com.focusguard.data

import java.io.File
import org.json.JSONArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubAppCatalogTest {

    private val seed = File("../data/seed/subapps.json")

    @Test
    fun laTablaDeSemillaSeLeeEntera() {
        val catalog = SubAppCatalog.parse(JSONArray(seed.readText()))
        assertTrue(catalog.rules.size >= 12)
        assertEquals(catalog.rules.size, catalog.rules.map { it.id }.toSet().size, "los identificadores no se repiten")
        catalog.rules.forEach {
            assertTrue(it.packages.isNotEmpty(), "${it.id} sin paquetes")
            assertTrue(it.viewIds.isNotEmpty() || it.activity != null, "${it.id} sin forma de reconocerse")
        }
        val shorts = assertNotNull(catalog.rules.firstOrNull { it.id == "YOUTUBE_SHORTS" })
        assertTrue("app.revanced.android.youtube" in shorts.packages)
        assertTrue(catalog.detector.covers("com.instagram.android"))
    }
}
