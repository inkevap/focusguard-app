# Diseño del backend

Servidor propio en un VPS. Diseño nuevo; no replica ningún servidor existente.

## 1. Componentes

| Componente | Función | Propuesta |
|---|---|---|
| API | cuentas, dispositivos, perfiles de copia, aprobaciones | Ktor (Kotlin) o Node + TypeScript |
| Base de datos | usuarios, dispositivos, compañeros, solicitudes, eventos | PostgreSQL |
| Correo | avisos y aprobaciones al compañero | SMTP propio o proveedor transaccional, con SPF, DKIM y DMARC |
| Push | avisar al teléfono de que una solicitud se resolvió | Firebase Cloud Messaging (FCM) |
| DNS | filtro de dominios adultos | AdGuard Home con listas de bloqueo |
| Proxy | HTTPS | Caddy o Nginx con certificados automáticos |
| Orquestación | despliegue | Docker Compose |

## 2. Modelo de datos

- `users`: id, correo, hash de contraseña (argon2), creado.
- `devices`: id, user_id, nombre, token FCM, última conexión.
- `partners`: id, user_id, correo del compañero, estado (`pendiente`, `aceptado`, `revocado`), token de confirmación, eventos activados (JSON).
- `requests`: id, device_id, partner_id, tipo de acción (qué protección se debilita), detalle (JSON), estado (`pendiente`, `aprobada`, `rechazada`, `caducada`, `cancelada`), token de respuesta de un solo uso, creada, caduca, resuelta.
- `events`: id, device_id, tipo (`bloqueo`, `intento_ajustes`, `intento_desinstalar`, `proteccion_apagada`), detalle (JSON), hora.
- `backups`: id, user_id, versión, contenido cifrado (JSON), creada.
- `blocklists`: dominios y palabras publicados, versión, firma.
- `selectors`: tabla de identificadores de vistas por navegador y de contenido dentro de apps, versionada.

## 3. API (esquema)

Autenticación: JWT de corta duración y token de refresco. Todo bajo HTTPS.

Cuenta y dispositivo
- `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/password/reset`
- `PUT /devices/register` (token FCM)
- `DELETE /devices/:id/push-token` (olvida el token de push del dispositivo; se llama al cerrar sesión)

Compañero
- `POST /partners` (invita; envía correo de confirmación)
- `GET /partners/confirm?token=` (el compañero acepta)
- `DELETE /partners/{id}` (requiere aprobación si hay compañero activo)

Solicitudes de aprobación
- `POST /requests` (el teléfono crea una; el servidor envía el correo)
- `GET /requests/{id}` (estado)
- `POST /requests/{id}/cancel`
- `GET /r/{token}`: la página a la que lleva el único botón del correo; enseña el cambio pedido y los dos botones (aprobar y rechazar). Un GET nunca cambia nada.
- `GET /r/{token}/approve` y `GET /r/{token}/reject` (enlaces directos, de un solo uso, que siguen valiendo para correos ya enviados; piden confirmación por POST para evitar que un escáner de correo apruebe sin querer)

Eventos y avisos
- `POST /events` (lotes; el servidor decide si avisa por correo según las preferencias del compañero)
- `POST /reports/daily` (o tarea programada en el servidor a partir de `events`)

Datos
- `GET /blocklists?since=` (descarga incremental)
- `GET /selectors?since=`
- `POST /backups`, `GET /backups/latest`, `DELETE /backups`

## 4. Flujo de una aprobación

1. El usuario intenta debilitar una protección en el teléfono.
2. El teléfono crea `POST /requests` con el tipo de acción y el detalle.
3. El servidor guarda la solicitud (caduca en 48 h), genera un token de un solo uso y envía el correo al compañero con el detalle y un solo botón («Revisar la solicitud»).
4. El compañero abre el enlace, ve la página con el cambio y los dos botones, y pulsa Aprobar o Rechazar (el POST es el que decide).
5. El servidor marca la solicitud y envía una notificación push al teléfono.
6. El teléfono consulta `GET /requests/{id}`; si está aprobada, aplica la acción. Si el teléfono está sin conexión, la consulta al volver a tenerla.
7. La acción aprobada se aplica una sola vez; el servidor marca la solicitud como consumida.

Seguridad: los tokens son aleatorios de 128 bits o más, caducan, se guardan con hash y solo valen una vez. Limitar a 3 solicitudes por usuario cada 24 h y a 20 por IP por hora.

## 5. Correos

