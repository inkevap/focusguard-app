package com.focusguard.decision

/** Qué se le pide al usuario en un paso del programa de recuperación (L5). */
enum class StepInput {
    /** Solo leer y marcarlo como hecho. */
    NONE,

    /** Elegir qué lo desencadena, de [UrgeTrigger]. */
    TRIGGERS,

    /** Elegir en qué momentos es más difícil, de [Recovery.MOMENTS]. */
    MOMENTS,

    /** Escribir una frase propia. */
    NOTE,
}

data class RecoveryStep(val id: Int, val title: String, val intro: String, val points: List<String>, val input: StepInput, val prompt: String? = null)

/** Lo que el usuario contestó en un paso: el día (lógico, desde 1970) en que lo hizo y la respuesta. */
data class StepRecord(val doneDay: Long, val answer: String)

sealed interface StepStatus {
    data class Done(val record: StepRecord) : StepStatus

    /** El siguiente paso, ya disponible. */
    data object Open : StepStatus

    /** Aún no: se abre en [openDay] (un paso por día, para que cada uno se asiente). */
    data class Locked(val openDay: Long?) : StepStatus
}

/**
 * Programa de recuperación por pasos (L5): siete pasos cortos, uno por día como mucho, que llevan de entender el hábito a tener un plan para
 * los momentos difíciles. Todo se guarda solo en el teléfono. El paso siguiente se abre el día después de hacer el anterior.
 */
object Recovery {
    const val MAX_NOTE = 500

    /** Momentos de riesgo que se pueden marcar en el paso 3. */
    val MOMENTS = listOf(
        "Por la noche, en la cama",
        "Al despertar",
        "Solo en casa",
        "En una pausa del trabajo o del estudio",
        "Después de discutir o de un mal día",
        "Aburrido en el sofá",
        "Esperando o de viaje",
    )

    val STEPS = listOf(
        RecoveryStep(
            1, "Entiende qué pasa",
            "Un hábito se sostiene porque da algo: alivio, distracción, compañía. Tenerlo no te hace débil. Este programa son siete pasos cortos, uno por día.",
            listOf("Dedícale unos minutos, no más.", "No hay respuestas incorrectas.", "Puedes volver a leer cualquier paso cuando quieras."),
            StepInput.NONE,
        ),
        RecoveryStep(
            2, "Tus disparadores",
            "Un disparador es lo que ocurre justo antes del impulso: un estado de ánimo, un lugar, una hora. Si los reconoces, los ves venir.",
            listOf("Piensa en las últimas veces que abriste el teléfono sin querer.", "Elige los que más se repiten."),
            StepInput.TRIGGERS,
        ),
        RecoveryStep(
            3, "Tus momentos de riesgo",
            "Además de lo que sientes, importa cuándo y dónde. Casi siempre son los mismos dos o tres momentos del día.",
            listOf("Marca los que te suenan.", "Son los momentos en los que más te va a ayudar tener un plan."),
            StepInput.MOMENTS,
        ),
        RecoveryStep(
            4, "Tu plan alternativo",
            "Decidir ahora qué vas a hacer, con la cabeza fría, es más fácil que decidirlo en mitad del impulso.",
            listOf("Algo concreto y pequeño: salir a la calle, llamar a alguien, ducharte.", "También puedes apuntarlo como actividad en el diario."),
            StepInput.NOTE,
            "Cuando me pase, haré…",
        ),
        RecoveryStep(
            5, "Quién te apoya",
            "Contárselo a alguien cambia las cosas: dejas de aguantar a solas. No hace falta contar todo; basta con poder escribir un mensaje corto.",
            listOf("Puede ser tu compañero de responsabilidad, un amigo o alguien de la familia."),
            StepInput.NOTE,
            "Si el impulso es fuerte, le escribiré a…",
        ),
        RecoveryStep(
            6, "Si hay un tropiezo",
            "Un tropiezo no borra lo que llevas. Lo que más pesa es cómo reaccionas en la hora siguiente: sin castigarte y volviendo al plan.",
            listOf("Piensa qué te dirías a ti mismo si fuera un amigo.", "Decide ya cuál es tu primer paso después de un tropiezo."),
            StepInput.NOTE,
            "Después de un tropiezo, lo primero que haré es…",
        ),
        RecoveryStep(
            7, "Seguir adelante",
            "Ya tienes tus disparadores, tus momentos de riesgo y un plan. Lo que queda es mantenerlo vivo.",
            listOf("Revisa el informe de impulsos de vez en cuando: te enseña cuándo suelen llegar.", "Si algo cambia, vuelve a cualquier paso y ajústalo."),
            StepInput.NONE,
        ),
    )

    /**
     * El estado de cada paso con lo hecho hasta ahora ([progress], por número de paso) y el día de hoy. Se hacen en orden: solo el primero
     * pendiente puede estar abierto, y lo está si el anterior se hizo antes de hoy; los demás esperan.
     */
    fun statuses(progress: Map<Int, StepRecord>, today: Long): List<StepStatus> {
        val firstPending = STEPS.firstOrNull { it.id !in progress }?.id
        return STEPS.map { step ->
            val done = progress[step.id]
            when {
                done != null -> StepStatus.Done(done)
                step.id == firstPending -> {
                    val before = progress[step.id - 1]
                    if (before == null || before.doneDay < today) StepStatus.Open else StepStatus.Locked(before.doneDay + 1)
                }
                else -> StepStatus.Locked(null)
            }
        }
    }

    /** ¿Se puede dar por hecho el paso con esta respuesta? Elegir algo o escribir algo cuando el paso lo pide. */
    fun canComplete(step: RecoveryStep, answer: String): Boolean = when (step.input) {
        StepInput.NONE -> true
        StepInput.TRIGGERS, StepInput.MOMENTS -> answer.split(SEPARATOR).any { it.isNotBlank() }
        StepInput.NOTE -> answer.trim().length >= MIN_NOTE
    }

    /**
     * La respuesta tal como se guarda: lo elegido solo puede ser de las opciones del paso (sin repetidos ni inventos) y lo escrito va
     * recortado y en una línea. Es el único sitio que decide qué se guarda; la pantalla y el almacén pasan por aquí.
     */
    fun normalize(step: RecoveryStep, raw: String): String = when (step.input) {
        StepInput.NONE -> ""
        StepInput.TRIGGERS -> encodeChoices(decodeChoices(raw).filter { it in TRIGGER_NAMES }.distinct())
        StepInput.MOMENTS -> encodeChoices(decodeChoices(raw).filter { it in MOMENTS }.distinct())
        StepInput.NOTE -> cleanNote(raw)
    }

    /** La respuesta de un paso de elegir, como texto guardable. */
    fun encodeChoices(choices: Collection<String>): String = choices.joinToString(SEPARATOR.toString())

    fun decodeChoices(answer: String): List<String> = answer.split(SEPARATOR).filter { it.isNotBlank() }

    /** Lo escrito, recortado y en una sola línea, para guardar. */
    fun cleanNote(text: String): String = text.replace(WHITESPACE, " ").trim().take(MAX_NOTE)

    /** Cuántos pasos se hicieron. */
    fun doneCount(progress: Map<Int, StepRecord>) = STEPS.count { progress.containsKey(it.id) }

    private const val SEPARATOR = '|'
    private val TRIGGER_NAMES = UrgeTrigger.entries.map { it.name }.toSet()
    private val WHITESPACE = Regex("\\s+")
    private const val MIN_NOTE = 3
}
