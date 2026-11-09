import { createHmac, timingSafeEqual } from "node:crypto";
import type { Config } from "./config.js";
import { NOTICE_KINDS, PREF_KEYS, type PrefKey } from "./noticeKinds.js";

export const DEFAULT_INACTIVE_HOURS = 48;
export const MIN_INACTIVE_HOURS = 24;
export const MAX_INACTIVE_HOURS = 168;
export const DEFAULT_REPORT_HOUR = 8;

/** Lo que el compañero elige: qué avisos, tras cuántas horas sin señales se avisa y a qué hora UTC llega el informe. */
export interface Prefs {
  flags: Record<PrefKey, boolean>;
  inactiveHours: number;
  reportHour: number;
}

const int = (v: unknown, min: number, max: number, fallback: number) => (typeof v === "number" && Number.isInteger(v) && v >= min && v <= max ? v : fallback);

export function effectivePrefs(stored: unknown): Prefs {
  const s = (stored && typeof stored === "object" ? stored : {}) as Record<string, unknown>;
  return {
    flags: Object.fromEntries(PREF_KEYS.map((k) => [k, typeof s[k] === "boolean" ? s[k] : NOTICE_KINDS[k].on])) as Record<PrefKey, boolean>,
    inactiveHours: int(s.inactive_hours, MIN_INACTIVE_HOURS, MAX_INACTIVE_HOURS, DEFAULT_INACTIVE_HOURS),
    reportHour: int(s.report_hour, 0, 23, DEFAULT_REPORT_HOUR),
  };
}

/** Solo las diferencias con los valores por defecto: si un día cambia un valor por defecto, lo heredan quienes no lo tocaron. */
export function diffPrefs(p: Prefs): Record<string, boolean | number> {
  const out: Record<string, boolean | number> = {};
  for (const k of PREF_KEYS) if (p.flags[k] !== NOTICE_KINDS[k].on) out[k] = p.flags[k];
  if (p.inactiveHours !== DEFAULT_INACTIVE_HOURS) out.inactive_hours = p.inactiveHours;
  if (p.reportHour !== DEFAULT_REPORT_HOUR) out.report_hour = p.reportHour;
  return out;
}

// El enlace de preferencias es una capacidad por compañero y se puede usar muchas veces: va firmado con el secreto del
// servidor (id + HMAC), así que no hace falta una fila por aviso. Deja de valer si el compañero se da de baja o si cambia el secreto.
const ID_LENGTH = 36;
const mac = (secret: string, partnerId: string) => createHmac("sha256", secret).update(`prefs:${partnerId}`).digest("base64url");

export const prefsToken = (secret: string, partnerId: string) => partnerId + mac(secret, partnerId);

/** Id del compañero si la firma es válida. */
export function parsePrefsToken(secret: string, token: string): string | null {
  const id = token.slice(0, ID_LENGTH);
  const given = Buffer.from(token.slice(ID_LENGTH));
  const expected = Buffer.from(mac(secret, id));
  return given.length === expected.length && timingSafeEqual(given, expected) ? id : null;
}

export const prefsUrl = (c: Config, partnerId: string) => `${c.PUBLIC_URL}/prefs?token=${prefsToken(c.JWT_SECRET, partnerId)}`;
