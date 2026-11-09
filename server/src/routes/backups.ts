import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { DAY, HttpError, limit, parse, schemas, type Deps } from "../http.js";

const KEEP = 5;
const MAX_BYTES = 2_000_000;

const latestQuery = z.object({ maxFormat: z.coerce.number().int().min(1).max(1000).optional() });

/**
 * Copias de seguridad (N2). El teléfono cifra el contenido antes de subirlo y el servidor solo guarda bytes opacos, sin
 * JSON ni base64: el cuerpo es `application/octet-stream` y el formato va en `X-Backup-Format`.
 * `format` es la versión del sobre cifrado; dentro va, aparte, el esquema de los datos. Una app nueva puede leer formatos
 * viejos (migrando al restaurar); una vieja pide `maxFormat` y no recibe lo que no entiende.
 */
export function backupRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  app.post("/backups", { bodyLimit: MAX_BYTES }, async (req, reply) => {
    limit(deps, `backup:${req.userId}`, 10, DAY);
    const format = parse(schemas.format, req.headers["x-backup-format"]);
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw new HttpError(415, "Manda la copia como application/octet-stream");
    const now = deps.now();
    const id = await db.tx(async (tx) => {
      const [row] = await tx.query<{ id: string }>("INSERT INTO backups (user_id, format, blob, created_at) VALUES ($1,$2,$3,$4) RETURNING id", [req.userId, format, req.body, now]);
      // Las 5 últimas de cada formato: las copias nuevas no deben expulsar las que aún lee una app más antigua.
      await tx.query(
        `DELETE FROM backups WHERE user_id = $1 AND id NOT IN (
           SELECT id FROM (SELECT id, row_number() OVER (PARTITION BY format ORDER BY created_at DESC, id) AS n FROM backups WHERE user_id = $1) ranked WHERE n <= $2)`,
        [req.userId, KEEP],
      );
      return row!.id;
    });
    return reply.code(201).send({ id, createdAt: now });
  });

  app.get("/backups/latest", async (req, reply) => {
    const { maxFormat } = parse(latestQuery, req.query);
    const [row] = await db.query<{ id: string; format: number; blob: Buffer; created_at: Date }>(
      "SELECT id, format, blob, created_at FROM backups WHERE user_id = $1 AND format <= $2 ORDER BY created_at DESC, id LIMIT 1",
      [req.userId, maxFormat ?? 1000],
    );
    if (!row) throw new HttpError(404, "No hay copias de seguridad que esta versión de la app pueda leer");
    return reply
      .type("application/octet-stream")
      .headers({ "x-backup-id": row.id, "x-backup-format": String(row.format), "x-backup-created": row.created_at.toISOString(), "cache-control": "no-store" })
      .send(row.blob);
  });

  app.delete("/backups", async (req, reply) => {
    await db.query("DELETE FROM backups WHERE user_id = $1", [req.userId]);
    return reply.code(204).send();
  });
}
