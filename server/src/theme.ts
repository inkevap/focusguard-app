import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/** Colores de un tema (claro u oscuro) tal como los define `data/theme/tokens.json`. */
export type Scheme = Record<string, string>;

export interface Tokens {
  name: string;
  light: Scheme;
  dark: Scheme;
  brand: { light: { start: string; end: string; onBrand: string }; dark: { start: string; end: string; onBrand: string } };
  shape: { small: number; medium: number; large: number; extraLarge: number };
}

/**
 * La paleta sale de `data/theme/tokens.json`, la misma fuente que la app (`ui/theme/Theme.kt`): si cambia el estilo en un sitio
 * hay que cambiarlo en el JSON y una prueba comprueba que `Theme.kt` coincide. Falta el fichero: el servidor no arranca.
 */
const dir = process.env.THEME_DIR ?? fileURLToPath(new URL("../../data/theme", import.meta.url));
export const tokens: Tokens = JSON.parse(readFileSync(`${dir}/tokens.json`, "utf8"));

const esc = (text: string) => text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
const FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif";
const L = tokens.light;
const D = tokens.dark;
const r = tokens.shape;

// ---- Páginas ----

const vars = (s: Scheme) =>
  Object.entries(s)
    .map(([k, v]) => `--${k}:${v}`)
    .join(";");

/** Hoja de estilo de las páginas: variables de color claro y, con `prefers-color-scheme`, oscuro. Sin scripts ni recursos externos. */
export function pageCss(): string {
  return (
    `:root{${vars(L)};--brand-start:${tokens.brand.light.start};--brand-end:${tokens.brand.light.end};--on-brand:${tokens.brand.light.onBrand};color-scheme:light dark}` +
    `@media (prefers-color-scheme:dark){:root{${vars(D)};--brand-start:${tokens.brand.dark.start};--brand-end:${tokens.brand.dark.end};--on-brand:${tokens.brand.dark.onBrand}}}` +
    `*{box-sizing:border-box}` +
    `body{margin:0;background:var(--background);color:var(--onBackground);font-family:${FONT};line-height:1.55}` +
    `.top{background:linear-gradient(135deg,var(--brand-start),var(--brand-end));color:var(--on-brand);padding:18px 24px;font-weight:700;font-size:1.05rem;letter-spacing:.2px}` +
    `main{max-width:30rem;margin:1.5rem auto;padding:0 1rem}` +
    `.card{background:var(--surface);border:1px solid var(--outlineVariant);border-radius:${r.large}px;padding:1.5rem}` +
    `h1{font-size:1.5rem;line-height:1.25;margin:0 0 .75rem;font-weight:650}` +
    `p{margin:.6rem 0}.muted{color:var(--onSurfaceVariant);font-size:.92rem}` +
    `a{color:var(--primary)}` +
    `input{font:inherit;width:100%;padding:.8rem 1rem;margin:.4rem 0;border:1px solid var(--outline);border-radius:${r.medium}px;background:var(--surface);color:var(--onSurface)}` +
    `input:focus-visible,button:focus-visible,a.btn:focus-visible{outline:3px solid var(--primary);outline-offset:2px}` +
    `button,a.btn{display:block;width:100%;font:inherit;font-weight:600;text-align:center;text-decoration:none;cursor:pointer;padding:.9rem 1.2rem;margin:.6rem 0;border-radius:${r.extraLarge}px;border:2px solid var(--primary);background:var(--primary);color:var(--onPrimary)}` +
    `button.secondary,a.btn.secondary{background:transparent;color:var(--primary)}` +
    `button.danger,a.btn.danger{background:var(--error);border-color:var(--error);color:var(--onError)}`
  );
}

/** Página completa con la cabecera de la marca y el contenido en una tarjeta. */
export function pageHtml(title: string, body: string): string {
  return (
    `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">` +
    `<meta name="color-scheme" content="light dark"><title>${esc(title)}</title><style>${pageCss()}</style></head>` +
    `<body><div class="top">FocusGuard</div><main><div class="card"><h1>${esc(title)}</h1>${body}</div></main></body></html>`
  );
}

// ---- Correos ----

export interface EmailButton {
  label: string;
  url: string;
  /** `primary` es la acción principal; `secondary` solo contorno; `danger` para rechazar o algo que no se deshace. */
  kind?: "primary" | "secondary" | "danger";
}

export interface EmailDoc {
  title: string;
  /** Texto que algunos clientes enseñan junto al asunto. */
  preheader?: string;
  paragraphs?: string[];
  list?: string[];
  buttons?: EmailButton[];
  /** Letra pequeña al final (caducidad, «si no fuiste tú…»). */
  fine?: string;
  /** Enlace a las preferencias de avisos, si procede. */
  prefsUrl?: string;
}

const btnStyle = (kind: EmailButton["kind"]) => {
  const base = `display:inline-block;font:600 16px/1 ${FONT};text-decoration:none;text-align:center;padding:16px 28px;border-radius:${r.extraLarge}px;`;
  if (kind === "secondary") return `${base}color:${L.primary};border:2px solid ${L.primary};background:${L.surface};`;
  if (kind === "danger") return `${base}color:${L.onError};background:${L.error};border:2px solid ${L.error};`;
  return `${base}color:${L.onPrimary};background:${L.primary};border:2px solid ${L.primary};`;
};

