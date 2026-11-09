import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { bearer, registerVerified, startServer, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

const put = (token: string, body: Buffer | string, base: number | string, format: number | string = 1, type = "application/octet-stream") =>
  s.app.inject({
    method: "PUT",
    url: "/sync",
    headers: { ...bearer(token), "content-type": type, "x-sync-base": String(base), "x-sync-format": String(format) },
    payload: body,
  });
const get = (token?: string) => s.app.inject({ method: "GET", url: "/sync", headers: token ? bearer(token) : {} });

describe("sincronización entre dispositivos (N4)", () => {
  it("guarda bytes opacos con versión y los devuelve tal cual, solo a su dueño", async () => {
    const ana = await registerVerified(s);
    const bea = await registerVerified(s, "bea@example.com");
    expect((await get(ana.accessToken)).statusCode).toBe(404);
    const raw = Buffer.from([0, 255, 1, 254, 2, 253]);
    const first = await put(ana.accessToken, raw, 0);
    expect(first.statusCode).toBe(200);
    expect((first.json() as { version: number }).version).toBe(1);
    const res = await get(ana.accessToken);
    expect(res.headers["content-type"]).toContain("application/octet-stream");
    expect(res.headers["x-sync-version"]).toBe("1");
    expect(res.headers["x-sync-format"]).toBe("1");
    expect(Buffer.compare(res.rawPayload, raw)).toBe(0);
    expect((await get(bea.accessToken)).statusCode).toBe(404);
    expect((await get()).statusCode).toBe(401);
  });

  it("un teléfono que escribe sobre una versión vieja recibe un conflicto y no pisa al otro", async () => {
    const ana = await registerVerified(s);
    expect((await put(ana.accessToken, "uno", 0)).statusCode).toBe(200);
    // El teléfono B también creía que no había nada.
    const late = await put(ana.accessToken, "intruso", 0);
    expect(late.statusCode).toBe(409);
    expect((late.json() as { code: string }).code).toBe("sync_conflict");
    // El A sigue con la versión 1 y escribe la 2; el B, que leyó la 1, ya llega tarde.
    expect(((await put(ana.accessToken, "dos", 1)).json() as { version: number }).version).toBe(2);
    expect((await put(ana.accessToken, "tres", 1)).statusCode).toBe(409);
    expect((await get(ana.accessToken)).rawPayload.toString()).toBe("dos");
  });

  it("cada cuenta tiene su documento y se puede borrar", async () => {
    const ana = await registerVerified(s);
    const bea = await registerVerified(s, "bea@example.com");
    await put(ana.accessToken, "de ana", 0);
    expect((await put(bea.accessToken, "de bea", 0)).statusCode).toBe(200);
    expect((await get(ana.accessToken)).rawPayload.toString()).toBe("de ana");
    expect((await s.app.inject({ method: "DELETE", url: "/sync", headers: bearer(ana.accessToken) })).statusCode).toBe(204);
    expect((await get(ana.accessToken)).statusCode).toBe(404);
    expect((await get(bea.accessToken)).rawPayload.toString()).toBe("de bea");
  });

  it("tras borrar, la versión sigue subiendo: quien leyó antes del borrado no pisa el documento nuevo", async () => {
    const ana = await registerVerified(s);
    await put(ana.accessToken, "viejo", 0);
    // El teléfono A leyó la versión 1 y está mezclando; entretanto se borra y otro teléfono crea uno nuevo.
    await s.app.inject({ method: "DELETE", url: "/sync", headers: bearer(ana.accessToken) });
    // Con el documento borrado, escribir sobre la versión vieja no sirve...
    expect((await put(ana.accessToken, "tarde", 1)).statusCode).toBe(409);
    // ...y crear uno nuevo (base 0) sí, con una versión que no se repite.
    const created = await put(ana.accessToken, "nuevo", 0);
    expect(created.statusCode).toBe(200);
    expect((created.json() as { version: number }).version).toBe(3);
    expect((await put(ana.accessToken, "tarde otra vez", 1)).statusCode).toBe(409);
    expect((await get(ana.accessToken)).rawPayload.toString()).toBe("nuevo");
    // Crear sobre un documento que existe (base 0) sigue siendo un conflicto.
    expect((await put(ana.accessToken, "otro", 0)).statusCode).toBe(409);
  });

  it("rechaza lo que no es binario, vacío, demasiado grande o con cabeceras mal", async () => {
    const ana = await registerVerified(s);
    expect((await put(ana.accessToken, JSON.stringify({ a: 1 }), 0, 1, "application/json")).statusCode).toBe(415);
    expect((await put(ana.accessToken, Buffer.alloc(0), 0)).statusCode).toBe(415);
    expect((await put(ana.accessToken, Buffer.alloc(300_000), 0)).statusCode).toBe(413);
    expect((await put(ana.accessToken, "x", -1)).statusCode).toBe(400);
    expect((await put(ana.accessToken, "x", "abc")).statusCode).toBe(400);
    expect((await put(ana.accessToken, "x", 0, 0)).statusCode).toBe(400);
  });
});
