import { generateKeyPairSync } from "node:crypto";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { FcmPush } from "../src/fcm.js";
import { bearer, flushMail, form, getJson, linkToken, post, putDevice, register, registerVerified, startServer, verifyEmail, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

describe("verificación del correo", () => {
  it("el registro envía el enlace, el GET solo muestra y el POST confirma", async () => {
    const { body } = await register(s);
    expect(await getJson(s, "/me", body.accessToken)).toEqual({ email: "ana@example.com", emailVerified: false });
    const mail = await flushMail(s);
    expect(mail.subject).toContain("Confirma tu correo");
    const token = linkToken(mail.text);

    expect((await s.app.inject({ method: "GET", url: `/verify?token=${token}` })).statusCode).toBe(200);
    expect((await getJson(s, "/me", body.accessToken)).emailVerified).toBe(false);

    expect((await form(s, "/verify", { token })).statusCode).toBe(200);
    expect((await getJson(s, "/me", body.accessToken)).emailVerified).toBe(true);
    expect((await form(s, "/verify", { token })).statusCode).toBe(400);
  });

  it("sin confirmar el correo no se puede invitar a un compañero", async () => {
    const { body } = await register(s);
    expect((await post(s, "/partners", { email: "luis@example.com" }, body.accessToken)).statusCode).toBe(403);
    await verifyEmail(s);
    expect((await post(s, "/partners", { email: "luis@example.com" }, body.accessToken)).statusCode).toBe(201);
  });

  it("se puede pedir otro enlace hasta 3 veces al día y no si ya está confirmado", async () => {
    const { body } = await register(s);
    for (let i = 0; i < 3; i++) expect((await post(s, "/auth/email/verify/resend", undefined, body.accessToken)).statusCode).toBe(202);
    expect((await post(s, "/auth/email/verify/resend", undefined, body.accessToken)).statusCode).toBe(429);
    const ok = await registerVerified(s, "bea@example.com");
    expect((await post(s, "/auth/email/verify/resend", undefined, ok.accessToken)).statusCode).toBe(409);
  });

  it("el enlace caduca a los 7 días", async () => {
    await register(s);
    const token = linkToken((await flushMail(s)).text);
    s.clock.now = new Date(s.clock.now.getTime() + 8 * 86_400_000);
    expect((await form(s, "/verify", { token })).statusCode).toBe(400);
  });
});

describe("push por FCM", () => {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048, privateKeyEncoding: { type: "pkcs8", format: "pem" }, publicKeyEncoding: { type: "spki", format: "pem" } });
  const creds = { project_id: "demo", client_email: "svc@demo.iam.gserviceaccount.com", private_key: privateKey };

  function fakeFetch(statusFor: (token: string) => number) {
    const calls: Array<{ url: string; body: any; auth?: string }> = [];
    const http = (async (url: string, init: any) => {
      if (url.includes("oauth2")) {
        calls.push({ url, body: String(init.body) });
        return new Response(JSON.stringify({ access_token: "acc", expires_in: 3600 }), { status: 200 });
      }
      const body = JSON.parse(init.body);
      calls.push({ url, body, auth: init.headers.authorization });
      return new Response("{}", { status: statusFor(body.message.token) });
    }) as unknown as typeof fetch;
    return { http, calls };
  }

  async function userWithDevices() {
    const ana = await registerVerified(s);
    await putDevice(s, ana.accessToken, { name: "A", fcmToken: "tok-a" });
    await putDevice(s, ana.accessToken, { name: "B", fcmToken: "tok-b" });
    const [u] = await s.deps.db.query("SELECT id FROM users");
    return u!.id as string;
  }

  it("envía a cada dispositivo solo el tipo y el id, y reutiliza el token de acceso", async () => {
    const userId = await userWithDevices();
    const { http, calls } = fakeFetch(() => 200);
    const fcm = new FcmPush(creds, s.deps.db, s.deps.now, http);
    await fcm.notifyUser(userId, { type: "request_resolved", requestId: "r1" });
    await fcm.notifyUser(userId, { type: "partner_confirmed" });
    const sends = calls.filter((c) => c.url.includes("fcm.googleapis.com"));
    expect(sends).toHaveLength(4);
    expect(sends[0]!.url).toContain("/projects/demo/");
    expect(sends[0]!.auth).toBe("Bearer acc");
    expect(sends[0]!.body.message.data).toEqual({ type: "request_resolved", requestId: "r1" });
    expect(calls.filter((c) => c.url.includes("oauth2"))).toHaveLength(1);
    expect(calls[0]!.body).toContain("assertion=");
  });

  it("olvida el token de un dispositivo que FCM da por desconocido", async () => {
    const userId = await userWithDevices();
    const { http } = fakeFetch((t) => (t === "tok-a" ? 404 : 200));
    await new FcmPush(creds, s.deps.db, s.deps.now, http).notifyUser(userId, { type: "partner_confirmed" });
    const rows = await s.deps.db.query("SELECT name, fcm_token FROM devices ORDER BY name");
    expect(rows).toEqual([{ name: "A", fcm_token: null }, { name: "B", fcm_token: "tok-b" }]);
  });

  it("un rechazo de FCM lanza (SafePush lo registra) y no toca los tokens", async () => {
    const userId = await userWithDevices();
    const { http } = fakeFetch(() => 500);
    await expect(new FcmPush(creds, s.deps.db, s.deps.now, http).notifyUser(userId, { type: "partner_confirmed" })).rejects.toThrow(/FCM/);
    expect(await s.deps.db.query("SELECT 1 FROM devices WHERE fcm_token IS NOT NULL")).toHaveLength(2);
  });
});

