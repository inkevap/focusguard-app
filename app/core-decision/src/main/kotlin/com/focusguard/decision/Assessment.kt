package com.focusguard.decision

/** Qué tanto pesa el hábito según el test (L6). */
enum class AssessmentBand(val label: String, val advice: String) {
    LOW("Bajo", "Por ahora tu uso no parece pesar mucho. Mantener tus rutinas y tus bloqueos te ayuda a seguir así."),
    MODERATE("Moderado", "Empieza a notarse. Es un buen momento para fijar bloqueos y probar el programa de recuperación."),
    HIGH(
        "Alto",
        "Pesa bastante en tu día a día. Conviene usar el programa de recuperación y contárselo a tu compañero o a alguien de confianza.",
    ),
    VERY_HIGH(
        "Muy alto",
        "Está afectando a muchas cosas. Además de usar la app, te recomendamos hablar con un profesional de la salud: pedir ayuda es un paso fuerte, no una derrota.",
    ),
}

/**
 * Test de adicción (L6): diez preguntas sobre el último mes, cada una de 0 («nunca») a 4 («casi siempre»), que suman de 0 a 40. Las preguntas
 * son propias y no vienen de un test clínico: sirve para ver cómo vas y compararte contigo mismo con el tiempo, no para diagnosticar.
 */
object Assessment {
    const val MAX_SCORE = 40

    const val DISCLAIMER = "Este test no es un diagnóstico y no sustituye a un profesional. Sirve para que veas cómo vas y cómo cambia con el tiempo."

    val OPTIONS = listOf("Nunca", "Casi nunca", "A veces", "A menudo", "Casi siempre")

    val QUESTIONS = listOf(
        "Paso más tiempo del que pensaba en lo que quiero dejar.",
        "He intentado reducirlo y no lo he conseguido.",
        "Pienso en ello a menudo durante el día.",
        "Lo uso para escapar de un mal momento: estrés, tristeza o soledad.",
        "Lo hago aunque tengo cosas importantes pendientes.",
        "Lo escondo o miento sobre cuánto tiempo le dedico.",
        "Me siento mal después: culpa, vergüenza o vacío.",
        "Me cuesta dormir o descansar por su causa.",
        "Ha afectado a mi trabajo, mis estudios o mis relaciones.",
        "Si no puedo hacerlo, me siento inquieto o irritable.",
    )

    /** La puntuación, o `null` si falta alguna respuesta, sobra alguna o alguna está fuera de 0 a 4. */
    fun score(answers: List<Int?>): Int? {
        if (answers.size != QUESTIONS.size) return null
        var total = 0
        for (answer in answers) {
            if (answer == null || answer !in 0 until OPTIONS.size) return null
            total += answer
        }
        return total
    }

    fun band(score: Int): AssessmentBand = when {
        score < 10 -> AssessmentBand.LOW
        score < 20 -> AssessmentBand.MODERATE
        score < 30 -> AssessmentBand.HIGH
        else -> AssessmentBand.VERY_HIGH
    }

    /** Cómo va respecto a la vez anterior, para decirlo con palabras; `null` si es la primera. */
    fun comparison(previous: Int?, current: Int): String? = when {
        previous == null -> null
        current < previous -> "Mejor que la última vez: ${previous - current} ${if (previous - current == 1) "punto" else "puntos"} menos."
        current > previous -> "Un poco más que la última vez: ${current - previous} ${if (current - previous == 1) "punto" else "puntos"} más. Tampoco pasa nada: es una foto de este mes."
        else -> "Igual que la última vez."
    }
}
