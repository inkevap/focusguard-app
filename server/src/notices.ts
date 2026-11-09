import type { Db } from "./db.js";
import { DAY, HOUR, MINUTE, type Deps } from "./http.js";
import { enqueueMail } from "./mailqueue.js";
import type { Mail } from "./mailer.js";
import { digestMail, inactiveMail, noticeMail, reportMail } from "./mails.js";
import { EVENT_TYPES, gapMinutes, isQuiet, NOTICE_KINDS, type EventType, type PrefKey } from "./noticeKinds.js";
import { effectivePrefs, MIN_INACTIVE_HOURS, prefsUrl, type Prefs } from "./prefs.js";

export { EVENT_TYPES };

// Cada cuánto sale el resumen de bloqueos. La frecuencia de los avisos graves es de cada tipo (`gapMinutes`).
const DIGEST_EVERY_MS = 10 * MINUTE;
// Un dispositivo sin señales se vuelve a avisar, como recordatorio, al cabo de un mes; un latido borra el aviso antes.
const INACTIVE_REMINDER_MS = 30 * DAY;
// La clave del informe lleva el día; los 25 h impiden que se reenvíe el mismo día (no que se espacien dos días seguidos).
const REPORT_GAP_MS = 25 * HOUR;
const STATE_KEEP_MS = 31 * DAY;
const MAX_AGE_MS = 7 * DAY;
const EVENT_RETENTION_MS = 30 * DAY;

export interface IncomingEvent {
  type: EventType;
  at?: Date;
  label?: string;
}

/** Una etiqueta de una línea: sin saltos que permitan colar texto falso en el correo. */
const tidy = (s: string) => s.replace(/\s+/g, " ").trim().slice(0, 100);

export interface PartnerInfo {
  id: string;
  userId: string;
  email: string;
  userEmail: string;
  prefs: Prefs;
}

const PARTNER_SELECT = "SELECT p.id, p.user_id, p.email, p.prefs, u.email AS user_email FROM partners p JOIN users u ON u.id = p.user_id";
type PartnerRow = { id: string; user_id: string; email: string; prefs: unknown; user_email: string };
const toPartner = (r: PartnerRow): PartnerInfo => ({ id: r.id, userId: r.user_id, email: r.email, userEmail: r.user_email, prefs: effectivePrefs(r.prefs) });

async function acceptedPartners(db: Db, userId: string): Promise<PartnerInfo[]> {
  const rows = await db.query<PartnerRow>(`${PARTNER_SELECT} WHERE p.user_id = $1 AND p.status = 'aceptado' ORDER BY p.confirmed_at, p.id`, [userId]);
  return rows.map(toPartner);
}

/** Reserva el aviso `key`: `true` si hace más de `gapMs` que no se mandaba (y lo anota), `false` si aún es pronto. */
export async function claimNotice(db: Db, key: string, now: Date, gapMs: number): Promise<boolean> {
  const rows = await db.query(
    "INSERT INTO notice_state (key, last_at) VALUES ($1,$2) ON CONFLICT (key) DO UPDATE SET last_at = $2 WHERE notice_state.last_at <= $3 RETURNING key",
    [key, now, new Date(now.getTime() - gapMs)],
  );
  return rows.length > 0;
}

/** Un dispositivo que vuelve a dar señales puede volver a avisar de que se apagó. */
export const clearInactive = (db: Db, deviceId: string) => db.query("DELETE FROM notice_state WHERE key LIKE $1", [`inactive:${deviceId}:%`]);

/**
 * La única forma de avisar al compañero: respeta lo que apagó, limita la frecuencia si se pide (`claim`) y añade al correo
 * el enlace para elegir avisos. Devuelve si se encoló algo.
 */
