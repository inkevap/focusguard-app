package com.focusguard.decision

import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryExpansionTest {

    private val table = CategoryTable(
        packages = mapOf(
            "com.instagram.android" to "social", "com.twitter.android" to "social", "com.zhiliaoapp.musically" to "social",
            "com.netflix.mediaclient" to "video",
        ),
        domains = mapOf("instagram.com" to "social", "x.com" to "social", "netflix.com" to "video"),
    )

    private val installed = setOf("com.instagram.android", "com.netflix.mediaclient", "com.otra")

    @Test fun anadeLasAppsInstaladasYTodasLasWebsDeLaCategoria() {
        val added = CategoryExpansion.expand(setOf("social"), table, installed, emptySet(), emptyList())
        assertEquals(setOf("com.instagram.android"), added.apps)
        assertEquals(listOf(WebRule("instagram.com", WebMatchType.EXACT), WebRule("x.com", WebMatchType.EXACT)), added.webs)
    }

    @Test fun noRepiteLoQueYaEsta() {
        val added = CategoryExpansion.expand(
            setOf("social"), table, installed,
            apps = setOf("com.instagram.android"),
            webs = listOf(WebRule("instagram.com", WebMatchType.EXACT)),
        )
        assertEquals(emptySet(), added.apps)
        assertEquals(listOf(WebRule("x.com", WebMatchType.EXACT)), added.webs)
    }

    @Test fun unaReglaWebMasAmpliaYaCubreElDominio() {
        // «contiene instagram» ya bloquea instagram.com: no se añade otra regla.
        val added = CategoryExpansion.expand(setOf("social"), table, installed, emptySet(), listOf(WebRule("instagram", WebMatchType.CONTAINS)))
        assertEquals(listOf(WebRule("x.com", WebMatchType.EXACT)), added.webs)
    }

    @Test fun variasCategoriasSeSumanSinDuplicar() {
        val added = CategoryExpansion.expand(setOf("social", "video"), table, installed, emptySet(), emptyList())
        assertEquals(setOf("com.instagram.android", "com.netflix.mediaclient"), added.apps)
        assertEquals(setOf("instagram.com", "x.com", "netflix.com"), added.webs.map { it.value }.toSet())
        assertEquals(3, added.webs.size)
    }

    @Test fun unaCategoriaVaciaODesconocidaNoAnadeNada() {
        assertTrue(CategoryExpansion.expand(setOf("nada"), table, installed, emptySet(), emptyList()).isEmpty)
        assertTrue(CategoryExpansion.expand(emptySet(), table, installed, emptySet(), emptyList()).isEmpty)
    }

    @Test fun sinAppsInstaladasSoloEntranLasWebs() {
        val added = CategoryExpansion.expand(setOf("social"), table, emptySet(), emptySet(), emptyList())
        assertTrue(added.apps.isEmpty())
        assertEquals(2, added.webs.size)
    }

    @Test fun laTablaListaLoQueTieneCadaCategoria() {
        assertEquals(setOf("netflix.com"), table.domainsOf("video"))
        assertEquals(setOf("com.netflix.mediaclient"), table.packagesOf("video"))
        assertTrue(table.packagesOf("otra").isEmpty())
    }
}
