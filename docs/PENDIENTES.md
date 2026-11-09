# Pendientes

Cosas conocidas que no se resolvieron en su paso, con el motivo. Se quitan de aquí al resolverlas.

## Reloj y manipulación de la hora

- **Salto hacia delante justo antes de reiniciar.** Tras un reinicio el reloj monótono vuelve a cero y no se sabe cuánto estuvo apagado el teléfono, así que `ClockGuard` acepta la hora del teléfono si no retrocede. Cerrarlo del todo exige una hora de red (SNTP), que no existe sin conexión.
- **Zona horaria.** `TrustedClock` usa la zona del sistema, porque cambiarla al viajar es legítimo. Solo se impide por el cierre de las pantallas de fecha y hora.
- **Señal `AUTO_TIME` / `AUTO_TIME_ZONE`.** Avisar o endurecer cuando la hora automática está apagada con una protección activa. No depende del idioma. Sin hacer.
- **`TrustedClock` como `java.time.Clock` y regla de lint** que prohíba `Instant.now()`, `System.currentTimeMillis()` y `LocalDateTime.now()` sin argumento fuera de una lista permitida. Hoy el uso del reloj de confianza depende de la disciplina de quien edita. Propuesto por `/simplify` (altura); descartado de momento por coste frente al beneficio.
- **Rediseño solo monótono** (confianza = ancla + tiempo monótono, sin tolerancia). Descartado: obliga a resincronizar tras cada reinicio y no resuelve la ventana del reinicio.

## Interfaz

Auditoría completa en `docs/ui/AUDITORIA.md`.

- **Probar con TalkBack y con letra al 130 %** en un teléfono real; solo está revisado por código y capturas.
- **Carril de navegación en tabletas** en lugar de la barra inferior. Hoy el contenido se limita a 640 dp de ancho.
- **Horizontal y plegables** sin comprobar.
- **Capturas de las pantallas secundarias** (edición de perfil, modo estricto, aprobaciones, sesión, ajustes): necesitan separar su estado de los ViewModels, como ya hacen Inicio, Protección y Estadísticas.
- **Editor de perfil:** guarda todo en un solo estado, así que cada tecla repinta toda la pantalla. Aceptable con un formulario corto; revisar si crece.
- **Validación de sesiones** repartida entre pantalla y ViewModel (`SessionScreen`, `SessionViewModel`).
- **Rutas de navegación** como cadenas sueltas; pasar a rutas tipadas.
- **Aviso de hora automática apagada** y rutas con scaffolds anidados: ver sección de reloj y estructura de `DECISIONES.md`.
- **Maquetas con `imagegen-frontend-mobile`:** no hay herramienta de generación de imágenes en esta sesión; se hizo la maqueta en HTML (`docs/ui/maquetas/`).

## F3 paso 2: secciones dentro de apps

- Sin identificadores en la especificación: búsqueda de Telegram, búsqueda de imágenes y vídeo en buscadores, compras en apps de tienda y webs de compras. Se añaden a `data/seed/subapps.json` cuando se verifiquen en un teléfono real.
- Los identificadores de las apps (Instagram, Snapchat, YouTube…) cambian con las versiones: comprobar en dispositivo que cada sección se detecta y que no hay falsos positivos (por ejemplo `comment_count`, `favorite`).
- Una sola recorrida del árbol por comprobación y reciclar nodos, si el consumo se nota en dispositivo.

## F3 paso 4a: botón de pánico

- Aviso al compañero al activar el pánico: hecho en F4. El widget «Ayúdame» abre la pantalla de pánico (hecho en el paso 11d).
- Las protecciones de `ProfileRepository.delete` y `setEnabled` sobre el pánico no tienen prueba automática (necesitan Room en un dispositivo o prueba instrumentada).

## F3 paso 4b: desbloqueo de emergencia

- Cupo y duración configurables (hoy 3 por semana y 5 minutos fijos) y poder apagar la función: cambiarlos debilita, así que debe pasar por el guardián de cambios y las aprobaciones.
- Aviso al compañero en cada uso: necesita el servidor (F4).
- La pantalla de los pasos no tiene captura Paparazzi (depende de un ViewModel); probar en un teléfono real el flujo desde la pantalla de bloqueo, la espera y volver a la app.

## F3 paso 5: pantalla de bloqueo

- Imagen o fondo propio y color libre (M1): las paletas fijas aseguran el contraste; una imagen exige comprobarlo sobre ella.
- M2: la frase propia se aplica a todos los ajustes protegidos por igual; no distingue «desinstalar» de «desactivar accesibilidad».
- La vista previa y el editor de mensajes por app no tienen captura Paparazzi (usan `AndroidView` y el selector de apps).

## F3 paso 6: protecciones y solucionador

- Comprobar en dispositivos reales (Samsung, Xiaomi, Pixel) los identificadores del menú de apagado y del panel de notificaciones.
- «Imprescindible» y «Recomendado» se distinguen solo por el texto de la etiqueta; falta una variante de color en `StatusChip`.

## F3 paso 7: categorías

- La tabla de categorías tiene unas 140 apps y webs; con la descarga del servidor (F4) crecerá. Hasta entonces, las apps que no estén en la tabla no se bloquean por categoría.

## F3 paso 8: avisos

- Faltan los avisos de racha y de estadísticas del día (J3): las rachas son de la fase 5.

## F3 paso 9: apps recién instaladas

- La hora de instalación sale del sistema (`firstInstallTime`); si el usuario adelanta el reloj, la protección de fecha y hora (aprobaciones o modo estricto) es lo que lo impide.
- No hay lista de excepciones («esta app nueva sí»): permitirla debilitaría el ajuste y necesitaría su propia aprobación. Sin hacer.

## F3 paso 10: vista previa y Block Me

- La vista previa no se actualiza con lo que el servicio tiene en memoria: el uso del límite se lee de lo guardado, que el servicio escribe cada pocos segundos.
- Pantalla de vista previa y diálogo de Block Me sin prueba en dispositivo con TalkBack; las capturas son de Paparazzi.

## F3 paso 11: estadísticas

- El sistema guarda eventos de uso unos 7 días; para ver más atrás haría falta guardar el resumen de cada día en el teléfono.
- Incógnito solo en los cinco navegadores de la tabla; comprobar en un teléfono real que las insignias siguen con esos identificadores.
- Las estadísticas de webs no incluyen las vistas web dentro de otras apps (B8).
- Pantalla de Estadísticas sin comprobar con TalkBack ni letra grande: los siete chips de día ocupan dos filas.

## F3 paso 12: notificaciones

- J2 se reconoce por el paquete `android` y el nombre de la app en el texto; comprobar en teléfonos reales que el aviso se esconde de verdad (los de servicios en primer plano pueden no dejarse posponer) y en otros idiomas del sistema.
- Con las notificaciones guardadas, las acciones y respuestas rápidas de la notificación original se pierden; solo queda el texto.
- Pantallas nuevas sin comprobar con TalkBack.
- Las notificaciones web que llegan a través de un navegador (notificaciones push de una web bloqueada) no se quitan: el servicio solo sabe qué web está en pantalla, no de cuál viene la notificación.
- `NotificationLog` (retención de 200 notificaciones y 7 días) no tiene prueba automática: necesita Room en un dispositivo.

## F3 paso 13: atajos y mensaje al apagar

- Los atajos del icono solo se pueden comprobar en un teléfono real (lanzadores distintos los muestran distinto). Los iconos son provisionales.
- «Quitar bloqueos» no pide aprobación una sola vez para todo: crea una solicitud por perfil.

## F3 paso 14: bienvenida

- La prueba muestra la pantalla de bloqueo pero no prueba la detección real: para eso haría falta que el servicio de accesibilidad estuviera activo y una app de la lista abierta. Si faltan permisos, el paso de permisos lo avisa pero deja continuar.
- Las preguntas no cubren el contenido adulto ni el compañero de responsabilidad (F4).

## F3 paso 15: más avisos

- La hora de la frase diaria y del resumen (9:00) no se puede cambiar.
- El resumen semanal solo cuenta bloqueos; faltan tiempo de uso y comparación con la semana anterior.

## F3 paso 16: navegadores integrados y ventanas flotantes

- B8 y A7 no se han comprobado en un teléfono real: la clase de la vista web puede no aparecer en apps con motores propios (Flutter, Unity, Chrome Custom Tabs, que corren dentro del navegador), y el botón de cerrar de la imagen en imagen cambia por fabricante.
- Chrome Custom Tabs (la página se abre dentro de Chrome sobre la app): la decisión mira el paquete en primer plano, que es Chrome; las webs se bloquean por los perfiles si la barra de direcciones se puede leer.
- B9, búsqueda segura forzada en Google, Bing y YouTube: se hará con el resolvedor DNS propio (F4).
- Popups y burbujas que no son imagen en imagen (A7) no se cierran.

## F4 paso 1: servidor base

- Los límites de intentos se llaman a mano en cada ruta y viven en memoria (un solo proceso). Pasarlos a una declaración por ruta con un gancho único cuando haya más rutas.
- El registro devuelve 409 si el correo ya tiene cuenta (revela que existe); aceptado por ahora.
- Sin prueba automática de que un token falso no llega a calcular el hash de contraseña (`isLive`).
- Sin probar el despliegue con Docker Compose: no hay demonio de Docker en esta sesión. Probar en el VPS: arranque, migraciones, HTTPS de Caddy y envío por SMTP real (SPF, DKIM, DMARC).

## F4 paso 2: compañero y aprobaciones (servidor)

- Push (FCM): hecho en la rama `claude/push-fcm` (token registrado, servicio de avisos, `PushHandler`). Sin probar en un teléfono real: hay que comprobar que llega con la app cerrada y con ahorro de batería de cada fabricante (Xiaomi, Huawei, Samsung…), y que el servidor lo envía con la clave montada en el VPS.
- Push, de la revisión de seguridad: (a) restringir en la consola de Google Cloud la clave de API de `google-services.json` al paquete `com.focusguard` y a la huella del APK de release (la clave va dentro del APK, no es secreta, pero sin restringir otros la podrían usar); (b) `PUT /devices/register` quita el token a otros dispositivos con una consulta aparte, sin índice único: dos registros simultáneos del mismo token podrían dejar los dos, y un índice único parcial sobre `fcm_token` lo cerraría con una migración; (c) al iniciar sesión la app registra el dispositivo dos veces (`refresh` y luego `ensureDevice`): es inofensivo, pero quitarlo exige cambiar la semántica de `ensureDevice`, que hoy las pruebas fijan como «confirmar con el servidor»; (d) el token se pide en cada `refresh` hasta 5 s tras `/me`: se podría pedir en paralelo o guardar el último enviado en `AccountStore` para no repetirlo tras reiniciar; (e) cerrar sesión espera hasta 5 s al servidor: si molesta, bajar el tope o mandar la baja del token sin esperar.
- La cola de correos no agrupa («intento bloqueado» cada 10 min) ni lleva enlace de baja por tipo de aviso: llegan con los eventos (paso 3).
- El compañero no puede pedir que se le dé de baja de los avisos todavía (I4); llega con las preferencias de avisos.
- Un solo compañero por cuenta; más de uno (por ejemplo el padre y la madre) exigiría quitar el índice único `partners_one_live` y decidir si basta con uno o hacen falta todos.
- Retención de 90 días para solicitudes resueltas y compañeros dados de baja: revisar con la privacidad en la fase de copias.
- El push de aviso es de mejor esfuerzo: no hay cola con reintentos como la del correo. Decidir con el paso de eventos si hace falta generalizar la cola de salida con un canal.
- El índice parcial `mail_outbox_next` fija `attempts < 8` y `MAX_ATTEMPTS` está en `mailqueue.ts`: si cambia uno hay que cambiar el otro (con una migración nueva).

