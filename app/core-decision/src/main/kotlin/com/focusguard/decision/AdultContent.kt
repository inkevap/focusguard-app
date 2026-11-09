package com.focusguard.decision

import java.net.URLDecoder
import java.text.Normalizer

/** Por qué se bloqueó algo como contenido adulto, con lo que coincidió (para la pantalla de bloqueo). */
data class AdultMatch(val kind: Kind, val matched: List<String>) {
    enum class Kind { DOMAIN, PORN_RULE, WORD }
}

/** Un texto en las dos formas que usa la búsqueda (`TABLAS_Y_ALGORITMO.md` 6, paso 4). */
data class NormalizedText(
    /** Minúsculas, sin acentos ni símbolos; conserva los espacios y el `+` de «18+». */
    val spaced: String,
    /** Lo mismo sin espacios, para encontrar palabras escritas con separadores («p.o.r.n»). */
    val joined: String,
)

/**
 * Filtro de contenido adulto (D2, D3, D4): dominios y palabras que vienen del servidor más las excepciones y los permitidos del
 * usuario. Todo es texto ya limpiado y búsqueda por subcadena; para no bloquear por una palabra suelta en una página de noticias,
 * el texto de pantalla pide varias coincidencias distintas y solo la barra de direcciones bloquea con una.
 *
 * Los dominios y las excepciones se esperan ya en minúsculas y sin `www.`: así los entrega el servidor, que los valida al
 * publicarlos, y no se vuelven a normalizar aquí (son decenas de miles).
 */
class AdultFilter(
    domains: Collection<String>,
    words: Collection<String>,
    exceptions: Collection<String> = emptyList(),
    /** Lo que el usuario dejó pasar a propósito (D7): dominios o palabras. */
    allowed: Collection<String> = emptyList(),
    /** Palabras y webs propias del usuario: se buscan también sin espacios (paso 8 del algoritmo). */
    userWords: Collection<String> = emptyList(),
) {
    private val domains: Set<String> = domains as? Set<String> ?: domains.toSet()
    private val exceptions: Set<String> = exceptions as? Set<String> ?: exceptions.toSet()
    private val serverWords = terms(words)
    // Lo permitido: un dominio (con punto) deja pasar esa web y sus subdominios, no cualquier texto que lo nombre; lo demás son palabras o frases.
    private val allowedDomains: Set<String> = allowed.map(UrlNormalizer::normalize).filter { '.' in it && ' ' !in it && it.length >= AdultContent.MIN_WORD_LENGTH }.toSet()
    private val allowedTerms = terms(allowed.filter { '.' !in it || ' ' in it })
    private val userWords = terms(userWords)

    /** La dirección de la barra del navegador. Una coincidencia basta. */
    fun checkUrl(rawUrl: String): AdultMatch? {
        val url = rawUrl.trim()
        if (url.isEmpty() || AdultContent.isNewTab(url)) return null
        val text = AdultContent.normalize(AdultContent.decode(url))
        UrlNormalizer.hostOrNull(url)?.let { host ->
            val chain = AdultContent.withParents(host)
            if (chain.any { it in exceptions || it in allowedDomains }) return null
            // Un dominio de la lista se bloquea aunque la dirección lleve una palabra permitida: lo permitido solo calla las palabras.
            chain.firstOrNull { it in domains }?.let { return AdultMatch(AdultMatch.Kind.DOMAIN, listOf(it)) }
        }
        if (isAllowed(text)) return null
        if (AdultContent.PORN_RULE in AdultContent.lettersOnly(text.spaced)) return AdultMatch(AdultMatch.Kind.PORN_RULE, listOf(AdultContent.PORN_RULE))
        val found = matchedWords(text)
        return if (found.isEmpty()) null else AdultMatch(AdultMatch.Kind.WORD, found)
    }

    /** Los textos de la pantalla: hacen falta [AdultContent.MIN_SCREEN_MATCHES] palabras distintas entre todos. */
    fun checkScreenText(texts: Collection<String>): AdultMatch? {
        val found = linkedSetOf<String>()
        val seen = HashSet<String>()
        for (raw in texts) {
            // Una palabra de la lista no cabe en un texto más corto, y las pantallas repiten mucho (etiquetas, barras).
            if (raw.length < AdultContent.MIN_WORD_LENGTH || !seen.add(raw)) continue
            val text = AdultContent.normalize(raw)
            if (isAllowed(text)) continue
            found += matchedWords(text)
            // Una palabra que cabe dentro de otra de la lista («porn» en «porno») es la misma mención, no otra: una sola «porno» no cuenta por dos.
            val independent = found.filter { word -> found.none { other -> other != word && word in other } }
            if (independent.size >= AdultContent.MIN_SCREEN_MATCHES) return AdultMatch(AdultMatch.Kind.WORD, independent)
        }
        return null
    }

    /** ¿Se bloquea ya sola una web o palabra? Sirve para no dejar añadir a la lista del usuario lo que no hace falta (cuenta las excepciones y lo permitido). */
    fun blocksAlready(domainOrWord: String): Boolean = checkUrl(domainOrWord) != null

    /** Una palabra o frase permitida cuenta entera (rodeada de espacios), no como trozo de otra palabra ni a través de los espacios. */
    private fun isAllowed(text: NormalizedText) = allowedTerms.any { " ${it.spaced} " in " ${text.spaced} " }

    private fun matchedWords(text: NormalizedText): List<String> =
        serverWords.filter { it.spaced in text.spaced }.map { it.spaced } + userWords.filter { it.containedIn(text) }.map { it.spaced }

    private fun terms(list: Collection<String>, minLength: Int = AdultContent.MIN_WORD_LENGTH) =
        list.map(AdultContent::normalize).filter { it.joined.length >= minLength }.distinct()
}

