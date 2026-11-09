# Instalación

Cómo poner FocusGuard en marcha de principio a fin: los servicios que necesita, el servidor, la app y lo que cambia según el teléfono. Es una app de uso personal: tú pones tu servidor y tú firmas tu APK. No pasa por Google Play.

Lo único probado de punta a punta es un **Motorola edge 40 neo con Android 14** y un servidor en un VPS de Oracle con nginx. Lo demás (otros teléfonos, otros proveedores) está escrito a partir de cómo suele funcionar Android y de la documentación pública de cada servicio, y puede necesitar ajustes. Si algo no coincide, anótalo en [PENDIENTES.md](PENDIENTES.md).

## 1. Qué necesitas antes de empezar

| Qué | Para qué | Notas |
|---|---|---|
| Un VPS con Docker y Docker Compose | Servidor y filtro DNS | 1 vCPU y 1 GB de RAM bastan; puertos 80 y 443 abiertos |
| Un dominio (o subdominio) | HTTPS | Dos nombres: uno para la API y otro para el resolvedor DNS |
| Una cuenta de correo SMTP | Correos de aprobación y avisos | Sin ella el servidor no arranca |
| Un proyecto de Firebase | Push (opcional) | Gratis; sin él no hay push |
| Una nube para copias (opcional) | Copias cifradas de la base | Cualquiera que admita rclone |
| Un PC con JDK 17 y el SDK de Android | Compilar el APK | También sirve Android Studio |
| Un teléfono con Android 8 o superior | La app | La detección de imágenes necesita Android 11 o superior |

## 2. Servicios externos

### Dominio y DNS

Crea dos registros A hacia la IP del VPS, por ejemplo `focusguard.midominio.com` (API) y `dns.midominio.com` (resolvedor). Si usas Cloudflare, déjalos en «solo DNS» (sin proxy): el resolvedor habla DNS sobre HTTPS y el certificado lo pide el propio servidor.

