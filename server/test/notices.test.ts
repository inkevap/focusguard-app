import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { checkHeartbeats, sendBlockedDigests, sendDailyReports } from "../src/notices.js";
import { advance, flushMail, form, linkToken, post, putDevice, register, registerVerified, relogin, requestToken, startServer, withPartner, type TestServer } from "./helpers.js";
import { drainMailQueue } from "../src/mailqueue.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

/** Lo que envía el formulario de preferencias: las casillas marcadas y los dos números. */
const savePrefs = (token: string, checked: string[], extra: Record<string, string> = {}) =>
  form(s, "/prefs", { token, inactive_hours: "48", report_hour: "8", ...Object.fromEntries(checked.map((k) => [k, "on"])), ...extra });
const prefsTokenFrom = (text: string) => /prefs\?token=([\w-]+)/.exec(text)![1]!;

const sendEvents = (token: string, deviceId: string, events: object[]) => post(s, "/events", { deviceId, events }, token);
/** Vacía la cola y devuelve los correos nuevos para el compañero. */
async function newMails() {
  const before = s.mailer.outbox.length;
  await drainMailQueue(s.deps);
  return s.mailer.outbox.slice(before).filter((m) => m.to === "luis@example.com");
}

describe("eventos", () => {
  it("avisa al compañero al momento de los intentos graves, con enlace de preferencias", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    expect((await sendEvents(ana.accessToken, device, [{ type: "uninstall_attempt" }])).statusCode).toBe(202);
    const [mail] = await newMails();
    expect(mail!.subject).toContain("desinstalar");
    expect(mail!.text).toContain("Pixel de Ana");
    expect(mail!.text).toMatch(/\/prefs\?token=[\w-]+/);
  });

  it("junta una ráfaga del mismo tipo en un solo correo", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }, { type: "panic" }, { type: "panic" }]);
    expect(await newMails()).toHaveLength(1);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE type = 'panic'")).toHaveLength(3);
  });

  it("la ráfaga se limita aunque el correo ya haya salido y aunque el teléfono ponga horas distintas", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    const now = s.deps.now().getTime();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    expect(await newMails()).toHaveLength(1);
    await sendEvents(ana.accessToken, device, [1, 2, 3].map((d) => ({ type: "panic", at: new Date(now - d * 86_400_000).toISOString() })));
    expect(await newMails()).toHaveLength(0);
    advance(s, 11 * 60_000);
    await sendEvents(await relogin(s), device, [{ type: "panic" }]);
    expect(await newMails()).toHaveLength(1);
  });

  it("las etiquetas salen en una línea, sin saltos que cuelen texto falso", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "settings_attempt", label: "Ajustes\n\nOtra cosa falsa" }]);
    const [row] = await s.deps.db.query("SELECT detail FROM events");
    expect(row!.detail.label).toBe("Ajustes Otra cosa falsa");
    const [mail] = await newMails();
    expect(mail!.text).toContain("Detalle: Ajustes Otra cosa falsa");
  });

  it("sin compañero no se guarda ni se manda nada", async () => {
    const ana = await registerVerified(s);
    const id = ((await putDevice(s, ana.accessToken, { name: "Móvil" })).json() as { deviceId: string }).deviceId;
    await sendEvents(ana.accessToken, id, [{ type: "panic" }]);
    expect(await newMails()).toHaveLength(0);
    expect(await s.deps.db.query("SELECT 1 FROM events")).toHaveLength(0);
  });

  it("solo se guarda lo que el compañero tiene activado", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "blocked", label: "a.com" }, { type: "settings_attempt" }]);
    const rows = await s.deps.db.query("SELECT type FROM events");
    expect(rows.map((r) => r.type)).toEqual(["settings_attempt"]);
  });

  it("los avisos raros no esperan y el pánico solo espera dos minutos", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "emergency_unlock" }, { type: "emergency_unlock" }]);
    expect(await newMails()).toHaveLength(2);
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    advance(s, 3 * 60_000);
    await sendEvents(await relogin(s), device, [{ type: "panic" }]);
    expect((await newMails()).filter((m) => m.subject.includes("pánico"))).toHaveLength(2);
  });

  it("ignora horas del futuro o de hace más de una semana, y no deja usar el dispositivo de otra cuenta", async () => {
    const { ana, device } = await withPartner(s);
    const now = s.deps.now();
    await sendEvents(ana.accessToken, device, [
      { type: "panic", at: new Date(now.getTime() + 3_600_000).toISOString() },
      { type: "panic", at: new Date(now.getTime() - 9 * 86_400_000).toISOString() },
      { type: "panic", at: new Date(now.getTime() - 3_600_000).toISOString() },
    ]);
    const rows = await s.deps.db.query("SELECT at FROM events ORDER BY at");
    expect(rows.filter((r) => r.at.getTime() === now.getTime())).toHaveLength(2);
    const bea = await registerVerified(s, "bea@example.com");
    expect((await sendEvents(bea.accessToken, device, [{ type: "panic" }])).statusCode).toBe(404);
  });

  it("rechaza tipos desconocidos y lotes enormes", async () => {
    const { ana, device } = await withPartner(s);
    expect((await sendEvents(ana.accessToken, device, [{ type: "inventado" }])).statusCode).toBe(400);
    expect((await sendEvents(ana.accessToken, device, Array.from({ length: 51 }, () => ({ type: "panic" })))).statusCode).toBe(400);
  });
});

