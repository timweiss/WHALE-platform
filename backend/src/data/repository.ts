import { Pool } from 'pg';
import { Logger, Observability } from '../o11y';

export class Repository {
  protected pool: Pool;
  protected observability: Observability;

  constructor(pool: Pool, observability: Observability) {
    this.pool = pool;
    this.observability = observability;
  }
}
