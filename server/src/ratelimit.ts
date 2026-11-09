const MAX_KEYS = 50_000;

/** Ventana fija en memoria. Basta con un solo proceso; con varios habría que moverlo a la base. */
export class RateLimiter {
  private readonly hits = new Map<string, { count: number; resetAt: number }>();
  private nextSweepAt = 0;

  constructor(private readonly now: () => number) {}

  /** `true` si la petición cabe en el cupo. */
  allow(key: string, max: number, windowMs: number): boolean {
    const t = this.now();
    if (t >= this.nextSweepAt) this.sweep(t);
    const entry = this.hits.get(key);
    if (!entry || entry.resetAt <= t) {
      // Tope duro: llenarlo exige miles de IP distintas (cada una tiene su propio cupo). Si pasa, se rechaza lo nuevo
      // en vez de expulsar contadores vivos, que dejaría a un atacante reiniciar el de una víctima.
      if (!entry && this.hits.size >= MAX_KEYS) return false;
      this.hits.set(key, { count: 1, resetAt: t + windowMs });
      return true;
    }
    entry.count += 1;
    return entry.count <= max;
  }

  private sweep(t: number) {
    for (const [k, v] of this.hits) if (v.resetAt <= t) this.hits.delete(k);
    this.nextSweepAt = t + 60_000;
  }
}
