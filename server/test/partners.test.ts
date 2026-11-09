import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { drainMailQueue, purgeMailQueue } from "../src/mailqueue.js";
import { advance, bearer, clearMail, flushMail, form, getJson, linkToken, post, putDevice, register, registerVerified, relogin, requestToken, startServer, withPartner, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

const get = (url: string, token?: string) => s.app.inject({ method: "GET", url, headers: token ? bearer(token) : {} });

describe("compañero", () => {
  it("invita por correo, solo se activa al confirmar con POST y avisa al teléfono", async () => {
    const ana = await registerVerified(s);
    const res = await post(s, "/partners", { email: "Luis@Example.com" }, ana.accessToken);
    expect(res.statusCode).toBe(201);
    const mail = await flushMail(s);
    expect(mail.to).toBe("luis@example.com");
    expect(mail.text).toContain("ana@example.com");
    const token = linkToken(mail.text);

    // El GET (lo que haría un escáner de correo) no cambia nada.
    const page = await get(`/partners/confirm?token=${token}`);
    expect(page.body).toContain("ana@example.com");
    expect(((await get("/partners", ana.accessToken)).json() as any).partners[0].status).toBe("pendiente");

    expect((await form(s, "/partners/confirm", { token })).statusCode).toBe(200);
    expect(((await get("/partners", ana.accessToken)).json() as any).partners[0].status).toBe("aceptado");
    expect(s.push.sent).toEqual([{ userId: expect.any(String), message: { type: "partner_confirmed" } }]);
    // El enlace vale una vez.
    expect((await form(s, "/partners/confirm", { token })).statusCode).toBe(400);
  });

  it("no se puede invitar a uno mismo ni dos veces a la misma persona, hay un tope de tres y cancelar una invitación es libre", async () => {
    const ana = await registerVerified(s);
    expect((await post(s, "/partners", { email: "ana@example.com" }, ana.accessToken)).statusCode).toBe(400);
    const first = await post(s, "/partners", { email: "luis@example.com" }, ana.accessToken);
    expect((await post(s, "/partners", { email: "LUIS@example.com" }, ana.accessToken)).statusCode).toBe(409);
    expect((await post(s, "/partners", { email: "marta@example.com" }, ana.accessToken)).statusCode).toBe(201);
    expect((await post(s, "/partners", { email: "pedro@example.com" }, ana.accessToken)).statusCode).toBe(201);
    expect((await post(s, "/partners", { email: "otro@example.com" }, ana.accessToken)).statusCode).toBe(409);
    const id = (first.json() as { id: string }).id;
    expect((await s.app.inject({ method: "DELETE", url: `/partners/${id}`, headers: bearer(ana.accessToken) })).statusCode).toBe(204);
    // El enlace de la invitación cancelada ya no sirve.
    await drainMailQueue(s.deps);
    const cancelled = s.mailer.outbox.find((m) => m.to === "luis@example.com")!;
    expect((await form(s, "/partners/confirm", { token: linkToken(cancelled.text) })).statusCode).toBe(400);
    expect((await post(s, "/partners", { email: "otro@example.com" }, ana.accessToken)).statusCode).toBe(201);
  });

  it("no deja tocar el compañero de otra cuenta", async () => {
    const { partnerId } = await withPartner(s);
    const bea = (await register(s, "bea@example.com")).body;
    expect((await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: bearer(bea.accessToken) })).statusCode).toBe(404);
  });

  it("quitar a un compañero que ya aceptó necesita su aprobación", async () => {
    const { ana, partnerId } = await withPartner(s);
    const del = await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: bearer(ana.accessToken) });
    expect(del.statusCode).toBe(202);
    expect(((await get("/partners", ana.accessToken)).json() as any).partners[0].status).toBe("aceptado");
    // Pedirlo otra vez no crea otra solicitud.
    expect((await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: bearer(ana.accessToken) })).statusCode).toBe(409);

    const token = requestToken((await flushMail(s)).text);
    expect((await form(s, `/r/${token}/approve`, {})).statusCode).toBe(200);
    expect(((await get("/partners", ana.accessToken)).json() as any).partners).toEqual([]);
  });
});

