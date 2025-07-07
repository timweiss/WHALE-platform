import { Pool, PoolConfig } from 'pg';
import { Config } from './index';
import { Logger, Observability } from '../o11y';

export function usePool(observability: Observability) {
  const config: PoolConfig = {};
  if (!Config.database.useEnv) {
    config['connectionString'] = Config.database.connectionString;
  }

  const pool = new Pool(config);

  // pool error handling to provide a failover
  pool.on('error', (err, client) => {
    observability.logger.error('Unexpected error on idle client', err);
    process.exit(-1);
  });

  return pool;
}
