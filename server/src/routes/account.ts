import type { FastifyInstance } from "fastify";
import { DAY, escapeHtml, htmlRoute, HttpError, invalidLink, limit, parse, schemas, sendPage, throttleAct, throttleView, type Deps } from "../http.js";
import { enqueueMail } from "../mailqueue.js";
import { verifyEmailMail } from "../mails.js";
import { consumeToken, issueToken, peekToken } from "../tokens.js";
import { getUser } from "../users.js";

const VERIFY_TTL_MS = 7 * DAY;

/** Datos de la cuenta y confirmación del correo. */
export function accountRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  app.get("/me", async (req) => {
    const user = await getUser(db, req.userId);
    return { email: user.email, emailVerified: user.verified };
  });

  app.post("/auth/email/verify/resend", async (req, reply) => {
    limit(deps, `verify-resend:${req.userId}`, 3, DAY);
    const user = await getUser(db, req.userId);
    if (user.verified) throw new HttpError(409, "Tu correo ya está confirmado");
    const now = deps.now();
    await db.tx(async (tx) => {
      const token = await issueToken(tx, "email_verify", req.userId, VERIFY_TTL_MS, now);
      await enqueueMail(tx, verifyEmailMail(deps.config, user.email, token), now);
    });
    return reply.code(202).send({ ok: true });
  });
}

/** Páginas públicas del enlace del correo. El GET solo muestra; confirma el POST. */
export function verifyPages(app: FastifyInstance, deps: Deps) {
  const { db } = deps;
  app.get("/verify", htmlRoute, async (req, reply) => {
    throttleView(deps, req.ip);
    const { token } = parse(schemas.tokenObject, req.query);
    if (!(await peekToken(db, "email_verify", token, deps.now()))) return invalidLink(reply, "El enlace no vale o caducó. Pide otro desde la app.");
    return sendPage(
      reply,
      200,
      "Confirmar correo",
      `<form method="post" action="/verify"><input type="hidden" name="token" value="${escapeHtml(token)}"><button type="submit">Confirmar mi correo</button></form>`,
    );
  });

  app.post("/verify", htmlRoute, async (req, reply) => {
    throttleAct(deps, req.ip);
    const { token } = parse(schemas.tokenObject, req.body);
    const now = deps.now();
    const ok = await db.tx(async (tx) => {
      const owner = await consumeToken(tx, "email_verify", token, now);
      if (!owner) return false;
      await tx.query("UPDATE users SET email_verified_at = $2 WHERE id = $1 AND email_verified_at IS NULL", [owner.userId, now]);
      return true;
    });
    return ok
      ? sendPage(reply, 200, "Correo confirmado", "<p>Listo. Ya puedes volver a la app.</p>")
      : invalidLink(reply, "El enlace no vale o caducó. Pide otro desde la app.");
  });
}