Todos los correos llevan versión HTML con el diseño de la app (`src/theme.ts`, la misma paleta que la app, con modo oscuro) y texto plano como alternativa. La cola `mail_outbox` guarda las dos versiones (columna `html`, migración 13) y el envío manda las dos. Antes de la migración 13 la cola guardaba solo el texto y todos los correos salían sin diseño.

Plantillas en español. Cada correo trae: qué ocurrió, en qué dispositivo, a qué hora, y un enlace para dejar de recibir ese tipo de aviso. Remitente con dominio propio y SPF, DKIM y DMARC configurados, o los correos acabarán en spam.

Tipos: invitación de compañero, solicitud de desbloqueo, intento bloqueado (agrupado cada 10 min), intento de desinstalar o de ajustes, protección apagada, informe diario.

## 6. DNS

- AdGuard Home en el VPS con DNS sobre HTTPS (DoH) o DoT, para que el teléfono pueda enviar consultas cifradas.
- Listas: StevenBlack unified con extensión porn, o similar, más una lista propia.
- En el teléfono: `VpnService` local que captura solo el tráfico DNS y lo reenvía al resolvedor propio. No se enruta el resto del tráfico.
- El servidor puede mandar al teléfono qué resolvedor usar (por si cambia la dirección).
- Si el teléfono no llega al servidor DNS: decisión abierta entre "fallar abierto" (usar el DNS del sistema) y "fallar cerrado" (sin resolución). Propuesta: fallar abierto con aviso, para no dejar el teléfono sin internet.

## 7. Operación

- Copias de seguridad diarias de PostgreSQL.
- Registros sin contenido sensible: no guardar URL completas, solo el dominio y el motivo.
- Variables de entorno en `.env` fuera del repositorio.
- Monitorización mínima: comprobación de salud y alerta si el envío de correo falla.

## 8. Rutas adicionales para las funciones completas

Ver `FUNCIONES_COMPLETAS.md`.

- `GET /categories` y `GET /selectors`: tabla de categorías de apps y webs y de identificadores de vistas por app (B1, A12, sección C).
- `POST /reports/wrong-block`: reporte de bloqueo erróneo (B10); la lista se revisa a mano.
- `POST /panic`: aviso al compañero cuando se activa el botón de pánico (F6 de funciones).
- Historial de consultas DNS (D8): **no está en el servidor**. El teléfono apunta qué dominios consulta y si se bloquearon (30 días, solo para el usuario) y al compañero le manda una vez al día solo los dos totales (`POST /events`, tipo `dns_counts`, etiqueta `bloqueadas,permitidas`), que salen en el informe diario. El resolvedor no guarda historial. Ver DECISIONES.
- `GET /sync`, `PUT /sync`, `DELETE /sync`: ajustes entre dispositivos (N4). Un solo documento cifrado en el teléfono por cuenta (bytes opacos, hasta 256 KB) con versión: `PUT` lleva `X-Sync-Base` (la versión leída, 0 si aún no hay) y `X-Sync-Format`; si otro dispositivo escribió antes responde 409 con código `sync_conflict` y el teléfono vuelve a leer, mezclar y escribir. La mezcla (solo suma, nunca afloja) la hace el teléfono.
- `POST /streaks/snapshot` y `GET /streaks`: copia de las rachas (L1, L2). Restaurar una racha crea una solicitud de aprobación.
- Informe de impulsos (L7): **no hay `/urges` en el servidor**. Los impulsos se quedan en el teléfono y, si el usuario lo permite, una vez por semana el teléfono manda un resumen (`POST /events`, tipo `urge_report`, etiqueta `total,pasados,cedidos,hora,desencadenante`) que el servidor valida y añade al informe diario del compañero. Ver DECISIONES.
- `POST /assessments`, `GET /assessments`, `DELETE /assessments`: puntuación total (0 a 40) del test de adicción (L6) y cuándo se hizo; las respuestas no salen del teléfono, el compañero no ve nada y solo se conservan las 50 últimas. El teléfono solo la manda si el usuario lo pide.
- `GET /partners/helping`: las personas que me eligieron como compañero (I3), con cuántas solicitudes esperan y si la relación es mutua. Solo para cuentas con el correo confirmado. El compañero mutuo no necesita tabla nueva: cada uno invita al otro por `POST /partners`. El modo parental (I7) es local: reutiliza al compañero (ver DECISIONES).
- `POST /heartbeat`: latido periódico del dispositivo. Si faltan latidos durante un tiempo configurable (por defecto 48 h), el servidor avisa al compañero de que la app se desinstaló o dejó de funcionar (H11).
- `POST /recovery/pin-code`: envío del código de recuperación del PIN o patrón al correo del usuario (H9), con límite de 3 al día.
