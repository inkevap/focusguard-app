package com.focusguard.decision

import com.focusguard.model.Profile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootShieldTest {
    private val now = Instant.parse("2026-03-10T10:00:00Z")
    private val local = LocalDateTime.ofInstant(now, ZoneOffset.UTC)

    private fun profile(enabled: Boolean = true) = Profile(id = 1, title = "x", enabled = enabled)

    @Test fun pasadosCincoMinutosSeRetira() {
        assertFalse(BootShield.isExpired(BootShield.MAX_AGE_MS - 1))
        assertTrue(BootShield.isExpired(BootShield.MAX_AGE_MS))
    }

    @Test fun unRelojDelArranqueNegativoNoSeAcepta() {
        assertTrue(BootShield.isExpired(-1))
    }

    @Test fun laReglaCompleta() {
        assertTrue(BootShield.shouldShow(20_000, serviceReady = false, somethingToProtect = true))
        assertFalse(BootShield.shouldShow(20_000, serviceReady = true, somethingToProtect = true))
        assertFalse(BootShield.shouldShow(20_000, serviceReady = false, somethingToProtect = false))
        assertFalse(BootShield.shouldShow(BootShield.MAX_AGE_MS, serviceReady = false, somethingToProtect = true))
    }

    @Test fun unPerfilSoloDeUnaWifiOUnLugarTambienJustificaElEscudo() {
        val home = Profile(id = 2, title = "Casa", wifiNames = setOf("Casa"))
        assertTrue(BootShield.hasSomethingToProtect(listOf(home), strictActive = false, local, now))
    }

    @Test fun unPerfilQueBloqueaAhoraHaceFaltaElEscudo() {
        assertTrue(BootShield.hasSomethingToProtect(listOf(profile()), strictActive = false, local, now))
    }

    @Test fun unPerfilApagadoNoLoJustifica() {
        assertFalse(BootShield.hasSomethingToProtect(listOf(profile(enabled = false)), strictActive = false, local, now))
    }

    @Test fun sinPerfilesNiModoEstrictoNoHaceFalta() {
        assertFalse(BootShield.hasSomethingToProtect(emptyList(), strictActive = false, local, now))
    }

    @Test fun elModoEstrictoActivoBasta() {
        assertTrue(BootShield.hasSomethingToProtect(emptyList(), strictActive = true, local, now))
    }

    @Test fun unBloqueoDePanicoEnCursoBasta() {
        val panic = Profile.panic(now.plusSeconds(600))
        assertTrue(BootShield.hasSomethingToProtect(listOf(panic), strictActive = false, local, now))
    }
}
