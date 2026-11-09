# Catálogo completo de funciones

FocusGuard es una versión propia que integra lo mejor de dos apps de bloqueo: un bloqueador de productividad (perfiles, horarios, límites, modo estricto) y un bloqueador de contenido adulto con apoyo de recuperación (compañero, DNS, impulsos, rachas). Todas las funciones están siempre disponibles: no hay planes de pago, límites de versión gratuita, anuncios ni comprobaciones de licencia. El backend es el VPS propio.

Este catálogo completa `ESPECIFICACION.md`. Cuando haya diferencias, manda este documento. Cada función indica la fase (F1 a F6, ver `ROADMAP.md`).

Convenciones: **[App]** corre en el teléfono; **[Srv]** necesita el servidor propio.

---

## A. Bloqueo de apps

| # | Función | Fase |
|---|---|---|
| A1 | Modo lista de bloqueo: se bloquean las apps elegidas | F1 |
| A2 | Modo lista de permitidos: se bloquea todo salvo lo elegido; apps del sistema, teléfono, emergencias y la propia app quedan fuera | F1 |
| A3 | Detección de la app en primer plano (ver `ESPECIFICACION.md` 1.1) | F1 |
| A4 | Pantalla de bloqueo a pantalla completa; si falta el permiso de superposición, una actividad propia con el mismo contenido | F1 |
| A5 | Al bloquear, enviar a la pantalla de inicio | F1 |
| A6 | Cerrar ventanas Picture-in-Picture de la app bloqueada | F2 |
| A7 | Plegar ventanas flotantes y popups de apps bloqueadas (opción) | F3 |
| A8 | Pausar la música o el vídeo de la app bloqueada (control de sesión de medios) | F2 |
| A9 | Impedir la reproducción en segundo plano de apps bloqueadas | F2 |
| A10 | Añadir automáticamente al perfil las apps recién instaladas (opción por perfil) | F2 |
| A11 | Bloquear apps nuevas por defecto durante un periodo tras instalarlas (interruptor global "apps recién instaladas") | F3 |
| A12 | Bloquear por categoría, con una tabla de categorías de apps y webs descargable del servidor | F3 |
| A13 | Apps recomendadas al elegir qué bloquear: las 5 apps o webs que más usas, y las webs relacionadas de las apps elegidas | F2 |
| A14 | Lista de ignoradas (no cuentan en estadísticas ni se bloquean), con valores por defecto editables | F2 |
| A15 | Bloquear los diálogos de compra dentro de apps | F4 |
| A16 | Detectar pantalla dividida y bloquear la app que corresponda | F2 |
| A17 | Bloquear el administrador de tareas en segundo plano y el cambio de usuario | F4 |
| A18 | Bloquear el panel de notificaciones desplegable (opción) | F3 |
| A19 | Bloquear el menú de apagado y reinicio | F3 |
| A20 | Bloquear el modo de ahorro extremo de batería | F4 |

## B. Bloqueo de webs

| # | Función | Fase |
|---|---|---|
| B1 | Lectura de la barra de direcciones de al menos 38 navegadores, con tabla actualizable desde el servidor | F1 |
| B2 | Bloqueo por dominio (con subdominios) o por palabra clave en el dominio o en cualquier parte de la URL | F1 |
| B3 | Redirección configurable al bloquear (por defecto página en blanco). No puede apuntar a webs adultas ni a elementos de la lista de bloqueo | F2 |
| B4 | Detección de pestañas de incógnito | F2 |
| B5 | Bloquear navegadores no compatibles (los que no se pueden leer), con lista de excepciones editable | F2 |
| B6 | Lista de permitidos en webs | F2 |
| B7 | Importar webs o palabras clave desde un archivo de texto o CSV (una por línea, validadas) | F2 |
| B8 | Bloquear navegadores dentro de otras apps (vistas web) para las apps elegidas o para todas; sugerir las apps detectadas | F3 |
| B9 | Búsqueda segura forzada en Google y Bing y en YouTube | F3 |
| B10 | Reportar un bloqueo erróneo desde la pantalla de bloqueo (se envía al servidor para revisar la lista) | F4 |

## C. Contenido dentro de apps (sub-apps)

Cada uno es un interruptor independiente. Al detectarlo, la app hace "atrás". Los identificadores de las vistas se descargan del servidor, porque cambian con cada versión de esas apps.

YouTube Shorts · Reels de Instagram · Stories de Instagram · búsqueda de Instagram · Stories de Snapchat · Spotlight de Snapchat · búsqueda de Telegram · Reels de Facebook · canales de WhatsApp · estados de WhatsApp · búsqueda de imágenes y vídeo en buscadores · compras en apps de tiendas y webs de compras. Fase F3.

