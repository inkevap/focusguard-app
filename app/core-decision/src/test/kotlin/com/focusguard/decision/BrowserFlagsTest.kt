package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserFlagsTest {

    @Test
    fun `reconoce las paginas flags de cada navegador`() {
        listOf(
            "chrome://flags",
            "brave://flags",
            "edge://flags",
            "internet://flags",
            "about:flags",
            "chrome://flags/",
            "chrome://flags/#incognito-screenshot",
            "brave://flags#incognito-screenshot",
            "CHROME://FLAGS",
            "  chrome://flags  ",
            "chrome:flags",
            "chrome://flags. La conexión no es segura",
            "chrome://flags?x",
        ).forEach { assertTrue(BrowserFlags.isFlagsPage(it), it) }
    }

    @Test
    fun `no confunde otras paginas`() {
        listOf(
            null,
            "",
            "chrome://settings",
            "chrome://flagship",
            "chrome://flags-ui",
            "https://flags.com",
            "flags",
            "example.com/chrome://flags",
            "https://example.com/flags",
            "chrome://history/flags",
        ).forEach { assertFalse(BrowserFlags.isFlagsPage(it), it.toString()) }
    }
}
