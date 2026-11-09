import type { Db } from "./db.js";
import { HttpError } from "./http.js";

/** El dispositivo si es de este usuario; si no, 404 (no se distingue «no existe» de «es de otra cuenta»). */
export async function ownedDevice(db: Db, userId: string, deviceId: string): Promise<{ id: string; name: string }> {
  const [device] = await db.query<{ id: string; name: string }>("SELECT id, name FROM devices WHERE id = $1 AND user_id = $2", [deviceId, userId]);
  if (!device) throw new HttpError(404, "Dispositivo desconocido", "device_unknown");
  return device;
}
