import type { FastifyBaseLogger, FastifyReply, FastifyRequest } from "fastify";
import jwt from "jsonwebtoken";
import { z } from "zod";
import type { Config } from "./config.js";
import type { Db } from "./db.js";
import type { Mailer } from "./mailer.js";
import type { Push } from "./push.js";
import { pageHtml } from "./theme.js";
import type { RateLimiter } from "./ratelimit.js";

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

/** Esquemas de entrada compartidos: una sola definición para que no diverjan entre rutas. */
const token = z.string().min(1).max(200);
export const schemas = {
  email: z.string().trim().toLowerCase().email().max(254),
  token,
  tokenObject: z.object({ token }),
  idParams: z.object({ id: z.string().uuid() }),
  /** Versión del sobre cifrado de una copia o de lo sincronizado (`X-Backup-Format`, `X-Sync-Format`). */
  format: z.coerce.number().int().min(1).max(1000),
};

export class HttpError extends Error {
  /** `code` es un identificador estable para que la app distinga casos sin comparar textos. */
  constructor(readonly status: number, message: string, readonly code?: string) {
    super(message);
  }
}

export interface Deps {
  config: Config;
  db: Db;
  mailer: Mailer;
  push: Push;
  log: FastifyBaseLogger;
  limiter: RateLimiter;
  /** Reloj inyectable para probar caducidades. */
  now: () => Date;
}

export function parse<S extends z.ZodType>(schema: S, data: unknown): z.infer<S> {
  const result = schema.safeParse(data);
  if (!result.success) throw new HttpError(400, "Datos no válidos");
  return result.data;
}

export function limit(deps: Deps, key: string, max: number, windowMs: number) {
  if (!deps.limiter.allow(key, max, windowMs)) throw new HttpError(429, "Demasiados intentos, espera un rato");
}

export const ACCESS_TTL_S = 15 * 60;

export function signAccess(deps: Deps, userId: string): string {
  const iat = Math.floor(deps.now().getTime() / 1000);
  return jwt.sign({ iat, exp: iat + ACCESS_TTL_S }, deps.config.JWT_SECRET, { subject: userId, algorithm: "HS256" });
}

declare module "fastify" {
  interface FastifyRequest {
    /** Rellenado por el gancho de autenticación en las rutas privadas. */
    userId: string;
  }
  interface FastifyContextConfig {
    /** Ruta que sirve una página: sus errores también se muestran como página. */
    html?: boolean;
  }
}

/** Devuelve el id de usuario del `Authorization: Bearer`, o lanza 401. */
export function requireUser(deps: Deps, req: FastifyRequest): string {
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  try {
    const payload = jwt.verify(token, deps.config.JWT_SECRET, { algorithms: ["HS256"], clockTimestamp: Math.floor(deps.now().getTime() / 1000) });
    if (typeof payload === "object" && typeof payload.sub === "string") return payload.sub;
  } catch {
    // cae en el 401
  }
  throw new HttpError(401, "Sesión no válida");
}

export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

/** Páginas con tokens: sin caché, sin enviar la dirección como referencia y sin scripts. */
export function sendPage(reply: FastifyReply, status: number, title: string, body: string) {
  return reply
    .code(status)
    .type("text/html; charset=utf-8")
    .headers({
      "cache-control": "no-store",
      "referrer-policy": "no-referrer",
      "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'",
    })
    .send(page(title, body));
}

const page = pageHtml;

// ---- Páginas que abre una persona desde un enlace del correo ----

/** Ruta que sirve una página: sus errores también se muestran como página. */
export const htmlRoute = { config: { html: true } } as const;

// Los escáneres de enlaces de los correos hacen GET desde pocas IP: las visitas tienen un cupo más holgado que las acciones.
export const throttleView = (deps: Deps, ip: string) => limit(deps, `link-view-ip:${ip}`, 600, HOUR);
// Datos públicos de las apps: cupo holgado porque los móviles comparten IP (CGNAT) y cada sincronización son varias peticiones baratas.
export const throttleData = (deps: Deps, ip: string) => limit(deps, `data-ip:${ip}`, 600, HOUR);
export const throttleAct = (deps: Deps, ip: string) => limit(deps, `link-act-ip:${ip}`, 60, HOUR);

export const invalidLink = (reply: FastifyReply, message = "El enlace no vale, caducó o ya se usó.") => sendPage(reply, 400, "Enlace no válido", `<p>${escapeHtml(message)}</p>`);
