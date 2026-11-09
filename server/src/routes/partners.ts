import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { createRequest, revokePartner } from "../approvals.js";
import { DAY, HttpError, limit, parse, schemas, type Deps } from "../http.js";
import { enqueueMail } from "../mailqueue.js";
import { partnerInviteMail } from "../mails.js";
import { issueToken } from "../tokens.js";
import { getUser, requireVerifiedUser } from "../users.js";
import { limitRequests } from "./requests.js";

const INVITE_TTL_MS = 7 * DAY;

/** Cuántos compañeros (aceptados o con invitación pendiente) puede tener una cuenta. */
export const MAX_PARTNERS = 3;

const inviteBody = z.object({ email: schemas.email });

export function partnerRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  app.post("/partners", async (req, reply) => {
    const body = parse(inviteBody, req.body);
    limit(deps, `partner-invite:${req.userId}`, 10, DAY);
    const me = await requireVerifiedUser(db, req.userId, "Confirma tu correo antes de invitar a un compañero");
    if (body.email === me.email) throw new HttpError(400, "Tu compañero tiene que ser otra persona");
    const now = deps.now();
    const created = await db.tx(async (tx) => {
      // Se bloquea la cuenta para que dos invitaciones a la vez no se salten el tope.
      await tx.query("SELECT 1 FROM users WHERE id = $1 FOR UPDATE", [req.userId]);
      const [live] = await tx.query<{ n: number }>("SELECT count(*)::int AS n FROM partners WHERE user_id = $1 AND status <> 'revocado'", [req.userId]);
      if ((live?.n ?? 0) >= MAX_PARTNERS) return "full" as const;
      const rows = await tx.query<{ id: string }>(
        "INSERT INTO partners (user_id, email, status, created_at) VALUES ($1,$2,'pendiente',$3) ON CONFLICT DO NOTHING RETURNING id",
        [req.userId, body.email, now],
      );
      const id = rows[0]?.id;
      if (!id) return null;
      const token = await issueToken(tx, "partner_confirm", req.userId, INVITE_TTL_MS, now, id);
      await enqueueMail(tx, partnerInviteMail(deps.config, body.email, me.email, token), now);
      return id;
    });
    if (created === "full") throw new HttpError(409, `Ya tienes ${MAX_PARTNERS} compañeros o invitaciones pendientes: quita uno antes de invitar a otro`);
    if (!created) throw new HttpError(409, "Esa persona ya es tu compañera o tiene una invitación pendiente");
    return reply.code(201).send({ id: created, email: body.email, status: "pendiente" });
  });

  app.get("/partners", async (req) => {
    const rows = await db.query<{ id: string; email: string; status: string }>("SELECT id, email, status FROM partners WHERE user_id = $1 AND status <> 'revocado'", [req.userId]);
    return { partners: rows };
  });

  /**
   * Las personas que me eligieron como compañero (I3). Solo cuenta quien demostró que el correo es suyo: si no, cualquiera podría
   * registrarse con el correo de otro y ver a quién ayuda. `mutual` dice si yo también tengo a esa persona de compañera.
   */
  app.get("/partners/helping", async (req) => {
    const me = await getUser(db, req.userId);
    if (!me.verified) return { helping: [] };
    const now = deps.now();
    const rows = await db.query<{ id: string; user_email: string; confirmed_at: Date; mutual: boolean; pending: number }>(
      `SELECT p.id, u.email AS user_email, p.confirmed_at,
              EXISTS (SELECT 1 FROM partners m WHERE m.user_id = $1 AND m.status = 'aceptado' AND m.email = u.email) AS mutual,
              (SELECT count(*)::int FROM requests r WHERE r.status = 'pendiente' AND r.expires_at > $3 AND (
                 r.partner_id = p.id
                 OR EXISTS (SELECT 1 FROM tokens t WHERE t.purpose = 'request_respond' AND t.subject_id = r.id AND t.partner_id = p.id AND t.used_at IS NULL AND t.expires_at > $3))) AS pending
         FROM partners p JOIN users u ON u.id = p.user_id
        WHERE p.status = 'aceptado' AND p.email = $2
        ORDER BY p.confirmed_at`,
      [req.userId, me.email, now],
    );
    return { helping: rows.map((r) => ({ id: r.id, email: r.user_email, since: r.confirmed_at, mutual: r.mutual, pending: r.pending })) };
  });

  /** Cancelar una invitación sin aceptar es libre; quitar a un compañero que ya aceptó necesita que él lo apruebe. */
  app.delete("/partners/:id", async (req, reply) => {
    const { id } = parse(schemas.idParams, req.params);
    const now = deps.now();
    const [partner] = await db.query<{ status: string }>("SELECT status FROM partners WHERE id = $1 AND user_id = $2 AND status <> 'revocado'", [id, req.userId]);
    if (!partner) throw new HttpError(404, "Compañero desconocido");
    if (partner.status === "pendiente") {
      await db.tx((tx) => revokePartner(tx, id, now));
      return reply.code(204).send();
    }
    limitRequests(deps, req.userId);
    const [open] = await db.query("SELECT 1 FROM requests WHERE partner_id = $1 AND server_action = 'partner_remove' AND status = 'pendiente' AND expires_at > $2", [id, now]);
    if (open) throw new HttpError(409, "Ya hay una solicitud pendiente para quitar a tu compañero");
    const created = await db.tx((tx) =>
      createRequest(deps, tx, {
        userId: req.userId,
        deviceId: null,
        partnerId: id,
        origin: { serverAction: "partner_remove" },
        summary: "Quitarle como compañero de responsabilidad",
      }),
    );
    return reply.code(202).send({ requestId: created.id, status: "pendiente", expiresAt: created.expiresAt });
  });
}
