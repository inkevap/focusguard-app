import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { ownedDevice } from "../devices.js";
import { HttpError, parse, schemas, type Deps } from "../http.js";
import { clearInactive } from "../notices.js";

const body = z.object({
  deviceId: z.string().uuid().optional(),
  name: z.string().trim().min(1).max(80),
  fcmToken: z.string().min(1).max(4096).nullable().optional(),
});

/**
 * Un token de FCM es de una instalación: si ya lo tenía otro dispositivo (otra cuenta que entró antes en este teléfono y cuyo cierre de
 * sesión no llegó al servidor, por ejemplo), pasa a ser solo de este. Así los avisos de la cuenta anterior no siguen llegando.
 */
async function releaseToken(deps: Deps, token: string | null | undefined, deviceId: string) {
  if (token) await deps.db.query("UPDATE devices SET fcm_token = NULL WHERE fcm_token = $1 AND id <> $2", [token, deviceId]);
}

export function deviceRoutes(app: FastifyInstance, deps: Deps) {
  /** Crea el dispositivo, o actualiza nombre y token de push si ya existe (el token de FCM cambia de vez en cuando). */
  app.put("/devices/register", async (req) => {
    const data = parse(body, req.body);
    const now = deps.now();
    if (data.deviceId) {
      const rows = await deps.db.query(
        "UPDATE devices SET name = $3, fcm_token = COALESCE($4, fcm_token), last_seen = $5 WHERE id = $1 AND user_id = $2 RETURNING id",
        [data.deviceId, req.userId, data.name, data.fcmToken ?? null, now],
      );
      if (!rows[0]) throw new HttpError(404, "Dispositivo desconocido", "device_unknown");
      await clearInactive(deps.db, data.deviceId);
      await releaseToken(deps, data.fcmToken, data.deviceId);
      return { deviceId: data.deviceId };
    }
    const [row] = await deps.db.query<{ id: string }>(
      "INSERT INTO devices (user_id, name, fcm_token, last_seen, created_at) VALUES ($1,$2,$3,$4,$4) RETURNING id",
      [req.userId, data.name, data.fcmToken ?? null, now],
    );
    await releaseToken(deps, data.fcmToken, row!.id);
    return { deviceId: row!.id };
  });

  /**
   * Olvida el token de push del dispositivo. La app lo llama al cerrar sesión: `PUT /devices/register` conserva el token si no llega
   * ninguno, y sin esto los avisos de esta cuenta seguirían llegando a un teléfono donde ya entró otra. El dispositivo y sus datos se quedan.
   */
  app.delete("/devices/:id/push-token", async (req, reply) => {
    const { id } = parse(schemas.idParams, req.params);
    await ownedDevice(deps.db, req.userId, id);
    await deps.db.query("UPDATE devices SET fcm_token = NULL WHERE id = $1", [id]);
    return reply.code(204).send();
  });
}
