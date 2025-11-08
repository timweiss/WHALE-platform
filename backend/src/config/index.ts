import dotenv from 'dotenv';

if (process.env.NODE_ENV === 'test') {
  console.log('loading test environment');
  dotenv.config({ path: '.test.env' });
}

export const Config = {
  app: {
    hostname: process.env.APP_HOSTNAME || 'localhost',
    port: process.env.APP_PORT || 8080,
    uploadLocation: process.env.APP_UPLOAD_LOCATION || './uploads',
  },
  database: {
    connectionString: process.env.DB_CONNECTION || 'localhost:5432',
    useEnv: process.env.DB_USE_ENV === 'true',
    poolSize: parseInt(process.env.DB_POOL_SIZE || '10', 10),
  },
  auth: {
    jwtSecret:
      process.env.AUTH_JWT_SECRET || 'change this or suffer the consequences',
  },
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD,
  },
  queue: {
    concurrency: parseInt(process.env.QUEUE_CONCURRENCY || '5', 10),
    maxJobsPerSecond: parseInt(
      process.env.QUEUE_MAX_JOBS_PER_SECOND || '10',
      10,
    ),
  },
};
