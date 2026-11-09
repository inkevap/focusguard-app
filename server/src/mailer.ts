import nodemailer from "nodemailer";
import type { Config } from "./config.js";

export interface Mail {
  to: string;
  subject: string;
  text: string;
  /** Versión con el estilo de la app. El texto sigue siendo la alternativa para los clientes que no pintan HTML. */
  html?: string;
}

export interface Mailer {
  send(mail: Mail): Promise<void>;
}

export class SmtpMailer implements Mailer {
  private readonly transport;

  constructor(private readonly config: Config) {
    this.transport = nodemailer.createTransport({
      host: config.SMTP_HOST,
      port: config.SMTP_PORT,
      secure: config.SMTP_PORT === 465,
      connectionTimeout: 10_000,
      greetingTimeout: 10_000,
      socketTimeout: 15_000,
      auth: config.SMTP_USER ? { user: config.SMTP_USER, pass: config.SMTP_PASSWORD } : undefined,
    });
  }

  async send(mail: Mail) {
    await this.transport.sendMail({ from: this.config.MAIL_FROM, ...mail });
  }
}

/** Desarrollo sin SMTP: escribe el correo en el registro (con sus enlaces). En producción la configuración exige SMTP. */
export class LogMailer implements Mailer {
  constructor(private readonly log: (msg: string) => void) {}

  async send(mail: Mail) {
    this.log(`Correo para ${mail.to}: ${mail.subject}\n${mail.text}`);
  }
}

export class MemoryMailer implements Mailer {
  readonly outbox: Mail[] = [];

  async send(mail: Mail) {
    this.outbox.push(mail);
  }
}

export function createMailer(config: Config, log: (msg: string) => void): Mailer {
  return config.SMTP_HOST ? new SmtpMailer(config) : new LogMailer(log);
}