## D. Contenido adulto

| # | Función | Fase |
|---|---|---|
| D1 | Interruptor global; apagarlo exige aprobación (sección I) | F1 |
| D2 | Lista de dominios y de palabras del servidor, con copia local y actualización incremental, más las del usuario | F4 |
| D3 | Coincidencia por palabra sobre texto de pantalla y barra de direcciones (normalizado, ver `ESPECIFICACION.md` 5) con lista de excepciones | F4 |
| D4 | Comparación del dominio sin `www.` y también del dominio principal si es un subdominio; ignorar pestañas nuevas | F4 |
| D5 | Filtro DNS propio mediante VPN local que solo captura DNS (ver `BACKEND.md` 6); permite excluir apps | F4 |
| D6 | Reactivación automática: si se apaga el bloqueo un tiempo, se enciende solo | F4 |
| D7 | Añadir a la lista de permitidos exige teclear a mano una frase de confirmación ("confirmo que este elemento no es pornográfico") | F4 |
| D8 | El DNS propio registra consultas bloqueadas por dispositivo y alimenta el informe al compañero | F5 |

## E. Perfiles y horarios

| # | Función | Fase |
|---|---|---|
| E1 | Varios perfiles con título y emoji | F1 |
| E2 | Condición por horario (intervalos por día de la semana, pueden cruzar medianoche) | F1 |
| E3 | Condición por ubicación (geovalla con radio, y modo invertido: activo al estar fuera) | F3 |
| E4 | Condición por Wi-Fi (por nombre de red) | F3 |
| E5 | Condición por límite de tiempo de uso y por número de aperturas | F2 |
| E6 | Condición por cargador | F3 |
| E7 | Condición por Bluetooth (dispositivo conectado). No existe en la app original; se añade | F5 |
| E8 | Combinar condiciones con todas (Y) o cualquiera (O); pantalla de combinación personalizada | F2 |
| E9 | Pausar un horario un tiempo, con aviso antes de que termine la pausa | F2 |
| E10 | Cuenta atrás de aviso antes de que empiece un horario | F3 |
| E11 | Vista previa del horario: título, etiquetas, sesiones, progreso, lo que bloquea, límites usados y permitidos, historial de pausas, cuenta atrás | F3 |
| E12 | Detección de horarios en conflicto (mismos días, mismo modo, intervalos que se solapan) con pantalla explicativa | F3 |
| E13 | Duplicar horario | F2 |
| E14 | Plantillas de horario (13): vuelta al cole, viernes negro, año nuevo, renovación de primavera, vacaciones de verano, enfoque, límite de apps, tiempo para mí, en casa, estudio, desintoxicación digital, relajación nocturna, mañana consciente | F3 |
| E15 | Bloquear la edición del perfil hasta una hora | F2 |
| E16 | Edición consciente: con el modo estricto apagado, abrir un horario para editarlo exige esperar 10 segundos con un aviso | F3 |
| E17 | Hora de inicio del día configurable (afecta a los límites diarios) | F2 |
| E18 | Horario "Block Me": horario simple por día de la semana con tiempo de emergencia | F3 |

## F. Quick Block, Pomodoro y botón de pánico

| # | Función | Fase |
|---|---|---|
| F1 | Quick Block: bloquear ahora con lista rápida y una duración; modos bloquear o permitir | F2 |
| F2 | Pomodoro: 25 min de foco, 5 de descanso, 4 sesiones (configurables), notificación al cambiar de fase | F2 |
| F3 | Botón en los ajustes rápidos del sistema | F3 |
| F4 | Widgets de pantalla de inicio: Quick Block, horarios, "ayúdame" y días activos | F4 |
| F5 | Atajos del icono (pulsación larga), incluido uno para desbloquear todo con aprobación | F3 |
| F6 | **Botón de pánico ("ayúdame")**: el usuario elige un tiempo y durante ese tiempo se activa el bloqueo máximo; no se puede apagar hasta que termine. Cuenta atrás visible en la app y en el widget. Al activarlo se puede avisar al compañero | F3 |
| F7 | Tarjeta de Quick Block en el panel principal, ocultable | F2 |

## G. Límites de uso

| # | Función | Fase |
|---|---|---|
| G1 | Límite de tiempo por app o web (contador de 1 s mientras está en primer plano) | F2 |
| G2 | Límite de aperturas (cuenta si pasaron 30 s o más desde la anterior) | F2 |
| G3 | Periodo diario u horario | F2 |
| G4 | Notificación persistente con lo que queda | F2 |
| G5 | Aviso antes de que se agote el límite | F2 |
| G6 | Pantalla específica al terminar el límite | F3 |
| G7 | El límite se reparte entre todas las apps y webs del perfil o cada una tiene el suyo («Cada app aparte») | F2 |
| G8 | La notificación fija dice cuánto llevas y cuánto queda del límite más cercano (se apaga en Ajustes → Avisos) | F2 |