Sin dominio propio sirve un nombre gratuito que apunte a tu IP (sslip.io, DuckDNS): ver [DESPLIEGUE.md](DESPLIEGUE.md#sin-dominio-propio-solo-la-ip-del-vps).

### Correo (SMTP)

El servidor manda por correo la invitación a los compañeros, las solicitudes de aprobación, los avisos y el informe diario. Vale cualquier proveedor SMTP. Lo que cambia es la configuración:

| Proveedor | `SMTP_HOST` | `SMTP_PORT` | `SMTP_USER` / `SMTP_PASSWORD` |
|---|---|---|---|
| Resend | `smtp.resend.com` | `465` | usuario `resend`, la clave de API |
| Gmail con contraseña de aplicación | `smtp.gmail.com` | `465` | tu correo y la contraseña de aplicación (necesita verificación en dos pasos) |
| Otro (Brevo, Mailgun, Postmark, el de tu hosting) | el que indique | `465` o `587` | los de tu cuenta |

`MAIL_FROM` tiene que ser una dirección que el proveedor te deje usar. Usa una cuenta solo para esto: los correos llevan los enlaces de aprobación. Si los correos acaban en spam, configura SPF y DKIM del dominio en tu proveedor.

### Firebase (push, opcional)

Sin push, la app se entera de la respuesta del compañero al abrirse, cada 6 horas o cada 15 s con la pantalla de Aprobaciones abierta. Con push se entera al momento.

1. En la consola de Firebase crea un proyecto (sin Analytics) y registra una app Android con el paquete `com.focusguard`.
2. Descarga `google-services.json` y déjalo en `app/google-services.json` (no se sube al repositorio).
3. En *Configuración del proyecto → Cuentas de servicio* genera una clave privada y guárdala en `secrets/fcm.json` del VPS, con la carpeta en `700` y el fichero en `400`. El contenedor lo lee como el usuario `node` (uid 1000): `sudo chown 1000:1000 secrets/fcm.json`.
4. Monta el fichero en el contenedor (en `docker-compose.override.yml`) y pon `FCM_SERVICE_ACCOUNT_FILE=/run/secrets/fcm.json` en `.env`.
5. Recompila el APK con el `google-services.json` puesto: un APK anterior no registra el token.

La clave privada nunca va al repositorio ni a chats. Restringe la clave de API de Android a tu paquete y al SHA-1 de tu firma.

Teléfonos sin servicios de Google (Huawei recientes, algunos con ROM propias) no reciben push: la app sigue funcionando con el sondeo.

### Copias de seguridad (recomendado)

La base está en el volumen `db-data`. Una copia diaria mínima:

```
docker compose exec -T db pg_dump -U focusguard focusguard | gzip > copia-$(date +%F).sql.gz
```

Para subirla cifrada a una nube, usa [rclone](https://rclone.org) con un remoto `crypt` sobre tu proveedor. Guarda **una copia del fichero de configuración de rclone fuera del servidor**: sin sus claves, las copias de la nube no se pueden leer. Prueba una restauración al menos una vez.

## 3. Servidor

```
git clone https://github.com/inkevap/focusguard && cd focusguard
cp server/.env.example .env
```

Rellena `.env`:

- `DOMAIN`, `DNS_DOMAIN`, `PUBLIC_URL` (con tu dominio) y `DNS_DOH_BOOTSTRAP` (la IP del VPS).
- `POSTGRES_PASSWORD`: `openssl rand -hex 24`.
- `JWT_SECRET`: `openssl rand -base64 48` (mínimo 32 caracteres).
- `SMTP_*` y `MAIL_FROM`: ver arriba.
- `FCM_SERVICE_ACCOUNT_FILE`: opcional.

```
docker compose up -d --build
curl https://focusguard.midominio.com/health      # debe responder ok
```

La primera vez migra la base y publica las listas; puede tardar hasta un minuto.

### Si el VPS ya tiene un servidor web en los puertos 80 y 443

El `docker-compose.yml` lleva Caddy para pedir los certificados. Si ya usas nginx (o similar) en el host, desactiva Caddy con un `docker-compose.override.yml` solo del VPS (no lo subas a git):

```
services:
  server:
    ports:
      - "127.0.0.1:3000:3000"
  caddy:
    profiles: ["caddy"]
```

Y pon en nginx un host virtual para cada nombre: la API hacia `127.0.0.1:3000` y el resolvedor hacia el puerto de AdGuard solo para la ruta `/dns-query/<id>` (con la expresión regular entre comillas si lleva `{24}`). Los certificados se piden con certbot.

### DNS del propio VPS

Si el DNS del VPS depende de otro equipo (por ejemplo un servicio de VPN que lo reenvía a una máquina de tu casa), un corte de ese equipo deja al contenedor sin resolver nombres y el push falla (`ENOTFOUND oauth2.googleapis.com`). Dale DNS propios al servidor en el override:

```
services:
  server:
    dns:
      - 1.1.1.1
      - 8.8.8.8
```

### Actualizar

```
git pull && docker compose up -d --build
```

Antes de actualizar, haz una copia de la base. Las migraciones solo añaden; no se deshacen.

## 4. La app

### Firma

Android solo instala APK firmados. La clave identifica tu app: **si la pierdes, no podrás actualizar la instalada** (habría que desinstalar y perder los datos), y **no debe subirse nunca al repositorio**.

```
scripts/crear-keystore.sh ~/focusguard-release.jks     # una sola vez; copia el fichero y la contraseña
```

### Modelos de imagen

La detección de imágenes necesita dos modelos TFLite que **no vienen en el repositorio** (cada uno tiene su licencia, ver [NOTICE](../NOTICE)). Déjalos en `app/modelos/models/`:

| Archivo | Modelo | Entrada |
|---|---|---|
| `gantman_mobilenetv2_224.tflite` | GantMan NSFW (MIT) | imagen 224×224 con valores de 0 a 1; salida de 5 clases (drawings, hentai, neutral, porn, sexy) |
| `nudenet_320n_w8a32.tflite` | NudeNet 320n (AGPL-3.0), cuantizado a 8 bits en los pesos | `[1, 3, 320, 320]`, imagen con bandas (letterbox); salida `[1, 22, 2100]` |

Sin ellos, la app compila y funciona, pero la detección de imágenes queda desactivada. La conversión de NudeNet a TFLite se hizo con la exportación `litert` de Ultralytics; no hay un script en el repositorio, así que tendrás que repetirla.

### Compilar

```
echo "sdk.dir=/ruta/al/android-sdk" > local.properties
export JAVA_HOME=/ruta/al/jdk-17
FG_KEYSTORE_FILE=~/focusguard-release.jks FG_KEYSTORE_PASSWORD=... \
  ./gradlew :app:assembleRelease -PserverUrl=https://focusguard.midominio.com
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. Cada versión publicada también trae el APK ya firmado en la página de **Releases** del repositorio (`focusguard-<versión>.apk`, con su SHA-256 en las notas); instálalo encima de la anterior, que lleva la misma firma. Cada APK nuevo debe llevar un `versionCode` mayor (`app/build.gradle.kts`) y la misma clave. `-PserverUrl` es opcional: sin él, la dirección del servidor se escribe en la app.

### Instalar

Con el teléfono por USB y la depuración USB activada:

```
adb install -r app/build/outputs/apk/release/app-release.apk
```

O pasa el APK al teléfono y ábrelo (Android pedirá permitir instalar desde esa fuente). **Play Protect** puede decir «app no reconocida»: es lo normal en un APK que no viene de Play Store; elige «Instalar de todos modos».

### Primer arranque

1. Abre la app y sigue el asistente de permisos.
2. Crea la cuenta (Ajustes → Cuenta), confirma el correo y, si quieres, invita a un compañero.
3. Activa el administrador del dispositivo: sin él, Android no impide desinstalar la app.

## 5. Por teléfono

El servicio de accesibilidad es el corazón de la app, y los fabricantes lo matan o lo restringen de formas distintas. En todos hace falta, además, quitar la optimización de batería y mantener la app «sin restricciones».

### Android 13 o superior: «ajustes restringidos»

En un APK instalado a mano, el interruptor de accesibilidad sale gris. Ve a *Ajustes → Aplicaciones → FocusGuard → ⋮ (arriba a la derecha) → Permitir ajustes restringidos* y vuelve a intentarlo. Sirve para cualquier marca.

### Motorola (probado: edge 40 neo, Android 14)

- Todos los permisos del asistente se conceden sin pasos extra.
- Pon la app en *Batería → Sin restricciones*.
- Tras instalar un APK nuevo, comprueba que el servicio de accesibilidad sigue encendido: una vez quedó apagado tras actualizar.
- Si el IDE o `adb` pierde la conexión con frecuencia, `adb kill-server && adb start-server` la recupera.

### Google Pixel y Android «puro» (sin probar)

Suelen ser los más fáciles: *Batería → Uso de la batería de la aplicación → Sin restricciones* y permisos del asistente.

### Samsung (One UI, sin probar)

- *Ajustes → Batería → Límites de uso en segundo plano*: quita FocusGuard de «Apps en reposo» y «Apps en reposo profundo», y añádela a «Apps que nunca se suspenden».
- Desactiva «Optimización automática» o «Ajuste de batería adaptable» si ves que el servicio se apaga.
- En *Ajustes → Aplicaciones → FocusGuard → Batería*: «Sin restricciones».

### Xiaomi, Redmi y POCO (MIUI y HyperOS, sin probar)

- *Ajustes → Aplicaciones → Permisos → Inicio automático*: activa FocusGuard.
- *Ahorro de batería → FocusGuard → Sin restricciones*.
- Abre las apps recientes, mantén pulsada la tarjeta de FocusGuard y pon el candado: así no se cierra al limpiar.
- Concede «Mostrar ventanas emergentes mientras se ejecuta en segundo plano» y «Mostrar en la pantalla de bloqueo» si la app los pide.

### Huawei y Honor (sin probar)

- Sin servicios de Google no hay push: la app consulta por su cuenta.
- *Batería → Inicio de aplicaciones → FocusGuard → Administrar manualmente*: activa los tres interruptores (inicio automático, inicio secundario y ejecución en segundo plano).

### OnePlus, Oppo, Realme y Vivo (sin probar)

- *Batería → Optimización de batería → FocusGuard → No optimizar*.
- Activa el inicio automático y bloquea la app en recientes.
- En algunas capas, «Permitir actividad en segundo plano» está dentro de la ficha de la app.

Para el resto de marcas, la web [dontkillmyapp.com](https://dontkillmyapp.com) recoge lo que cada una hace.

### Permisos que pide la app (en cualquier teléfono)

- **Accesibilidad:** detecta la app en primer plano, lee la barra de direcciones y el texto de pantalla (se procesa solo en el teléfono) y captura la pantalla para la detección de imágenes.
- **Acceso al uso, superposición, notificaciones, batería sin restricciones y VPN:** los explica el asistente y el solucionador de problemas.
- **Administrador del dispositivo:** impide desinstalar sin permiso. No frena el modo seguro ni el restablecimiento de fábrica.
- **Android 14:** para algunas pantallas de ajustes, el nombre exacto de los textos cambia con el fabricante y el idioma; si una pantalla protegida no se cierra, anótalo.

## 6. Comprobar que funciona

1. Bloquear una app y abrirla: debe salir la pantalla de bloqueo.
2. Filtro DNS: encender la VPN y comprobar que un dominio de la lista no carga.
3. Cuenta y compañero: crear cuenta, confirmar el correo, invitar a un compañero y probar una aprobación de principio a fin (con la app cerrada, para ver el push).
4. Reiniciar el teléfono: el bloqueo debe volver solo.
5. Detección de imágenes: encenderla en Ajustes y ver en la tarjeta que cuenta capturas.
6. Anotar lo que falle en [PENDIENTES.md](PENDIENTES.md).
