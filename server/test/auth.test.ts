import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { drainMailQueue } from "../src/mailqueue.js";
import { loadConfig } from "../src/config.js";
import { RateLimiter } from "../src/ratelimit.js";
import { purgeExpiredTokens } from "../src/tokens.js";
import { advance, bearer, flushMail, linkToken, post as hpost, putDevice, register, startServer, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

const post = (url: string, payload?: object) => hpost(s, url, payload);

describe("registro e inicio de sesión", () => {
  it("registra, inicia sesión y no guarda la contraseña en claro", async () => {
    const { res } = await register(s, "Ana@Example.com");
    expect(res.statusCode).toBe(201);
    const rows = await s.deps.db.query("SELECT email, password_hash FROM users");
    expect(rows[0]!.email).toBe("ana@example.com");
    expect(rows[0]!.password_hash).toMatch(/^\$argon2id\$/);
    const login = await post("/auth/login", { email: "ana@example.com", password: "contraseña-larga" });
    expect(login.statusCode).toBe(200);
  });

  it("rechaza correo repetido, contraseña corta y datos mal formados", async () => {
    await register(s);
    expect((await register(s)).res.statusCode).toBe(409);
    expect((await register(s, "b@example.com", "corta")).res.statusCode).toBe(400);
    expect((await post("/auth/register", { email: "no-es-correo", password: "contraseña-larga" })).statusCode).toBe(400);
  });

  it("da el mismo error con correo desconocido que con contraseña equivocada", async () => {
    await register(s);
    const bad = await post("/auth/login", { email: "ana@example.com", password: "otra-contraseña" });
    const unknown = await post("/auth/login", { email: "nadie@example.com", password: "otra-contraseña" });
    expect(bad.statusCode).toBe(401);
    expect(unknown.statusCode).toBe(401);
    expect(unknown.json()).toEqual(bad.json());
  });

  it("limita los intentos de inicio de sesión por correo", async () => {
    await register(s);
    for (let i = 0; i < 10; i++) await post("/auth/login", { email: "ana@example.com", password: "mala-contraseña" });
    expect((await post("/auth/login", { email: "ana@example.com", password: "contraseña-larga" })).statusCode).toBe(429);
    advance(s, 61 * 60_000);
    expect((await post("/auth/login", { email: "ana@example.com", password: "contraseña-larga" })).statusCode).toBe(200);
  });
});

describe("tokens", () => {
  it("el token de acceso abre rutas protegidas y caduca a los 15 minutos", async () => {
    const { body } = await register(s);
    const put = () => putDevice(s, body.accessToken, { name: "Pixel" });
    expect((await put()).statusCode).toBe(200);
    advance(s, 16 * 60_000);
    expect((await put()).statusCode).toBe(401);
  });

  it("rechaza rutas protegidas sin token o con uno manipulado", async () => {
    const { body } = await register(s);
    const call = (headers: Record<string, string>) => s.app.inject({ method: "PUT", url: "/devices/register", headers, payload: { name: "x" } });
    expect((await call({})).statusCode).toBe(401);
    expect((await call(bearer(body.accessToken.slice(0, -2) + "xx"))).statusCode).toBe(401);
  });

  it("el refresco rota el token y reutilizar uno viejo cierra todas las sesiones", async () => {
    const { body } = await register(s);
    const first = await post("/auth/refresh", { refreshToken: body.refreshToken });
    expect(first.statusCode).toBe(200);
    const second = first.json() as { refreshToken: string };
    expect(second.refreshToken).not.toBe(body.refreshToken);

    advance(s, 2 * 60_000);
    expect((await post("/auth/refresh", { refreshToken: body.refreshToken })).statusCode).toBe(401);
    // El robo detectado invalida también el token nuevo.
    expect((await post("/auth/refresh", { refreshToken: second.refreshToken })).statusCode).toBe(401);
  });

  it("el token de refresco caduca a los 60 días", async () => {
    const { body } = await register(s);
    advance(s, 61 * 86_400_000);
    expect((await post("/auth/refresh", { refreshToken: body.refreshToken })).statusCode).toBe(401);
  });

  it("solo guarda el hash de los tokens de refresco", async () => {
    const { body } = await register(s);
    const rows = await s.deps.db.query("SELECT token_hash FROM tokens WHERE purpose = 'refresh'");
    expect(rows[0]!.token_hash).not.toBe(body.refreshToken);
  });
});

describe("restablecer contraseña", () => {
  const tokenFromMail = async () => linkToken((await flushMail(s)).text);

  it("envía un enlace de un solo uso, cambia la contraseña y cierra las sesiones", async () => {
    const { body } = await register(s);
    expect((await post("/auth/password/reset", { email: "ana@example.com" })).statusCode).toBe(202);
    const token = await tokenFromMail();
    expect(s.mailer.outbox.at(-1)!.text).toContain("https://fg.test/reset?token=");

    const page = await s.app.inject({ method: "GET", url: `/reset?token=${token}` });
    expect(page.body).toContain('name="token"');

    const done = await s.app.inject({
      method: "POST", url: "/reset", headers: { "content-type": "application/x-www-form-urlencoded" },
      payload: `token=${token}&password=nueva-contraseña-1`,
    });
    expect(done.statusCode).toBe(200);
    expect((await post("/auth/login", { email: "ana@example.com", password: "nueva-contraseña-1" })).statusCode).toBe(200);
    expect((await post("/auth/refresh", { refreshToken: body.refreshToken })).statusCode).toBe(401);
    expect((await post("/auth/password/reset/confirm", { token, password: "otra-contraseña-2" })).statusCode).toBe(400);
  });

  it("no revela si el correo existe", async () => {
    const known = await post("/auth/password/reset", { email: "ana@example.com" });
    await register(s);
    const unknown = await post("/auth/password/reset", { email: "nadie@example.com" });
    expect(unknown.statusCode).toBe(known.statusCode);
    await drainMailQueue(s.deps);
    expect(s.mailer.outbox.filter((m) => m.to === "nadie@example.com")).toHaveLength(0);
  });

  it("el enlace caduca a la hora y se escapa el token en la página", async () => {
    await register(s);
    await post("/auth/password/reset", { email: "ana@example.com" });
    const token = await tokenFromMail();
    advance(s, 61 * 60_000);
    expect((await post("/auth/password/reset/confirm", { token, password: "nueva-contraseña-1" })).statusCode).toBe(400);
    const xss = await s.app.inject({ method: "GET", url: `/reset?token=${encodeURIComponent('"><script>x</script>')}` });
    expect(xss.body).not.toContain("<script>x");
  });
});

describe("dispositivos", () => {
  it("registra, actualiza y no deja tocar los de otra cuenta", async () => {
    const ana = (await register(s)).body;
    const bea = (await register(s, "bea@example.com")).body;
    const put = (token: string, payload: object) => putDevice(s, token, payload);

    const created = await put(ana.accessToken, { name: "Pixel", fcmToken: "tok1" });
    const { deviceId } = created.json() as { deviceId: string };
    expect((await put(ana.accessToken, { deviceId, name: "Pixel 8" })).statusCode).toBe(200);
    const [row] = await s.deps.db.query("SELECT name, fcm_token FROM devices WHERE id = $1", [deviceId]);
    expect(row).toMatchObject({ name: "Pixel 8", fcm_token: "tok1" });
    expect((await put(bea.accessToken, { deviceId, name: "robado" })).statusCode).toBe(404);
  });
});

describe("limpieza y errores", () => {
  it("borra los tokens caducados y conserva los vigentes", async () => {
    await register(s);
    advance(s, 61 * 86_400_000);
    await register(s, "bea@example.com");
    await purgeExpiredTokens(s.deps.db, s.deps.now());
    // Quedan solo los de la segunda cuenta: su sesión y su enlace de verificación.
    const rows = await s.deps.db.query("SELECT DISTINCT user_id FROM tokens");
    expect(rows).toHaveLength(1);
    expect(await s.deps.db.query("SELECT 1 FROM tokens")).toHaveLength(2);
  });

  it("las páginas de enlace muestran los errores como página, no como JSON", async () => {
    const res = await s.app.inject({ method: "GET", url: "/reset" });
    expect(res.statusCode).toBe(400);
    expect(res.headers["content-type"]).toContain("text/html");
    expect(res.headers["cache-control"]).toBe("no-store");
  });

  it("no calcula el hash de la contraseña con un token falso", async () => {
    const res = await s.app.inject({ method: "POST", url: "/auth/password/reset/confirm", payload: { token: "falso", password: "nueva-contraseña-1" } });
    expect(res.statusCode).toBe(400);
  });
});

describe("configuración", () => {
  const base = { PUBLIC_URL: "https://fg.test", JWT_SECRET: "a".repeat(40) };

  it("exige SMTP en producción", () => {
    expect(() => loadConfig({ ...base, NODE_ENV: "production" })).toThrow(/SMTP_HOST/);
    expect(loadConfig({ ...base, NODE_ENV: "production", SMTP_HOST: "smtp.test" }).SMTP_HOST).toBe("smtp.test");
  });

  it("rechaza un secreto JWT corto", () => {
    expect(() => loadConfig({ ...base, JWT_SECRET: "corto" })).toThrow(/JWT_SECRET/);
  });
});

describe("limitador", () => {
  it("cuenta por ventana y se renueva", () => {
    let t = 0;
    const l = new RateLimiter(() => t);
    expect([1, 2, 3].map(() => l.allow("k", 2, 1000))).toEqual([true, true, false]);
    t = 1001;
    expect(l.allow("k", 2, 1000)).toBe(true);
  });
});

describe("refresco y reintentos", () => {
  it("si la respuesta se perdió, un reintento inmediato recibe el mismo token nuevo y las sesiones siguen", async () => {
    const { body } = await register(s);
    const first = (await post("/auth/refresh", { refreshToken: body.refreshToken })).json() as { refreshToken: string };
    const retry = await post("/auth/refresh", { refreshToken: body.refreshToken });
    expect(retry.statusCode).toBe(200);
    expect((retry.json() as { refreshToken: string }).refreshToken).toBe(first.refreshToken);
    expect((await post("/auth/refresh", { refreshToken: first.refreshToken })).statusCode).toBe(200);
  });

  it("el reintento no crea otro token de refresco y el sustituto no se guarda en claro", async () => {
    const { body } = await register(s);
    const first = (await post("/auth/refresh", { refreshToken: body.refreshToken })).json() as { refreshToken: string };
    const before = await s.deps.db.query("SELECT count(*)::int AS n FROM tokens WHERE purpose = 'refresh'");
    await post("/auth/refresh", { refreshToken: body.refreshToken });
    expect(await s.deps.db.query("SELECT count(*)::int AS n FROM tokens WHERE purpose = 'refresh'")).toEqual(before);
    const rows = await s.deps.db.query("SELECT successor FROM tokens WHERE successor IS NOT NULL");
    expect(rows).toHaveLength(1);
    expect(rows[0]!.successor).not.toContain(first.refreshToken);
  });

  it("pasado el margen, reutilizar un token gastado cierra todas las sesiones y no devuelve nada", async () => {
    const { body } = await register(s);
    const first = (await post("/auth/refresh", { refreshToken: body.refreshToken })).json() as { refreshToken: string };
    advance(s, 2 * 60_000);
    expect((await post("/auth/refresh", { refreshToken: body.refreshToken })).statusCode).toBe(401);
    expect((await post("/auth/refresh", { refreshToken: first.refreshToken })).statusCode).toBe(401);
  });
});

describe("limitador lleno", () => {
  it("rechaza claves nuevas en vez de expulsar contadores vivos", () => {
    const l = new RateLimiter(() => 0);
    for (let i = 0; i < 50_000; i++) l.allow(`k${i}`, 1, 1000);
    expect(l.allow("nueva", 1, 1000)).toBe(false);
    expect(l.allow("k0", 5, 1000)).toBe(true);
  });
});
