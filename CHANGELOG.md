# Cambios

## 0.8.0 (2026-11-13)

Interfaz nueva. No cambia ninguna función ni lo que se bloquea: cambia dónde está cada cosa y cómo se ve.

- Aspecto más sobrio: listas agrupadas de filas cortas, sin bordes ni tarjetas dentro de tarjetas, títulos grandes y barra de arriba centrada.
- Cinco pestañas: Inicio, Perfiles, Progreso, Protección y Ajustes.
- Inicio queda en lo urgente: el estado de ahora, Bloquear ya, Enfoque, Dormir, Tengo un impulso, lo que está en curso, la racha y Ayúdame.
- Perfiles tiene su pestaña. Cada perfil es una ficha de filas (apps, webs, categorías, horario, condiciones, límite, pausa, candado) y cada fila abre su propia pantalla.
- Progreso reúne la racha, las estadísticas, los informes, el diario, el programa de recuperación, el test y los hábitos.
- Protección reúne modo estricto, aprobaciones, contenido adulto, filtro DNS y detección de imágenes (antes en Ajustes).
- Ajustes y las demás pantallas largas se reparten en subpantallas: se entra a lo que se busca en vez de desplazarse por una página.

## 0.7.2 (2026-11-11)

Correcciones de una auditoría general; no cambia ninguna función.

- El servicio de bloqueo ya no se cae por un fallo suelto al guardar o limpiar datos (disco lleno, error de la base): pierde ese segundo y sigue bloqueando.
- Una solicitud aprobada con datos que no se entienden (editar, apagar o borrar un perfil, cambiar el método de aprobación) se descarta con un mensaje en vez de cerrar la app.
- Pruebas del servidor: más margen para arrancar la base de pruebas. El servidor desplegado no cambia; no hace falta actualizarlo.

## 0.7.1 (2026-11-09)

- Panel del compañero: avisos de lo bloqueado por clase de cosa y por perfil, y una casilla por cada función de la app (perfiles, filtros, imágenes, ajustes sensibles…) cuyo cambio aprueba siempre el compañero, sin modo estricto.

## 0.7.0 (2026-11-09)

- Varios bloques horarios el mismo día en un perfil (ya se admitían; ahora el editor los ordena y lo explica).
- Panel del compañero (Cuenta): qué avisos manda el teléfono y qué protecciones valen con compañero sin activar el modo estricto, por separado para «Avisarle lo que pasa» y para el modo parental. Quitar algo lo aprueba el compañero.

## 0.6.0 (2026-11-08)

**App**
- Límite de uso con alcance: «Entre todas» (un contador por perfil) o «Cada app aparte».
- Notificación fija y silenciosa con lo que llevas y lo que queda del límite más cercano (Ajustes → Avisos).
- Candado por perfil, sin modo estricto: no se apaga, pausa, edita ni borra hasta cumplir los métodos de salida elegidos.
- Desactivar el administrador (desinstalar) con compañero vinculado lo aprueba él por correo; la espera de Aprobaciones, si la hay, va antes del envío.
- Detección de imágenes con menos falsos avisos: sensibilidad de serie «Baja», un bloqueo cuenta 6 h en el modo dinámico y umbrales más altos en los pasos 4 y 5.
- Base de datos versión 20 (migración 19 → 20, sin pérdida de datos).

**Servidor**
- Los correos salen con el diseño de la app (HTML y modo oscuro): la cola de envío guardaba solo el texto (migración 13).
- El correo de solicitud lleva un solo botón que abre la página con «Aprobar» y «Rechazar».

**Se actualiza así:** instala el APK encima de la versión anterior (misma firma) y, en el servidor, `git pull && docker compose up -d --build`.

## 0.5.22 y anteriores

Ver el historial de git: varios compañeros de responsabilidad (hasta 3), ayuda con 39 fichas, filtro adulto, detección de imágenes con GantMan y NudeNet, licencia AGPL-3.0.
