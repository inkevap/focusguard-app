import { buildApp } from "./app.js";
import { loadConfig } from "./config.js";
import { pgDb } from "./db.js";
import { defaultJobs, startJobs } from "./jobs.js";
import { createMailer } from "./mailer.js";
import { FcmPush, type FcmCredentials } from "./fcm.js";
import { publishLocalLists } from "./blocklists.js";
import { publishDatasets } from "./datasets.js";
import { migrate } from "./migrations.js";
import { readFileSync } from "node:fs";
import pino from "pino";
import { createDeps, serializers } from "./deps.js";

const log = pino({ serializers });
const config = loadConfig();
const db = pgDb();
await migrate(db);

const push = config.FCM_SERVICE_ACCOUNT_FILE
  ? new FcmPush(JSON.parse(readFileSync(config.FCM_SERVICE_ACCOUNT_FILE, "utf8")) as FcmCredentials, db, () => new Date())
  : undefined;
if (!push) log.warn("Sin FCM_SERVICE_ACCOUNT_FILE: no hay avisos push, el teléfono se entera al consultar");
const deps = createDeps({ config, db, mailer: createMailer(config, (m) => log.info(m)), push, log });
await publishDatasets(deps);
await publishLocalLists(deps);
const app = buildApp(deps);
const jobs = startJobs(defaultJobs(deps), log);

for (const signal of ["SIGTERM", "SIGINT"] as const) {
  process.once(signal, async () => {
    // Plazo propio: si una petición se cuelga, mejor salir que esperar al SIGKILL de Docker.
    setTimeout(() => process.exit(1), 10_000).unref();
    try {
      await jobs.stop();
      await app.close();
      await db.close();
      process.exit(0);
    } catch (e) {
      log.error(e, "error al cerrar");
      process.exit(1);
    }
  });
}

await app.listen({ port: config.PORT, host: "0.0.0.0" });
