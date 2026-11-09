import type { Db } from "./db.js";
import { HttpError } from "./http.js";

export interface UserRow {
  email: string;
  verified: boolean;
}

export async function getUser(db: Db, userId: string): Promise<UserRow> {
  const [row] = await db.query<{ email: string; email_verified_at: Date | null }>("SELECT email, email_verified_at FROM users WHERE id = $1", [userId]);
  // Un token de acceso aún válido de una cuenta ya borrada.
  if (!row) throw new HttpError(401, "Sesión no válida");
  return { email: row.email, verified: row.email_verified_at !== null };
}

/** Para lo que solo debe hacer quien demostró que el correo es suyo. */
export async function requireVerifiedUser(db: Db, userId: string, message: string): Promise<UserRow> {
  const user = await getUser(db, userId);
  if (!user.verified) throw new HttpError(403, message);
  return user;
}
