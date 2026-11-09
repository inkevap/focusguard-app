import { readFile } from "node:fs/promises";
import { join } from "node:path";
import type { Db } from "./db.js";
import { DAY, type Deps } from "./http.js";
import { claimNotice } from "./notices.js";

/** Listas con versión propia. Las palabras van por idioma porque la app recarga la del idioma del teléfono. */
export const LIST_NAMES = ["domains", "exceptions", "words_es", "words_en"] as const;
export type ListName = (typeof LIST_NAMES)[number];

const DOMAIN = /^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9-]{2,63}$/;
const WORD = /^[\p{L}\p{N}+]{2,60}$/u;
const MIN_REMOTE_DOMAINS = 1000;
const HISTORY_KEEP_MS = 90 * DAY;
const MAX_DOWNLOAD_BYTES = 30 * 1024 * 1024;
const BATCH = 5000;

/** Líneas sin comentarios, en minúsculas y sin vacías. */
const cleanLines = (text: string) => text.split("\n").map((l) => l.replace(/#.*/, "").trim().toLowerCase()).filter(Boolean);

const chunks = <T>(items: T[], size: number): T[][] => Array.from({ length: Math.ceil(items.length / size) }, (_, i) => items.slice(i * size, (i + 1) * size));

/** Formato `hosts` (`0.0.0.0 dominio`) de las listas públicas, o un dominio por línea. Descarta lo que no sea un dominio válido. */
export function parseDomains(text: string): Set<string> {
  const out = new Set<string>();
  for (const line of cleanLines(text)) {
    const parts = line.split(/\s+/);
    const candidate = parts.length >= 2 && /^(0\.0\.0\.0|127\.0\.0\.1)$/.test(parts[0]!) ? parts[1]! : parts.length === 1 ? parts[0]! : "";
    if (candidate && candidate !== "localhost" && DOMAIN.test(candidate)) out.add(candidate);
  }
  return out;
}

/** Misma normalización que hace la app con el texto de pantalla: sin acentos ni espacios, puntos o guiones. */
const normalizeWord = (w: string) => w.normalize("NFD").replace(/\p{M}/gu, "").replace(/[\s.\-_]/g, "");

/** `onDrop` recibe las líneas que no valen, para avisar de que una palabra se quedó fuera. */
export function parseWords(text: string, onDrop?: (line: string) => void): Set<string> {
  const out = new Set<string>();
  for (const line of cleanLines(text)) {
    const w = normalizeWord(line);
    if (WORD.test(w)) out.add(w);
    else onDrop?.(line);
  }
  return out;
}

export interface ApplyResult {
  changed: boolean;
  version: number;
}

/**
 * Deja la lista igual que `values`: sube la versión una vez y anota qué entró y qué salió.
 * `check` recibe cuántas entradas había y puede lanzar para cancelar el cambio (protección contra listas rotas).
 */
export async function applyList(db: Db, list: ListName, values: Set<string>, now: Date, check?: (currentCount: number) => void): Promise<ApplyResult> {
  return db.tx(async (tx) => {
    await tx.query("INSERT INTO list_versions (list) VALUES ($1) ON CONFLICT DO NOTHING", [list]);
    const [state] = await tx.query<{ version: number }>("SELECT version FROM list_versions WHERE list = $1 FOR UPDATE", [list]);
    const current = new Set((await tx.query<{ value: string }>("SELECT value FROM list_entries WHERE list = $1 AND removed_version IS NULL", [list])).map((r) => r.value));
    check?.(current.size);
    const toAdd = [...values].filter((v) => !current.has(v));
    const toRemove = [...current].filter((v) => !values.has(v));
    if (!toAdd.length && !toRemove.length) return { changed: false, version: state!.version };
    const version = state!.version + 1;
    for (const part of chunks(toAdd, BATCH)) {
      await tx.query(
        `INSERT INTO list_entries (list, value, added_version) SELECT $1, unnest($2::text[]), $3
         ON CONFLICT (list, value) DO UPDATE SET added_version = $3, removed_version = NULL, removed_at = NULL`,
        [list, part, version],
      );
    }
    for (const part of chunks(toRemove, BATCH)) {
      await tx.query("UPDATE list_entries SET removed_version = $3, removed_at = $4 WHERE list = $1 AND value = ANY($2::text[])", [list, part, version, now]);
    }
    await tx.query("UPDATE list_versions SET version = $2 WHERE list = $1", [list, version]);
    return { changed: true, version };
  });
}

export async function listVersions(db: Db): Promise<Record<ListName, number>> {
  const rows = await db.query<{ list: string; version: number }>("SELECT list, version FROM list_versions");
  return Object.fromEntries(LIST_NAMES.map((n) => [n, rows.find((r) => r.list === n)?.version ?? 0])) as Record<ListName, number>;
}

/** La lista entera ya serializada: depende solo de `(lista, versión)`, así que se guarda y no se vuelve a pedir a la base ni a armar. */
export interface FullList {
  version: number;
  json: string;
  text: string;
}

// Una caché por conexión a la base: si se cambia de base (o se restaura una copia), no se mezclan versiones de datos distintos.
const fullCaches = new WeakMap<Db, Map<ListName, FullList>>();

async function fullList(db: Db, list: ListName): Promise<FullList> {
  const cache = fullCaches.get(db) ?? fullCaches.set(db, new Map()).get(db)!;
  const cached = cache.get(list);
  // Con la versión al día no se vuelve a leer ni armar la lista.
  const [head] = await db.query<{ version: number }>("SELECT version FROM list_versions WHERE list = $1", [list]);
  if (cached && cached.version === (head?.version ?? 0)) return cached;
  // Una sola sentencia: versión y valores salen del mismo estado aunque otra transacción publique a la vez.
  const [row] = await db.query<{ version: number | null; values: string[] }>(
    `SELECT (SELECT version FROM list_versions WHERE list = $1) AS version,
            coalesce(array_agg(value ORDER BY value), '{}') AS values
       FROM list_entries WHERE list = $1 AND removed_version IS NULL`,
    [list],
  );
  const version = row!.version ?? 0;
  const values = row!.values;
  const full: FullList = { version, json: JSON.stringify({ version, full: true, added: values, removed: [] }), text: values.length ? `${values.join("\n")}\n` : "" };
  cache.set(list, full);
  return full;
}

export const currentList = fullList;

const rulesCaches = new WeakMap<Db, { version: string; text: string }>();

/**
 * Reglas para AdGuard Home: `||dominio^` bloquea el dominio y sus subdominios y `@@||dominio^` lo permite (las excepciones,
 * que son falsos positivos conocidos, se permiten también en el DNS). Sale de las mismas listas que usa la app.
 */
export async function dnsRules(db: Db): Promise<{ version: string; text: string }> {
  const [domains, exceptions] = await Promise.all([fullList(db, "domains"), fullList(db, "exceptions")]);
  // La versión sale de las listas ya leídas, así que cabecera, clave de caché y contenido van siempre juntos.
  const version = `${domains.version}.${exceptions.version}`;
  const cached = rulesCaches.get(db);
  if (cached?.version === version) return cached;
  const rule = (prefix: string) => (value: string) => `${prefix}${value}^`;
  const lines = (text: string) => text.split("\n").filter(Boolean);
  // Los valores ya están validados como dominios: no pueden traer modificadores de regla.
  const rules = { version, text: ["! FocusGuard " + version, ...lines(domains.text).map(rule("||")), ...lines(exceptions.text).map(rule("@@||"))].join("\n") + "\n" };
  rulesCaches.set(db, rules);
  return rules;
}

export interface ListChanges {
  version: number;
  /** `true` si `added` es la lista entera y el cliente debe reemplazar la suya (primera descarga o historial ya recortado). */
  full: boolean;
  added: string[];
  removed: string[];
}

export type Delta = { kind: "full"; list: FullList } | { kind: "delta"; changes: ListChanges };

/** Lo que cambió desde la versión `since` que tiene el cliente: la lista entera (de la caché) o solo las diferencias. */
export async function deltaSince(db: Db, list: ListName, since: number): Promise<Delta> {
  const [state] = await db.query<{ version: number; compacted_through: number }>("SELECT version, compacted_through FROM list_versions WHERE list = $1", [list]);
  const version = state?.version ?? 0;
  if (since <= 0 || since > version || since < (state?.compacted_through ?? 0)) return { kind: "full", list: await fullList(db, list) };
  if (since === version) return { kind: "delta", changes: { version, full: false, added: [], removed: [] } };
  const added = await db.query<{ value: string }>("SELECT value FROM list_entries WHERE list = $1 AND added_version > $2 AND removed_version IS NULL ORDER BY value", [list, since]);
  const removed = await db.query<{ value: string }>("SELECT value FROM list_entries WHERE list = $1 AND removed_version > $2 AND added_version <= $2 ORDER BY value", [list, since]);
  return { kind: "delta", changes: { version, full: false, added: added.map((r) => r.value), removed: removed.map((r) => r.value) } };
}

export async function changesSince(db: Db, list: ListName, since: number): Promise<ListChanges> {
  const d = await deltaSince(db, list, since);
  return d.kind === "full" ? (JSON.parse(d.list.json) as ListChanges) : d.changes;
}

/** Borra las bajas de más de 90 días; quien tenga una versión anterior recibirá la lista entera. */
export async function compactLists(db: Db, now: Date): Promise<void> {
  await db.query(
    `WITH gone AS (DELETE FROM list_entries WHERE removed_at <= $1 RETURNING list, removed_version)
     UPDATE list_versions lv SET compacted_through = GREATEST(lv.compacted_through, m.mx)
       FROM (SELECT list, max(removed_version) AS mx FROM gone GROUP BY list) m WHERE lv.list = m.list`,
    [new Date(now.getTime() - HISTORY_KEEP_MS)],
  );
}

// Un fichero que falta no es una lista vacía: si lo fuera, publicarlo borraría la lista de todos los teléfonos. Falla y dice cuál.
const readRequired = (dir: string, file: string) => readFile(join(dir, file), "utf8");
const ownDomains = async (dir: string) => parseDomains(await readRequired(dir, "own_domains.txt"));

const LOCAL_FILES: Array<[ListName, string, (text: string, onDrop: (line: string) => void) => Set<string>]> = [
  ["words_es", "words_es.txt", parseWords],
  ["words_en", "words_en.txt", parseWords],
  ["exceptions", "exceptions.txt", (text) => parseDomains(text)],
];

/** Palabras, excepciones y la copia propia de dominios salen de ficheros del repositorio y se publican al arrancar. */
export async function publishLocalLists(deps: Pick<Deps, "db" | "now" | "config" | "log">): Promise<void> {
  const dir = deps.config.OWN_DIR;
  for (const [list, file, parse] of LOCAL_FILES) {
    const dropped: string[] = [];
    const values = parse(await readRequired(dir, file), (line) => dropped.push(line));
    if (dropped.length) deps.log.warn(`${file}: ${dropped.length} líneas descartadas por no ser válidas (${dropped.slice(0, 3).join(", ")}…)`);
    if (list.startsWith("words") && values.size === 0) throw new Error(`${file} no tiene ninguna palabra válida: no se publica una lista de palabras vacía`);
    await applyList(deps.db, list, values, deps.now());
  }
  // Sin red en el primer arranque, al menos queda la lista propia; la pública la completa el trabajo de actualización.
  if ((await listVersions(deps.db)).domains === 0) await applyList(deps.db, "domains", await ownDomains(dir), deps.now());
}

type Fetch = typeof fetch;

/** Lee el cuerpo contando bytes y se corta al pasar el tope, sin fiarse de `content-length` ni de que el servidor termine. */
async function readLimited(res: Response, maxBytes: number): Promise<string> {
  if (!res.body) return res.text();
  const reader = res.body.getReader();
  const parts: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel();
      throw new Error("la lista pública es demasiado grande");
    }
    parts.push(value);
  }
  return Buffer.concat(parts).toString("utf8");
}

