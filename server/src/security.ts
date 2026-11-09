import { createCipheriv, createDecipheriv, createHash, randomBytes, randomInt } from "node:crypto";
import argon2 from "argon2";

/** Token aleatorio de 256 bits, apto para URL. */
export function newToken(): string {
  return randomBytes(32).toString("base64url");
}

// Un valor sellado solo lo abre quien conoce `secret`: la clave sale de él. Sirve para guardar el token que sustituyó a otro y
// poder repetirlo si la respuesta se perdió, sin que la base lo pueda leer (`secret` es el token viejo, del que solo hay hash).
const sealKey = (secret: string) => createHash("sha256").update(`seal:${secret}`).digest();

export function seal(secret: string, plain: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", sealKey(secret), iv);
  const body = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), body]).toString("base64url");
}

export function unseal(secret: string, sealed: string): string | null {
  try {
    const raw = Buffer.from(sealed, "base64url");
    const decipher = createDecipheriv("aes-256-gcm", sealKey(secret), raw.subarray(0, 12));
    decipher.setAuthTag(raw.subarray(12, 28));
    return Buffer.concat([decipher.update(raw.subarray(28)), decipher.final()]).toString("utf8");
  } catch {
    return null;
  }
}

/** Código de 6 cifras para cosas de vida corta que se teclean a mano (recuperar el PIN). */
export const newCode = () => String(randomInt(0, 1_000_000)).padStart(6, "0");

/** Los tokens solo se guardan con hash: una filtración de la base no sirve para usarlos. */
export function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

// Configuración de argon2id recomendada por OWASP (19 MiB, 2 pasadas, 1 hilo).
let cost = { memoryCost: 19_456, timeCost: 2, parallelism: 1 };

/** Solo para las pruebas: baja el coste para que no tarden. Producción no lo llama. */
export function useCheapPasswordHashing() {
  cost = { memoryCost: 1024, timeCost: 1, parallelism: 1 };
}

export const hashPassword = (password: string) => argon2.hash(password, { type: argon2.argon2id, ...cost });

// Hash de relleno para que iniciar sesión con un correo desconocido tarde lo mismo que con uno conocido.
let decoy: Promise<string> | undefined;
const decoyHash = () => (decoy ??= hashPassword("relleno-sin-uso"));

export async function verifyPassword(hash: string | null, password: string): Promise<boolean> {
  const ok = await argon2.verify(hash ?? (await decoyHash()), password).catch(() => false);
  return ok && hash !== null;
}
