# Especificación de comportamiento

Descripción de lo que la app debe hacer. Los números son valores iniciales razonables, ajustables. Donde dice "decisión abierta" hay que elegir y anotarlo en `DECISIONES.md`.

## 1. Detección

### 1.1 App en primer plano
- Base: servicio de accesibilidad con eventos de cambio de ventana. Es la fuente principal.
- Respaldo: consulta de eventos de uso (`UsageStatsManager`) de la última hora, cada 300 ms mientras la pantalla esté encendida y haya al menos un perfil activo. Se detiene con la pantalla apagada.
- Ignorar siempre: la propia app, el teclado, el sistema de la interfaz y el lanzador (salvo que el lanzador sea la app bloqueada a propósito).

### 1.2 Páginas web
- El servicio de accesibilidad lee la barra de direcciones del navegador. Se necesita una tabla navegador → identificador de la vista de la barra. Debe poder actualizarse desde el servidor sin publicar versión nueva.
- Navegadores a soportar al inicio: Chrome, Firefox, Edge, Brave, Opera, Samsung Internet, DuckDuckGo, Vivaldi, Kiwi.
- Navegador no soportado: opción de bloquearlo entero.
- Navegadores dentro de otras apps (Facebook, Instagram, Line) y vistas web genéricas: detectarlos por la clase de la actividad o de la vista y tratarlos como navegador.
- Coincidencia de dominio: "contiene" (el dominio aparece en la URL) o "exacto" (solo el dominio y sus subdominios). Se normaliza quitando protocolo, `www.` y el parámetro de rastreo.

### 1.3 Contenido dentro de apps
Interruptores independientes, cada uno con su identificación de vistas (actualizable desde el servidor):
- YouTube Shorts (lista vertical de Shorts).
- Reels de Instagram y de Facebook.
- Búsqueda de Instagram, Telegram, Snapchat Stories.
- Búsqueda de imágenes y vídeo en buscadores.
- Compras dentro de apps de tiendas.

## 2. Decisión de bloqueo

Orden de evaluación, de mayor a menor prioridad. La primera regla que bloquea gana:
1. Excepciones: apps que nunca se bloquean (teléfono, emergencias, la propia app).
2. Pausa vigente (modo estricto pausado, desbloqueo de emergencia concedido).
3. Perfil activo ahora (ver sección 3) que incluya la app o web, en modo "bloquear". En modo "permitir solo esto", se bloquea todo lo que no esté en la lista.
4. Límite de uso agotado.
5. Contenido adulto (sección 5).
6. Interruptores de contenido dentro de apps (1.3).

Resultado: la decisión indica el motivo, para mostrarlo en la pantalla de bloqueo y para el aviso al compañero.

## 3. Perfiles

Un perfil agrupa: apps, webs (con tipo de coincidencia), subapps, redes Wi-Fi, límites de uso, y condiciones.

Condiciones de activación (se combinan con Y; las que no estén definidas no se evalúan):
- **Horario:** intervalos por día de la semana (inicio y fin, puede cruzar medianoche).
- **Wi-Fi:** activo cuando el teléfono está conectado a una de las redes de la lista.
- **Ubicación:** activo dentro de un radio (por defecto 100 m) de un punto.
- **Cargador:** activo mientras carga.
- **Manual / Quick Block:** activo hasta una hora fijada.

Un perfil puede estar activado o desactivado y pausado hasta una hora. Hay un perfil especial para el modo estricto (sección 4) que no se muestra en la lista normal.

### Límites de uso
- Tipos: tiempo de uso, número de aperturas, y "no más de N minutos seguidos".
- Periodos: diario, semanal.
- Al llegar al límite, la app queda bloqueada hasta el siguiente reinicio del periodo.
- Una cuenta atrás en la pantalla de bloqueo muestra cuánto falta para el reinicio.

### Quick Block
Bloqueo inmediato con lista rápida de apps y webs y una duración elegida. Modos: lista de bloqueo o lista de permitidos.

### Pomodoro
Foco 25 min, descanso 5 min, 4 sesiones por defecto, configurable. Durante el foco bloquea la lista elegida; en el descanso no. Cada fase termina con una notificación.

## 4. Modo estricto

Evita que el usuario se salte sus propios bloqueos.

