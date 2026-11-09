package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileTemplateTest {

    private val table = CategoryTable(
        packages = mapOf("com.instagram.android" to "social", "com.twitter.android" to "social"),
        domains = mapOf("instagram.com" to "social", "x.com" to "social"),
    )

    private val social = ProfileTemplate(
        id = "redes", emoji = "📵", title = "Redes sociales", description = "d",
        categories = setOf("social"), limit = UsageLimit(LimitKind.TIME, 45, LimitPeriod.DAILY),
    )

    @Test fun elPerfilTrazaLosDatosDeLaPlantillaYSuCategoria() {
        val profile = social.toProfile(table, setOf("com.instagram.android", "com.otra"))
        assertEquals(0, profile.id)
        assertEquals("Redes sociales", profile.title)
        assertEquals("📵", profile.emoji)
        assertEquals(setOf("social"), profile.categories)
        assertEquals(UsageLimit(LimitKind.TIME, 45, LimitPeriod.DAILY), profile.limit)
        assertEquals(setOf("com.instagram.android"), profile.apps)
        assertEquals(setOf("instagram.com", "x.com"), profile.webs.map { it.value }.toSet())
        assertTrue(profile.enabled)
    }

    @Test fun lasAppsPropiasSoloEntranSiEstanInstaladas() {
        val template = social.copy(categories = emptySet(), apps = setOf("a", "b"))
        assertEquals(setOf("a"), template.toProfile(table, setOf("a")).apps)
    }

    @Test fun enPermitirSoloNoHayCategoriasNiSeAnadeNada() {
        val template = ProfileTemplate(
            "estudio", "📚", "Estudio", "d", BlockMode.ALLOW_ONLY, categories = setOf("social"), apps = setOf("keep"),
            intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 16 * 60, 20 * 60)),
        )
        val profile = template.toProfile(table, setOf("keep", "com.instagram.android"))
        assertEquals(BlockMode.ALLOW_ONLY, profile.mode)
        assertEquals(emptySet(), profile.categories)
        assertEquals(setOf("keep"), profile.apps)
        assertEquals(emptyList(), profile.webs)
        assertEquals(1, profile.intervals.size)
    }

    @Test fun lasWebsPropiasNoSeRepitenConLasDeLaCategoria() {
        val template = social.copy(webs = listOf(WebRule("instagram.com", WebMatchType.EXACT)))
        val webs = template.toProfile(table, emptySet()).webs.map { it.value }
        assertEquals(listOf("instagram.com", "x.com"), webs)
    }
}
