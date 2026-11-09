import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { expireRequests, purgeHistory } from "../src/approvals.js";
import { redactUrl } from "../src/deps.js";
import { startJobs } from "../src/jobs.js";
import { drainMailQueue, enqueueMail } from "../src/mailqueue.js";
import { SafePush } from "../src/push.js";
import { advance, flushMail, form, post, relogin, requestToken, getJson, withPartner, startServer, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const quietLog = { error: () => {} };

describe("trabajos periódicos", () => {
  it("no solapa ejecuciones del mismo trabajo y stop espera a la que sigue en curso", async () => {
    let active = 0, maxActive = 0, runs = 0;
    const jobs = startJobs([{ name: "lento", everyMs: 5, run: async () => { runs++; active++; maxActive = Math.max(maxActive, active); await sleep(40); active--; } }], quietLog);
    await sleep(60);
    await jobs.stop();
    expect(maxActive).toBe(1);
    expect(active).toBe(0);
    expect(runs).toBeGreaterThanOrEqual(1);
  });

  it("un trabajo que falla se registra y no detiene a los demás", async () => {
    const errors: unknown[] = [];
    let ok = 0;
    const jobs = startJobs(
      [
        { name: "malo", everyMs: 5, run: async () => { throw new Error("boom"); } },
        { name: "bueno", everyMs: 5, run: async () => { ok++; } },
      ],
      { error: (e: unknown) => void errors.push(e) },
    );
    await sleep(30);
    await jobs.stop();
    expect(errors.length).toBeGreaterThan(0);
    expect(ok).toBeGreaterThan(1);
  });
});

describe("caducidad e historial", () => {
  it("marca como caducadas las solicitudes vencidas y avisa al teléfono", async () => {
    const { ana, device } = await withPartner(s);
    const { requestId } = (await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "x" }, ana.accessToken)).json() as { requestId: string };
    expect(await expireRequests(s.deps)).toBe(0);
    advance(s, 49 * 3_600_000);
    expect(await expireRequests(s.deps)).toBe(1);
    expect(await expireRequests(s.deps)).toBe(0);
    expect(s.push.sent.at(-1)!.message).toEqual({ type: "request_resolved", requestId });
    const [row] = await s.deps.db.query("SELECT status FROM requests WHERE id = $1", [requestId]);
    expect(row!.status).toBe("caducada");
  });

  it("borra a los 90 días las solicitudes resueltas y los compañeros dados de baja, pero no lo vigente", async () => {
    const { ana, device, partnerId } = await withPartner(s);
    await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "x" }, ana.accessToken);
    const token = requestToken((await flushMail(s)).text);
    await form(s, `/r/${token}/reject`, {});
    advance(s, 91 * 86_400_000);
    await purgeHistory(s.deps.db, s.deps.now());
    expect(await s.deps.db.query("SELECT 1 FROM requests")).toHaveLength(0);
    expect(await s.deps.db.query("SELECT 1 FROM partners WHERE id = $1", [partnerId])).toHaveLength(1);
  });

  it("dar de baja al compañero cancela sus solicitudes pendientes y sus enlaces", async () => {
    const { ana, device, partnerId } = await withPartner(s);
    const { requestId } = (await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "Apagar" }, ana.accessToken)).json() as { requestId: string };
    const unrelated = requestToken((await flushMail(s)).text);

    await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: { authorization: `Bearer ${ana.accessToken}` } });
    const removal = requestToken((await flushMail(s)).text);
    expect((await form(s, `/r/${removal}/approve`, {})).statusCode).toBe(200);

    expect((await getJson(s, `/requests/${requestId}`, ana.accessToken)).status).toBe("cancelada");
    expect((await form(s, `/r/${unrelated}/approve`, {})).statusCode).toBe(400);
    expect(await getJson(s, "/partners", ana.accessToken)).toEqual({ partners: [] });
    // Un compañero nuevo puede invitarse después.
    expect((await post(s, "/partners", { email: "marta@example.com" }, await relogin(s))).statusCode).toBe(201);
  });
});

describe("cola de correos: deduplicación y retraso", () => {
  it("no encola dos veces la misma clave y respeta la hora de inicio", async () => {
    const now = s.deps.now();
    const mail = { to: "luis@example.com", subject: "aviso", text: "x" };
    await enqueueMail(s.deps.db, mail, now, { dedupeKey: "latido:1", notBefore: new Date(now.getTime() + 600_000) });
    await enqueueMail(s.deps.db, mail, now, { dedupeKey: "latido:1" });
    expect(await s.deps.db.query("SELECT 1 FROM mail_outbox")).toHaveLength(1);
    await flushMail(s).catch(() => {});
    expect(s.mailer.outbox).toHaveLength(0);
    advance(s, 601_000);
    expect((await flushMail(s)).subject).toBe("aviso");
  });
});

describe("push seguro", () => {
  it("un fallo del push se registra y no rompe la operación", async () => {
    const errors: unknown[] = [];
    const safe = new SafePush({ notifyUser: async () => { throw new Error("FCM caído"); } }, { error: (e: unknown) => void errors.push(e) });
    await expect(safe.notifyUser("u", { type: "partner_confirmed" })).resolves.toBeUndefined();
    expect(errors).toHaveLength(1);
  });
});

describe("registro sin tokens", () => {
  it("oculta los tokens de las direcciones de los enlaces", () => {
    expect(redactUrl("/r/abc123_-X/approve")).toBe("/r/[token]/approve");
    expect(redactUrl("/partners/confirm?token=abc123")).toBe("/partners/confirm?token=[token]");
    expect(redactUrl("/reset?token=abc&x=1")).toBe("/reset?token=[token]&x=1");
    expect(redactUrl("/auth/login")).toBe("/auth/login");
  });
});

describe("enlaces del correo", () => {
  it("las visitas de un escáner no gastan el cupo de las acciones", async () => {
    const { ana, device } = await withPartner(s);
    await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "x" }, ana.accessToken);
    const token = requestToken((await flushMail(s)).text);
    for (let i = 0; i < 100; i++) await s.app.inject({ method: "GET", url: `/r/${token}/approve` });
    expect((await form(s, `/r/${token}/approve`, {})).statusCode).toBe(200);
  });

  it("al darse de baja el compañero se avisa de cada solicitud cancelada", async () => {
    const { ana, device, partnerId } = await withPartner(s);
    const { requestId: other } = (await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "x" }, ana.accessToken)).json() as { requestId: string };
    await flushMail(s);
    const { requestId: removal } = (await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: { authorization: `Bearer ${ana.accessToken}` } })).json() as { requestId: string };
    await form(s, `/r/${requestToken((await flushMail(s)).text)}/approve`, {});
    const notified = s.push.sent.filter((p) => p.message.type === "request_resolved").map((p) => (p.message as { requestId: string }).requestId);
    expect(notified).toEqual(expect.arrayContaining([removal, other]));
  });
});

describe("cola de correos: entrega parcial", () => {
  it("un correo entregado se borra aunque otro del mismo lote falle", async () => {
    const now = s.deps.now();
    for (const to of ["a@example.com", "b@example.com"]) await enqueueMail(s.deps.db, { to, subject: "s", text: "t" }, now);
    const real = s.mailer.send.bind(s.mailer);
    s.mailer.send = async (m) => { if (m.to === "b@example.com") throw new Error("rechazado"); return real(m); };
    expect(await drainMailQueue(s.deps)).toBe(1);
    const left = await s.deps.db.query("SELECT to_addr FROM mail_outbox");
    expect(left.map((r) => r.to_addr)).toEqual(["b@example.com"]);
  });
});