### Estados
- **Inactivo:** no configurado.
- **En espera:** configurado, fuera de su horario.
- **Activo:** dentro de su horario.
- **Pausado:** pausa vigente hasta una hora.

### Qué protege (opciones, se combinan)
- No editar perfiles ni listas durante un bloqueo.
- No acceder a los ajustes del teléfono (pantalla de la app, accesibilidad, administradores, desinstalar).
- No desinstalar la app.
- No usar la pantalla de apps recientes.
- No usar pantalla dividida.
- Bloquear apps recién reinstaladas.

### Cómo se desbloquea o se pausa
Métodos de acceso, solos o combinados:
- **Temporizador:** hay que esperar un tiempo fijo.
- **Seguir horarios:** solo se puede desactivar cuando termina el horario.
- **Cargador:** hay que conectar el teléfono al cargador.
- **PIN:** código de 4 a 8 dígitos.
- **Espera (cooldown):** se pide, y se concede tras un tiempo de espera.
- **Aprobación del compañero:** se pide por correo y el compañero acepta o rechaza.

Pausas: 5, 10, 15 o 30 minutos. Al terminar la pausa el bloqueo vuelve solo.

### Protección de ajustes
El servicio de accesibilidad detecta que el usuario entra en pantallas sensibles del sistema: ajustes de accesibilidad, administradores de dispositivo, información de la app, desinstalar, restablecer de fábrica. Si el modo estricto las protege, se cierra la pantalla (atrás, luego inicio) y se registra el intento. Hay que enviar un aviso al compañero cuando se intenta desinstalar o restablecer.

## 5. Contenido adulto

- Interruptor global. Cuando está activo, no se puede apagar sin una aprobación (ver sección 7).
- Fuentes: lista de dominios y de palabras que vienen del servidor (con una copia local), más las del usuario.
- Palabras: se normaliza el texto de la pantalla y de la barra de direcciones (minúsculas, sin acentos, sin espacios ni puntos ni guiones) y se busca la palabra como subcadena. Para evitar falsos positivos, mantener una lista de excepciones y exigir longitud mínima.
- Búsqueda segura forzada en buscadores y en YouTube.
- Filtro DNS (sección 6 de `BACKEND.md`): todas las consultas DNS del teléfono van a un resolvedor propio que rechaza dominios adultos.
- Al bloquear: mostrar la pantalla de bloqueo, enviar la app a segundo plano y registrar el evento.

## 6. Pantalla de bloqueo

- Superposición a pantalla completa sobre la app. Mensaje personalizable y motivo del bloqueo.
- Si se bloquea una página web: cerrar la pestaña con la acción atrás, tres veces separadas 500 ms; si sigue visible, volver al inicio a los 5 s.
- Si se bloquea una app: volver al inicio.
- Botones según contexto: "Pedir desbloqueo", "Volver", "Desbloqueo de emergencia" (si está permitido).
- Evitar bucles: no reabrir la pantalla de bloqueo más de una vez por segundo.

## 7. Aprobaciones para apagar o cambiar una protección

**Solo se pide aprobación al debilitar una protección** (apagar un interruptor, quitar un elemento de una lista, quitar el compañero, desactivar el modo estricto). Activar o añadir es libre.

Modos de aprobación, elegibles por el usuario:
1. **Compañero real:** el compañero recibe un correo con el detalle y dos botones. Responde en el servidor.
2. **Retardo propio:** la acción se aplica tras 24 o 72 h. Se puede cancelar.
3. **Frase larga:** hay que escribir a mano una cita larga de una lista fija. El campo de texto bloquea pegar y las capturas de pantalla.
4. **Pasos:** hay que caminar 250 pasos (sensor contador de pasos).
5. **Aprobación instantánea de pago:** decisión abierta, probablemente se omite.

Cada acción lleva un identificador de qué se debilita, y cuando se aprueba el servidor lo devuelve al teléfono para aplicarlo.

Límites para evitar abuso: máximo 3 solicitudes de verificación por correo cada 24 h.

### Reactivación automática
Cuando una protección se apaga con aprobación, queda apagada solo un tiempo (por defecto 24 h para la mayoría, configurable) y se enciende sola. El usuario puede quitar esto solo con una aprobación.

### Desbloqueo de emergencia
Configurable, por defecto 3 por semana, cada uno concede 5 minutos, y se avisa al compañero.

## 8. Compañero de responsabilidad

