import type { Db } from "./db.js";
import { DAY, HOUR, HttpError, type Deps } from "./http.js";
import { enqueueMail } from "./mailqueue.js";
import { requestMail } from "./mails.js";
import { consumeToken, issueToken, spendBySubject } from "./tokens.js";

export const REQUEST_TTL_MS = 48 * HOUR;

export type RequestStatus = "pendiente" | "aprobada" | "rechazada" | "caducada" | "cancelada";
export type Decision = "approve" | "reject";

/** Acciones que decide y aplica el servidor al aprobarse (no hay nada que aplicar en el teléfono). */
export type ServerAction = "partner_remove";

/** Cada efecto devuelve las solicitudes ajenas que dejó canceladas, para avisar a sus teléfonos. */
const serverEffects: Record<ServerAction, (tx: Db, request: { partnerId: string }, now: Date) => Promise<string[]>> = {
  partner_remove: (tx, r, now) => revokePartner(tx, r.partnerId, now),
};

/** Una pendiente cuya hora pasó cuenta como caducada aunque el trabajo que la marca aún no haya corrido. */
export function effectiveStatus(row: { status: RequestStatus; expires_at: Date }, now: Date): RequestStatus {
  return row.status === "pendiente" && row.expires_at <= now ? "caducada" : row.status;
}

/** Da de baja al compañero y deja sin efecto todo lo que dependía de él: invitaciones y solicitudes pendientes con sus enlaces. */
export async function revokePartner(tx: Db, partnerId: string, now: Date): Promise<string[]> {
  const [gone] = await tx.query<{ user_id: string }>("UPDATE partners SET status = 'revocado', revoked_at = $2 WHERE id = $1 RETURNING user_id", [partnerId, now]);
  // Se anulan la invitación y los enlaces de respuesta de este compañero; los de los demás compañeros siguen valiendo.
  await tx.query(
    `UPDATE tokens SET used_at = $2 WHERE used_at IS NULL AND (
       (purpose = 'partner_confirm' AND subject_id = $1)
       OR (purpose = 'request_respond' AND partner_id = $1))`,
    [partnerId, now],
  );
  // Una solicitud que pidió a varios compañeros sigue viva con los que quedan; las que eran solo de este compañero (quitarle a él) se cancelan.
  const [other] = gone
    ? await tx.query<{ id: string }>("SELECT id FROM partners WHERE user_id = $1 AND status = 'aceptado' AND id <> $2 ORDER BY confirmed_at LIMIT 1", [gone.user_id, partnerId])
    : [];
  if (other) {
    await tx.query("UPDATE requests SET partner_id = $2 WHERE partner_id = $1 AND status = 'pendiente' AND server_action IS NULL", [partnerId, other.id]);
  }
  const cancelled = await tx.query<{ id: string }>("UPDATE requests SET status = 'cancelada', resolved_at = $2 WHERE partner_id = $1 AND status = 'pendiente' RETURNING id", [partnerId, now]);
  for (const r of cancelled) await spendBySubject(tx, "request_respond", r.id, now);
  return cancelled.map((r) => r.id);
}

export interface NewRequest {
  userId: string;
  deviceId: string | null;
  /** Si se indica, la solicitud es solo de ese compañero (por ejemplo quitarle a él); si no, la reciben todos los que aceptaron y basta con que uno responda. */
  partnerId?: string;
  origin: { actionType: number } | { serverAction: ServerAction };
  summary: string;
  detail?: Record<string, unknown>;
}