describe("resumen de bloqueos", () => {
  it("con el aviso apagado (por defecto) los bloqueos no quedan en el servidor", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "blocked", label: "tiktok.com" }]);
    expect(await s.deps.db.query("SELECT 1 FROM events")).toHaveLength(0);
    expect(await sendBlockedDigests(s.deps)).toBe(0);
  });

  it("con el aviso activado manda un resumen cada 10 minutos", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    const token = prefsTokenFrom((await newMails())[0]!.text);
    await savePrefs(token, ["blocked", "panic", "daily_report"]);

    await sendEvents(ana.accessToken, device, [{ type: "blocked", label: "tiktok.com" }, { type: "blocked", label: "tiktok.com" }, { type: "blocked", label: "instagram.com" }]);
    expect(await sendBlockedDigests(s.deps)).toBe(1);
    const [mail] = await newMails();
    expect(mail!.text).toContain("3 veces");
    expect(mail!.text).toContain("- tiktok.com: 2");

    // Otro bloqueo enseguida espera a que pasen 10 minutos.
    await sendEvents(ana.accessToken, device, [{ type: "blocked", label: "x.com" }]);
    expect(await sendBlockedDigests(s.deps)).toBe(0);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE NOT notified")).toHaveLength(1);
    advance(s, 11 * 60_000);
    expect(await sendBlockedDigests(s.deps)).toBe(1);
  });

  it("si el compañero apaga el resumen después, los pendientes se descartan sin correo", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    const token = prefsTokenFrom((await newMails())[0]!.text);
    await savePrefs(token, ["blocked", "panic"]);
    await sendEvents(ana.accessToken, device, [{ type: "blocked", label: "a.com" }]);
    await savePrefs(token, ["panic"]);
    expect(await sendBlockedDigests(s.deps)).toBe(0);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE NOT notified")).toHaveLength(0);
  });
});