## F4 paso 3: eventos y avisos (servidor)

- La hora del informe es UTC: en América llega de madrugada y en España a las 9 o 10. Hace falta una zona horaria por compañero.
- El intervalo del resumen (10 min) y la limitación de avisos graves son fijos.
- El informe diario no incluye tiempo de uso ni racha (F5); solo cuenta eventos.
- Los bloqueos guardan una fila por intento durante 30 días y no hay tope por usuario (solo el de 120 lotes por hora). Agregar por ventana si pesa.
- Los reportes de bloqueo erróneo se guardan sin pantalla de revisión ni purga: se leen de la base.
- El informe diario recorre a todos los compañeros cada hora y, si coinciden en la hora, salen en ráfaga por la misma cola que los avisos graves: poner prioridad en la cola cuando haya muchos.
- La purga de eventos es un `DELETE` por fecha sin índice propio (bastaría un índice BRIN o borrar por lotes con mucho volumen).
- `users` no tiene nombre para mostrar: los correos llaman «usuario» a su dirección. Hará falta con el modo familiar.
- Falta el cliente Android de todo esto (cuenta, eventos, latido, PIN por correo): paso siguiente.

## F4 paso 4: listas, selectores, categorías y copias (servidor)

- La lista inicial de palabras es corta (unas 13 por idioma): hay que ampliarla y probarla con contenido real antes de fiarse de ella, y añadir más idiomas.
- La lista de excepciones por dominio empieza vacía.
- Las listas no van firmadas (ver `DECISIONES.md`). Un servidor comprometido podría mandar una lista que bloquee o deje de bloquear lo que quiera.
- Las categorías llevan unas 147 apps y webs; la tabla de categorías del servidor es la misma que la del repositorio, sin ampliar desde fuentes públicas.
- El cifrado de las copias es cosa de la app: falta decidir la clave (derivada de la contraseña de la cuenta, o una propia que el usuario guarda) y qué pasa si la olvida. Restaurar una copia con ajustes más débiles que los actuales debe pasar por una aprobación (H8).
- Sin descarga en segundo plano probada contra la fuente real desde el VPS (sí se probó el analizador con la lista de hoy: 76 795 de 76 801 líneas).
- `applyList` carga en memoria la lista actual y calcula la diferencia en Node (unos 100-200 ms con 77 000 dominios, una vez al día): si el DNS pasa a listas de cientos de miles de dominios, hacerlo en SQL.
- Sin `ETag`/`304` en las listas: la caché evita el cálculo pero la respuesta completa (2 MB) se sigue mandando; Caddy la comprime.
- La limitación de copias (10 al día) se aplica al llegar el cuerpo (hasta 2 MB), no antes.

## F4 paso 5: DNS propio

- La configuración de AdGuard Home y su servicio en Compose no se han probado contra una imagen real (no hay Docker en esta sesión): la versión de la imagen, el número de `schema_version`, si acepta como filtro una dirección de la red interna y si identifica al cliente por la ruta `/dns-query/<id>` hay que comprobarlos al primer arranque.
- Falta leer las estadísticas por cliente de AdGuard Home para el informe diario (D8, `GET /dns/stats`).
- Las apps con su propio DNS cifrado hacia otros servidores no pasan por este resolvedor; la VPN de la app solo captura el DNS normal. Una lista de dominios de DoH conocidos se puede sumar a las reglas.
- B9 solo funciona con la VPN de la app activa (cliente Android, siguiente paso).
- El panel de AdGuard Home no lleva contraseña (solo se alcanza por túnel SSH).
- D8 necesita el registro de consultas de AdGuard Home en memoria (o un contador propio): decidir con el usuario, porque contiene los dominios de cada dispositivo.
- Restringir el resolvedor a los identificadores registrados (sincronizar `allowed_clients` de AdGuard Home desde el servidor cuando se crea un dispositivo).
- Cambios en `dns/AdGuardHome.yaml` no llegan a un despliegue existente (la plantilla se copia solo la primera vez): borrar el volumen `dns-conf` o editar en el panel.
- Segundo filtro con dominios de DoH conocidos (`/blocklists/dns-doh.txt`): el `Map` de derivados ya lo admite.

## F4 paso 6 (Android): cuenta y cliente del servidor

- Cerrar sesión o cambiar de servidor no pide aprobación todavía: con un compañero activo, cerrar sesión silenciaría sus avisos. Se protege en el paso del compañero (siguiente).
- Registro del token de push: hecho (ver F4 push en `DECISIONES.md`). Un teléfono sin Google Play o sin `google-services.json` en la compilación sigue funcionando sin push: se entera al abrir la app.
- El cliente se probó con un transporte falso y contra un servidor local, no contra el servidor real ni en un teléfono; el almacenamiento con el almacén de claves solo se prueba en dispositivo.
- La cuenta no tiene «cambiar contraseña» ni «borrar cuenta»; el servidor tampoco tiene esos endpoints.
- Sin certificado fijado (pinning): se confía en los certificados del sistema.
- El margen de 60 s del token de refresco no detecta un robo si el ladrón actúa dentro de ese minuto; acortarlo o pedir un identificador de cliente sería más estricto.
- `ServerApi.call` solo trabaja con objetos JSON: los lotes de eventos y las listas necesitarán una variante con arrays; separar `ApiClient` de las funciones por área cuando haya unos diez endpoints.
- Falta una prueba instrumentada de `KeystoreAccountStore` en un teléfono (el cifrado, la invalidación de la clave).
- La app no usa certificado fijado (pinning).


## F4 paso 7 (Android): compañero y aprobaciones

- Falta probar el flujo completo en un teléfono con el servidor real y un correo real (invitar, aceptar, aprobar, aplicar); lo cubierto son pruebas con transporte falso y las del servidor.
- Con push (FCM), al resolverse una solicitud llega una notificación («Tu compañero respondió») aunque la app esté cerrada; el cambio aprobado se sigue aplicando al abrir Aprobaciones, no solo. Sin push (teléfono sin Google Play) la pantalla de Aprobaciones consulta cada 15 s mientras está abierta y, con la app cerrada, el cambio se aplica al volver a abrirla.
- Quien borra los datos de la app o la desinstala pierde la solicitud pendiente local; la del servidor caduca sola a las 48 h.
- Un usuario puede crear una segunda cuenta sin compañero: es inherente a un bloqueador personal y el modo estricto lo dificulta, no lo impide.
- El correo de invitación y de solicitud solo describe la acción en una frase corta; no incluye cuál perfil o lista se toca.
- Diseño: el aprobador obligatorio de una acción (`viaPartner`) atraviesa `ChangeGuard`, `ApprovalGate` y `ApprovalRepository` como un parámetro. Si aparece otra acción «solo compañero», conviene una política por clave (`requiredApprover`) en lugar de otro parámetro. La claves de `describe` (en `core-decision`) y las `KEY_*` del ejecutor son dos listas; la prueba `DescribeActionsTest` avisa si falta una, pero lo limpio es una tabla única.
- La política «cerrar sesión pide al compañero» vive en el `AccountViewModel`; `AccountRepository.signOut()` sigue siendo público. Hoy solo llaman la pantalla y el ejecutor; si hay más llamadores, moverla al repositorio.
- `sync()` consulta las solicitudes en espera una por una; con unas pocas basta, y un endpoint que devuelva todas las del dispositivo lo dejaría en una petición.
- `POST /requests` no es idempotente: si la respuesta se pierde antes de guardar el id, el siguiente intento crea otra solicitud y manda otro correo. Haría falta una clave de idempotencia generada en el teléfono (`clientId`) y guardada en el servidor.
- Si la aprobación del compañero se gasta y la acción ya no es aplicable (el perfil se borró entretanto), se pierde y hay que pedirla otra vez; solo el modo estricto se comprueba antes de gastarla.
- Con el modo «compañero» y la sesión cerrada, los cambios que debilitan esperan a volver a entrar.

## F4 paso 8 (Android): avisos al compañero y latido

- Falta probar en un teléfono real: que el correo llegue en cada caso, que WorkManager mande el aviso grave en segundos con red, que el ahorro de batería de las marcas no retrase el latido más de 48 h (el solucionador ya avisa de ello) y que un reinicio por actualización no cuente como «protección apagada».
- Los intentos de desinstalar no se distinguen de los de ajustes. Haría falta reconocer el diálogo de desinstalar por sus vistas (tabla en `data/seed`) y mandar `uninstall_attempt`.
- «Protección apagada» no cubre otras formas de dejarla sin efecto (quitar permisos de uso o de superposición, forzar parada): el servidor lo notaría por la falta de latidos tras 48 h.
- El informe diario (`daily_report`) y el resumen de bloqueos del servidor no usan todavía datos del teléfono más allá de los eventos; el informe del día con estadísticas llegaría con los endpoints de `docs/BACKEND.md` §8.
- El push solo avisa de solicitudes resueltas y de compañero confirmado; el servidor sigue sin despertar el teléfono por inactividad: el latido es solo del teléfono hacia el servidor.
- Los sucesos se registran en cinco sitios (servicio de accesibilidad, receptor del administrador, pánico y emergencia en sus pantallas). Más limpio: que los repositorios de dominio (`EmergencyRepository.grant`, guardado del pánico) avisen por callback, como `partnerGone`, y un único observador de permisos emita «protección apagada» también para acceso de uso y superposición.
- El trabajo periódico se programa siempre, tengan o no compañero; con compañero activo solo hace algo útil. Programarlo y cancelarlo según `PartnerRepository.state` evitaría despertar el proceso cada 6 h a quien no lo usa.
- Quien ya tuviera un compañero activo antes de este paso empieza con los avisos apagados (se encienden al aceptar un compañero); hoy no hay instalaciones previas, pero habría que migrarlo si las hubiera.

## F4 paso 9 (Android): listas del servidor y contenido adulto

- Falta probar en un teléfono con las listas reales: el tiempo de armar un filtro de unas 100 000 entradas (se hace en segundo plano; mientras tanto vale el anterior) y la memoria que ocupa. Si pesa demasiado, pasar a un conjunto de dominios guardado ya normalizado (el servidor ya los valida).
- La respuesta JSON de una lista entera tiene el tope de 8 MB del cliente; si la lista de dominios lo supera hay que bajarla como texto (`GET /blocklists/domains.txt`, que ya existe) y por partes.
- No se usa todavía el parámetro de búsqueda segura en buscadores ni los bloqueos previos por URL del algoritmo (paso 1 de `TABLAS_Y_ALGORITMO.md` 6: `tbm=isch`, `udm=…`); la reescritura de la dirección con el parámetro seguro también falta.
- El texto de pantalla solo se evalúa en la ventana activa y no en cada vista de un navegador integrado en otra app; se mide en un teléfono si es suficiente.
- La reactivación automática (D6) es fija de 24 h; que el usuario la configure (y que alargarla o quitarla pida aprobación) queda para después.
- Las palabras y excepciones del usuario no entran todavía en la copia de seguridad (`BackupRepository`).
- Los dominios principales se calculan quitando etiquetas hasta quedar dos: `co.uk` y similares se tratan como un dominio principal (no hay lista de sufijos públicos).
- El filtro DNS (D5), VPN solo DNS, es el paso siguiente.
- La lectura de pantalla (`readScreen`, detección de WebView) merece un colaborador propio en vez de crecer dentro del servicio de accesibilidad; `PartnerSyncWorker` mezcla compañero y listas públicas (las listas no necesitan cuenta): separar en dos trabajos si crece más.
- Una lista base de dominios y palabras dentro del APK (que el servidor no pueda quitar) haría el filtro independiente de lo que publique un servidor; hoy una lista que pierde más de la mitad se rechaza, pero un servidor que la vacía poco a poco no se detecta.
- La frase D7 no está atada a la entrada: escribirla una vez sirve para el siguiente intento. Con aprobaciones activadas cada «permitir» pide su aprobación; sin ellas es solo fricción.

