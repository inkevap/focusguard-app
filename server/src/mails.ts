import type { Config } from "./config.js";
import type { Mail } from "./mailer.js";
import { renderEmail, type EmailDoc } from "./theme.js";
import type { UrgeSummary } from "./notices.js";
import { EVENT_TYPES, isQuiet, NOTICE_KINDS, type EventType, type PrefKey } from "./noticeKinds.js";

/** El mismo correo con su versión HTML; el texto no cambia. */
const withHtml = (mail: Omit<Mail, "html">, doc: EmailDoc): Mail => ({ ...mail, html: renderEmail(doc) });

const fmt = (d: Date) => `${d.toLocaleString("es-ES", { timeZone: "UTC", dateStyle: "long", timeStyle: "short" })} (UTC)`;

export const resetMail = (c: Config, to: string, token: string): Mail => {
  const url = `${c.PUBLIC_URL}/reset?token=${token}`;
  return withHtml(
    {
      to,
      subject: "Restablecer tu contraseña de FocusGuard",
      text: `Abre este enlace para elegir una contraseña nueva (vale 1 hora):\n${url}\n\nSi no lo pediste tú, ignora este correo.`,
    },
    {
      title: "Restablece tu contraseña",
      preheader: "Elige una contraseña nueva para FocusGuard.",
      paragraphs: ["Pulsa el botón para elegir una contraseña nueva. El enlace vale 1 hora."],
      buttons: [{ label: "Elegir contraseña nueva", url }],
      fine: "Si no lo pediste tú, ignora este correo.",
    },
  );
};

export const verifyEmailMail = (c: Config, to: string, token: string): Mail => {
  const url = `${c.PUBLIC_URL}/verify?token=${token}`;
  return withHtml(
    {
      to,
      subject: "Confirma tu correo de FocusGuard",
      text: `Abre este enlace para confirmar que este correo es tuyo (vale 7 días):\n${url}\n\nSi no creaste una cuenta, ignora este correo.`,
    },
    {
      title: "Confirma tu correo",
      preheader: "Un paso más para activar tu cuenta de FocusGuard.",
      paragraphs: ["Confirma que este correo es tuyo para activar tu cuenta. El enlace vale 7 días."],
      buttons: [{ label: "Confirmar mi correo", url }],
      fine: "Si no creaste una cuenta, ignora este correo.",
    },
  );
};

export const partnerInviteMail = (c: Config, to: string, inviter: string, token: string): Mail => {
  const url = `${c.PUBLIC_URL}/partners/confirm?token=${token}`;
  return withHtml(
    {
      to,
      subject: `${inviter} quiere que seas su compañero en FocusGuard`,
      text:
        `${inviter} usa FocusGuard para bloquear distracciones y te pide que seas su compañero de responsabilidad.\n\n` +
        `Si aceptas, recibirás un correo cuando quiera desactivar o debilitar una protección, y solo se hará si tú lo apruebas. No hace falta crear una cuenta.\n\n` +
        `Para aceptar, abre este enlace y pulsa «Aceptar» (vale 7 días):\n${url}\n\n` +
        `Si no conoces a esta persona o no quieres, ignora este correo.`,
    },
    {
      title: `${inviter} quiere que seas su compañero`,
      preheader: "Te pide que seas su compañero de responsabilidad en FocusGuard.",
      paragraphs: [
        `${inviter} usa FocusGuard para bloquear distracciones y te pide que seas su compañero de responsabilidad.`,
        "Si aceptas, recibirás un correo cuando quiera desactivar o debilitar una protección, y solo se hará si tú lo apruebas. No hace falta crear una cuenta.",
      ],
      buttons: [{ label: "Aceptar ser su compañero", url }],
      fine: "El enlace vale 7 días. Si no conoces a esta persona o no quieres, ignora este correo.",
    },
  );
};

export const requestMail = (c: Config, p: { to: string; user: string; device: string; summary: string; expiresAt: Date; token: string }): Mail => {
  // Un solo enlace a la página donde están los dos botones: así en el correo no hay un «Aprobar» a un toque de distancia.
  const url = `${c.PUBLIC_URL}/r/${p.token}`;
  return withHtml(
    {
      to: p.to,
      subject: `${p.user} pide tu aprobación en FocusGuard`,
      text:
        `${p.user} quiere hacer este cambio en el dispositivo «${p.device}»:\n\n${p.summary}\n\n` +
        `Para aprobarla o rechazarla, abre este enlace:\n${url}\n\n` +
        `La solicitud caduca el ${fmt(p.expiresAt)}. ` +
        `Si dudas, habla primero con ${p.user}: aprobar quita una protección que se puso para ayudarle.`,
    },
    {
      title: `${p.user} pide tu aprobación`,
      preheader: p.summary,
      paragraphs: [`${p.user} quiere hacer este cambio en el dispositivo «${p.device}»:`, p.summary],
      buttons: [{ label: "Revisar la solicitud", url, kind: "primary" }],
      fine:
        `En la página verás los botones para aprobar o rechazar. La solicitud caduca el ${fmt(p.expiresAt)}. ` +
        `Si dudas, habla primero con ${p.user}: aprobar quita una protección que se puso para ayudarle.`,
    },
  );
};

