import type { Db } from "./db.js";

interface Migration {
  id: number;
  sql: string;
}

/** Solo se añaden migraciones al final; las que ya corrieron en un servidor desplegado no se editan. */
const migrations: Migration[] = [
  {
    id: 1,
    sql: `
      CREATE TABLE users (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        email text NOT NULL UNIQUE,
        password_hash text NOT NULL,
        created_at timestamptz NOT NULL
      );
      CREATE TABLE devices (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        name text NOT NULL,
        fcm_token text,
        last_seen timestamptz NOT NULL,
        created_at timestamptz NOT NULL
      );
      CREATE INDEX devices_user ON devices(user_id);
      -- Tokens de un solo uso (refresco de sesión, restablecer contraseña y, más adelante, compañero y aprobaciones). Solo se guarda el hash.
      CREATE TABLE tokens (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        purpose text NOT NULL,
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        token_hash text NOT NULL UNIQUE,
        expires_at timestamptz NOT NULL,
        used_at timestamptz,
        created_at timestamptz NOT NULL
      );
      CREATE INDEX tokens_user ON tokens(user_id, purpose);
      CREATE INDEX tokens_expires ON tokens(expires_at);
    `,
  },
  {
    id: 2,
    sql: `
      ALTER TABLE tokens ADD COLUMN subject_id uuid;
      CREATE TABLE partners (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        email text NOT NULL,
        status text NOT NULL CHECK (status IN ('pendiente','aceptado','revocado')),
        created_at timestamptz NOT NULL,
        confirmed_at timestamptz,
        revoked_at timestamptz
      );
      -- Un solo compañero vivo por cuenta.
      CREATE UNIQUE INDEX partners_one_live ON partners(user_id) WHERE status <> 'revocado';
      CREATE TABLE requests (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
        partner_id uuid NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
        -- Una solicitud o la pide el teléfono (action_type, código de Weakening.kt) o la crea el servidor (server_action).
        action_type integer,
        server_action text,
        summary text NOT NULL,
        detail jsonb NOT NULL DEFAULT '{}',
        status text NOT NULL CHECK (status IN ('pendiente','aprobada','rechazada','caducada','cancelada')),
        created_at timestamptz NOT NULL,
        expires_at timestamptz NOT NULL,
        resolved_at timestamptz,
        consumed_at timestamptz,
        CHECK ((action_type IS NULL) <> (server_action IS NULL))
      );
      CREATE INDEX requests_user ON requests(user_id);
      CREATE INDEX requests_partner ON requests(partner_id);
      CREATE INDEX requests_device ON requests(device_id) WHERE device_id IS NOT NULL;
      CREATE INDEX requests_open ON requests(expires_at) WHERE status = 'pendiente';
      CREATE INDEX tokens_subject ON tokens(purpose, subject_id) WHERE subject_id IS NOT NULL;
      -- Correos pendientes de enviar. Una fila se borra al enviarse; las que fallan se reintentan con espera creciente.
      -- dedupe_key evita encolar dos veces el mismo aviso periódico; next_at permite retrasar un envío (avisos agrupados).
      CREATE TABLE mail_outbox (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        to_addr text NOT NULL,
        subject text NOT NULL,
        body text NOT NULL,
        attempts integer NOT NULL DEFAULT 0,
        next_at timestamptz NOT NULL,
        dedupe_key text,
        last_error text,
        created_at timestamptz NOT NULL
      );
      CREATE INDEX mail_outbox_next ON mail_outbox(next_at) WHERE attempts < 8;
      CREATE UNIQUE INDEX mail_outbox_dedupe ON mail_outbox(dedupe_key) WHERE dedupe_key IS NOT NULL;
    `,
  },
  {
    id: 3,
    sql: `
      -- Quien invita a un compañero tiene que demostrar que el correo es suyo (si no, podría invitar suplantando a otra persona).
      ALTER TABLE users ADD COLUMN email_verified_at timestamptz;
    `,
  },
  {
    id: 4,
    sql: `
      -- prefs: avisos y plazos que el compañero eligió; solo se guardan las diferencias con los valores por defecto.
      ALTER TABLE partners ADD COLUMN prefs jsonb NOT NULL DEFAULT '{}';
      -- Cuándo se mandó por última vez cada aviso (resumen de bloqueos, informe del día, dispositivo sin señales, ráfagas).
      -- Sobrevive a la cola de correos, que borra lo enviado.
      CREATE TABLE notice_state (
        key text PRIMARY KEY,
        last_at timestamptz NOT NULL
      );
      -- Solo el tipo y una etiqueta corta (app o dominio): el texto de la pantalla no sale del teléfono.
      -- at: hora que dice el teléfono (para mostrar); received_at: la del servidor (para informes y retención).
      -- notified: solo importa para los bloqueos, que esperan al resumen; el resto nace en true.
      CREATE TABLE events (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        device_id uuid REFERENCES devices(id) ON DELETE SET NULL,
        type text NOT NULL,
        detail jsonb NOT NULL DEFAULT '{}',
        at timestamptz NOT NULL,
        received_at timestamptz NOT NULL,
        notified boolean NOT NULL DEFAULT false
      );
      CREATE INDEX events_user_received ON events(user_id, received_at);
      CREATE INDEX events_device ON events(device_id) WHERE device_id IS NOT NULL;
      CREATE INDEX events_unnotified ON events(user_id) WHERE NOT notified;
      CREATE TABLE wrong_block_reports (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        kind text NOT NULL CHECK (kind IN ('app','domain','word')),
        value text NOT NULL,
        created_at timestamptz NOT NULL
      );
      CREATE INDEX wrong_block_reports_user ON wrong_block_reports(user_id);
    `,
  },
  {
    id: 5,
    sql: `
      -- Documentos pequeños que la app descarga enteros cuando cambian (categorías, identificadores de vistas).
      CREATE TABLE datasets (
        name text PRIMARY KEY,
        version integer NOT NULL,
        content jsonb NOT NULL,
        updated_at timestamptz NOT NULL
      );
      -- Listas largas con descarga incremental: una fila por valor, con la versión en que entró y, si salió, en la que salió.
      -- compacted_through: versión hasta la que se borraron las bajas antiguas; quien tenga menos recibe la lista entera.
      CREATE TABLE list_versions (
        list text PRIMARY KEY,
        version integer NOT NULL DEFAULT 0,
        compacted_through integer NOT NULL DEFAULT 0
      );
      CREATE TABLE list_entries (
        list text NOT NULL,
        value text NOT NULL,
        added_version integer NOT NULL,
        removed_version integer,
        removed_at timestamptz,
        PRIMARY KEY (list, value)
      );
      CREATE INDEX list_entries_added ON list_entries(list, added_version);
      CREATE INDEX list_entries_removed ON list_entries(list, removed_version) WHERE removed_version IS NOT NULL;
      -- Copias de seguridad: el contenido lo cifra el teléfono; el servidor no puede leerlo.
      CREATE TABLE backups (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        format integer NOT NULL,
        blob bytea NOT NULL,
        created_at timestamptz NOT NULL
      );
      CREATE INDEX backups_user ON backups(user_id, created_at);
      -- Ya viene cifrado: no se puede comprimir, así que Postgres no pierde tiempo intentándolo.
      ALTER TABLE backups ALTER COLUMN blob SET STORAGE EXTERNAL;
    `,
  },
  {
    id: 6,
    sql: `
      -- Identificador con el que el dispositivo habla con el resolvedor DNS (/dns-query/<id>). Aleatorio y guardado, no derivado
      -- de ningún secreto: no cambia si rota la clave del servidor y permite buscar el dispositivo a partir de un identificador
      -- de cliente. Atribuir bloqueos a un dispositivo (D8) exige además activar el registro de consultas (ver DECISIONES.md).
      ALTER TABLE devices ADD COLUMN dns_client_id text;
      CREATE UNIQUE INDEX devices_dns_client ON devices(dns_client_id) WHERE dns_client_id IS NOT NULL;
    `,
  },
  {
    id: 7,
    sql: `
      -- Token de refresco que sustituyó a este, sellado con una clave derivada de este: si la respuesta se perdió, un reintento
      -- dentro del margen recibe el mismo sustituto en vez de quedarse sin sesión.
      ALTER TABLE tokens ADD COLUMN successor text;
    `,
  },
  {
    id: 8,
    sql: `
      -- «Personas a las que ayudas»: se busca por el correo del compañero.
      CREATE INDEX partners_accepted_email ON partners(email) WHERE status = 'aceptado';
    `,
  },
  {
    id: 9,
    sql: `
      -- Sincronización entre dispositivos (N4): un documento cifrado en el teléfono por cuenta, con versión para no pisarse.
      CREATE TABLE sync_docs (
        user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
        version integer NOT NULL,
        format integer NOT NULL,
        blob bytea NOT NULL,
        updated_at timestamptz NOT NULL
      );
    `,
  },
  {
    id: 10,
    sql: `
      -- Test de adicción (L6): solo la puntuación total y cuándo se hizo; las respuestas no salen del teléfono.
      CREATE TABLE assessments (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        score smallint NOT NULL CHECK (score BETWEEN 0 AND 40),
        taken_at timestamptz NOT NULL,
        created_at timestamptz NOT NULL
      );
      CREATE INDEX assessments_user ON assessments(user_id, taken_at DESC);
      CREATE UNIQUE INDEX assessments_once ON assessments(user_id, taken_at, score);
    `,
  },
  {
    id: 11,
    sql: `
      -- Los totales que solo cuentan en el informe diario (DNS, impulsos) se marcan al entrar en uno: ni se pierden si un informe no sale ni se repiten.
      ALTER TABLE events ADD COLUMN reported boolean NOT NULL DEFAULT false;
      CREATE INDEX events_unreported ON events(user_id, type) WHERE NOT reported;
    `,
  },
  {
    id: 12,
    sql: `
      -- Varios compañeros de responsabilidad por cuenta (hasta MAX_PARTNERS, que vigila el código): el mismo correo no puede estar dos veces.
      DROP INDEX partners_one_live;
      CREATE UNIQUE INDEX partners_one_live_per_email ON partners(user_id, lower(email)) WHERE status <> 'revocado';
      -- Un enlace de respuesta es de un compañero concreto: así, al darlo de baja, se anulan solo los suyos y no los de los demás.
      ALTER TABLE tokens ADD COLUMN partner_id uuid;
      CREATE INDEX tokens_partner ON tokens(partner_id) WHERE partner_id IS NOT NULL AND used_at IS NULL;
    `,
  },
  {
    id: 13,
    sql: `
      -- La cola guardaba solo el texto y todos los correos salían sin diseño: ahora también la versión HTML (null en los que ya estaban en cola).
      ALTER TABLE mail_outbox ADD COLUMN html text;
    `,
  },
];

export async function migrate(db: Db): Promise<void> {
  await db.query("CREATE TABLE IF NOT EXISTS schema_migrations (id integer PRIMARY KEY)");
  const done = new Set((await db.query<{ id: number }>("SELECT id FROM schema_migrations")).map((r) => r.id));
  for (const m of migrations.filter((m) => !done.has(m.id))) {
    await db.tx(async (tx) => {
      await tx.exec(m.sql);
      await tx.query("INSERT INTO schema_migrations (id) VALUES ($1)", [m.id]);
    });
  }
}
