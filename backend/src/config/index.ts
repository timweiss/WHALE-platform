export const Config = {
  app: {
    hostname: process.env.APP_HOSTNAME || 'localhost',
    port: process.env.APP_PORT || 3000,
  },
  database: {
    connectionString: process.env.DB_CONNECTION || 'localhost:5432',
  },
};