describe("latido", () => {
  it("avisa una vez cuando faltan 48 horas y se rearma con el siguiente latido", async () => {
    const { device } = await withPartner(s);
    await newMails();
    advance(s, 47 * 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(0);
    advance(s, 2 * 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(1);
    expect(await checkHeartbeats(s.deps)).toBe(0);
    const [mail] = await newMails();
    expect(mail!.subject).toContain("dejó de dar señales");

    const token = await relogin(s);
    expect((await post(s, "/heartbeat", { deviceId: device }, token)).statusCode).toBe(200);
    advance(s, 49 * 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(1);
  });

  it("el compañero elige el plazo y el aviso no se repite hasta que haya un latido", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    const token = prefsTokenFrom((await newMails())[0]!.text);
    await savePrefs(token, ["panic", "inactive", "daily_report"], { inactive_hours: "24" });
    advance(s, 25 * 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(1);
    advance(s, 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(0);
    await post(s, "/heartbeat", { deviceId: device }, await relogin(s));
    advance(s, 25 * 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(1);
  });

  it("un compañero nuevo se entera aunque el aviso anterior sea reciente", async () => {
    const { partnerId } = await withPartner(s);
    await newMails();
    advance(s, 49 * 3_600_000);
    expect(await checkHeartbeats(s.deps)).toBe(1);
    await newMails();
    await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: { authorization: `Bearer ${await relogin(s)}` } });
    await form(s, `/r/${requestToken((await flushMail(s)).text)}/approve`, {});
    const token = await relogin(s);
    const invite = await post(s, "/partners", { email: "marta@example.com" }, token);
    expect(invite.statusCode).toBe(201);
    await form(s, "/partners/confirm", { token: linkToken((await flushMail(s)).text) });
    expect(await checkHeartbeats(s.deps)).toBe(1);
  });

  it("el latido de un dispositivo ajeno da 404", async () => {
    const { device } = await withPartner(s);
    const bea = await registerVerified(s, "bea@example.com");
    expect((await post(s, "/heartbeat", { deviceId: device }, bea.accessToken)).statusCode).toBe(404);
  });
});

describe("informe diario", () => {
  it("no sale antes de las 8:00 UTC, sale una vez al día y cuenta lo ocurrido", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    await newMails();
    s.clock.now = new Date("2026-01-02T07:00:00Z");
    expect(await sendDailyReports(s.deps)).toBe(0);
    s.clock.now = new Date("2026-01-02T08:30:00Z");
    expect(await sendDailyReports(s.deps)).toBe(1);
    expect(await sendDailyReports(s.deps)).toBe(0);
    const [mail] = await newMails();
    expect(mail!.subject).toContain("Informe diario");
    expect(mail!.text).toContain("botón de pánico");
    s.clock.now = new Date("2026-01-03T09:00:00Z");
    expect(await sendDailyReports(s.deps)).toBe(1);
    expect((await newMails())[0]!.text).toContain("Sin incidencias");
  });
});

describe("preferencias del compañero", () => {
  async function prefsLink() {
    const { ana, device, partnerId } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    const [mail] = await newMails();
    return { ana, device, partnerId, token: prefsTokenFrom(mail!.text) };
  }

  it("muestra las casillas, guarda lo marcado y el enlace se puede usar varias veces", async () => {
    const { device, token } = await prefsLink();
    const page = await s.app.inject({ method: "GET", url: `/prefs?token=${token}` });
    expect(page.body).toContain("ana@example.com");
    expect(page.body).toContain('name="panic" checked');
    expect(page.body).not.toContain('name="blocked" checked');
    expect(page.body).toContain('name="inactive_hours" value="48"');

    expect((await savePrefs(token, ["uninstall_attempt"])).body).toContain("Guardado");
    expect((await savePrefs(token, ["uninstall_attempt", "blocked"])).statusCode).toBe(200);
    await savePrefs(token, ["uninstall_attempt"]);

    advance(s, 11 * 60_000);
    await sendEvents(await relogin(s), device, [{ type: "panic" }, { type: "uninstall_attempt" }]);
    const mails = await newMails();
    expect(mails).toHaveLength(1);
    expect(mails[0]!.subject).toContain("desinstalar");
  });

  it("el enlace es el mismo en todos los avisos y no crea filas en la base", async () => {
    const { device, token } = await prefsLink();
    advance(s, 11 * 60_000);
    await sendEvents(await relogin(s), device, [{ type: "panic" }]);
    expect(prefsTokenFrom((await newMails())[0]!.text)).toBe(token);
    expect(await s.deps.db.query("SELECT 1 FROM tokens WHERE purpose NOT IN ('refresh','email_verify','partner_confirm')")).toHaveLength(0);
  });

  it("solo guarda las diferencias con los valores por defecto", async () => {
    const { token } = await prefsLink();
    await savePrefs(token, ["panic", "settings_attempt", "uninstall_attempt", "protection_off", "emergency_unlock", "icon_changed", "dns_counts", "urge_report", "inactive", "daily_report"]);
    expect((await s.deps.db.query("SELECT prefs FROM partners"))[0]!.prefs).toEqual({});
    await savePrefs(token, ["panic", "blocked"], { inactive_hours: "72", report_hour: "12" });
    const stored = (await s.deps.db.query("SELECT prefs FROM partners"))[0]!.prefs;
    expect(stored).toMatchObject({ blocked: true, settings_attempt: false, inactive_hours: 72, report_hour: 12 });
    expect(stored.panic).toBeUndefined();
  });

  it("rechaza plazos y horas fuera de rango sin guardar nada", async () => {
    const { token } = await prefsLink();
    const rejected = await savePrefs(token, ["panic", "blocked"], { inactive_hours: "5" });
    expect(rejected.statusCode).toBe(400);
    expect(rejected.body).toContain("no son válidos");
    expect(rejected.body).toContain('name="blocked" checked');
    expect((await savePrefs(token, ["panic"], { report_hour: "30" })).statusCode).toBe(400);
    expect((await savePrefs(token, ["panic"], { inactive_hours: "abc" })).statusCode).toBe(400);
    expect((await s.deps.db.query("SELECT prefs FROM partners"))[0]!.prefs).toEqual({});
  });

  it("la hora del informe es la que elige el compañero", async () => {
    const { token } = await prefsLink();
    await savePrefs(token, ["panic", "daily_report"], { report_hour: "12" });
    s.clock.now = new Date("2026-01-02T11:00:00Z");
    expect(await sendDailyReports(s.deps)).toBe(0);
    s.clock.now = new Date("2026-01-02T12:00:00Z");
    expect(await sendDailyReports(s.deps)).toBe(1);
  });

  it("rechaza un enlace falso o manipulado y no muestra datos", async () => {
    const { partnerId, token } = await prefsLink();
    for (const bad of ["falso", token.slice(0, -1) + (token.endsWith("A") ? "B" : "A"), partnerId]) {
      const res = await s.app.inject({ method: "GET", url: `/prefs?token=${bad}` });
      expect(res.statusCode).toBe(400);
      expect(res.body).not.toContain("ana@example.com");
      expect((await savePrefs(bad, ["blocked"])).statusCode).toBe(400);
    }
  });

  it("deja de valer cuando el compañero se da de baja", async () => {
    const { ana, partnerId, token } = await prefsLink();
    await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: { authorization: `Bearer ${ana.accessToken}` } });
    await form(s, `/r/${requestToken((await flushMail(s)).text)}/approve`, {});
    expect((await s.app.inject({ method: "GET", url: `/prefs?token=${token}` })).statusCode).toBe(400);
  });
});

