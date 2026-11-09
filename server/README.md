# Servidor de FocusGuard

API en Node 22 con TypeScript (Fastify), PostgreSQL y correo SMTP. Diseño en `../docs/BACKEND.md`; decisiones en `../docs/DECISIONES.md`.

```
npm ci
npm test            # usa PGlite (Postgres en memoria): no necesita base ni red
npm run typecheck
npm run dev         # variables de .env.example más PGHOST, PGUSER, PGPASSWORD y PGDATABASE de un PostgreSQL
```

Despliegue: `docker compose up -d` desde la raíz del repositorio, con un `.env` basado en `server/.env.example`. Las migraciones corren al arrancar (`src/migrations.ts`).
