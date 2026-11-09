package com.focusguard.server

import com.focusguard.data.AccountRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class AccountRepositoryTest {
    private val store = MemoryAccountStore()
    private val saved = mutableListOf<String>()
    private fun repo(transport: FakeTransport, pushToken: PushTokenSource = PushTokenSource { null }) =
        AccountRepository(ServerApi(transport, { "https://fg.test" }, store), store, { "Pixel 8" }, { saved += it; true }, pushToken = pushToken)

    /** Tokens de push que llegaron al servidor, en orden. */
    private fun registeredTokens(transport: FakeTransport) =
        transport.requests.filter { it.url.endsWith("/devices/register") }.map { JSONObject(it.bodyText()).getString("fcmToken") }

    private fun serverWith(verified: Boolean = false, deviceStatus: Int = 200) = FakeTransport { r ->
        when {
            r.url.endsWith("/auth/register") || r.url.endsWith("/auth/login") -> json(201, """{"accessToken":"a","refreshToken":"r"}""")
            r.url.endsWith("/me") -> json(200, """{"email":"ana@example.com","emailVerified":$verified}""")
            r.url.endsWith("/devices/register") -> if (deviceStatus == 200) json(200, """{"deviceId":"dev-${r.bodyText().hashCode() and 0xff}"}""") else json(deviceStatus, """{"error":"x"}""")
            else -> json(404, "{}")
        }
    }

    @Test fun alCrearLaCuentaSeRegistraElDispositivoYSeConsultaElEstado() = runBlocking {
        val repo = repo(serverWith(verified = false))
        assertIs<ApiResult.Ok<Unit>>(repo.register("ana@example.com", "contraseña-larga"))
        val state = repo.state.value
        assertEquals("ana@example.com", state.email)
        assertEquals(false, state.emailVerified)
        assertTrue(state.deviceRegistered)
        assertTrue(state.signedIn)
    }

    @Test fun alEntrarSeOlvidaElCompaniero() = runBlocking {
        var cleared = 0
        val repo = AccountRepository(ServerApi(serverWith(), { "https://fg.test" }, store), store, { "Pixel 8" }, { true }, onSignIn = { cleared++ })
        repo.login("ana@example.com", "contraseña-larga")
        assertEquals(1, cleared)
    }

    @Test fun alRegistrarElDispositivoSeMandaElTokenDePush() = runBlocking {
        val transport = serverWith()
        val repo = repo(transport, pushToken = { "tok-1" })
        repo.register("ana@example.com", "contraseña-larga")
        val tokens = registeredTokens(transport)
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.all { it == "tok-1" })
    }

    @Test fun siElTokenCambiaSeVuelveAMandarAlRefrescar() = runBlocking {
        val transport = serverWith()
        var token = "tok-1"
        val repo = repo(transport, pushToken = { token })
        repo.register("ana@example.com", "contraseña-larga")
        val afterSignIn = registeredTokens(transport)
        repo.refresh()
        assertEquals(afterSignIn, registeredTokens(transport))
        token = "tok-2"
        repo.refresh()
        assertEquals(afterSignIn + "tok-2", registeredTokens(transport))
    }

    @Test fun sinPushElDispositivoSeRegistraIgual() = runBlocking {
        val repo = repo(serverWith(), pushToken = { error("sin Google Play") })
        assertIs<ApiResult.Ok<Unit>>(repo.register("ana@example.com", "contraseña-larga"))
        assertTrue(repo.state.value.deviceRegistered)
    }

    @Test fun alCerrarSesionElServidorOlvidaElTokenDePushDeEsteDispositivo() = runBlocking {
        val transport = serverWith()
        val repo = repo(transport)
        repo.register("ana@example.com", "contraseña-larga")
        val deviceId = store.deviceId!!
        repo.signOut()
        val forget = transport.requests.single { it.method == "DELETE" }
        assertTrue(forget.url.endsWith("/devices/$deviceId/push-token"))
        assertFalse(repo.state.value.signedIn)
        assertNull(store.deviceId)
    }

    @Test fun sinRedElCierreDeSesionSeHaceIgual() = runBlocking {
        var online = true
        val repo = repo(FakeTransport { r ->
            when {
                !online -> null
                r.url.endsWith("/auth/register") -> json(201, """{"accessToken":"a","refreshToken":"r"}""")
                r.url.endsWith("/me") -> json(200, """{"email":"ana@example.com","emailVerified":true}""")
                r.url.endsWith("/devices/register") -> json(200, """{"deviceId":"d-1"}""")
                else -> json(404, "{}")
            }
        })
        repo.register("ana@example.com", "contraseña-larga")
        online = false
        repo.signOut()
        assertFalse(repo.state.value.signedIn)
        assertNull(store.accessToken)
    }

    @Test fun sinSesionElEstadoEstaVacio() {
        val state = repo(serverWith()).state.value
        assertFalse(state.signedIn)
        assertNull(state.emailVerified)
    }

    @Test fun siElServidorNoConoceElDispositivoSeRegistraOtro() = runBlocking {
        var calls = 0
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/devices/register") -> if (calls++ == 0) json(404, """{"error":"Dispositivo desconocido","code":"device_unknown"}""") else json(200, """{"deviceId":"nuevo"}""")
                else -> json(404, "{}")
            }
        }
        store.saveSession("ana@example.com", "a", "r")
        store.saveDeviceId("viejo")
        assertIs<ApiResult.Ok<Unit>>(repo(transport).ensureDevice())
        assertEquals("nuevo", store.deviceId)
        assertEquals(2, transport.requests.size)
        assertFalse(transport.requests[1].bodyText().contains("viejo"))
    }

    @Test fun unNotFoundSinCodigoNoHaceOlvidarElDispositivo() = runBlocking {
        val transport = FakeTransport { json(404, "<html>no hay ruta</html>") }
        store.saveSession("ana@example.com", "a", "r")
        store.saveDeviceId("d-1")
        assertIs<ApiResult.Failure>(repo(transport).ensureDevice())
        assertEquals("d-1", store.deviceId)
        assertEquals(1, transport.requests.size)
    }

    @Test fun siAlEntrarNoSePudoRegistrarElDispositivoSeReintentaAlComprobarLaCuenta() = runBlocking {
        var online = false
        val transport = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/login") -> json(200, """{"accessToken":"a","refreshToken":"r"}""")
                r.url.endsWith("/me") -> json(200, """{"email":"ana@example.com","emailVerified":true}""")
                r.url.endsWith("/devices/register") -> if (online) json(200, """{"deviceId":"d-9"}""") else null
                else -> json(404, "{}")
            }
        }
        val repo = repo(transport)
        repo.login("ana@example.com", "contraseña-larga")
        assertFalse(repo.state.value.deviceRegistered)
        online = true
        repo.refresh()
        assertTrue(repo.state.value.deviceRegistered)
        assertEquals("d-9", store.deviceId)
    }

    @Test fun cerrarSesionVuelveAlEstadoVacio() = runBlocking {
        val repo = repo(serverWith())
        repo.login("ana@example.com", "contraseña-larga")
        repo.signOut()
        assertFalse(repo.state.value.signedIn)
        assertFalse(repo.state.value.deviceRegistered)
    }

    @Test fun noSeCambiaDeServidorConLaSesionIniciada() = runBlocking {
        val repo = repo(serverWith())
        assertTrue(repo.changeServer("https://otro.example.com"))
        repo.login("ana@example.com", "contraseña-larga")
        assertFalse(repo.changeServer("https://malo.example.com"))
        assertEquals(listOf("https://otro.example.com"), saved)
        repo.signOut()
        assertTrue(repo.changeServer("https://tercero.example.com"))
    }

    @Test fun cualquierLlamadaConSesionCaducadaDejaElEstadoAlDia() = runBlocking {
        val repo = repo(serverWith())
        repo.login("ana@example.com", "contraseña-larga")
        assertTrue(repo.state.value.signedIn)
        val expired = FakeTransport { json(401, "{}") }
        val repo2 = AccountRepository(ServerApi(expired, { "https://fg.test" }, store), store, { "Pixel 8" }, { true })
        assertIs<ApiResult.Failure>(repo2.resendVerification())
        assertFalse(repo2.state.value.signedIn)
        assertEquals("ana@example.com", store.email)
    }

    @Test fun unaSesionCaducadaSeReflejaEnElEstado() = runBlocking {
        val repo = repo(serverWith())
        repo.login("ana@example.com", "contraseña-larga")
        val expired = FakeTransport { json(401, "{}") }
        val repo2 = AccountRepository(ServerApi(expired, { "https://fg.test" }, store), store, { "Pixel 8" }, { true })
        assertIs<ApiResult.Failure>(repo2.refresh())
        assertFalse(repo2.state.value.signedIn)
    }
}