## F4 paso 10 (Android): filtro DNS por VPN local

- **No se ha probado en un teléfono real ni contra AdGuard Home real**: el montaje de la VPN (dirección, ruta, `addDisallowedApplication`), la lectura del túnel y la respuesta por el mismo túnel solo se prueban en dispositivo. La parte pura (paquetes IPv4/UDP, mensajes DNS) y el cliente DoH tienen pruebas unitarias.
- Android «DNS privado» (DoT) en modo estricto salta la VPN para el DNS: hay que avisar al usuario de que lo ponga en automático o apagado. Tampoco se cubren apps con su propio DNS cifrado a otro servidor (lo tapa la lista de DoH conocidos, pendiente en el servidor).
- Sin TCP en el túnel: una respuesta DoH nunca lleva el bit TC, así que no se necesita; pero una app que fuerce DNS por TCP a la dirección de la VPN no recibe respuesta.
- No hay reactivación automática del filtro DNS apagado con aprobación (D6 solo en contenido adulto) ni arranque tras reiniciar el teléfono (paso 11, H5).
- El permiso de VPN lo pide la pantalla del filtro; si el usuario lo retira desde el sistema, solo se entera el compañero (evento) y el estado vuelve a «apagado».
- Fallar abierto no distingue una consulta bloqueada por el resolvedor de una que no contestó: si el resolvedor devuelve NXDOMAIN o una IP nula para un dominio de las listas, se respeta.
- El identificador de cliente de la URL no autentica (ver `dns/README.md`): cualquiera que conozca la URL de otro dispositivo gasta su cuota de consultas.
- Sin HTTP/2 ni deduplicación de consultas idénticas: cada consulta es un POST sobre una conexión reutilizada (`HttpURLConnection`). Si en un teléfono real la latencia o el consumo se notan, pasar a un cliente con multiplexado (OkHttp o Cronet) y agrupar las consultas A/AAAA iguales en vuelo.

## F4 paso 11b (Android): copia de seguridad en el servidor

- Sin copia automática periódica (haría falta guardar la frase cifrada con el almacén de claves del teléfono, y se perdería al cambiar de teléfono, que es justo cuando hace falta). Hoy es «Guardar ahora».
- El historial de bloqueos, las estadísticas y las rachas no entran en la copia (decisión de privacidad en DECISIONES); las rachas irán en F5 con su propia ruta.
- Restaurar no cambia perfiles ya existentes ni quita los actuales: solo añade. Si alguien restaura dos veces tendrá perfiles duplicados.
- Falta probar en un teléfono real el tiempo de derivar la clave (210 000 vueltas) en teléfonos modestos.
- No hay prueba automática de la restauración completa (necesita Android); sí del cifrado y del cliente.

## F4 paso 11c (Android): reporte de bloqueo, temporizador y protecciones A15/A17/A20

- A15, A17 y A20 se reconocen por nombres de actividad y títulos que no se han comprobado en teléfonos reales: otros fabricantes (Xiaomi, Huawei, OnePlus), otras tiendas de apps y otros idiomas hay que añadirlos probando. El ahorro extremo solo está en las 17 cadenas conocidas (Samsung y similares); el ahorro normal de Android no se cierra.
- El temporizador no impide ir al inicio del teléfono ni usar el lanzador (hacen falta para llamar y para las emergencias); impide usar apps. La cuenta atrás se ve solo en FocusGuard.
- El reporte de bloqueo erróneo no se encola sin conexión (se avisa y hay que reintentar); tampoco hay botón en la pantalla alternativa (`LockActivity`).
- El servidor guarda los reportes pero aún no hay panel ni proceso para revisarlos y publicar excepciones (`wrong_block_reports` se consulta a mano).

## F4 paso 11d (Android): widgets

- Nada de esto se ha visto en un lanzador real: las capturas pintan el mismo `RemoteViews`, pero el redimensionado, los colores sobre fondos de pantalla y el comportamiento en Samsung, Xiaomi o Pixel hay que mirarlos en teléfono.
- El widget de estado solo conoce horarios y pausas: Wi-Fi, cargador y lugar se dan por cumplidos, los límites de uso no cuentan y un Pomodoro cambia de fase en el siguiente refresco (como mucho 30 min si el sistema retrasa el aviso).
- El cronómetro del bloqueo puede enseñar un tiempo negativo unos minutos si el sistema retrasa el aviso de fin (ahorro de batería).
- No hay widget en la pantalla de bloqueo ni configuración por widget (qué perfiles enseñar).
- Las pruebas de capturas no pueden usar la forma más corta (un fondo cambiado por código); la forma de tres vistas por día es un compromiso para poder probar.

## F5 etapa 1 (Android): rachas

- Las rachas no entran en la copia de seguridad ni en el archivo exportado: al cambiar de teléfono empiezan de cero (se hará con la copia del servidor, no con archivos).
- «Bloqueo activo» solo mira el filtro adulto y los perfiles; el filtro DNS por sí solo no suma. Apagar un perfil o terminar una sesión antes de tiempo no cuenta como pausa.
- No se ha visto en un teléfono real que el servicio sume bien con la pantalla apagada ni tras un reinicio del servicio (el tope de 5 s por lectura es deliberado: un servicio parado no cuenta).
- La hora del aviso de racha (20:00) y las reglas de restauración (2 días, 3 de racha, cada 14 días) no se pueden cambiar.
- Cambiar el inicio del día reinterpreta solo los días nuevos.
- Falta un widget o aviso de la racha en la pantalla de bloqueo; hoy solo está en Inicio y en su pantalla.

## F5 etapa 2 (Android): impulsos y diario

- No hay «borrar todos mis datos»: el diario y los impulsos se quedan en el teléfono hasta desinstalar la app (carencia general, no solo de esta etapa).
- La base de datos no está cifrada; el diario es legible con acceso root o ADB.
- Las actividades para distraerse, los mensajes y el ritmo de respiración (4-4-6) son fijos y no se pueden cambiar ni añadir propias.
- El informe de impulsos (cuándo, a qué hora, qué desencadenó) queda para F6 (L7); hoy solo se guardan los datos.
- Las pruebas de capturas no ven la animación: el círculo de respiración se ha de mirar en un teléfono (fluidez y consumo de batería con la pantalla encendida 20 minutos).
- Los casos de abrir la app a mitad de una cuenta atrás (proceso muerto) y de pasar de medianoche con el diario abierto no tienen prueba automática.

## F5 etapa 3a (Android): insights e informe semanal

- Nada se ha visto con datos reales de un teléfono: el rato de uso seguido, el descanso más largo y la tendencia dependen de cómo reporte los eventos cada fabricante (algunos no emiten el fin de uso al apagar la pantalla).
- La primera semana tras instalar no hay semana anterior (el sistema ya no guarda sus eventos): el informe dice «sin datos» en la comparación.
- Un día sin uso no se distingue de un día que el sistema ya olvidó; no cuenta en los promedios.
- Los bloqueos de los días viejos se leen del historial de bloqueos (35 días), no de `day_usage`.
- La notificación del resumen semanal de los lunes sigue contando solo bloqueos; no abre el informe ni incluye el uso.

## F5 etapa 3b (Android): aviso de tiempo, recordatorios y ánimo

- Los recordatorios y la pregunta de ánimo solo salen con el servicio de accesibilidad activo y la pantalla encendida; con la protección apagada no suenan. Si falla en teléfonos con ahorro de batería agresivo, habrá que pasar a alarmas del sistema.
- Tocar un aviso no abre la pantalla correspondiente (hoy solo abre la app).
- El aviso de tiempo no distingue webs ni cuenta el tiempo con la pantalla apagada; se reinicia al reiniciar el servicio.
- No hay gráfica de ánimo ni cruce con rachas, impulsos o uso (queda para más adelante, con datos reales).
- Un recordatorio no se puede editar: se borra y se crea otro.

## F5 etapa 4 (Android): Bluetooth e icono

- La condición por Bluetooth no se ha probado con dispositivos reales (auriculares, coche, relojes): lo conectado al arrancar solo cubre audio y manos libres, y los fabricantes difieren en cuándo avisan de las desconexiones.
- Dos dispositivos con el mismo nombre cuentan como el mismo; si uno se desconecta antes que el otro, la lista pierde el nombre aunque el otro siga conectado.
- Los cuatro iconos disimulados se han dibujado a mano con vectores: hay que verlos en el lanzador de un teléfono real (el recorte redondo, el tema de colores del sistema, algunos lanzadores tardan en refrescar el cambio). No hay icono monocromo para los temas de Android 13.
- Cambiar el icono no cambia el nombre en Ajustes > Aplicaciones, en recientes ni en las notificaciones de FocusGuard.
- El suceso `icon_changed` requiere el servidor actualizado: con uno antiguo, el envío de ese suceso se rechaza y se descarta (no bloquea los demás).

## F5 etapa 5 (Android): modo parental

- No hay vínculo familiar propio: un padre con varios hijos es compañero de cada uno por separado, y la lista «personas a las que ayudas» (I3) sigue sin existir.
- El aviso «el hijo desinstaló la app» de I7 no existe: desinstalar sin permiso de administrador no se puede detectar desde el teléfono (solo el servidor podría notarlo por la falta de señales).
- La decisión del modo parental vive en `ChangeGuard` (depende de preferencias de Android) y no tiene prueba unitaria; se prueba en dispositivo. La regla pura (`ChangeRule`) sí está probada.
- No se ha probado con un compañero real: la aprobación por correo de cada cambio es incómoda si los cambios son muy frecuentes.

## Rediseño visual

- Los iconos del lanzador y la marca no se han visto en un teléfono real (recorte redondo y cuadrado, icono con tema, lanzadores de fabricantes).
- Solo Inicio, la bienvenida, la pantalla de bloqueo y los títulos de las pantallas principales estrenan insignias; el resto de pantallas heredan paleta e iconos pero no la nueva composición.
- Inicio repinta toda la pantalla cada segundo si hay una cuenta atrás (ya era así); convendría mover la hora a las tarjetas de sesión.
- Tipografía propia pendiente (hoy Roboto del sistema).

## F5 etapa 6: compañero mutuo

- Aprobar o rechazar solicitudes de las personas a las que ayudo se sigue haciendo desde el enlace del correo; la app solo cuenta las pendientes. Aprobar desde la propia app (y con aviso al teléfono del compañero) queda para más adelante.
- Si la persona a la que ayudo cambia el correo de su cuenta, deja de aparecer (el compañero se guarda por correo, no por cuenta).
- La lista solo se actualiza al abrir Cuenta o con «Comprobar de nuevo»: no hay aviso cuando alguien me elige compañero.
- Un correo reciclado (el compañero lo libera y otra persona lo registra y confirma) ve a quienes ese correo ayudaba y sus solicitudes pendientes; para evitarlo habría que atar al compañero a la cuenta, no al correo.
- Con varias personas que me eligieron y sin compañero, cada fila ofrece invitarla; solo cabe un compañero vivo, así que la segunda invitación se rechaza con el aviso del servidor.

## F5 etapa 7: sincronización entre dispositivos

