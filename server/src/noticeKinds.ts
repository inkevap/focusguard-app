/**
 * Todos los avisos que puede recibir el compañero, en un solo sitio: su texto, si empiezan encendidos y si llegan del teléfono
 * como evento. Añadir uno es una sola entrada aquí; el resto del código se deriva.
 */
export interface NoticeKind {
  /** Frase para la página de preferencias. */
  label: string;
  /** Encendido por defecto. */
  on: boolean;
  /** «<usuario> <text>» en asuntos y cuerpos. */
  text: string;
  /** Lo manda el teléfono (`POST /events`); si no, lo genera el servidor. */
  event: boolean;
  /** Minutos mínimos entre dos correos iguales del mismo dispositivo (solo eventos graves). Los raros no esperan. */
  gapMinutes?: number;
  /** Solo cuenta en el informe diario: no avisa por correo cuando llega (es un total del día, sin detalle). */
  quiet?: boolean;
}

export const NOTICE_KINDS = {
  // El resumen de bloqueos es ruidoso: empieza apagado y el compañero lo enciende si quiere.
  blocked: { label: "Cada vez que intenta abrir algo bloqueado (resumen cada 10 minutos)", on: false, text: "ha intentado abrir algo bloqueado", event: true },
  settings_attempt: { label: "Intenta entrar en los ajustes para desactivar la protección", on: true, text: "ha intentado entrar en los ajustes para desactivar la protección", event: true, gapMinutes: 10 },
  uninstall_attempt: { label: "Intenta desinstalar la app", on: true, text: "ha intentado desinstalar la app", event: true, gapMinutes: 10 },
  protection_off: { label: "Se apaga la protección (accesibilidad, administrador del dispositivo…)", on: true, text: "ha apagado una protección", event: true, gapMinutes: 10 },
  panic: { label: "Usa el botón de pánico", on: true, text: "ha usado el botón de pánico y necesita apoyo", event: true, gapMinutes: 2 },
  emergency_unlock: { label: "Usa un desbloqueo de emergencia", on: true, text: "ha usado un desbloqueo de emergencia", event: true, gapMinutes: 0 },
  icon_changed: { label: "Cambia el icono de la app para disimularla", on: true, text: "ha cambiado el icono de la app para disimularla", event: true, gapMinutes: 0 },
  // El teléfono manda cada día cuántas consultas DNS se bloquearon y se permitieron, sin dominios: el detalle solo lo ve el usuario.
  dns_counts: { label: "Cuántas consultas DNS se bloquearon y se permitieron (solo el total, en el informe diario)", on: true, text: "ha enviado el resumen de consultas DNS", event: true, quiet: true },
  // El resumen semanal de impulsos solo sale del teléfono si el usuario lo permite; el compañero puede, además, no querer recibirlo.
  urge_report: { label: "Resumen semanal de impulsos (solo si la persona lo permite): cuántos y cuándo suelen llegar", on: true, text: "ha enviado el resumen semanal de impulsos", event: true, quiet: true },
  inactive: { label: "La app deja de dar señales (desinstalada o apagada)", on: true, text: "ha dejado de dar señales", event: false },
  daily_report: { label: "Informe diario", on: true, text: "tiene un informe diario", event: false },
} as const satisfies Record<string, NoticeKind>;

export type PrefKey = keyof typeof NOTICE_KINDS;
export const PREF_KEYS = Object.keys(NOTICE_KINDS) as PrefKey[];

export type EventType = { [K in PrefKey]: (typeof NOTICE_KINDS)[K]["event"] extends true ? K : never }[PrefKey];
export const EVENT_TYPES = PREF_KEYS.filter((k) => NOTICE_KINDS[k].event) as [EventType, ...EventType[]];

/** ¿Es un aviso que solo entra en el informe diario? */
export const isQuiet = (kind: PrefKey) => (NOTICE_KINDS[kind] as NoticeKind).quiet === true;

/** Minutos mínimos entre dos correos iguales del mismo dispositivo (0 si el tipo no limita). */
export const gapMinutes = (kind: PrefKey) => (NOTICE_KINDS[kind] as NoticeKind).gapMinutes ?? 0;
