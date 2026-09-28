import dotenv from 'dotenv';

if (process.env.NODE_ENV === 'test') {
  console.log('loading test environment');
  dotenv.config({ path: '.test.env' });
}

export const Config = {
  app: {
    hostname: process.env.APP_HOSTNAME || 'localhost',
    port: process.env.APP_PORT || 8080,
    // proxies whose X-Forwarded-For header is trusted to determine the client
    // IP, as a comma-separated list of addresses/subnets or express presets.
    // The default covers nginx on the same host, also when the API runs in a
    // Docker container (requests then arrive from the bridge network).
    trustProxy: process.env.APP_TRUST_PROXY || 'loopback, uniquelocal',
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
  rateLimit: {
    // per client IP and hour; lenient because participants on mobile networks
    // can share a public IP
    enrolmentsPerHour: parseInt(
      process.env.RATE_LIMIT_ENROLMENTS_PER_HOUR || '100',
      10,
    ),
    studyKeyLookupsPerHour: parseInt(
      process.env.RATE_LIMIT_STUDY_KEY_LOOKUPS_PER_HOUR || '300',
      10,
    ),
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
