import { Pool } from 'pg';
import { Config } from './index';

export function usePool() {
  const pool = new Pool({
    connectionString: Config.database.connectionString,
  });

  // pool error handling to provide a failover
  pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err);
    process.exit(-1);
  });

  return pool;
}
