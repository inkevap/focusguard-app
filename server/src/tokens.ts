import type { Db } from "./db.js";
import { hashToken, newToken } from "./security.js";

export type TokenPurpose = "refresh" | "password_reset" | "partner_confirm" | "request_respond" | "email_verify" | "pin_recovery";

/** Dueño del token y, si lo tiene, lo que autoriza (por ejemplo la solicitud o el compañero). */
export interface TokenOwner {
  userId: string;
  subjectId: string | null;
}

async function insertToken(db: Db, purpose: TokenPurpose, userId: string, subjectId: string | null, hash: string, ttlMs: number, now: Date, partnerId: string | null = null) {
  await db.query("INSERT INTO tokens (purpose, user_id, subject_id, token_hash, expires_at, created_at, partner_id) VALUES ($1,$2,$3,$4,$5,$6,$7)", [
    purpose,
    userId,
    subjectId,
    hash,
    new Date(now.getTime() + ttlMs),
    now,
    partnerId,
  ]);
}

/** Crea un token de un solo uso y devuelve su valor en claro (en la base solo queda el hash). */
export async function issueToken(db: Db, purpose: TokenPurpose, userId: string, ttlMs: number, now: Date, subjectId: string | null = null, partnerId: string | null = null): Promise<string> {
  const token = newToken();
  await insertToken(db, purpose, userId, subjectId, hashToken(token), ttlMs, now, partnerId);
  return token;
}

const owner = (r: { user_id: string; subject_id: string | null } | undefined): TokenOwner | null => (r ? { userId: r.user_id, subjectId: r.subject_id } : null);

/** Mira el token sin gastarlo: sirve para descartar falsos antes de trabajo caro y para mostrar la página de confirmación. */
export async function peekToken(db: Db, purpose: TokenPurpose, token: string, now: Date): Promise<TokenOwner | null> {
  const rows = await db.query<{ user_id: string; subject_id: string | null }>(
    "SELECT user_id, subject_id FROM tokens WHERE purpose = $1 AND token_hash = $2 AND used_at IS NULL AND expires_at > $3",
    [purpose, hashToken(token), now],
  );
  return owner(rows[0]);
}

/** Gasta el token en una sola sentencia atómica. Devuelve su dueño, o `null` si no vale, ya se usó o caducó. */
export async function consumeToken(db: Db, purpose: TokenPurpose, token: string, now: Date): Promise<TokenOwner | null> {
  const rows = await db.query<{ user_id: string; subject_id: string | null }>(
    "UPDATE tokens SET used_at = $3 WHERE purpose = $1 AND token_hash = $2 AND used_at IS NULL AND expires_at > $3 RETURNING user_id, subject_id",
    [purpose, hashToken(token), now],
  );
  return owner(rows[0]);
}

/** Invalida los tokens aún sin usar de un sujeto (por ejemplo, al cancelar una solicitud). */
export async function spendBySubject(db: Db, purpose: TokenPurpose, subjectId: string, now: Date): Promise<void> {
  await db.query("UPDATE tokens SET used_at = $3 WHERE purpose = $1 AND subject_id = $2 AND used_at IS NULL", [purpose, subjectId, now]);
}

// Un código corto solo vale para un usuario: se guarda el hash de `usuario:código`.
const codeToken = (userId: string, code: string) => `${userId}:${code}`;

/** Código corto (por ejemplo 6 cifras) en vez de un token largo. Sustituye al anterior del mismo usuario y finalidad. */
export async function issueCode(db: Db, purpose: TokenPurpose, userId: string, code: string, ttlMs: number, now: Date): Promise<void> {
  await revokeTokens(db, purpose, userId);
  await insertToken(db, purpose, userId, null, hashToken(codeToken(userId, code)), ttlMs, now);
}

export const consumeCode = (db: Db, purpose: TokenPurpose, userId: string, code: string, now: Date) => consumeToken(db, purpose, codeToken(userId, code), now);

export async function revokeTokens(db: Db, purpose: TokenPurpose, userId: string): Promise<void> {
  await db.query("DELETE FROM tokens WHERE purpose = $1 AND user_id = $2", [purpose, userId]);
}

/** Anota qué token sustituyó a este (sellado), para poder repetirlo si la respuesta se perdió. */
export async function recordSuccessor(db: Db, purpose: TokenPurpose, token: string, successorSealed: string): Promise<void> {
  await db.query("UPDATE tokens SET successor = $3 WHERE purpose = $1 AND token_hash = $2", [purpose, hashToken(token), successorSealed]);
}

/** Si el token se gastó hace menos de `graceMs`, el sustituto sellado y el dueño: es un reintento, no un robo. */
export async function recentSuccessor(db: Db, purpose: TokenPurpose, token: string, now: Date, graceMs: number): Promise<{ userId: string; sealed: string } | null> {
  const rows = await db.query<{ user_id: string; successor: string | null }>(
    "SELECT user_id, successor FROM tokens WHERE purpose = $1 AND token_hash = $2 AND used_at >= $4 AND expires_at > $3 AND successor IS NOT NULL",
    [purpose, hashToken(token), now, new Date(now.getTime() - graceMs)],
  );
  const row = rows[0];
  return row?.successor ? { userId: row.user_id, sealed: row.successor } : null;
}

/** Usuario dueño de un token gastado antes de `spentBefore` y aún vigente: señal de que alguien lo reutiliza. */
export async function spentOwner(db: Db, purpose: TokenPurpose, token: string, now: Date, spentBefore: Date): Promise<string | null> {
  const rows = await db.query<{ user_id: string }>(
    "SELECT user_id FROM tokens WHERE purpose = $1 AND token_hash = $2 AND used_at < $4 AND expires_at > $3",
    [purpose, hashToken(token), now, spentBefore],
  );
  return rows[0]?.user_id ?? null;
}

export async function purgeExpiredTokens(db: Db, now: Date): Promise<void> {
  await db.query("DELETE FROM tokens WHERE expires_at <= $1", [now]);
}
