# FocusGuard: guía para Claude

Bloqueador personal para Android (apps, webs y contenido adulto) con compañero de responsabilidad por correo. Backend propio en un VPS. El usuario habla español: documentación, mensajes de commit y textos de la app en español; identificadores de código en inglés.

## Léelo antes de escribir código

1. `docs/ROADMAP.md`: fases y decisiones de arquitectura.
2. `docs/FUNCIONES_COMPLETAS.md`: catálogo completo de funciones con su fase. Es el alcance del proyecto: una versión propia que integra dos apps de bloqueo, con todo desbloqueado y sin planes de pago.
3. `docs/ESPECIFICACION.md`: comportamiento exacto de las partes centrales (qué decide, cuándo, con qué números). Si choca con el catálogo, manda el catálogo.
4. `docs/BACKEND.md`: API, aprobaciones por correo, push, DNS y rutas adicionales.
5. `docs/TABLAS_Y_ALGORITMO.md` y `data/seed/`: tablas de identificadores de vistas, categorías y el algoritmo de coincidencia de palabras. Úsalos tal cual para no reconstruirlos; verifica los identificadores en un teléfono real porque cambian con las actualizaciones.

## Reglas

- **Implementación limpia.** Se escribe desde cero a partir de la especificación. No pegues código de otras apps, ni código descompilado, ni nombres internos de clases de apps comerciales. Las tablas de datos y las descripciones de comportamiento de `docs/` y `data/seed/` sí se pueden reutilizar. Si algo no está en la especificación, diséñalo tú y anótalo en `docs/DECISIONES.md`.
- **Pila:** Kotlin, Jetpack Compose, Room, corrutinas y Flow, Hilt o Koin para inyección. Android 8+ (minSdk 26), compileSdk el más reciente estable.
- **Servidor:** el que elijas en `docs/DECISIONES.md` (propuesta: Kotlin con Ktor o Node con TypeScript) con PostgreSQL y correo SMTP. Todo se despliega con Docker Compose.
- **Seguridad:** nunca subas claves, tokens, contraseñas SMTP ni keystores. Van en `.env` o en secretos de CI (el `.gitignore` ya los excluye).
- **Privacidad:** el texto de pantalla leído por accesibilidad se procesa solo en el teléfono. Al servidor solo van eventos mínimos (qué se bloqueó, cuándo) y solo si el usuario activó los avisos.
- **Pruebas:** la lógica de decisión (perfiles, horarios, límites, coincidencia de palabras, estado del modo estricto) va en módulos Kotlin puros con pruebas unitarias. La capa de accesibilidad y el overlay son finos y se prueban en dispositivo.
- **Commits pequeños** con mensaje claro en español. Una fase de la hoja de ruta por rama.

## Estructura objetivo

```
app/        Android (módulos: core-model, core-decision, data, service, ui)
server/     API
dns/        Configuración del resolvedor DNS
docs/       Especificación, backend, decisiones, hoja de ruta
```

## Cómo empezar

Fase 1 de `docs/ROADMAP.md`: proyecto Android base, asistente de permisos, modelo Room, servicio de accesibilidad que detecta la app en primer plano, pantalla de bloqueo. Pregunta al usuario solo lo que no esté en estos documentos.
