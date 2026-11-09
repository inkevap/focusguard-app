import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { DAY, limit, parse, type Deps } from "../http.js";

/** La puntuación máxima del test (10 preguntas de 0 a 4); el teléfono la calcula, el servidor solo comprueba que esté en rango. */
const MAX_SCORE = 40;
const KEEP = 50;

const body = z.object({
  score: z.number().int().min(0).max(MAX_SCORE),
  // Cuándo se hizo en el teléfono; nunca en el futuro ni de hace más de un año.
  takenAt: z.coerce.date().optional(),
});

/**
 * Test de adicción (L6). Solo se guarda la puntuación total y cuándo se hizo: las respuestas no salen del teléfono, y el compañero no
 * ve nada de esto. Sirve para que el usuario compare su evolución entre dispositivos.
 */
export function assessmentRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;

  app.post("/assessments", async (req, reply) => {
    const { score, takenAt } = parse(body, req.body);
    limit(deps, `assessments:${req.userId}`, 20, DAY);
    const now = deps.now();
    const at = takenAt && takenAt <= now && now.getTime() - takenAt.getTime() <= 365 * DAY ? takenAt : now;
    const id = await db.tx(async (tx) => {
      // La misma puntuación a la misma hora es la misma: si la respuesta se perdió y el teléfono reintenta, no se duplica.
      const [created] = await tx.query<{ id: string }>(
        "INSERT INTO assessments (user_id, score, taken_at, created_at) VALUES ($1,$2,$3,$4) ON CONFLICT (user_id, taken_at, score) DO NOTHING RETURNING id",
        [req.userId, score, at, now],
      );
      const [row] = created ? [created] : await tx.query<{ id: string }>("SELECT id FROM assessments WHERE user_id = $1 AND taken_at = $2 AND score = $3", [req.userId, at, score]);
      // Solo las últimas: un historial sin fin no hace falta para ver la evolución.
      await tx.query(
        "DELETE FROM assessments WHERE user_id = $1 AND id NOT IN (SELECT id FROM assessments WHERE user_id = $1 ORDER BY taken_at DESC, created_at DESC LIMIT $2)",
        [req.userId, KEEP],
      );
      return row!.id;
    });
    return reply.code(201).send({ id, score, takenAt: at });
  });

  app.get("/assessments", async (req) => {
    const rows = await db.query<{ id: string; score: number; taken_at: Date }>("SELECT id, score, taken_at FROM assessments WHERE user_id = $1 ORDER BY taken_at DESC, created_at DESC", [req.userId]);
    return { assessments: rows.map((r) => ({ id: r.id, score: r.score, takenAt: r.taken_at })) };
  });

  app.delete("/assessments", async (req, reply) => {
    await db.query("DELETE FROM assessments WHERE user_id = $1", [req.userId]);
    return reply.code(204).send();
  });
}