describe("totales de consultas DNS (D8)", () => {
  const dns = (label: string | undefined) => ({ type: "dns_counts", ...(label === undefined ? {} : { label }) });
  const report = async () => {
    s.clock.now = new Date(s.deps.now().getTime() + 24 * 3_600_000);
    s.clock.now = new Date(Date.UTC(s.clock.now.getUTCFullYear(), s.clock.now.getUTCMonth(), s.clock.now.getUTCDate(), 9));
    expect(await sendDailyReports(s.deps)).toBe(1);
    return (await newMails())[0]!;
  };

  it("el total del día no avisa al momento: solo entra en el informe diario, sin dominios", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    expect((await sendEvents(ana.accessToken, device, [dns("12,340")])).statusCode).toBe(202);
    expect(await newMails()).toHaveLength(0);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE type = 'dns_counts' AND notified")).toHaveLength(1);
    const mail = await report();
    expect(mail.text).toContain("Consultas DNS desde el último informe: 12 bloqueadas y 340 permitidas");
    expect(mail.text).not.toContain("resumen de consultas DNS");
  });

  it("con dos compañeros, el total entra en el informe de los dos y no se gasta con el primero", async () => {
    const { ana, device } = await withPartner(s);
    await post(s, "/partners", { email: "marta@example.com" }, ana.accessToken);
    await drainMailQueue(s.deps);
    const invite = s.mailer.outbox.filter((m) => m.to === "marta@example.com").at(-1)!;
    await form(s, "/partners/confirm", { token: linkToken(invite.text) });
    await newMails();
    await sendEvents(ana.accessToken, device, [dns("7,8")]);
    s.clock.now = new Date(Date.UTC(s.clock.now.getUTCFullYear(), s.clock.now.getUTCMonth(), s.clock.now.getUTCDate() + 1, 9));
    expect(await sendDailyReports(s.deps)).toBe(2);
    await drainMailQueue(s.deps);
    const reports = s.mailer.outbox.filter((m) => m.subject.includes("Informe diario"));
    expect(reports).toHaveLength(2);
    for (const r of reports) expect(r.text).toContain("7 bloqueadas y 8 permitidas");
    // Ya recibieron los dos: el total no se repite.
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE type = 'dns_counts' AND NOT reported")).toHaveLength(0);
  });

  it("suma los totales de varios dispositivos y días dentro de las 24 horas, e ignora lo que no son dos números", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [dns("1,2"), dns("3,4"), dns("<b>hola</b>"), dns("5,x"), dns("9999999999,1"), dns(undefined)]);
    expect((await report()).text).toContain("Consultas DNS desde el último informe: 4 bloqueadas y 6 permitidas");
  });

  it("un total entra en un solo informe: no se repite el día siguiente y espera si el informe no sale", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [dns("1,2")]);
    // El informe de hoy no sale (antes de la hora del compañero): el total sigue esperando.
    s.clock.now = new Date(Date.UTC(s.clock.now.getUTCFullYear(), s.clock.now.getUTCMonth(), s.clock.now.getUTCDate() + 1, 3));
    expect(await sendDailyReports(s.deps)).toBe(0);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE type = 'dns_counts' AND NOT reported")).toHaveLength(1);
    s.clock.now = new Date(s.clock.now.getTime() + 6 * 3_600_000);
    expect(await sendDailyReports(s.deps)).toBe(1);
    expect((await newMails())[0]!.text).toContain("1 bloqueadas y 2 permitidas");
    // Al día siguiente ya no está.
    s.clock.now = new Date(s.clock.now.getTime() + 24 * 3_600_000);
    expect(await sendDailyReports(s.deps)).toBe(1);
    expect((await newMails())[0]!.text).not.toContain("DNS");
  });

  it("sin totales, el informe no habla de DNS", async () => {
    await withPartner(s);
    await newMails();
    expect((await report()).text).not.toContain("DNS");
  });

  it("con el aviso apagado no se guarda nada en el servidor", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [{ type: "panic" }]);
    const token = prefsTokenFrom((await newMails())[0]!.text);
    await savePrefs(token, ["panic", "daily_report"]);
    await sendEvents(ana.accessToken, device, [dns("1,1")]);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE type = 'dns_counts'")).toHaveLength(0);
  });
});

