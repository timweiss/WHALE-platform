import { Pool } from 'pg';

export class Repository {
  protected pool: Pool;

  constructor(pool: Pool) {
    this.pool = pool;
  }
}
