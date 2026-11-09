import type { Db } from "./db.js";
import { DAY, type Deps } from "./http.js";
import type { Mail } from "./mailer.js";

// Debe coincidir con el índice parcial `attempts < 8` de la migración 2.
const MAX_ATTEMPTS = 8;
const BATCH = 20;
const CONCURRENCY = 5;
// Un envío en curso se reserva este rato; si el proceso muere a medias, se reintenta después.
const CLAIM_MS = 5 * 60_000;
const KEEP_MS = 2 * DAY;

export interface EnqueueOptions {
  /** No enviar antes de esta hora (avisos agrupados). */
  notBefore?: Date;
  /** Si ya hay un correo pendiente con esta clave, no se encola otro (avisos periódicos). */
  dedupeKey?: string;
}

/** Encola un correo; puede ir dentro de la transacción que lo provoca, así no se pierde ni se envía sin su causa. */
export async function enqueueMail(db: Db, mail: Mail, now: Date, opts: EnqueueOptions = {}): Promise<void> {
  await db.query(
    "INSERT INTO mail_outbox (to_addr, subject, body, html, next_at, dedupe_key, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7) ON CONFLICT DO NOTHING",
    [mail.to, mail.subject, mail.text, mail.html ?? null, opts.notBefore ?? now, opts.dedupeKey ?? null, now],
  );
}

/** Espera antes del siguiente intento: 1, 2, 4… minutos. */
const backoffMs = (attempts: number) => 60_000 * 2 ** (attempts - 1);

type Row = { id: string; to_addr: string; subject: string; body: string; html: string | null; attempts: number };

/** Envía los correos que toca. Devuelve cuántos salieron. Varias instancias no se pisan: cada una reserva su lote. */
export async function drainMailQueue(deps: Pick<Deps, "db" | "mailer" | "now" | "log">): Promise<number> {
  const now = deps.now();
  const batch = await deps.db.query<Row>(
    `UPDATE mail_outbox SET next_at = $2 WHERE id IN (
       SELECT id FROM mail_outbox WHERE next_at <= $1 AND attempts < $4 ORDER BY next_at LIMIT $3 FOR UPDATE SKIP LOCKED
     ) RETURNING id, to_addr, subject, body, html, attempts`,
    [now, new Date(now.getTime() + CLAIM_MS), BATCH, MAX_ATTEMPTS],
  );
  let sent = 0;
  for (let i = 0; i < batch.length; i += CONCURRENCY) {
    const results = await Promise.allSettled(batch.slice(i, i + CONCURRENCY).map((row) => deliver(deps, row)));
    for (const r of results) {
      if (r.status === "fulfilled" && r.value) sent++;
      else if (r.status === "rejected") deps.log.error(r.reason, "no se pudo anotar el resultado de un correo");
    }
  }
  return sent;
}

/** Envía un correo y lo borra enseguida, para que un corte a mitad de lote no reenvíe los ya entregados. */
async function deliver(deps: Pick<Deps, "db" | "mailer" | "now" | "log">, row: Row): Promise<boolean> {
  try {
    await deps.mailer.send({ to: row.to_addr, subject: row.subject, text: row.body, ...(row.html ? { html: row.html } : {}) });
  } catch (e) {
    await recordFailure(deps, row, e);
    return false;
  }
  await deps.db.query("DELETE FROM mail_outbox WHERE id = $1", [row.id]);
  return true;
}

async function recordFailure(deps: Pick<Deps, "db" | "now" | "log">, row: Row, e: unknown) {
  const attempts = row.attempts + 1;
  const message = e instanceof Error ? e.message.slice(0, 300) : "error desconocido";
  await deps.db.query("UPDATE mail_outbox SET attempts = $2, next_at = $3, last_error = $4 WHERE id = $1", [
    row.id,
    attempts,
    new Date(deps.now().getTime() + backoffMs(attempts)),
    message,
  ]);
  // Es la alerta de «el envío de correo falla» de BACKEND.md §7: se ve en el registro, sin el contenido.
  deps.log.error(`Correo ${row.id} sin enviar (intento ${attempts}/${MAX_ATTEMPTS}): ${message}`);
}

/** Quita lo que lleva días sin salir: el cuerpo puede llevar un enlace con token. */
export async function purgeMailQueue(db: Db, now: Date): Promise<void> {
  await db.query("DELETE FROM mail_outbox WHERE created_at <= $1", [new Date(now.getTime() - KEEP_MS)]);
}
