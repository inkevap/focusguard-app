# Manual de FocusGuard

Para qué sirve cada sección y cada opción de la app, con ejemplos y excepciones. Está redactado a partir de las pantallas reales; si una función cambia, este manual debe cambiar con ella (la sección **Ayuda** de la app resume lo esencial y sale de `HelpTopics.kt`).

## Índice

1. [Qué es y cómo está organizada](#1-qué-es-y-cómo-está-organizada)
2. [Inicio](#2-inicio)
3. [Perfiles](#3-perfiles)
4. [Sesiones rápidas: Bloquear ya, Enfoque, Ayúdame, Dormir](#4-sesiones-rápidas)
5. [La pantalla de bloqueo y el desbloqueo de emergencia](#5-la-pantalla-de-bloqueo-y-el-desbloqueo-de-emergencia)
6. [Estadísticas](#6-estadísticas)
7. [Protección](#7-protección)
8. [Cuenta, compañero y copias](#8-cuenta-compañero-y-copias)
9. [Bienestar y recuperación](#9-bienestar-y-recuperación)
10. [Ajustes](#10-ajustes)
11. [Asistente de bienvenida](#11-asistente-de-bienvenida)
12. [Widgets y accesos directos](#12-widgets-y-accesos-directos)
13. [Qué NO hace la app](#13-qué-no-hace-la-app)
14. [Preguntas frecuentes](#14-preguntas-frecuentes)

---

## 1. Qué es y cómo está organizada

FocusGuard bloquea las apps y las webs que tú elijas, a las horas que elijas, y te frena cuando quieres saltarte tus propias reglas. Todas las funciones están disponibles siempre: no hay planes de pago ni anuncios.

**Cuatro pestañas** abajo:

| Pestaña | Para qué |
|---|---|
| **Inicio** | Ver qué está bloqueado ahora, crear perfiles y lanzar sesiones rápidas |
| **Estadísticas** | Cuánto usas el teléfono y cuántas veces te frenó el bloqueo |
| **Protección** | Lo que evita que te saltes tus reglas: modo estricto, aprobaciones, contenido adulto, filtro DNS |
| **Ajustes** | Tu cuenta, avisos, copia de seguridad, ayuda y aspecto de la app |

Tres ideas explican casi todo:

- **Perfil**: una regla con nombre. Dice *qué* se bloquea, *cuándo* y con qué límite.
- **Aprobación**: para **aflojar** una protección (apagar un perfil, quitar una web…) tienes que pedir permiso. Para **endurecer** (añadir, crear, encender) no hace falta nada.
- **Compañero**: una persona de confianza que recibe avisos y puede aprobar o rechazar tus cambios desde su correo.

**Siempre funciona, pase lo que pase:** las llamadas, las emergencias y la propia FocusGuard nunca se bloquean.

**Lo que necesita para funcionar:** unos permisos que Android obliga a activar a mano (sección 7.9). Sin el de accesibilidad la app no puede ver qué tienes abierto.

---

## 2. Inicio

La pantalla principal. De arriba abajo:

- **Titular de estado**: cuántos perfiles te están protegiendo ahora, y si falta algún permiso (con un botón para darlo).
- **Bloquear ya** y **Enfoque**: dos accesos a sesiones rápidas (sección 4).
- **Racha**: tus días seguidos de meta lograda (sección 9.2).
- **Tengo un impulso**, **Dormir**, **Diario**, **Programa de recuperación**, **Test de adicción**: herramientas de bienestar (sección 9).
- **Ayúdame**: el botón de pánico (sección 4.3).
- **Plantillas de perfil**: perfiles listos para crear con un toque (sección 3.9).
- **Tus perfiles**: la lista, con un interruptor para encender o apagar cada uno. Tocar un perfil lo abre para editarlo; con el modo estricto activo se abre solo para verlo.
- **Nuevo perfil** (botón flotante): crea uno desde cero.

Sin perfiles, Inicio muestra una pantalla de arranque con «Crear mi primer perfil» y las opciones de bloquear ya.

**Apagar un perfil** con su interruptor cuenta como aflojar: con aprobaciones activadas crea una solicitud, y con el modo estricto no se puede.

---

## 3. Perfiles

Un perfil es un conjunto de apps y webs que se bloquean (o, al revés, las únicas permitidas), con un horario, condiciones y, si quieres, un límite de uso. Puedes tener varios a la vez: si **cualquiera** bloquea algo, se bloquea.

### 3.1 Nombre

Un nombre y un emoji para reconocerlo: «Estudio», «Noche tranquila».

### 3.2 Qué hace (el modo)

- **Bloquear esto**: se bloquea lo que pongas en el perfil.
- **Permitir solo esto**: se bloquea **todo menos** lo que pongas. Es el modo para concentrarse (estudio, noche). El teléfono, las emergencias y FocusGuard quedan fuera siempre.

> Cuidado: un perfil de «permitir solo» sin apps bloquea casi todo el teléfono mientras está activo. Pon siempre un horario.

### 3.3 Qué incluir

- **Apps**: las que tengas instaladas. El selector permite buscar y ver las elegidas.
- **Webs**: dominios (bloquea también sus subdominios) o palabras que aparezcan en la dirección. Se reconocen en los navegadores compatibles; **no en incógnito** salvo que lo permita el filtro DNS (7.4).
- **Categorías** (solo en «Bloquear»): al marcar «Redes sociales», «Vídeo», «Juegos», «Compras», «Noticias», «Citas»… se **añaden a las listas del perfil** las apps de ese tipo que tienes instaladas y sus webs, y se ve cuántas. La categoría sigue activa: las apps de ese tipo que instales más adelante también se bloquean. Para dejar pasar una, desmarca la categoría y quítala de la lista.
- **Dentro de apps**: bloquea solo una parte de una app (Shorts de YouTube, Reels y Stories de Instagram, Stories de Snapchat…). Cuando aparece, la app vuelve atrás; el resto de la app sigue funcionando.

### 3.4 Cuándo: horario

Uno o varios horarios por días de la semana, con selector de días (L M X J V S D, con atajos «Laborables», «Fines de semana», «Todos»), hora «Desde» y «Hasta» y una barra de 24 h que muestra el rango. Si «Hasta» es menor que «Desde», el horario **sigue al día siguiente** (por ejemplo, de 23:00 a 07:00). Sin horario, el perfil está activo siempre mientras esté encendido.

### 3.5 Cuándo más: condiciones

Además del horario, el perfil puede activarse solo:

- con una **red Wi-Fi** concreta;
- con un **dispositivo Bluetooth** conectado (el coche, unos auriculares);
- **mientras carga** (útil de noche);
- en un **lugar** (un círculo con radio), o al revés, **fuera** de él.

Con varias condiciones eliges si hacen falta **todas** o basta **una**. Sin el permiso de ubicación o de Bluetooth esas condiciones no se pueden leer y se dan por cumplidas.

### 3.6 Límite de uso

Con límite, el perfil **solo bloquea cuando lo agotas**: minutos de uso, número de aperturas o minutos seguidos sin descanso, por hora, por día o por semana. Eliges cómo se reparte:

- **Entre todas**: un solo contador para todas las apps y webs del perfil; lo que gastes en cualquiera cuenta para todas.
- **Cada app aparte**: cada app o web tiene su propio contador, así que agotar una no bloquea las demás.

Pasar de «Entre todas» a «Cada app aparte» da más tiempo en total, así que cuenta como aflojar y pide aprobación si la hay. Mientras usas una app con límite, una **notificación fija y silenciosa** dice cuánto llevas y cuánto te queda («Llevas 18 de 30 min · quedan 12 min»); se apaga en Ajustes → Avisos → «Cuánto te queda mientras usas una app». Si varios límites cuentan a la vez, enseña el más cercano a agotarse. El día empieza a la hora que fijes en Ajustes → Inicio del día.

### 3.7 Pausa

Deja de bloquear un rato (5, 10, 15, 30 min…) y se reanuda solo. Con aprobaciones activadas pide una aprobación; con el modo estricto no se puede. Muestra las últimas pausas.

### 3.8 Vista previa y modo estricto

La vista previa muestra, sin poder cambiar nada, cómo está configurado el perfil, si está activo ahora y cuánto falta. Es lo que ves al tocar un perfil con el modo estricto activo.

**Candado de un perfil.** En la pantalla de edición de un perfil ya creado, la tarjeta «Candado del perfil» lo protege **sin activar el modo estricto**: mientras está puesto no se puede apagar, pausar, editar ni borrar ese perfil (los demás siguen libres). Al ponerlo eliges uno o varios métodos de salida y hay que cumplirlos **todos** para quitarlo: esperar un tiempo, pedirlo y esperar (se concede tras la espera y vale 10 minutos), cargador conectado, escribir el PIN o que lo apruebe tu compañero por correo. Con el compañero entre los métodos, «Pedir quitar el candado» le manda el correo. «Seguir el horario» no se ofrece. Si el compañero se da de baja, el candado no se queda cerrado para siempre: pasadas 72 h se puede quitar. El candado no se guarda en las copias de seguridad ni se sincroniza entre teléfonos.

### 3.9 Plantillas de perfil

Inicio → **Plantillas de perfil**. Cada tarjeta dice qué incluiría **en tu teléfono** («4 apps instaladas · 6 webs · 45 min al día») y «Crear perfil» lo crea y lo abre para editarlo. Hay plantillas generales (redes, vídeo, juegos, apuestas, citas, noticias, compras, música, mensajería con horario, concentración para estudiar, descanso nocturno) y, si tu app las trae, una sección **«Para ti»**. Crear nunca afloja nada, así que no pide aprobación.

### 3.10 Qué cambios piden aprobación

Aflojar: quitar una app o web del perfil, quitar una categoría, apagarlo, borrarlo, ponerle un límite a uno que bloqueaba siempre, subir un límite, acortar o quitar un horario, aflojar condiciones, pausarlo. Pasar un límite de «Entre todas» a «Cada app aparte» también cuenta como aflojar. Quitar el candado de un perfil no pasa por aquí: se quita cumpliendo los métodos que se eligieron al ponerlo. Endurecer o añadir es libre.

---

## 4. Sesiones rápidas

Cuatro herramientas distintas. Se confunden, así que van juntas:

| | **Bloquear ya** | **Enfoque** | **Ayúdame** | **Dormir** |
|---|---|---|---|---|
| Qué hace | Bloquea lo que elijas ahora | Alterna foco (bloquea) y descanso (no) | Bloquea **todo** el teléfono | Bloquea todo **hasta la hora de despertar** |
| Para qué | «45 min sin redes» | Estudiar con técnica Pomodoro | Un impulso fuerte | Que el teléfono no te quite el sueño |
| ¿Se puede terminar antes? | Sí (con modo estricto no; con aprobaciones, pide) | Sí, igual | **No**, ni con aprobación | **No**, ni con aprobación |

### 4.1 Bloquear ya

Elige **qué** (apps y webs, en modo «bloquear esto» o «permitir solo esto») y **cuánto tiempo** con la rueda de horas y minutos (o los atajos 15 min, 30 min, 1 h, 2 h). Debajo ves a qué hora termina. No crea un perfil permanente: desaparece al acabar.

### 4.2 Enfoque (Pomodoro)

Foco, descanso y número de bloques. Atajos 25/5, 50/10 y 90/20, con «−» y «+». Resume el total («4 bloques · 1 h 55 min») y a qué hora termina. Solo bloquea durante los focos y avisa al cambiar de fase.

### 4.3 Ayúdame

El botón de pánico. Eliges un tiempo y durante todo él se bloquea **todo el teléfono** salvo llamadas, emergencias y FocusGuard. Pide confirmar («No podrás deshacerlo hasta que termine el tiempo»). **No se puede deshacer**, ni con una aprobación. Muestra la cuenta atrás en la app y en el widget, y puede avisar a tu compañero. Está pensado para un impulso, no para planificar.

### 4.4 Dormir

Bloquea el teléfono **hasta la hora a la que despiertas**, no durante un número de minutos. Eliges la hora (atajos de 6:00 a 8:00 o el selector de hora), ves cuánto falta («dentro de 8 h 12 min») y confirmas. Queda permitido el **reloj y la alarma**, para que suene al despertar, y las apps que añadas (por ejemplo, tu diario). Las llamadas, las emergencias y FocusGuard nunca se bloquean. **No se puede deshacer** hasta la hora, ni con aprobación. Para la noche; para un impulso, Ayúdame.

### 4.5 Quitar todos los bloqueos

Acceso (también un atajo de pulsación larga del icono) que pide apagar de una vez todos los perfiles encendidos. Cada uno sigue su camino: con el modo estricto no se hace nada, con aprobaciones crea **una solicitud por perfil**, y sin ellas se apagan. No toca las sesiones en curso ni Ayúdame.

---

## 5. La pantalla de bloqueo y el desbloqueo de emergencia

### 5.1 Qué ves cuando algo se bloquea

Una pantalla a pantalla completa con **qué** está bloqueado, **por qué** (qué perfil o perfiles, o qué protección) y, si es por un límite, cuándo se reinicia. Botones: volver al inicio y, si procede, **Desbloqueo de emergencia**. Si lo bloquea más de un perfil, los nombra todos.

Al bloquear, la app te manda a la pantalla de inicio (y, en un navegador, sale de la página bloqueada).

Si Android no permite la superposición, se abre una actividad propia con el mismo contenido.

### 5.2 Desbloqueo de emergencia

Para cuando de verdad necesitas algo con un bloqueo activo. Pasos: una introducción, preguntas para responder con calma («qué estás sintiendo»; lo que escribes **no se guarda ni lo lee nadie**), escribir a mano una frase de compromiso y esperar un minuto. Después, el bloqueo se levanta unos minutos y **vuelve solo**. Hay un **tope por semana** (la pantalla dice cuántos te quedan). Avisa al compañero.

No sirve para las protecciones (modo estricto, administrador): solo para bloqueos de tus perfiles.

### 5.3 Reportar un bloqueo erróneo

Desde la pantalla de bloqueo puedes avisar de que algo no debía bloquearse; se envía al servidor para revisar las listas.

---

## 6. Estadísticas

- **Uso**: tiempo medio al día, comparación con la semana anterior, gráfico de la semana, y por día: apps más usadas, webs (si las guardas), bloqueos, evitados y desbloqueos.
- **Informe semanal**: resumen de la semana: uso, mejor y peor día, racha lograda, impulsos dejados pasar y cedidos.
- Todo se calcula **en el teléfono**. Nada de esto sale a ningún servidor.
- Necesita el permiso de **acceso al uso**; sin él la pantalla lo pide.
- Las estadísticas de navegación (webs visitadas, tiempo en incógnito) se activan por separado en Ajustes y guardan solo el dominio, 35 días.

---

## 7. Protección

La pestaña de lo que evita que te saltes tus reglas.

### 7.1 Modo estricto

Mientras está activo **no puedes editar tus perfiles** ni entrar en los ajustes que apagan la app. Tú eliges:

- **Cuándo está activo**: siempre, o en un horario.
- **Qué protege**: edición de perfiles, ajustes que desactivan o desinstalan la app, apps recientes, pantalla dividida, menú de apagado, diálogos de compra, administrador de tareas y cambio de usuario, ahorro extremo de batería, panel de notificaciones.
- **Cómo se sale** (para pausarlo o desactivarlo; hacen falta **todos** los que marques): esperar un tiempo; pedirlo y esperar; seguir el horario; cargador conectado; escribir un PIN; **que tu compañero lo apruebe**.

Un modo estricto que aprueba el compañero: al pedir salir le llega un correo; cuando aprueba, **no sales solo**: confirmas cuando cumplas el resto de métodos.

No impide: arrancar en modo seguro, restablecer de fábrica. El botón «?» abre su ayuda.

### 7.2 Aprobaciones

Cómo se aprueba aflojar algo. Eliges **un** método:

| Método | Qué hay que hacer |
|---|---|
| Esperar 24 h / 72 h | Pedirlo y que pase el tiempo |
| Frase larga | Teclear a mano un texto largo (no vale pegar) |
| Pasos | Caminar un número de pasos |
| Compañero | Que lo apruebe desde su correo |

La pantalla muestra las **solicitudes pendientes** (cuándo se pidieron, cuándo caducan) y permite **cancelarlas**. Lo que aprueba tu compañero se **aplica solo**, sin pulsar nada. Una solicitud al compañero caduca a las 48 h.

**El correo del compañero** lleva el diseño de la app (con modo oscuro) y **un solo botón**, «Revisar la solicitud», que abre una página con el cambio pedido y los dos botones, «Aprobar el cambio» y «Rechazar el cambio». Abrir el enlace no decide nada: hace falta pulsar uno. Solo en la solicitud de desactivar el administrador (desinstalar), si en Aprobaciones elegiste una espera de 24 h o 72 h, esa espera va **antes** de mandarle el correo; en el resto de cambios, el método elegido sustituye al compañero.

### 7.3 Contenido adulto

**Viene activado.** Bloquea las webs de contenido adulto (incluido el manga para adultos) con las listas del servidor y las tuyas. Se actualiza solo con internet. Apagarlo pide aprobación y **se enciende solo pasado un tiempo**. Dentro:

- **Tus palabras y webs**: añadir es libre; quitar pide aprobación.
- **Dejar pasar**: para algo que se bloquea sin ser contenido adulto; hay que escribir a mano una frase de confirmación.

**Detección de imágenes.** Apagada de serie. Captura la pantalla y dos modelos en el teléfono (GantMan y NudeNet) puntúan lo explícito y lo sugerente; solo el nivel explícito, tras confirmarse, tapa la pantalla. En Ajustes, en la tarjeta de detección de imágenes:

- **Sensibilidad**, de «Mínima» a «Máxima»; de serie **«Baja»** (nivel 2). Subirla es libre; bajarla pide aprobación si hay compañero.
- **Modo dinámico**: cuantos más bloqueos explícitos haya en el periodo, más sube el nivel de alerta (1 bloqueo, nivel 1; 3, nivel 2; 6, nivel 3). Cada nivel captura un 20 % más seguido y sube la sensibilidad un paso. De serie un bloqueo **cuenta 6 h** y el nivel sube hasta el 2.
- **Vigilancia tras lo explícito** y **avisos de lo sugerente**, con sus plazos.

Los valores guardados en un teléfono no cambian al actualizar; solo los de serie.

### 7.4 Filtro DNS

Un filtro a nivel de teléfono: crea una VPN local y consulta, cifrado, a **tu** servidor FocusGuard si cada dirección está permitida. Rechaza dominios para adultos y fuerza la búsqueda segura, en **todas** las apps y navegadores, también incógnito. Solo ve nombres de dominio, nunca lo que lees. Necesita tu cuenta.

- **Consultas**: qué dominios pidió el teléfono en 30 días; solo las ves tú.
- **Apps fuera del filtro**: algunas apps no funcionan con una VPN; dejarlas fuera pide aprobación, volver a filtrarlas no.
- Android solo admite una VPN a la vez; para que no se pueda apagar sin avisar, activa «VPN siempre activa» para FocusGuard en los ajustes del sistema.

### 7.5 Apps recién instaladas

Una app nueva queda **bloqueada un tiempo** (por defecto 24 h), así no sirve para saltarte las reglas. Apagarlo o acortarlo pide aprobación.

### 7.6 Navegadores dentro de apps

Facebook, Instagram y otras abren las webs en su vista propia y se saltan el bloqueo de webs. Con esto se bloquea la app mientras enseña una. Puedes elegir todas o solo algunas (hay sugeridas).

### 7.7 Ventanas flotantes

Intenta cerrar la ventana de imagen en imagen de una app bloqueada. Depende del teléfono.

### 7.8 Contra la desinstalación

Mientras el **administrador del dispositivo** esté activo, Android no deja desinstalar FocusGuard (ni con `adb`). **Desactivarlo cuesta**: la pantalla de ajustes donde se desactiva se cierra (haya o no modo estricto), salvo que pidas permiso aquí («Pedir permiso para desactivarlo»). Con aprobaciones se crea la solicitud y, al aprobarse, se abre una ventana de **10 minutos**. Entrar en esa pantalla avisa a tu compañero. **Con un compañero vinculado, pedir ese permiso siempre lo aprueba él por correo**, aunque en Aprobaciones hayas elegido otro método; si elegiste esperar 24 h o 72 h, esa espera va antes de mandarle el correo. Sin compañero se aplica el método de siempre.

No frena: el modo seguro, el restablecimiento de fábrica.

### 7.9 Permisos y solucionador

**¿Algo no bloquea?** revisa qué falta, en tres grupos: *imprescindible*, *recomendado* y *en orden*. Los permisos, uno a uno:

| Permiso | Para qué | Qué NO hace |
|---|---|---|
| Accesibilidad | Ver qué app o web tienes abierta, para poder bloquearla | No guarda lo que ves ni lo que escribes |
| Mostrar sobre otras apps | Poner la pantalla de bloqueo encima | — |
| Acceso al uso | Contar el tiempo por app (límites y estadísticas) | — |
| Administrador del dispositivo | Impedir la desinstalación | No borra datos ni ve tus archivos |
| Sin límite de batería | Que el sistema no apague la app | — |
| Ubicación / Bluetooth | Perfiles por lugar, Wi-Fi o dispositivo | Solo si los usas |
| Acceso a notificaciones | Guardar las de apps bloqueadas | Solo si lo activas |

Ninguno lo concede la app por ti: se activan desde los ajustes de Android.

---

## 8. Cuenta, compañero y copias

Ajustes → **Cuenta y compañero**.

### 8.1 Cuenta

Correo y contraseña en **tu** servidor (sin inicio con redes sociales). Hay que **confirmar el correo** con el enlace que llega. Sin cuenta la app funciona entera, pero no hay compañero, ni copia en el servidor, ni recuperar el PIN por correo.

### 8.2 Compañero de responsabilidad

Lo invitas por correo y **tiene que aceptar** (el enlace vale 7 días). Cuando acepta, te llega una notificación. Recibe avisos (intentos de apagar la protección, bloqueos urgentes) y aprueba o rechaza tus solicitudes con un botón en su correo. Quitarlo también lo tiene que aprobar él.

- **Avisos del teléfono**: qué le llega. Se pueden apagar con aprobación.
- **Modo parental**: todos tus cambios pasan por él.
- **Cerrar sesión** con un compañero activo pide aprobación (callaría sus avisos).
- **Personas a las que ayudas**: si tú eres compañero de alguien.

### 8.3 Copia de seguridad en el servidor

Guarda tu configuración **cifrada con una frase que solo tú sabes**: el servidor guarda bytes que no puede leer. Sin la frase no se recupera; se avisa antes de guardar. **Restaurar solo añade perfiles** y nunca quita ni afloja una protección que ya tengas. Se puede borrar del servidor.

### 8.4 Sincronizar varios dispositivos

Con la misma frase en todos, se sincronizan listas e interruptores cada pocas horas, aplicando solo lo que endurece.

### 8.5 Copia a un archivo

En Ajustes: guarda perfiles y ajustes en un archivo. **No incluye** el modo estricto, el PIN ni los lugares (Wi-Fi y ubicación). Al importar solo se añaden perfiles nuevos.

### 8.6 Servidor

La dirección de tu servidor FocusGuard. Cambiarla pide aprobación (de él salen las listas de bloqueo).

---

## 9. Bienestar y recuperación

Herramientas de apoyo; **no bloquean nada** salvo donde se dice. Lo que escribes en ellas **no sale del teléfono**.

### 9.1 Impulso («Tengo un impulso»)

Una cuenta atrás con respiración guiada (inspira 4 s, sostén 4, espira 6) y cosas que hacer mientras pasa. Al final apuntas si lo **dejaste pasar** o **cediste**, y, si quieres, qué lo desencadenó. «Si cedes, no pasa nada: se apunta sin juicio.» Solo se guarda cuándo empieza y cómo acaba.

### 9.2 Racha

Días seguidos de **meta lograda**: un día se logra con al menos cierto tiempo de bloqueo activo (la pantalla dice cuántas horas) y se pierde con una pausa de más de 15 minutos o con más de 3 pausas. Muestra la racha actual y la mejor, un calendario (lleno = logrado, claro = perdido, aro = hoy) y por qué se perdió un día. Puedes cambiar la meta, **reiniciarla** (la mejor se conserva) y **restaurar** un día perdido (pide aprobación).

### 9.3 Diario

Cada día: cómo te sientes, «¿qué podría haber hecho mejor?» (editable hoy, mañana queda fijo) y hasta **3 actividades** que vas a hacer en vez de usar el teléfono (una vez planificadas no se cambian, solo se marcan hechas). Ni a la copia ni al servidor ni al compañero.

### 9.4 Programa de recuperación

Siete pasos, uno por día, para reconocer qué te lleva a abrir el teléfono y qué haces entonces. Se puede empezar de nuevo (borra tus respuestas).

### 9.5 Test de adicción

Diez preguntas con puntuación, para compararte contigo mismo con el tiempo. **No es un diagnóstico.** Guardas la puntuación en el teléfono; si quieres, solo puntuación y fecha en tu cuenta (las respuestas y el compañero no).

### 9.6 Informe de impulsos

Cuántos impulsos, a qué hora llegan y qué los desencadena. Si lo permites, tu compañero recibe un resumen semanal (total, hora y motivo).

### 9.7 Hábitos y recordatorios

Ajustes → Hábitos. Son **avisos, no bloqueos**:

- **Aviso de tiempo**: una notificación cuando llevas un rato seguido con el teléfono.
- **Recordatorios**: a la misma hora cada día (beber agua, caminar, llamar a alguien). Hasta un máximo.
- **Pregunta de ánimo**: una vez al día, a la hora que elijas.

---

## 10. Ajustes

| Opción | Para qué |
|---|---|
| **Cuenta y compañero** | Sección 8 |
| **Ayuda** | Fichas con buscador, ejemplos y excepciones, y botón para abrir cada pantalla |
| **Repetir el asistente de bienvenida** | Vuelve al asistente. Solo crea un perfil; no afloja nada |
| **Hábitos y recordatorios** | Sección 9.7 |
| **Pantalla de bloqueo** | Mensaje propio, mensaje distinto por app, frases motivadoras rotativas, frase cuando intentan salir de FocusGuard, color y emoji |
| **Icono de la app** | Cambia el icono y el nombre (por ejemplo, «Notas» o «Calculadora») para pasar desapercibida. Pide aprobación |
| **Inicio del día** | Hora a la que se reinician los límites diarios (por ejemplo 04:00 si duermes de madrugada) |
| **Avisos** | Antes de que empiece un horario, antes de que se acabe un límite, cuánto te queda mientras usas una app con límite (notificación fija), cuando termina un horario, cuando una pausa va a terminar, una frase cada día, resumen de la semana, tu racha |
| **Notificaciones guardadas** | Las notificaciones de apps bloqueadas se guardan 7 días en el teléfono para verlas después. Hace falta dar acceso a las notificaciones |
| **Estadísticas de navegación** | Guardar webs visitadas (solo el dominio, 35 días) y contar el tiempo en incógnito |
| **Copia a un archivo** | Sección 8.5 |
| **PIN de la app** | Un PIN que se pide al abrir FocusGuard, y otra vez cada vez que sales de ella |

---

## 11. Asistente de bienvenida

Nueve pasos la primera vez (y repetible desde Ajustes). Se puede volver atrás y saltar.

1. **Bienvenida**: qué hace, qué no y tu privacidad.
2. **Objetivo**: reducir redes, estudiar o trabajar, dormir, dejar el contenido adulto o un poco de todo. Rellena de antemano los pasos siguientes.
3. **Qué cuidar**: categorías; se añaden al perfil tus apps instaladas y sus webs.
4. **Cuándo**: siempre, horas de trabajo, tardes o noche.
5. **Contenido adulto**: ya activado; solo informa.
6. **Compañero** (opcional): solo informa; lo configuras después.
7. **Modo estricto y emergencia**: explicados; se recomienda activar el estricto tras la primera semana.
8. **Permisos**: uno por uno, con comprobación al volver.
9. **Prueba**: ves la pantalla de bloqueo de ejemplo.

Al terminar queda creado **«Mi primer perfil»**. El asistente nunca apaga ni afloja nada.

---

## 12. Widgets y accesos directos

- **Widgets** de la pantalla de inicio: estado de los perfiles y la racha, bloquear ya, y **Ayúdame** con su cuenta atrás.
- **Pulsación larga del icono**: cuatro atajos: Ayúdame, Bloquear ya, Quitar bloqueos e Impulso. Solo abren la pantalla: no cambian nada hasta que confirmas dentro de la app.
- **Ajustes rápidos** del sistema (el panel que bajas desde arriba): un botón de **Ayúdame**.

---

## 13. Qué NO hace la app

- No bloquea llamadas ni emergencias.
- No lee lo que escribes ni guarda lo que ves en pantalla. Tu uso y tus estadísticas se quedan en el teléfono.
- No es imposible de quitar: **modo seguro** y **restablecimiento de fábrica** siempre pueden con ella, y el administrador del dispositivo solo frena el camino normal.
- No es un tratamiento ni un diagnóstico: el test y el programa de recuperación son apoyo.
- No funciona sin los permisos de Android, que solo puedes dar tú.
- Los navegadores no compatibles no se pueden leer; el filtro DNS cubre su tráfico, pero no por nombre de web dentro de la página.

---

## 14. Preguntas frecuentes

**¿Cuál es la diferencia entre Bloquear ya y Ayúdame?** Bloquear ya bloquea lo que **tú eliges** y puedes terminarlo (salvo modo estricto o aprobaciones). Ayúdame bloquea **todo** y **no se puede deshacer**.

**¿Y entre Dormir y Ayúdame?** Los dos son irreversibles. Ayúdame es para un impulso: unos minutos y no permite ninguna app. Dormir es para la noche: dura hasta la hora de despertar y deja la alarma y las apps que elijas.

**Quito una app de un perfil y sigue bloqueada.** Si la categoría sigue marcada, la bloquea. Desmarca la categoría y quítala de la lista.

**Pedí algo y mi compañero ya lo aprobó, pero no cambió nada.** Debería aplicarse solo en cuanto llegue el aviso. Abre Aprobaciones para forzar la sincronización; si el modo estricto está activo, no se aplica.

**Quiero desactivar el modo estricto.** Protección → Modo estricto → «Desactivar» y cumple los métodos que elegiste. Si incluyen a tu compañero, espera su aprobación y después confirma.

**No me deja desactivar el administrador.** Es a propósito. Protección → «Contra la desinstalación» → «Pedir permiso para desactivarlo».

**El bloqueo no salta.** Protección → «¿Algo no bloquea?». Lo más habitual: falta el permiso de accesibilidad o el sistema apagó la app por ahorro de batería.

**Cambié de teléfono.** Instala FocusGuard, inicia sesión y restaura la copia con tu frase. Sin la frase no hay forma de abrirla.

**Un bloqueo es un error.** Repórtalo desde la pantalla de bloqueo o añade la excepción en Contenido adulto → «Dejar pasar».
