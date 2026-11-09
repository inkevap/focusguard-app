package com.focusguard.data

import android.content.Context
import com.focusguard.server.ApiError
import com.focusguard.server.ApiResult
import com.focusguard.server.ListChanges
import com.focusguard.server.ServerApi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Una lista del servidor tal como está en el teléfono. */
data class StoredList(val version: Long, val values: Set<String>) {
    companion object {
        val EMPTY = StoredList(0, emptySet())
    }
}

/** Dónde se guardan las listas descargadas. */
interface BlocklistStore {
    fun read(list: String): StoredList

    fun write(list: String, stored: StoredList)
}

/** Un archivo por lista, con la versión en la primera línea. Se escribe en uno temporal y se renombra: nunca queda una lista a medias. */
class FileBlocklistStore(context: Context) : BlocklistStore {
    private val dir = File(context.filesDir, "blocklists").apply { mkdirs() }

    // El nombre sale de la lista de nombres conocidos de `BlocklistRepository`, nunca de lo que mande el servidor.
    private fun file(list: String) = File(dir, "$list.txt")

    override fun read(list: String): StoredList = runCatching {
        file(list).bufferedReader().use { reader ->
            val version = reader.readLine().trim().toLong()
            StoredList(version, reader.lineSequence().filter { it.isNotEmpty() }.toSet())
        }
    }.getOrDefault(StoredList.EMPTY)

    override fun write(list: String, stored: StoredList) {
        val tmp = File(dir, "$list.tmp")
        tmp.bufferedWriter().use { writer ->
            writer.write(stored.version.toString())
            writer.newLine()
            stored.values.forEach {
                writer.write(it)
                writer.newLine()
            }
        }
        check(tmp.renameTo(file(list))) { "No se pudo guardar la lista $list" }
    }
}

/**
 * Listas de contenido adulto del servidor (D2): dominios, excepciones y palabras. Se descargan de forma incremental (solo lo que
 * cambió desde la versión que se tiene) y se conservan en el teléfono, así que el filtro funciona sin conexión.
 */
class BlocklistRepository(private val api: ServerApi, private val store: BlocklistStore) {
    private val lock = Mutex()
    private val _lists = MutableStateFlow(NAMES.associateWith { StoredList.EMPTY })
    private var loaded = false

    /** Lo que hay ahora en el teléfono. Cambia cuando una descarga trae algo nuevo. */
    val lists: StateFlow<Map<String, StoredList>> = _lists.asStateFlow()

    /** Lee del disco lo que ya se había descargado. Cuesta (decenas de miles de líneas): se hace en segundo plano, no al crear el objeto. */
    suspend fun load() = withContext(Dispatchers.IO) { lock.withLock { ensureLoaded() } }

    private fun ensureLoaded() {
        if (loaded) return
        _lists.value = NAMES.associateWith { store.read(it) }
        loaded = true
    }

    /**
     * Pregunta qué versiones hay y baja lo que falta. Una lista que falla no impide las demás; devuelve el primer fallo. Se publica
     * todo junto al final, para que quien escucha (el filtro) se arme una sola vez.
     */
    suspend fun sync(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        lock.withLock {
            ensureLoaded()
            val remote = when (val result = api.blocklistVersions()) {
                is ApiResult.Ok -> result.value
                is ApiResult.Failure -> return@withLock result
            }
            var failure: ApiResult.Failure? = null
            val updated = _lists.value.toMutableMap()
            for (name in NAMES) {
                val have = updated.getValue(name)
                val latest = remote[name] ?: continue
                // Una versión menor que la local (un servidor restaurado de una copia) también reemplaza: la del servidor manda.
                if (latest == have.version) continue
                when (val changes = api.blocklistChanges(name, if (latest < have.version) 0 else have.version)) {
                    is ApiResult.Ok -> runCatching { apply(name, have, changes.value) }
                        .onSuccess { updated[name] = it }
                        // Sin sitio en el disco no se publica esa lista: memoria y disco siguen iguales.
                        .onFailure { if (failure == null) failure = ApiResult.Failure(ApiError.Unexpected) }
                    is ApiResult.Failure -> if (failure == null) failure = changes
                }
            }
            _lists.value = updated
            failure ?: ApiResult.Ok(Unit)
        }
    }

    /** Lo que llega del servidor no se acepta sin mirar: solo valores con la forma esperada, y nunca una lista que casi vacíe la que ya hay. */
    private fun apply(name: String, have: StoredList, changes: ListChanges): StoredList {
        val valid = if (name == DOMAINS || name == EXCEPTIONS) DOMAIN else WORD
        val added = changes.added.filter { valid.matches(it) }
        val values = if (changes.full) added.toHashSet() else HashSet(have.values).apply { removeAll(changes.removed.toSet()); addAll(added) }
        // Un servidor ajeno, o roto, no puede dejar el filtro vacío: una lista que pierde más de la mitad se rechaza (y la local sigue valiendo).
        check(have.values.size < MIN_TO_PROTECT || values.size * 2 >= have.values.size) { "La lista $name perdería demasiadas entradas" }
        return StoredList(changes.version, values).also { store.write(name, it) }
    }

    companion object {
        /** Como el servidor las valida al publicar (`server/src/blocklists.ts`). */
        private val DOMAIN = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9-]{2,63}$")
        private val WORD = Regex("^[\\p{L}\\p{N}+]{2,60}$")

        /** Con menos entradas que esto no se vigila la caída (listas recién empezadas). */
        private const val MIN_TO_PROTECT = 100
        const val DOMAINS = "domains"
        const val EXCEPTIONS = "exceptions"
        const val WORDS_ES = "words_es"
        const val WORDS_EN = "words_en"
        val NAMES = listOf(DOMAINS, EXCEPTIONS, WORDS_ES, WORDS_EN)

        /** Las palabras de los dos idiomas: el texto de una página no tiene por qué estar en el idioma del teléfono. */
        val WORDS = listOf(WORDS_ES, WORDS_EN)
    }
}
