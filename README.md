# FocusGuard

Bloqueador personal de apps, webs, contenido adulto e imágenes para Android, con compañeros de responsabilidad que aprueban por correo los cambios que debilitan la protección. Es software libre ([AGPL-3.0](LICENSE)) y se pone en marcha en un servidor propio: no hay nube de terceros ni cuenta de empresa de por medio.

> Estado: uso personal en pruebas. Funciona en un Motorola edge 40 neo con Android 14; el resto de teléfonos está sin probar (ver [instalación por teléfono](docs/INSTALACION.md#por-teléfono)).

## Qué hace

- **Bloqueo de apps y webs** por perfiles, horarios, límites de uso, Wi-Fi, ubicación, cargador y Bluetooth. Lee la barra de direcciones de más de 38 navegadores. También bloquea Reels, Shorts y otras partes de una app.
- **Contenido adulto**: listas de dominios y palabras del servidor más las tuyas, filtro DNS propio por VPN local, y lectura del texto de pantalla (con apps de notas excluidas).
- **Detección de imágenes en el teléfono**: captura la pantalla y dos modelos (GantMan y NudeNet) puntúan lo explícito y lo sugerente. Lo explícito se bloquea; lo sugerente avisa. Ritmo adaptativo, vigilancia tras un incidente y modo dinámico. Nada sale del teléfono.
- **Límites de uso** compartidos por todo el perfil o **por cada app**, con una notificación que dice cuánto llevas y cuánto te queda.
- **Modo estricto, PIN y administrador del dispositivo** para que no se pueda apagar, editar ni desinstalar sin permiso, y **candado por perfil** (sin modo estricto) con sus métodos de salida. Desinstalar con compañero vinculado lo aprueba él por correo.
- **Hasta tres compañeros de responsabilidad**: cada solicitud de aprobación les llega por correo (con diseño y un solo botón que abre la página de aprobar o rechazar) y basta con que uno la apruebe. Avisos configurables, informe diario y modo parental.
- **Recuperación**: rachas, modo impulsos, diario, programa por pasos y test.
- **Botón de pánico, Dormir, Quick Block y Pomodoro**, widgets y atajos del icono.

Cambios de cada versión en [CHANGELOG.md](CHANGELOG.md). La lista completa está en [docs/FUNCIONES_COMPLETAS.md](docs/FUNCIONES_COMPLETAS.md) y dentro de la app, en Ajustes → Ayuda (39 fichas, una por función).

## Cómo ponerlo en marcha

Hacen falta dos cosas: un **servidor** (un VPS pequeño con Docker) y el **APK** en el teléfono.

| Servicio | Para qué | ¿Obligatorio? |
|---|---|---|
| VPS con Docker y un dominio | API, listas de bloqueo y filtro DNS | Sí |
| Proveedor de correo (SMTP) | Avisos y aprobaciones de los compañeros | Sí, sin él el servidor no arranca |
| Firebase (FCM) | Push cuando el compañero responde | No: sin él la app consulta al abrirse y cada 6 h |
| Almacenamiento en la nube (rclone) | Copias cifradas de la base de datos | Recomendado |

Guía completa, paso a paso, con lo que cambia según el servicio y según el teléfono: **[docs/INSTALACION.md](docs/INSTALACION.md)**. Referencia más corta del servidor: [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md).

Resumen:

```
git clone https://github.com/inkevap/focusguard && cd focusguard
cp server/.env.example .env            # rellena dominio, secretos y SMTP
docker compose up -d --build           # servidor y filtro DNS

echo "sdk.dir=/ruta/al/android-sdk" > local.properties
scripts/crear-keystore.sh ~/focusguard-release.jks       # una vez; haz copia
FG_KEYSTORE_FILE=~/focusguard-release.jks FG_KEYSTORE_PASSWORD=... \
  ./gradlew :app:assembleRelease -PserverUrl=https://focusguard.midominio.com
adb install -r app/build/outputs/apk/release/app-release.apk
```

Para la detección de imágenes hay que descargar los dos modelos aparte: no van en el repositorio (ver [NOTICE](NOTICE)).

## Estructura

| Carpeta | Contenido |
|---|---|
| `app/` | App Android (Kotlin, Jetpack Compose, Room). Los módulos `core-model` y `core-decision` son Kotlin puro y se prueban sin Android |
| `server/` | API (Node, Fastify, PostgreSQL): cuentas, compañeros, aprobaciones, correos, push y listas |
| `dns/` | Configuración del filtro DNS propio (AdGuard Home) |
| `data/` | Tablas de categorías, plantillas y paleta compartida |
| `docs/` | Decisiones, pendientes, especificación y guías |

## Probar y compilar

```
./gradlew :core-decision:test :app:testDebugUnitTest      # pruebas de la lógica y de la app
./gradlew :app:assembleDebug                              # APK de depuración
(cd server && npm ci && npm test)                         # servidor (las pruebas usan PGlite: no hace falta base de datos)
```

Hace falta JDK 17 y el SDK de Android.

## Licencia y créditos

FocusGuard se publica bajo la **GNU Affero General Public License v3.0** ([LICENSE](LICENSE)). Si alguien ofrece una versión modificada como servicio, tiene que compartir sus cambios con los usuarios. Los modelos de detección tienen sus propias licencias: [NOTICE](NOTICE).

## Reglas del proyecto

- Implementación limpia: el código se escribe desde cero a partir de una descripción de comportamiento. No se copia ni se incluye código descompilado de otras apps.
- El análisis de apps comerciales vive fuera de este repositorio.
- Las decisiones de diseño y lo que queda pendiente están en [docs/DECISIONES.md](docs/DECISIONES.md) y [docs/PENDIENTES.md](docs/PENDIENTES.md).