describe("resumen semanal de impulsos (L7)", () => {
  const urge = (label: string | undefined) => ({ type: "urge_report", ...(label === undefined ? {} : { label }) });
  const report = async () => {
    const next = new Date(s.deps.now().getTime() + 24 * 3_600_000);
    s.clock.now = new Date(Date.UTC(next.getUTCFullYear(), next.getUTCMonth(), next.getUTCDate(), 9));
    expect(await sendDailyReports(s.deps)).toBe(1);
    return (await newMails())[0]!;
  };

  it("no avisa al momento: entra en el informe diario con el patrón, sin texto del teléfono", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    expect((await sendEvents(ana.accessToken, device, [urge("5,4,1,22,BOREDOM")])).statusCode).toBe(202);
    expect(await newMails()).toHaveLength(0);
    const mail = await report();
    expect(mail.text).toContain("Impulsos de la semana pasada: 5 en total; 4 los superó y en 1 cedió.");
    expect(mail.text).toContain("La hora más frecuente: 22:00.");
    expect(mail.text).toContain("Lo que más los desencadenó: aburrimiento.");
    expect(mail.text).not.toContain("resumen semanal de impulsos");
  });

  it("sin patrón no dice hora ni desencadenante, y un desencadenante desconocido no pasa", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [urge("2,2,0,,")]);
    let text = (await report()).text;
    expect(text).toContain("Impulsos de la semana pasada: 2 en total; 2 los superó y en 0 cedió.");
    expect(text).not.toContain("hora más frecuente");
    expect(text).not.toContain("desencadenó");
    await sendEvents(await relogin(s), device, [urge("9,5,4,23,<script>")]);
    text = (await report()).text;
    expect(text).not.toContain("<script>");
  });

  it("ignora lo que no es el formato, las cifras que no cuadran y las horas fuera de rango", async () => {
    const { ana, device } = await withPartner(s);
    await newMails();
    await sendEvents(ana.accessToken, device, [urge("hola"), urge("3,3,3,10,STRESS"), urge("3,1,0,99,STRESS"), urge(undefined), urge("1,1,0,7,otra cosa")]);
    const text = (await report()).text;
    expect(text).toContain("Impulsos de la semana pasada: 3 en total; 1 los superó y en 0 cedió.");
    expect(text).not.toContain("99");
    expect(text).not.toContain("hora más frecuente");
    expect(text).not.toContain("hola");
  });

  it("sin resumen, el informe no habla de impulsos; con el aviso apagado no se guarda nada", async () => {
    const { device } = await withPartner(s);
    await newMails();
    expect((await report()).text).not.toContain("Impulsos");
    await sendEvents(await relogin(s), device, [{ type: "panic" }]);
    const token = prefsTokenFrom((await newMails())[0]!.text);
    await savePrefs(token, ["panic", "daily_report"]);
    await sendEvents(await relogin(s), device, [urge("3,3,0,22,STRESS")]);
    expect(await s.deps.db.query("SELECT 1 FROM events WHERE type = 'urge_report'")).toHaveLength(0);
  });
});