- Los perfiles (con sus apps y webs) no se sincronizan: se pasan con la copia de seguridad. Sincronizarlos pide un identificador estable por perfil y una regla para borrados y cambios.
- Quitar una palabra o apagar un interruptor en un teléfono no llega a los otros (a propósito); para quitarla de todos hay que hacerlo en cada uno.
- No hay aviso de «se sincronizó con otro dispositivo»: los cambios aparecen en la siguiente sincronización (cada 6 horas o con el botón «Sincronizar ahora»).
- Si dos teléfonos eligen frases distintas la primera vez, el segundo recibe «la frase no es la de tus otros dispositivos»; no hay forma de recuperar un documento cifrado con una frase olvidada: se borra y se vuelve a crear.
- La frase de la sincronización vive en el teléfono (cifrada con el almacén de claves); quien tenga el teléfono desbloqueado y la app abierta puede sincronizar, pero no leerla desde Ajustes.
- Hoy cada repositorio de la cuenta se limpia a mano en el cierre y en el inicio de sesión (`AppModule`); una interfaz de «cosas de la cuenta» que se limpie sola en cada cambio de cuenta evitaría olvidos al añadir otro.
- Cada sincronización deriva la clave de PBKDF2 al descifrar y otra vez al cifrar, y baja el documento aunque no haya cambiado (GET condicional por versión).

## F5 etapa 8: consultas DNS

- Una app que usa su propio DNS cifrado a otro servidor, o una app excluida del filtro, no pasa por la VPN: sus consultas no salen en el historial.
- El bloqueo se reconoce por la dirección vacía en la respuesta; si se cambia el modo de bloqueo del resolvedor (por ejemplo a NXDOMAIN) hay que ajustar `DnsMessages.isBlocked`. Un dominio que resuelva de verdad a 0.0.0.0 cuenta como bloqueado.
- El historial va por dominio completo (sin agrupar por dominio principal), y las consultas del propio teléfono (sistema, servicios) también salen.
- Sin conexión con el servidor no hay total diario para el compañero (sale con el siguiente envío si el teléfono sigue con los avisos encendidos); el día que no se envíe, no se recupera.
- No hay borrado selectivo (un dominio): solo todo el historial.
- No se ha comprobado contra un AdGuard Home real que el bloqueo salga como REFUSED o dirección vacía (es lo que dice su documentación): si el historial muestra todo como permitido, empezar por ahí.
- `DnsMessages` recorre los nombres en tres sitios (`questionName`, `skipName`, `questionLength`); convendría un solo recorrido. Si el historial crece mucho, una tabla de totales por día evitaría recorrer toda la tabla al abrir la pantalla.

## F6 etapa 1: programa de recuperación

- El contenido de los siete pasos es mío (redactado desde cero) y no lo ha revisado ningún profesional; conviene que alguien con formación lo lea antes de ofrecerlo a más gente.
- Las respuestas de los pasos no se copian en la copia de seguridad ni se sincronizan: al cambiar de teléfono el programa empieza de cero.
- Los disparadores elegidos no se cruzan aún con los impulsos reales (eso llega con el informe L7).
- `RecoveryStore.complete` (comprobar el paso y la respuesta al guardar) se prueba por sus reglas puras; el guardado en Room se probará en dispositivo.

## F6 etapa 2: test de adicción

- Las diez preguntas y los tramos son míos, no de un instrumento validado: la puntuación orienta, no diagnostica; si el test va a ser una pieza seria, conviene que lo revise un profesional o sustituirlo por un cuestionario validado con permiso.
- La app no lee `GET /assessments`: el historial de otro teléfono no aparece (solo lo guardado aquí).
- El resultado no se copia en la copia de seguridad ni se sincroniza.

## F6 etapa 3: informe de impulsos

- Los impulsos no entran en la copia de seguridad ni se sincronizan: el informe es de este teléfono.
- El informe cuenta lo que el usuario contesta: un impulso sin cerrar pasada una hora cuenta como abandonado, y sin decir el desencadenante no entra en esa lista.
- El resumen para el compañero sale con el envío periódico (cada 6 horas) y entra en su siguiente informe diario; puede tardar hasta dos días en leerlo.
- Si el compañero apaga el resumen semanal de impulsos en sus preferencias, el servidor deja de guardarlo; el teléfono no se entera.
- Decisión para el usuario: el resumen semanal de impulsos envía, con su permiso, la hora más frecuente y el desencadenante principal. Si la regla de privacidad de `CLAUDE.md` se lee en estricto («solo qué se bloqueó y cuándo»), conviene recortarlo a solo los totales o ampliar la regla.
- Un resumen que el teléfono encola puede perderse si la cola de sucesos está llena de otros graves (límite de 200); la semana ya figura como enviada.

- (F6, /simplify) Los seis nombres de desencadenante están copiados en `server/src/notices.ts` (`URGE_TRIGGERS`) desde `UrgeTrigger`: si se añade o renombra uno en la app, hay que tocar el servidor o el correo pierde la línea. Falta una prueba que los compare.
- (F6, /simplify) `RecoveryStore.today()` y `JournalStore.today()` son el mismo paso a `StreakStore.today()`; `UrgeLog.calendarToday` usa el día natural (no el lógico con inicio de día). Decisión de producto: unificar o dejarlo dicho.
- (F6, /simplify) `takeQuiet` podría usar un índice parcial `WHERE type IN ('dns_counts','urge_report') AND NOT reported`; `GET /assessments` y `uploaded` no los usa la app.

## Pantalla de bloqueo, navegador y estadísticas (2026-10-28)

- Confirmar en un teléfono real: que `leaveBlockedPage` saca de la página bloqueada en Brave, Chrome, Firefox, Edge y Samsung Internet (el identificador de la barra y que acepte `ACTION_SET_TEXT` cambian de uno a otro; en Android 10 o inferior no se puede pulsar Intro sin teclado y se cae a «Inicio»).
- Confirmar que Estadísticas ya cuenta los bloqueos de hoy (hallazgo K1). Si no, mirar con `adb shell dumpsys dbinfo com.focusguard` si `block_events` recibe filas.
- Capturas Paparazzi de la pantalla de bloqueo con uno y con varios perfiles, y de un perfil de sistema: sin hacer.
- La pantalla de bloqueo ya no se cierra sola: queda decidir si hace falta una forma de salir cuando entra una llamada con la ventana abierta (hoy depende de «Volver» o del tope de 10 minutos).

## Notificación del compañero, contenido adulto y manga (2026-10-28)

- Probar en el teléfono: que al aceptar el compañero llega «Tu compañero aceptó» con la app en segundo plano, y que una instalación nueva trae «Contenido adulto» encendido.
- Añadir `manganato.com`, `mangakakalot.com` y otros dominios de manga a `server/data/own_domains.txt` cuando se confirme que siguen en uso.
- Sin prueba automática del valor por defecto de `adult_block_enabled` (necesita `Context`): comprobado a mano.

## Aplicar lo aprobado (2026-10-28)

- Probar en el teléfono: crear una solicitud, aprobarla desde el correo con la app en segundo plano y comprobar, sin abrir la app, que el cambio se aplica y llega «Tu compañero aprobó»; repetirlo con la app cerrada y con dos aprobaciones seguidas.
- Punto 10 de `CORRECCIONES.md` (desactivar el modo estricto con el compañero): sin hacer. Exige un método de salida nuevo en `ExitMethod` (`core-model`), `StrictMode.evaluateExit`, la serialización de `StrictConfig` (copia de seguridad, sincronización y `RestorePolicy`), la pantalla `StrictScreen` y una acción nueva en `ApprovalExecutor`; además, con el modo estricto activo `ChangeGuard` bloquea cualquier aplicación, así que esa acción tiene que ser la excepción.
- Punto 5 (ver y cancelar las pendientes con claridad, tarjeta visible en Protección/Inicio): sin hacer; hoy la lista sigue dentro de «Configurar».
- Seguridad: es el punto que decide si un cambio de protección ocurre sin que el usuario lo vea. Pasar `/security-review` antes de fusionar.

## Correos y páginas con el estilo de la app (2026-10-28)

- Ver los correos en clientes reales (Gmail, Outlook, Apple Mail, claro y oscuro): la comprobación fue con pruebas del HTML, no con un cliente. Pasar el correo por una herramienta de pruebas de clientes si hace falta.
- Cambiar un color en la app exige hacerlo también en `data/theme/tokens.json` (la prueba avisa); queda pendiente que la app lo lea del JSON al compilar y que `Theme.kt` deje de repetir los valores (una tarea de Gradle que genere `ThemeTokens.kt`).
- Tipografía y formas: el JSON lleva los radios; la tipografía de la app (pesos) no está en el JSON y los correos usan la fuente del sistema.
- Imagen de la marca en los correos (logo): sin hacer.
- Probar en producción tras el despliegue: pedir una aprobación y comprobar el correo y las páginas de aprobar/rechazar.

## Anti-desinstalación (punto 18 de `CORRECCIONES.md`), lo que falta (2026-10-28)

- Hecho: el administrador del dispositivo es imprescindible (permiso obligatorio y problema `REQUIRED`).
- Sin hacer: cerrar con el servicio de accesibilidad la pantalla de **desactivar el administrador** y las de desinstalar fuera de Ajustes (Play Store, instalador de paquetes, arrastrar el icono) aunque el modo estricto esté apagado, con salida por aprobación del compañero o espera (puntos 4 y 10); aviso al compañero al desactivar el administrador o apagar la VPN; reinstalar recuperando la protección desde la copia del servidor.
- Probar en el teléfono: que `adb shell pm uninstall com.focusguard` falla con `DELETE_FAILED_DEVICE_POLICY_MANAGER` una vez activo el administrador, y que la app pide el permiso al abrirla en una instalación que no lo tenía.
- Límites que hay que decir en la Ayuda: modo seguro, restablecimiento de fábrica y propietario del dispositivo (device owner).

## Solicitudes pendientes visibles (2026-10-28)

- Probar en el teléfono: crear una solicitud, comprobar que se ve al abrir Aprobaciones, cancelarla con y sin conexión y que el enlace del correo deja de valer (`GET /r/:token/approve` no la deja aprobar).
- Un historial corto de las ya resueltas (aprobadas, rechazadas, caducadas) sigue sin hacer.
- La tarjeta de Inicio no dice nada de las pendientes; solo la de Protección.
- Capturas Paparazzi de la pantalla de Aprobaciones: sin hacer (la pantalla usa `SecureWindowEffect`).

## Modo estricto con el compañero (2026-10-28)

- Probar en el teléfono con un compañero real: pedir desactivar con «Que mi compañero lo apruebe» (+ esperar 1 min), comprobar que llega el correo con botones, que al aprobar llega la notificación y se puede confirmar, y que cancelar la salida deja sin efecto el enlace del correo.
- Una salida que el compañero aprobó y el usuario no llegó a confirmar se queda marcada como aprobada hasta que se cancele; no caduca por sí sola. Ponerle un tope (p. ej. 10 minutos como el cooldown) es una mejora posible.
- Si el compañero se desvincula teniendo ese método, el modo estricto solo se puede pausar o desactivar tras vincular uno nuevo.
- Capturas Paparazzi de `StrictScreen`: sin regenerar (se hará junto con los selectores de tiempo del punto 9, que tocan la misma pantalla).

## Categorías que rellenan el perfil (2026-10-28)

- Probar en el teléfono: nuevo perfil → marcar «Redes sociales» y ver que salen las apps instaladas en «Apps» y las webs en «Webs» con el aviso; marcar y desmarcar rápido no deja entradas a medias.
- No hecho: que las apps instaladas **después** también aparezcan en la lista (hoy las bloquea la categoría pero no se ven como entrada) y que las listas que lleguen del servidor se sumen «sin pisar lo que el usuario quitó a mano». Ambas piden guardar exclusiones por perfil (columna nueva + migración + pasar por `Weakening`).
- La tabla de categorías sigue en unas 140 entradas; se ampliará con las plantillas del punto 15.
- `Weakening` sigue contando como «debilitar» desmarcar una categoría aunque sus entradas queden en la lista (conservador a propósito).

