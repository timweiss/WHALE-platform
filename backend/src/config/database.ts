import { Pool, PoolConfig } from 'pg';
import { Config } from './index';

export function usePool() {
  const config: PoolConfig = {};
  if (!Config.database.useEnv) {
    config['connectionString'] = Config.database.connectionString;
  }

  const pool = new Pool(config);

  // pool error handling to provide a failover
  pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err);
    process.exit(-1);
  });

  return pool;
}
