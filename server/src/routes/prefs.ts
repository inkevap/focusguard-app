import type { FastifyInstance, FastifyReply } from "fastify";
import { z } from "zod";
import { escapeHtml, htmlRoute, invalidLink, parse, schemas, sendPage, throttleAct, throttleView, type Deps } from "../http.js";
import { NOTICE_KINDS, PREF_KEYS, type PrefKey } from "../noticeKinds.js";
import { diffPrefs, effectivePrefs, MAX_INACTIVE_HOURS, MIN_INACTIVE_HOURS, parsePrefsToken, type Prefs } from "../prefs.js";

const formBody = z.object({ token: schemas.token }).catchall(z.string());

/**
 * Página del compañero para elegir qué avisos recibe y con qué plazos (I4). El enlace del pie de cada aviso va firmado por
 * compañero y se puede usar muchas veces; deja de valer si el compañero se da de baja.
 */
export function prefsPages(app: FastifyInstance, deps: Deps) {
  const { db } = deps;
  const invalid = (reply: FastifyReply) => invalidLink(reply, "El enlace no vale. Cualquier aviso nuevo trae uno que sirve.");

  async function load(token: string) {
    const partnerId = parsePrefsToken(deps.config.JWT_SECRET, token);
    if (!partnerId) return null;
    const [row] = await db.query<{ prefs: unknown; user_email: string }>(
      "SELECT p.prefs, u.email AS user_email FROM partners p JOIN users u ON u.id = p.user_id WHERE p.id = $1 AND p.status = 'aceptado'",
      [partnerId],
    );
    return row ? { partnerId, prefs: effectivePrefs(row.prefs), userEmail: row.user_email } : null;
  }

  const number = (name: string, label: string, value: number, min: number, max: number) =>
    `<label style="display:block;margin:.8rem 0">${escapeHtml(label)}<input type="number" name="${name}" value="${value}" min="${min}" max="${max}" required></label>`;

  const form = (token: string, user: string, prefs: Prefs, note = "") =>
    `<p>Avisos sobre <strong>${escapeHtml(user)}</strong>.</p>${note}` +
    `<form method="post" action="/prefs"><input type="hidden" name="token" value="${escapeHtml(token)}">` +
    PREF_KEYS.map((k) => `<label style="display:block;margin:.6rem 0"><input type="checkbox" name="${k}" ${prefs.flags[k] ? "checked" : ""} style="width:auto;margin-right:.5rem">${escapeHtml(NOTICE_KINDS[k].label)}</label>`).join("") +
    number("inactive_hours", `Avisar si la app no da señales durante (horas, ${MIN_INACTIVE_HOURS} a ${MAX_INACTIVE_HOURS})`, prefs.inactiveHours, MIN_INACTIVE_HOURS, MAX_INACTIVE_HOURS) +
    number("report_hour", "Hora a la que llega el informe diario (0 a 23, hora UTC)", prefs.reportHour, 0, 23) +
    `<button type="submit">Guardar</button></form>`;

  app.get("/prefs", htmlRoute, async (req, reply) => {
    throttleView(deps, req.ip);
    const { token } = parse(schemas.tokenObject, req.query);
    const found = await load(token);
    return found ? sendPage(reply, 200, "Tus avisos", form(token, found.userEmail, found.prefs)) : invalid(reply);
  });

  app.post("/prefs", htmlRoute, async (req, reply) => {
    throttleAct(deps, req.ip);
    const body = parse(formBody, req.body);
    const found = await load(body.token);
    if (!found) return invalid(reply);
    // Un formulario solo envía las casillas marcadas: lo que no llega, se apaga. Los números se validan: fuera de rango se rechaza.
    const next = effectivePrefs({
      ...Object.fromEntries(PREF_KEYS.map((k: PrefKey) => [k, k in body])),
      inactive_hours: Number(body.inactive_hours),
      report_hour: Number(body.report_hour),
    });
    if (next.inactiveHours !== Number(body.inactive_hours) || next.reportHour !== Number(body.report_hour)) {
      // Se vuelve a pintar el formulario con lo que había marcado y los plazos anteriores, sin perder las casillas.
      const kept = { ...next, inactiveHours: found.prefs.inactiveHours, reportHour: found.prefs.reportHour };
      return sendPage(reply, 400, "Tus avisos", form(body.token, found.userEmail, kept, "<p><strong>Los plazos no son válidos</strong> (horas sin señales: 24 a 168; hora del informe: 0 a 23). No se guardó nada.</p>"));
    }
    await db.query("UPDATE partners SET prefs = $2 WHERE id = $1", [found.partnerId, JSON.stringify(diffPrefs(next))]);
    return sendPage(reply, 200, "Tus avisos", form(body.token, found.userEmail, next, "<p><strong>Guardado.</strong></p>"));
  });
}