const btnClass = (kind: EmailButton["kind"]) => (kind === "secondary" ? "btn2" : kind === "danger" ? "btn3" : "btn");

/**
 * Correo HTML con la paleta de la app: tablas y estilos en línea (los clientes de correo no admiten variables CSS ni hojas externas),
 * con un bloque `@media (prefers-color-scheme: dark)` para los que sí respetan el modo oscuro. Los botones son enlaces con relleno
 * propio y, debajo, la dirección en texto por si el cliente no los pinta. Todo lo que viene de usuarios se escapa.
 */
export function renderEmail(doc: EmailDoc): string {
  const p = (text: string) => `<p class="t" style="margin:0 0 14px;color:${L.onSurface};font:400 16px/1.55 ${FONT}">${esc(text)}</p>`;
  const list = doc.list?.length
    ? `<ul style="margin:0 0 14px;padding-left:20px;color:${L.onSurface};font:400 16px/1.55 ${FONT}" class="t">${doc.list.map((i) => `<li style="margin:0 0 6px">${esc(i)}</li>`).join("")}</ul>`
    : "";
  const buttons = (doc.buttons ?? [])
    .map(
      (b) =>
        `<table role="presentation" cellpadding="0" cellspacing="0" style="margin:6px 0 10px"><tr><td align="center" bgcolor="${b.kind === "secondary" ? L.surface : b.kind === "danger" ? L.error : L.primary}" class="${btnClass(b.kind)}" style="border-radius:${r.extraLarge}px">` +
        `<a href="${esc(b.url)}" class="${btnClass(b.kind)}" style="${btnStyle(b.kind)}">${esc(b.label)}</a></td></tr></table>` +
        `<p class="m" style="margin:0 0 14px;color:${L.onSurfaceVariant};font:400 12px/1.4 ${FONT};word-break:break-all">${esc(b.url)}</p>`,
    )
    .join("");
  const fine = doc.fine ? `<p class="m" style="margin:18px 0 0;color:${L.onSurfaceVariant};font:400 13px/1.5 ${FONT}">${esc(doc.fine)}</p>` : "";
  const prefs = doc.prefsUrl
    ? `<p class="m" style="margin:18px 0 0;color:${L.onSurfaceVariant};font:400 13px/1.5 ${FONT}">Elige qué avisos quieres recibir, o déjalos de recibir, <a href="${esc(doc.prefsUrl)}" style="color:${L.primary}">aquí</a>.</p>`
    : "";
  const dark =
    `@media (prefers-color-scheme: dark){` +
    `.bg{background:${D.background}!important}.card{background:${D.surface}!important;border-color:${D.outlineVariant}!important}` +
    `.t{color:${D.onSurface}!important}.m{color:${D.onSurfaceVariant}!important}` +
    `.btn{background:${D.primary}!important;color:${D.onPrimary}!important;border-color:${D.primary}!important}` +
    `.btn2{background:${D.surface}!important;color:${D.primary}!important;border-color:${D.primary}!important}` +
    `.btn3{background:${D.error}!important;color:${D.onError}!important;border-color:${D.error}!important}` +
    `.top{background:${tokens.brand.dark.start}!important;color:${tokens.brand.dark.onBrand}!important}a{color:${D.primary}!important}}`;
  return (
    `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">` +
    `<meta name="color-scheme" content="light dark"><meta name="supported-color-schemes" content="light dark"><title>${esc(doc.title)}</title><style>${dark}</style></head>` +
    `<body class="bg" style="margin:0;padding:0;background:${L.background}">` +
    (doc.preheader ? `<div style="display:none;max-height:0;overflow:hidden;opacity:0">${esc(doc.preheader)}</div>` : "") +
    `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" class="bg" bgcolor="${L.background}" style="background:${L.background}"><tr><td align="center" style="padding:24px 12px">` +
    `<table role="presentation" width="560" cellpadding="0" cellspacing="0" style="width:100%;max-width:560px">` +
    `<tr><td class="top" bgcolor="${tokens.brand.light.start}" style="background:linear-gradient(135deg,${tokens.brand.light.start},${tokens.brand.light.end});color:${tokens.brand.light.onBrand};padding:20px 24px;border-radius:${r.large}px ${r.large}px 0 0;font:700 18px/1.2 ${FONT}">FocusGuard</td></tr>` +
    `<tr><td class="card" bgcolor="${L.surface}" style="background:${L.surface};border:1px solid ${L.outlineVariant};border-top:0;border-radius:0 0 ${r.large}px ${r.large}px;padding:24px">` +
    `<h1 class="t" style="margin:0 0 14px;color:${L.onSurface};font:650 22px/1.25 ${FONT}">${esc(doc.title)}</h1>` +
    (doc.paragraphs ?? []).map(p).join("") +
    list +
    buttons +
    fine +
    prefs +
    `</td></tr></table></td></tr></table></body></html>`
  );
}