object AdultContent {
    const val MIN_WORD_LENGTH = 4
    const val MIN_SCREEN_MATCHES = 2
    const val PORN_RULE = "porn"

    private val accents = Regex("\\p{M}+")
    private val symbols = Regex("[^\\p{L}\\p{N}\\s]")
    private val spaces = Regex("\\s+")

    /** Los accesos de «pestaña nueva» y páginas internas del navegador no son una dirección que mirar. */
    private val internalSchemes = listOf("about:", "chrome:", "edge:", "brave:", "opera:", "vivaldi:")

    fun isNewTab(url: String): Boolean = internalSchemes.any { url.startsWith(it, ignoreCase = true) }

    /** El dominio y, quitando una etiqueta cada vez, los dominios principales hasta quedarse con dos etiquetas (D4). */
    fun withParents(host: String): List<String> {
        val name = host.removePrefix("www.")
        val result = ArrayList<String>(4)
        var start = 0
        while (true) {
            val dot = name.indexOf('.', start)
            // Sin más puntos, lo que queda es una sola etiqueta: ya no es un dominio.
            if (dot < 0 || dot == name.length - 1) break
            result += name.substring(start)
            start = dot + 1
        }
        return result
    }

    /** Las direcciones llevan texto codificado (`%20`, `+`); el texto de pantalla se deja tal cual (un «50%» no es una codificación). */
    fun decode(url: String): String = try {
        URLDecoder.decode(url.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        url
    }

    fun normalize(raw: String): NormalizedText {
        val plain = accents.replace(Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD), "")
        // «18+» es lo único con un símbolo que importa: se protege antes de quitarlos.
        val spaced = spaces.replace(symbols.replace(plain.replace("18+", "18plus"), " ").replace("18plus", "18+"), " ").trim()
        return NormalizedText(spaced, spaced.replace(" ", ""))
    }

    /** Solo las letras, para la regla fija de «porn». */
    fun lettersOnly(text: String): String = text.filter(Char::isLetter)
}

