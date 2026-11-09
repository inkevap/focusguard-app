import { PGlite } from "@electric-sql/pglite";
import { pino } from "pino";
import type { FastifyInstance } from "fastify";
import { buildApp } from "../src/app.js";
import { loadConfig } from "../src/config.js";
import type { Db } from "../src/db.js";
import type { Deps } from "../src/http.js";
import { MemoryMailer } from "../src/mailer.js";
import { drainMailQueue } from "../src/mailqueue.js";
import { MemoryPush } from "../src/push.js";
import { migrate } from "../src/migrations.js";
import { useCheapPasswordHashing } from "../src/security.js";
import { createDeps } from "../src/deps.js";

function pgliteDb(pg: PGlite): Db {
  const over = (q: Pick<PGlite, "query" | "exec">): Db => ({
    exec: async (sql) => void (await q.exec(sql)),
    query: async (sql, params) => (await q.query(sql, params as any[])).rows as any,
    tx: (fn) => pg.transaction((t) => fn(over(t))),
    close: () => pg.close(),
  });
  return over(pg);
}

export interface TestServer {
  app: FastifyInstance;
  deps: Deps;
  mailer: MemoryMailer;
  push: MemoryPush;
  clock: { now: Date };
  close(): Promise<void>;
}

export async function startServer(): Promise<TestServer> {
  useCheapPasswordHashing();
  const db = pgliteDb(new PGlite());
  await migrate(db);
  const clock = { now: new Date("2026-01-01T10:00:00Z") };
  const mailer = new MemoryMailer();
  const push = new MemoryPush();
  const deps = createDeps({
    config: loadConfig({ PUBLIC_URL: "https://fg.test", JWT_SECRET: "a".repeat(40), NODE_ENV: "test", BLOCKLIST_URL: "https://lists.test/hosts" }),
    db,
    mailer,
    push,
    log: pino({ level: "silent" }),
    now: () => clock.now,
  });
  const app = buildApp(deps);
  await app.ready();
  return { app, deps, mailer, push, clock, close: async () => { await app.close(); await db.close(); } };
}

export async function register(s: TestServer, email = "ana@example.com", password = "contraseña-larga") {
  const res = await s.app.inject({ method: "POST", url: "/auth/register", payload: { email, password } });
  return { res, body: res.json() as { accessToken: string; refreshToken: string } };
}

export const bearer = (token: string) => ({ authorization: `Bearer ${token}` });

export const advance = (s: TestServer, ms: number) => {
  s.clock.now = new Date(s.clock.now.getTime() + ms);
};

export const putDevice = (s: TestServer, token: string, payload: object) =>
  s.app.inject({ method: "PUT", url: "/devices/register", headers: bearer(token), payload });

/** Envía los correos encolados y devuelve el último. */
export async function flushMail(s: TestServer) {
  await drainMailQueue(s.deps);
  return s.mailer.outbox.at(-1)!;
}

export const post = (s: TestServer, url: string, payload?: object, token?: string) =>
  s.app.inject({ method: "POST", url, payload: payload as object, headers: token ? bearer(token) : {} });

export const form = (s: TestServer, url: string, fields: Record<string, string>) =>
  s.app.inject({ method: "POST", url, headers: { "content-type": "application/x-www-form-urlencoded" }, payload: new URLSearchParams(fields).toString() });

/** Registra a Ana, le da un dispositivo y un compañero que ya aceptó. */
export async function withPartner(s: TestServer) {
  const ana = await registerVerified(s);
  const device = ((await putDevice(s, ana.accessToken, { name: "Pixel de Ana" })).json() as { deviceId: string }).deviceId;
  const invite = await post(s, "/partners", { email: "luis@example.com" }, ana.accessToken);
  const mail = await flushMail(s);
  const token = /token=([\w-]+)/.exec(mail.text)![1]!;
  await form(s, "/partners/confirm", { token });
  return { ana, device, partnerId: (invite.json() as { id: string }).id };
}

/** Token de acceso nuevo: los de 15 minutos caducan cuando una prueba avanza el reloj. */
export async function relogin(s: TestServer, email = "ana@example.com", password = "contraseña-larga") {
  return ((await post(s, "/auth/login", { email, password })).json() as { accessToken: string }).accessToken;
}

/** Vacía la cola y la bandeja: para pruebas que no quieren el correo de verificación del registro. */
export async function clearMail(s: TestServer) {
  await s.deps.db.query("DELETE FROM mail_outbox");
  s.mailer.outbox.length = 0;
}

/** Token del enlace `?token=` (restablecer, invitación) de un correo. */
export const linkToken = (text: string) => /token=([\w-]+)/.exec(text)![1]!;

/** Token del enlace `/r/<token>` de un correo de solicitud. */
export const requestToken = (text: string) => /\/r\/([\w-]+)/.exec(text)![1]!;

export const getJson = async <T = any>(s: TestServer, url: string, token?: string) =>
  (await s.app.inject({ method: "GET", url, headers: token ? bearer(token) : {} })).json() as T;

/** Confirma el correo con el enlace del último correo encolado (el de verificación). */
export async function verifyEmail(s: TestServer) {
  const mail = await flushMail(s);
  return form(s, "/verify", { token: linkToken(mail.text) });
}

/** Cuenta con el correo ya confirmado. */
export async function registerVerified(s: TestServer, email = "ana@example.com") {
  const { body } = await register(s, email);
  await verifyEmail(s);
  return body;
}