## Selectores de tiempo (2026-10-28)

- Probar en el teléfono: el selector de hora (reloj y teclado), el deslizador con TalkBack, y que «− +» respetan mínimos y máximos. Solo se han visto capturas (Paparazzi: `selectoresDeHorario`, `selectoresDeHorarioOscuro`, `selectorDeDuracion`, `bloqueMe`); la hora de «Hasta las…» de la captura depende de la zona horaria del PC que la grabó.
- No hecho del punto 9: la barra de 24 h **no se arrastra** (solo muestra el rango), no hay «Duplicar de otro día» ni varias franjas con un gesto, y el campo de minutos del límite de uso de un perfil sigue siendo un número tecleado (`LimitCard`).
- Las capturas de Paparazzi de `StrictScreen`, `SessionScreen` y `PanicScreen` siguen sin existir.

## Ayuda y asistente (2026-10-28)

- Probar en el teléfono: abrir Ajustes → Ayuda, buscar, usar «Abrir» desde cada ficha, el «?» de Modo estricto y Aprobaciones, y «Repetir el asistente de bienvenida» (debe volver al paso 1 sin cerrar la app).
- Ayuda: los textos están escritos a mano y no se generan del catálogo de funciones (`docs/`): la prueba solo comprueba una lista fija de fichas obligatorias. Faltan fichas para Block Me, el icono disimulado, los widgets, el modo parental, Hábitos y Diario, Recuperación y el test de adicción. El «?» solo está en dos pantallas; faltan horarios, perfil y contenido adulto.
- Asistente, no hecho: los pasos de compañero y de modo estricto **solo informan** (no abren la cuenta ni activan nada; se hace después), los permisos no tienen una pantalla cada uno ni explican «qué NO hace», y la prueba de bloqueo enseña la pantalla de ejemplo pero no abre de verdad una app elegida.
- El asistente repetido crea otro «Mi primer perfil» (no sustituye al anterior).
- Capturas Paparazzi nuevas: `ayuda`, `ayudaBuscando`, `bienvenidaObjetivo`, `bienvenidaAdulto`, `bienvenidaCompanero`, `bienvenidaEstricto`, `bienvenidaPrueba`; regrabadas `bienvenida`, `bienvenidaQueCuidar`, `bienvenidaCuando`, `bienvenidaPermisos`.

## Plantillas (2026-10-28)

- Probar en el teléfono: Inicio → Plantillas de perfil, crear una y ver que aparecen sus apps instaladas y sus webs; una de «permitir solo» con su horario; que sin `app/personal/plantillas.json` la app compila y no muestra «Para ti».
- **Los nombres de paquete de las plantillas personales son una primera versión sin comprobar contra el teléfono** (algunos son de memoria): una app con nombre de paquete equivocado simplemente no entra en el perfil. Con el teléfono conectado hay que cruzarlos con `pm list packages` y corregir `app/personal/plantillas.json` (que no está en git).
- Las plantillas personales llevan un límite común por perfil, no uno por app (el modelo de perfil solo tiene un límite compartido).
- No hecho del punto 14: «Sugerencias a partir de tu uso» dentro de la app (proponer perfiles con las apps más usadas de 7 días). Las plantillas personales se hicieron a mano con los datos de la sesión de pruebas.
- Las plantillas genéricas no incluyen las secciones dentro de apps (Shorts, Reels): se activan al editar el perfil.
- Una plantilla creada dos veces crea dos perfiles; no avisa de duplicados.

## Anti-desinstalación, segunda parte (2026-10-28)

- Hecho: la pantalla de desactivar el administrador se cierra salvo con un permiso aprobado (10 min). Los avisos al compañero de administrador, accesibilidad y DNS ya existían.
- Probar en el teléfono (lo único que no se puede saber sin él): que el texto de esa pantalla coincide en el Android 15 de Motorola (si no se cierra, hay que añadir sus frases a `deviceAdminPhrases`), que no se cierra la ficha normal de la app, que la aprobación abre la ventana y que `adb shell pm uninstall com.focusguard` falla con `DELETE_FAILED_DEVICE_POLICY_MANAGER`.
- No hecho: capa 3 (barreras en la ficha de Play Store, el instalador de paquetes de terceros, gestores de archivos y el gesto de arrastrar el icono), y capa 5 (reinstalar recuperando la protección desde la copia del servidor y avisando de «dispositivo nuevo»).
- Aviso conocido: el permiso de 10 minutos lo puede abrir cualquiera que tenga el teléfono desbloqueado cuando no hay aprobaciones activadas; con ellas (y más con compañero) cuesta de verdad.
- Capturas Paparazzi regrabadas: `proteccionClaro`, `proteccionOscuro`.

## Administrador y eventos fantasma (2026-10-29)

- Probar con Ayúdame activo (no se puede deshacer, así que lo prueba quien lo tenga a mano): que al volver al inicio ya no sale «Google no está en tu lista de permitidos» y que abrir la app de Google de verdad sigue bloqueándose.
- Otros paquetes que viven dentro del lanzador (widgets de otras apps) pueden dar el mismo evento fantasma; `WindowEvents` los cubre por regla general, no por nombre.
- Sin comprobar: `adb shell pm uninstall com.focusguard` con el administrador activo (debe fallar con `DELETE_FAILED_DEVICE_POLICY_MANAGER`); no se probó para no arriesgar los datos.

## Temporizador con apps permitidas (2026-10-29)

- Probar en el teléfono: Temporizador → «Elegir apps permitidas» → empezar → que la app elegida se puede abrir y el resto sale bloqueada, y que no se puede quitar antes de tiempo.
- Pendiente de decidir: un Temporizador con **horas** de bloqueo repetidas, o recordar las últimas apps permitidas para la próxima vez. Hoy se eligen cada vez.
- La maqueta y el manual (`docs/MANUAL.md`, rama `claude/manual`) siguen diciendo que el Temporizador te deja «dentro de una pantalla»: hay que actualizarlos.

## Respiración en Ayúdame (2026-10-29)

- Probar en el teléfono con un Ayúdame corto (1 minuto; no se puede deshacer): que la pantalla se queda con la cuenta atrás, que «Empezar» anima el círculo con cada ritmo, que se puede parar, y que la tarjeta de Ayúdame en Inicio abre esa pantalla.
- Sin hacer: vibración o sonido suave al cambiar de fase (hoy solo el círculo y el texto), recordar el último ritmo elegido, y ofrecer los mismos ritmos en «Impulso» (que sigue con el 4-4-6 fijo).
- La maqueta (`Propuesta: Ayúdame`) no muestra todavía la tarjeta de respiración.

## Respiración: lo que quedaba (2026-10-29)

- Hecho: vibración y sonido suaves, recordar ritmo y ajustes, mismos ritmos en Impulso, Ayúdame al principio de Inicio.
- Probar en el teléfono: que la vibración se nota y no molesta, que el sonido es suave, y que Impulso arranca solo con el último ritmo. El sonido usa un tono del sistema (no hay audio propio): si no gusta, se cambia por un archivo corto.
- Sin hacer: ritmos propios (elegir los segundos), y una duración fija del ejercicio («3 minutos»).

## Dormir (2026-10-29)

- Probar en el teléfono: abrir Dormir, elegir 7:00 y mirar «dentro de X», elegir «Otra hora» con el selector de Material, y **que el reloj de Motorola aparece como permitido** (si no se encuentra por «ver alarmas», hay que añadir su paquete). Con un Dormir corto de prueba (por ejemplo, a una hora dentro de 2 minutos) comprobar que la alarma suena aunque todo esté bloqueado.
- Sin hacer: **empezar más tarde** («en 30 o 60 minutos», como «pantallas fuera una hora antes de dormir»): exige un inicio diferido en el perfil de pánico, que hoy no existe; y **repetir cada noche** a la misma hora (hoy es un bloqueo suelto; la repetición ya la cubre un perfil «Descanso nocturno»).
- La maqueta ya tiene «Dormir» y «Dormir en curso»; el manual (`docs/MANUAL.md`, rama `claude/manual`) hay que actualizarlo.

## Arreglos del widget y condiciones de fin (2026-10-29)

- Probar en el teléfono: Bloquear ya con «Antes, si se cumple algo» → **cargador** (enchufarlo y ver que el bloqueo se levanta en unos segundos con su aviso), **Wi-Fi** (conectarse a la red elegida), **lugar** (llegar al círculo; con la ubicación activada) y **Bluetooth** (conectar el dispositivo). Probar también «Solo cuando acabe el tiempo» (que no sale «Terminar» y que un intento de borrarlo desde aprobaciones dice que no) y la **espera** (que «Terminar» sale al pasar el rato). Y que con el cargador ya puesto no deja empezar.
- Probar en el teléfono: con Ayúdame o Dormir activos, que el aviso de bloqueo dice «Ayúdame» o «Dormir» y no «Botón de pánico»; y que el widget enseña «25 min» y, al acabar, vuelve a «Nada bloqueado» sin números negativos.
- Sin hacer: un «ya se cumple» más amable que un error (por ejemplo, «el cargador ya está puesto: ¿empezar cuando lo desenchufes?»); decidir si un bloqueo levantado por una condición debe avisar al compañero (hoy no se le avisa); y mostrar en Inicio el lugar elegido con su nombre en vez de «un lugar».
- La maqueta (`Propuesta: cómo se puede terminar`) sigue siendo la de antes de programarlo y el manual (`claude/manual`) no lo menciona: hay que actualizarlos.

## Detección de imágenes en pantalla (2026-10-29)

- Hecho: la lógica pura (cascada, voto ponderado, pesos por fiabilidad, umbral por tasa de falsos avisos, compuertas, ventana de confirmación y frecuencia adaptable), con pruebas. No se ejecuta en la app.
- Sin hacer, por orden: (1) **modelos**: descargar y convertir a TensorFlow Lite GantMan (MobileNetV2, nivel 1) y NudeNet `320n` (nivel 2), con `open_nsfw` como tercer candidato si mejora los resultados; **comprobar sus licencias** antes de incluirlos y no meter archivos de terceros sin que el usuario los autorice; (2) **prototipo que solo registra**: captura con `AccessibilityService.takeScreenshot` (Android 11 o más; la app admite desde Android 8, así que hay que protegerlo), reduce a 224 px y puntúa, midiendo milisegundos por captura, batería y qué fracción de capturas llega al nivel 2; las apps con ventana segura devuelven la captura vacía; (3) **evaluación**: el usuario aporta imágenes de prueba con respuesta conocida y con ellas se fijan pesos y umbrales (los valores de serie son provisionales); (4) solo entonces **actuar**: tapar con la pantalla de bloqueo, con la opción de empezar solo avisando; (5) decidir si se avisa al compañero y con qué.
- Cuidados: el permiso de captura es muy invasivo, así que nada saldría del teléfono y no se guardarían imágenes; los pesos que el usuario pudiera cambiar a mano no deben poder debilitar el filtro con el modo estricto activo.
- Medidas hasta ahora: **ninguna**. Los tamaños y el consumo que se dieron al usuario son estimaciones.

### Modelos descargados para evaluar (2026-10-29)

Descargados **fuera del repositorio** (carpeta de trabajo de la sesión, nunca en git) y solo para evaluar en local; no se han ejecutado en el teléfono.