- Se invita por correo. El compañero no necesita la app; usa enlaces del correo.
- Eventos que le llegan por correo, cada uno activable:
  - Solicitud de desbloqueo (con botones aprobar y rechazar).
  - Intento de abrir una app o página bloqueada (con nombre, hora y cuántas veces hoy).
  - Intento de desinstalar o de entrar en ajustes protegidos.
  - Protección apagada.
  - Informe diario (resumen de bloqueos del día; si no hubo ninguno pasadas las 20:00, "sin incidencias").
- Evitar spam: agrupar los intentos repetidos en un mismo aviso cada 10 minutos.

## 9. Rachas y estadísticas

- Un día cuenta como logrado si el bloqueo estuvo activo al menos 1 hora.
- Se pierde si hubo una pausa de más de 15 minutos o más de 3 pausas en el día.
- Hitos de racha: 1, 3, 7, 14, 30, 50 y 100 días.
- Estadísticas: tiempo por app, aperturas, bloqueos por día, con filtro de apps ignoradas.

## 10. Avisos de tiempo (opcional)

Si el uso de las apps elegidas suma 15 minutos dentro de la última hora, mostrar un aviso y esperar 1 hora antes de repetirlo.

## 11. Persistencia

Room con: perfiles, apps por perfil, webs por perfil, Wi-Fi por perfil, intervalos, límites de uso, historial de bloqueos, eventos de uso, configuración, estado del modo estricto. Copia de seguridad opcional en el servidor (perfiles, ajustes, modo estricto).

## 12. Límites por app, candado de perfil y desinstalación (0.6.0)

- **Alcance del límite** (`LimitScope`): `SHARED` (un contador por perfil, como siempre) o `PER_TARGET` (un contador por app o web del perfil, con clave `(perfil, destino)`; el destino de cada contador es el mismo que da el decisor al bloquear). Pasar de `SHARED` a `PER_TARGET` debilita (más tiempo en total) y pide aprobación.
- **Aviso de lo que queda**: mientras la app en primer plano cuenta para algún límite, una notificación fija y silenciosa muestra `Llevas X de N min · quedan M min` (o aperturas, o minutos seguidos). Se refresca cada 10 s o al cambiar de app, enseña el límite más cercano a agotarse y se retira al agotarse o al salir.
- **Candado de perfil**: reutiliza `StrictMode.evaluateExit` con los métodos `Timer`, `Cooldown`, `Charger`, `Pin` y `Partner` (no `FollowSchedule`); hay que cumplirlos todos. Bloquea apagar, pausar, editar y borrar solo ese perfil. Si el método `Partner` está puesto y el compañero ya no existe, a las 72 h se puede quitar con espera.
- **Desinstalación**: con compañero activo, `requestUninstall` crea siempre una solicitud al compañero (`ApprovalMode.PARTNER`); con modo estricto activo no se puede. La espera de 24 h o 72 h de Aprobaciones, si está elegida, retrasa el envío del correo (`sendAfter`) en vez de sustituirlo.
- **Imágenes**: sensibilidad de serie 2; un bloqueo cuenta 6 h en el modo dinámico (nivel máximo 2 de serie); umbrales de los pasos 4 y 5 con los dos modelos: explícito 0,20 y 0,18, sugerente 0,28 y 0,24 (ver `DECISIONES.md`).

## 13. Varios bloques al día y panel del compañero (0.7.0)

- **Horarios**: un perfil guarda una lista de intervalos semanales; puede haber varios el mismo día y el perfil está activo si la hora cae en cualquiera. El editor los ordena por día y hora.
- **Panel del compañero** (`PartnerPanel`): para cada forma de tener compañero (`watch` y `parental`) guarda `noticesOff` (tipos de aviso que el teléfono no manda) y `protections` (protecciones del modo estricto que valen con compañero activo). Vale la del modo en uso. `PartnerEventOutbox.record` descarta los avisos apagados; el servicio de accesibilidad une esas protecciones a las del modo estricto; con `EDIT_LOCK`, `ChangeGuard.check` manda los cambios por el compañero (`ApprovalMode.PARTNER`) en vez de rechazarlos.
- **Debilitar**: apagar un aviso o quitar una protección (en cualquiera de las dos) pide la aprobación del compañero (`partner_panel`, con el panel entero como carga); añadir es libre.
