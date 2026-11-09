import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { currentList, deltaSince, dnsRules, LIST_NAMES, listVersions } from "../blocklists.js";
import { DATASET_NAMES, datasetSince, datasetVersions } from "../datasets.js";
import { parse, throttleData, type Deps } from "../http.js";

const since = z.coerce.number().int().min(0).default(0);
const listQuery = z.object({ list: z.enum(LIST_NAMES).optional(), since });
const datasetQuery = z.object({ since });
const listFile = z.string().regex(/^[a-z_]+\.txt$/).transform((f) => f.slice(0, -4)).pipe(z.enum(LIST_NAMES));
// Textos derivados de las listas (reglas para otros programas). Uno nuevo es una entrada aquí.
const derived = new Map<string, (db: Deps["db"]) => Promise<{ version: number | string; text: string }>>([["dns-rules.txt", dnsRules]]);
const derivedFile = z.string().refine((f) => derived.has(f));
const fileParams = z.object({ file: z.union([listFile, derivedFile]) });

/**
 * Datos públicos que la app descarga sin cuenta (listas de bloqueo, identificadores de vistas, categorías): no son secretos
 * y así el bloqueo funciona desde el primer arranque. Se limitan por IP.
 */
export function dataRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  /** Sin `list`: el manifiesto con la versión de cada lista y documento. Con `list` y `since`: lo que cambió desde esa versión. */
  app.get("/blocklists", async (req, reply) => {
    throttleData(deps, req.ip);
    const q = parse(listQuery, req.query);
    if (!q.list) return { versions: await listVersions(db), datasets: await datasetVersions(db) };
    const delta = await deltaSince(db, q.list, q.since);
    // La lista entera sale ya serializada de la caché.
    return delta.kind === "full" ? reply.type("application/json; charset=utf-8").send(delta.list.json) : delta.changes;
  });

  /**
   * La lista entera como texto, un valor por línea, o `dns-rules.txt`: las mismas listas como reglas de AdGuard Home.
   * El resolvedor DNS las descarga de aquí, así que hay una sola fuente.
   */
  app.get("/blocklists/:file", async (req, reply) => {
    throttleData(deps, req.ip);
    const { file } = parse(fileParams, req.params);
    const make = derived.get(file);
    const { version, text } = make ? await make(db) : await currentList(db, file as (typeof LIST_NAMES)[number]);
    return reply.type("text/plain; charset=utf-8").header("x-list-version", String(version)).header("cache-control", "public, max-age=300").send(text);
  });

  for (const name of DATASET_NAMES) {
    app.get(`/${name}`, async (req) => {
      throttleData(deps, req.ip);
      return datasetSince(db, name, parse(datasetQuery, req.query).since);
    });
  }
}
