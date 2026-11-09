package com.focusguard.decision

/**
 * Apps cuyo texto de pantalla el filtro adulto no lee: las que casi nunca enseñan contenido para adultos y donde lo que sale es del propio usuario
 * (notas, documentos, calendario, tareas, reloj). Escribir ahí sobre el problema que se tiene no es un motivo para bloquear. Los navegadores nunca
 * entran: su dirección y su texto siempre se miran. La dirección de una web se sigue mirando aunque se pidiera lo contrario.
 */
object AdultTextExemptions {

    /** De serie, sin pedir nada. Paquetes conocidos; los que no estén instalados no hacen nada. */
    val DEFAULT: Set<String> = setOf(
        // Notas y escritura
        "md.obsidian", "com.google.android.keep", "notion.id", "com.evernote", "com.microsoft.office.onenote", "net.cozic.joplin",
        "com.automattic.simplenote", "org.standardnotes", "com.samsung.android.app.notes", "com.miui.notes", "com.oneplus.note",
        // Documentos y hojas de cálculo
        "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
        "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
        // Agenda y tareas
        "com.google.android.calendar", "com.google.android.apps.tasks", "com.todoist", "com.ticktick.task",
        // Reloj y calculadora
        "com.google.android.deskclock", "com.google.android.calculator", "com.android.calculator2", "com.android.deskclock",
    )

    /** ¿No hay que leer el texto de [packageName]? Nunca en un navegador; sí si es de serie o el usuario lo eligió. */
    fun skipsText(packageName: String, isBrowser: Boolean, userChosen: Set<String>): Boolean =
        !isBrowser && (packageName in DEFAULT || packageName in userChosen)
}
