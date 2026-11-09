# Despliegue y APK de prueba

Guía para poner FocusGuard en marcha: el servidor en tu VPS y el APK en tu teléfono. Todo es de uso personal; no se publica en Google Play.

## 1. Servidor (VPS)

Necesitas un VPS con Docker y Docker Compose, un dominio y los puertos 80 y 443 abiertos.

1. **DNS del dominio:** dos registros A hacia la IP del VPS: `focusguard.midominio.com` (API) y `dns.midominio.com` (resolvedor).
2. **Código:** `git clone https://github.com/inkevap/focusguard && cd focusguard`.
3. **Configuración:** `cp server/.env.example .env` y rellena:
   - `DOMAIN`, `DNS_DOMAIN`, `PUBLIC_URL` (con tu dominio) y `DNS_DOH_BOOTSTRAP` (la IP del VPS).
   - `POSTGRES_PASSWORD`: `openssl rand -hex 24`.
   - `JWT_SECRET`: `openssl rand -base64 48`.
   - `SMTP_*` y `MAIL_FROM`: sin SMTP el servidor no arranca. Sirve cualquier proveedor de correo (usa una cuenta solo para esto).
   - `FCM_SERVICE_ACCOUNT_FILE`: opcional; sin él no hay push y la app tira de sondeo. Con push (ver «Push con Firebase» más abajo) es `/run/secrets/fcm.json`.
4. **Arranque:** `docker compose up -d --build`. La primera vez migra la base (migraciones 1 a 12) y publica las listas; tarda hasta un minuto.
5. **Comprobar:** `curl https://focusguard.midominio.com/health` debe responder `ok`, y `docker compose ps` debe dejar todo en `healthy`/`running`.
6. **Panel de AdGuard Home:** solo desde el VPS: `ssh -L 3001:localhost:3001 usuario@vps` y abre `http://localhost:3001` (ver `dns/README.md`).
7. **Copias:** la base está en el volumen `db-data`. Copia diaria: `docker compose exec -T db pg_dump -U focusguard focusguard | gzip > copia-$(date +%F).sql.gz`, y guárdala fuera del VPS.
8. **Actualizar:** `git pull && docker compose up -d --build`.

### Sin dominio propio (solo la IP del VPS)

La app exige HTTPS (`cleartextTrafficPermitted="false"` y solo acepta `dohUrl` con `https://`), y Caddy necesita un nombre para pedir el certificado. Sin comprar un dominio, usa un nombre que ya apunta a tu IP:

- **sslip.io** (sin registro): para la IP `203.0.113.5`, `DOMAIN=203-0-113-5.sslip.io` y `DNS_DOMAIN=dns.203-0-113-5.sslip.io`; ambos resuelven a esa IP.
- **DuckDNS** (gratis, con cuenta): eliges `midominio.duckdns.org` y `dns.midominio.duckdns.org` (el segundo como registro propio) hacia tu IP.

Pon el mismo valor en `PUBLIC_URL` y la IP en `DNS_DOH_BOOTSTRAP`. Los enlaces de los correos y la app usarán ese nombre.

Sin probar: los servicios gratuitos comparten límites de Let's Encrypt, y si el certificado falla (`docker compose logs caddy`) hay que cambiar de servicio. Con solo la IP, sin ningún nombre, no funciona: el certificado de Caddy se pide por nombre y la app no acepta certificados que no sean de confianza.

### Push con Firebase (opcional)

El servidor avisa al teléfono cuando el compañero resuelve una solicitud o acepta la invitación. Sin push la app funciona igual: se entera al abrirla, cada 6 horas o, con la pantalla de Aprobaciones abierta, cada 15 segundos.

1. **Proyecto:** en la consola de Firebase crea un proyecto (sin Google Analytics) y registra una app Android con el nombre de paquete `com.focusguard`.
2. **App:** descarga `google-services.json` y déjalo en `app/google-services.json` (no se sube al repositorio). Sin ese fichero el APK compila sin push.
3. **Servidor:** en *Configuración del proyecto → Cuentas de servicio* genera una clave privada y guárdala en `./secrets/fcm.json` del VPS con `secrets/` en `700`. El contenedor lee el fichero como el usuario `node` (uid 1000): si tu usuario del VPS no es ese, `sudo chown 1000:1000 secrets/fcm.json` y `chmod 400`; no lo dejes legible para todos (`644`). Descomenta el volumen del servidor en `docker-compose.yml` (o añádelo en un `docker-compose.override.yml`) y pon `FCM_SERVICE_ACCOUNT_FILE=/run/secrets/fcm.json` en `.env`. Reinicia con `docker compose up -d`.
4. **Compilar el APK** con el `google-services.json` en su sitio y distribuir la nueva versión: el APK anterior no registra el token.