describe("varios compañeros", () => {
  /** Invita y confirma a otro compañero de Ana. */
  async function addPartner(token: string, email: string) {
    await post(s, "/partners", { email }, token);
    await drainMailQueue(s.deps);
    const mail = s.mailer.outbox.filter((m) => m.to === email).at(-1)!;
    await form(s, "/partners/confirm", { token: linkToken(mail.text) });
  }
  const mailsTo = (to: string) => s.mailer.outbox.filter((m) => m.to === to);

  it("una solicitud llega a todos, decide el primero que responde y el enlace de los demás deja de valer", async () => {
    const { ana, device } = await withPartner(s);
    await addPartner(ana.accessToken, "marta@example.com");
    await clearMail(s);
    const created = await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "Apagar el bloqueo", detail: {} }, ana.accessToken);
    expect(created.statusCode).toBe(201);
    await drainMailQueue(s.deps);
    const luis = requestToken(mailsTo("luis@example.com").at(-1)!.text);
    const marta = requestToken(mailsTo("marta@example.com").at(-1)!.text);
    expect(luis).not.toBe(marta);

    const id = (created.json() as { requestId: string }).requestId;
    // Un rechazo no cierra la solicitud mientras otro pueda aprobar.
    expect((await form(s, `/r/${marta}/reject`, {})).statusCode).toBe(200);
    expect((await getJson(s, `/requests/${id}`, ana.accessToken)).status).toBe("pendiente");
    // Aprueba Luis: decide él y queda aprobada.
    expect((await form(s, `/r/${luis}/approve`, {})).statusCode).toBe(200);
    expect((await getJson(s, `/requests/${id}`, ana.accessToken)).status).toBe("aprobada");
    expect((await form(s, `/r/${marta}/approve`, {})).statusCode).toBe(400);
  });

  it("si todos rechazan, la solicitud queda rechazada", async () => {
    const { ana, device } = await withPartner(s);
    await addPartner(ana.accessToken, "marta@example.com");
    await clearMail(s);
    const created = await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "Apagar el bloqueo", detail: {} }, ana.accessToken);
    await drainMailQueue(s.deps);
    const luis = requestToken(mailsTo("luis@example.com").at(-1)!.text);
    const marta = requestToken(mailsTo("marta@example.com").at(-1)!.text);
    await form(s, `/r/${luis}/reject`, {});
    await form(s, `/r/${marta}/reject`, {});
    expect((await getJson(s, `/requests/${(created.json() as { requestId: string }).requestId}`, ana.accessToken)).status).toBe("rechazada");
  });

  it("el segundo compañero también ve la solicitud pendiente en «personas a las que ayudas»", async () => {
    const { ana, device } = await withPartner(s);
    await addPartner(ana.accessToken, "marta@example.com");
    const marta = await registerVerified(s, "marta@example.com");
    await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "Apagar el bloqueo", detail: {} }, ana.accessToken);
    expect((await getJson(s, "/partners/helping", marta.accessToken)).helping[0].pending).toBe(1);
  });

  it("dar de baja a un compañero no tumba las solicitudes que también esperan a los demás", async () => {
    const { ana, device, partnerId } = await withPartner(s);
    await addPartner(ana.accessToken, "marta@example.com");
    await clearMail(s);
    const created = await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "Apagar el bloqueo", detail: {} }, ana.accessToken);
    await drainMailQueue(s.deps);
    const luis = requestToken(mailsTo("luis@example.com").at(-1)!.text);
    const marta = requestToken(mailsTo("marta@example.com").at(-1)!.text);
    const id = (created.json() as { requestId: string }).requestId;

    // Quitar a Luis es solo cosa de Luis: Marta no recibe esa petición.
    advance(s, 25 * 3600_000);
    await clearMail(s);
    expect((await s.app.inject({ method: "DELETE", url: `/partners/${partnerId}`, headers: bearer(await relogin(s)) })).statusCode).toBe(202);
    await drainMailQueue(s.deps);
    expect(mailsTo("marta@example.com")).toEqual([]);
    const removal = requestToken(mailsTo("luis@example.com").at(-1)!.text);
    expect((await form(s, `/r/${removal}/approve`, {})).statusCode).toBe(200);

    // Luis ya no es compañero: su enlace de la otra solicitud no vale, el de Marta sí y la solicitud sigue pendiente.
    expect((await form(s, `/r/${luis}/approve`, {})).statusCode).toBe(400);
    expect((await getJson(s, `/requests/${id}`, await relogin(s))).status).toBe("pendiente");
    expect((await form(s, `/r/${marta}/approve`, {})).statusCode).toBe(200);
    expect((await getJson(s, `/requests/${id}`, await relogin(s))).status).toBe("aprobada");
  });
});