/**
 * Descarga la lista pública de dominios y la publica junto con la propia. Si la descarga falla o parece rota
 * (muy pequeña, o menos de la mitad que antes) no se toca nada: un fallo de la fuente no puede vaciar la lista.
 */
export async function refreshPublicDomains(deps: Pick<Deps, "db" | "now" | "config">, http: Fetch = fetch): Promise<ApplyResult | null> {
  const url = deps.config.BLOCKLIST_URL;
  if (!url) return null;
  const res = await http(url, { signal: AbortSignal.timeout(60_000) });
  if (!res.ok) throw new Error(`la lista pública respondió ${res.status}`);
  const remote = parseDomains(await readLimited(res, MAX_DOWNLOAD_BYTES));
  if (remote.size < MIN_REMOTE_DOMAINS) throw new Error(`la lista pública trae solo ${remote.size} dominios: se descarta`);
  const all = new Set([...remote, ...(await ownDomains(deps.config.OWN_DIR))]);
  return applyList(deps.db, "domains", all, deps.now(), (current) => {
    if (current > MIN_REMOTE_DOMAINS && all.size < current / 2) throw new Error(`la lista pública pasó de ${current} a ${all.size} dominios: se descarta`);
  });
}

const REFRESH_EVERY_MS = 23 * 3_600_000;

/**
 * Para el trabajo horario: descarga si hace más de 23 horas de la última descarga buena. Si falla se olvida la marca,
 * así que se reintenta a la hora siguiente en vez de dejar una lista casi vacía un día entero.
 */
export async function refreshPublicDomainsIfDue(deps: Pick<Deps, "db" | "now" | "config">, http: Fetch = fetch): Promise<ApplyResult | null> {
  if (!(await claimNotice(deps.db, "refresh:domains", deps.now(), REFRESH_EVERY_MS))) return null;
  try {
    return await refreshPublicDomains(deps, http);
  } catch (e) {
    await deps.db.query("DELETE FROM notice_state WHERE key = 'refresh:domains'");
    throw e;
  }
}
