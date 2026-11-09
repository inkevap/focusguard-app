package com.focusguard.decision

import com.focusguard.model.WebMatchType.CONTAINS
import com.focusguard.model.WebMatchType.EXACT
import com.focusguard.model.WebRule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebMatcherTest {

    @Test
    fun exactoCoincideConElDominioYSusSubdominios() {
        val rule = WebRule("reddit.com", EXACT)
        assertTrue(WebMatcher.matches(rule, "https://www.reddit.com/r/kotlin"))
        assertTrue(WebMatcher.matches(rule, "old.reddit.com"))
    }

    @Test
    fun exactoNoCoincideConDominiosParecidos() {
        val rule = WebRule("reddit.com", EXACT)
        assertFalse(WebMatcher.matches(rule, "notreddit.com"))
        assertFalse(WebMatcher.matches(rule, "example.com/reddit.com"))
    }

    @Test
    fun contieneBuscaEnCualquierParteDeLaUrl() {
        val rule = WebRule("shorts", CONTAINS)
        assertTrue(WebMatcher.matches(rule, "youtube.com/shorts/abc"))
        assertFalse(WebMatcher.matches(rule, "youtube.com/watch?v=abc"))
    }

    @Test
    fun laReglaSeNormalizaComoLaUrl() {
        assertTrue(WebMatcher.matches(WebRule("https://www.Reddit.com/", EXACT), "reddit.com"))
    }

    @Test
    fun reglaVaciaNuncaCoincide() {
        assertFalse(WebMatcher.matches(WebRule("  ", CONTAINS), "example.com"))
    }
}