describe("personas a las que ayudas (I3)", () => {
  it("quien aceptó ser compañero ve a quién ayuda, con las solicitudes que esperan", async () => {
    const { ana, device } = await withPartner(s);
    await clearMail(s);
    const luis = await registerVerified(s, "luis@example.com");
    expect(await getJson(s, "/partners/helping", ana.accessToken)).toEqual({ helping: [] });
    expect((await getJson(s, "/partners/helping", luis.accessToken)).helping).toEqual([
      { id: expect.any(String), email: "ana@example.com", since: expect.any(String), mutual: false, pending: 0 },
    ]);
    await post(s, "/requests", { deviceId: device, actionType: 2003, summary: "Apagar el bloqueo", detail: {} }, ana.accessToken);
    expect(((await getJson(s, "/partners/helping", luis.accessToken)).helping[0])).toMatchObject({ pending: 1 });
  });

  it("es mutuo cuando cada uno es compañero del otro", async () => {
    const { ana } = await withPartner(s);
    await clearMail(s);
    const luis = await registerVerified(s, "luis@example.com");
    expect((await post(s, "/partners", { email: "ana@example.com" }, luis.accessToken)).statusCode).toBe(201);
    // Hasta que ana acepta, la relación no es mutua.
    expect((await getJson(s, "/partners/helping", luis.accessToken)).helping[0].mutual).toBe(false);
    const token = linkToken((await flushMail(s)).text);
    expect((await form(s, "/partners/confirm", { token })).statusCode).toBe(200);
    expect((await getJson(s, "/partners/helping", luis.accessToken)).helping[0].mutual).toBe(true);
    expect((await getJson(s, "/partners/helping", ana.accessToken)).helping[0]).toMatchObject({ email: "luis@example.com", mutual: true });
  });

  it("no enseña nada sin el correo confirmado ni a quien no ha iniciado sesión", async () => {
    await withPartner(s);
    await clearMail(s);
    // Registrarse con el correo de otro no basta: hay que confirmarlo.
    const intruso = (await register(s, "luis@example.com")).body;
    expect(await getJson(s, "/partners/helping", intruso.accessToken)).toEqual({ helping: [] });
    expect((await get("/partners/helping")).statusCode).toBe(401);
  });
});