## H. Modo estricto y protección de la app

| # | Función | Fase |
|---|---|---|
| H1 | Estados y opciones de `ESPECIFICACION.md` sección 4 | F2 |
| H2 | Métodos de salida: temporizador, seguir horarios, cargador, PIN, espera y aprobación del compañero, y sus combinaciones | F2 |
| H3 | Ampliar el temporizador de salida con el modo estricto en marcha | F2 |
| H4 | Administrador de dispositivo para impedir la desinstalación y el bloqueo forzado | F2 |
| H5 | **Bloqueo de arranque:** tras reiniciar, mostrar una superposición hasta que los servicios estén listos, para que no haya hueco de acceso (solo en los primeros 5 minutos tras el arranque) | F4 |
| H6 | **Bloquear el reinicio del teléfono:** si el modo estricto está activo, cerrar el menú de apagado | F3 |
| H7 | **Pantalla en blanco al entrar en ajustes protegidos**, si la app tiene permiso de administrador | F2 |
| H8 | Copia y restauración de la configuración del modo estricto | F4 |
| H9 | PIN o patrón para entrar en la app; si se olvida, se envía un código de recuperación al correo | F2 |
| H10 | Cambiar el icono de la app (varios alias) para disimularla | F5 |
| H11 | Aviso al compañero al apagar la accesibilidad, al olvidar el patrón, al desinstalar y al restablecer de fábrica | F4 |
| H12 | Desbloqueo de emergencia con fricción: introducción, por qué lo bloqueaste, qué ganas, cómo te sentirías, frase de compromiso escrita a mano, espera, éxito. Cupo configurable (por defecto 3 por semana, 5 minutos cada uno) y aviso al compañero | F3 |
| H13 | Candado de un perfil, sin modo estricto: no se apaga, pausa, edita ni borra hasta cumplir los métodos de salida elegidos (espera, pedirlo y esperar, cargador, PIN, compañero) | F2 |
| H14 | Desactivar el administrador (desinstalar) con compañero vinculado lo aprueba siempre el compañero por correo; la espera de Aprobaciones, si hay, va antes del envío | F4 |
| H15 | Panel del compañero: elegir qué avisos manda el teléfono, en «Avisarle lo que pasa» y en el modo parental (apagar uno lo aprueba el compañero) | F5 |
| H16 | Panel del compañero: elegir qué protecciones del modo estricto valen con compañero sin activar el modo estricto, por modo; incluye «los cambios de perfiles los aprueba él» | F5 |

## I. Aprobaciones y compañero

Ver `ESPECIFICACION.md` secciones 7 y 8. Complementos:

| # | Función | Fase |
|---|---|---|
| I1 | Compañero real por correo (enlace de un solo uso, con página de confirmación) | F4 |
| I2 | "Ser mi propio compañero": retardo de 24 o 72 h, frase larga o pasos | F3 |
| I3 | "Ayudo a un compañero y yo también me bloqueo": relación mutua; cada uno es compañero del otro | F5 |
| I4 | El compañero confirma su correo antes de activarse; si se da de baja de los avisos hay una pantalla para reanudarlos | F4 |
| I5 | Cambiar o quitar al compañero: el compañero aprueba o rechaza | F4 |
| I6 | Aviso al compañero cuando el usuario intenta abrir algo bloqueado (en el original no existe; aquí sí) | F4 |
| I7 | Modo parental: un dispositivo "hijo" gestionado por un "padre"; si el hijo desinstala, el padre recibe aviso | F5 |
| I8 | Aprobación instantánea de pago: se omite | — |

## J. Notificaciones

| # | Función | Fase |
|---|---|---|
| J1 | Borrar y guardar las notificaciones de apps bloqueadas; pantalla para verlas | F3 |
| J2 | Ocultar 24 h el aviso del sistema "app en segundo plano" | F3 |
| J3 | Avisos configurables: apps bloqueadas, fin de límite, racha, antes de que empiece un horario, antes de que termine un límite, al terminar un horario o Quick Block, antes de que termine una pausa, estadísticas, informe semanal, Pomodoro, motivación diaria | F3 |
| J4 | Recordatorios programados propios (diarios a horas fijas) opcionales | F5 |

## K. Estadísticas e insights

