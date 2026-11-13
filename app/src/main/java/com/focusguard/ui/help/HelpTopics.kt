package com.focusguard.ui.help

import com.focusguard.ui.Routes
import java.text.Normalizer

/**
 * Una ficha de la sección Ayuda. [route] lleva a la pantalla de la función (null si no tiene una propia). [notes] son las excepciones y
 * lo que hay que tener en cuenta; [keywords] solo sirven al buscador (palabras con las que alguien buscaría esto sin saber cómo se llama).
 */
internal data class HelpTopic(
    val id: String,
    val title: String,
    val summary: String,
    val example: String,
    val notes: List<String>,
    val route: String? = null,
    val keywords: String = "",
)

/** El texto de la ayuda, en un solo sitio: la pantalla, el buscador y la prueba que exige una ficha por función salen de esta lista. */
internal object HelpTopics {

    val all: List<HelpTopic> = listOf(
        HelpTopic(
            id = "perfiles",
            title = "Perfiles",
            summary = "Un perfil es un conjunto de apps y webs que se bloquean, con su horario, sus condiciones y, si quieres, un límite de uso. Puedes tener varios a la vez; basta con que uno bloquee algo para que se bloquee. Un perfil puede llevar su propio candado, sin modo estricto.",
            example = "«Estudio»: redes y juegos, de lunes a viernes de 9:00 a 17:00.",
            notes = listOf(
                "En «Permitir solo esto» el perfil hace lo contrario: se bloquea todo menos lo que elijas.",
                "Nunca bloquea las llamadas, las emergencias ni la propia FocusGuard.",
                "Necesita el permiso de accesibilidad: sin él no puede ver qué app tienes abierta.",
            ),
            keywords = "bloquear apps webs reglas crear nuevo",
        ),
        HelpTopic(
            id = "horarios",
            title = "Horarios y condiciones",
            summary = "Un horario son los días y las horas en que el perfil está activo. Además puedes pedir que solo bloquee con ciertas condiciones: conectado a una red Wi-Fi, con un dispositivo Bluetooth (el coche, los auriculares), cargando o en un lugar.",
            example = "De 23:00 a 07:00 todos los días, y solo si el teléfono está cargando.",
            notes = listOf(
                "Si «Hasta» es menor que «Desde», el horario sigue al día siguiente.",
                "Con varias condiciones eliges si hacen falta todas o basta con una.",
                "Sin permiso de ubicación o de Bluetooth esas condiciones no se pueden leer y se dan por cumplidas.",
            ),
            keywords = "hora dias franja wifi bluetooth cargador lugar noche",
        ),
        HelpTopic(
            id = "limites",
            title = "Límite de uso",
            summary = "Con un límite, el perfil solo bloquea cuando lo agotas: minutos de uso, número de aperturas o minutos seguidos sin descanso. Puedes repartirlo entre todas las apps y webs del perfil (un solo contador) o darle uno a cada app. Mientras usas una app con límite, una notificación dice cuánto llevas y cuánto te queda.",
            example = "60 minutos al día entre Instagram y TikTok; al llegar a 60 se bloquean hasta mañana.",
            notes = listOf(
                "Ponerle un límite a un perfil que bloqueaba siempre lo debilita, así que con aprobaciones activadas lo pide.",
            ),
            keywords = "tiempo minutos aperturas descanso cuota",
        ),
        HelpTopic(
            id = "categorias",
            title = "Categorías",
            summary = "Al marcar una categoría (redes sociales, vídeo, juegos…) se añaden al perfil las apps de ese tipo que tienes instaladas y sus webs. Las verás en las listas del perfil.",
            example = "Marcar «Redes sociales» añade Instagram, TikTok y X, si los tienes, y sus páginas web.",
            notes = listOf(
                "La categoría sigue activa para las apps que instales más adelante.",
                "Para dejar pasar alguna, desmarca la categoría y quítala de la lista.",
                "Solo vale en modo «Bloquear». Las webs se reconocen en los navegadores compatibles, no en incógnito.",
            ),
            keywords = "redes sociales juegos compras noticias tipo grupo",
        ),
        HelpTopic(
            id = "bloquear-ya",
            title = "Bloquear ya",
            summary = "Un bloqueo inmediato con la duración que elijas, sin crear un perfil. Eliges las apps y webs, el tiempo (con el deslizador o con − y +), cómo se podrá terminar y empieza.",
            example = "«Bloquear ya» 45 minutos con las redes mientras terminas un informe, y que solo se levante antes si enchufas el cargador al llegar a casa.",
            notes = listOf(
                "«Cómo se puede terminar» se decide antes de empezar y no se cambia después: cuando quieras, solo al acabar el tiempo, o antes si se cumple algo.",
                "Las condiciones son llegar a un lugar, conectar el cargador, conectarte a un Wi-Fi, conectar un dispositivo Bluetooth, o esperar un rato y pedir aprobación. Basta con una.",
                "Si el teléfono no puede saber una condición (sin permiso de Ubicación o de Bluetooth), no se da por cumplida: el bloqueo sigue.",
                "Con el modo estricto activo no se puede terminar antes de tiempo a mano, y con aprobaciones activadas, terminarlo antes pasa por ellas.",
                "Ayúdame y Dormir nunca terminan antes: esto es solo para Bloquear ya.",
            ),
            route = Routes.QUICK_BLOCK,
            keywords = "rapido ahora inmediato sesion quick",
        ),
        HelpTopic(
            id = "enfoque",
            title = "Enfoque (Pomodoro)",
            summary = "Alterna tiempo de foco, donde se bloquea lo que elijas, con descansos, donde no. Tienes los ritmos 25/5, 50/10 y 90/20 o los eliges tú, y la pantalla te dice cuánto durará en total.",
            example = "25 minutos de foco, 5 de descanso, 4 veces: 1 h 55 min en total.",
            notes = listOf("Durante los descansos no se bloquea nada de lo que elegiste."),
            route = "session/pomodoro",
            keywords = "pomodoro concentracion estudiar trabajo foco descanso",
        ),
        HelpTopic(
            id = "dormir-ayudame",
            title = "Dormir y Ayúdame: cuál usar",
            summary = "Los dos bloquean el teléfono entero y no se pueden deshacer, pero sirven para cosas distintas. Ayúdame es para un impulso, ahora: bloquea todo durante unos minutos. Dormir es para la noche: bloquea hasta la hora de despertar y deja la alarma y las apps que elijas.",
            example = "Dormir: a las 22:30 lo activas «hasta las 7:00» y solo quedan la alarma y tu diario. Ayúdame: te llega un impulso fuerte y quieres que nada se pueda abrir 30 minutos.",
            notes = listOf(
                "Dormir: eliges una hora de despertar, no una duración. Siempre quedan permitidos el reloj y la alarma para que suene, y puedes añadir otras apps.",
                "Ayúdame: todo bloqueado salvo llamadas, emergencias y FocusGuard. Pide confirmación («No podrás deshacerlo hasta que termine el tiempo»), muestra la cuenta atrás en la app y en el widget, puede avisar a tu compañero y ofrece un ejercicio de respiración opcional con varios ritmos.",
                "Con los dos, hasta que pase el tiempo no se puede quitar, ni con aprobación.",
                "Para bloquear con una duración y elegir apps y webs, usa Bloquear ya.",
            ),
            route = Routes.PANIC,
            keywords = "panico impulso urgente dormir sueño noche despertar alarma bloqueo maximo",
        ),
        HelpTopic(
            id = "adulto",
            title = "Contenido adulto",
            summary = "El bloqueo de contenido adulto viene activado: bloquea las webs de contenido adulto y las palabras de tu propia lista, incluidas las páginas de manga para adultos. Funciona junto con el filtro DNS para cubrir todo el teléfono.",
            example = "Abres una página adulta en el navegador y en su lugar ves la pantalla de bloqueo.",
            notes = listOf(
                "Apagarlo pide aprobación y se enciende solo pasado un tiempo.",
                "Las apps de notas, documentos y agenda no se leen por texto (ver «Apps cuyo texto no se lee»).",
                "Las listas permitidas y las palabras propias se gestionan en Protección → Listas, palabras y excepciones.",
            ),
            route = Routes.ADULT,
            keywords = "porno adulto manga filtro palabras",
        ),
        HelpTopic(
            id = "dns",
            title = "Filtro DNS",
            summary = "Un filtro a nivel de teléfono: FocusGuard crea una VPN local y pregunta a tu servidor FocusGuard si cada dirección está permitida. Bloquea dominios en cualquier app o navegador, también en incógnito.",
            example = "Aunque abras una web bloqueada desde un navegador que no conoce la accesibilidad, no carga.",
            notes = listOf(
                "Solo ve nombres de dominio, no lo que lees ni lo que escribes.",
                "Android solo permite una VPN a la vez: con otra VPN activa, el filtro puede apagarse.",
                "Apagarlo o dejar una app fuera del filtro pide aprobación.",
            ),
            route = Routes.DNS,
            keywords = "vpn dominio adguard servidor",
        ),
        HelpTopic(
            id = "aprobaciones",
            title = "Aprobaciones",
            summary = "Todo cambio que debilita una protección (apagar un perfil, quitar una web, subir un límite…) puede pedir una aprobación. Tú eliges cómo: esperar 24 h o 72 h, escribir una frase larga, caminar unos pasos o que lo apruebe tu compañero.",
            example = "Quieres quitar Instagram de «Estudio»: con 24 h de espera, el cambio se aplica mañana si sigues queriéndolo.",
            notes = listOf(
                "Añadir o endurecer es libre: solo se pide para aflojar.",
                "Las solicitudes pendientes se ven en Protección → Aprobaciones y se pueden cancelar.",
                "Lo que aprueba tu compañero se aplica solo, sin tener que pulsar «Aplicar».",
            ),
            route = "approvals",
            keywords = "solicitud permiso espera frase pasos",
        ),
        HelpTopic(
            id = "companero",
            title = "Compañero de responsabilidad",
            summary = "Una persona de confianza que recibe avisos de lo que pasa en tu teléfono y puede aprobar o rechazar tus cambios desde su correo, con un botón. Lo invitas desde Ajustes → Compañero y tiene que aceptar.",
            example = "Pides apagar el filtro DNS: a tu compañero le llega un correo con «Aprobar» y «Rechazar».",
            notes = listOf(
                "Cuando acepta, te llega una notificación.",
                "Con el modo parental, todos los cambios pasan por él.",
                "Necesitas una cuenta (correo) y conexión para esto.",
            ),
            route = Routes.ACCOUNT,
            keywords = "pareja amigo avisos correo cuenta parental",
        ),
        HelpTopic(
            id = "modo-estricto",
            title = "Modo estricto",
            summary = "Mientras está activo no puedes editar perfiles ni los ajustes que lo desactivarían. Tú eliges cuándo se activa (siempre o en un horario) y qué protege. Para pausarlo o desactivarlo tienes que cumplir los métodos de salida que elegiste: esperar, el cargador, un PIN, pedirlo y esperar, o que lo apruebe tu compañero.",
            example = "Activo de lunes a viernes de 9 a 17; para salir hace falta esperar 10 minutos y que tu compañero lo apruebe.",
            notes = listOf(
                "Si eliges varios métodos, hacen falta todos.",
                "No impide reiniciar el teléfono en modo seguro ni restablecerlo de fábrica.",
                "Se recomienda activarlo después de la primera semana, cuando ya sabes qué reglas quieres proteger.",
            ),
            route = "strict",
            keywords = "bloqueo estricto salir desactivar pausar pin cargador",
        ),
        HelpTopic(
            id = "emergencia",
            title = "Desbloqueo de emergencia",
            summary = "Un respiro limitado para cuando de verdad lo necesitas: respondes con calma unas preguntas, escribes a mano un compromiso y el bloqueo se levanta unos minutos. Pasado ese tiempo, vuelve solo.",
            example = "Necesitas abrir la app del banco con un perfil activo: lo desbloqueas unos minutos.",
            notes = listOf(
                "Tiene un tope por semana; la pantalla te dice cuántos te quedan.",
                "Lo que escribes no se guarda.",
            ),
            route = Routes.EMERGENCY,
            keywords = "urgencia desbloquear excepcion",
        ),
        HelpTopic(
            id = "impulso",
            title = "Impulso",
            summary = "Una pantalla para los momentos en que algo te tira de coger el teléfono: te acompaña a esperar unos minutos con una respiración guiada y actividades, y al final apuntas si lo dejaste pasar.",
            example = "Te das cuenta de que ibas a abrir una app sin pensar: abres Impulso y dejas pasar las ganas.",
            notes = listOf(
                "Si cedes, no pasa nada: se apunta sin juicio.",
                "Solo se guarda en el teléfono cuándo empieza y cómo acaba.",
            ),
            route = Routes.URGE,
            keywords = "ganas antojo respirar urge craving",
        ),
        HelpTopic(
            id = "racha",
            title = "Racha",
            summary = "Cuenta los días seguidos en que lograste tu meta. Se ve el mejor récord, un calendario y por qué se perdió un día si ocurrió.",
            example = "Llevas 12 días seguidos sin saltarte el bloqueo.",
            notes = listOf(
                "Puedes reiniciarla; tu mejor racha se conserva.",
                "Un día perdido se puede restaurar con una aprobación, si lo permiten las reglas.",
            ),
            route = Routes.STREAK,
            keywords = "dias seguidos meta logro",
        ),
        HelpTopic(
            id = "estadisticas",
            title = "Estadísticas y resumen semanal",
            summary = "Cuánto usas el teléfono, qué apps y webs se llevan más tiempo y cuántas veces se bloqueó algo, por día y por semana.",
            example = "Ves que el martes pasaste 3 h en redes y que el bloqueo te frenó 14 veces.",
            notes = listOf(
                "Se calcula en el teléfono con tus eventos de uso: no sale a ningún servidor.",
                "Necesita el permiso de acceso al uso; sin él no hay datos.",
            ),
            route = Routes.WEEKLY,
            keywords = "uso tiempo pantalla informe semana",
        ),
        HelpTopic(
            id = "copia",
            title = "Copia de seguridad y sincronización",
            summary = "Guarda tu configuración cifrada en el servidor con una frase que solo sabes tú, y recupérala en otro teléfono o tras reinstalar. También puedes sincronizar varios dispositivos.",
            example = "Cambias de teléfono: instalas FocusGuard, inicias sesión y restauras la copia con tu frase.",
            notes = listOf(
                "Sin la frase la copia no se puede abrir: el servidor no la conoce.",
                "Restaurar añade tus perfiles y nunca quita ni afloja una protección que ya tengas.",
            ),
            route = Routes.BACKUP,
            keywords = "backup restaurar exportar importar frase nuevo telefono",
        ),
        HelpTopic(
            id = "permisos",
            title = "Permisos y problemas",
            summary = "FocusGuard necesita que actives a mano unos permisos de Android (accesibilidad, mostrar sobre otras apps, uso de apps, administrador del dispositivo…). Cada uno explica para qué sirve, y el solucionador te dice qué falta.",
            example = "El bloqueo no salta: el solucionador te avisa de que falta el permiso de accesibilidad.",
            notes = listOf(
                "Ningún permiso lo concede la app por ti: Android obliga a hacerlo desde sus ajustes.",
                "El ahorro de batería de algunos móviles apaga la app; el solucionador lo detecta.",
            ),
            route = Routes.TROUBLESHOOTER,
            keywords = "accesibilidad bateria no funciona solucionador problemas",
        ),
        HelpTopic(
            id = "desinstalar",
            title = "Evitar que la app se desinstale",
            summary = "Con el permiso de administrador del dispositivo activo, Android no deja desinstalar FocusGuard (ni con una orden de adb) hasta que se desactive el administrador. Mientras el administrador esté activo, las pantallas de Android donde se desactiva él o la accesibilidad de FocusGuard se cierran: hay que pedir permiso antes (una aprobación o tu compañero). Con el modo estricto se cierran además otras pantallas de ajustes.",
            example = "En un momento de debilidad intentas desinstalar la app: Android lo impide.",
            notes = listOf(
                "No frena el modo seguro ni el restablecimiento de fábrica.",
                "Con el permiso concedido, durante 10 minutos se puede desactivar el administrador o la accesibilidad desde los ajustes de Android; con el modo estricto no se puede.",
                "Abrir la pantalla de accesibilidad de Android, aunque sea para otra cosa, se cierra mientras FocusGuard aparece en ella.",
            ),
            route = "permissions",
            keywords = "desinstalar borrar quitar administrador",
        ),
        HelpTopic(
            id = "privacidad",
            title = "Qué se queda en el teléfono",
            summary = "El bloqueo se decide en el teléfono. Lo que ves en pantalla, tu uso y tus estadísticas no salen de él. Al servidor solo va lo que tú activas: la cuenta, los correos al compañero, las copias cifradas y las consultas del filtro DNS.",
            example = "Con el compañero, el servidor sabe que pediste apagar el DNS, pero no lo que estabas mirando.",
            notes = listOf(
                "La copia de seguridad va cifrada con tu frase.",
                "El filtro DNS solo ve nombres de dominio.",
            ),
            keywords = "datos servidor seguridad privacidad",
        ),
        HelpTopic(
            id = "imagenes",
            title = "Detección de imágenes",
            summary = "Con la detección encendida, la app captura la pantalla (con el permiso de accesibilidad) y dos modelos que corren en el teléfono, GantMan y NudeNet, puntúan si hay contenido explícito o sugerente. Lo explícito se bloquea con la pantalla de bloqueo; lo sugerente te avisa con una notificación que ofrece «Bloquear ahora». No se guarda ninguna imagen y nada sale del teléfono. Necesita Android 11 o más.",
            example = "Abres una red social, sale una foto explícita y la pantalla se bloquea; si es solo sugerente, te llega un aviso con el botón para bloquearla.",
            notes = listOf(
                "Ritmo: al entrar en una app o web se captura lo más seguido que se puede durante 2 minutos y después cada vez más espaciado, hasta un tope de 102 s (la mitad en redes y vídeo, el doble en webs sin riesgo). Algo dudoso vuelve a poner 2 minutos al ritmo mínimo.",
                "Sensibilidad (1 a 5): subirla es libre; bajarla pide aprobación de tu compañero si lo tienes. Con más sensibilidad se marca más, también más falsos avisos.",
                "Un aviso de lo sugerente no se repite hasta pasados N minutos (1 a 30, de serie 5); lo explícito se bloquea siempre.",
                "Vigilancia tras lo explícito: durante un tiempo (5 a 60 min) se captura más seguido lo que puede ser un riesgo, con intensidad suave, media o alta.",
                "Modo dinámico: cuantas más veces se bloquea algo explícito en el periodo (6 h a 7 días), más sube el nivel: se captura más seguido, el enfriamiento dura más y la sensibilidad sube un paso. Baja solo con el tiempo.",
                "Apagarla, bajar la sensibilidad, vigilar menos o adaptar menos pide aprobación del compañero; con el modo estricto no se puede.",
                "No mira las apps de confianza ni las ventanas que Android marca como seguras. Gasta más batería durante los primeros 2 minutos en cada app.",
            ),
            keywords = "imagen imagenes fotos explicita sugerente nudenet gantman captura sensibilidad vigilancia dinamico",
        ),
        HelpTopic(
            id = "adulto-apps",
            title = "Apps cuyo texto no se lee",
            summary = "El filtro adulto lee el texto de la pantalla de las apps y bloquea cuando coinciden dos palabras distintas de su lista. En las apps de notas, documentos, agenda, tareas, reloj y calculadora (Obsidian, Keep, Notion, Docs…) no lee el texto, para que escribir ahí sobre tu problema no las bloquee. Puedes añadir más apps en Contenido adulto.",
            example = "En Obsidian escribes sobre tu problema con el porno y la app no se bloquea.",
            notes = listOf(
                "Añadir una app pide aprobación (tu compañero, o el modo estricto la impide); volver a leerla es libre.",
                "Los navegadores nunca se saltan: sus webs se miran siempre, y la dirección de una web también.",
                "Cuando algo se bloquea por texto, la pantalla de bloqueo dice «Coincidió con:» y las palabras, solo en tu teléfono.",
                "Una palabra que cabe dentro de otra (como «porn» en «porno») cuenta como una sola mención.",
            ),
            route = Routes.ADULT,
            keywords = "obsidian notas texto palabras excluir exenta lista blanca whitelist coincidio",
        ),
        HelpTopic(
            id = "bloqueo-apps",
            title = "Más opciones para bloquear apps",
            summary = "Además de la lista del perfil, FocusGuard puede cerrar las ventanas Picture-in-Picture y las flotantes de apps bloqueadas, pausar su música o vídeo, bloquear las apps recién instaladas durante un tiempo, bloquear los navegadores dentro de otras apps y detectar la pantalla dividida. Con el modo estricto, también cierra el menú de apagado, el panel de notificaciones, el administrador de tareas, los diálogos de compra y el ahorro extremo de batería.",
            example = "Instalas una app nueva y queda bloqueada unas horas hasta que decidas.",
            notes = listOf(
                "Las apps recién instaladas se pueden añadir solas a un perfil, o bloquearse por un plazo con un interruptor global; aflojarlo pide aprobación.",
                "Los navegadores integrados (vistas web dentro de apps) se pueden bloquear en las apps que elijas o en todas.",
                "Hay una lista de apps ignoradas: no cuentan en las estadísticas ni se bloquean. Nunca se bloquean el teléfono, las emergencias ni FocusGuard.",
            ),
            keywords = "pip ventana flotante musica nuevas instaladas navegador integrado pantalla dividida compras apagado panel",
        ),
        HelpTopic(
            id = "bloqueo-webs",
            title = "Bloquear webs",
            summary = "FocusGuard lee la barra de direcciones de más de 38 navegadores y bloquea por dominio (con sus subdominios) o por palabra en cualquier parte de la dirección. Puedes tener listas de permitidos, detectar las pestañas de incógnito, bloquear los navegadores que no se pueden leer, importar webs o palabras desde un archivo de texto y forzar la búsqueda segura en Google, Bing y YouTube.",
            example = "Bloqueas «ejemplo.com» y también «foro.ejemplo.com» se bloquea.",
            notes = listOf(
                "Al bloquear, el navegador va a una página en blanco o a la dirección que elijas (nunca una web adulta).",
                "Si una web se bloquea sin ser lo que parece, la pantalla de bloqueo permite reportarla para que se revise la lista.",
                "El filtro DNS cubre además los navegadores que no se pueden leer.",
            ),
            keywords = "web url dominio navegador incognito buscador seguro importar csv redirigir",
        ),
        HelpTopic(
            id = "subapps",
            title = "Reels, Shorts y otros dentro de apps",
            summary = "Puedes bloquear partes concretas de una app sin bloquearla entera: los Shorts de YouTube, los Reels, las historias y la búsqueda de Instagram, las historias y Spotlight de Snapchat, la búsqueda de Telegram y otros. Cada una es un interruptor. Al detectarlo, la app vuelve atrás.",
            example = "Puedes ver los vídeos de tus suscripciones en YouTube pero no los Shorts.",
            notes = listOf(
                "Los identificadores de esas pantallas cambian con cada versión de la app, así que se descargan del servidor y pueden fallar tras una actualización.",
            ),
            keywords = "shorts reels historias stories spotlight instagram youtube snapchat subapps",
        ),
        HelpTopic(
            id = "condiciones",
            title = "Ubicación, Wi-Fi y otras condiciones",
            summary = "Un perfil puede activarse solo en ciertas condiciones: en un lugar (con un radio, o al revés, estando fuera), conectado a una red Wi-Fi, con el teléfono cargando o con un dispositivo Bluetooth conectado. Se combinan todas (y) o cualquiera (o). También hay límites de tiempo de uso y de número de aperturas.",
            example = "Un perfil «Casa» que solo bloquea cuando estás conectado a tu Wi-Fi.",
            notes = listOf(
                "Pausar un horario un rato pide los pasos de las aprobaciones y avisa antes de que termine.",
                "La app avisa si dos horarios chocan (mismos días, mismo modo, intervalos que se solapan).",
                "Se puede duplicar un horario, ver su vista previa y bloquear su edición hasta una hora.",
                "Con el modo estricto apagado, abrir un horario para editarlo exige esperar 10 segundos.",
            ),
            keywords = "ubicacion geovalla wifi bluetooth cargador condiciones combinar pausa duplicar conflicto",
        ),
        HelpTopic(
            id = "plantillas",
            title = "Plantillas de horario",
            summary = "En la pestaña Perfiles, un perfil se puede crear desde una plantilla: enfoque, tiempo para mí, vuelta al cole, vacaciones y otras. La plantilla ya trae horarios y condiciones razonables; después se ajusta como cualquier perfil.",
            example = "Empiezas con la plantilla de enfoque y solo cambias qué apps bloquea.",
            notes = listOf(
                "Las plantillas de serie son genéricas. Las personales no vienen con la app.",
            ),
            route = Routes.TEMPLATES,
            keywords = "plantilla modelo empezar rapido",
        ),
        HelpTopic(
            id = "atajos",
            title = "Widgets, ajustes rápidos y atajos",
            summary = "Hay un botón de pánico en los ajustes rápidos del sistema, widgets de la pantalla de inicio (Bloquear ya, horarios, «Ayúdame» y días activos) y tres atajos al mantener pulsado el icono: «Ayúdame», «Bloquear ya» y «Quitar bloqueos».",
            example = "Mantienes pulsado el icono y eliges «Ayúdame» sin abrir la app.",
            notes = listOf(
                "«Quitar bloqueos» pide apagar cada perfil encendido por el camino de siempre: con modo estricto no se puede y con aprobaciones se crea una solicitud por perfil.",
                "Desde fuera de la app solo se abre la pantalla: nada cambia hasta que lo confirmas dentro.",
            ),
            keywords = "widget atajo icono pulsacion larga ajustes rapidos tile",
        ),
        HelpTopic(
            id = "notificaciones",
            title = "Avisos",
            summary = "Puedes recibir avisos antes de que empiece un horario, cuando se acaba un límite o va a acabarse, al terminar un horario o un bloqueo, cuando termina una pausa, una frase motivadora diaria y un resumen semanal. También se pueden guardar las notificaciones de las apps bloqueadas para verlas después, y ocultar 24 h el aviso del sistema de «app en segundo plano».",
            example = "Te avisa diez minutos antes de que empiece tu horario de estudio.",
            notes = listOf(
                "Cada aviso se activa o apaga por separado en Ajustes → Avisos; lo de las notificaciones guardadas, en Ajustes → Notificaciones de apps.",
            ),
            route = Routes.NOTIFICATIONS,
            keywords = "avisos notificaciones recordatorios resumen semanal frase",
        ),
        HelpTopic(
            id = "insights",
            title = "Insights, ánimo y avisos de tiempo",
            summary = "Además de las estadísticas, la app compara tu uso por día y semana, enseña tu mayor racha de uso continuo y de descanso, genera un informe semanal, puede preguntarte cómo te sientes y mostrar una insignia de tendencia. El aviso de tiempo te avisa sin bloquear cuando llevas 15 minutos de uso en una hora.",
            example = "El informe semanal te dice que usaste un 20 % menos las redes que la semana pasada.",
            notes = listOf(
                "Todo se calcula en el teléfono.",
            ),
            route = Routes.WEEKLY,
            keywords = "insights semanal animo tendencia aviso tiempo comparacion",
        ),
        HelpTopic(
            id = "recuperacion",
            title = "Programa de recuperación, diario y test",
            summary = "Un programa por pasos para identificar tus disparadores, un diario con «qué podría haber hecho mejor», un planificador de hasta tres actividades por día que no se pueden editar después y un test de adicción con puntuación de gravedad guardada en tu servidor.",
            example = "Anotas en el diario qué te llevó al impulso y planificas tres actividades para mañana.",
            notes = listOf(
                "El informe de impulsos lo ves tú y, si lo permites, tu compañero (solo el resumen, nunca el detalle).",
            ),
            route = Routes.RECOVERY,
            keywords = "recuperacion diario planificador test adiccion programa disparadores",
        ),
        HelpTopic(
            id = "pantalla-bloqueo",
            title = "Personalizar la pantalla de bloqueo",
            summary = "Puedes elegir colores, emoji, un mensaje propio, mensajes motivadores que rotan y una frase distinta cuando se intenta apagar o desinstalar la app. Hay vista previa. El Temporizador te deja dentro de la pantalla un tiempo sin poder salir.",
            example = "Pones «Respira, este rato pasa» y un fondo verde.",
            notes = listOf(
                "Tu mensaje propio sustituye al de serie, y con él ya no se añade la lista de coincidencias del filtro adulto.",
                "El bloqueo de arranque tapa la pantalla unos minutos tras reiniciar hasta que los servicios estén listos.",
            ),
            keywords = "pantalla bloqueo personalizar colores emoji mensaje motivador temporizador arranque",
        ),
        HelpTopic(
            id = "pin-icono",
            title = "PIN, patrón e icono disimulado",
            summary = "Puedes proteger la entrada a la app con un PIN o patrón; si lo olvidas, un código de recuperación llega al correo de tu cuenta. También puedes cambiar el icono y el nombre de la app (calculadora, notas o reloj) para disimularla.",
            example = "Pones PIN a FocusGuard y le cambias el icono a «Calculadora».",
            notes = listOf(
                "Cambiar el icono se aprueba como cualquier cambio que debilita, y el compañero se entera.",
                "El servidor no guarda tu PIN.",
            ),
            keywords = "pin patron icono disfrazar disimular calculadora recuperar codigo",
        ),
        HelpTopic(
            id = "varios-companeros",
            title = "Varios compañeros",
            summary = "Puedes tener hasta tres compañeros de responsabilidad. Cada solicitud de aprobación les llega a todos por correo y basta con que uno la apruebe; el primero que responde decide y los enlaces de los demás dejan de valer. Cada uno recibe los avisos que tenga activados.",
            example = "Pides apagar el filtro: tu hermana y tu amigo reciben el correo y ella lo rechaza antes de que él lo abra.",
            notes = listOf(
                "Quitar a un compañero lo tiene que aprobar él, no los demás.",
                "Si das de baja a uno, las solicitudes que esperaban también a los demás siguen vivas.",
                "Cada compañero elige sus avisos desde el enlace de sus correos.",
            ),
            route = Routes.ACCOUNT,
            keywords = "companeros varios dos tres mas de uno aprobar hermano amigo pareja",
        ),
        HelpTopic(
            id = "parental",
            title = "Modo parental",
            summary = "Para el teléfono de un hijo o una hija: con el modo encendido y un compañero activo (su padre o su madre), todo cambio que debilite la protección lo aprueba el compañero, sin retardos ni frases. Quitar el modo también lo tiene que aprobar.",
            example = "Tu hija pide quitar un bloqueo y a ti te llega el correo para aprobarlo o no.",
            notes = listOf(
                "Se apaga solo si el compañero deja de estar activo o se cierra la sesión.",
                "Hoy es un interruptor de la cuenta, no de un compañero concreto.",
            ),
            route = Routes.ACCOUNT,
            keywords = "parental hijo hija padre madre ninos menores",
        ),
        HelpTopic(
            id = "ayudas",
            title = "Personas a las que ayudas",
            summary = "Si alguien te eligió como compañero, en Ajustes → Cuenta ves a quién ayudas y cuántas solicitudes suyas esperan tu respuesta en el correo. Si tú también quieres un compañero, puedes pedirle que lo sea: así os cuidáis los dos.",
            example = "Ves «1 solicitud espera tu respuesta» y la apruebas desde el correo.",
            notes = listOf(
                "Solo cuenta quien confirmó su correo.",
            ),
            route = Routes.ACCOUNT,
            keywords = "ayudar companero mutuo solicitudes pendientes",
        ),
        HelpTopic(
            id = "sincronizacion",
            title = "Sincronización y avisos del servidor",
            summary = "Con una cuenta, las palabras y webs propias y los interruptores que endurecen (contenido adulto, ventanas flotantes) se sincronizan entre tus dispositivos: solo se suma, nunca se afloja. El servidor también avisa al teléfono con un push cuando tu compañero responde.",
            example = "Añades una palabra en un teléfono y aparece en el otro.",
            notes = listOf(
                "Las listas públicas de bloqueo se descargan del servidor y funcionan sin conexión.",
                "Si no llega el push, la respuesta se ve al abrir la app o en la siguiente sincronización.",
            ),
            route = Routes.ACCOUNT,
            keywords = "sincronizar dispositivos push notificacion servidor cuenta",
        ),
        HelpTopic(
            id = "ajustes",
            title = "Ajustes generales",
            summary = "En Ajustes están la cuenta y el compañero, la copia de seguridad, los avisos, las notificaciones de apps, la pantalla de bloqueo, el icono y el PIN de la app, la hora de inicio del día (cuando se reinician los límites), las estadísticas de navegación y la sección Ayuda. El aviso de tiempo y los recordatorios están en Progreso; los navegadores integrados y las apps recién instaladas, en Protección, igual que la detección de imágenes y el filtro DNS.",
            example = "Si duermes de madrugada, pones el inicio del día a las 04:00.",
            notes = listOf(
                "Apagar o aflojar una protección desde Ajustes pasa por las aprobaciones.",
            ),
            keywords = "ajustes configuracion idioma tema inicio del dia",
        ),
        HelpTopic(
            id = "asistente",
            title = "Asistente de permisos y solucionador",
            summary = "Al empezar, un asistente te guía por los permisos que necesita FocusGuard: accesibilidad, uso de apps, superposición, administrador del dispositivo, batería sin optimizar y, en algunos fabricantes, inicio automático. El solucionador de problemas te dice qué falta y cómo arreglarlo.",
            example = "Un perfil no bloquea nada: el solucionador te dice que falta el permiso de accesibilidad.",
            notes = listOf(
                "El administrador del dispositivo es imprescindible: sin él Android no impide desinstalar.",
                "Si apagas el servicio de accesibilidad, el compañero recibe un aviso.",
            ),
            route = Routes.TROUBLESHOOTER,
            keywords = "permisos asistente solucionador accesibilidad bateria fabricante xiaomi samsung problema",
        ),
    )

    fun byId(id: String): HelpTopic? = all.firstOrNull { it.id == id }

    /** Las fichas que contienen todas las palabras de [query], sin distinguir mayúsculas ni acentos. Sin texto, todas. */
    fun search(query: String, topics: List<HelpTopic> = all): List<HelpTopic> {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return topics
        return topics.filter { topic ->
            val haystack = normalize((listOf(topic.title, topic.summary, topic.example, topic.keywords) + topic.notes).joinToString(" "))
            words.all { it in haystack }
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