/** Crea la solicitud, su token de respuesta y el correo al compañero, todo en la transacción dada. */
export async function createRequest(deps: Deps, tx: Db, r: NewRequest) {
  const now = deps.now();
  const expiresAt = new Date(now.getTime() + REQUEST_TTL_MS);
  // Se bloquea la fila del compañero: si se está dando de baja a la vez, una de las dos espera a la otra y no queda una solicitud viva para un ex compañero.
  const partners = await tx.query<{ id: string; email: string }>(
    "SELECT id, email FROM partners WHERE user_id = $1 AND status = 'aceptado' AND ($2::uuid IS NULL OR id = $2) ORDER BY confirmed_at, id FOR SHARE",
    [r.userId, r.partnerId ?? null],
  );
  const partner = partners[0];
  if (!partner) throw new HttpError(409, "Aún no tienes un compañero que haya aceptado");
  const [row] = await tx.query<{ id: string }>(
    `INSERT INTO requests (user_id, device_id, partner_id, action_type, server_action, summary, detail, status, created_at, expires_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,'pendiente',$8,$9) RETURNING id`,
    [
      r.userId,
      r.deviceId,
      partner.id,
      "actionType" in r.origin ? r.origin.actionType : null,
      "serverAction" in r.origin ? r.origin.serverAction : null,
      r.summary,
      JSON.stringify(r.detail ?? {}),
      now,
      expiresAt,
    ],
  );
  const id = row!.id;
  const [info] = await tx.query<{ user_email: string; device_name: string | null }>(
    "SELECT u.email AS user_email, d.name AS device_name FROM users u LEFT JOIN devices d ON d.id = $2 WHERE u.id = $1",
    [r.userId, r.deviceId],
  );
  // Cada compañero recibe su propio enlace; el primero que responde decide y el resto dejan de valer.
  for (const p of partners) {
    const token = await issueToken(tx, "request_respond", r.userId, REQUEST_TTL_MS, now, id, p.id);
    await enqueueMail(
      tx,
      requestMail(deps.config, { to: p.email, user: info!.user_email, device: info!.device_name ?? "cuenta", summary: r.summary, expiresAt, token }),
      now,
    );
  }
  return { id, expiresAt };
}

/** El compañero responde con el enlace del correo. Devuelve a quién avisar, o `null` si el enlace ya no vale. */
export async function resolveRequest(tx: Db, token: string, decision: Decision, now: Date): Promise<{ userId: string; requestIds: string[] } | null> {
  const owner = await consumeToken(tx, "request_respond", token, now);
  if (!owner?.subjectId) return null;
  // Un rechazo no cierra la solicitud mientras otro compañero pueda aún aprobarla: se cierra cuando todos rechazan o caduca.
  if (decision === "reject") {
    const [left] = await tx.query("SELECT 1 FROM tokens WHERE purpose = 'request_respond' AND subject_id = $1 AND used_at IS NULL AND expires_at > $2", [owner.subjectId, now]);
    if (left) return { userId: owner.userId, requestIds: [] };
  }
  const [r] = await tx.query<{ partner_id: string; server_action: ServerAction | null }>(
    "UPDATE requests SET status = $2, resolved_at = $3 WHERE id = $1 AND status = 'pendiente' AND expires_at > $3 RETURNING partner_id, server_action",
    [owner.subjectId, decision === "approve" ? "aprobada" : "rechazada", now],
  );
  if (!r) return null;
  // Ya decidió uno: los enlaces de los demás compañeros no valen.
  await spendBySubject(tx, "request_respond", owner.subjectId, now);
  const alsoCancelled = decision === "approve" && r.server_action ? await serverEffects[r.server_action](tx, { partnerId: r.partner_id }, now) : [];
  return { userId: owner.userId, requestIds: [owner.subjectId, ...alsoCancelled] };
}

/** Marca como caducadas las pendientes vencidas y avisa a sus teléfonos. */
export async function expireRequests(deps: Pick<Deps, "db" | "push" | "now">): Promise<number> {
  const rows = await deps.db.query<{ id: string; user_id: string }>(
    "UPDATE requests SET status = 'caducada', resolved_at = expires_at WHERE status = 'pendiente' AND expires_at <= $1 RETURNING id, user_id",
    [deps.now()],
  );
  for (const r of rows) await deps.push.notifyUser(r.user_id, { type: "request_resolved", requestId: r.id });
  return rows.length;
}

const RETENTION_MS = 90 * DAY;

/** Historial: las solicitudes resueltas y los compañeros dados de baja se conservan 90 días. */
export async function purgeHistory(db: Db, now: Date): Promise<void> {
  const before = new Date(now.getTime() - RETENTION_MS);
  await db.query("DELETE FROM requests WHERE status <> 'pendiente' AND resolved_at <= $1", [before]);
  await db.query("DELETE FROM partners WHERE status = 'revocado' AND revoked_at <= $1", [before]);
}