describe("solicitudes de aprobación", () => {
  const create = (token: string, deviceId: string, extra: object = {}) =>
    post(s, "/requests", { deviceId, actionType: 2003, summary: "Apagar el bloqueo de contenido adulto", detail: { profileId: 7 }, ...extra }, token);

  it("sin compañero que haya aceptado no se puede pedir", async () => {
    const ana = await registerVerified(s);
    const device = ((await putDevice(s, ana.accessToken, { name: "Pixel" })).json() as { deviceId: string }).deviceId;
    expect((await create(ana.accessToken, device)).statusCode).toBe(409);
    await post(s, "/partners", { email: "luis@example.com" }, ana.accessToken);
    expect((await create(ana.accessToken, device)).statusCode).toBe(409); // invitado, pero sin aceptar
  });

  it("flujo completo: crear, correo con dos enlaces, aprobar, aplicar una sola vez", async () => {
    const { ana, device } = await withPartner(s);
    const res = await create(ana.accessToken, device);
    expect(res.statusCode).toBe(201);
    const { requestId } = res.json() as { requestId: string };

    const mail = await flushMail(s);
    expect(mail.to).toBe("luis@example.com");
    expect(mail.text).toContain("Apagar el bloqueo de contenido adulto");
    expect(mail.text).toContain("Pixel de Ana");
    // Un solo enlace, a la página con los dos botones; el correo ya no lleva «Aprobar» ni «Rechazar» sueltos.
    expect(mail.text).toMatch(/\/r\/[\w-]+(\s|$)/);
    expect(mail.text).not.toMatch(/\/(approve|reject)/);
    expect(mail.html).toContain("Revisar la solicitud");
    expect(mail.html).not.toMatch(/\/(approve|reject)/);
    const token = requestToken(mail.text);

    // La página del enlace enseña el cambio y los dos botones, y un GET no decide nada.
    const choose = await get(`/r/${token}`);
    expect(choose.statusCode).toBe(200);
    expect(choose.body).toContain("Apagar el bloqueo de contenido adulto");
    expect(choose.body).toContain(`/r/${token}/approve`);
    expect(choose.body).toContain(`/r/${token}/reject`);

    expect(((await get(`/requests/${requestId}`, ana.accessToken)).json() as any).status).toBe("pendiente");
    // El GET del enlace solo muestra la confirmación.
    const page = await get(`/r/${token}/approve`);
    expect(page.body).toContain("Apagar el bloqueo de contenido adulto");
    expect(((await get(`/requests/${requestId}`, ana.accessToken)).json() as any).status).toBe("pendiente");

    expect((await form(s, `/r/${token}/approve`, {})).statusCode).toBe(200);
    const state = (await get(`/requests/${requestId}`, ana.accessToken)).json() as any;
    expect(state).toMatchObject({ status: "aprobada", consumed: false, actionType: 2003, detail: { profileId: 7 } });
    expect(s.push.sent.at(-1)!.message).toEqual({ type: "request_resolved", requestId });

    expect((await post(s, `/requests/${requestId}/consume`, undefined, ana.accessToken)).statusCode).toBe(200);
    expect((await post(s, `/requests/${requestId}/consume`, undefined, ana.accessToken)).statusCode).toBe(409);
    expect(((await get(`/requests/${requestId}`, ana.accessToken)).json() as any).detail).toBeUndefined();
    // El enlace tampoco se puede usar de nuevo, ni para rechazar.
    expect((await form(s, `/r/${token}/reject`, {})).statusCode).toBe(400);
  });

  it("rechazada no se puede aplicar", async () => {
    const { ana, device } = await withPartner(s);
    const { requestId } = (await create(ana.accessToken, device)).json() as { requestId: string };
    const token = requestToken((await flushMail(s)).text);
    await form(s, `/r/${token}/reject`, {});
    expect(((await get(`/requests/${requestId}`, ana.accessToken)).json() as any).status).toBe("rechazada");
    expect((await post(s, `/requests/${requestId}/consume`, undefined, ana.accessToken)).statusCode).toBe(409);
  });

  it("caduca a las 48 horas", async () => {
    const { ana, device } = await withPartner(s);
    const { requestId } = (await create(ana.accessToken, device)).json() as { requestId: string };
    const token = requestToken((await flushMail(s)).text);
    advance(s, 49 * 3_600_000);
    ana.accessToken = await relogin(s);
    expect(((await get(`/requests/${requestId}`, ana.accessToken)).json() as any).status).toBe("caducada");
    expect((await form(s, `/r/${token}/approve`, {})).statusCode).toBe(400);
  });

  it("cancelar invalida el enlace", async () => {
    const { ana, device } = await withPartner(s);
    const { requestId } = (await create(ana.accessToken, device)).json() as { requestId: string };
    const token = requestToken((await flushMail(s)).text);
    expect((await post(s, `/requests/${requestId}/cancel`, undefined, ana.accessToken)).statusCode).toBe(200);
    expect((await form(s, `/r/${token}/approve`, {})).statusCode).toBe(400);
    expect((await post(s, `/requests/${requestId}/cancel`, undefined, ana.accessToken)).statusCode).toBe(409);
  });

  it("solo el dueño la ve, y el dispositivo tiene que ser suyo", async () => {
    const { ana, device } = await withPartner(s);
    const bea = (await register(s, "bea@example.com")).body;
    const { requestId } = (await create(ana.accessToken, device)).json() as { requestId: string };
    const other = await get(`/requests/${requestId}`, bea.accessToken);
    expect(other.statusCode).toBe(404);
    expect((other.json() as { code?: string }).code).toBe("request_unknown");
    expect((await create(bea.accessToken, device)).statusCode).toBe(404);
  });

  it("limita a 3 solicitudes por usuario cada 24 horas", async () => {
    const { ana, device } = await withPartner(s);
    for (let i = 0; i < 3; i++) expect((await create(ana.accessToken, device)).statusCode).toBe(201);
    expect((await create(ana.accessToken, device)).statusCode).toBe(429);
    advance(s, 25 * 3_600_000);
    expect((await create(await relogin(s), device)).statusCode).toBe(201);
  });

  it("un código de acción del teléfono nunca activa un efecto del servidor", async () => {
    const { ana, device } = await withPartner(s);
    await create(ana.accessToken, device, { actionType: 3001 });
    await form(s, `/r/${requestToken((await flushMail(s)).text)}/approve`, {});
    expect(((await get("/partners", ana.accessToken)).json() as any).partners[0].status).toBe("aceptado");
  });

  it("rechaza códigos fuera de rango y detalles enormes", async () => {
    const { ana, device } = await withPartner(s);
    expect((await create(ana.accessToken, device, { actionType: 0 })).statusCode).toBe(400);
    expect((await create(ana.accessToken, device, { actionType: 10_000 })).statusCode).toBe(400);
    expect((await create(ana.accessToken, device, { actionType: 1 })).statusCode).toBe(201);
    expect((await create(ana.accessToken, device, { detail: { x: "a".repeat(3000) } })).statusCode).toBe(400);
  });

  it("escapa el texto del teléfono en la página del compañero", async () => {
    const { ana, device } = await withPartner(s);
    await create(ana.accessToken, device, { summary: '<img src=x onerror=alert(1)>' });
    const token = requestToken((await flushMail(s)).text);
    const page = await get(`/r/${token}/approve`);
    expect(page.body).not.toContain("<img");
    expect(page.body).toContain("&lt;img");
  });
});