La clave de la cuenta de servicio es una clave privada: nunca en el repositorio ni en chats.

### Pi-hole en vez de AdGuard Home

Para empezar no necesitas tener un AdGuard Home: **ya viene en `docker-compose.yml`** y se levanta con el resto. Pi-hole solo merece la pena si ya lo tienes en marcha, y **no está soportado ni probado**:

- La app habla DNS sobre HTTPS (`/dns-query/<id>`) y Pi-hole no lo ofrece; haría falta un conversor DoH→DNS delante (por ejemplo `dnsproxy` o `cloudflared`) y cambiar la ruta de `Caddyfile` hacia él.
- La lista del servidor para el resolvedor (`/blocklists/dns-rules.txt`) está en sintaxis de AdGuard (`||dominio^`, excepciones `@@`), que Pi-hole no entiende. Habría que usar las listas de dominios en texto plano del servidor (un dominio por línea), sin las excepciones y sin subdominios automáticos.
- Con el bloqueo por defecto de Pi-hole (responde `0.0.0.0`) la app sí cuenta la consulta como bloqueada.
- La búsqueda segura (B9) tampoco viene hecha: iría con registros CNAME locales.

Si quieres ir por ahí, es un trabajo aparte: se diseña, se prueba y se anota en `docs/DECISIONES.md`.

## 2. APK

### Firma

Android solo instala APK firmados. La clave de firma identifica tu app: **si la pierdes no podrás actualizar la instalada** (habría que desinstalarla y perder sus datos), y **no debe subirse nunca al repositorio** (`*.jks` está en `.gitignore`).

```
scripts/crear-keystore.sh ~/focusguard-release.jks      # una sola vez; haz copia del fichero y de la contraseña
FG_KEYSTORE_FILE=~/focusguard-release.jks FG_KEYSTORE_PASSWORD=... \
  ./gradlew :app:assembleRelease -PserverUrl=https://focusguard.midominio.com
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. `-PserverUrl` es opcional: sin él, la dirección del servidor se escribe en la app.

Cada APK nuevo debe llevar un `versionCode` mayor (`app/build.gradle.kts`) y la misma clave. El `focusguard-0.1.0.apk` de prueba que se entregó con esta guía está firmado con una clave temporal que no se guardó: la próxima versión firmada con tu clave se instalará **desinstalando antes la de prueba**.

### Instalar

1. Pasa el APK al teléfono y ábrelo; Android pedirá permitir instalar desde esa fuente.
2. **Play Protect** puede decir «app no reconocida» o ofrecer analizarla. Es lo normal en un APK que no viene de Play Store: no se puede evitar ni se debe intentar. Elige «Instalar de todos modos» (o «Analizar app» y luego instalar).
3. Abre la app y sigue el asistente de permisos.

### Permisos que pide Android (y por qué)

- **Accesibilidad:** detecta la app en primer plano y el texto de pantalla, que se procesa solo en el teléfono.
- **Android 13 o superior, «ajustes restringidos»:** en un APK instalado a mano, el interruptor de accesibilidad sale gris. Ve a *Ajustes → Aplicaciones → FocusGuard → ⋮ (arriba a la derecha) → Permitir ajustes restringidos* y vuelve a intentarlo.
- **Acceso al uso, superposición, notificaciones, batería sin restricciones, VPN:** los explica el asistente de la app.
- **Fabricantes que matan servicios** (Xiaomi, Huawei, Samsung…): quita la optimización de batería y fija la app en recientes; ver `dontkillmyapp.com`.

## 3. Qué probar primero

1. Bloquear una app y abrirla: debe salir la pantalla de bloqueo.
2. Filtro DNS: encender la VPN y comprobar que un dominio de la lista no carga. Revisa en «Consultas DNS» que aparece como bloqueado.
3. Cuenta y compañero: crear cuenta, verificar el correo, invitar a un compañero y probar una aprobación.
4. Reiniciar el teléfono: el bloqueo debe volver solo.
5. Anota lo que falle en `docs/PENDIENTES.md`.
