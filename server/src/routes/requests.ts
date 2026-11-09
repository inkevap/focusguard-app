import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { createRequest, effectiveStatus, type RequestStatus } from "../approvals.js";
import { ownedDevice } from "../devices.js";
import { DAY, HOUR, HttpError, limit, parse, schemas, type Deps } from "../http.js";
import { spendBySubject } from "../tokens.js";

const createBody = z.object({
  deviceId: z.string().uuid(),
  // Códigos de acción del teléfono (ver `Weakening.kt`); el servidor no les da significado.
  actionType: z.number().int().min(1).max(9999),
  // Texto legible que el compañero verá en el correo; lo redacta el teléfono.
  summary: z.string().trim().min(1).max(200),
  // Datos que el teléfono necesita para aplicar la acción cuando se apruebe; el servidor solo los guarda.
  detail: z.record(z.string(), z.unknown()).refine((d) => JSON.stringify(d).length <= 2000).default({}),
});

/** Cupo de solicitudes por usuario, compartido por todas las rutas que crean una. */
export const limitRequests = (deps: Deps, userId: string) => limit(deps, `request-user:${userId}`, 3, DAY);

export function requestRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  app.post("/requests", async (req, reply) => {
    const body = parse(createBody, req.body);
    limitRequests(deps, req.userId);
    limit(deps, `request-ip:${req.ip}`, 20, HOUR);
    await ownedDevice(db, req.userId, body.deviceId);
    const created = await db.tx((tx) =>
      createRequest(deps, tx, { userId: req.userId, deviceId: body.deviceId, origin: { actionType: body.actionType }, summary: body.summary, detail: body.detail }),
    );
    return reply.code(201).send({ requestId: created.id, status: "pendiente", expiresAt: created.expiresAt });
  });

  async function owned(id: string, userId: string) {
    const [row] = await db.query<{ id: string; status: RequestStatus; expires_at: Date; resolved_at: Date | null; consumed_at: Date | null; action_type: number | null; detail: unknown }>(
      "SELECT id, status, expires_at, resolved_at, consumed_at, action_type, detail FROM requests WHERE id = $1 AND user_id = $2",
      [id, userId],
    );
    if (!row) throw new HttpError(404, "Solicitud desconocida", "request_unknown");
    return row;
  }

  app.get("/requests/:id", async (req) => {
    const { id } = parse(schemas.idParams, req.params);
    const row = await owned(id, req.userId);
    const status = effectiveStatus(row, deps.now());
    return {
      requestId: row.id,
      status,
      resolvedAt: row.resolved_at,
      consumed: row.consumed_at !== null,
      // El detalle solo se entrega cuando ya se puede aplicar.
      ...(status === "aprobada" && !row.consumed_at && row.action_type !== null ? { actionType: row.action_type, detail: row.detail } : {}),
    };
  });

  app.post("/requests/:id/cancel", async (req) => {
    const { id } = parse(schemas.idParams, req.params);
    const now = deps.now();
    const cancelled = await db.tx(async (tx) => {
      const rows = await tx.query(
        "UPDATE requests SET status = 'cancelada', resolved_at = $3 WHERE id = $1 AND user_id = $2 AND status = 'pendiente' AND expires_at > $3 RETURNING id",
        [id, req.userId, now],
      );
      if (rows[0]) await spendBySubject(tx, "request_respond", id, now);
      return rows.length > 0;
    });
    if (!cancelled) throw new HttpError(409, "La solicitud ya no está pendiente");
    return { status: "cancelada" };
  });

  /** El teléfono pide aplicar la acción aprobada. Solo funciona una vez. */
  app.post("/requests/:id/consume", async (req) => {
    const { id } = parse(schemas.idParams, req.params);
    const rows = await db.query(
      "UPDATE requests SET consumed_at = $3 WHERE id = $1 AND user_id = $2 AND status = 'aprobada' AND consumed_at IS NULL AND action_type IS NOT NULL RETURNING id",
      [id, req.userId, deps.now()],
    );
    if (!rows[0]) throw new HttpError(409, "La solicitud no está aprobada o ya se aplicó");
    return { ok: true };
  });
}
