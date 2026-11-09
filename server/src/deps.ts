import type { FastifyBaseLogger } from "fastify";
import { pino } from "pino";
import type { Config } from "./config.js";
import type { Db } from "./db.js";
import type { Deps } from "./http.js";
import type { Mailer } from "./mailer.js";
import { NoopPush, SafePush, type Push } from "./push.js";
import { RateLimiter } from "./ratelimit.js";

/** Los enlaces del correo llevan el token en la dirección: el registro de peticiones no debe guardarlo. */
export const redactUrl = (url: string) => url.replace(/(\/r\/)[^/?]+/, "$1[token]").replace(/([?&]token=)[^&]+/, "$1[token]");

export const serializers = {
  req(req: unknown) {
    const out = pino.stdSerializers.req(req as Parameters<typeof pino.stdSerializers.req>[0]);
    return { ...out, url: redactUrl(out.url) };
  },
};

/** Único sitio donde se arman las dependencias, para que producción y pruebas no diverjan. */
export function createDeps(parts: { config: Config; db: Db; mailer: Mailer; push?: Push; log?: FastifyBaseLogger; now?: () => Date }): Deps {
  const now = parts.now ?? (() => new Date());
  const log = parts.log ?? pino({ serializers });
  return {
    config: parts.config,
    db: parts.db,
    mailer: parts.mailer,
    push: new SafePush(parts.push ?? new NoopPush(), log),
    log,
    now,
    limiter: new RateLimiter(() => now().getTime()),
  };
}
