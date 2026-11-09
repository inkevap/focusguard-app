import { isIP } from "node:net";
import { fileURLToPath } from "node:url";
import { z } from "zod";

// Relativas a este fichero (server/src o server/dist), no al directorio desde el que se arranca el proceso.
const here = (relative: string) => fileURLToPath(new URL(relative, import.meta.url));

const schema = z.object({
  PORT: z.coerce.number().int().default(3000),
  PUBLIC_URL: z.string().url().transform((u) => u.replace(/\/+$/, "")),
  JWT_SECRET: z.string().min(32, "JWT_SECRET necesita al menos 32 caracteres"),
  TRUST_PROXY: z.enum(["true", "false"]).default("false").transform((v) => v === "true"),
  // Ruta a la clave JSON de la cuenta de servicio de Firebase (montada como secreto). Sin ella no hay push.
  FCM_SERVICE_ACCOUNT_FILE: z.string().default(""),
  // Lista pública de dominios adultos (formato hosts). Vacía = no se descarga y solo vale la propia.
  BLOCKLIST_URL: z.string().default("https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"),
  // Tablas del repositorio (categorías, identificadores de vistas) y listas propias (palabras, excepciones, dominios).
  SEED_DIR: z.string().default(here("../../data/seed")),
  OWN_DIR: z.string().default(here("../data")),
  // Dirección pública del resolvedor DNS sobre HTTPS (por ejemplo https://dns.midominio.com/dns-query). Vacía = sin resolvedor propio.
  DNS_DOH_URL: z
    .string()
    .default("")
    .transform((u) => u.replace(/\/+$/, ""))
    .refine((u) => u === "" || (URL.canParse(u) && u.startsWith("https://")), "tiene que ser una dirección https"),
  // IP del resolvedor (separadas por comas): la app las usa para llegar a él sin depender del DNS del sistema ni de su propia VPN.
  DNS_DOH_BOOTSTRAP: z
    .string()
    .default("")
    .transform((v) => v.split(",").map((s) => s.trim()).filter(Boolean))
    .refine((ips) => ips.every((ip) => isIP(ip) !== 0), "tienen que ser direcciones IP separadas por comas"),
  SMTP_HOST: z.string().default(""),
  SMTP_PORT: z.coerce.number().int().default(587),
  SMTP_USER: z.string().default(""),
  SMTP_PASSWORD: z.string().default(""),
  MAIL_FROM: z.string().default("FocusGuard <avisos@localhost>"),
  NODE_ENV: z.string().default("development"),
}).superRefine((c, ctx) => {
  // Sin SMTP en producción los avisos al compañero se perderían en silencio: mejor no arrancar.
  if (c.NODE_ENV === "production" && !c.SMTP_HOST) ctx.addIssue({ code: "custom", path: ["SMTP_HOST"], message: "obligatorio en producción" });
});

export type Config = z.infer<typeof schema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const parsed = schema.safeParse(env);
  if (!parsed.success) {
    const problems = parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
    throw new Error(`Configuración no válida: ${problems}`);
  }
  return parsed.data;
}
