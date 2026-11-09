package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncMergeTest {
    private val off = BackupSettings()

    @Test fun losInterruptoresSoloSeEnciendenYNuncaSeApagan() {
        val merged = SyncMerge.merge(BackupSettings(adultBlock = true), BackupSettings(foldFloating = true))
        assertTrue(merged.adultBlock)
        assertTrue(merged.foldFloating)
        assertFalse(SyncMerge.merge(off, off).adultBlock)
    }

    @Test fun lasPalabrasSeUnenYNoSeQuitanAlSincronizar() {
        val merged = SyncMerge.merge(BackupSettings(adultWords = setOf("a", "b")), BackupSettings(adultWords = setOf("b", "c")))
        assertEquals(setOf("a", "b", "c"), merged.adultWords)
        // Quitar una palabra en un teléfono no la quita en el otro: sigue en la unión.
        assertEquals(setOf("a", "b"), SyncMerge.merge(BackupSettings(adultWords = setOf("a", "b")), off).adultWords)
    }

    @Test fun conMuchasPalabrasSeConservanLasPrimerasEnOrdenYEsDeterminista() {
        val many = (1..SyncMerge.MAX_WORDS + 50).map { "p%05d".format(it) }.toSet()
        val one = SyncMerge.merge(BackupSettings(adultWords = many), off)
        val other = SyncMerge.merge(off, BackupSettings(adultWords = many))
        assertEquals(SyncMerge.MAX_WORDS, one.adultWords.size)
        assertEquals(one, other)
        assertTrue("p00001" in one.adultWords)
    }

    @Test fun lasAppsNuevasQuedanConLaPoliticaMasEstricta() {
        val on24 = NewAppsPolicy(enabled = true, hours = 24)
        val on72 = NewAppsPolicy(enabled = true, hours = 72)
        val off24 = NewAppsPolicy(enabled = false, hours = 24)
        assertEquals(on72, SyncMerge.merge(BackupSettings(newApps = on24), BackupSettings(newApps = on72)).newApps)
        assertEquals(on72, SyncMerge.merge(BackupSettings(newApps = on72), BackupSettings(newApps = on24)).newApps)
        assertEquals(on24, SyncMerge.merge(BackupSettings(newApps = off24), BackupSettings(newApps = on24)).newApps)
        assertEquals(on24, SyncMerge.merge(BackupSettings(newApps = on24), BackupSettings(newApps = off24)).newApps)
        assertEquals(on24, SyncMerge.merge(BackupSettings(newApps = on24), off).newApps)
        assertNull(SyncMerge.merge(off, off).newApps)
    }

    @Test fun losNavegadoresIntegradosSeUnenSinAflojar() {
        val some = EmbeddedBrowserPolicy(enabled = true, apps = setOf("x"))
        val other = EmbeddedBrowserPolicy(enabled = true, apps = setOf("y"))
        val all = EmbeddedBrowserPolicy(enabled = true, allApps = true)
        val disabled = EmbeddedBrowserPolicy(enabled = false, apps = setOf("z"))
        assertEquals(setOf("x", "y"), SyncMerge.merge(BackupSettings(embeddedBrowsers = some), BackupSettings(embeddedBrowsers = other)).embeddedBrowsers!!.apps)
        assertTrue(SyncMerge.merge(BackupSettings(embeddedBrowsers = some), BackupSettings(embeddedBrowsers = all)).embeddedBrowsers!!.allApps)
        val withDisabled = SyncMerge.merge(BackupSettings(embeddedBrowsers = some), BackupSettings(embeddedBrowsers = disabled)).embeddedBrowsers!!
        assertTrue(withDisabled.enabled)
        assertEquals(setOf("x"), withDisabled.apps)
        assertFalse(SyncMerge.merge(BackupSettings(embeddedBrowsers = disabled), BackupSettings(embeddedBrowsers = disabled)).embeddedBrowsers!!.enabled)
    }

    @Test fun elInicioDelDiaNoSeSincroniza() {
        assertNull(SyncMerge.merge(BackupSettings(dayStartMinute = 360), BackupSettings(dayStartMinute = 120)).dayStartMinute)
    }

    @Test fun nuncaAfloja() {
        val strong = BackupSettings(adultBlock = true, adultWords = setOf("a"), newApps = NewAppsPolicy(true, 72), embeddedBrowsers = EmbeddedBrowserPolicy(true, allApps = true), foldFloating = true)
        val weak = BackupSettings(newApps = NewAppsPolicy(false, 12), embeddedBrowsers = EmbeddedBrowserPolicy(false))
        listOf(SyncMerge.merge(strong, weak), SyncMerge.merge(weak, strong)).forEach { merged ->
            assertTrue(merged.adultBlock && merged.foldFloating && "a" in merged.adultWords)
            assertFalse(Weakening.newAppsWeakened(strong.newApps!!, merged.newApps!!))
            assertFalse(Weakening.embeddedBrowsersWeakened(strong.embeddedBrowsers!!, merged.embeddedBrowsers!!))
        }
    }

    @Test fun esConmutativaEIdempotente() {
        val a = BackupSettings(adultBlock = true, adultWords = setOf("a"), newApps = NewAppsPolicy(true, 24), embeddedBrowsers = EmbeddedBrowserPolicy(true, apps = setOf("x")))
        val b = BackupSettings(adultWords = setOf("b"), newApps = NewAppsPolicy(true, 48), foldFloating = true, embeddedBrowsers = EmbeddedBrowserPolicy(true, apps = setOf("y")))
        val ab = SyncMerge.merge(a, b)
        assertEquals(ab, SyncMerge.merge(b, a))
        assertEquals(ab, SyncMerge.merge(ab, ab))
        assertEquals(ab, SyncMerge.merge(ab, a))
    }
}