describe("cola de correos", () => {
  it("reintenta con espera creciente y borra el correo al enviarse", async () => {
    await register(s);
    await clearMail(s);
    await post(s, "/auth/password/reset", { email: "ana@example.com" });
    const real = s.mailer.send.bind(s.mailer);
    let failures = 2;
    s.mailer.send = async (m) => { if (failures-- > 0) throw new Error("SMTP caído"); return real(m); };

    expect(await drainMailQueue(s.deps)).toBe(0);
    expect(await drainMailQueue(s.deps)).toBe(0); // aún en espera
    advance(s, 61_000);
    expect(await drainMailQueue(s.deps)).toBe(0); // segundo fallo
    advance(s, 61_000);
    expect(await drainMailQueue(s.deps)).toBe(0); // la espera ahora es de 2 minutos
    advance(s, 2 * 60_000);
    expect(await drainMailQueue(s.deps)).toBe(1);
    expect(await s.deps.db.query("SELECT 1 FROM mail_outbox")).toHaveLength(0);
  });

  it("deja de intentar tras 8 fallos y borra los antiguos", async () => {
    await register(s);
    await clearMail(s);
    await post(s, "/auth/password/reset", { email: "ana@example.com" });
    s.mailer.send = async () => { throw new Error("nunca"); };
    for (let i = 0; i < 12; i++) { await drainMailQueue(s.deps); advance(s, 5 * 3_600_000); }
    const [row] = await s.deps.db.query("SELECT attempts FROM mail_outbox");
    expect(row!.attempts).toBe(8);
    await purgeMailQueue(s.deps.db, s.deps.now());
    expect(await s.deps.db.query("SELECT 1 FROM mail_outbox")).toHaveLength(0);
  });
});
