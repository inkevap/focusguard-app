import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { ownedDevice } from "../devices.js";
import { DAY, HOUR, HttpError, limit, parse, type Deps } from "../http.js";
import { clearInactive, EVENT_TYPES, recordEvents } from "../notices.js";

const eventsBody = z.object({
  deviceId: z.string().uuid(),
  events: z
    .array(
      z.object({
        type: z.enum(EVENT_TYPES),
        at: z.coerce.date().optional(),
        // Solo el nombre corto de la app o el dominio; el texto de pantalla no sale del teléfono.
        label: z.string().max(200).optional(),
      }),
    )
    .min(1)
    .max(50),
});
const heartbeatBody = z.object({ deviceId: z.string().uuid() });

export function eventRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  /** Lotes de eventos del teléfono. Los graves avisan al compañero al momento; los bloqueos, en un resumen cada 10 minutos. */
  app.post("/events", async (req, reply) => {
    const body = parse(eventsBody, req.body);
    limit(deps, `events:${req.userId}`, 120, HOUR);
    const device = await ownedDevice(db, req.userId, body.deviceId);
    await recordEvents(deps, req.userId, device, body.events);
    return reply.code(202).send({ ok: true });
  });

  /** Latido periódico: si faltan más horas que el plazo del compañero (48 h por defecto), este recibe un aviso de que la app dejó de funcionar. */
  app.post("/heartbeat", async (req) => {
    const body = parse(heartbeatBody, req.body);
    limit(deps, `heartbeat:${req.userId}`, 1000, DAY);
    const rows = await db.query("UPDATE devices SET last_seen = $3 WHERE id = $1 AND user_id = $2 RETURNING id", [body.deviceId, req.userId, deps.now()]);
    if (!rows[0]) throw new HttpError(404, "Dispositivo desconocido");
    await clearInactive(db, body.deviceId);
    return { ok: true };
  });
}
