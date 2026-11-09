import pg from "pg";

export type Row = Record<string, any>;

/** Lo mínimo que necesita el servidor de una base de datos: producción usa `pg`, las pruebas PGlite. */
export interface Db {
  query<T extends Row = Row>(sql: string, params?: unknown[]): Promise<T[]>;
  /** Ejecuta varias sentencias sin parámetros (migraciones). */
  exec(sql: string): Promise<void>;
  tx<R>(fn: (db: Db) => Promise<R>): Promise<R>;
  close(): Promise<void>;
}

/** Se conecta con las variables estándar `PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`: así la contraseña no pasa por una URL. */
export function pgDb(): Db {
  const pool = new pg.Pool({ max: 10 });
  // Sin este manejador, una conexión inactiva cortada por PostgreSQL tumba el proceso.
  pool.on("error", (e) => console.error("Error en una conexión inactiva de la base de datos:", e.message));
  return {
    ...over(pool, (fn) => runInTransaction(pool, fn)),
    close: () => pool.end(),
  };
}

function over(q: Pick<pg.Pool, "query">, tx: Db["tx"]): Omit<Db, "close"> {
  return {
    query: async (sql, params = []) => (await q.query(sql, params)).rows as any,
    exec: async (sql) => void (await q.query(sql)),
    tx,
  };
}

async function runInTransaction<R>(pool: pg.Pool, fn: (db: Db) => Promise<R>): Promise<R> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const inner: Db = { ...over(client, (f) => f(inner)), close: async () => {} };
    const result = await fn(inner);
    await client.query("COMMIT");
    return result;
  } catch (e) {
    await client.query("ROLLBACK");
    throw e;
  } finally {
    client.release();
  }
}
