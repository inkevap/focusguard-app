import { readFile } from "node:fs/promises";
import { join } from "node:path";
import type { Db } from "./db.js";
import type { Deps } from "./http.js";

/** `paquete,dominio,categoría` por línea; el dominio puede ir vacío (navegadores). */
export function parseCategories(csv: string): Array<{ package: string; domain: string; category: string }> {
  return csv
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => {
      const [pkg = "", domain = "", category = ""] = l.split(",");
      return { package: pkg, domain, category };
    });
}

const readJson = async (path: string) => JSON.parse(await readFile(path, "utf8")) as unknown;

/** Documentos pequeños que la app descarga enteros cuando cambian. Cada uno sabe cómo leerse de `data/seed` y cómo es vacío. */
const DATASETS = {
  selectors: {
    empty: {} as unknown,
    load: async (dir: string): Promise<unknown> => ({
      browsers: await readJson(join(dir, "browser_view_ids.json")),
      incognito: await readJson(join(dir, "incognito_view_ids.json")),
      subapps: await readJson(join(dir, "subapps.json")),
    }),
  },
  categories: {
    empty: [] as unknown,
    load: async (dir: string): Promise<unknown> => parseCategories(await readFile(join(dir, "apps_webs_categories.csv"), "utf8")),
  },
};

export type DatasetName = keyof typeof DATASETS;
export const DATASET_NAMES = Object.keys(DATASETS) as DatasetName[];

/** Publica el documento si cambió desde la última vez (otra versión) y no hace nada si es idéntico. */
export async function publishDataset(db: Db, name: DatasetName, content: unknown, now: Date): Promise<number> {
  const json = JSON.stringify(content);
  const [row] = await db.query<{ version: number; same: boolean }>("SELECT version, content = $2::jsonb AS same FROM datasets WHERE name = $1", [name, json]);
  if (row?.same) return row.version;
  const version = (row?.version ?? 0) + 1;
  await db.query(
    `INSERT INTO datasets (name, version, content, updated_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (name) DO UPDATE SET version = $2, content = $3, updated_at = $4`,
    [name, version, json, now],
  );
  return version;
}

export async function publishDatasets(deps: Pick<Deps, "db" | "now" | "config">): Promise<void> {
  for (const name of DATASET_NAMES) await publishDataset(deps.db, name, await DATASETS[name].load(deps.config.SEED_DIR), deps.now());
}

export async function datasetVersions(db: Db): Promise<Record<DatasetName, number>> {
  const rows = await db.query<{ name: string; version: number }>("SELECT name, version FROM datasets");
  return Object.fromEntries(DATASET_NAMES.map((n) => [n, rows.find((r) => r.name === n)?.version ?? 0])) as Record<DatasetName, number>;
}

/** Si el cliente ya tiene la versión actual no se manda el contenido (ni sale de la base). */
export async function datasetSince(db: Db, name: DatasetName, since: number): Promise<{ version: number; unchanged: true } | { version: number; content: unknown }> {
  const [row] = await db.query<{ version: number; content: unknown | null }>("SELECT version, CASE WHEN version = $2 THEN NULL ELSE content END AS content FROM datasets WHERE name = $1", [name, since]);
  if (!row) return { version: 0, content: DATASETS[name].empty };
  return row.version === since ? { version: row.version, unchanged: true } : { version: row.version, content: row.content };
}