describe("olvidar el token de push al cerrar sesión", () => {
  it("un token que registra otro dispositivo deja de ser del anterior", async () => {
    const ana = await registerVerified(s);
    await putDevice(s, ana.accessToken, { name: "A", fcmToken: "tok-1" });
    const luis = (await register(s, "luis@example.com")).body;
    await putDevice(s, luis.accessToken, { name: "L", fcmToken: "tok-1" });
    expect(await s.deps.db.query("SELECT name, fcm_token FROM devices ORDER BY name")).toEqual([{ name: "A", fcm_token: null }, { name: "L", fcm_token: "tok-1" }]);
    // Volver a registrar el mismo dispositivo con su token no se lo quita.
    const l = (await s.deps.db.query("SELECT id FROM devices WHERE name = 'L'"))[0]!.id as string;
    await putDevice(s, luis.accessToken, { name: "L2", deviceId: l, fcmToken: "tok-1" });
    expect(await s.deps.db.query("SELECT name, fcm_token FROM devices ORDER BY name")).toEqual([{ name: "A", fcm_token: null }, { name: "L2", fcm_token: "tok-1" }]);
  });

  it("borra el token del dispositivo propio y no toca los demás", async () => {
    const ana = await registerVerified(s);
    const a = (await putDevice(s, ana.accessToken, { name: "A", fcmToken: "tok-a" })).json().deviceId as string;
    await putDevice(s, ana.accessToken, { name: "B", fcmToken: "tok-b" });
    const res = await s.app.inject({ method: "DELETE", url: `/devices/${a}/push-token`, headers: bearer(ana.accessToken) });
    expect(res.statusCode).toBe(204);
    expect(await s.deps.db.query("SELECT name, fcm_token FROM devices ORDER BY name")).toEqual([{ name: "A", fcm_token: null }, { name: "B", fcm_token: "tok-b" }]);
  });

  it("un dispositivo ajeno o desconocido responde 404 con device_unknown y no se toca", async () => {
    const ana = await registerVerified(s);
    const deviceId = (await putDevice(s, ana.accessToken, { name: "A", fcmToken: "tok-a" })).json().deviceId as string;
    const luis = (await register(s, "luis@example.com")).body;
    const ajeno = await s.app.inject({ method: "DELETE", url: `/devices/${deviceId}/push-token`, headers: bearer(luis.accessToken) });
    expect(ajeno.statusCode).toBe(404);
    expect(ajeno.json()).toMatchObject({ code: "device_unknown" });
    const desconocido = await s.app.inject({ method: "DELETE", url: "/devices/6f1c1d4e-0000-4000-8000-000000000000/push-token", headers: bearer(ana.accessToken) });
    expect(desconocido.statusCode).toBe(404);
    expect(await s.deps.db.query("SELECT fcm_token FROM devices")).toEqual([{ fcm_token: "tok-a" }]);
  });

  it("pide sesión y un identificador válido", async () => {
    const ana = await registerVerified(s);
    expect((await s.app.inject({ method: "DELETE", url: "/devices/6f1c1d4e-0000-4000-8000-000000000000/push-token" })).statusCode).toBe(401);
    expect((await s.app.inject({ method: "DELETE", url: "/devices/no-es-un-uuid/push-token", headers: bearer(ana.accessToken) })).statusCode).toBe(400);
  });
});

describe("códigos de error estables", () => {
  it("un dispositivo ajeno o desconocido responde con el código device_unknown", async () => {
    const ana = await registerVerified(s);
    const res = await s.app.inject({ method: "PUT", url: "/devices/register", headers: bearer(ana.accessToken), payload: { deviceId: "6f1c1d4e-0000-4000-8000-000000000000", name: "x" } });
    expect(res.statusCode).toBe(404);
    expect(res.json()).toMatchObject({ code: "device_unknown" });
  });
});

describe("sesión de una cuenta borrada", () => {
  it("un token aún válido de un usuario que ya no existe da 401, no 500", async () => {
    const { body } = await register(s);
    await s.deps.db.query("DELETE FROM users");
    expect((await s.app.inject({ method: "GET", url: "/me", headers: bearer(body.accessToken) })).statusCode).toBe(401);
  });
});