async function notifyPartner(
  tx: Db,
  deps: Pick<Deps, "config">,
  partner: PartnerInfo,
  kind: PrefKey,
  now: Date,
  build: (prefsUrl: string) => Mail | Promise<Mail>,
  claim?: { key: string; gapMs: number },
): Promise<boolean> {
  if (!partner.prefs.flags[kind]) return false;
  if (claim && !(await claimNotice(tx, claim.key, now, claim.gapMs))) return false;
  await enqueueMail(tx, await build(prefsUrl(deps.config, partner.id)), now);
  return true;
}

/**
 * Guarda los eventos y avisa ya al compañero de los graves. Solo se guarda lo que el compañero tiene activado: sin compañero
 * o con ese aviso apagado no queda nada en el servidor. Los bloqueos no avisan aquí: los junta `sendBlockedDigests`.
 */
export async function recordEvents(deps: Deps, userId: string, device: { id: string; name: string }, events: IncomingEvent[]): Promise<void> {
  const now = deps.now();
  await deps.db.tx(async (tx) => {
    const partners = await acceptedPartners(tx, userId);
    for (const e of events) {
      // Solo se guarda lo que algún compañero tiene activado; cada uno recibe solo lo suyo.
      const interested = partners.filter((p) => p.prefs.flags[e.type]);
      if (!interested.length) continue;
      // La hora la pone el teléfono, pero nunca en el futuro ni de hace más de una semana.
      const at = e.at && e.at <= now && now.getTime() - e.at.getTime() <= MAX_AGE_MS ? e.at : now;
      const label = e.label ? tidy(e.label) : "";
      await tx.query("INSERT INTO events (user_id, device_id, type, detail, at, received_at, notified) VALUES ($1,$2,$3,$4,$5,$6,$7)", [
        userId,
        device.id,
        e.type,
        JSON.stringify(label ? { label } : {}),
        at,
        now,
        e.type !== "blocked",
      ]);
      if (e.type === "blocked" || isQuiet(e.type)) continue;
      // La clave usa la hora del servidor y el estado vive aparte de la cola: el teléfono no puede esquivarla ni provocar correos de más.
      for (const partner of interested) {
        await notifyPartner(
          tx,
          deps,
          partner,
          e.type,
          now,
          (url) => noticeMail({ to: partner.email, user: partner.userEmail, device: device.name, type: e.type, at, label, prefsUrl: url }),
          { key: `ev:${e.type}:${device.id}:${partner.id}`, gapMs: gapMinutes(e.type) * MINUTE },
        );
      }
    }
  });
}

/** Junta los bloqueos sin avisar en un correo por compañero, como mucho uno cada 10 minutos. */
export async function sendBlockedDigests(deps: Pick<Deps, "db" | "config" | "now">): Promise<number> {
  const now = deps.now();
  const pending = await deps.db.query<{ user_id: string }>("SELECT DISTINCT user_id FROM events WHERE NOT notified");
  let sent = 0;
  for (const { user_id: userId } of pending) {
    await deps.db.tx(async (tx) => {
      // Sin compañeros con el resumen encendido, o con el resumen apagado después de recibir los eventos, no hay nada que mandar: se descartan.
      const wanting = (await acceptedPartners(tx, userId)).filter((p) => p.prefs.flags.blocked);
      const due: PartnerInfo[] = [];
      for (const p of wanting) if (await claimNotice(tx, `digest:${p.id}`, now, DIGEST_EVERY_MS)) due.push(p);
      // Si alguno aún está dentro de su espera, los bloqueos esperan al siguiente turno para que no se pierdan para él.
      if (wanting.length && due.length < wanting.length) return;
      const events = await tx.query<{ detail: { label?: string } }>("UPDATE events SET notified = true WHERE user_id = $1 AND NOT notified RETURNING detail", [userId]);
      if (!due.length) return;
      const counts = new Map<string, number>();
      for (const e of events) {
        const label = e.detail.label ?? "algo bloqueado";
        counts.set(label, (counts.get(label) ?? 0) + 1);
      }
      const top = [...counts].sort((a, b) => b[1] - a[1]).slice(0, 5);
      for (const partner of due) {
        if (await notifyPartner(tx, deps, partner, "blocked", now, (url) => digestMail({ to: partner.email, user: partner.userEmail, total: events.length, top, prefsUrl: url }))) sent++;
      }
    });
  }
  return sent;
}

