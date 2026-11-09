package com.focusguard.model

import java.time.DayOfWeek
import java.time.Instant

enum class BlockMode {
    /** Se bloquea lo que está en el perfil. */
    BLOCK,

    /** Se bloquea todo lo que no está en el perfil. */
    ALLOW_ONLY,
}

enum class WebMatchType {
    /** El valor aparece en cualquier parte de la URL normalizada. */
    CONTAINS,

    /** El dominio coincide o es un subdominio del valor. */
    EXACT,
}

data class WebRule(
    val value: String,
    val matchType: WebMatchType = WebMatchType.EXACT,
)

/**
 * Intervalo semanal de un perfil. Los minutos cuentan desde las 00:00 del día [day].
 * Si [endMinute] es menor o igual que [startMinute], el intervalo cruza la medianoche
 * y termina al día siguiente.
 */
data class ScheduleInterval(
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute fuera de rango: $startMinute" }
        require(endMinute in 0..MINUTES_PER_DAY) { "endMinute fuera de rango: $endMinute" }
    }

    /** Minuto de la semana (desde el lunes a las 00:00) en que empieza. */
    val startOfWeek: Int get() = (day.value - 1) * MINUTES_PER_DAY + startMinute

    /** Cuánto dura; si empieza y termina a la misma hora, dura 24 h. */
    val lengthMinutes: Int
        get() = if (endMinute > startMinute) endMinute - startMinute else MINUTES_PER_DAY - startMinute + endMinute

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
    }
}

enum class LimitKind {
    /** Minutos de uso acumulados en el periodo. */
    TIME,

    /** Número de aperturas en el periodo; se bloquea al superar el máximo. */
    OPENS,

    /** Minutos seguidos de uso; se desbloquea tras un descanso. */
    CONTINUOUS,
}

enum class LimitPeriod { HOURLY, DAILY, WEEKLY }

/** Cómo se reparte el límite entre lo que lista el perfil. */
enum class LimitScope {
    /** Un solo contador para todas las apps y webs del perfil (el comportamiento de siempre). */
    SHARED,

    /** Un contador propio para cada app o web: agotar una no bloquea las demás. */
    PER_TARGET,
}

/** Identifica el contador de una app o web dentro de un perfil con límite [LimitScope.PER_TARGET]. */
data class UsageKey(val profileId: Long, val target: String)

/**
 * Límite de las apps y webs del perfil, compartido o por cada una según [scope]. El perfil solo bloquea
 * cuando el límite se agota (condición E5), y se combina con su horario.
 * [amount] son minutos (TIME, CONTINUOUS) o aperturas (OPENS).
 */
data class UsageLimit(
    val kind: LimitKind,
    val amount: Int,
    val period: LimitPeriod = LimitPeriod.DAILY,
    val scope: LimitScope = LimitScope.SHARED,
) {
    init {
        require(amount > 0) { "amount debe ser positivo" }
    }
}

/** Uso acumulado de un perfil en el periodo vigente. Se actualiza con `UsageAccumulator`. */
data class ProfileUsage(
    val periodStartMs: Long = 0,
    val timeMs: Long = 0,
    val opens: Int = 0,
    val continuousMs: Long = 0,
    val lastActiveMs: Long = 0,
    val inUse: Boolean = false,
)

/** Cómo se combinan las condiciones definidas de un perfil (horario, Wi-Fi, cargador, lugar). */
enum class ConditionMode {
    /** Todas las condiciones definidas a la vez. */
    ALL,

    /** Basta con una. */
    ANY,
}

/** Lugar con radio. Con [inverted], la condición se cumple al estar fuera. */
data class GeoFence(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val inverted: Boolean = false,
) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "coordenadas fuera de rango" }
        require(radiusMeters > 0) { "el radio debe ser positivo" }
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 100
    }
}

/** Lo que el teléfono sabe ahora mismo, para evaluar las condiciones de los perfiles. */
data class DeviceContext(
    /** Nombre de la red Wi-Fi conectada; null si no hay o no se puede leer. */
    val wifiName: String? = null,
    /** Nombres de los dispositivos Bluetooth conectados ahora. */
    val bluetoothDevices: Set<String> = emptySet(),
    val charging: Boolean = false,
    /** Última posición conocida; null si no hay permiso o no hay posición. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Sin permiso de ubicación no se puede leer la red ni la posición; entonces esas condiciones se dan por cumplidas. */
    val locationAvailable: Boolean = true,
    /** Sin permiso de Bluetooth no se pueden leer los dispositivos conectados; entonces esa condición se da por cumplida. */
    val bluetoothAvailable: Boolean = true,
)

/** Cómo se puede terminar un «Bloquear ya». Se elige antes de empezar y no se cambia después. */
enum class EndPolicy {
    /** Cuando el usuario quiera, con «Terminar» (y las aprobaciones de siempre). */
    ANYTIME,

    /** Solo al acabar el tiempo: ni a mano ni con una aprobación. */
    TIME_ONLY,

    /** Antes de tiempo solo si se cumple alguna de las [EndConditions]. */
    CONDITIONS,
}

/**
 * Las salidas de un bloqueo con [EndPolicy.CONDITIONS]; basta con que se cumpla una. Las que leen el teléfono (lugar, cargador, Wi-Fi, Bluetooth)
 * levantan el bloqueo solas. La espera no lo levanta: pasado [waitUntil] aparece «Terminar», que pide las aprobaciones de siempre.
 */
