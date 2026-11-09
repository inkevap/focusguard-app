import { describe, expect, it } from "vitest";
import type { Config } from "../src/config.js";
import { partnerInviteMail, requestMail, resetMail, verifyEmailMail, digestMail } from "../src/mails.js";
import { pageHtml, renderEmail, tokens } from "../src/theme.js";

const config = { PUBLIC_URL: "https://fg.test" } as Config;
const hrefs = (html: string) => [...html.matchAll(/<a href="([^"]+)"/g)].map((m) => m[1]);

describe("paleta compartida con la app", () => {
  it("trae los colores que usan correos y páginas, en claro y en oscuro", () => {
    for (const scheme of [tokens.light, tokens.dark]) {
      for (const key of ["primary", "onPrimary", "background", "surface", "onSurface", "onSurfaceVariant", "outline", "outlineVariant", "error", "onError"]) {
        expect(scheme[key], key).toMatch(/^#[0-9A-F]{6}$/);
      }
    }
    expect(tokens.light.primary).not.toBe(tokens.dark.primary);
  });

  it("la página usa las variables de color y respeta el modo oscuro del sistema", () => {
    const html = pageHtml("Hola <b>", "<p>cuerpo</p>");
    expect(html).toContain(`--primary:${tokens.light.primary}`);
    expect(html).toContain("@media (prefers-color-scheme:dark)");
    expect(html).toContain(`--primary:${tokens.dark.primary}`);
    expect(html).toContain("Hola &lt;b&gt;");
    expect(html).not.toContain("<script");
  });
});

describe("correos con el estilo de la app", () => {
  it("la aprobación tiene un solo botón a la página con los dos, y el texto sigue con el enlace", () => {
    const mail = requestMail(config, { to: "c@x.org", user: "Ana", device: "Pixel", summary: "Apagar el filtro DNS", expiresAt: new Date("2026-01-03T00:00:00Z"), token: "tok" });
    expect(hrefs(mail.html!)).toEqual(["https://fg.test/r/tok"]);
    expect(mail.html).toContain(">Revisar la solicitud</a>");
    expect(mail.html).not.toContain(">Aprobar</a>");
    expect(mail.html).not.toContain(">Rechazar</a>");
    expect(mail.html).toContain(tokens.light.primary);
    expect(mail.html).toContain("prefers-color-scheme: dark");
    expect(mail.text).toContain("https://fg.test/r/tok");
    expect(mail.text).not.toMatch(/\/(approve|reject)/);
  });

  it("confirmar el correo, restablecer la contraseña y aceptar la invitación son botones", () => {
    expect(hrefs(verifyEmailMail(config, "a@x.org", "v1").html!)).toEqual(["https://fg.test/verify?token=v1"]);
    expect(hrefs(resetMail(config, "a@x.org", "r1").html!)).toEqual(["https://fg.test/reset?token=r1"]);
    const invite = partnerInviteMail(config, "a@x.org", "Ana", "p1");
    expect(hrefs(invite.html!)).toEqual(["https://fg.test/partners/confirm?token=p1"]);
    expect(invite.html).toContain(">Aceptar ser su compañero</a>");
  });

  it("debajo de cada botón va la dirección en texto, por si el cliente no lo pinta", () => {
    const html = verifyEmailMail(config, "a@x.org", "v1").html!;
    expect(html.match(/https:\/\/fg\.test\/verify\?token=v1/g)).toHaveLength(2);
  });

  it("lo que viene de un usuario se escapa", () => {
    const mail = partnerInviteMail(config, "a@x.org", `<img src=x onerror=alert(1)> "Ana"`, "p1");
    expect(mail.html).not.toContain("<img src=x");
    expect(mail.html).toContain("&lt;img src=x onerror=alert(1)&gt; &quot;Ana&quot;");
    const digest = digestMail({ to: "a@x.org", user: "Ana", total: 2, top: [["<b>juego</b>", 2]], prefsUrl: "https://fg.test/prefs?token=t" });
    expect(digest.html).toContain("&lt;b&gt;juego&lt;/b&gt;: 2");
  });

  it("el enlace a las preferencias sale en los avisos", () => {
    const digest = digestMail({ to: "a@x.org", user: "Ana", total: 1, top: [["x", 1]], prefsUrl: "https://fg.test/prefs?token=t" });
    expect(hrefs(digest.html!)).toEqual(["https://fg.test/prefs?token=t"]);
  });

  it("renderEmail sin botones ni lista da un correo válido", () => {
    const html = renderEmail({ title: "Solo texto", paragraphs: ["Hola"] });
    expect(html.startsWith("<!doctype html>")).toBe(true);
    expect(html).toContain("Solo texto");
    expect(hrefs(html)).toEqual([]);
  });
});