const footer = (prefsUrl: string) => `\n\n—\nElige qué avisos quieres recibir, o déjalos de recibir, aquí:\n${prefsUrl}`;

const about = (user: string, kind: PrefKey) => `${user} ${NOTICE_KINDS[kind].text}`;

export const noticeMail = (p: { to: string; user: string; device: string; type: EventType; at: Date; label: string; prefsUrl: string }): Mail =>
  withHtml(
    {
      to: p.to,
      subject: about(p.user, p.type),
      text: `${about(p.user, p.type)} en el dispositivo «${p.device}».\n\nHora: ${fmt(p.at)}${p.label ? `\nDetalle: ${p.label}` : ""}\n\nUn mensaje de apoyo ahora vale más que cualquier bloqueo.${footer(p.prefsUrl)}`,
    },
    {
      title: about(p.user, p.type),
      paragraphs: [
        `${about(p.user, p.type)} en el dispositivo «${p.device}».`,
        `Hora: ${fmt(p.at)}${p.label ? `. Detalle: ${p.label}` : ""}`,
        "Un mensaje de apoyo ahora vale más que cualquier bloqueo.",
      ],
      prefsUrl: p.prefsUrl,
    },
  );

export const digestMail = (p: { to: string; user: string; total: number; top: Array<[string, number]>; prefsUrl: string }): Mail =>
  withHtml(
    {
      to: p.to,
      subject: `${p.user} ha intentado abrir algo bloqueado (${p.total})`,
      text: `En los últimos minutos, ${p.user} ha intentado abrir algo bloqueado ${p.total} ${p.total === 1 ? "vez" : "veces"}:\n\n${p.top.map(([label, n]) => `- ${label}: ${n}`).join("\n")}${footer(p.prefsUrl)}`,
    },
    {
      title: `${p.user} ha intentado abrir algo bloqueado`,
      paragraphs: [`En los últimos minutos, ${p.user} ha intentado abrir algo bloqueado ${p.total} ${p.total === 1 ? "vez" : "veces"}:`],
      list: p.top.map(([label, n]) => `${label}: ${n}`),
      prefsUrl: p.prefsUrl,
    },
  );

export const inactiveMail = (p: { to: string; user: string; device: string; lastSeen: Date; prefsUrl: string }): Mail => {
  const body = `El dispositivo «${p.device}» de ${p.user} no da señales desde el ${fmt(p.lastSeen)}. Puede que la app se haya desinstalado o apagado, o que el teléfono esté sin batería o sin conexión. Conviene escribirle.`;
  return withHtml({ to: p.to, subject: `FocusGuard de ${p.user} dejó de dar señales`, text: `${body}${footer(p.prefsUrl)}` }, { title: `FocusGuard de ${p.user} dejó de dar señales`, paragraphs: [body], prefsUrl: p.prefsUrl });
};

export const reportMail = (p: { to: string; user: string; counts: Record<string, number>; dns?: { blocked: number; allowed: number } | null; urges?: UrgeSummary | null; prefsUrl: string }): Mail => {
  const lines = EVENT_TYPES.filter((t) => p.counts[t] && !isQuiet(t)).map((t) => `- ${about(p.user, t)}: ${p.counts[t]}`);
  if (p.urges) {
    const u = p.urges;
    const extra = [u.peakHour === null ? "" : ` La hora más frecuente: ${String(u.peakHour).padStart(2, "0")}:00.`, u.trigger === null ? "" : ` Lo que más los desencadenó: ${u.trigger}.`].join("");
    lines.push(`- Impulsos de la semana pasada: ${u.total} en total; ${u.passed} los superó y en ${u.gaveIn} cedió.${extra} ${p.user} ha decidido compartir este resumen contigo.`);
  }
  if (p.dns) lines.push(`- Consultas DNS desde el último informe: ${p.dns.blocked} bloqueadas y ${p.dns.allowed} permitidas (solo el total; ${p.user} es el único que ve los dominios)`);
  return withHtml(
    {
      to: p.to,
      subject: `Informe diario de ${p.user}`,
      text: `Resumen de las últimas 24 horas de ${p.user}:\n\n${lines.length ? lines.join("\n") : "Sin incidencias. Buen día."}${footer(p.prefsUrl)}`,
    },
    {
      title: `Informe diario de ${p.user}`,
      paragraphs: [`Resumen de las últimas 24 horas de ${p.user}:`, ...(lines.length ? [] : ["Sin incidencias. Buen día."])],
      list: lines.map((l) => l.replace(/^- /, "")),
      prefsUrl: p.prefsUrl,
    },
  );
};

export const pinRecoveryMail = (to: string, code: string, validMinutes: number): Mail =>
  withHtml(
    {
      to,
      subject: "Código para recuperar el PIN de FocusGuard",
      text: `Tu código es ${code}. Vale ${validMinutes} minutos. Si no lo pediste tú, ignora este correo: sin el código nadie puede quitar el PIN.`,
    },
    {
      title: "Código para recuperar el PIN",
      paragraphs: [`Tu código es ${code}. Vale ${validMinutes} minutos.`],
      fine: "Si no lo pediste tú, ignora este correo: sin el código nadie puede quitar el PIN.",
    },
  );
