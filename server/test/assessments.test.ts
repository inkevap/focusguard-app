import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { advance, bearer, getJson, post, registerVerified, startServer, type TestServer } from "./helpers.js";

let s: TestServer;
beforeEach(async () => { s = await startServer(); });
afterEach(() => s.close());

describe("test de adicción (L6)", () => {
  it("guarda la puntuación y la devuelve, solo a su dueño y de más reciente a más antigua", async () => {
    const ana = await registerVerified(s);
    const bea = await registerVerified(s, "bea@example.com");
    expect((await post(s, "/assessments", { score: 23 }, ana.accessToken)).statusCode).toBe(201);
    advance(s, 60_000);
    expect((await post(s, "/assessments", { score: 15 }, ana.accessToken)).statusCode).toBe(201);
    const list = (await getJson(s, "/assessments", ana.accessToken)).assessments;
    expect(list.map((a: { score: number }) => a.score)).toEqual([15, 23]);
    expect((await getJson(s, "/assessments", bea.accessToken)).assessments).toEqual([]);
    expect((await s.app.inject({ method: "GET", url: "/assessments" })).statusCode).toBe(401);
  });

  it("reintentar la misma puntuación a la misma hora no la duplica", async () => {
    const ana = await registerVerified(s);
    const takenAt = new Date(s.deps.now().getTime() - 60_000).toISOString();
    const first = await post(s, "/assessments", { score: 12, takenAt }, ana.accessToken);
    const again = await post(s, "/assessments", { score: 12, takenAt }, ana.accessToken);
    expect(first.statusCode).toBe(201);
    expect(again.statusCode).toBe(201);
    expect((again.json() as { id: string }).id).toBe((first.json() as { id: string }).id);
    expect((await getJson(s, "/assessments", ana.accessToken)).assessments).toHaveLength(1);
  });

  it("solo acepta puntuaciones enteras entre 0 y 40", async () => {
    const ana = await registerVerified(s);
    for (const score of [-1, 41, 2.5, "20", null]) expect((await post(s, "/assessments", { score }, ana.accessToken)).statusCode).toBe(400);
    expect((await post(s, "/assessments", {}, ana.accessToken)).statusCode).toBe(400);
    expect((await post(s, "/assessments", { score: 0 }, ana.accessToken)).statusCode).toBe(201);
    expect((await post(s, "/assessments", { score: 40 }, ana.accessToken)).statusCode).toBe(201);
  });

  it("la hora del teléfono no puede ser futura ni de hace más de un año", async () => {
    const ana = await registerVerified(s);
    const now = s.deps.now();
    const at = (ms: number) => new Date(now.getTime() + ms).toISOString();
    await post(s, "/assessments", { score: 1, takenAt: at(86_400_000) }, ana.accessToken);
    await post(s, "/assessments", { score: 2, takenAt: at(-400 * 86_400_000) }, ana.accessToken);
    await post(s, "/assessments", { score: 3, takenAt: at(-10 * 86_400_000) }, ana.accessToken);
    const list = (await getJson(s, "/assessments", ana.accessToken)).assessments as Array<{ score: number; takenAt: string }>;
    for (const a of list.filter((x) => x.score !== 3)) expect(new Date(a.takenAt).getTime()).toBe(now.getTime());
    expect(new Date(list.find((x) => x.score === 3)!.takenAt).getTime()).toBe(now.getTime() - 10 * 86_400_000);
  });

  it("conserva las 50 últimas, limita a 20 por día y se puede borrar todo", async () => {
    const ana = await registerVerified(s);
    for (let i = 0; i < 20; i++) expect((await post(s, "/assessments", { score: i }, ana.accessToken)).statusCode).toBe(201);
    expect((await post(s, "/assessments", { score: 1 }, ana.accessToken)).statusCode).toBe(429);
    expect((await s.app.inject({ method: "DELETE", url: "/assessments", headers: bearer(ana.accessToken) })).statusCode).toBe(204);
    expect((await getJson(s, "/assessments", ana.accessToken)).assessments).toEqual([]);
  });

  it("el compañero y el informe diario no saben nada de la puntuación", async () => {
    const ana = await registerVerified(s);
    await post(s, "/assessments", { score: 35 }, ana.accessToken);
    expect(await s.deps.db.query("SELECT 1 FROM events")).toHaveLength(0);
  });
});
