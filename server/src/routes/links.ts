import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { effectiveStatus, resolveRequest, type RequestStatus } from "../approvals.js";
import { escapeHtml, htmlRoute, invalidLink, parse, schemas, sendPage, throttleAct, throttleView, type Deps } from "../http.js";
import { consumeToken, peekToken } from "../tokens.js";

const decisionParams = z.object({ token: schemas.token, decision: z.enum(["approve", "reject"]) });

const decisions = {
  approve: { verb: "Aprobar", done: "Has aprobado el cambio." },
  reject: { verb: "Rechazar", done: "Has rechazado el cambio." },
} as const;

const confirmForm = (action: string, button: string, fields = "", kind = "") =>
  `<form method="post" action="${escapeHtml(action)}">${fields}<button type="submit"${kind ? ` class="${kind}"` : ""}>${escapeHtml(button)}</button></form>`;

/**
 * Páginas que abren el compañero desde el correo. Un GET nunca cambia nada: muestra los datos y un botón que envía por POST,
 * para que un escáner de enlaces del correo no apruebe sin querer.
 */
export function linkPages(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  type RequestRow = { summary: string; status: RequestStatus; expires_at: Date; email: string; device: string | null };
  const requestInfo = async (requestId: string) =>
    (
      await db.query<RequestRow>(
        `SELECT r.summary, r.status, r.expires_at, u.email, d.name AS device
           FROM requests r JOIN users u ON u.id = r.user_id LEFT JOIN devices d ON d.id = r.device_id WHERE r.id = $1`,
        [requestId],
      )
    )[0];
  const requestSummary = (row: RequestRow) =>
    `<p><strong>${escapeHtml(row.email)}</strong> quiere hacer este cambio en «${escapeHtml(row.device ?? "su cuenta")}»:</p><blockquote>${escapeHtml(row.summary)}</blockquote>`;

  // ---- Invitación de compañero ----
  app.get("/partners/confirm", htmlRoute, async (req, reply) => {
    throttleView(deps, req.ip);
    const { token } = parse(schemas.tokenObject, req.query);
    const owner = await peekToken(db, "partner_confirm", token, deps.now());
    if (!owner) return invalidLink(reply);
    const [row] = await db.query<{ email: string }>("SELECT email FROM users WHERE id = $1", [owner.userId]);
    return sendPage(
      reply,
      200,
      "Ser compañero de responsabilidad",
      `<p><strong>${escapeHtml(row!.email)}</strong> te pide que seas su compañero en FocusGuard.</p>` +
        `<p>Recibirás un correo cuando quiera quitar o debilitar una protección, y solo se hará si lo apruebas. No necesitas cuenta.</p>` +
        confirmForm("/partners/confirm", "Aceptar", `<input type="hidden" name="token" value="${escapeHtml(token)}">`),
    );
  });

  app.post("/partners/confirm", htmlRoute, async (req, reply) => {
    throttleAct(deps, req.ip);
    const { token } = parse(schemas.tokenObject, req.body);
    const now = deps.now();
    const userId = await db.tx(async (tx) => {
      const owner = await consumeToken(tx, "partner_confirm", token, now);
      if (!owner?.subjectId) return null;
      const rows = await tx.query("UPDATE partners SET status = 'aceptado', confirmed_at = $2 WHERE id = $1 AND status = 'pendiente' RETURNING id", [owner.subjectId, now]);
      return rows[0] ? owner.userId : null;
    });
    if (!userId) return invalidLink(reply);
    await deps.push.notifyUser(userId, { type: "partner_confirmed" });
    return sendPage(reply, 200, "Listo", "<p>Ya eres compañero de responsabilidad. Te escribiremos cuando haya algo que aprobar.</p>");
  });

  // ---- Respuesta a una solicitud ----
  // La página a la que lleva el correo: el cambio pedido y los dos botones. Un GET no cambia nada.
  app.get("/r/:token", htmlRoute, async (req, reply) => {
    throttleView(deps, req.ip);
    const { token } = parse(schemas.tokenObject, req.params);
    const now = deps.now();
    const owner = await peekToken(db, "request_respond", token, now);
    if (!owner?.subjectId) return invalidLink(reply);
    const row = await requestInfo(owner.subjectId);
    if (!row || effectiveStatus(row, now) !== "pendiente") return invalidLink(reply);
    const base = `/r/${encodeURIComponent(token)}`;
    return sendPage(
      reply,
      200,
      "Solicitud de aprobación",
      requestSummary(row) +
        confirmForm(`${base}/approve`, "Aprobar el cambio") +
        confirmForm(`${base}/reject`, "Rechazar el cambio", "", "secondary") +
        `<p class="muted">Si dudas, habla primero con ${escapeHtml(row.email)}: aprobar quita una protección que se puso para ayudarle.</p>`,
    );
  });

  app.get("/r/:token/:decision", htmlRoute, async (req, reply) => {
    throttleView(deps, req.ip);
    const { token, decision } = parse(decisionParams, req.params);
    const now = deps.now();
    const owner = await peekToken(db, "request_respond", token, now);
    if (!owner?.subjectId) return invalidLink(reply);
    const row = await requestInfo(owner.subjectId);
    if (!row || effectiveStatus(row, now) !== "pendiente") return invalidLink(reply);
    return sendPage(
      reply,
      200,
      `${decisions[decision].verb} solicitud`,
      requestSummary(row) +
        confirmForm(`/r/${encodeURIComponent(token)}/${decision}`, `${decisions[decision].verb} el cambio`, "", decision === "reject" ? "secondary" : ""),
    );
  });

  app.post("/r/:token/:decision", htmlRoute, async (req, reply) => {
    throttleAct(deps, req.ip);
    const { token, decision } = parse(decisionParams, req.params);
    const outcome = await db.tx((tx) => resolveRequest(tx, token, decision, deps.now()));
    if (!outcome) return invalidLink(reply);
    for (const requestId of outcome.requestIds) await deps.push.notifyUser(outcome.userId, { type: "request_resolved", requestId });
    return sendPage(reply, 200, "Respuesta enviada", `<p>${decisions[decision].done} Ya puedes cerrar esta página.</p>`);
  });
}
