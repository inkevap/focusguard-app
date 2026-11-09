# Hoja de ruta

## Decisiones tomadas

- App Android nativa: Kotlin, Jetpack Compose, Room, corrutinas.
- Detección de lo que se abre: servicio de accesibilidad para URL y contenido, `UsageStatsManager` como respaldo para la app en primer plano.
- Pantalla de bloqueo: ventana superpuesta desde el servicio de accesibilidad.
- Filtro DNS: `VpnService` local que reenvía solo consultas DNS a un resolvedor propio (por ejemplo AdGuard Home en el VPS).
- Backend en el VPS: API REST, base de datos propia, correo transaccional y notificaciones push.

## Fases

El catálogo de funciones por fase está en `FUNCIONES_COMPLETAS.md` (columna "Fase").

### F1: núcleo local, sin servidor
Proyecto Android base y asistente de permisos; modelo Room; servicio de accesibilidad (app en primer plano y URL de navegadores); pantalla de bloqueo; perfiles con horarios; listas de bloqueo y de permitidos.

### F2: control y protección local
Límites de tiempo y aperturas; Quick Block y Pomodoro; modo estricto completo con sus métodos de salida; aprobaciones propias (retardo, frase larga, pasos); estadísticas básicas; exportar e importar configuración; PIN de la app.

### F3: riqueza de bloqueo
Condiciones por ubicación, Wi-Fi, cargador; sub-apps (Shorts, Reels, Stories…); plantillas de horario; pantalla de bloqueo personalizable; botón de pánico; desbloqueo de emergencia con fricción; notificaciones y solucionador de problemas; bloqueo del menú de apagado y del panel de notificaciones.

**Estado (rama `claude/fase-3-bloqueo-avanzado`, 16 pasos con su revisión cada uno):** hecho todo lo local del catálogo. Condiciones, secciones dentro de apps, plantillas y conflictos, edición consciente, pánico, desbloqueo de emergencia, pantalla de bloqueo personalizable y mensaje al intentar apagar, protecciones del menú de apagado y del panel, solucionador, categorías, avisos (J3: antes de empezar, antes de agotarse un límite, al terminar un horario, frase diaria y resumen semanal), apps recién instaladas, vista previa del perfil y Block Me, estadísticas por app, día y webs, notificaciones guardadas y aviso de segundo plano, bienvenida, atajos del icono, navegadores dentro de apps y cierre de ventanas flotantes. Queda para F4 lo que necesita servidor o DNS (B9, aviso al compañero H12) y para F5 las rachas. Lo que falta comprobar en teléfonos reales está en `PENDIENTES.md`.

### F4: servidor y contenido adulto
API, cuentas y copias; compañero por correo con aprobaciones y avisos; listas y selectores desde el servidor; VPN solo DNS contra el resolvedor propio; bloqueo de arranque; temporizador de la pantalla de bloqueo; widgets.

### F5: recuperación y extras
Rachas, modo impulsos, diario, insights, aviso de tiempo, ánimo; sincronización multidispositivo; modo parental; compañero mutuo; condición Bluetooth; cambio de icono.

### F6: programa de recuperación
Programa por pasos, test de adicción, informe de impulsos para el compañero.

## Riesgos conocidos

- Algunos fabricantes matan los servicios en segundo plano; hay que probar en dispositivos reales.
- Google Play restringe el uso del servicio de accesibilidad; la distribución prevista es instalación directa del APK.
- Los textos de pantalla pueden contener datos personales: no subirlos al servidor sin necesidad.

## Estado actual

Versión **0.7.0** (ver `CHANGELOG.md`): las fases F1 a F6 están hechas en lo local y en el servidor; lo que queda por probar o decidir está en `PENDIENTES.md`.
