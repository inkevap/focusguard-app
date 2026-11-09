# Auditoría de la interfaz

Hecha con `impeccable audit` (guía nativa de Android) sobre el código de `app/src/main/java/com/focusguard/ui` y las capturas de `app/src/test/snapshots/images/`. No sustituye probar en un teléfono: gestos, fabricantes, TalkBack y tamaño de letra siguen sin verificar en dispositivo.

## Puntuación tras los arreglos

| # | Dimensión | Nota | Hallazgo principal |
|---|---|---|---|
| 1 | Accesibilidad | 3 | Etiquetas, roles y áreas de 48 dp correctos; falta probar con TalkBack y letra al 130 % |
| 2 | Rendimiento | 3 | Repintado por segundo acotado a lo visible; editor de perfil guarda todo en un estado |
| 3 | Aspecto y temas | 3 | Claro y oscuro con roles de Material; sin color dinámico por decisión |
| 4 | Conformidad con Android | 3 | Barra inferior, un solo botón flotante, snackbars y atrás predictivo; falta carril en tabletas |
| 5 | Adaptación | 2 | Ancho máximo de 640 dp y teclado resuelto; horizontal y plegables sin comprobar |
| | **Total** | **14/20** | Bueno |

## Arreglado en esta pasada

- **P1 Teclado:** en Ajustes tapaba los campos del PIN. Los insets los reparte ahora un solo sitio (`AppNavigation`) y la barra inferior se oculta con el teclado abierto.
- **P1 Contraste:** el perfil apagado se atenuaba con transparencia y el texto secundario bajaba de 4,5:1. Ahora se distingue por el color del título.
- **P1 Rendimiento:** Inicio y Protección se repintaban enteras cada segundo. El temporizador respeta el ciclo de vida, y Inicio solo va por segundos si hay una cuenta atrás visible.
- **P1 Hilo principal:** el selector de apps y las estadísticas hacían trabajo pesado en el hilo de la interfaz.
- **P2 Texto grande:** anchos fijos en estadísticas recortarían el texto con letra grande.
- **P2 Estructura:** cabeceras de tarjeta, campos numéricos y de PIN, fila de interruptor, aviso de permisos, aviso pasajero, formato de tiempo y nombres de día estaban repetidos; ahora hay un único componente de cada.
- **P2 Cambios que debilitan:** el guardián del modo estricto y las aprobaciones estaba repetido en cuatro sitios y ahora es uno (`ChangeGuard`); eliminar un perfil ya avisa cuando crea una solicitud.
- **P3** Atrás predictivo activado, ancho máximo en tabletas, etiquetas de estado compartidas.

## Pendiente

Ver `docs/PENDIENTES.md`, sección Interfaz.

## Bien resuelto

- Interruptores grandes en toda la fila, que el lector de pantalla lee como interruptor.
- Estados vacío, de error y de falta de permiso con la acción siguiente.
- Un solo conjunto de iconos (Material); los emoji solo aparecen en los nombres que elige el usuario.
- El texto de bloqueo explica el motivo y qué hacer.
