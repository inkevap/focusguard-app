import Fastify, { type FastifyInstance } from "fastify";
import { escapeHtml, HttpError, requireUser, sendPage, type Deps } from "./http.js";
import { accountRoutes, verifyPages } from "./routes/account.js";
import { authRoutes } from "./routes/auth.js";
import { assessmentRoutes } from "./routes/assessments.js";
import { backupRoutes } from "./routes/backups.js";
import { syncRoutes } from "./routes/sync.js";
import { dataRoutes } from "./routes/data.js";
import { deviceRoutes } from "./routes/devices.js";
import { dnsRoutes } from "./routes/dns.js";
import { eventRoutes } from "./routes/events.js";
import { linkPages } from "./routes/links.js";
import { partnerRoutes } from "./routes/partners.js";
import { prefsPages } from "./routes/prefs.js";
import { recoveryRoutes } from "./routes/recovery.js";
import { requestRoutes } from "./routes/requests.js";

export function buildApp(deps: Deps): FastifyInstance {
  // Ninguna petición legítima pasa de unos KB; el límite acota lo que se lee antes de validar.
  const app = Fastify({ loggerInstance: deps.log, trustProxy: deps.config.TRUST_PROXY, bodyLimit: 64 * 1024 });

  // Los formularios de las páginas del correo llegan como application/x-www-form-urlencoded.
  app.addContentTypeParser("application/x-www-form-urlencoded", { parseAs: "string" }, (_req, body, done) => {
    done(null, Object.fromEntries(new URLSearchParams(body as string)));
  });

  // Las copias de seguridad llegan como bytes opacos (ya cifrados por el teléfono).
  app.addContentTypeParser("application/octet-stream", { parseAs: "buffer" }, (_req, body, done) => done(null, body));

  app.setErrorHandler((error: { statusCode?: number }, req, reply) => {
    const status = error instanceof HttpError ? error.status : (error.statusCode ?? 500);
    if (status >= 500) req.log.error(error);
    const message = error instanceof HttpError ? error.message : status >= 500 ? "Error interno" : "Petición no válida";
    if (req.routeOptions?.config?.html) return sendPage(reply, status, "No se pudo completar", `<p>${escapeHtml(message)}</p>`);
    return reply.code(status).send({ error: message, ...(error instanceof HttpError && error.code ? { code: error.code } : {}) });
  });

  // Públicas: cada ruta nueva debe decidir a conciencia estar aquí.
  app.get("/health", async () => ({ ok: true }));
  authRoutes(app, deps);
  dataRoutes(app, deps);
  linkPages(app, deps);
  verifyPages(app, deps);
  prefsPages(app, deps);

  // Privadas: protegidas por omisión con el gancho de autenticación.
  app.register(async (priv) => {
    priv.decorateRequest("userId", "");
    priv.addHook("onRequest", async (req) => {
      req.userId = requireUser(deps, req);
    });
    accountRoutes(priv, deps);
    backupRoutes(priv, deps);
    syncRoutes(priv, deps);
    assessmentRoutes(priv, deps);
    deviceRoutes(priv, deps);
    dnsRoutes(priv, deps);
    eventRoutes(priv, deps);
    recoveryRoutes(priv, deps);
    partnerRoutes(priv, deps);
    requestRoutes(priv, deps);
  });
  return app;
}
