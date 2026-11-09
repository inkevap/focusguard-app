import { randomBytes } from "node:crypto";
import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { HttpError, parse, type Deps } from "../http.js";

const query = z.object({ deviceId: z.string().uuid() });

export function dnsRoutes(app: FastifyInstance, deps: Deps) {
  /**
   * A qué resolvedor DNS sobre HTTPS tiene que mandar las consultas el teléfono. Si el servidor lo cambia de sitio, la app se
   * entera aquí. La ruta lleva un identificador de cliente propio del dispositivo (24 cifras hexadecimales, como admite
   * AdGuard Home): sirve para atribuir las consultas a un dispositivo sin guardar su IP.
   */
  app.get("/dns/config", async (req) => {
    const { deviceId } = parse(query, req.query);
    const base = deps.config.DNS_DOH_URL;
    if (!base) throw new HttpError(404, "Este servidor no tiene resolvedor DNS propio");
    const [device] = await deps.db.query<{ dns_client_id: string | null }>("SELECT dns_client_id FROM devices WHERE id = $1 AND user_id = $2", [deviceId, req.userId]);
    if (!device) throw new HttpError(404, "Dispositivo desconocido");
    // El identificador se crea una vez (la primera consulta); después solo se lee, sin escribir en la fila.
    const clientId =
      device.dns_client_id ??
      (await deps.db.query<{ dns_client_id: string }>("UPDATE devices SET dns_client_id = COALESCE(dns_client_id, $2) WHERE id = $1 RETURNING dns_client_id", [deviceId, randomBytes(12).toString("hex")]))[0]!.dns_client_id;
    return {
      dohUrl: `${base}/${clientId}`,
      // Direcciones del resolvedor, para llegar a él sin pasar por la propia VPN de la app ni por el DNS del sistema.
      bootstrapIps: deps.config.DNS_DOH_BOOTSTRAP,
      // Si el resolvedor no responde, el teléfono usa el DNS del sistema y avisa; sin internet sería peor.
      failOpen: true,
    };
  });
}
