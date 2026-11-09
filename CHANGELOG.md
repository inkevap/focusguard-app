# Cambios

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
