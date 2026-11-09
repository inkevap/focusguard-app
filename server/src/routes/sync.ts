import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { DAY, HttpError, limit, parse, schemas, type Deps } from "../http.js";

const MAX_BYTES = 256_000;

const putHeaders = z.object({
  "x-sync-base": z.coerce.number().int().min(0).max(1_000_000_000),
  "x-sync-format": schemas.format,
});

/**
 * Sincronización de ajustes entre dispositivos (N4). Un solo documento por cuenta, cifrado en el teléfono: el servidor guarda bytes opacos y
 * solo cuida que dos teléfonos no se pisen. Cada escritura dice sobre qué versión se hizo (`X-Sync-Base`; 0 si aún no hay documento): si
 * entretanto otro teléfono escribió, el servidor responde 409 y el teléfono vuelve a leer, mezclar y escribir.
 */
export function syncRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  app.get("/sync", async (req, reply) => {
    const [row] = await db.query<{ version: number; format: number; blob: Buffer; updated_at: Date }>(
      "SELECT version, format, blob, updated_at FROM sync_docs WHERE user_id = $1 AND octet_length(blob) > 0",
      [req.userId],
    );
    if (!row) throw new HttpError(404, "Todavía no hay nada sincronizado");
    return reply
      .type("application/octet-stream")
      .headers({ "x-sync-version": String(row.version), "x-sync-format": String(row.format), "x-sync-updated": row.updated_at.toISOString(), "cache-control": "no-store" })
      .send(row.blob);
  });

  app.put("/sync", { bodyLimit: MAX_BYTES }, async (req, reply) => {
    limit(deps, `sync:${req.userId}`, 500, DAY);
    const headers = parse(putHeaders, { "x-sync-base": req.headers["x-sync-base"], "x-sync-format": req.headers["x-sync-format"] });
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw new HttpError(415, "Manda el documento como application/octet-stream");
    const now = deps.now();
    const base = headers["x-sync-base"];
    const format = headers["x-sync-format"];
    const rows =
      base === 0
        ? // Sin documento: o no hay fila, o quedó la del borrado (vacía), cuya versión sigue subiendo.
          await db.query<{ version: number }>(
            `INSERT INTO sync_docs (user_id, version, format, blob, updated_at) VALUES ($1, 1, $2, $3, $4)
             ON CONFLICT (user_id) DO UPDATE SET version = sync_docs.version + 1, format = $2, blob = $3, updated_at = $4 WHERE octet_length(sync_docs.blob) = 0
             RETURNING version`,
            [req.userId, format, req.body, now],
          )
        : await db.query<{ version: number }>(
            "UPDATE sync_docs SET version = version + 1, format = $2, blob = $3, updated_at = $4 WHERE user_id = $1 AND version = $5 AND octet_length(blob) > 0 RETURNING version",
            [req.userId, format, req.body, now, base],
          );
    if (!rows[0]) throw new HttpError(409, "Otro dispositivo sincronizó antes: vuelve a leer y mezclar", "sync_conflict");
    return { version: rows[0].version, updatedAt: now };
  });

  /** Borrar vacía el documento pero deja la fila con su versión: si la versión volviera a 1, un teléfono con una lectura anterior al borrado escribiría encima del documento nuevo. */
  app.delete("/sync", async (req, reply) => {
    await db.query("UPDATE sync_docs SET blob = ''::bytea, version = version + 1, updated_at = $2 WHERE user_id = $1", [req.userId, deps.now()]);
    return reply.code(204).send();
  });
}