describe("recuperación del PIN y reportes", () => {
  it("el código llega al correo confirmado, vale una vez y caduca", async () => {
    const ana = await registerVerified(s);
    expect((await post(s, "/recovery/pin-code", undefined, ana.accessToken)).statusCode).toBe(202);
    const code = /es (\d{6})/.exec((await flushMail(s)).text)![1]!;
    expect((await post(s, "/recovery/pin-code/verify", { code: code === "000000" ? "111111" : "000000" }, ana.accessToken)).statusCode).toBe(400);
    expect((await post(s, "/recovery/pin-code/verify", { code }, ana.accessToken)).statusCode).toBe(200);
    expect((await post(s, "/recovery/pin-code/verify", { code }, ana.accessToken)).statusCode).toBe(400);

    await post(s, "/recovery/pin-code", undefined, ana.accessToken);
    const code2 = /es (\d{6})/.exec((await flushMail(s)).text)![1]!;
    advance(s, 16 * 60_000);
    expect((await post(s, "/recovery/pin-code/verify", { code: code2 }, await relogin(s))).statusCode).toBe(400);
  });

  it("sin correo confirmado no se manda el código, y los intentos se limitan", async () => {
    const { body } = await register(s);
    expect((await post(s, "/recovery/pin-code", undefined, body.accessToken)).statusCode).toBe(403);
    const bea = await registerVerified(s, "bea@example.com");
    for (let i = 0; i < 5; i++) await post(s, "/recovery/pin-code/verify", { code: "123456" }, bea.accessToken);
    expect((await post(s, "/recovery/pin-code/verify", { code: "123456" }, bea.accessToken)).statusCode).toBe(429);
  });

  it("guarda el reporte de bloqueo erróneo y valida el tipo", async () => {
    const ana = await registerVerified(s);
    expect((await post(s, "/reports/wrong-block", { kind: "domain", value: "ejemplo.org" }, ana.accessToken)).statusCode).toBe(201);
    expect((await post(s, "/reports/wrong-block", { kind: "otro", value: "x" }, ana.accessToken)).statusCode).toBe(400);
    expect(await s.deps.db.query("SELECT 1 FROM wrong_block_reports")).toHaveLength(1);
  });
});
