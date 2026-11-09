# Pruebas en el teléfono, nivel 1

Prueba por `adb` sobre FocusGuard 0.2.0 (versionCode 2) en un Motorola edge 40 neo con Android 15, contra el servidor desplegado, el 2026-10-28. Solo se hizo lo que no exige cambiar ajustes del sistema, introducir contraseñas ni esperar días.

**No es una prueba del 100 %.** El catálogo (`FUNCIONES_COMPLETAS.md`) tiene unas 125 funciones; aquí aparecen 39 filas. Lo que no sale (condiciones por ubicación, Wi-Fi, cargador y Bluetooth; 37 de los 38 navegadores; pantalla dividida; ventanas flotantes; límites y horarios reales; modo parental; otros fabricantes) **no se ha probado**. Las 260 pruebas unitarias de la app y las de `core-decision` cubren la lógica de decisión, pero no sustituyen a las pruebas con el teléfono.

Leyenda: **PASA** = comprobado en el teléfono; **VISTO** = la pantalla existe y muestra lo esperado, sin ejecutar la acción; **FALLA** = no se comporta como debe; **FALLA?** = comportamiento dudoso o por decisión de diseño; **NO PROBADO** = no se pudo o no se debía probar.

Resumen: FALLA: 1, FALLA?: 1, NO PROBADO: 6, PASA: 18, VISTO: 13.

| ID | Función | Resultado | Detalle |
|---|---|---|---|
| A1 | Modo lista de bloqueo | PASA | Quick Block y Pomodoro con Maps; perfil «Mi primer perfil» (1 categoría) bloquea Instagram |
| A4 | Pantalla de bloqueo a pantalla completa | PASA | Con «Volver» y «Desbloqueo de emergencia» (o «No debería bloquearse» en contenido adulto) |
| A5 | Al bloquear, enviar a inicio | PASA | Tras la pantalla de bloqueo vuelve al inicio |
| A11 | Apps recién instaladas | VISTO | Activo por defecto, 12/24/48/72 h; sin probar instalando una app |
| B1 | Lectura de la barra de direcciones | PASA | Brave (`:id/url_bar`) lee `007dating.com`; solo 1 de 38 navegadores probado |
| B2/D3 | Bloqueo por dominio de la lista | PASA | Con Contenido adulto activado: «007dating.com tiene contenido para adultos…» |
| B10 | Reportar un bloqueo erróneo | VISTO | Botón «No debería bloquearse» en la pantalla de bloqueo adulto; no se pulsó |
| D1 | Interruptor de contenido adulto | FALLA? | Viene APAGADO por defecto; con él apagado el navegador no se bloquea (solo actúa el DNS). Ver CORRECCIONES, hallazgos |
| D2 | Listas del servidor | PASA | 76795 dominios y 26 palabras (versión 1), coincide con el servidor |
| D5 | Filtro DNS por VPN | PASA | Bloquea `007dating.com` (resuelve a 127.0.0.1) y deja pasar `example.com`; también tras reiniciar el teléfono |
| D7 | Añadir a permitidos con frase | VISTO | Pantalla con la frase a copiar a mano; sin probar |
| E1 | Varios perfiles con título y emoji | VISTO | «Tus perfiles»: «🌱 Mi primer perfil», con interruptor y «Nuevo perfil»; sin crear otro |
| F1 | Quick Block | PASA | 1 min: Maps bloqueada durante y libre al acabar; aviso «Bloqueo rápido terminado» |
| F2 | Pomodoro | PASA | 1/1/1: Maps bloqueada en foco, libre en descanso; aviso «Pomodoro terminado» |
| F6 | Botón de pánico «Ayúdame» | PASA | 1 min con confirmación; bloqueó Maps y Mensajes y volvió a la normalidad al acabar |
| G | Límites de uso (G1–G6) | NO PROBADO | Necesita tiempo de uso real |
| H1 | Modo estricto | VISTO | 10 opciones; no se activó (impide cambiar ajustes del sistema) |
| H12 | Desbloqueo de emergencia | VISTO | Primer paso: «Te quedan 3 de 3 esta semana y cada uno dura 5 min»; no se completó (avisa al compañero). Solo se llega pulsando muy rápido: la ventana de bloqueo se cierra a los 3 s |
| I1 | Compañero real por correo | PASA | Invitación, aceptación, solicitud de aprobación, aprobación y notificación |
| I2 | Ser mi propio compañero | NO PROBADO | Esperas de 24/72 h, frase larga y pasos |
| J1 | Notificaciones de apps bloqueadas | VISTO | Opción «Quitar las de apps bloqueadas» y «Ver las guardadas»; sin probar |
| J3 | Avisos configurables | PASA | «Pomodoro terminado. Completaste 1 sesiones» (plural mal) y «Bloqueo rápido terminado» |
| K1 | Estadísticas de uso y bloqueos | FALLA | «Bloqueos de los últimos 7 días: Todavía no hay bloqueos» y «Lo que más se bloqueó: Nada» tras ≥8 bloqueos hoy; «Uso del teléfono: No hay uso registrado ese día» con «8 h 35 min» arriba. Pista: `StatsViewModel.kt` línea 87 (`blockLog.since(...)`) y `BlockLog.record` desde `FocusAccessibilityService.record` |
| K5 | Estado de ánimo | VISTO | 5 emojis; sin registrar |
| L1 | Rachas | PASA | 0 días, metas, «Hoy llevas 39 min de 1 h de bloqueo activo» |
| L3 | Modo impulsos | VISTO | Texto, cuenta atrás e informe; sin ejecutar la cuenta atrás |
| L4 | Diario | VISTO | Pantalla abre |
| L5 | Programa de recuperación | VISTO | 7 pasos, 0 de 7 |
| L6 | Test de adicción | VISTO | «Diez preguntas…»; sin responder |
| M1 | Pantalla de bloqueo personalizable | VISTO | Sección en Ajustes con mensaje propio; sin cambiar |
| M3 | Temporizador de bloqueo | NO PROBADO | Bloquea el teléfono sin poder salir: arriesgado |
| N1 | Cuenta | PASA | Alta, correo confirmado, dispositivo registrado, servidor en `https://focusguard.midominio.com` |
| N2 | Copia de seguridad en el servidor | NO PROBADO | Pide una frase de cifrado del usuario («Frase de la copia»); no la introduzco yo |
| N3 | Exportar e importar a archivo | NO PROBADO | No localizado en las pantallas recorridas |
| N4 | Sincronizar entre dispositivos | NO PROBADO | Necesita otro dispositivo |
| N5 | Registro del dispositivo para push | PASA | Token de 142 caracteres en el servidor; aviso por FCM llega en ~1 s |
| O1 | Asistente de permisos | PASA | Accesibilidad, uso, superposición y notificaciones |
| O2 | Solucionador de problemas | PASA | Solo falta lo «Recomendado»: administrador del dispositivo |
| H5 | Bloqueo de arranque tras reiniciar | PASA | Tras reiniciar, el servicio de accesibilidad, el bloqueo y la VPN volvieron solos |