- **GantMan `nsfw_model`** (`mobilenet_v2_140_224.1.zip`, versión 1.2.0, 100,6 MB, SHA-256 `22c0892695929639c16ea302996b8f64df9c52e7a6c1d874c1de1047bfe109f7`). Licencia **MIT** (con la salvedad de que lleva material de terceros con otras licencias). Trae el modelo **ya en TensorFlow Lite**: `saved_model.tflite`, **16,55 MB en coma flotante** (MobileNetV2 con ancho 1,4 a 224 px; SHA-256 `380f98f7…fab40`). Cinco clases: dibujos, hentai, neutro, porno, sexy. Su propio registro de entrenamiento da un acierto de validación de entre el 91 y el 92 % (sobre sus datos, no los nuestros). **Corrección de lo dicho antes**: el archivo que se enviaría pesa unos 16,5 MB, no 3 a 4 MB; esa cifra solo valdría tras **cuantizarlo a 8 bits**, que el mismo paquete sugiere (la versión cuantizada para web pesa 4,1 MB), pero no se ha hecho ni medido.
- **NudeNet `320n`** (`320n.onnx`, v3.4-weights, 12,15 MB, SHA-256 `c15d8273adad2d0a92f014cc69ab2d6c311a06777a55545f2c4eb46f51911f0f`). Es un **detector** YOLOv8n (entrada 320 px; salida de cajas con 18 clases de partes del cuerpo), no un clasificador: para usarlo en la cascada habría que convertir sus detecciones en una sola probabilidad (por ejemplo, la mayor puntuación entre las clases «expuestas»). **Licencia AGPL-3.0**: el repositorio la declara y los metadatos del propio archivo dicen «AGPL-3.0 License» de Ultralytics. Incluirlo en un APK que se reparta obligaría a ofrecer el código fuente de toda la app. **No debe entrar en el APK sin una decisión expresa del usuario**; para uso privado sin repartir la app no se activa esa obligación, pero hay que tenerlo presente si el repositorio o el APK llegan a hacerse públicos. Alternativa de licencia permisiva ya identificada: `open_nsfw` de Yahoo (BSD 2-Clause, repositorio archivado desde 2018, unos 24 MB), no descargado todavía; también habría que buscar y comprobar otros modelos con licencia permisiva.
- La primera descarga de `320n.onnx` con `curl` devolvió una página HTML de GitHub en vez del archivo (51 KB); se rehízo con `gh release download` y el tamaño coincide con el publicado. Conviene verificar siempre el tamaño y la firma de lo descargado.
- Siguiente: ejecutar GantMan y NudeNet con imágenes **neutras** en el PC para comprobar el formato de entrada y salida y el mapeo a una probabilidad; luego medir tiempos en el teléfono. Para ejecutar el `.tflite` en el PC hace falta instalar un intérprete de TensorFlow Lite (descarga adicional, sin hacer: pendiente de autorización).

## Antes de publicar el código (2026-10-29)

- Elegir la licencia del proyecto (ver la decisión del 2026-10-29 en `docs/DECISIONES.md`): AGPL-3.0 si se quiere usar NudeNet y que las copias modificadas también queden abiertas; MIT o Apache-2.0 si se prefiere la máxima adopción, y entonces solo modelos con licencia permisiva. Añadir el `LICENSE` y los avisos de terceros que pidan las licencias de las dependencias y los modelos.
- Limpiar el historial de git de todo lo personal (el commit con `app/personal/plantillas.json` en `claude/correcciones`) y repasar que no quede ninguna clave, correo ni dirección propia. Hoy `gitleaks` no encontró secretos, pero eso no cubre datos personales.
- Revisar el nombre, el icono y los textos por si coinciden con marcas de terceros, y escribir un `README` pensado para quien llega de fuera.

### Registro experimental de imágenes: lo que se vio al compilar (2026-10-29)

- El APK con el modelo pesa **42 MB** (antes 9,9 MB): 16,6 MB del modelo sin cuantizar y unos 14 MB de bibliotecas nativas de TensorFlow Lite para cuatro arquitecturas (arm64 solo es 3,3 MB). Limitar el APK a `arm64-v8a` (y `armeabi-v7a` si hiciera falta) quitaría unos 10 MB; cuantizar el modelo a 8 bits, unos 12 MB más. Ninguna de las dos cosas está hecha.
- **No probado en el teléfono**: la conexión por USB se cayó al ir a instalar. Falta comprobar que el servicio de accesibilidad sigue encendido tras actualizar (puede que Android pida concederlo otra vez por el permiso nuevo de captura), que la captura funciona, que las puntuaciones de pantallas normales son bajas (si no, probar el rango de entrada −1 a 1), los milisegundos por captura y por modelo, y que las apps con ventana segura devuelven un error y no una imagen.
- Con las pantallas del propio teléfono solo se puede comprobar que el circuito funciona y que lo normal puntúa bajo; la exactitud con contenido explícito la tiene que valorar el usuario con sus imágenes de prueba.

### Primeras medidas en el teléfono (2026-10-29, Motorola edge 40 neo, 0.5.7)

- **Instalar** la 0.5.7 encima de la 0.5.6 con `adb install -r` **no apagó el servicio de accesibilidad** (seguía en `enabled_accessibility_services`) aunque se añadió `canTakeScreenshot`. No hizo falta volver a concederlo.
- **El circuito funciona**: `takeScreenshot` devolvió imagen, se redujo a 224 px y el modelo puntuó. Tras unos minutos con pantallas del propio teléfono (Ajustes, FocusGuard) hubo 5 intentos: 2 puntuadas y 3 saltadas (primer plano en una app de confianza), ninguna fallida.
- **Tiempos medidos**: captura entre 147 y 172 ms de media; modelo unos **106 ms** de media (107 ms el máximo) con 2 hilos y el modelo sin cuantizar. Es unas tres veces más de lo que se estimó (15 a 40 ms). Con una captura cada 40 s son unos 0,25 s de CPU por captura (menos del 1 % del tiempo); la batería sigue siendo una cuenta aproximada, no medida: unas 360 capturas en 4 h de pantalla serían del orden de 0,3 % de batería al día. Cuantizar el modelo a 8 bits o usar la GPU lo acortaría; sin hacer.
- **Puntuaciones**: todas en el primer tramo (0 a 0,1), como corresponde a pantallas normales. **Eso no demuestra que el preprocesado sea el correcto**: con un modelo mal alimentado también podrían salir valores bajos. Hace falta un control positivo con imágenes de prueba del usuario, comparando la entrada entre 0 y 1 con la de −1 a 1. Sin hacer.
- No se probó una app con ventana protegida ni se anotó el registro de `logcat` (la etiqueta `FGimg` no salió con el filtro usado); los contadores de la pantalla de Ajustes sí funcionan.
- Estado de la cascada: **solo el nivel 1 (GantMan) está en la app**. NudeNet está descargado pero no integrado, y `open_nsfw` ni siquiera descargado.

### Ritmo según la app (2026-10-29)

- Hecho y probado con pruebas unitarias (62 entre `ImageCapturePlanTest`, `CaptureRiskTest`, `ImageEnsembleTest`, `ModelReliabilityTest` y `GantManOutputTest`; todas pasan): riesgo por app y por web, subida gradual, bajada de golpe, ráfaga y memoria acotada. **No probado en el teléfono**: la conexión por USB se cayó antes de instalar la 0.5.8.
- Por probar: en Chrome con una web neutra debe salir «una captura cada 80 s (navegador, más espaciado)»; en Ajustes de FocusGuard, «ritmo normal»; y que tras unas capturas seguras el número crece. Con una app de redes o vídeo debería salir «más seguido»; no lo probé a propósito para no abrir cuentas ni recomendaciones del usuario.
- Sin hacer: que el usuario pueda elegir qué categorías cuentan como de riesgo (hoy fijas en el código), y que se puedan marcar apps concretas como «de poco riesgo».

### Primera evaluación con imágenes del usuario (2026-10-29)

Carpeta de 83 imágenes aportadas por el usuario y ordenadas por él: **41 «seguras», 22 «explícitas» y 20 «dudosas»**. Se puntuaron con un programa en el PC (nadie las abrió ni las vio); los resultados por imagen están solo en la carpeta de trabajo, no en el repositorio. Muestra pequeña y los umbrales se eligieron sobre los mismos datos, así que **las cifras son orientativas, no una medición fiable**.

- **Preprocesado de GantMan**: la entrada **entre 0 y 1 (la de la app) es la correcta**. Frente a −1..1 da más confianza (0,76 contra 0,67), mejor reconocimiento de las seguras como «neutro» (media 0,78) y menos falsos avisos con 0,5 (3 de 41 contra 8). Queda comprobado lo que estaba pendiente.
- **GantMan solo** (porno + hentai): con 0,5 marca 17 de 22 explícitas pero también **3 de 41 seguras**; la más alta de las seguras llega a 0,83. Para no tener falsos avisos en esta muestra hay que subir el umbral por encima de 0,83 y entonces detecta 10 de 22.
- **NudeNet solo** (partes íntimas expuestas): la segura más alta es 0,16; con el umbral justo por encima detecta 13 de 22 explícitas y 2 de 20 dudosas.
- **Voto ponderado de los dos** (media de logits, pesos 0,70 y 0,59 por el índice de Youden): sin falsos avisos en las 41 seguras (umbral justo por encima de 0,20) detecta **18 de 22 explícitas y 9 de 20 dudosas**, claramente mejor que cada uno por separado. Funciona porque NudeNet casi siempre da 0 en las seguras que GantMan se equivoca en marcar. El coste: dos explícitas que GantMan ve (0,77 y 0,60) y NudeNet no quedan fuera.
- **Piso para NudeNet** (que un «no detecto nada» no pese tanto): no mejoró nada en esta muestra; la media de logits sin más fue la mejor.
- **La clase «sexy» de GantMan separa las dudosas**: 6 de 20 dudosas la superan con 0,5, y ninguna segura; solo 1 de 22 explícitas. Sugiere un **segundo nivel «sugerente»** aparte del explícito, para vigilar más de cerca o avisar con otra intensidad. No está hecho.
- Quedan fuera del conjunto de tipo explícito unas pocas imágenes que ambos modelos puntúan casi a cero; si fueran de verdad explícitas serían falsos negativos de los dos.
- Sin hacer: poner el umbral y los pesos reales en el código (hoy son provisionales), integrar NudeNet en la app (es AGPL-3.0, ver arriba), y repetir la evaluación con más imágenes y con un umbral elegido en una parte de ellas y comprobado en otra.

### Tercer modelo: `open_nsfw` (2026-10-29)

Mismas 83 imágenes del usuario (41 seguras, 22 explícitas, 20 dudosas), puntuadas con un programa; nadie las vio. Muestra pequeña: orientativo. **Archivo usado**: `open-nsfw.onnx` (23,6 MB, SHA-256 `864bb37b…305fa`), extraído de `opennsfw_standalone-0.0.6-py3-none-any.whl` de PyPI (21,9 MB; envoltorio con licencia MIT; modelo de Yahoo con licencia BSD 2-Clause). Se descargó solo la rueda y se ejecutó el modelo con `onnxruntime`; el paquete no se instaló ni se ejecutó su código. Preprocesado de la app (pantalla entera a 224, valores en BGR menos la media del conjunto); el del paquete (recorte central y paso por JPEG) dio un Youden algo mejor (0,56 contra 0,52) pero no se puede replicar sin más en la app.

