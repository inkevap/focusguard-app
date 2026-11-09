import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { applyList } from "../src/blocklists.js";
import { loadConfig } from "../src/config.js";
import { bearer, getJson, putDevice, registerVerified, startServer, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

describe("reglas para el resolvedor", () => {
  it("convierte dominios en reglas ||dominio^ y las excepciones en reglas de permitido", async () => {
    await applyList(s.deps.db, "domains", new Set(["a.example.com", "b.example.org"]), s.deps.now());
    await applyList(s.deps.db, "exceptions", new Set(["b.example.org"]), s.deps.now());
    const res = await s.app.inject({ method: "GET", url: "/blocklists/dns-rules.txt" });
    expect(res.statusCode).toBe(200);
    expect(res.headers["x-list-version"]).toBe("1.1");
    expect(res.body.split("\n")).toEqual(["! FocusGuard 1.1", "||a.example.com^", "||b.example.org^", "@@||b.example.org^", ""]);
  });

  it("las reglas cambian de versión con cualquiera de las dos listas", async () => {
    await applyList(s.deps.db, "domains", new Set(["a.example.com"]), s.deps.now());
    const first = (await s.app.inject({ method: "GET", url: "/blocklists/dns-rules.txt" })).headers["x-list-version"];
    await applyList(s.deps.db, "exceptions", new Set(["a.example.com"]), s.deps.now());
    expect((await s.app.inject({ method: "GET", url: "/blocklists/dns-rules.txt" })).headers["x-list-version"]).not.toBe(first);
  });
});

describe("configuración del resolvedor para el teléfono", () => {
  const withDns = () => {
    s.deps.config.DNS_DOH_URL = "https://dns.fg.test/dns-query";
    s.deps.config.DNS_DOH_BOOTSTRAP = ["203.0.113.7"];
  };

  it("da la dirección con un identificador estable y propio de cada dispositivo", async () => {
    withDns();
    const ana = await registerVerified(s);
    const a = ((await putDevice(s, ana.accessToken, { name: "A" })).json() as { deviceId: string }).deviceId;
    const b = ((await putDevice(s, ana.accessToken, { name: "B" })).json() as { deviceId: string }).deviceId;
    const one = await getJson(s, `/dns/config?deviceId=${a}`, ana.accessToken);
    const again = await getJson(s, `/dns/config?deviceId=${a}`, ana.accessToken);
    const two = await getJson(s, `/dns/config?deviceId=${b}`, ana.accessToken);
    expect(one.dohUrl).toMatch(/^https:\/\/dns\.fg\.test\/dns-query\/[a-f0-9]{24}$/);
    expect(one.dohUrl).toBe(again.dohUrl);
    expect(two.dohUrl).not.toBe(one.dohUrl);
    expect(one.dohUrl).not.toContain(a);
    expect(one.failOpen).toBe(true);
    expect(one.bootstrapIps).toEqual(["203.0.113.7"]);
    // Se puede buscar el dispositivo a partir del identificador.
    const [row] = await s.deps.db.query("SELECT id FROM devices WHERE dns_client_id = $1", [one.dohUrl.split("/").pop()]);
    expect(row!.id).toBe(a);
  });

  it("no da el identificador de un dispositivo ajeno y exige sesión", async () => {
    withDns();
    const ana = await registerVerified(s);
    const bea = await registerVerified(s, "bea@example.com");
    const a = ((await putDevice(s, ana.accessToken, { name: "A" })).json() as { deviceId: string }).deviceId;
    expect((await s.app.inject({ method: "GET", url: `/dns/config?deviceId=${a}`, headers: bearer(bea.accessToken) })).statusCode).toBe(404);
    expect((await s.app.inject({ method: "GET", url: `/dns/config?deviceId=${a}` })).statusCode).toBe(401);
    expect((await s.app.inject({ method: "GET", url: "/dns/config?deviceId=no-es-uuid", headers: bearer(ana.accessToken) })).statusCode).toBe(400);
  });

  it("la dirección con barra final en la configuración se normaliza al cargarla", () => {
    const base = { PUBLIC_URL: "https://fg.test", JWT_SECRET: "a".repeat(40), NODE_ENV: "test" };
    expect(loadConfig({ ...base, DNS_DOH_URL: "https://dns.fg.test/dns-query//", DNS_DOH_BOOTSTRAP: " 1.2.3.4, 5.6.7.8 ," })).toMatchObject({ DNS_DOH_URL: "https://dns.fg.test/dns-query", DNS_DOH_BOOTSTRAP: ["1.2.3.4", "5.6.7.8"] });
  });

  it("sin resolvedor propio configurado responde 404", async () => {
    const ana = await registerVerified(s);
    const a = ((await putDevice(s, ana.accessToken, { name: "A" })).json() as { deviceId: string }).deviceId;
    expect((await s.app.inject({ method: "GET", url: `/dns/config?deviceId=${a}`, headers: bearer(ana.accessToken) })).statusCode).toBe(404);
  });
});

describe("archivos derivados", () => {
  it("solo sirve los que existen, sin caer en propiedades heredadas de los objetos", async () => {
    for (const name of ["constructor", "__proto__", "toString", "dns-rules", "otra.txt"]) {
      expect((await s.app.inject({ method: "GET", url: `/blocklists/${name}` })).statusCode).toBe(400);
    }
  });

  it("la cabecera, la versión y las reglas salen de las mismas listas", async () => {
    await applyList(s.deps.db, "domains", new Set(["a.example.com"]), s.deps.now());
    const res = await s.app.inject({ method: "GET", url: "/blocklists/dns-rules.txt" });
    expect(res.body.split("\n")[0]).toBe(`! FocusGuard ${res.headers["x-list-version"]}`);
  });
});

describe("configuración del DNS", () => {
  const base = { PUBLIC_URL: "https://fg.test", JWT_SECRET: "a".repeat(40), NODE_ENV: "test" };
  it("rechaza una dirección que no es https o unas IP mal escritas", () => {
    expect(() => loadConfig({ ...base, DNS_DOH_URL: "http://dns.fg.test/dns-query" })).toThrow(/https/);
    expect(() => loadConfig({ ...base, DNS_DOH_BOOTSTRAP: "dns.fg.test" })).toThrow(/IP/);
    expect(() => loadConfig({ ...base, DNS_DOH_BOOTSTRAP: "1.2.3" })).toThrow(/IP/);
    expect(loadConfig({ ...base, DNS_DOH_BOOTSTRAP: "203.0.113.7, 2001:db8::1" }).DNS_DOH_BOOTSTRAP).toEqual(["203.0.113.7", "2001:db8::1"]);
  });
});
