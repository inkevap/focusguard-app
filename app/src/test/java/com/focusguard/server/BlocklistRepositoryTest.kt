package com.focusguard.server

import com.focusguard.data.BlocklistRepository
import com.focusguard.data.BlocklistStore
import com.focusguard.data.StoredList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

private class MemoryBlocklists(initial: Map<String, StoredList> = emptyMap()) : BlocklistStore {
    val lists = initial.toMutableMap()

    override fun read(list: String) = lists[list] ?: StoredList.EMPTY

    override fun write(list: String, stored: StoredList) {
        lists[list] = stored
    }
}

class BlocklistRepositoryTest {
    private val account = MemoryAccountStore()

    private fun repo(store: MemoryBlocklists, transport: FakeTransport) =
        BlocklistRepository(ServerApi(transport, { "https://fg.test" }, account), store)

    private fun manifest(domains: Int, words: Int = 0) = """{"versions":{"domains":$domains,"exceptions":0,"words_es":$words,"words_en":0},"datasets":{}}"""

    @Test fun laPrimeraDescargaTraeLaListaEntera() = runBlocking {
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/blocklists") -> json(200, manifest(3))
                r.url.contains("list=domains&since=0") -> json(200, """{"version":3,"full":true,"added":["a.com","b.com"],"removed":[]}""")
                else -> json(404, "{}")
            }
        }
        val store = MemoryBlocklists()
        val repo = repo(store, transport)
        assertIs<ApiResult.Ok<Unit>>(repo.sync())
        assertEquals(setOf("a.com", "b.com"), repo.lists.value.getValue("domains").values)
        assertEquals(3L, store.lists.getValue("domains").version)
    }

    @Test fun despuesSoloSeBajanLasDiferencias() = runBlocking {
        val store = MemoryBlocklists(mapOf("domains" to StoredList(3, setOf("a.com", "b.com"))))
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/blocklists") -> json(200, manifest(5))
                r.url.contains("list=domains&since=3") -> json(200, """{"version":5,"full":false,"added":["c.com"],"removed":["a.com"]}""")
                else -> json(404, "{}")
            }
        }
        val repo = repo(store, transport)
        repo.sync()
        assertEquals(setOf("b.com", "c.com"), repo.lists.value.getValue("domains").values)
    }

    @Test fun conLaVersionAlDiaNoSePideNada() = runBlocking {
        val store = MemoryBlocklists(mapOf("domains" to StoredList(3, setOf("a.com"))))
        val transport = FakeTransport { json(200, manifest(3)) }
        repo(store, transport).sync()
        assertEquals(1, transport.requests.size)
    }

    @Test fun siElServidorTieneUnaVersionMenorSeReemplazaTodo() = runBlocking {
        val store = MemoryBlocklists(mapOf("domains" to StoredList(9, setOf("viejo.com"))))
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/blocklists") -> json(200, manifest(2))
                r.url.contains("list=domains&since=0") -> json(200, """{"version":2,"full":true,"added":["nuevo.com"],"removed":[]}""")
                else -> json(404, "{}")
            }
        }
        val repo = repo(store, transport)
        repo.sync()
        assertEquals(setOf("nuevo.com"), repo.lists.value.getValue("domains").values)
    }

    @Test fun unaListaQueFallaNoImpideLasDemas() = runBlocking {
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/blocklists") -> json(200, manifest(3, words = 2))
                r.url.contains("list=domains") -> json(500, """{"error":"x"}""")
                r.url.contains("list=words_es") -> json(200, """{"version":2,"full":true,"added":["porno"],"removed":[]}""")
                else -> json(404, "{}")
            }
        }
        val repo = repo(MemoryBlocklists(), transport)
        assertIs<ApiResult.Failure>(repo.sync())
        assertEquals(setOf("porno"), repo.lists.value.getValue("words_es").values)
    }

    @Test fun sinConexionSeConservaLoQueHabia() = runBlocking {
        val store = MemoryBlocklists(mapOf("domains" to StoredList(3, setOf("a.com"))))
        val repo = repo(store, FakeTransport { null })
        assertIs<ApiResult.Failure>(repo.sync())
        assertEquals(setOf("a.com"), repo.lists.value.getValue("domains").values)
    }

    private fun many(n: Int) = (1..n).map { "d$it.com" }.toSet()

    @Test fun unaListaQueCasiVaciaLaLocalSeRechaza() = runBlocking {
        val store = MemoryBlocklists(mapOf("domains" to StoredList(3, many(500))))
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/blocklists") -> json(200, manifest(9))
                r.url.contains("list=domains") -> json(200, """{"version":9,"full":true,"added":["a.com"],"removed":[]}""")
                else -> json(404, "{}")
            }
        }
        val repo = repo(store, transport)
        assertIs<ApiResult.Failure>(repo.sync())
        assertEquals(500, repo.lists.value.getValue("domains").values.size)
        assertEquals(3L, store.lists.getValue("domains").version)
    }

    @Test fun losValoresMalFormadosSeDescartan() = runBlocking {
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/blocklists") -> json(200, manifest(1))
                r.url.contains("list=domains") -> json(200, """{"version":1,"full":true,"added":["ok.com","MAL.COM","sin-punto","con espacio.com","www.mal.com"],"removed":[]}""")
                else -> json(404, "{}")
            }
        }
        val repo = repo(MemoryBlocklists(), transport)
        repo.sync()
        assertEquals(setOf("ok.com", "www.mal.com"), repo.lists.value.getValue("domains").values)
    }
}
