import { Pool, PoolConfig } from 'pg';
import { Config } from './index';
import { Observability } from '../o11y';

export function usePool(observability: Observability) {
  const config: PoolConfig = {
    max: Config.database.poolSize,
  };

  if (!Config.database.useEnv) {
    observability.logger.warn('Not using environment to connect to PostgreSQL');
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