- **Solo**: es el más flojo de los tres. Índice de Youden 0,52, 3 de 41 seguras con 0,5 o más, y sin falsos avisos detecta 8 de 22 explícitas y 2 de 20 dudosas.
- **Umbral elegido con la mitad de las seguras y probado en la otra mitad** (200 sorteos; tasa de falsos avisos en seguras no vistas casi igual en todos, de 4,4 a 5,1 %): explícitas detectadas, GantMan solo 59 %, NudeNet solo 68 %, `open_nsfw` solo 44 %, **GantMan + NudeNet 82 %**, GantMan + `open_nsfw` 70 %, NudeNet + `open_nsfw` 77 %, **los tres 82 %**. Dudosas detectadas: GantMan + NudeNet 47 %, los tres 48 %.
- **Conclusión**: sumar `open_nsfw` a GantMan + NudeNet **no mejora** (la diferencia es ruido) y costaría unos 24 MB más y un motor ONNX en el teléfono. **Si el proyecto acaba con una licencia permisiva y NudeNet (AGPL-3.0) no puede ir dentro, GantMan + `open_nsfw` es la mejor combinación permisiva**: sube de 59 % a 70 % sobre GantMan solo. Si el proyecto es AGPL-3.0, GantMan + NudeNet basta.
- Con un umbral que deja pasar alrededor de un 5 % de capturas seguras, hace falta la confirmación de 2 de 3 capturas (ya está en la lógica) para que los falsos avisos no molesten.
- Sin hacer: decidir la licencia, cuantizar o convertir el modelo elegido para el teléfono, y repetir la evaluación con más imágenes.

### Segundo nivel «sugerente» con las demás clases (2026-10-29)

Decisión del usuario: **`open_nsfw` queda fuera**; se sigue con GantMan + NudeNet (con lo que, si el proyecto se publica, tendrá que ser AGPL-3.0). Mismas 83 imágenes; orientativo, muestra pequeña.

- **Clases usadas hasta ahora**: de GantMan solo porno + hentai; de NudeNet solo el máximo de cinco partes **expuestas**. Se probó añadir el resto.
- **Señales sueltas** (detección sin falsos avisos en las 41 seguras): GantMan «sexy» 7 de 20 dudosas (1 de 22 explícitas); NudeNet partes **cubiertas** (pecho, glúteos, genitales y ano cubiertos) 9 de 20 dudosas (3 de 22 explícitas); «dibujos» no aporta nada (hentai ya está en lo explícito); partes «leves» de NudeNet (vientre, axilas, pies, pecho masculino) detectan 12 de 20 dudosas pero son ruidosas (una segura llega a 0,47) y no se usan.
- **Nivel sugerente** = voto ponderado de «sexy» (peso 0,30) y cubiertas (peso 0,70; pesos por Youden contra las dudosas): detecta 13 de 20 dudosas sin falsos avisos (umbral 0,34).
- **Dos niveles juntos** (explícito O sugerente, cada umbral justo por encima de su segura más alta): 0 de 41 seguras marcadas, **20 de 22 explícitas y 17 de 20 dudosas**.
- **Con la prueba honesta** (umbral con la mitad de las seguras, prueba en la otra mitad): solo explícito, falsos avisos 4,4 %, explícitas 82 %, dudosas 47 %; **explícito + sugerente, falsos avisos 9,8 %, explícitas 91 %, dudosas 91 %**. Es decir, el nivel sugerente duplica los falsos avisos a cambio de casi duplicar lo que detecta de las dudosas.
- **Decisión de diseño**: el nivel sugerente no actúa: solo cuenta como «dudoso» (vigilar más de cerca con el ritmo corto y, si se quiere, avisar), y solo el nivel explícito, tras la confirmación de 2 de 3, tapa la pantalla. Así su cuota de falsos avisos no molesta.
- Sin hacer: ponerlo en el código (`ImageResult` con un nivel sugerente y los pesos y umbrales), integrar NudeNet en la app (modelo ONNX de YOLOv8: hace falta un motor ONNX en Android o convertirlo a TensorFlow Lite), y repetir con más imágenes.

### Sensibilidad y nivel sugerente (2026-10-29)

- Hecho y probado con pruebas unitarias (`ImageAssessmentTest`: niveles, umbrales válidos y decrecientes con la sensibilidad, que bajar debilita y subir no, y que un modelo que falla no anula al otro). **No probado en el teléfono**: la conexión por USB se cayó antes de instalar la 0.5.9.
- Por probar: que el deslizador cambia y se queda en su sitio al subir; **que bajarlo con un compañero vinculado crea una solicitud y el deslizador vuelve a su valor** (y que sin compañero y sin modo estricto sí baja); que con el modo estricto activo no baja; que el compañero recibe el texto de la solicitud; y que al aprobarla el valor cambia.
- Sin hacer: poner NudeNet en la app (modelo ONNX de YOLOv8; hace falta un motor ONNX en Android o convertirlo a TensorFlow Lite) y recalcular entonces los umbrales de la sensibilidad para los dos modelos; que el nivel sugerente pueda avisar con una notificación (hoy solo vigila más de cerca); y repetir la calibración con más imágenes.

### Sensibilidad probada en el teléfono (2026-10-29, 0.5.9)

- Instalada la 0.5.9 encima de la anterior con `adb install -r`: el servicio de accesibilidad sigue encendido y el registro sigue en marcha. Medidas del registro: captura entre 112 y 220 ms, modelo entre 80 y 90 ms.
- El deslizador muestra cinco pasos con «Media» de serie. **Subir** de Media a Máxima: se aplicó al soltar. **Bajar** de Máxima a Baja en este teléfono (sin cuenta iniciada, sin compañero y sin modo estricto): se aplicó. Se dejó de nuevo en Media.
- **No probado**: bajar con un compañero vinculado (este teléfono no tiene cuenta iniciada, así que no hay compañero y habría enviado una solicitud real); bajar con el modo estricto activo; y que el compañero reciba la solicitud y, al aprobarla, el valor cambie. La lógica de `ImageSensitivity.weakens` tiene pruebas unitarias, pero el camino por `ChangeGuard` con `viaPartner` solo se ha comprobado leyendo el código.
- Para llevar NudeNet al teléfono: el motor ONNX Runtime para Android (`onnxruntime-android` 1.31.1, 55 MB el AAR con cuatro arquitecturas) pesa mucho más que TensorFlow Lite, que ya está en la app (el AAR de `tensorflow-lite` 2.16.1 son 6,2 MB). Conviene convertir NudeNet a TensorFlow Lite y comprobar con las mismas 83 imágenes que sus puntuaciones no cambian.

### Detección de imágenes que bloquea y avisa (2026-10-29, 0.5.11)

- Hecho y probado con pruebas unitarias (`ImageResponderTest`, `ImageAssessmentTest`): lo explícito bloquea (al momento si es claro; si es dudoso, con 2 de 3 capturas), lo sugerente ofrece bloquear con una notificación como mucho una cada N minutos (1, 2, 5, 10, 15 o 30; de serie 5), y apagar la detección o bajar su sensibilidad pasa por `ChangeGuard`.
- Probado en el teléfono: la tarjeta nueva (interruptor «Detectar imágenes», minutos de los avisos, sensibilidad); apagar y encender sin compañero ni modo estricto funciona. **No probado**: que lo explícito bloquee de verdad, que la notificación salga con su botón «Bloquear ahora» y que el botón cierre la app (hace falta contenido que el modelo marque; lo prueba el usuario), apagar con un compañero vinculado y con el modo estricto, y que el compañero reciba la solicitud.
- **NudeNet convertido a TensorFlow Lite**: en WSL (Ubuntu 24.04, entorno aislado `~/conv`) con Ultralytics 8.4 (formato `litert`) se obtuvo `320n_w8a32.tflite`, **3,25 MB** (pesos de 8 bits, entrada `[1,3,320,320]` en coma flotante, salida `[1,22,2100]`). La variante de 32 bits falló por un error de las herramientas (`duplicate registrations for aten.miopen_batch_norm.default`). **Comparado con la versión ONNX sobre las 83 imágenes: correlación 0,999 en las partes expuestas y en las cubiertas, diferencia media 0,006 y máxima 0,058**; con el umbral justo por encima de la segura más alta detecta lo mismo (13 a 15 de 22 explícitas; la diferencia es ruido). Sirve como sustituto del ONNX. Los archivos están solo en la carpeta de trabajo, no en el repositorio (NudeNet es AGPL-3.0).
- Sigue sin hacer, en este orden: (1) poner `320n_w8a32.tflite` en `app/modelos/models/` (ignorado por git) e integrarlo como segundo nivel de la cascada (preparar la imagen como NudeNet: cuadrado con relleno negro abajo y a la derecha, 320 px, 0 a 1, canales primero; tomar la mayor puntuación de las cinco partes expuestas para lo explícito y de las cuatro cubiertas para lo sugerente); (2) recalcular los umbrales de la sensibilidad para el voto de los dos modelos (con las 83 imágenes: explícito 0,20 sin falsos avisos y sugerente 0,34, como punto de partida) y los pesos (GantMan 0,70 y 0,59 NudeNet para explícito; «sexy» 0,30 y cubiertas 0,70 para sugerente); (3) repetir la evaluación con más imágenes y un umbral elegido en una parte y comprobado en otra; (4) borrar el entorno `~/conv` de WSL cuando ya no haga falta.

## Pendientes de la próxima semana (decididos el 2026-10-29; no se hacen ahora)

Recordatorio puesto en el calendario del usuario para el **martes 3 de noviembre de 2026 a las 8:00** (cuando se reinicia su sesión semanal). Por orden:

1. **Ayuda al 100 %**: añadir a la sección de Ayuda (`HelpTopics`) todas las funciones nuevas (detección de imágenes con sus dos niveles, la sensibilidad y su protección, los avisos y el botón «Bloquear ahora», cómo se apaga y qué lo protege, las condiciones para terminar un Bloquear ya, Dormir, Ayúdame con respiración, los widgets, las plantillas, el anti-desinstalación, el ritmo de captura según app y web…). Objetivo: que quede explicado **el 100 % de las funciones de la aplicación**; `HelpTopicsTest` ya falla si falta la ficha de una función obligatoria: ampliar su lista.
2. **Subir todo al repositorio y hacer `push` a `main`**: hoy hay una pila de ramas (`claude/…`) con PR abiertos sin fusionar; fusionarlas en orden, resolver conflictos y dejar `main` al día. **Solo cuando el usuario lo confirme ese día**, y revisando antes que no entre ningún modelo de terceros ni archivo personal (`app/modelos/`, `app/personal/`).
3. **README al día con instrucciones para instalarlo todo**: servidor (Docker, `.env`, nginx o Caddy, copias de seguridad, Firebase para las notificaciones push, correo con Resend), compilar y firmar la app (JDK 17, Android SDK, almacén de claves), instalar en el teléfono y los modelos de imágenes (dónde ponerlos y de dónde se bajan). **Incluir los cambios necesarios para cada servicio** (servidor, DNS/AdGuard, Firebase, correo, dominio y certificados) **y para cada teléfono** (permisos de accesibilidad, uso, superposición, batería sin restricciones, administrador del dispositivo, ubicación y Bluetooth; particularidades de Xiaomi, Samsung, Motorola, Pixel y Huawei; Android 11 o más para la detección de imágenes).
4. **Poner la licencia** para hacer público el repositorio. El usuario dice que ya había indicado cuál; en la conversación no quedó una elección expresa, pero al decidir que se siguen usando GantMan (MIT) y NudeNet (**AGPL-3.0**) lo coherente es **AGPL-3.0**. **Confirmarlo con el usuario antes de añadir el `LICENSE`**, junto con los avisos de terceros y la limpieza del historial (el commit con `app/personal/plantillas.json` en `claude/correcciones`).
5. **Instalar la skill `/brag` y crear una presentación** de la aplicación.
6. **Comparar con la competencia**: todas las características de esta app gratuita contra lo que hay en el mercado, **gratis y de pago** (Covenant Eyes, Accountable2You, Bark, Qustodio, Canopy, Net Nanny, Forest, Freedom, Opal, AppBlock, Digital Wellbeing, Family Link, Block Me, Ever Accountable, etc.), con una tabla por función y lo que esta app no tiene. Buscar y citar fuentes; no inventar características.

