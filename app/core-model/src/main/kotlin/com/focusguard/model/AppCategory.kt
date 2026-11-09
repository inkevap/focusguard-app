package com.focusguard.model

/** Categorías de apps y webs que se pueden bloquear de una vez (A12). Los ids son los de `apps_webs_categories.csv`. */
enum class AppCategory(val id: String, val label: String) {
    SOCIAL("social", "Redes sociales"),
    VIDEO("video", "Vídeo"),
    COMMUNICATION("communication", "Mensajería"),
    SHOPPING("shopping", "Compras"),
    MUSIC("music", "Música"),
    PHOTO("photo", "Fotos"),
    GAMES("games", "Juegos"),
    NEWS("news", "Noticias"),
    DATING("dating", "Citas"),
    TRAVEL("travel", "Viajes"),
    EDUCATION("education", "Educación"),
}
