import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { applyList, changesSince, compactLists, parseDomains, parseWords, publishLocalLists, refreshPublicDomains, refreshPublicDomainsIfDue } from "../src/blocklists.js";
import { parseCategories, publishDataset, publishDatasets } from "../src/datasets.js";
import { advance, bearer, getJson, registerVerified, startServer, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

const hosts = (domains: string[]) => `# lista\n127.0.0.1 localhost\n${domains.map((d) => `0.0.0.0 ${d}`).join("\n")}\n`;
const many = (n: number) => Array.from({ length: n }, (_, i) => `d${i}.example.com`);
const fetchOf = (body: string, status = 200) => (async () => new Response(body, { status })) as unknown as typeof fetch;

describe("analizadores", () => {
  it("lee el formato hosts y descarta lo que no es un dominio", () => {
    const parsed = parseDomains("# c\n127.0.0.1 localhost\n0.0.0.0 0.0.0.0\n0.0.0.0 Ejemplo.COM # nota\n0.0.0.0 raro_dominio.com\n0.0.0.0 a.b.xn--p1ai\nsolo.dominio.org\n0.0.0.0 -mal.com\n1.2.3.4 otro.com");
    expect([...parsed].sort()).toEqual(["a.b.xn--p1ai", "ejemplo.com", "solo.dominio.org"]);
  });

  it("normaliza las palabras como la app (sin acentos, espacios, puntos ni guiones) y avisa de las que descarta", () => {
    const dropped: string[] = [];
    const words = parseWords("# x\nPorn\nx\nSexo en vivo\n18+\nPornográfico\nporn-hub\n¿?\n", (l) => dropped.push(l));
    expect([...words].sort()).toEqual(["18+", "porn", "pornhub", "pornografico", "sexoenvivo"]);
    expect(dropped).toEqual(["x", "¿?"]);
  });

  it("lee el CSV de categorías con el dominio vacío de los navegadores", () => {
    expect(parseCategories("com.a,a.com,social\ncom.chrome,,browsers\n")).toEqual([
      { package: "com.a", domain: "a.com", category: "social" },
      { package: "com.chrome", domain: "", category: "browsers" },
    ]);
  });
});

describe("listas incrementales", () => {
  it("la primera descarga es entera y las siguientes solo traen lo que entró y salió", async () => {
    const now = s.deps.now();
    await applyList(s.deps.db, "domains", new Set(["a.com", "b.com", "c.com"]), now);
    expect(await changesSince(s.deps.db, "domains", 0)).toEqual({ version: 1, full: true, added: ["a.com", "b.com", "c.com"], removed: [] });

    await applyList(s.deps.db, "domains", new Set(["b.com", "c.com", "d.com"]), now);
    expect(await changesSince(s.deps.db, "domains", 1)).toEqual({ version: 2, full: false, added: ["d.com"], removed: ["a.com"] });
    expect(await changesSince(s.deps.db, "domains", 2)).toEqual({ version: 2, full: false, added: [], removed: [] });
  });

  it("sin cambios no sube la versión", async () => {
    const now = s.deps.now();
    await applyList(s.deps.db, "domains", new Set(["a.com"]), now);
    expect((await applyList(s.deps.db, "domains", new Set(["a.com"]), now)).changed).toBe(false);
    expect((await changesSince(s.deps.db, "domains", 0)).version).toBe(1);
  });

  it("un valor que sale y vuelve a entrar se entrega otra vez como alta (inofensivo); entrar y salir entre dos versiones no se nota", async () => {
    const now = s.deps.now();
    await applyList(s.deps.db, "domains", new Set(["a.com", "b.com"]), now); // v1
    await applyList(s.deps.db, "domains", new Set(["b.com"]), now); // v2: sale a
    await applyList(s.deps.db, "domains", new Set(["a.com", "b.com", "x.com"]), now); // v3: vuelve a, entra x
    await applyList(s.deps.db, "domains", new Set(["a.com", "b.com"]), now); // v4: sale x
    expect(await changesSince(s.deps.db, "domains", 1)).toMatchObject({ added: ["a.com"], removed: [] }); // x no aparece
    expect(await changesSince(s.deps.db, "domains", 2)).toMatchObject({ added: ["a.com"], removed: [] });
    expect(await changesSince(s.deps.db, "domains", 4)).toMatchObject({ added: [], removed: [] });
  });

  it("una versión del futuro (servidor reiniciado de cero) recibe la lista entera", async () => {
    await applyList(s.deps.db, "domains", new Set(["a.com"]), s.deps.now());
    expect(await changesSince(s.deps.db, "domains", 99)).toMatchObject({ full: true, added: ["a.com"] });
  });

  it("recortar el historial de más de 90 días manda la lista entera a quien tenga una versión anterior", async () => {
    await applyList(s.deps.db, "domains", new Set(["a.com", "b.com"]), s.deps.now()); // v1
    await applyList(s.deps.db, "domains", new Set(["b.com"]), s.deps.now()); // v2
    advance(s, 91 * 86_400_000);
    await applyList(s.deps.db, "domains", new Set(["b.com", "c.com"]), s.deps.now()); // v3
    await compactLists(s.deps.db, s.deps.now());
    expect(await changesSince(s.deps.db, "domains", 1)).toMatchObject({ full: true, added: ["b.com", "c.com"] });
    expect(await changesSince(s.deps.db, "domains", 2)).toMatchObject({ full: false, added: ["c.com"], removed: [] });
  });

  it("maneja listas grandes", async () => {
    await applyList(s.deps.db, "domains", new Set(many(12_000)), s.deps.now());
    expect((await changesSince(s.deps.db, "domains", 0)).added).toHaveLength(12_000);
  });
});

describe("lista pública de dominios", () => {
  /** Sin los dominios propios reales del repositorio, para que contar descargas no dependa de cuántos haya. */
  async function depsWithoutOwn() {
    const dir = await mkdtemp(join(tmpdir(), "fg-own-"));
    await writeFile(join(dir, "own_domains.txt"), "# sin propios\n");
    return { ...s.deps, config: { ...s.deps.config, OWN_DIR: dir } };
  }

  it("se publica junto con la propia y se actualiza sin tocar lo que no cambió", async () => {
    const d = await depsWithoutOwn();
    const first = await refreshPublicDomains(d, fetchOf(hosts(many(1500))));
    expect(first).toEqual({ changed: true, version: 1 });
    const second = await refreshPublicDomains(d, fetchOf(hosts([...many(1500), "nuevo.example.org"])));
    expect(second).toEqual({ changed: true, version: 2 });
    expect(await changesSince(d.db, "domains", 1)).toMatchObject({ added: ["nuevo.example.org"], removed: [] });
  });

  it("descarta una descarga rota, pequeña o que se encoge a menos de la mitad, y no vacía la lista", async () => {
    const d = await depsWithoutOwn();
    await refreshPublicDomains(d, fetchOf(hosts(many(3000))));
    await expect(refreshPublicDomains(d, fetchOf("", 500))).rejects.toThrow(/500/);
    await expect(refreshPublicDomains(d, fetchOf(hosts(many(10))))).rejects.toThrow(/solo 10/);
    await expect(refreshPublicDomains(d, fetchOf(hosts(many(1200))))).rejects.toThrow(/pasó de 3000 a 1200/);
    expect((await changesSince(d.db, "domains", 0)).added).toHaveLength(3000);
  });

  it("corta una descarga que pasa de 30 MB aunque el servidor no diga su tamaño", async () => {
    const d = await depsWithoutOwn();
    const chunk = new Uint8Array(1024 * 1024).fill(97);
    let sent = 0;
    const stream = new ReadableStream({ pull(c) { if (sent++ < 40) c.enqueue(chunk); else c.close(); } });
    const http = (async () => new Response(stream, { status: 200 })) as unknown as typeof fetch;
    await expect(refreshPublicDomains(d, http)).rejects.toThrow(/demasiado grande/);
    expect(sent).toBeLessThan(40);
  });

  it("si la descarga falla se reintenta a la hora siguiente, y si sale bien no vuelve a bajar hasta pasadas 23 horas", async () => {
    const d = await depsWithoutOwn();
    await expect(refreshPublicDomainsIfDue(s.deps, fetchOf("", 503))).rejects.toThrow(/503/);
    advance(s, 60 * 60_000);
    expect(await refreshPublicDomainsIfDue(s.deps, fetchOf(hosts(many(1500))))).toMatchObject({ changed: true });
    advance(s, 60 * 60_000);
    expect(await refreshPublicDomainsIfDue(s.deps, fetchOf("no debe pedirse", 500))).toBeNull();
    advance(s, 23 * 60 * 60_000);
    expect(await refreshPublicDomainsIfDue(s.deps, fetchOf(hosts(many(1500))))).toMatchObject({ changed: false });
  });

  it("sin URL configurada no descarga", async () => {
    const d = await depsWithoutOwn();
    const deps = { ...s.deps, config: { ...s.deps.config, BLOCKLIST_URL: "" } };
    expect(await refreshPublicDomains(deps, fetchOf("no se debe pedir", 500))).toBeNull();
  });
});

describe("ficheros propios", () => {
  async function dirWith(files: Record<string, string>) {
    const dir = await mkdtemp(join(tmpdir(), "fg-lists-"));
    for (const [name, text] of Object.entries(files)) await writeFile(join(dir, name), text);
    return { ...s.deps, config: { ...s.deps.config, OWN_DIR: dir } };
  }
  const ok = { "words_es.txt": "porno\n", "words_en.txt": "porn\n", "exceptions.txt": "# vacío\n", "own_domains.txt": "propio.example.org\n" };

  it("un fichero que falta no publica una lista vacía: falla diciendo cuál", async () => {
    const dir = await mkdtemp(join(tmpdir(), "fg-empty-"));
    await publishLocalLists(await dirWith(ok));
    await expect(publishLocalLists({ ...s.deps, config: { ...s.deps.config, OWN_DIR: dir } })).rejects.toThrow(/words_es\.txt/);
    // La lista ya publicada sigue intacta.
    expect((await changesSince(s.deps.db, "words_es", 0)).added).toEqual(["porno"]);
  });

  it("una lista de palabras sin ninguna válida no se publica y no borra la anterior", async () => {
    await publishLocalLists(await dirWith(ok));
    await expect(publishLocalLists(await dirWith({ ...ok, "words_es.txt": "# solo comentarios\n" }))).rejects.toThrow(/ninguna palabra válida/);
    expect((await changesSince(s.deps.db, "words_es", 0)).added).toEqual(["porno"]);
  });

  it("publica los ficheros propios y la lista propia de dominios si aún no hay otra", async () => {
    await publishLocalLists(await dirWith(ok));
    expect((await changesSince(s.deps.db, "domains", 0)).added).toEqual(["propio.example.org"]);
    expect((await changesSince(s.deps.db, "words_en", 0)).added).toEqual(["porn"]);
  });

  it("los ficheros del repositorio existen en las rutas por defecto", async () => {
    await publishLocalLists(s.deps);
    await publishDatasets(s.deps);
    expect((await changesSince(s.deps.db, "words_es", 0)).added.length).toBeGreaterThan(5);
  });
});

describe("rutas públicas de datos", () => {
  beforeEach(async () => {
    await publishLocalLists(s.deps);
    await publishDatasets(s.deps);
  });

  it("publica las palabras por idioma y el índice de versiones, sin cuenta", async () => {
    const index = await getJson(s, "/blocklists");
    expect(index.versions).toMatchObject({ words_es: 1, words_en: 1 });
    expect(index.datasets).toEqual({ selectors: 1, categories: 1 });
    const es = await getJson(s, "/blocklists?list=words_es");
    expect(es.full).toBe(true);
    expect(es.added).toContain("porno");
    expect(await getJson(s, `/blocklists?list=words_es&since=${es.version}`)).toMatchObject({ full: false, added: [], removed: [] });
  });

  it("rechaza una lista desconocida y una versión mal escrita", async () => {
    expect((await s.app.inject({ method: "GET", url: "/blocklists?list=otra" })).statusCode).toBe(400);
    expect((await s.app.inject({ method: "GET", url: "/blocklists?list=domains&since=abc" })).statusCode).toBe(400);
  });

  it("sirve las tablas del repositorio y no manda el contenido a quien ya tiene la versión", async () => {
    const sel = await getJson(s, "/selectors");
    expect(sel.version).toBe(1);
    expect(sel.content.browsers.length).toBeGreaterThan(5);
    expect(sel.content.subapps.some((x: { id: string }) => x.id === "YOUTUBE_SHORTS")).toBe(true);
    expect(await getJson(s, "/selectors?since=1")).toEqual({ version: 1, unchanged: true });

    const cats = await getJson(s, "/categories");
    expect(cats.content.length).toBeGreaterThan(100);
    expect(cats.content.find((c: { package: string }) => c.package === "com.android.chrome")).toMatchObject({ category: "browsers", domain: "" });
  });

  it("publicar de nuevo lo mismo no cambia la versión y un cambio la sube", async () => {
    await publishDatasets(s.deps);
    expect((await getJson(s, "/selectors")).version).toBe(1);
    expect(await publishDataset(s.deps.db, "categories", [{ package: "x", domain: "", category: "tools" }], s.deps.now())).toBe(2);
    expect(await getJson(s, "/categories?since=1")).toMatchObject({ version: 2, content: [{ package: "x" }] });
  });

  it("sirve la lista entera como texto, una por línea, con su versión, y la repite desde la caché", async () => {
    const res = await s.app.inject({ method: "GET", url: "/blocklists/words_es.txt" });
    expect(res.statusCode).toBe(200);
    expect(res.headers["content-type"]).toContain("text/plain");
    expect(res.headers["x-list-version"]).toBe("1");
    expect(res.body.split("\n")).toContain("porno");
    expect(res.body.endsWith("\n")).toBe(true);

    // Misma versión: sale la misma respuesta ya armada. Con una versión nueva, la caché se renueva.
    expect((await s.app.inject({ method: "GET", url: "/blocklists/words_es.txt" })).body).toBe(res.body);
    await applyList(s.deps.db, "words_es", new Set(["nueva"]), s.deps.now());
    const next = await s.app.inject({ method: "GET", url: "/blocklists/words_es.txt" });
    expect(next.headers["x-list-version"]).toBe("2");
    expect(next.body).toBe("nueva\n");
    expect((await s.app.inject({ method: "GET", url: "/blocklists/otra.txt" })).statusCode).toBe(400);
    expect((await s.app.inject({ method: "GET", url: "/blocklists/..%2Fetc.txt" })).statusCode).toBe(400);
  });

  it("limita las consultas por IP", async () => {
    for (let i = 0; i < 600; i++) await s.app.inject({ method: "GET", url: "/blocklists" });
    expect((await s.app.inject({ method: "GET", url: "/blocklists" })).statusCode).toBe(429);
  });
});

describe("copias de seguridad", () => {
  const bytes = (text: string) => Buffer.from(text);
  const put = (token: string, body: Buffer, format: number | string = 1, type = "application/octet-stream") =>
    s.app.inject({ method: "POST", url: "/backups", headers: { ...bearer(token), "content-type": type, "x-backup-format": String(format) }, payload: body });
  const latest = (token: string, query = "") => s.app.inject({ method: "GET", url: `/backups/latest${query}`, headers: bearer(token) });

  it("guarda bytes opacos y devuelve la última tal cual, solo a su dueño", async () => {
    const ana = await registerVerified(s);
    const bea = await registerVerified(s, "bea@example.com");
    expect((await put(ana.accessToken, bytes("primera"))).statusCode).toBe(201);
    advance(s, 60_000);
    const raw = Buffer.from([0, 255, 1, 254, 2, 253]);
    expect((await put(ana.accessToken, raw)).statusCode).toBe(201);
    const res = await latest(ana.accessToken);
    expect(res.headers["content-type"]).toContain("application/octet-stream");
    expect(res.headers["x-backup-format"]).toBe("1");
    expect(Buffer.compare(res.rawPayload, raw)).toBe(0);
    expect((await latest(bea.accessToken)).statusCode).toBe(404);
    expect((await s.app.inject({ method: "GET", url: "/backups/latest" })).statusCode).toBe(401);
  });

  it("las copias nuevas de otro formato no expulsan las 5 que aún lee una app más antigua", async () => {
    const ana = await registerVerified(s);
    for (let i = 0; i < 2; i++) { advance(s, 60_000); await put(ana.accessToken, bytes(`vieja ${i}`), 1); }
    for (let i = 0; i < 7; i++) { advance(s, 60_000); await put(ana.accessToken, bytes(`nueva ${i}`), 2); }
    const rows = await s.deps.db.query("SELECT format FROM backups ORDER BY format");
    expect(rows.filter((r) => r.format === 1)).toHaveLength(2);
    expect(rows.filter((r) => r.format === 2)).toHaveLength(5);
    expect((await latest(ana.accessToken, "?maxFormat=1")).rawPayload.toString()).toBe("vieja 1");
  });

  it("conserva las 5 últimas y se pueden borrar todas", async () => {
    const ana = await registerVerified(s);
    for (let i = 0; i < 7; i++) {
      advance(s, 60_000);
      await put(ana.accessToken, bytes(`copia ${i}`));
    }
    expect(await s.deps.db.query("SELECT 1 FROM backups")).toHaveLength(5);
    expect((await s.app.inject({ method: "DELETE", url: "/backups", headers: bearer(ana.accessToken) })).statusCode).toBe(204);
    expect(await s.deps.db.query("SELECT 1 FROM backups")).toHaveLength(0);
  });

  it("una app vieja pide maxFormat y no recibe copias de un formato que no entiende", async () => {
    const ana = await registerVerified(s);
    await put(ana.accessToken, bytes("vieja"), 1);
    advance(s, 60_000);
    await put(ana.accessToken, bytes("nueva"), 2);
    expect((await latest(ana.accessToken)).rawPayload.toString()).toBe("nueva");
    const old = await latest(ana.accessToken, "?maxFormat=1");
    expect(old.rawPayload.toString()).toBe("vieja");
    expect(old.headers["x-backup-format"]).toBe("1");
    expect((await latest(ana.accessToken, "?maxFormat=0")).statusCode).toBe(400);
  });

  it("rechaza lo que no son bytes, el formato fuera de rango y las copias vacías", async () => {
    const ana = await registerVerified(s);
    expect((await put(ana.accessToken, bytes("{}"), 1, "application/json")).statusCode).toBe(415);
    expect((await put(ana.accessToken, bytes("x"), 0)).statusCode).toBe(400);
    expect((await put(ana.accessToken, bytes("x"), "abc")).statusCode).toBe(400);
    expect((await put(ana.accessToken, Buffer.alloc(0))).statusCode).toBe(415);
  });

  it("admite casi 2 MB y rechaza más", async () => {
    const ana = await registerVerified(s);
    const big = Buffer.alloc(1_900_000, 7);
    expect((await put(ana.accessToken, big)).statusCode).toBe(201);
    expect(Buffer.compare((await latest(ana.accessToken)).rawPayload, big)).toBe(0);
    expect((await put(ana.accessToken, Buffer.alloc(2_100_000, 7))).statusCode).toBe(413);
  });

  it("limita a 10 copias al día por usuario", async () => {
    const ana = await registerVerified(s);
    for (let i = 0; i < 10; i++) expect((await put(ana.accessToken, bytes(`c${i}`))).statusCode).toBe(201);
    expect((await put(ana.accessToken, bytes("una más"))).statusCode).toBe(429);
  });
});