/** Avisa de los dispositivos que llevan más del plazo del compañero (48 h por defecto) sin dar señales. */
export async function checkHeartbeats(deps: Pick<Deps, "db" | "config" | "now">): Promise<number> {
  const now = deps.now();
  const stale = await deps.db.query<PartnerRow & { device_id: string; device_name: string; last_seen: Date }>(
    `SELECT d.id AS device_id, d.name AS device_name, d.last_seen, p.id, p.user_id, p.email, p.prefs, u.email AS user_email
       FROM devices d JOIN partners p ON p.user_id = d.user_id AND p.status = 'aceptado' JOIN users u ON u.id = d.user_id
      WHERE d.last_seen <= $1
        AND NOT EXISTS (SELECT 1 FROM notice_state s WHERE s.key = 'inactive:' || d.id || ':' || p.id AND s.last_at > $2)`,
    [new Date(now.getTime() - MIN_INACTIVE_HOURS * HOUR), new Date(now.getTime() - INACTIVE_REMINDER_MS)],
  );
  let sent = 0;
  for (const r of stale) {
    const partner = toPartner(r);
    if (now.getTime() - r.last_seen.getTime() < partner.prefs.inactiveHours * HOUR) continue;
    await deps.db.tx(async (tx) => {
      const ok = await notifyPartner(tx, deps, partner, "inactive", now, (url) => inactiveMail({ to: partner.email, user: partner.userEmail, device: r.device_name, lastSeen: r.last_seen, prefsUrl: url }), {
        // La clave lleva al compañero: uno nuevo se entera aunque el aviso anterior sea reciente.
        key: `inactive:${r.device_id}:${partner.id}`,
        gapMs: INACTIVE_REMINDER_MS,
      });
      if (ok) sent++;
    });
  }
  return sent;
}

/** Lo que el formato «bloqueadas,permitidas» deja pasar: solo dos números, nunca texto del teléfono en el correo. */
const DNS_LABEL = /^(\d{1,9}),(\d{1,9})$/;

/**
 * Toma las etiquetas de los totales que aún no entraron en ningún informe y los marca como contados, en la misma transacción que
 * el correo: si el informe no sale, siguen esperando al siguiente; si sale, no se repiten.
 */
async function peekQuiet(db: Db, userId: string, type: "dns_counts" | "urge_report"): Promise<string[]> {
  const rows = await db.query<{ label: string | null }>("SELECT detail->>'label' AS label FROM events WHERE user_id = $1 AND type = $2 AND NOT reported ORDER BY ctid", [userId, type]);
  return rows.map((r) => r.label ?? "");
}

/**
 * Marca como contados los totales silenciosos de la cuenta, pero solo cuando todos sus compañeros con informe ya lo recibieron hoy: con varios compañeros
 * cada uno los necesita en su informe, y el primero no debe gastarlos para los demás.
 */
async function settleQuiet(db: Db, partners: PartnerInfo[], userId: string, today: string): Promise<void> {
  for (const p of partners.filter((x) => x.userId === userId && x.prefs.flags.daily_report)) {
    const [sentToday] = await db.query("SELECT 1 FROM notice_state WHERE key = $1", [`report:${p.id}:${today}`]);
    if (!sentToday) return;
  }
  await db.query("UPDATE events SET reported = true WHERE user_id = $1 AND type IN ('dns_counts','urge_report') AND NOT reported", [userId]);
}

/** Suma los totales de consultas DNS pendientes de contar; `null` si no hay ninguno. */
function dnsTotals(labels: string[]): { blocked: number; allowed: number } | null {
  let found = false;
  const total = { blocked: 0, allowed: 0 };
  for (const label of labels) {
    const m = DNS_LABEL.exec(label);
    if (!m) continue;
    found = true;
    total.blocked += Number(m[1]);
    total.allowed += Number(m[2]);
  }
  return found ? total : null;
}

