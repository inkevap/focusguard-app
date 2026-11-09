/** Aviso al teléfono de que algo cambió en el servidor (una solicitud resuelta, el compañero aceptó…). */
export interface PushMessage {
  type: "request_resolved" | "partner_confirmed";
  requestId?: string;
}

export interface Push {
  /** Avisa a todos los dispositivos de la cuenta. */
  notifyUser(userId: string, message: PushMessage): Promise<void>;
}

/** Sin credenciales de FCM el teléfono se entera consultando el estado al abrir la app. */
export class NoopPush implements Push {
  async notifyUser() {}
}

export class MemoryPush implements Push {
  readonly sent: Array<{ userId: string; message: PushMessage }> = [];

  async notifyUser(userId: string, message: PushMessage) {
    this.sent.push({ userId, message });
  }
}

/** Garantiza el contrato de las dependencias: un fallo del push se registra y nunca rompe la operación que lo provoca. */
export class SafePush implements Push {
  constructor(private readonly inner: Push, private readonly log: { error(obj: unknown, msg?: string): void }) {}

  async notifyUser(userId: string, message: PushMessage) {
    try {
      await this.inner.notifyUser(userId, message);
    } catch (e) {
      this.log.error(e, "no se pudo enviar el aviso al teléfono");
    }
  }
}