| # | Función | Fase |
|---|---|---|
| K1 | Tiempo de uso, aperturas y desbloqueos de pantalla, por app y por día | F3 |
| K2 | Estadísticas de webs y de incógnito, activables por separado | F3 |
| K3 | Insights: comparación diaria y semanal, mayor racha de uso continuo y de descanso, sesiones de bloqueo | F5 |
| K4 | Informe semanal | F5 |
| K5 | Registro de estado de ánimo con aparición automática opcional | F5 |
| K6 | Insignia de tendencia diaria | F5 |
| K7 | Aviso de tiempo (*time nudge*) sin bloquear: 15 minutos de uso en una hora, espera de una hora entre avisos | F5 |

## L. Recuperación y hábitos

| # | Función | Fase |
|---|---|---|
| L1 | Rachas: días logrados, calendario, motivos de pérdida, pausa, hitos 1, 3, 7, 14, 30, 50, 100, avisos para no perderla | F5 |
| L2 | Metas de racha, reinicio y restauración (la restauración exige aprobación) | F5 |
| L3 | **Modo impulsos:** cuenta atrás ante el impulso, mensajes ("un impulso dura unos 20 minutos"), ejercicio de respiración y actividades de distracción integradas | F5 |
| L4 | Diario con "qué podría haber hecho mejor" y planificador de hasta 3 actividades por día que no se pueden editar después | F5 |
| L5 | Programa de recuperación por pasos, con identificación de disparadores | F6 |
| L6 | Test de adicción (cuestionario con puntuación de gravedad), puntuación guardada en el servidor propio | F6 |
| L7 | Informe de impulsos (cuándo, qué hora, qué desencadenó) visible para el usuario y, si lo permite, para el compañero | F6 |

## M. Pantalla de bloqueo personalizable

| # | Función | Fase |
|---|---|---|
| M1 | Colores, emoji, fondo, imagen y mensaje propios, con vista previa | F3 |
| M2 | Mensaje distinto cuando se bloquea una app concreta (por ejemplo, al intentar desinstalar) | F3 |
| M3 | **Temporizador de bloqueo:** durante una duración elegida no se puede salir de la pantalla | F4 |
| M4 | Mensajes motivadores rotativos | F3 |

## N. Cuenta, sincronización y copias

| # | Función | Fase |
|---|---|---|
| N1 | Cuenta propia con correo y contraseña (sin inicio con redes sociales) | F4 |
| N2 | Copia de seguridad en el servidor: perfiles, apps, webs, horarios, Wi-Fi, límites, Pomodoro, historial, ajustes, pantalla de bloqueo, estadísticas y modo estricto | F4 |
| N3 | Exportar e importar la configuración a un archivo local | F2 |
| N4 | Sincronizar listas (palabras, webs, apps) y interruptores entre varios dispositivos | F5 |
| N5 | Registro del dispositivo para notificaciones push | F4 |

## O. Ayuda y puesta en marcha

| # | Función | Fase |
|---|---|---|
| O1 | Asistente de permisos paso a paso: accesibilidad, uso, superposición, administrador, batería sin optimizar, inicio automático para fabricantes que lo exigen | F1 |
| O2 | Solucionador de problemas: pila de pasos con lo que falta | F3 |
| O3 | Onboarding con preguntas y prueba de bloqueo | F3 |
| O4 | Ajustes generales: idioma, tema claro y oscuro, hora de inicio del día, ajustes de bloqueo, de estadísticas, de notificaciones, de aviso de tiempo y modo desarrollador | F2 |

---

## Fuera de alcance

Quedan fuera porque no aportan al bloqueo y exigirían comunidad y moderación: monedas y canje, comunidad, publicaciones, chat entre usuarios, buscador de compañero entre desconocidos, tabla de líderes pública, cursos y academia, chatbot de soporte, sesiones grupales, suscripciones, anuncios, referidos, pruebas gratuitas, comprobación de licencia y avisos de versión pirata.

Pueden valorarse más adelante, pero ninguno es necesario para tener una versión completa de bloqueo y compañero.

## Mapa de fases

- **F1:** núcleo (apps, webs, perfiles, horarios, pantalla de bloqueo, asistente de permisos).
- **F2:** límites, Quick Block y Pomodoro, modo estricto, aprobaciones locales, estadísticas básicas.
- **F3:** condiciones avanzadas, sub-apps, plantillas, pantalla personalizable, pánico, emergencia, notificaciones.
- **F4:** servidor, compañero, contenido adulto completo, DNS, copias.
- **F5:** extras de recuperación, insights, sincronización multidispositivo, modo parental.
- **F6:** programa de recuperación, test y informe de impulsos.