/** Los desencadenantes que el teléfono puede nombrar (las mismas del modo impulsos), con su texto: el correo nunca lleva texto del teléfono. */
const URGE_TRIGGERS: Record<string, string> = {
  BOREDOM: "aburrimiento",
  STRESS: "estrés",
  LONELINESS: "soledad",
  TIREDNESS: "cansancio",
  ANXIETY: "ansiedad",
  OTHER: "otra cosa",
};

/** `total,pasados,cedidos,hora,desencadenante`: la hora (0 a 23) y el desencadenante pueden ir vacíos. */
const URGE_LABEL = /^(\d{1,4}),(\d{1,4}),(\d{1,4}),(\d{1,2})?,([A-Z]{0,12})$/;

export interface UrgeSummary {
  total: number;
  passed: number;
  gaveIn: number;
  peakHour: number | null;
  trigger: string | null;
}

/** El último resumen de impulsos válido de los pendientes (el más reciente); `null` si no hay ninguno. */
function urgeSummary(labels: string[]): UrgeSummary | null {
  let best: UrgeSummary | null = null;
  for (const label of labels) {
    const m = URGE_LABEL.exec(label);
    if (!m) continue;
    const peak = m[4] === undefined ? null : Number(m[4]);
    const summary: UrgeSummary = {
      total: Number(m[1]),
      passed: Number(m[2]),
      gaveIn: Number(m[3]),
      peakHour: peak !== null && peak <= 23 ? peak : null,
      trigger: URGE_TRIGGERS[m[5] ?? ""] ?? null,
    };
    // Cifras que no cuadran (más pasados y cedidos que impulsos) no se enseñan.
    if (summary.passed + summary.gaveIn > summary.total) continue;
    best = summary;
  }
  return best;
}

/** Informe diario: a la hora que eligió el compañero (8:00 UTC por defecto), uno por día, con lo ocurrido en las últimas 24 h. */
export async function sendDailyReports(deps: Pick<Deps, "db" | "config" | "now">): Promise<number> {
  const now = deps.now();
  const today = now.toISOString().slice(0, 10);
  const partners = (await deps.db.query<PartnerRow>(`${PARTNER_SELECT} WHERE p.status = 'aceptado'`)).map(toPartner);
  let sent = 0;
  for (const partner of partners) {
    if (now.getUTCHours() < partner.prefs.reportHour) continue;
    await deps.db.tx(async (tx) => {
      const ok = await notifyPartner(
        tx,
        deps,
        partner,
        "daily_report",
        now,
        async (url) => {
          const rows = await tx.query<{ type: string; n: number }>("SELECT type, count(*)::int AS n FROM events WHERE user_id = $1 AND received_at > $2 GROUP BY type", [partner.userId, new Date(now.getTime() - DAY)]);
          const dns = partner.prefs.flags.dns_counts ? dnsTotals(await peekQuiet(tx, partner.userId, "dns_counts")) : null;
          const urges = partner.prefs.flags.urge_report ? urgeSummary(await peekQuiet(tx, partner.userId, "urge_report")) : null;
          return reportMail({ to: partner.email, user: partner.userEmail, counts: Object.fromEntries(rows.map((r) => [r.type, r.n])), dns, urges, prefsUrl: url });
        },
        { key: `report:${partner.id}:${today}`, gapMs: REPORT_GAP_MS },
      );
      if (ok) {
        sent++;
        await settleQuiet(tx, partners, partner.userId, today);
      }
    });
  }
  return sent;
}

export async function purgeEvents(db: Db, now: Date): Promise<void> {
  await db.query("DELETE FROM events WHERE received_at <= $1", [new Date(now.getTime() - EVENT_RETENTION_MS)]);
  await db.query("DELETE FROM notice_state WHERE last_at <= $1", [new Date(now.getTime() - STATE_KEEP_MS)]);
}

