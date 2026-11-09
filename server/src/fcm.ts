import { createSign } from "node:crypto";
import type { Db } from "./db.js";
import type { Push, PushMessage } from "./push.js";

export interface FcmCredentials {
  project_id: string;
  client_email: string;
  private_key: string;
}

type Fetch = typeof fetch;

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

const b64 = (data: string | Buffer) => Buffer.from(data).toString("base64url");

/**
 * Push por Firebase Cloud Messaging (API HTTP v1). Solo viaja el tipo de aviso y el id de la solicitud: ningún dato personal.
 * Las credenciales de la cuenta de servicio llegan por un fichero montado, nunca en el repositorio.
 */
export class FcmPush implements Push {
  private access: { token: string; expiresAt: number } | null = null;

  constructor(
    private readonly creds: FcmCredentials,
    private readonly db: Db,
    private readonly now: () => Date,
    private readonly http: Fetch = fetch,
  ) {}

  private async accessToken(): Promise<string> {
    const t = this.now().getTime();
    if (this.access && this.access.expiresAt - 60_000 > t) return this.access.token;
    const iat = Math.floor(t / 1000);
    const claims = b64(JSON.stringify({ iss: this.creds.client_email, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 3600 }));
    const unsigned = `${b64(JSON.stringify({ alg: "RS256", typ: "JWT" }))}.${claims}`;
    const signature = createSign("RSA-SHA256").update(unsigned).sign(this.creds.private_key).toString("base64url");
    const res = await this.http(TOKEN_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${signature}` }),
    });
    if (!res.ok) throw new Error(`FCM: no se obtuvo el token de acceso (${res.status})`);
    const body = (await res.json()) as { access_token: string; expires_in: number };
    this.access = { token: body.access_token, expiresAt: t + body.expires_in * 1000 };
    return body.access_token;
  }

  async notifyUser(userId: string, message: PushMessage): Promise<void> {
    const devices = await this.db.query<{ id: string; fcm_token: string }>("SELECT id, fcm_token FROM devices WHERE user_id = $1 AND fcm_token IS NOT NULL", [userId]);
    if (!devices.length) return;
    const bearer = await this.accessToken();
    const data: Record<string, string> = { type: message.type, ...(message.requestId ? { requestId: message.requestId } : {}) };
    await Promise.all(
      devices.map(async (d) => {
        const res = await this.http(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(this.creds.project_id)}/messages:send`, {
          method: "POST",
          headers: { authorization: `Bearer ${bearer}`, "content-type": "application/json" },
          body: JSON.stringify({ message: { token: d.fcm_token, data, android: { priority: "high" } } }),
        });
        // Token caducado o de una app desinstalada: se olvida para no insistir.
        if (res.status === 404 || res.status === 400) await this.db.query("UPDATE devices SET fcm_token = NULL WHERE id = $1 AND fcm_token = $2", [d.id, d.fcm_token]);
        else if (!res.ok) throw new Error(`FCM: envío rechazado (${res.status})`);
      }),
    );
  }
}
