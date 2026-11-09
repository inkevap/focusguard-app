import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { ACCESS_TTL_S, DAY, escapeHtml, HOUR, HttpError, limit, parse, schemas, sendPage, signAccess, type Deps } from "../http.js";
import type { Db } from "../db.js";
import { enqueueMail } from "../mailqueue.js";
import { resetMail, verifyEmailMail } from "../mails.js";
import { hashPassword, seal, unseal, verifyPassword } from "../security.js";
import { consumeToken, issueToken, peekToken, recentSuccessor, recordSuccessor, revokeTokens, spentOwner } from "../tokens.js";

const REFRESH_TTL_MS = 60 * 24 * HOUR;
const RESET_TTL_MS = HOUR;
const REUSE_GRACE_MS = 60_000;
const VERIFY_TTL_MS = 7 * DAY;

const { email, token } = schemas;
const password = z.string().min(8).max(128);

const registerBody = z.object({ email, password });
// Al iniciar sesión no se exige la longitud mínima: solo se compara.
const loginBody = z.object({ email, password: z.string().max(128) });
const refreshBody = z.object({ refreshToken: token });
const resetBody = z.object({ email });
const confirmBody = z.object({ token, password });
const tokenQuery = z.object({ token });

export function authRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  async function issueSession(userId: string, conn: Db = db) {
    const refreshToken = await issueToken(conn, "refresh", userId, REFRESH_TTL_MS, deps.now());
    return { accessToken: signAccess(deps, userId), refreshToken, expiresIn: ACCESS_TTL_S };
  }

  async function sendVerification(userId: string, address: string) {
    const now = deps.now();
    await db.tx(async (tx) => {
      const verifyToken = await issueToken(tx, "email_verify", userId, VERIFY_TTL_MS, now);
      await enqueueMail(tx, verifyEmailMail(deps.config, address, verifyToken), now);
    });
  }

  app.post("/auth/register", async (req, reply) => {
    limit(deps, `register:${req.ip}`, 10, HOUR);
    const body = parse(registerBody, req.body);
    const hash = await hashPassword(body.password);
    const rows = await db.query<{ id: string }>(
      "INSERT INTO users (email, password_hash, created_at) VALUES ($1,$2,$3) ON CONFLICT (email) DO NOTHING RETURNING id",
      [body.email, hash, deps.now()],
    );
    if (!rows[0]) throw new HttpError(409, "Ese correo ya tiene cuenta");
    await sendVerification(rows[0].id, body.email);
    return reply.code(201).send(await issueSession(rows[0].id));
  });

  app.post("/auth/login", async (req) => {
    const body = parse(loginBody, req.body);
    limit(deps, `login-ip:${req.ip}`, 30, HOUR);
    limit(deps, `login:${body.email}`, 10, HOUR);
    const [user] = await db.query<{ id: string; password_hash: string }>("SELECT id, password_hash FROM users WHERE email = $1", [body.email]);
    if (!(await verifyPassword(user?.password_hash ?? null, body.password)) || !user) throw new HttpError(401, "Correo o contraseña incorrectos");
    return issueSession(user.id);
  });

  /**
   * El token de refresco vale una vez y se sustituye por otro. Si la respuesta se perdió, un reintento del mismo token dentro
   * de un minuto recibe el mismo sustituto (sin crear otro); pasado ese margen, un token gastado se toma por copiado y se
   * cierran todas las sesiones.
   */
  app.post("/auth/refresh", async (req) => {
    const { refreshToken } = parse(refreshBody, req.body);
    const now = deps.now();
    // Gastar el token y emitir el nuevo van juntos: si falla lo segundo, el token viejo sigue valiendo.
    const session = await db.tx(async (tx) => {
      const owner = await consumeToken(tx, "refresh", refreshToken, now);
      if (!owner) return null;
      const next = await issueSession(owner.userId, tx);
      await recordSuccessor(tx, "refresh", refreshToken, seal(refreshToken, next.refreshToken));
      return next;
    });
    if (session) return session;
    const retry = await recentSuccessor(db, "refresh", refreshToken, now, REUSE_GRACE_MS);
    const replayed = retry ? unseal(refreshToken, retry.sealed) : null;
    if (retry && replayed) return { accessToken: signAccess(deps, retry.userId), refreshToken: replayed, expiresIn: ACCESS_TTL_S };
    const victim = await spentOwner(db, "refresh", refreshToken, now, new Date(now.getTime() - REUSE_GRACE_MS));
    if (victim) await revokeTokens(db, "refresh", victim);
    throw new HttpError(401, "Sesión caducada, inicia sesión otra vez");
  });

  app.post("/auth/password/reset", async (req, reply) => {
    const body = parse(resetBody, req.body);
    limit(deps, `reset-ip:${req.ip}`, 10, HOUR);
    limit(deps, `reset:${body.email}`, 3, HOUR);
    const [user] = await db.query<{ id: string }>("SELECT id FROM users WHERE email = $1", [body.email]);
    if (user) {
      const now = deps.now();
      await db.tx(async (tx) => {
        const resetToken = await issueToken(tx, "password_reset", user.id, RESET_TTL_MS, now);
        await enqueueMail(tx, resetMail(deps.config, body.email, resetToken), now);
      });
    }
    // Misma respuesta exista o no la cuenta, para no revelar quién está registrado.
    return reply.code(202).send({ ok: true });
  });

  /** Límite, validación y cambio de contraseña; las dos rutas de confirmación solo difieren en cómo responden. */
  async function confirmReset(req: FastifyRequest): Promise<boolean> {
    limit(deps, `reset-confirm:${req.ip}`, 20, HOUR);
    const body = parse(confirmBody, req.body);
    const now = deps.now();
    // Descarta tokens falsos antes de calcular el hash de la contraseña, que es lo caro.
    if (!(await peekToken(db, "password_reset", body.token, now))) return false;
    const hash = await hashPassword(body.password);
    return db.tx(async (tx) => {
      const owner = await consumeToken(tx, "password_reset", body.token, now);
      if (!owner) return false;
      await tx.query("UPDATE users SET password_hash = $2 WHERE id = $1", [owner.userId, hash]);
      await revokeTokens(tx, "refresh", owner.userId);
      return true;
    });
  }

  app.post("/auth/password/reset/confirm", async (req) => {
    if (!(await confirmReset(req))) throw new HttpError(400, "El enlace no vale o caducó");
    return { ok: true };
  });

  // Página a la que lleva el enlace del correo. El formulario envía por POST, no por GET, para que el token no quede en el historial al enviar.
  app.get("/reset", { config: { html: true } }, async (req, reply) => {
    const { token: t } = parse(tokenQuery, req.query);
    return sendPage(
      reply,
      200,
      "Nueva contraseña",
      `<form method="post" action="/reset"><input type="hidden" name="token" value="${escapeHtml(t)}">` +
        `<label>Contraseña nueva (8 caracteres o más)<input type="password" name="password" minlength="8" maxlength="128" required autocomplete="new-password"></label>` +
        `<button type="submit">Guardar</button></form>`,
    );
  });

  app.post("/reset", { config: { html: true } }, async (req, reply) => {
    const ok = await confirmReset(req);
    return ok
      ? sendPage(reply, 200, "Contraseña cambiada", "<p>Ya puedes iniciar sesión en la app con la contraseña nueva.</p>")
      : sendPage(reply, 400, "Enlace no válido", "<p>El enlace no vale o caducó. Pide otro desde la app.</p>");
  });
}
