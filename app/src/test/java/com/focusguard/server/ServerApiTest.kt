package com.focusguard.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class ServerApiTest {
    private val store = MemoryAccountStore()
    private fun api(transport: FakeTransport, base: String? = "https://fg.test") = ServerApi(transport, { base }, store)

    private val session = """{"accessToken":"acc1","refreshToken":"ref1","expiresIn":900}"""

    @Test fun registrarGuardaLaSesionConElCorreoEnMinusculas() = runBlocking {
        val transport = FakeTransport { json(201, session) }
        assertIs<ApiResult.Ok<Unit>>(api(transport).register("  Ana@Example.com ", "contraseña-larga"))
        assertEquals("acc1", store.accessToken)
        assertEquals("ref1", store.refreshToken)
        assertEquals("ana@example.com", store.email)
        val sent = JSONObject(transport.requests.single().bodyText())
        assertEquals("Ana@Example.com", sent.getString("email"))
        assertEquals("https://fg.test/auth/register", transport.requests.single().url)
        assertNull(transport.requests.single().bearer())
    }

    @Test fun unErrorDelServidorLlegaConSuMensaje() = runBlocking {
        val result = api(FakeTransport { json(409, """{"error":"Ese correo ya tiene cuenta"}""") }).register("a@b.co", "contraseña-larga")
        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals("Ese correo ya tiene cuenta", failure.error.message)
        assertNull(store.accessToken)
    }

    @Test fun sinDireccionNoSeHaceNingunaPeticion() = runBlocking {
        val transport = FakeTransport { json(200, "{}") }
        assertEquals(ApiError.NotConfigured, assertIs<ApiResult.Failure>(api(transport, base = null).login("a@b.co", "x")).error)
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun sinConexionDaOffline() = runBlocking {
        assertEquals(ApiError.Offline, assertIs<ApiResult.Failure>(api(FakeTransport { null }).login("a@b.co", "x")).error)
    }

    @Test fun cuerpoQueNoEsJsonDaUnMensajeGenerico() = runBlocking {
        val failure = assertIs<ApiResult.Failure>(api(FakeTransport { json(502, "<html>Bad gateway</html>") }).login("a@b.co", "x"))
        assertTrue(failure.error.message.contains("servidor"))
    }

    @Test fun lasPersonasAQuienesAyudoSeLeenDelServidor() = runBlocking {
        store.saveSession("a@b.co", "acc1", "ref1")
        val transport = FakeTransport { json(200, """{"helping":[{"id":"p1","email":"luis@example.com","since":"2026-10-01T00:00:00Z","mutual":true,"pending":2}]}""") }
        val helping = assertIs<ApiResult.Ok<List<HelpedInfo>>>(api(transport).helping())
        assertEquals(listOf(HelpedInfo("p1", "luis@example.com", mutual = true, pending = 2)), helping.value)
        assertEquals("/partners/helping", transport.paths().single())
        assertEquals("Bearer acc1", transport.requests.single().bearer())
    }

    @Test fun laPuntuacionDelTestSeGuardaSinLasRespuestas() = runBlocking {
        store.saveSession("a@b.co", "acc1", "ref1")
        val transport = FakeTransport { json(201, """{"id":"x","score":24,"takenAt":"2026-10-28T10:00:00Z"}""") }
        assertIs<ApiResult.Ok<Unit>>(api(transport).saveAssessment(24, java.time.Instant.parse("2026-10-28T10:00:00Z")))
        val sent = JSONObject(transport.requests.single().bodyText())
        assertEquals(setOf("score", "takenAt"), sent.keys().asSequence().toSet())
        assertEquals(24, sent.getInt("score"))
        assertEquals("Bearer acc1", transport.requests.single().bearer())
        assertEquals("/assessments", transport.paths().single())
    }

    @Test fun conSesionMandaElTokenDeAcceso() = runBlocking {
        store.saveSession("a@b.co", "acc1", "ref1")
        val transport = FakeTransport { json(200, """{"email":"a@b.co","emailVerified":true}""") }
        val me = assertIs<ApiResult.Ok<Me>>(api(transport).me())
        assertEquals(Me("a@b.co", true), me.value)
        assertEquals("Bearer acc1", transport.requests.single().bearer())
    }

    @Test fun conUn401RenuevaElTokenYRepiteLaPeticionUnaVez() = runBlocking {
        store.saveSession("a@b.co", "viejo", "ref1")
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/refresh") -> json(200, """{"accessToken":"nuevo","refreshToken":"ref2"}""")
                r.bearer() == "Bearer nuevo" -> json(200, """{"email":"a@b.co","emailVerified":false}""")
                else -> json(401, """{"error":"Sesión no válida"}""")
            }
        }
        assertIs<ApiResult.Ok<Me>>(api(transport).me())
        assertEquals(listOf("/me", "/auth/refresh", "/me"), transport.paths())
        assertEquals("nuevo", store.accessToken)
        assertEquals("ref2", store.refreshToken)
        assertEquals("ref1", JSONObject(transport.requests[1].bodyText()).getString("refreshToken"))
    }

    @Test fun siElRefrescoYaNoValeCierraLaSesion() = runBlocking {
        store.saveSession("a@b.co", "viejo", "ref1")
        store.saveDeviceId("d-1")
        val transport = FakeTransport { json(401, """{"error":"Sesión caducada"}""") }
        assertEquals(ApiError.SessionExpired, assertIs<ApiResult.Failure>(api(transport).me()).error)
        assertNull(store.accessToken)
        assertFalse(store.hasSession)
        // La sesión caducó, pero el dispositivo se conserva: al volver a entrar no se duplica en la cuenta.
        assertEquals("d-1", store.deviceId)
    }

    @Test fun siFallaLaRedAlRenovarLaSesionSeConserva() = runBlocking {
        store.saveSession("a@b.co", "viejo", "ref1")
        val transport = FakeTransport { r -> if (r.url.endsWith("/auth/refresh")) null else json(401, "{}") }
        assertEquals(ApiError.Offline, assertIs<ApiResult.Failure>(api(transport).me()).error)
        assertEquals("ref1", store.refreshToken)
    }

    @Test fun unSegundo401TrasRenovarCierraLaSesion() = runBlocking {
        store.saveSession("a@b.co", "viejo", "ref1")
        val transport = FakeTransport { r -> if (r.url.endsWith("/auth/refresh")) json(200, """{"accessToken":"n","refreshToken":"r"}""") else json(401, "{}") }
        assertEquals(ApiError.SessionExpired, assertIs<ApiResult.Failure>(api(transport).me()).error)
        assertNull(store.accessToken)
    }

    @Test fun variasPeticionesConElTokenCaducadoRenuevanUnaSolaVez() = runBlocking {
        store.saveSession("a@b.co", "viejo", "ref1")
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/refresh") -> json(200, """{"accessToken":"nuevo","refreshToken":"ref2"}""")
                r.bearer() == "Bearer nuevo" -> json(200, """{"email":"a@b.co","emailVerified":true}""")
                else -> json(401, "{}")
            }
        }
        val client = api(transport)
        val results = (1..5).map { async { client.me() } }.awaitAll()
        assertTrue(results.all { it is ApiResult.Ok })
        assertEquals(1, transport.paths().count { it == "/auth/refresh" })
    }

    @Test fun registrarDispositivoMandaElIdSiYaLoTiene() = runBlocking {
        store.saveSession("a@b.co", "acc", "ref")
        val transport = FakeTransport { json(200, """{"deviceId":"d-1"}""") }
        assertEquals("d-1", assertIs<ApiResult.Ok<String>>(api(transport).registerDevice("Pixel 8", "d-1")).value)
        val sent = JSONObject(transport.requests.single().bodyText())
        assertEquals("Pixel 8", sent.getString("name"))
        assertEquals("d-1", sent.getString("deviceId"))
        assertEquals("PUT", transport.requests.single().method)
    }

    @Test fun registrarDispositivoMandaElTokenDePushSoloSiLoHay() = runBlocking {
        store.saveSession("a@b.co", "acc", "ref")
        val transport = FakeTransport { json(200, """{"deviceId":"d-1"}""") }
        api(transport).registerDevice("Pixel 8", "d-1", pushToken = "tok-123")
        api(transport).registerDevice("Pixel 8", "d-1")
        val (conToken, sinToken) = transport.requests.map { JSONObject(it.bodyText()) }
        assertEquals("tok-123", conToken.getString("fcmToken"))
        assertFalse(sinToken.has("fcmToken"))
    }

    @Test fun unaRespuestaCorrectaQueNoEsJsonNoHaceFallarLaApp() = runBlocking {
        val failure = assertIs<ApiResult.Failure>(api(FakeTransport { json(200, "<html>portal</html>") }).login("a@b.co", "x"))
        assertEquals(ApiError.Unexpected, failure.error)
        assertNull(store.accessToken)
    }

    @Test fun unaRespuestaCorrectaSinLosCamposEsperadosEsInesperada() = runBlocking {
        store.saveSession("a@b.co", "acc", "ref")
        assertEquals(ApiError.Unexpected, assertIs<ApiResult.Failure>(api(FakeTransport { json(200, "{}") }).me()).error)
        assertEquals(ApiError.Unexpected, assertIs<ApiResult.Failure>(api(FakeTransport { json(200, """{"accessToken":"a"}""") }).login("a@b.co", "x")).error)
    }

    @Test fun elCodigoEstableDelServidorLlegaEnElError() = runBlocking {
        store.saveSession("a@b.co", "acc", "ref")
        val failure = assertIs<ApiResult.Failure>(api(FakeTransport { json(404, """{"error":"Dispositivo desconocido","code":"device_unknown"}""") }).registerDevice("x", "d-1"))
        assertTrue(failure.error.isDeviceUnknown)
        val plain = assertIs<ApiResult.Failure>(api(FakeTransport { json(404, "<html>no</html>") }).registerDevice("x", "d-1"))
        assertFalse(plain.error.isDeviceUnknown)
    }

    @Test fun siElRefrescoFallaPorUnErrorDelServidorNoSeDiceQueNoHayConexion() = runBlocking {
        store.saveSession("a@b.co", "viejo", "ref1")
        val transport = FakeTransport { r -> if (r.url.endsWith("/auth/refresh")) json(500, "{}") else json(401, "{}") }
        val error = assertIs<ApiResult.Failure>(api(transport).me()).error
        assertTrue(error is ApiError.Http && error.status == 500 && error.retryable)
        assertEquals("ref1", store.refreshToken)
    }

    @Test fun seRenuevaConElTokenQueSalioEnLaPeticionAunqueOtraYaLoCambiara() = runBlocking {
        store.saveSession("a@b.co", "A", "ref1")
        var meCalls = 0
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/refresh") -> json(200, """{"accessToken":"B","refreshToken":"ref2"}""")
                // La primera petición sale con A; mientras espera, otra renueva a B y la segunda ya sale con B.
                r.bearer() == "Bearer B" -> json(200, """{"email":"a@b.co","emailVerified":true}""")
                else -> { meCalls++; json(401, "{}") }
            }
        }
        val client = api(transport)
        assertIs<ApiResult.Ok<Me>>(client.me())
        assertEquals(1, meCalls)
        assertEquals(1, transport.paths().count { it == "/auth/refresh" })
    }

    @Test fun losErroresSabenSiMerecenReintento() {
        assertTrue(ApiError.Offline.retryable)
        assertTrue(ApiError.Http(503, "x").retryable)
        assertTrue(ApiError.Http(429, "x").retryable)
        assertFalse(ApiError.Http(400, "x").retryable)
        assertFalse(ApiError.Http(404, "x").retryable)
        assertFalse(ApiError.SessionExpired.retryable)
        assertTrue(ApiError.Http(404, "x").isNotFound)
    }

    @Test fun cerrarSesionOlvidaTodoLoLocal() {
        store.saveSession("a@b.co", "acc", "ref")
        store.saveDeviceId("d-1")
        api(FakeTransport { null }).signOut()
        assertNull(store.accessToken)
        assertNull(store.deviceId)
    }
}
