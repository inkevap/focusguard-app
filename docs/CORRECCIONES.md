# Correcciones y mejoras pendientes tras la sesión de pruebas

Cosas que se encontraron o se piden al probar FocusGuard 0.2.0 en un teléfono real (Motorola edge 40 neo, Android 15) contra el servidor desplegado (2026-10-28). **No se corrige nada hasta que termine la sesión de pruebas**; entonces se hace cada punto en su rama (`claude/<tema>`), con pruebas, `/simplify`, `/code-review` y, donde toque, `/security-review`.

Estado de cada punto: `pendiente` (sin tocar). Los hallazgos nuevos de la sesión de pruebas se añaden al final.

---

## Estado de cada punto (2026-10-28, actualizado al cerrar los puntos 14 a 18)

Las ramas están apiladas: se fusionan en orden, de #7 a #19. «Hecho» significa implementado con pruebas automáticas; **ninguna se ha probado todavía en el teléfono** (instalar `focusguard-0.5.0.apk`, que incluye todas y las plantillas personales; no se comparte) y `/simplify`, `/code-review` y `/security-review` están sin pasar.

| Punto | Estado | Dónde |
|---|---|---|
| 1. La ventana de bloqueo no se cierra sola | Hecho | PR #7 `claude/pantalla-bloqueo` |
| 2. Correos y páginas con el estilo de la app, con botones | Hecho (paleta única `data/theme/tokens.json`; **0.6.0**: el HTML no llegaba por la cola de envío y se corrigió, y el correo de solicitud lleva un solo botón a la página con aprobar y rechazar; falta ver en clientes de correo reales) | PR #11 `claude/correos-y-paginas` |
| 3. Notificación al aceptar el compañero | Hecho | PR #8 `claude/notificaciones` |
| 4. Aplicar solo lo aprobado | Hecho; **requiere `/security-review`** | PR #10 `claude/aplicar-aprobado` |
| 5. Ver y cancelar las pendientes con claridad | Hecho | PR #13 `claude/aprobaciones-visibles` |
| 6. Qué perfiles bloquean | Hecho | PR #7 |
| 7. Bucle del navegador al pulsar «Volver» | Hecho (sin probar en navegadores reales) | PR #7 |
| 8. Contenido adulto por defecto | Hecho | PR #9 `claude/adulto-por-defecto` |
| 9. Selectores modernos de tiempo | **Casi hecho**: deslizador, «− +», días en círculos, selector de hora, barra de 24 h y Pomodoro con presets; falta que la barra se arrastre, «Duplicar de otro día» y el campo del límite de uso | PR #16 `claude/selectores-de-tiempo` |
| 10. Modo estricto desactivable con el compañero | Hecho; **requiere `/security-review`** (única solicitud que se aplica con el modo estricto activo) | PR #14 `claude/modo-estricto-companero` |
| 11. Asistente de primera vez más completo | **Casi hecho**: 9 pasos, atrás, saltar y repetir desde Ajustes; los pasos de compañero y modo estricto solo informan y la prueba no abre una app real | PR #17 `claude/asistente-y-ayuda` |
| 12. Sección de Ayuda | **Casi hecho**: 20 fichas con buscador, «?» en dos pantallas y la ficha Temporizador vs Ayúdame; faltan fichas menores y que se generen de una fuente común con el catálogo | PR #17 |
| 13. Perfil por categorías rellena apps y webs | **Casi hecho**: se añaden las apps instaladas y las webs y la categoría sigue activa; faltan las exclusiones a mano y que las instaladas después aparezcan en la lista | PR #15 `claude/categorias-rellenan-perfil` |
| 14–16. Plantillas (genéricas y personales) por defecto | **Casi hecho**: 11 genéricas en el repo y personales en `app/personal/plantillas.json` (fuera de git), creables desde Inicio; faltan «Sugerencias a partir de tu uso» y cruzar los nombres de paquete personales con el teléfono | PR #18 `claude/plantillas` |
| 17. Manga dentro de contenido adulto | Hecho (4 dominios comprobados) | PR #9 |
| 18. Anti-desinstalación | **Parcial**: administrador imprescindible (#12) y su pantalla de desactivar se cierra salvo con permiso aprobado (#19); **0.6.0**: con compañero vinculado, pedir ese permiso siempre lo aprueba él por correo, con la espera de Aprobaciones antes del envío si la hay; los avisos al compañero ya existían; faltan las barreras de Play Store e instaladores de terceros y reinstalar recuperando la protección | PR #12 `claude/anti-desinstalacion`, PR #19 `claude/anti-desinstalacion-2` |
| Hallazgo K1: Estadísticas sin bloqueos | Hecho por deducción del código; **por confirmar en el teléfono** | PR #7 |

## 1. La ventana de bloqueo no debe cerrarse por su cuenta

**Qué se ve.** Al abrir una app bloqueada (Instagram) sale «Bloqueado… Instagram está bloqueada ahora mismo por uno de tus perfiles», con «Volver» y «Desbloqueo de emergencia». A los pocos segundos la ventana desaparece sola y queda la pantalla de inicio.

**Causa (en el código).** `FocusAccessibilityService`:
- `OVERLAY_TIMEOUT_MS = 3_000L` (línea ~846): al mostrar la superposición se programa `removeOverlayRunnable`, que la quita a los 3 s.
- Además `block()` pulsa `GLOBAL_ACTION_HOME` en cuanto bloquea, y en navegadores repite «Atrás» y vuelve al inicio.

**Qué se quiere.** La ventana se queda hasta que el usuario pulse «Volver» (o el desbloqueo de emergencia), sin temporizador.

**Propuesta.**
- Quitar el cierre por temporizador, o dejarlo solo como seguro cuando la app bloqueada ya no está en primer plano (para no dejar una superposición huérfana si el servicio pierde el evento).
- Revisar que la superposición no tape ni bloquee el sistema (barra de estado, llamadas): ya es `TYPE_ACCESSIBILITY_OVERLAY`, que lo permite.
- Comprobar en `ESPECIFICACION.md` y `FUNCIONES_COMPLETAS.md` si los 3 s son una decisión de diseño; si lo son, anotar el cambio en `DECISIONES.md`.

**Cómo comprobarlo.** Abrir una app bloqueada con `adb shell monkey -p <paquete> -c android.intent.category.LAUNCHER 1`, esperar 30 s y comprobar con captura y `dumpsys window | grep mCurrentFocus` que la ventana sigue. Probar también rotar la pantalla, bloquear/desbloquear el teléfono y el botón de multitarea.

---

## 2. Correos y páginas del servidor con el tema y la paleta de la app, y que sigan a la app

**Qué se ve.** Los correos son solo texto (`server/src/mails.ts`) y las páginas de aceptar, confirmar y restablecer son HTML mínimo con una hoja de estilo de una línea (`page()` en `server/src/http.ts`): fuente del sistema, sin colores ni marca.

**Qué se quiere.**
0. **En los correos de aprobar o rechazar (y en la invitación al compañero, la confirmación de correo y el restablecimiento) los enlaces se ven como botones**, no como una URL suelta: «Aprobar» y «Rechazar» son botones en el propio correo que llevan a la página, y esa página también tiene el estilo de la app. (Hoy `requestMail` solo pone `Aprobar: <url>` y `Rechazar: <url>` en texto.)
1. Correos y páginas con el mismo tema que la app: paleta «Guardia» (verde azulado `#0B6E6A`, ámbar de brasa para avisos, coral para peligro), claro y oscuro, tipografía y esquinas redondeadas de la app.
2. Que **si cambia el estilo o la paleta de la app, cambie también en el correo y la página**, sin tener que acordarse de tocar dos sitios.

**Fuente de verdad hoy.** `app/src/main/java/com/focusguard/ui/theme/Theme.kt`: dos `ColorScheme` de Material 3 (`Light`, `Dark`), más `BrandColors` (degradado de marca), formas y tipografía.

**Propuesta de diseño.**
- Un único fichero de tokens de diseño en el repositorio, por ejemplo `data/theme/tokens.json`, con los colores claro y oscuro (`primary`, `onPrimary`, `background`, `surface`, `error`, `tertiary`…), el degradado de marca, el radio de las esquinas y la pila tipográfica.
- **La app** lo lee en tiempo de compilación: una tarea de Gradle genera `ThemeTokens.kt` y `Theme.kt` deja de repetir valores a mano.
- **El servidor** lo lee al compilar (el `Dockerfile` ya copia `data/seed`): un módulo `theme.ts` genera la hoja de estilo de las páginas y los estilos en línea de los correos a partir del mismo JSON.
- **Prueba de coherencia** en ambos lados que falle si los colores de `Theme.kt` y los del JSON difieren, para que nadie los retoque por separado.
- Las páginas usan `prefers-color-scheme` para claro/oscuro.
- Los correos son HTML con estilos **en línea** y tablas (los clientes de correo no admiten variables CSS ni hojas externas), con **versión de texto** como alternativa (Resend envía `html` y `text`), logo como imagen alojada en el propio servidor o en línea (SVG no es fiable en correo), y contraste mínimo AA. Oscuro en correo solo donde el cliente lo respete.
- Una sola plantilla de maquetación compartida entre correos (cabecera de marca, tarjeta, botón principal, pie con el enlace a preferencias) y páginas, para que un cambio de estilo toque un solo sitio.

**A tener en cuenta.**
- Hoy `Mail` solo tiene `text`; hay que añadir `html` en `server/src/mailer.ts` (nodemailer lo admite) y en las pruebas (`MemoryMailer`).
- Los botones apuntan a las mismas URL de hoy (`/r/:token/approve`, `/r/:token/reject`, `/partners/confirm`, `/verify`, `/reset`): no cambiar rutas ni tokens. Cada botón debe ser un `<a>` con estilo de botón (relleno con `bgcolor` en la celda de la tabla, para que lo respete Outlook) y, debajo, el mismo enlace en texto por si el cliente no pinta botones; "Aprobar" en el color primario y "Rechazar" en un estilo secundario o de peligro, con espacio entre ellos para no pulsar el equivocado.
- Páginas afectadas: confirmar correo (`/verify`), restablecer contraseña (`/reset`), aceptar compañero (`/partners/confirm`), aprobar/rechazar (`/r/:token/...`), preferencias de avisos, enlace no válido y las confirmaciones.
- Documentar la decisión en `DECISIONES.md` («los tokens de diseño viven en `data/theme/tokens.json`») y mencionarlo en `CLAUDE.md`, para que cualquier cambio de paleta pase por ahí.
- Diseño visual: usar las skills de diseño del repositorio y, si hace falta, una maqueta antes de implementar.

**Cómo comprobarlo.** Enviar los correos reales de prueba a una bandeja y abrir las páginas en el teléfono, en claro y en oscuro; cambiar un color en `tokens.json` y comprobar que cambia en la app, en el correo y en la página tras recompilar.

---

## 3. No llega ninguna notificación al teléfono cuando el compañero acepta

**Qué se ve.** Al aceptar el compañero la invitación (22:32:34 UTC, `POST /partners/confirm`), el teléfono no mostró ninguna notificación.

**Lo que sí funciona (comprobado y confirmado en el teléfono).** Cuando el compañero aprueba una solicitud llega la notificación casi al instante (aprobada a las 22:43:50; el teléfono pidió `GET /requests/<id>` en ese mismo segundo). El aviso por FCM llega: un segundo después (22:32:35) el teléfono pidió `GET /partners` al servidor, con la app en segundo plano. Lo mismo con los avisos de prueba enviados a mano desde el servidor.

**Causa (en el código).** `PushHandler` solo muestra notificación para `request_resolved` y solo cuando una solicitud pasa a aprobada o rechazada. Para `partner_confirmed` solo llama a `refreshPartner()`: actualiza el estado en silencio. Es lo que se diseñó en `docs/DECISIONES.md` (F4 push), pero no es lo que se espera.

**Qué se quiere.** Una notificación visible cuando el compañero acepta («Tu compañero aceptó la invitación»).

**Propuesta.**
- `PushHandler`: añadir un cuarto parámetro `notifyPartnerConfirmed` y llamarlo tras `refreshPartner()` **solo si** el estado pasó de «invitado» a «activo» (como `syncReportingResolved`, para no avisar dos veces ni por avisos repetidos).
- `Notifier`: nueva notificación en el canal «Respuestas de tu compañero» (o uno propio), con título/texto en `strings.xml` y apertura de «Cuenta y compañero».
- Pruebas en `PushHandlerTest` y en la capa de `PartnerRepository` (detectar la transición).
- Revisar el resto de avisos del servidor que hoy no tienen notificación visible (rechazos, caducidad, compañero que se va) y decidir cuáles deben tenerla; apuntar la lista en `DECISIONES.md`.

**Cómo comprobarlo.** Invitar a un compañero con la app en segundo plano y aceptar desde su correo; comprobar con captura que aparece la notificación y que al tocarla abre la pantalla correcta.

---

## 4. Una solicitud aprobada debe aplicarse sola, al momento

**Qué se ve.** Tras aprobar el compañero («Apagar el filtro DNS»), la solicitud queda en «Solicitudes pendientes» con un botón **«Aplicar»** y no se hace nada hasta que el usuario lo pulsa (así pasó: aprobada a las 22:43:50 y consumida a mano a las 22:46:22).

**Qué se quiere.** En cuanto el compañero aprueba, el cambio se aplica inmediatamente, sin lista de espera ni botón, y con la app abierta o cerrada.

**Causa (en el código).** `PartnerApprovals.sync()` solo actualiza el estado a `APPROVED`; quien consume la aprobación (`consume`) y aplica el cambio (`ApprovalExecutor.apply`) es `ApprovalsViewModel`, es decir, solo ocurre desde la pantalla de Aprobaciones y al pulsar «Aplicar».

**Propuesta.**
- Tras `syncReportingResolved()` (en `PushHandler` y en el envío periódico), recorrer las solicitudes aprobadas y, para cada una: **primero `consume` en el servidor** (una aprobación vale una sola vez; si falla, no se aplica) y **después `ApprovalExecutor.apply`**, y quitarla de la lista. Es la misma secuencia que hoy hace el ViewModel; hay que moverla a una clase sin pantalla (por ejemplo `ApprovalApplier` en `data/`) y que el ViewModel la reutilice.
- Cuidado con la concurrencia: dos avisos seguidos, o el aviso y el ViewModel a la vez, no deben aplicar dos veces (un `Mutex` por solicitud y que el `consume` del servidor sea la puerta: solo quien lo consiga aplica).
- Qué pasa si aplicar falla tras consumir (p. ej. el cambio ya no es válido): la solicitud se descarta con una notificación que lo diga, no se reintenta.
- Notificación al usuario: «Tu compañero aprobó: <cambio>. Ya está aplicado» (en lugar de «abre FocusGuard para ver tu solicitud»); si se rechaza, el texto actual.
- Seguridad: aplicar desde segundo plano solo las acciones que la propia solicitud describe (`action_type`), sin confiar en el contenido del aviso; el servidor sigue siendo quien dice si está aprobada y no consumida. Revisar con `/security-review`: es el punto que decide si un cambio de protección ocurre sin que el usuario lo vea.
- Con el modo «Que lo apruebe mi compañero», la pantalla de Aprobaciones deja de necesitar el botón «Aplicar» (puede quedarse como respaldo si el cambio no pudo aplicarse sin conexión).

**Cómo comprobarlo.** Crear una solicitud, aprobarla desde el correo con la app en segundo plano y comprobar, sin abrir la app, que el filtro se apaga solo y que llega la notificación; repetir con la app cerrada, con el teléfono bloqueado y con dos aprobaciones seguidas.

---

## 5. Ver y cancelar las solicitudes pendientes de respuesta del compañero

**Qué se pide.** En Aprobaciones se deben ver con claridad las solicitudes que esperan respuesta del compañero y poder cancelarlas cuando haga falta.

**Qué hay hoy (visto en el teléfono).** La lista ya existe, pero escondida: está en *Protección → Aprobaciones → «Configurar»*, al final de la pantalla «Cómo se aprueba apagar algo», bajo «Solicitudes pendientes». Cada solicitud muestra su descripción («Apagar el filtro DNS (deja de aplicarse a todo el teléfono)»), «Esperando a tu compañero. Le ha llegado un correo; caduca a las 48 horas.» y dos botones, «Aplicar» (desactivado mientras espera) y **«Retirar»** (`ApprovalsScreen.kt`: «Retirar» si espera al compañero, «Cancelar» en los demás modos). `PartnerApprovals.cancel` avisa al servidor (`POST /requests/:id/cancel`) para que los enlaces del correo dejen de valer.

**Qué falta.**
- **Descubribilidad.** Quien no entra en «Configurar» no ve nada. Que la tarjeta «Aprobaciones» de Protección y/o Inicio muestre «1 solicitud esperando a tu compañero» y lleve directamente a la lista.
- **Claridad.** Para cada solicitud: qué se pidió, a quién, desde cuándo (hora), cuándo caduca, y su estado en palabras (esperando, aprobada, rechazada, caducada).
- **Cancelar sin dudas.** El botón «Retirar» pasa a «Cancelar solicitud», con una confirmación breve, y el resultado se ve (desaparece de la lista y el compañero ya no puede aprobarla; si el correo ya salió, el enlace da «solicitud cancelada»).
- **Cancelar sin conexión.** Hoy, si falla la llamada al servidor, la solicitud local se quita pero el enlace del correo puede seguir vivo hasta que caduque: reintentar la cancelación en el siguiente envío y mostrarlo (o no quitarla hasta que el servidor confirme).
- Con el punto 4 (aplicar solo lo aprobado) el botón «Aplicar» desaparece de las solicitudes del compañero; la lista queda para ver y cancelar las que esperan, y las ya resueltas pasan a un historial corto.

**Cómo comprobarlo.** Crear una solicitud, comprobar que se ve sin entrar en «Configurar», cancelarla y comprobar en el servidor que su estado es `cancelada` y que el enlace del correo ya no aprueba (`GET /r/:token/approve` no la deja aprobar).

---

## 6. La pantalla de bloqueo debe decir qué perfil o perfiles bloquean

**Qué se ve.** Al abrir una app bloqueada sale «Instagram está bloqueada ahora mismo por uno de tus perfiles», sin decir cuál.

**Qué se quiere.** Cuando algo se bloquea, mostrar **qué perfil o perfiles** lo están bloqueando (título y emoji del perfil), por ejemplo «Bloqueada por: 📵 Trabajo, 🌙 De noche». También en el aviso al compañero y en el registro de bloqueos, si aplica.

**Dónde mirar.** `BlockDecision.Block` (`core-decision`) ya lleva `profileId` y el motivo (`BlockReason.PROFILE_APP`); el texto genérico sale de `lock_reason_*` en `strings.xml` y lo compone `LockScreenContent`. Hoy solo viaja un `profileId`: si varios perfiles bloquean a la vez, hay que decidir si `decide()` devuelve el primero o la lista completa.

**Propuesta.**
- `core-decision`: que `Block` lleve la lista de perfiles que coinciden (ordenada de forma estable), con pruebas unitarias para uno, varios y ninguno (bloqueos que no vienen de un perfil: límite global, contenido adulto, apps recién instaladas, modo estricto, Quick Block).
- `LockScreenContent`: un texto con los títulos (y emojis), que se acorte con «y 2 más» si son muchos, y una línea distinta cuando el motivo no es un perfil.
- Privacidad: el nombre del perfil se queda en el teléfono; al compañero solo va lo que ya va hoy (ver `docs/DECISIONES.md`).
- Capturas Paparazzi nuevas de la pantalla de bloqueo con uno y con varios perfiles.

**Cómo comprobarlo.** Con dos perfiles que bloqueen la misma app (uno por horario y otro por lista), abrir la app y comprobar que salen los dos; con un solo perfil, que sale uno; con el bloqueo por contenido adulto, que no sale ningún perfil sino su motivo.

---

## 7. El navegador entra en bucle al pulsar «Volver» en una pantalla de bloqueo

**Qué pasa.** Con una web bloqueada (contenido adulto o una web de un perfil), «Volver» deja al usuario otra vez en el navegador con la misma página; la app la vuelve a bloquear y así sin fin: **no se puede usar el navegador**.

**Causa probable.** «Volver» solo cierra la ventana de bloqueo (`LockScreenView.onDismiss`); la pestaña del navegador sigue en la URL bloqueada y se vuelve a leer en cuanto el navegador recupera el foco. `closeBrowserTab` ya intenta «Atrás» tres veces y, si sigue bloqueada, «Inicio», pero no corre cuando el usuario pulsa «Volver» ni cuando la pantalla de bloqueo ya está abierta.

**Qué se quiere.** Al pulsar «Volver» en un bloqueo de web, la página bloqueada **desaparece**: se vuelve a la página anterior (atrás) y, si no hay una permitida, se cierra la pestaña o se abre una en blanco (`about:blank`, que es lo que describe B3 «Redirección al bloquear, por defecto página en blanco»). Nunca debe quedar la URL bloqueada en pantalla.

**Propuesta.**
- Al descartar la pantalla de bloqueo de una web: `performGlobalAction(GLOBAL_ACTION_BACK)` hasta que la URL leída ya no esté bloqueada (máximo 3–4 veces, con espera corta entre cada una); si sigue bloqueada, escribir `about:blank` en la barra de direcciones (`ACTION_SET_TEXT` e Intro) o cerrar la pestaña con el botón de pestañas; si tampoco sirve, llevar a inicio y dejar un aviso.
- Que la redirección sea configurable (B3) con valor por defecto en blanco.
- No volver a bloquear la misma URL en bucle: enfriamiento de unos segundos tras «Volver» y un contador; con más de N bloqueos seguidos de la misma página, cerrar la pestaña.
- Probar con varios navegadores (Brave, Chrome, Firefox, Edge, Samsung Internet) y con pestañas de incógnito.

**Cómo comprobarlo.** Abrir `007dating.com` en Brave con el contenido adulto activado, pulsar «Volver» y comprobar que se llega a una página permitida y que el navegador sigue usable, sin bucle, y que no se acumulan pestañas.

---

## 8. El contenido adulto debe venir bloqueado por defecto

**Qué se pide.** Que el bloqueo de contenido adulto (D1) esté **activado** desde la primera vez.

**Qué hay hoy.** El interruptor de «Contenido adulto» en *Protección* viene apagado; con él apagado el navegador no muestra bloqueo (solo actúa el filtro DNS).

**Propuesta.**
- `SettingsRepository.adultBlockEnabled` con valor inicial `true` (y migración para quien ya tenía el valor guardado: no tocar lo que el usuario ya eligió).
- Apagarlo sigue pidiendo aprobación y se reactiva solo pasado un día (D6, ya existente).
- El asistente de primera vez (punto 11) lo explica y no ofrece apagarlo en el primer paso.
- Prueba unitaria del valor por defecto y de que una instalación existente conserva su elección; documentar en `DECISIONES.md`.

---

## 9. Elegir horarios y tiempos de forma intuitiva y moderna (sin teclear números)

**Qué se pide.** La forma de definir un horario o un tiempo de espera debe ser más intuitiva y moderna, no solo escribir el número a mano.

**Qué hay hoy (visto en el teléfono).** Quick Block, Pomodoro y Ayúdame piden «Cuánto tiempo» con chips fijos (15 min, 30 min, 1 h, 2 h) y un campo «Otro valor, en minutos» que se escribe a mano; el Pomodoro pide tres números a mano; los horarios de los perfiles se definen por intervalos escritos.

**Propuesta.**
- **Duraciones:** un control deslizante con marcas y una vista previa «Hasta las 18:42», chips que se puedan combinar con pasos de ±5 min (`− 5 +`), y un selector de rueda (horas/minutos) para valores raros; todo accesible con teclado y TalkBack.
- **Horarios:** selector de días en forma de círculos (L M X J V S D), con atajos «Laborables», «Fines de semana», «Todos»; rango de horas con el selector de tiempo de Material 3 (`TimePicker`/`TimeInput`) y una barra de 24 h arrastrable que muestre el rango y avise si cruza la medianoche; varias franjas por día con «Duplicar de otro día».
- **Pomodoro:** presets («25/5», «50/10», «90/20») más los tres valores con selectores, y un resumen «4 bloques · 2 h 0 min en total».
- Formato legible en toda la app («2 h 30 min», «hasta las 18:42») y texto de ayuda con ejemplos.
- Maquetas antes de implementar (usar las skills de diseño del repo) y capturas Paparazzi nuevas.

---

## 10. El modo estricto se debe poder desactivar con el compañero

**Qué se pide.** Poder desactivar el modo estricto con la aprobación del compañero (igual que otros cambios que debilitan una protección).

**Qué hay hoy.** `FUNCIONES_COMPLETAS.md` H2 lista entre los métodos de salida «espera y aprobación del compañero»; en la pantalla de *Modo estricto* del teléfono no se vio esa opción (solo «Activar modo estricto» y lo que protege). Hay que comprobar si falta en la interfaz o en la lógica.

**Propuesta.**
- Al activar el modo estricto, el usuario elige su método de salida; con compañero activo se ofrece «Que lo apruebe mi compañero» (correo con botones, caduca a las 48 h) además de temporizador, horarios, cargador y PIN.
- Para desactivarlo: crear una solicitud (`action_type` nueva) y, cuando el compañero la apruebe, aplicarlo solo (punto 4).
- Si se pierde el compañero (se da de baja), definir qué pasa: no debe quedar el modo estricto sin salida; avisar y exigir otro método.
- Documentar en `DECISIONES.md`; pruebas del servidor y de `core-decision`.

---

## 11. Asistente de primera vez (setup wizard) más completo

**Qué hay hoy.** Un asistente de 5 pasos: bienvenida, «¿Qué quieres cuidar?» (redes, vídeo, juegos, compras, noticias), «¿Cuándo?» (siempre, trabajo, tardes, noche), permisos y fin. Crea un único perfil «Mi primer perfil».

**Qué se pide.** Un asistente que ayude a elegir las opciones que mejor se ajusten.

**Propuesta de pasos.**
1. Bienvenida y qué hace y qué no hace la app (privacidad: el texto de pantalla se queda en el teléfono).
2. Objetivo («reducir redes», «dejar el contenido adulto», «estudiar o trabajar mejor», «dormir mejor»).
3. Qué cuidar, con las plantillas del punto 15 ya rellenas con las apps y webs instaladas, que el usuario marca o desmarca.
4. Cuándo y cuánto, con los selectores del punto 9.
5. Contenido adulto: **activado** (punto 8), con una línea que explique el filtro DNS y por qué conviene.
6. Compañero de responsabilidad (opcional): crear cuenta, invitar y elegir cómo se aprueba apagar algo.
7. Modo estricto y desbloqueo de emergencia: explicados con ejemplos; recomendado activarlo tras la primera semana.
8. Permisos, con una pantalla por permiso (qué hace, qué NO hace), y comprobación en vivo.
9. Prueba de bloqueo: abrir una app elegida y ver la pantalla de bloqueo, para comprobar que funciona.
- Poder saltar, volver atrás y repetir el asistente desde Ajustes.

---

## 12. Sección de Ayuda dentro de la app

**Qué se pide.** Una sección donde se explique para qué sirve cada función, con ejemplos y excepciones.

**Propuesta.**
- Nueva pestaña o entrada en Ajustes: «Ayuda», con buscador y una ficha por función del catálogo (A–O): qué hace, ejemplo, excepciones («no bloquea llamadas ni emergencias», «no funciona si falta el permiso de accesibilidad») y enlace directo a la pantalla.
- Textos en `strings.xml`, generados a partir de una sola fuente (por ejemplo `docs/AYUDA.md` o un JSON en `data/`), para que la ayuda y el catálogo no se desfasen; una prueba que falle si una función del catálogo no tiene ficha.
- Botón «?» contextual en cada pantalla compleja (horarios, aprobaciones, modo estricto).
- **Ficha de ejemplo, diferencia entre «Temporizador» y «Ayúdame»:**
  - **Temporizador** (M3, Inicio → «Temporizador»): «Bloquea el teléfono un rato, sin poder salir hasta que acabe». Durante la duración elegida **no se puede salir de la pantalla del temporizador**: es un encierro de concentración (estudiar, dormir sin tocar el teléfono). No responde a una urgencia.
  - **Ayúdame** (F6, Inicio → «Ayúdame»): «Bloquea todo ahora durante el tiempo que elijas. No se puede deshacer». Aplica el **bloqueo máximo a todas las apps** salvo llamadas, emergencias y FocusGuard, pide confirmación («No podrás deshacerlo hasta que termine el tiempo»), muestra cuenta atrás en la app y en el widget y puede avisar al compañero. Está pensado para un impulso. Comprobado en el teléfono con 1 min: bloqueó Maps y Mensajes y se liberó al acabar.
  - En una frase: el Temporizador **te deja dentro de una pantalla**; Ayúdame **te deja fuera de todo lo demás**.

---

## 13. Al crear un perfil por categorías, que se rellene con aplicaciones y páginas por defecto

**Qué hay hoy.** «Mi primer perfil» muestra «Bloquea 1 categoría»: la categoría se aplica con la tabla de unas 140 apps y webs (`CategoryCatalog`, `data/seed/`), pero el perfil no muestra qué apps y páginas incluye ni se pueden quitar una a una.

**Qué se pide.** Que al crear un perfil y marcar categorías se vayan **añadiendo** aplicaciones y páginas por defecto.

**Propuesta.**
- Al marcar una categoría en «Nuevo perfil»: añadir a las listas del perfil las **apps instaladas** de esa categoría y las **webs** de la tabla, como entradas explícitas que el usuario ve y puede quitar o añadir (con aviso «se añadieron 7 apps y 12 webs»).
- Conservar el vínculo con la categoría para que las apps nuevas de esa categoría se sumen al instalarse (A10) y las listas del servidor se actualicen sin pisar lo que el usuario quitó a mano.
- Ampliar la tabla de categorías (hoy unas 140 entradas) con las plantillas del punto 15.
- Pruebas en `core-decision` para marcar y desmarcar categoría, entradas quitadas a mano que no vuelven y apps recién instaladas.

---

## 14. Plantillas de perfiles a partir del uso real y del contexto del usuario

**Qué se pide.** Crear plantillas de perfil con sitios web y apps que se ven instaladas y que convendría limitar, según el **tiempo en pantalla** del usuario y lo que se sabe de él de otras conversaciones, incluido su vault personal de Obsidian.

**Decisión de privacidad (importante, el repo puede hacerse público):**
- **Plantillas genéricas** (sirven a cualquiera, ver punto 15): van al repositorio, en `data/seed/`, con apps y dominios conocidos.
- **Plantillas personales** (hechas con el uso del teléfono y el contexto del vault): **no entran en el repositorio ni en git**. Se guardan solo en el vault del usuario (`10 IA y Herramientas/`) y se cargan en la app con «Importar configuración» (N3) o desde el asistente. Ningún dato del vault viaja al servidor ni a documentos del repo.
- El tiempo en pantalla se lee del propio teléfono (`UsageStatsManager` en la app; por `adb` durante las pruebas) y se queda en el teléfono.

**Propuesta de la función en la app.**
- «Sugerencias a partir de tu uso»: en el teléfono, con el permiso de acceso de uso ya concedido, listar las apps más usadas en 7 días y proponer perfiles («Esto te quita 2 h 10 min al día: ¿lo limitas?»), sin enviar nada fuera.
- Plantillas con nombre, emoji, apps, webs, palabras clave y horario sugerido, que el usuario acepta, edita o descarta.

---

## 15. Lista de plantillas genéricas a incluir (para el repo)

Redes sociales (Instagram, Facebook, X, TikTok, Snapchat, Threads, Reddit, Pinterest, LinkedIn), Vídeo (YouTube, Twitch, Netflix, Disney+, Prime Video, Shorts y Reels), Juegos (Steam, Epic, Roblox, Clash, Candy Crush, Free Fire, PUBG), Apuestas (casas de apuestas y casinos), Citas (Tinder, Bumble, Badoo), Noticias (portales y agregadores), Compras (Amazon, Temu, AliExpress, Shein, Mercado Libre), Contenido adulto (lista del servidor), Música y podcasts (opcional), Mensajería (opcional, con horario), Concentración para estudiar (todo salvo lo elegido) y Descanso nocturno (22:00 a 7:00).

---

## 16. Todo lo anterior debe venir por defecto en la aplicación

**Qué se pide.** Que las plantillas, el contenido adulto bloqueado, el asistente, la ayuda y el resto de correcciones **vengan ya incluidos en la app**, sin que el usuario tenga que montarlos a mano.

**Cómo se reparte (importante por privacidad: el repositorio puede hacerse público).**
- **En el repositorio (para cualquiera):** las plantillas genéricas (punto 15), el contenido adulto activado de serie (punto 8), las páginas de manga dentro de la lista de adulto (punto 17), el asistente (11), la ayuda (12), los selectores (9), el arreglo del navegador (7), la aprobación del compañero para el modo estricto (10), la aplicación automática de lo aprobado (4), etc.
- **Solo en el APK del usuario (no en git):** las plantillas personales (punto 14), hechas con su uso real y su contexto. Se empaquetan en el momento de compilar desde un archivo local ignorado por git, como ya se hace con `google-services.json`:
  - Ruta: `app/personal/plantillas.json`, añadida al `.gitignore`. Si existe, `build.gradle.kts` lo copia a los `assets` del APK; si no, la app compila igual sin él.
  - La app lo lee en el primer arranque, antes del asistente, y lo ofrece como «Plantillas para ti»: el usuario las acepta, edita o descarta; son perfiles normales y se pueden modificar siempre.
  - Prueba que falla si ese archivo se cuela en un commit (`gitleaks` con una regla propia o una comprobación en el `.gitignore`).
- La fuente de las plantillas personales sigue siendo la nota del vault (`10 IA y Herramientas/FocusGuard - plantillas personales.md`); desde ella se genera `plantillas.json` a mano o con un script local que **no** entra en el repo.

**Plan de trabajo propuesto (ramas, una por tema, cada una con pruebas y revisiones):**
1. `claude/estadisticas-bloqueos` (hallazgo K1) y `claude/bucle-navegador` (punto 7): lo más roto.
2. `claude/pantalla-bloqueo` (puntos 1 y 6): no cerrarse sola y decir qué perfil o motivo bloquea.
3. `claude/aplicar-aprobado` (puntos 4, 5 y 10): aplicar solo lo aprobado, lista visible y modo estricto con compañero. Con `/security-review`.
   `claude/anti-desinstalacion` (punto 18): administrador del dispositivo como requisito, barreras y avisos. Con `/security-review`.
4. `claude/notificaciones` (punto 3 y el plural de J3).
5. `claude/correos-y-paginas` (punto 2): tokens de diseño y botones.
6. `claude/adulto-por-defecto` (puntos 8 y 17).
7. `claude/selectores-tiempo` (punto 9).
8. `claude/categorias-y-plantillas` (puntos 13, 14, 15 y 16).
9. `claude/asistente-y-ayuda` (puntos 11 y 12), al final porque usa las piezas anteriores.

---

## 17. Las páginas de manga entran en el bloqueo de contenido adulto

**Qué se pide.** Bloquear las páginas de manga dentro de «Contenido adulto», con la misma regla (apagarlo pide aprobación del compañero y se reactiva solo al día siguiente).

**Propuesta.**
- Añadir a la lista propia del servidor (`server/data/own/`, la que publica `GET /blocklists/...` y consume también el resolvedor DNS) los dominios de manga conocidos, por ejemplo `mangadex.org`, `manganato.com` y `mangapill.com`, **verificándolos uno a uno antes** (que existan, que sean de manga y que no arrastren sitios legítimos); documentar la lista en `DECISIONES.md`.
- Misma lista para el filtro del navegador y para el DNS, de modo que cuente en «Contenido adulto» y en las estadísticas de bloqueos.
- Como es una decisión de categoría («manga = contenido a evitar»), dejarla en una lista aparte dentro de adulto (`manga`) para que cualquier usuario pueda ver qué hay en ella y quitarla con aprobación.
- Prueba: el dominio se bloquea en el navegador y en el DNS, y un dominio legítimo parecido no.

---

## 18. La aplicación debe impedir que se desinstale

**Qué se pide.** Que FocusGuard prevenga su desinstalación (y que no se pueda esquivar el bloqueo quitándola).

**Qué hay hoy.**
- Existe el administrador del dispositivo (`FocusDeviceAdminReceiver`, H4), pero en el teléfono de pruebas **no está activado** y el solucionador de problemas lo marca solo como «Recomendado»: el APK se puede desinstalar con normalidad.
- Con el modo estricto activado, el servicio de accesibilidad cierra las pantallas de «Desinstalar» y los ajustes de la app (`SensitiveScreens.isUninstallScreen`, `StrictProtection.SETTINGS_GUARD`). Con el modo estricto apagado (como está ahora), no hay ninguna barrera.
- H11 prevé avisar al compañero al apagar la accesibilidad, al desinstalar y al restablecer de fábrica.

**Qué se quiere (por capas).**
1. **Administrador del dispositivo activo como requisito.** Mientras esté activo, Android no deja desinstalar la app ni por los ajustes ni por `adb uninstall`: hay que desactivar primero el administrador. Pasa de «Recomendado» a **«Imprescindible»** en el solucionador, se pide en el asistente (punto 11) con una pantalla propia que explique qué hace y qué no, y se vuelve a pedir si se desactiva.
2. **Desactivar el administrador debe costar:** esa pantalla de ajustes del sistema se cierra con el servicio de accesibilidad (como las de desinstalar) salvo que exista una aprobación del compañero o haya pasado la espera elegida; es la misma ruta de «apagar algo» (puntos 4 y 10).
3. **Barreras de la desinstalación fuera del sistema:** la ficha de la app en Play Store, el instalador de paquetes y las apps de «gestor de archivos» que ofrecen desinstalar; y el gesto de arrastrar el icono a «Desinstalar» del lanzador.
4. **Aviso al compañero (H11)** cuando se intenta o se logra: accesibilidad apagada, administrador desactivado, VPN del DNS apagada y la app sin `heartbeat` más de 48 h (ya existe el servidor, que avisa por inactividad; comprobar que también cubre una desinstalación).
5. **Reinstalar no debe borrar la protección:** al reinstalar con la misma cuenta, volver a registrar el dispositivo y recuperar la configuración desde la copia del servidor (N2) con el modo estricto y el compañero activos; el compañero ve el aviso de «dispositivo nuevo».

**Límites honestos (hay que decirlos al usuario en la ayuda):**
- Android no permite que una app normal sea indesinstalable al 100 %. Con el administrador activo se frena el camino normal, pero **no** se frenan: arrancar en modo seguro, un restablecimiento de fábrica, `adb` con permisos de propietario o desactivar el administrador desde ajustes (mientras no esté cerrada esa pantalla).
- La barrera completa solo existe con un **propietario del dispositivo** (device owner), que exige configurarlo con el teléfono recién restablecido; queda como mejora opcional para quien lo quiera, con documentación aparte.
- Cada intento queda registrado y, si hay compañero, se le avisa.

**Cómo comprobarlo.** Activar el administrador y comprobar con `adb shell pm uninstall com.focusguard` (debe fallar con `DELETE_FAILED_DEVICE_POLICY_MANAGER`) y desde Ajustes → Aplicaciones → FocusGuard (el botón «Desinstalar» está desactivado o la pantalla se cierra); desactivar el administrador desde ajustes y comprobar que se cierra la pantalla y llega el aviso al compañero; reinstalar y comprobar que la protección vuelve.

---

## Hallazgos de la sesión de pruebas (se añaden aquí)

Sesión del 2026-10-28, Motorola edge 40 neo (Android 15), APK 0.2.0 firmado con la clave de release, servidor en `https://focusguard.midominio.com`.

**Funciona (comprobado):**
- Instalación, asistente y permisos (accesibilidad, acceso de uso, superposición, notificaciones).
- Cuenta, confirmación del correo, registro del dispositivo con token de push (142 caracteres) en el servidor.
- Bloqueo de Instagram con la pantalla de bloqueo («Volver» y «Desbloqueo de emergencia»), también tras reiniciar el teléfono.
- Filtro DNS por la VPN de la app: `007dating.com` se bloquea (resuelve a `127.0.0.1`) y `example.com` resuelve; «Consultas DNS» cuenta 1 bloqueada y 52 permitidas; tras reiniciar, la VPN y el bloqueo vuelven solos.
- Push: aviso real por FCM desde el servidor, con la app en segundo plano, y el teléfono consulta al servidor en ~1 s.
- Compañero: invitación, aceptación por correo, solicitud de aprobación («Apagar el filtro DNS»), aprobación desde el correo del compañero y notificación «Tu compañero respondió» casi al instante.
- Reinicio: el servicio de accesibilidad y el proceso de la app arrancan solos; el servidor ve el dispositivo (`last_seen`) a los pocos segundos.

**No probado en el teléfono:**
- Cerrar sesión y que el servidor olvide el token (`DELETE /devices/:id/push-token`): cubierto con pruebas automáticas (servidor y app), pero cerrar sesión con compañero activo exige su aprobación; sin hacer.
- Notificación con el proceso de la app muerto del todo (el servicio de accesibilidad lo mantiene vivo, así que `am kill` no lo mata).
- Teléfonos de otros fabricantes y el ahorro de batería de cada uno.
- El administrador del dispositivo (recomendado, sin activar).

**Hallazgos del nivel 1 de pruebas (ver `docs/PRUEBAS_NIVEL1.md`):**
- **Estadísticas no cuentan los bloqueos (FALLA).** Tras ≥8 bloqueos el mismo día (Instagram, Maps, Brave en contenido adulto, Quick Block y pánico), «Bloqueos de los últimos 7 días» dice «Todavía no hay bloqueos» y «Lo que más se bloqueó» dice «Nada en estos días»; «Uso del teléfono» dice «No hay uso registrado ese día» aunque arriba pone «8 h 35 min». `FocusAccessibilityService.record` llama a `blockLog.record(...)` en una corrutina; `StatsViewModel.kt` (línea ~87) lee con `blockLog.since(...)`. Investigar si se escribe y no se lee, si se lee con otra zona horaria o con la «hora de inicio del día» (E17), o si el ViewModel no se refresca al volver a la pestaña. Añadir prueba.
- **Contenido adulto viene apagado por defecto (D1).** Con el interruptor apagado solo bloquea el DNS: el navegador abre la página sin pantalla de bloqueo. Decidir si debe ir encendido de serie o si el asistente de puesta en marcha lo ofrece; anotar en `DECISIONES.md`.
- **La ventana de bloqueo se cierra a los 3 s y eso impide usar sus botones** (refuerza el punto 1). «Desbloqueo de emergencia» y «No debería bloquearse» (B10) solo se pueden pulsar si se toca a ciegas en el primer segundo y medio.
- **El texto «uno de tus perfiles» también sale con Quick Block y con el botón de pánico**, que no son perfiles (refuerza el punto 6): debe decir «Bloqueo rápido», «Pomodoro» o «Botón de pánico».
- **Plural incorrecto:** «Completaste 1 sesiones» (J3); usar `plurals` de Android.

**Observaciones menores:**
- Las pantallas de Aprobaciones salen negras en las capturas (parece `FLAG_SECURE`): es una protección de privacidad, pero conviene confirmar que es intencionado y documentarlo.
- La primera petición con el token caducado recibe 401 y se reintenta tras renovar: normal, pero produce dos `POST /requests` en el registro del servidor (el segundo, 201).
- El asistente pide el permiso de notificaciones en un diálogo encima de la bienvenida: correcto, pero la bienvenida queda tapada hasta responder.
- Las copias, los certificados y el DoH del servidor están verificados aparte (ver `DESPLIEGUE.md` y la memoria del proyecto).