### Vigilancia y NudeNet en la app (2026-10-29, 0.5.12)

- Hecho y probado con pruebas unitarias: la vigilancia (`ImageVigilanceTest`: tiempos, renovación, alcance, intensidad, ajustes, qué debilita), la salida de NudeNet (`NudeNetOutputTest`: qué clases cuentan) y los umbrales por conjunto de modelos. Compila y el APK de release se genera (45 MB, con los dos modelos). **No probado en el teléfono**: se desconectó antes de instalar la 0.5.12.
- Por probar en el teléfono, por orden: (1) **que NudeNet carga** con el motor TensorFlow Lite 2.16.1 de la app (el modelo lo generó una versión más nueva de las herramientas y podría usar operaciones que este motor no conoce; si falla, la tarjeta dirá «NudeNet no está en esta versión» y el registro del sistema, etiqueta `FGimg`, dirá por qué; la solución sería subir el motor a LiteRT); (2) que las puntuaciones de pantallas normales siguen siendo bajas y cuánto tarda cada captura con los dos modelos (antes 80 a 90 ms con GantMan); (3) que con un contenido que NudeNet marque la tarjeta cuenta explícitas o sugerentes; (4) **la prueba de paridad**: poner en el teléfono una captura de las «seguras» con puntuación de NudeNet distinta de cero en el PC y comparar con lo que da la app, por si la preparación de la imagen en Android difiere de la del PC; (5) que tras un bloqueo la tarjeta enseña «Vigilancia activa» y el ritmo baja; (6) que bajar la vigilancia con un compañero vinculado o con el modo estricto no se aplica.
- Sin hacer: pasar la prueba de paridad con muchas imágenes en el teléfono (hoy solo se compara en el PC); medir el consumo de batería con los dos modelos; recalcular pesos y umbrales con más imágenes; y que la vigilancia también acorte los tiempos de apps de confianza (hoy no se capturan nunca).

### Vigilancia y NudeNet probados en el teléfono (2026-10-29, 0.5.16, Motorola edge 40 neo)

- **NudeNet carga y corre** con el motor TensorFlow Lite 2.16.1 de la app (el temor de que el modelo convertido usara operaciones desconocidas no se cumplió). Con los dos modelos, una captura tarda unos **90 a 340 ms** de puntuación (la primera, con la carga, 450 ms; lo habitual, 100 a 200 ms) y unos 50 a 130 ms de captura. NudeNet delega 190 de 256 nodos en XNNPACK (103 particiones): se podría acelerar, sin hacer.
- **Paridad con el PC**, con una captura «segura» del usuario mostrada en Google Fotos y puntuada por la app: NudeNet partes expuestas 0,006 (PC 0,005) y cubiertas 0,136 a 0,145 (PC 0,116). Coinciden. GantMan dio 0,63 a 0,68 (PC 0,83): menos parecido, esperable porque en el teléfono la captura incluye la barra de estado y la interfaz del visor y GantMan recibe la pantalla aplastada a 224×224; no se investigó más.
- **Fallo encontrado y arreglado**: el bloqueo por imágenes **se descartaba en silencio** cuando el servicio aún no conocía la app en primer plano (por ejemplo, justo tras arrancar), porque `currentState` solo se rellena con el primer cambio de ventana. Ahora se pregunta a la ventana activa como alternativa (`foregroundState()`). Con el arreglo, la captura marcada como explícita dos veces seguidas bloqueó (registro: `decisión=BLOCK`) y llevó al inicio.
- **La sensibilidad «Máxima» marca imágenes seguras como explícitas** (el voto conjunto de una imagen segura salió en 0,12 y el umbral de Máxima es 0,10), tal como se calculó: es el paso que deja pasar cerca del 20 % de las seguras. Con ese paso y la confirmación de 2 capturas, esa imagen se bloqueó. No es un fallo, pero conviene que el usuario lo sepa: Media (0,21) no marcó ninguna segura en la muestra.
- **Vigilancia verificada**: tras un bloqueo, 15 minutos de vigilancia (en el registro, «vigilancia=851s» y bajando); en el navegador el tiempo entre capturas pasó de 68 s a **34 s** (la mitad, intensidad media), tras la ráfaga de 6,8 s que sigue a todo lo sospechoso. La tarjeta enseña «Bloqueos» y «avisos enviados» (la línea de contadores no salía por un error mío, ya arreglado) y «Vigilancia activa: quedan N min» (también faltaba y ya está).
- **Limitación**: la vigilancia y la cuenta de marcas viven **en memoria**; si el servicio de accesibilidad se reinicia (actualización, el sistema lo mata, o una herramienta de pruebas como `uiautomator`, que desconecta un momento los servicios de accesibilidad), se pierden y la vigilancia vuelve a empezar de cero. Posible mejora: guardar hasta cuándo dura la vigilancia con la hora de pared.
- Sin probar: la notificación de lo sugerente con su botón «Bloquear ahora» (hace falta contenido que dé «sugerente»), que el compañero reciba el aviso del bloqueo, y bajar la vigilancia o apagar la detección con un compañero vinculado o con el modo estricto.

### Modo dinámico (2026-10-29, 0.5.18)

- Hecho y probado con pruebas unitarias (`ImageAdaptationTest`: niveles por veces y periodo, que baja con el tiempo, tope, límite exacto del periodo, que no cuenta el modo apagado ni las horas futuras, factor de tiempo, sensibilidad que nunca baja de la elegida, duración de la vigilancia con tope, ajustes y qué debilita, historial con tope y limpieza, y la vigilancia recuperable). Compila y el APK se genera. **No probado en el teléfono**: se desconectó antes de instalar la 0.5.18.
- Por probar: (1) que un bloqueo sube el nivel a 1 (en el registro, `nivel=1`) y la tarjeta enseña «Modo dinámico»; (2) con tres bloqueos, nivel 2; (3) que **tras forzar un reinicio del servicio** (basta un `uiautomator dump`) la vigilancia y el nivel siguen (no se pierden); (4) que pasado el periodo el nivel baja (se puede comprobar eligiendo 6 h y esperando, o probando con la hora); (5) que la sensibilidad aplicada sube un paso por nivel (el usuario la tiene en «Máxima», que ya es el tope: hay que bajarla a «Media» para verlo) y vuelve; (6) apagar el modo o acortar el periodo con compañero o modo estricto.
- Sin hacer: que el historial de incidentes se pueda ver (hoy solo el recuento en la tarjeta); distinguir un bloqueo claro de uno dudoso al contarlo; y un tope de falsos avisos que impida que una racha de falsos bloqueos suba la sensibilidad hasta «Máxima».

- 2026-10-29, prueba en el teléfono del modo dinámico: se encontró y corrigió un fallo del ritmo. Con la pantalla quieta, una captura dudosa se reutilizaba («sin cambios») y cada reutilización renovaba la ráfaga de capturas cada 3 s, así que el ritmo no volvía a crecer. Ahora un resultado reutilizado no renueva la ráfaga (`CaptureScheduler.record(..., fresh)`, con prueba) y en el teléfono el ritmo vuelve a crecer (17 s, 25,5 s, 51 s). **Sigue sin probarse en el teléfono** que un bloqueo suba el nivel a 1 o 2, que sobreviva a un reinicio del servicio y que suba la sensibilidad aplicada: la imagen de prueba salió segura en esta pasada y hace falta una pantalla que el programa marque como explícita.
- 2026-10-29, segunda pasada en el teléfono (0.5.18): con una imagen que el programa marca como explícita (0,94) se bloqueó, el nivel pasó a 1 y, con más bloqueos, a 2; la vigilancia duró 420 s en el nivel 1 y 600 s en el 2 (5 min más la mitad por nivel), como dice la regla. Sigue sin comprobarse: que la sensibilidad aplicada suba un paso por nivel (el usuario la tiene en «Máxima», el tope), que el nivel baje al pasar el periodo, el reinicio real del servicio y el modo con compañero o estricto.

### 2026-11-03 (0.5.19)

- Sin probar en el teléfono (estaba desconectado): el ritmo nuevo (1 s durante 2 minutos y luego creciendo; ver en el registro `FGimg` los intervalos y medir el gasto de batería), que se cierre la pantalla de accesibilidad de FocusGuard sin permiso y se abra con él, y que la pantalla de bloqueo adulto enseñe las palabras.
- Hecho el 2026-11-04 (0.5.21): lista de apps cuyo texto no lee el filtro adulto (de serie las de notas y productividad; las del usuario con aprobación). Probado en el teléfono solo que Obsidian ya no se bloquea al abrirlo; sin probar la pantalla de elegir apps ni la aprobación con compañero.

### Revisión de «varios compañeros» (2026-11-04, `/code-review` alto)

- Corregido: el informe diario gastaba los totales de DNS e impulsos con el primer compañero (ahora se marcan como contados cuando todos los compañeros con informe lo recibieron); «Personas a las que ayudas» solo enseñaba las solicitudes pendientes del compañero principal; `DESPLIEGUE.md` decía migraciones 1 a 11. Con pruebas.
- Sin hacer, a propósito: (1) un **rechazo** de uno cierra la solicitud para todos, aunque «basta con que uno apruebe»; hay que decidir si un rechazo debe esperar a los demás o a que caduque; (2) la solicitud sigue guardando un `partner_id` «principal» y los demás destinatarios solo viven en los tokens: una tabla `request_recipients` sería más limpia; (3) en el resumen de bloqueos, un compañero que aún estaba dentro de su espera de 10 minutos pierde esos bloqueos cuando otro sí los recibe; (4) con varios compañeros, la tarjeta de Cuenta enseña dos botones de «Pedir que me quite» (el del principal y el de la lista).
- Sin pasar: `/simplify` y `/security-review` completos (este último necesita una sesión cuyo directorio sea el repositorio). Las revisiones de seguridad pendientes de #10, #12, #14 y #19 siguen sin hacerse.

### Versión 0.6.0 (2026-11-08)

- Hecho y probado en el teléfono (Motorola edge 40 neo): límite «Cada app aparte» con su notificación de lo que queda, candado de perfil con espera, desinstalación aprobada por el compañero (rechazo y aprobación por correo) y los nuevos valores de la detección de imágenes. Pruebas unitarias de la app y del servidor pasan (1016+ y 166).
- Sin probar en el teléfono: los demás métodos de salida del candado (cargador, PIN, «pedirlo y esperar», compañero); la espera previa de 24 h o 72 h antes de mandar el correo de desinstalación.
- Sin hacer, a propósito: el candado no entra en las copias de seguridad ni en la sincronización; «apagar todos los perfiles» se detiene en el primero con candado; si el alcance de un límite cambia con el contador a medias, el contador compartido queda sin usar.
- **Correos con diseño**: el HTML no llegaba por la cola de envío; corregido (migración 13). Falta ver cómo se pintan en Gmail, Outlook y Apple Mail reales y, sobre todo, en modo oscuro.
- **Detección de imágenes**: los umbrales de los pasos 4 y 5 y los valores de serie salen de 13 capturas de Instagram (falsos avisos) puntuadas en el PC; la carpeta de 83 imágenes etiquetadas ya no existe, así que no se midió cuántas detecciones reales se pierden. Hay un círculo vicioso por decidir: cada falso bloqueo cuenta como incidente, sube el nivel del modo dinámico y con él la sensibilidad. Opciones: que solo cuenten los bloqueos muy claros, o que el modo dinámico no suba la sensibilidad.
- El servidor del VPS y la rama `claude/deteccion-de-imagenes` no se habían fusionado con `main` hasta este release.
