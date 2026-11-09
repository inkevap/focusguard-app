import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { DAY, HOUR, HttpError, limit, MINUTE, parse, type Deps } from "../http.js";
import { enqueueMail } from "../mailqueue.js";
import { pinRecoveryMail } from "../mails.js";
import { newCode } from "../security.js";
import { consumeCode, issueCode } from "../tokens.js";
import { requireVerifiedUser } from "../users.js";

const PIN_CODE_MINUTES = 15;

const codeBody = z.object({ code: z.string().regex(/^\d{6}$/) });
const wrongBlockBody = z.object({ kind: z.enum(["app", "domain", "word"]), value: z.string().trim().min(1).max(200) });

export function recoveryRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  /** Recuperación del PIN (H9): el código llega al correo confirmado del usuario; el servidor no guarda el PIN. */
  app.post("/recovery/pin-code", async (req, reply) => {
    limit(deps, `pin-code:${req.userId}`, 3, DAY);
    const user = await requireVerifiedUser(db, req.userId, "Confirma tu correo para poder recuperar el PIN");
    const code = newCode();
    const now = deps.now();
    await db.tx(async (tx) => {
      await issueCode(tx, "pin_recovery", req.userId, code, PIN_CODE_MINUTES * MINUTE, now);
      await enqueueMail(tx, pinRecoveryMail(user.email, code, PIN_CODE_MINUTES), now);
    });
    return reply.code(202).send({ ok: true });
  });

  app.post("/recovery/pin-code/verify", async (req) => {
    const { code } = parse(codeBody, req.body);
    limit(deps, `pin-verify:${req.userId}`, 5, HOUR);
    if (!(await consumeCode(db, "pin_recovery", req.userId, code, deps.now()))) throw new HttpError(400, "El código no vale o caducó");
    return { ok: true };
  });

  /** Reporte de un bloqueo erróneo (B10); la lista se revisa a mano. */
  app.post("/reports/wrong-block", async (req, reply) => {
    const body = parse(wrongBlockBody, req.body);
    limit(deps, `wrong-block:${req.userId}`, 20, DAY);
    await db.query("INSERT INTO wrong_block_reports (user_id, kind, value, created_at) VALUES ($1,$2,$3,$4)", [req.userId, body.kind, body.value, deps.now()]);
    return reply.code(201).send({ ok: true });
  });
}