data class EndConditions(
    /** Llegar a este lugar (dentro del círculo). */
    val place: GeoFence? = null,
    val charging: Boolean = false,
    val wifiNames: Set<String> = emptySet(),
    val bluetoothNames: Set<String> = emptySet(),
    val waitUntil: Instant? = null,
) {
    /** ¿Alguna salida depende de lo que lee el teléfono? */
    val readsDevice: Boolean get() = place != null || charging || wifiNames.isNotEmpty() || bluetoothNames.isNotEmpty()

    val isEmpty: Boolean get() = !readsDevice && waitUntil == null
}

enum class ProfileKind {
    NORMAL,

    /** Bloqueo inmediato con duración fija (F1 del catálogo). Se borra al caducar. */
    QUICK_BLOCK,

    /** Sesión de Pomodoro: solo bloquea durante las fases de foco. Se borra al terminar. */
    POMODORO,

    /** Botón de pánico: bloquea todo durante un tiempo y no se puede terminar antes. Se borra al caducar. */
    PANIC,
}

/** Plan de un Pomodoro: [sessions] focos separados por descansos; no hay descanso tras el último foco. */
data class PomodoroPlan(
    val startedAt: Instant,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val sessions: Int = 4,
) {
    init {
        require(focusMinutes > 0 && breakMinutes >= 0 && sessions > 0) { "plan de Pomodoro no válido" }
    }
}

data class Profile(
    val id: Long,
    val title: String,
    val emoji: String = "",
    val enabled: Boolean = true,
    val mode: BlockMode = BlockMode.BLOCK,
    val apps: Set<String> = emptySet(),
    val webs: List<WebRule> = emptyList(),
    /** Sin intervalos, el perfil está activo siempre que esté habilitado. */
    val intervals: List<ScheduleInterval> = emptyList(),
    val pausedUntil: Instant? = null,
    /** Condición de uso: el perfil solo bloquea cuando se agota. Null = sin límite. */
    val limit: UsageLimit? = null,
    val kind: ProfileKind = ProfileKind.NORMAL,
    /** Activo solo hasta esta hora (Quick Block / bloqueo manual). Null = sin tope. */
    val activeUntil: Instant? = null,
    val pomodoro: PomodoroPlan? = null,
    /** Activo solo al conectarse a una de estas redes Wi-Fi. Vacío = sin condición. */
    val wifiNames: Set<String> = emptySet(),
    /** Activo solo con uno de estos dispositivos Bluetooth conectado (auriculares, el coche…). Vacío = sin condición. */
    val bluetoothNames: Set<String> = emptySet(),
    val requireCharging: Boolean = false,
    val geofence: GeoFence? = null,
    val conditionMode: ConditionMode = ConditionMode.ALL,
    /** Ids de [SubAppRule] que el perfil bloquea dentro de sus apps (Shorts, Reels…). */
    val subApps: Set<String> = emptySet(),
    /** Ids de [AppCategory] que el perfil bloquea enteras, apps y webs (solo en modo bloquear). */
    val categories: Set<String> = emptySet(),
    /** Solo un Bloquear ya la cambia; el resto de perfiles se terminan como siempre. */
    val endPolicy: EndPolicy = EndPolicy.ANYTIME,
    val endConditions: EndConditions = EndConditions(),
) {
    /**
     * ¿Hay alguna manera de terminarlo antes de tiempo, a mano o con aprobación? El bloqueo de pánico, Dormir y un Bloquear ya «solo al acabar el tiempo»
     * no tienen ninguna; uno que se levanta con condiciones solo la tiene a mano si se eligió una espera.
     */
    val canEndEarly: Boolean
        get() = when {
            kind == ProfileKind.PANIC -> false
            endPolicy == EndPolicy.TIME_ONLY -> false
            endPolicy == EndPolicy.CONDITIONS -> endConditions.waitUntil != null
            else -> true
        }

    /** [canEndEarly] y, si lo que lo permite es una espera, que ya haya pasado. */
    fun canEndNow(now: Instant): Boolean =
        canEndEarly && (endPolicy != EndPolicy.CONDITIONS || endConditions.waitUntil?.let { !now.isBefore(it) } == true)

    /** Lo que se editó en pantalla; el estado (encendido, pausa, tipo, duración) se conserva como está ahora. Un campo nuevo se aplica solo. */
    fun withEditableFieldsFrom(edited: Profile) = edited.copy(
        id = id, enabled = enabled, pausedUntil = pausedUntil, kind = kind, activeUntil = activeUntil, pomodoro = pomodoro,
        endPolicy = endPolicy, endConditions = endConditions,
    )

    companion object {
        /**
         * Bloqueo máximo hasta [until] que no se puede deshacer: nada permitido salvo teléfono, emergencias, la propia app y, si se dan, [allowed].
         * Sin permitidas es Ayúdame; con ellas, Dormir (el reloj y la alarma, y lo que se elija).
         */
        fun panic(until: Instant, title: String = "Ayúdame", allowed: Set<String> = emptySet()) = Profile(
            id = 0, title = title, kind = ProfileKind.PANIC, mode = BlockMode.ALLOW_ONLY, apps = allowed, activeUntil = until,
        )
    }
}
