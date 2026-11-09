import { expireRequests, purgeHistory } from "./approvals.js";
import { DAY, HOUR, MINUTE, type Deps } from "./http.js";
import { drainMailQueue, purgeMailQueue } from "./mailqueue.js";
import { compactLists, refreshPublicDomainsIfDue } from "./blocklists.js";
import { checkHeartbeats, purgeEvents, sendBlockedDigests, sendDailyReports } from "./notices.js";
import { purgeExpiredTokens } from "./tokens.js";

export interface Job {
  name: string;
  everyMs: number;
  run(): Promise<unknown>;
}

/** Trabajos periódicos del servidor. Un trabajo nuevo es una entrada aquí. */
export function defaultJobs(deps: Deps): Job[] {
  return [
    { name: "enviar correos", everyMs: 10_000, run: () => drainMailQueue(deps) },
    { name: "caducar solicitudes", everyMs: MINUTE, run: () => expireRequests(deps) },
    { name: "resumen de bloqueos", everyMs: MINUTE, run: () => sendBlockedDigests(deps) },
    { name: "revisar latidos", everyMs: 30 * MINUTE, run: () => checkHeartbeats(deps) },
    { name: "informe diario", everyMs: HOUR, run: () => sendDailyReports(deps) },
    { name: "limpiar eventos", everyMs: DAY, run: () => purgeEvents(deps.db, deps.now()) },
    { name: "actualizar lista de dominios", everyMs: HOUR, run: () => refreshPublicDomainsIfDue(deps) },
    { name: "recortar historial de listas", everyMs: DAY, run: () => compactLists(deps.db, deps.now()) },
    { name: "limpiar tokens", everyMs: HOUR, run: () => purgeExpiredTokens(deps.db, deps.now()) },
    { name: "limpiar correos", everyMs: HOUR, run: () => purgeMailQueue(deps.db, deps.now()) },
    { name: "limpiar historial", everyMs: DAY, run: () => purgeHistory(deps.db, deps.now()) },
  ];
}

/** Ejecuta cada trabajo al arrancar y luego a su ritmo, sin solapar dos ejecuciones del mismo. `stop` espera a las que siguen en curso. */
export function startJobs(jobs: Job[], log: Pick<Deps["log"], "error">) {
  const running = new Map<string, Promise<void>>();
  const tick = (job: Job) => {
    if (running.has(job.name)) return;
    const p = job
      .run()
      .then(() => {})
      .catch((e) => log.error(e, `falló el trabajo «${job.name}»`))
      .finally(() => running.delete(job.name));
    running.set(job.name, p);
  };
  const timers = jobs.map((job) => {
    tick(job);
    const timer = setInterval(() => tick(job), job.everyMs);
    timer.unref();
    return timer;
  });
  return {
    async stop() {
      timers.forEach(clearInterval);
      await Promise.allSettled([...running.values()]);
    },
  };
}