/** Una palabra o texto en sus dos formas: ¿aparece en otro, con o sin espacios? */
internal fun NormalizedText.containedIn(other: NormalizedText) = spaced in other.spaced || joined in other.joined

/**
 * Cuándo mirar la pantalla y cuándo basta lo último que se vio (D3). La dirección del navegador es barata y se mira siempre; el texto de
 * la pantalla exige recorrer la ventana, así que solo se hace cuando [scan] lo pide (en un evento de accesibilidad) y como mucho cada
 * [screenIntervalMs] por app; si hace poco que se vio limpia, no se vuelve a mirar durante [staleMs] salvo evento. Un bloqueo no se recuerda.
 */
class AdultGate(private val screenIntervalMs: Long = 3_000, private val staleMs: Long = 10_000) {
    private data class Seen(val packageName: String, val atMs: Long)

    private var last: Seen? = null

    /** [screenTexts] devuelve null si no se pudo leer la ventana (no se recuerda: no es un «no hay nada»). */
    fun check(
        packageName: String,
        url: String?,
        enabled: Boolean,
        exempt: Boolean,
        nowMs: Long,
        scan: Boolean,
        filter: AdultFilter,
        /** No leer el texto de esta app ([AdultTextExemptions]); su dirección, si la hay, sí se mira. */
        skipText: Boolean = false,
        screenTexts: () -> Collection<String>?,
    ): AdultMatch? {
        if (!enabled || exempt) return null
        url?.let(filter::checkUrl)?.let { return it }
        if (skipText) return null
        val seen = last?.takeIf { it.packageName == packageName }
        val age = seen?.let { nowMs - it.atMs }
        if (seen != null && age != null && (age < screenIntervalMs || (!scan && age < staleMs))) return null
        if (!scan) return null
        val texts = screenTexts() ?: return null
        val match = filter.checkScreenText(texts)
        // Un bloqueo no se recuerda: tras cerrar la página, lo siguiente que se vea vuelve a mirarse sin arrastrar un «sí» viejo.
        last = if (match == null) Seen(packageName, nowMs) else null
        return match
    }
}

/** Añadir a la lista de permitidos del contenido adulto exige teclear a mano esta frase (D7). */
object AllowPhrase {
    const val PHRASE = "confirmo que este elemento no es pornográfico"

    fun matches(typed: String): Boolean = AdultContent.normalize(typed).spaced == AdultContent.normalize(PHRASE).spaced
}

/** Validación de una web que el usuario añade a su lista (`TABLAS_Y_ALGORITMO.md` 6). */
object UserWebValidator {
    sealed interface Result {
        data class Valid(val web: String) : Result

        data object NoDot : Result

        data object TooShort : Result

        /** Ya la bloquea el filtro de contenido adulto: no tiene sentido añadirla. */
        data object AlreadyBlocked : Result
    }

    private val shortLanguages = setOf("ko", "zh", "ja")

    fun validate(raw: String, language: String, filter: AdultFilter): Result {
        val web = UrlNormalizer.normalize(raw.filterNot(Char::isWhitespace)).replace(Regex("\\.{2,}"), ".")
        if ('.' !in web) return Result.NoDot
        if (web.length < (if (language in shortLanguages) 2 else 3)) return Result.TooShort
        if (filter.blocksAlready(web)) return Result.AlreadyBlocked
        return Result.Valid(web)
    }
}

/** Reactivación automática (D6): una protección apagada con aprobación se enciende sola pasado un tiempo. */
object AutoReactivation {
    const val DEFAULT_HOURS = 24

    fun reactivateAt(offAtMs: Long, hours: Int = DEFAULT_HOURS): Long = offAtMs + hours.coerceAtLeast(1) * 3_600_000L

    /** `offUntilMs` es la hora de reactivación guardada, o null si la protección no está apagada de forma temporal. */
    fun due(offUntilMs: Long?, nowMs: Long): Boolean = offUntilMs != null && nowMs >= offUntilMs
}
